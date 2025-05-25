package app.service;

import app.ServiceLocator;
import app.controller.ChatController;
import app.model.Conversation;
import app.model.MessageDTO;
import app.util.ConversationKeyManager;
import app.util.EncryptionUtil;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import network.ClientConnection;
import network.Packet;
import network.PacketType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatService phụ trách gọi clientConnection.sendText,sendFile
 * và lắng nghe callback => hiển thị UI.
 */
public class ChatService {
    private ChatController chatUI;
    private ClientConnection client;
    private final Map<String, byte[]> fileCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> thumbCache = new ConcurrentHashMap<>();
    private final Set<String> thumbRequested = ConcurrentHashMap.newKeySet();

    private final Set<String> shownFileIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public byte[] getFileData(String key){ return fileCache.get(key); }
    public void removeFileData(String key){ fileCache.remove(key); }
    private final List<MessageDTO> messageHistory = new ArrayList<>();
    /* bảng tạm cho file đang download */
    private final Map<String, ByteArrayOutputStream> downloading = new ConcurrentHashMap<>();

    // Ví dụ ta có sẵn 1 conversation (group) cứng
    // Hoặc bạn tìm conversation theo ID, username,... tuỳ ý
    private Conversation currentConversation;

    public ChatService() {
        // Tạo client
        client = new ClientConnection();
        client.connect();

        // Lắng nghe callback khi nhận tin nhắn
        client.setOnTextReceived((from, text) -> {
            // Kiểm tra xem tin nhắn có được mã hóa không
            if (EncryptionUtil.isEncrypted(text) &&
                    ConversationKeyManager.getInstance().isGlobalChatEncryptionEnabled()) {
                // Giải mã tin nhắn
                String key = ConversationKeyManager.getInstance().getKey(ConversationKeyManager.GLOBAL_CHAT_ID);
                try {
                    String decryptedText = EncryptionUtil.decrypt(text, key);

                    // Cập nhật UI với tin nhắn đã giải mã
                    if (chatUI != null) {
                        Platform.runLater(() -> {
                            boolean isOutgoing = from.equals(chatUI.getCurrentUser());
                            // Truyền thêm tham số wasEncrypted=true để hiển thị biểu tượng khóa
                            chatUI.displayMessageWithEncryptionStatus(from, decryptedText, isOutgoing,
                                    LocalDateTime.now(), true);
                        });
                    }
                } catch (Exception e) {
                    // Không thể giải mã
                    System.err.println("Không thể giải mã tin nhắn Global: " + e.getMessage());
                    if (chatUI != null) {
                        Platform.runLater(() -> {
                            boolean isOutgoing = from.equals(chatUI.getCurrentUser());
                            chatUI.displayMessage(from, "[Không thể giải mã] " + text,
                                    isOutgoing, LocalDateTime.now());
                        });
                    }
                }
            } else {
                // Tin nhắn thường
                if (chatUI != null) {
                    Platform.runLater(() -> {
                        boolean isOutgoing = from.equals(chatUI.getCurrentUser());
                        chatUI.displayMessage(from, text, isOutgoing, LocalDateTime.now());
                    });
                }
            }
        });

        client.setOnFileReceived((from, filename, data) -> {
            String key = UUID.randomUUID().toString();
            fileCache.put(key, data);
            String fileMsg = String.format("[FILE]%s|%d|%s", filename, data.length, key);

            Platform.runLater(() -> {
                boolean out = from.equals(chatUI.getCurrentUser());
                chatUI.displayMessage(from, fileMsg, out, LocalDateTime.now());
            });
        });

        client.setOnFileMeta((from, name, size, idFlag) -> {
            String[] parts = idFlag.split("\\|", 2);
            String id   = parts[0];
            String flag = (parts.length == 2) ? parts[1] : "N";

            String msg = "[FILE]" + name + "|" + size + "|" + id;
            Platform.runLater(() -> {
                boolean outgoing = from.equals(chatUI.getCurrentUser());
                chatUI.displayMessage(from, msg, outgoing, LocalDateTime.now());
            });

            boolean needThumb = "T".equals(flag) || from.equals(chatUI.getCurrentUser());
            if(needThumb && !thumbCache.containsKey(id)){
                requestThumb(id);
            }
        });

        client.setOnFileThumb((id,bytes)->{
            System.out.println("[RECV_THUMB] "+id+" bytes="+bytes.length);
            thumbCache.put(id, bytes);
            Platform.runLater(() -> chatUI.refreshThumbnail(id));
        });

        client.setOnFileChunk((id,seq,last,data) -> {
            downloading
                    .computeIfAbsent(id, k-> new ByteArrayOutputStream())
                    .writeBytes(data);
            if(last){
                byte[] all = downloading.remove(id).toByteArray();
                fileCache.put(id, all);
            }
        });
    }

    public void bindUI(ChatController ui) {
        this.chatUI = ui;
    }

    public void login(String username) {
        client.login(username);
    }

    public void clearFileCache() { fileCache.clear(); }

    public void shutdown(){
        clearFileCache();
        if(client != null) client.close();
    }

    /**
     * Gửi tin nhắn với hỗ trợ mã hóa
     */
    public void sendMessage(String fromUser, String text) {
        if ("Global".equals(chatUI.getCurrentTarget())) {
            // Kiểm tra xem mã hóa có bật cho Global Chat không
            if (ConversationKeyManager.getInstance().isGlobalChatEncryptionEnabled()) {
                // Lấy khóa mã hóa cho Global Chat
                String key = ConversationKeyManager.getInstance().getKey(ConversationKeyManager.GLOBAL_CHAT_ID);
                // Mã hóa tin nhắn
                String encryptedText = EncryptionUtil.encrypt(text, key);
                // Gửi tin nhắn đã mã hóa
                client.sendText(encryptedText);
            } else {
                // Gửi tin nhắn thường
                client.sendText(text);
            }
        } else if (chatUI.isGroupTarget(chatUI.getCurrentTarget())) {
            // Xử lý tin nhắn nhóm
            long groupId = chatUI.getGroupId(chatUI.getCurrentTarget());
            // Kiểm tra xem mã hóa có bật cho nhóm này không
            if (ConversationKeyManager.getInstance().isEncryptionEnabled(groupId)) {
                // Lấy khóa mã hóa
                String key = ConversationKeyManager.getInstance().getKey(groupId);
                // Mã hóa tin nhắn
                String encryptedText = EncryptionUtil.encrypt(text, key);
                // Gửi tin nhắn đã mã hóa
                client.sendGroup(groupId, encryptedText);
            } else {
                // Gửi tin nhắn thường
                client.sendGroup(groupId, text);
            }
        } else {
            // Xử lý tin nhắn riêng tư
            String target = chatUI.getCurrentTarget();

            // Tìm conversation
            Conversation conv = chatUI.findPrivateConversation(fromUser, target);

            if (conv != null && ConversationKeyManager.getInstance().isEncryptionEnabled(conv.getId())) {
                // Lấy khóa mã hóa
                String key = ConversationKeyManager.getInstance().getKey(conv.getId());
                // Mã hóa tin nhắn
                String encryptedText = EncryptionUtil.encrypt(text, key);
                // Gửi tin nhắn đã mã hóa
                client.sendPrivate(target, encryptedText);
            } else {
                // Gửi tin nhắn thường
                client.sendPrivate(target, text);
            }
        }
    }

    public void download(String id){
        client.requestFile(id);
    }

    public boolean hasFile(String id){
        return fileCache.containsKey(id);
    }

    public void sendFile(long convId, String path){
        try {
            File f = new File(path);
            byte[] data = Files.readAllBytes(f.toPath());

            /* cache cho chính mình */
            String key = UUID.randomUUID().toString();
            fileCache.put(key, data);

            /* gởi server */
            client.sendFile(convId, f.getName(), data);
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public List<MessageDTO> getHistory() {
        return messageHistory;
    }

    // setter/getter cho currentConversation
    public void setConversation(Conversation conv) {
        this.currentConversation = conv;
    }

    public Conversation getConversation() {
        return this.currentConversation;
    }

    public ClientConnection getClient() {
        return client;
    }

    public void setClient(ClientConnection client) {
        this.client = client;
    }

    public byte[] getThumb(String id){
        return thumbCache.get(id);
    }

    public boolean isThumbRequested(String id) {
        return thumbRequested.contains(id);
    }

    public void requestThumb(String id) {
        if (!thumbRequested.contains(id)) {
            thumbRequested.add(id);
            client.requestThumb(id);
        }
    }
}