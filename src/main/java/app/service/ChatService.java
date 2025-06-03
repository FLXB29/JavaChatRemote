package app.service;

import app.ServiceLocator;
import app.controller.ChatController;
import app.model.Conversation;
import app.model.MessageDTO;
//import app.util.ConversationKeyManager;
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

        client.setOnTextReceived((from, text) -> {
            // Tin nhắn đã được server giải mã trước khi gửi xuống
            // Chỉ cần hiển thị bình thường
            if (chatUI != null) {
                Platform.runLater(() -> {
                    boolean isOutgoing = from.equals(chatUI.getCurrentUser());
                    chatUI.displayMessage(from, text, isOutgoing, LocalDateTime.now());
                });
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
    public void sendMessage(String fromUser, String content) {
        try {
            // Kiểm tra xem đang chat ở đâu
            String target = chatUI.getCurrentTarget();

            if ("Global".equals(target)) {
                // Chat global
                client.sendText(content);
            } else if (chatUI.isGroupTarget(target)) {
                // Chat nhóm
                long groupId = chatUI.getGroupId(target);
                client.sendGroup(groupId, content);
            } else {
                // Chat riêng
                client.sendPrivate(target, content);
                chatUI.lastPmTarget = target;
            }

            // Lưu vào database với mã hóa
            Conversation conv = null;
            if ("Global".equals(target)) {
                conv = ServiceLocator.conversationDAO().findByName("Global Chat");
            } else if (chatUI.isGroupTarget(target)) {
                long groupId = chatUI.getGroupId(target);
                conv = ServiceLocator.conversationDAO().findById(groupId);
            } else {
                // Private conversation
                conv = chatUI.findPrivateConversation(fromUser, target);
            }

            if (conv != null) {
                // Content sẽ được mã hóa trong saveMessage
                ServiceLocator.messageService().saveMessage(conv, fromUser, content, null);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] Không thể gửi tin nhắn: " + e.getMessage());
            e.printStackTrace();
            throw e;
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