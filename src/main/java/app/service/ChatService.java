package app.service;

import app.ServiceLocator;
import app.controller.ChatController;
import app.model.Conversation;
import app.model.MessageDTO;
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
    private final Map<String, byte[]> thumbCache = new ConcurrentHashMap<>();   // ➕
    private final Set<String> thumbRequested = ConcurrentHashMap.newKeySet();    // ➕ Theo dõi các yêu cầu thumbnail

    private final Set<String> shownFileIds =
            java.util.concurrent.ConcurrentHashMap.newKeySet();   // ➊
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
            // Lưu vào DB
//            ServiceLocator.messageService().saveMessage(currentConversation, from, text, null);

            // Cập nhật UI
            if (chatUI != null) {
                Platform.runLater(() -> {
                    boolean isOutgoing = from.equals(chatUI.getCurrentUser());
                    chatUI.displayMessage(from, text, isOutgoing, java.time.LocalDateTime.now());
                });
            }
        });

        client.setOnFileReceived((from, filename, data) -> {
            String key = UUID.randomUUID().toString();   // ① tạo khoá duy nhất
            fileCache.put(key, data);                    // ② cache vào RAM

            // ③ build nội dung tin nhắn: [FILE]name|size|key
            String fileMsg = String.format("[FILE]%s|%d|%s", filename, data.length, key);

            Platform.runLater(() -> {
                boolean out = from.equals(chatUI.getCurrentUser());
                chatUI.displayMessage(from, fileMsg, out, LocalDateTime.now());
            });
        });
        // ChatService
        client.setOnFileMeta((from, name, size, idFlag) -> {
            /* tách "id|flag" an toàn */
            String[] parts = idFlag.split("\\|", 2);
            String id   = parts[0];
            String flag = (parts.length == 2) ? parts[1] : "N";

            /* ① vẽ bubble ngay */
            String msg = "[FILE]" + name + "|" + size + "|" + id;
            Platform.runLater(() -> {
                boolean outgoing = from.equals(chatUI.getCurrentUser());
                chatUI.displayMessage(from, msg, outgoing, java.time.LocalDateTime.now());
            });

            /* ② xin thumbnail nếu server báo có hoặc mình là người gửi */
            boolean needThumb = "T".equals(flag) || from.equals(chatUI.getCurrentUser());
            if(needThumb && !thumbCache.containsKey(id)){
                requestThumb(id);                 // gửi GET_THUMB
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
                fileCache.put(id, all);            // vào RAM cache
                // tuỳ ý: popup "Đã tải xong ..."
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
        clearFileCache();          // 1) xoá RAM-cache
        if(client != null) client.close();   // 2) đóng socket
    }

    public void sendMessage(String fromUser, String text) {
        // 1) Gửi tin qua socket
        client.sendText(text);
    }
    public void download(String id){
        client.requestFile(id);
    }

    public boolean hasFile(String id){          // ❶ kiểm tra đã cache chưa
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

//            /* vẽ bubble ngay cho sender */
//            if(chatUI != null){
//                String msg = String.format("[FILE]%s|%d|%s",
//                        f.getName(), data.length, key);
//                Platform.runLater(() ->
//                        chatUI.displayMessage(chatUI.getCurrentUser(),
//                                msg, true, java.time.LocalDateTime.now()));
//            }
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public List<MessageDTO> getHistory() {
        return messageHistory;
    }

    // ...
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
    public byte[]  getThumb(String id){ return thumbCache.get(id);}            // ➕
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
