package app.service;

import app.ServiceLocator;
import app.controller.ChatController;
import app.controller.chat.ChatControllerRefactored;
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
    private ChatController chatUI; // Giữ lại cho tương thích ngược
    private ChatControllerRefactored chatUIRefactored; // Thêm controller mới
    private ClientConnection client;
    private final Map<String, byte[]> fileCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> thumbCache = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.CompletableFuture<byte[]>> thumbFutures = new ConcurrentHashMap<>();
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
            if (chatUIRefactored != null && chatUIRefactored.getMessageHandler() != null) {
                Platform.runLater(() -> {
                    // Giao cho MessageHandler xử lý tin nhắn global
                    boolean isOutgoing = from.equals(chatUIRefactored.getCurrentUsername());
                    
                    // Sử dụng handleNewMessage để cập nhật RecentChat
                    if (!isOutgoing) {
                        chatUIRefactored.getMessageHandler().handleNewMessage(from, text);
                    }
                    
                    // Hiển thị tin nhắn nếu đang ở màn hình global chat
                    String currentTarget = chatUIRefactored.getMessageHandler().getCurrentTarget();
                    if ("Global".equals(currentTarget)) {
                        chatUIRefactored.getMessageHandler().displayMessage(
                            from, text, isOutgoing, LocalDateTime.now());
                    }
                    
                    // Làm mới danh sách chat gần đây
                    chatUIRefactored.getMessageHandler().refreshRecentChats();
                });
            }
        });

        client.setOnPrivateMsgReceived((from, text) -> {
            // Xử lý tin nhắn riêng tư 
            if (chatUIRefactored != null && chatUIRefactored.getMessageHandler() != null) {
                Platform.runLater(() -> {
                    String currentUsername = chatUIRefactored.getCurrentUsername();
                    boolean isOutgoing = from.equals(currentUsername);
                    String currentTarget = chatUIRefactored.getMessageHandler().getCurrentTarget();
                    
                    // Nếu đang trong chat với người gửi, hiển thị tin nhắn
                    if (from.equals(currentTarget) || currentUsername.equals(currentTarget)) {
                        chatUIRefactored.getMessageHandler().displayMessage(
                            from, text, isOutgoing, LocalDateTime.now());
                    }
                    
                    // Xử lý tin nhắn mới
                    if (!isOutgoing) {
                        chatUIRefactored.getMessageHandler().handleNewMessage(from, text);
                    }
                    
                    // Làm mới danh sách chat gần đây
                    chatUIRefactored.getMessageHandler().refreshRecentChats();
                });
            }
        });

        client.setOnFileReceived((from, filename, data) -> {
            String key = UUID.randomUUID().toString();
            fileCache.put(key, data);
            String fileMsg = String.format("[FILE]%s|%d|%s", filename, data.length, key);

            if (chatUIRefactored != null && chatUIRefactored.getMessageHandler() != null) {
                Platform.runLater(() -> {
                    String currentUsername = chatUIRefactored.getCurrentUsername();
                    boolean isOutgoing = from.equals(currentUsername);
                    
                    // Hiển thị tin nhắn file
                    chatUIRefactored.getMessageHandler().displayMessage(
                        from, fileMsg, isOutgoing, LocalDateTime.now());
                    
                    // Nếu không phải tin nhắn của mình, xử lý như tin nhắn mới
                    if (!isOutgoing) {
                        chatUIRefactored.getMessageHandler().handleNewMessage(from, fileMsg);
                    }
                    
                    // Làm mới danh sách chat gần đây
                    chatUIRefactored.getMessageHandler().refreshRecentChats();
                });
            }
        });

        client.setOnFileMeta((from, name, size, idFlag) -> {
            String[] parts = idFlag.split("\\|", 2);
            String id   = parts[0];
            String flag = (parts.length == 2) ? parts[1] : "N";

            String msg = "[FILE]" + name + "|" + size + "|" + id;
            
            if (chatUIRefactored != null && chatUIRefactored.getMessageHandler() != null) {
                Platform.runLater(() -> {
                    String currentUsername = chatUIRefactored.getCurrentUsername();
                    boolean outgoing = from.equals(currentUsername);
                    
                    // Chỉ hiển thị tin nhắn file nếu không phải tin nhắn của mình
                    // Tin nhắn của mình đã được hiển thị khi gửi
                    if (!outgoing) {
                        // Hiển thị tin nhắn file
                        chatUIRefactored.getMessageHandler().displayMessage(
                            from, msg, outgoing, LocalDateTime.now());
                        
                        // Xử lý như tin nhắn mới
                        chatUIRefactored.getMessageHandler().handleNewMessage(from, msg);
                        
                        // Làm mới danh sách chat gần đây
                        chatUIRefactored.getMessageHandler().refreshRecentChats();
                    }
                });
            }

            boolean needThumb = "T".equals(flag) || (chatUIRefactored != null 
                && from.equals(chatUIRefactored.getCurrentUsername()));
            if(needThumb && !thumbCache.containsKey(id)){
                requestThumb(id);
            }
        });

        client.setOnFileThumb((id,bytes)->{
            System.out.println("[RECV_THUMB] "+id+" bytes="+bytes.length);
            // Thêm vào cache và hoàn thành CompletableFuture
            addThumbToCache(id, bytes);
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
        
        // Thiết lập callback cho group message
        client.setOnGroupMsg((groupId, from, text) -> {
            if (chatUIRefactored != null && chatUIRefactored.getMessageHandler() != null) {
                Platform.runLater(() -> {
                    String currentUsername = chatUIRefactored.getCurrentUsername();
                    boolean isOutgoing = from.equals(currentUsername);
                    
                    // Tìm tên nhóm từ groupId
                    String groupName = "Group " + groupId;
                    
                    // Cập nhật groupMap
                    chatUIRefactored.getMessageHandler().updateGroupMap(groupName, groupId);
                    
                    // Chỉ xử lý tin nhắn nhóm mới nếu không phải tin nhắn từ chính mình
                    // Tin nhắn từ chính mình đã được xử lý khi gửi
                    if (!isOutgoing) {
                        chatUIRefactored.getMessageHandler().handleNewGroupMessage(groupName, from, text);
                        
                        // Làm mới danh sách chat gần đây
                        chatUIRefactored.getMessageHandler().refreshRecentChats();
                    }
                });
            }
        });
    }

    public void bindUI(ChatController ui) {
        this.chatUI = ui;
    }
    
    /**
     * Liên kết với ChatControllerRefactored
     */
    public void bindRefactoredUI(ChatControllerRefactored ui) {
        this.chatUIRefactored = ui;
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
            String target = "";
            
            if (chatUIRefactored != null && chatUIRefactored.getMessageHandler() != null) {
                target = chatUIRefactored.getMessageHandler().getCurrentTarget();
                
                if ("Global".equals(target)) {
                    // Chat global
                    client.sendText(content);
                } else if (chatUIRefactored.getMessageHandler().isGroupTarget(target)) {
                    // Chat nhóm
                    long groupId = chatUIRefactored.getMessageHandler().getGroupId(target);
                    client.sendGroup(groupId, content);
                } else {
                    // Chat riêng
                    client.sendPrivate(target, content);
                }
                
                // Lưu vào database với mã hóa
                Conversation conv = null;
                if ("Global".equals(target)) {
                    conv = ServiceLocator.conversationDAO().findByName("Global Chat");
                } else if (chatUIRefactored.getMessageHandler().isGroupTarget(target)) {
                    long groupId = chatUIRefactored.getMessageHandler().getGroupId(target);
                    conv = ServiceLocator.conversationDAO().findById(groupId);
                } else {
                    // Private conversation
                    conv = chatUIRefactored.getMessageHandler().findPrivateConversation(fromUser, target);
                }

                if (conv != null) {
                    // Content sẽ được mã hóa trong saveMessage
                    ServiceLocator.messageService().saveMessage(conv, fromUser, content, null);
                }
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
            if (!f.exists()) {
                throw new IOException("File không tồn tại: " + path);
            }
            
            // Đọc dữ liệu file
            byte[] data = Files.readAllBytes(f.toPath());
            
            // Gửi file với đầy đủ tham số
            client.sendFile(convId, f.getName(), data);
        } catch (Exception e) {
            System.out.println("Lỗi khi gửi file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<MessageDTO> getHistory() {
        return messageHistory;
    }

    public void setConversation(Conversation conv) {
        this.currentConversation = conv;
    }

    public Conversation getConversation() {
        return currentConversation;
    }

    public ClientConnection getClient() {
        return client;
    }

    public void setClient(ClientConnection client) {
        this.client = client;
    }

    /**
     * Lấy thumbnail từ cache
     * @param id ID của file cần lấy thumbnail
     * @return byte[] dữ liệu thumbnail hoặc null nếu không có
     */
    public byte[] getThumb(String id){
        return thumbCache.get(id);
    }
    
    /**
     * Lấy CompletableFuture cho thumbnail
     * @param id ID của file
     * @return CompletableFuture sẽ hoàn thành khi thumbnail có sẵn
     */
    public java.util.concurrent.CompletableFuture<byte[]> getThumbFuture(String id) {
        // Nếu đã có thumbnail trong cache, trả về CompletableFuture đã hoàn thành
        byte[] cachedThumb = thumbCache.get(id);
        if (cachedThumb != null) {
            System.out.println("[DEBUG] Thumbnail " + id + " đã có trong cache, trả về ngay lập tức");
            return java.util.concurrent.CompletableFuture.completedFuture(cachedThumb);
        }
        
        // Nếu đã có future đang chờ, trả về future đó
        java.util.concurrent.CompletableFuture<byte[]> existingFuture = thumbFutures.get(id);
        if (existingFuture != null) {
            System.out.println("[DEBUG] Đã có CompletableFuture đang chờ cho thumbnail " + id);
            return existingFuture;
        }
        
        // Tạo future mới và yêu cầu thumbnail
        java.util.concurrent.CompletableFuture<byte[]> newFuture = new java.util.concurrent.CompletableFuture<>();
        thumbFutures.put(id, newFuture);
        System.out.println("[DEBUG] Tạo CompletableFuture mới cho thumbnail " + id);
        
        // Yêu cầu thumbnail nếu chưa yêu cầu
        if (!thumbRequested.contains(id)) {
            System.out.println("[DEBUG] Gửi yêu cầu thumbnail cho " + id);
            requestThumb(id);
        } else {
            System.out.println("[DEBUG] Thumbnail " + id + " đã được yêu cầu trước đó");
        }
        
        return newFuture;
    }
    
    /**
     * Lấy toàn bộ thumbnail cache
     */
    public Map<String, byte[]> getThumbCache() {
        return thumbCache;
    }
    
    /**
     * Thêm thumbnail vào cache và hoàn thành CompletableFuture tương ứng
     * @param id ID của file
     * @param thumbData Dữ liệu thumbnail
     */
    public void addThumbToCache(String id, byte[] thumbData) {
        if (id != null && thumbData != null) {
            // Thêm vào cache
            thumbCache.put(id, thumbData);
            
            // Hoàn thành future nếu có
            java.util.concurrent.CompletableFuture<byte[]> future = thumbFutures.get(id);
            if (future != null && !future.isDone()) {
                future.complete(thumbData);
                System.out.println("[DEBUG] Hoàn thành CompletableFuture cho thumbnail " + id);
            }
            
            // Thông báo cho tất cả các file bubble cập nhật thumbnail
            if (chatUIRefactored != null && chatUIRefactored.getFileHandler() != null) {
                Platform.runLater(() -> {
                    chatUIRefactored.getFileHandler().refreshThumbnail(id);
                });
            }
        }
    }
    
    /**
     * Kiểm tra đã yêu cầu thumbnail chưa
     */
    public boolean isThumbRequested(String id) {
        return thumbRequested.contains(id);
    }

    /**
     * Yêu cầu thumbnail từ server
     * @param id ID của file cần lấy thumbnail
     */
    public void requestThumb(String id) {
        if(!thumbRequested.contains(id)){
            thumbRequested.add(id);
            System.out.println("[INFO] Gửi yêu cầu thumbnail cho ID: " + id);
            client.requestThumb(id);
        } else {
            System.out.println("[INFO] Thumbnail ID: " + id + " đã được yêu cầu trước đó");
        }
    }
}