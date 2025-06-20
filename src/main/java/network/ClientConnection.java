package network;

import app.ServiceLocator;
import app.model.User;
import app.util.Config;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ClientConnection {
    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private static final int SOCKET_TIMEOUT = 300000; // 5 phút
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private int reconnectAttempts = 0;

    // Callback khi nhận MSG(String from, String content)
    private BiConsumer<String, String> onTextReceived;

    // Callback khi nhận FILE(String from, String fileName, byte[] data)
    private TriConsumer<String, String, byte[]> onFileReceived;

    // Callback khi nhận danh sách user
    private ConsumerOfList onUserListReceived;

    private Consumer<String> onHistoryReceived;

    private FriendRequestCallback onFriendRequestReceived;



    // Callback khi nhận private message
    private BiConsumer<String, String> onPrivateMsgReceived;

    private BiConsumer<String,Long> onGroupCreated;   // (groupName, convId)
    private TriConsumer<Long,String,String> onGroupMsg;// convId, from, content

    private Consumer<String> onConvList;                 // JSON
    private BiConsumer<Long,String> onHistory;           // convId, json
    /* trong ClientConnection */
    private OnConvJoinedHandler onConvJoined;

    private FileMetaConsumer onFileMeta;
    private FileChunkConsumer onFileChunk;
    private FileThumbConsumer onFileThumb;     // ↔ setter ở dưới

    private Map<String, User> onlineUsers;
    private String currentUser;
    private ListView<User> listOnlineUsers;

    private Runnable onLoginSuccess;             // đặt cạnh các callback khác
    public  void setOnLoginSuccess(Runnable r){ this.onLoginSuccess = r; }


    private BiConsumer<String, String> onFriendRequestAccepted;
    private BiConsumer<String, String> onFriendRequestRejected;


    /* Callback khi nhận danh sách lời mời (JSON hoặc List<Friendship>) */
    private Consumer<String> onPendingListReceived;   // đơn giản: nhận JSON


    // Thêm callback cho sự kiện nhận avatar mới
    private BiConsumer<String, byte[]> onAvatarUpdated;

    public ClientConnection() {
        this.host = Config.getClientHost();
        this.port = Config.getClientPort();
        this.onlineUsers = new HashMap<>();
    }

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
        this.onlineUsers = new HashMap<>();
    }

    public interface OnConvJoinedHandler {
        void onJoined(long convId);
    }
    public void setOnConvJoined(OnConvJoinedHandler h) {
        this.onConvJoined = h;
    }
    public void setOnPendingListReceived(Consumer<String> cb){
        // Mới: Xử lý JSON trước khi gửi đến callback
        this.onPendingListReceived = json -> {
            try {
                // Kiểm tra xem JSON có hợp lệ không, nếu không sẽ gửi lại chuỗi trống
                if (json == null || json.isEmpty() || "[]".equals(json.trim())) {
                    System.out.println("[DEBUG] Pending requests list is empty");
                    if (cb != null) cb.accept("[]"); // Gửi mảng trống
                    return;
                }
                
                // Log debug
                System.out.println("[DEBUG] Processing pending friend requests JSON: " + 
                    (json.length() > 100 ? json.substring(0, 97) + "..." : json));
                
                // Chuyển tiếp JSON đến callback
                if (cb != null) {
                    cb.accept(json);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Error processing pending requests JSON: " + e.getMessage());
                e.printStackTrace();
                // Gửi một mảng trống trong trường hợp lỗi
                if (cb != null) cb.accept("[]");
            }
        };
    }



    public void setOnFriendRequestAccepted(BiConsumer<String, String> callback) {
        this.onFriendRequestAccepted = callback;
    }

    public void setOnFriendRequestRejected(BiConsumer<String, String> callback) {
        this.onFriendRequestRejected = callback;
    }


    public void connect() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(SOCKET_TIMEOUT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            reconnectAttempts = 0; // Reset reconnect attempts on successful connection
            new Thread(this::listen).start();
        } catch (IOException e) {
            e.printStackTrace();
            handleConnectionError();
        }
    }

    private void handleConnectionError() {
        System.err.println("Connection error: " + (socket == null ? "null socket" : "socket closed"));
        
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            System.out.println("Attempting to reconnect... (Attempt " + reconnectAttempts + ")");
            
            try {
                // Đảm bảo socket cũ đã đóng
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                
                // Đợi một chút trước khi thử kết nối lại
                Thread.sleep(2000); // Đợi 2 giây
                
                // Kết nối lại
                socket = new Socket(host, port);
                socket.setSoTimeout(SOCKET_TIMEOUT);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                
                // Khởi động lại thread lắng nghe
                new Thread(this::listen).start();
                
                // Đăng nhập lại nếu có username
                if (currentUser != null && !currentUser.isBlank()) {
                    login(currentUser);
                    System.out.println("[INFO] Re-logged in as: " + currentUser);
                }
                
                // Reset counter nếu kết nối thành công
                reconnectAttempts = 0;
                
                System.out.println("[INFO] Reconnected successfully");
            } catch (IOException | InterruptedException e) {
                System.err.println("[ERROR] Failed to reconnect: " + e.getMessage());
                
                // Lên lịch thử kết nối lại sau một khoảng thời gian
                new Thread(() -> {
                    try {
                        Thread.sleep(5000); // Đợi 5 giây
                        handleConnectionError(); // Thử lại
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        } else {
            System.err.println("Max reconnection attempts reached. Please restart the application.");
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    public void login(String username) {
        this.currentUser = username;
        // gửi Packet LOGIN
        sendPacket(new Packet(PacketType.LOGIN, username, null));
    }

    // Chat chung (broadcast)
    public void sendText(String text) {
        sendPacket(new Packet(PacketType.MSG, "", text.getBytes()));
    }

    // Chat riêng
    public void sendPrivate(String toUser, String content) {
        if (toUser == null || content == null) {
            System.out.println("[ERROR] Invalid parameters for sendPrivate");
            return;
        }

        try {
            // First ensure the conversation exists
            checkPrivateConversation(toUser);

            // Then send the message with a small delay to make sure the conversation exists
            new Thread(() -> {
                try {
                    // Small delay to ensure conversation is created
                    Thread.sleep(200);
                    sendPacket(new Packet(PacketType.PM, toUser, content.getBytes()));
                    System.out.println("[DEBUG] Private message sent to " + toUser);
                } catch (Exception e) {
                    System.out.println("[ERROR] Failed to send private message: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to prepare private message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Trong ClientConnection
    public void sendFile(long conversationId, String fileName, byte[] data) {
        try {
            // header format: "convId:fileName"
            String header = conversationId + ":" + fileName;
            sendPacket(new Packet(PacketType.FILE, header, data));
            System.out.println("[DEBUG] File sent successfully: " + fileName + " to conversation: " + conversationId);
        } catch (RuntimeException e) {
            System.err.println("[ERROR] Failed to send file: " + e.getMessage());
            handleConnectionError();
        }
    }
    private void sendPacket(Packet p) {
        if (out == null) {
            System.err.println("[ERROR] Connection not initialized");
            throw new RuntimeException("Connection not initialized");
        }

        try {
            System.out.println("[SEND] " + p.type() + " header=" + p.header());
            out.writeObject(p);
            out.flush();
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to send packet: " + e.getMessage());
            // Đánh dấu kết nối bị lỗi để có thể reconnect
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
            
            throw new RuntimeException("Failed to send packet: " + e.getMessage(), e);
        }
    }

    private void listen() {
        try {
            while (socket != null && !socket.isClosed()) {
                try {
                    Packet packet = (Packet) in.readObject();
                    
                    if (packet == null) continue;
                    
                    // Debug output for tracking received packets
                    System.out.println("[RECV] " + packet.type() + " header=" + packet.header());
                    
                    processPacket(packet);
                } catch (ClassNotFoundException e) {
                    System.err.println("[ERROR] Invalid packet format: " + e.getMessage());
                } catch (IOException e) {
                    System.err.println("[ERROR] Connection error while reading: " + e.getMessage());
                    handleConnectionError();
                    break; // Thoát khỏi vòng lặp khi có lỗi kết nối
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Fatal error in listen thread: " + e.getMessage());
            e.printStackTrace();
            handleConnectionError();
        }
    }
    
    private void processPacket(Packet packet) {
        try {
            switch (packet.type()) {
                case ACK -> {
                    String status = packet.header();
                    System.out.println("[ACK] " + status);

                    if (status.equals("LOGIN_OK")) {
                        // Đặt tên người dùng nếu cần
                        if (currentUser == null || currentUser.isBlank()) {
                            System.out.println("[WARN] currentUser null in LOGIN_SUCCESS callback");
                        }

                        if (onLoginSuccess != null) {
                            Platform.runLater(onLoginSuccess);
                        }
                    }

                    // Handle friendship status
                    if (status.startsWith("FRIEND_REQUEST_")) {
                        System.out.println("[ACK] Friend request status: " + status);
                    }

                    if (status.startsWith("FRIEND_ACCEPT_")) {
                        if (status.equals("FRIEND_ACCEPT_SUCCESS")) {
                            System.out.println("[ACK] Friend request accepted successfully");
                        } else {
                            System.out.println("[ACK] Friend request accept failed: " + status);
                        }
                    }
                }

                case MSG -> {
                    String sender = packet.header();
                    String content = new String(packet.payload());

                    // Debug để phát hiện các vấn đề với username
                    if (sender == null || sender.isBlank()) {
                        System.err.println("[ERROR] Received MSG packet with null/empty sender");
                    }
                    
                    if (currentUser == null || currentUser.isBlank()) {
                        System.err.println("[ERROR] currentUser is null when processing MSG packet from " + sender);
                    }

                    if (onTextReceived != null) {
                        onTextReceived.accept(sender, content);
                    }
                }

                case FILE -> {
                    String[] parts = packet.header().split(":", 2);
                    String from = parts[0];
                    String filename = parts[1];
                    if (onFileReceived != null) {
                        onFileReceived.accept(from, filename, packet.payload());
                    }
                }

                case FILE_META -> {
                    String[] hdr = packet.header().split(":");
                    long convId = Long.parseLong(hdr[0]);
                    String parts = new String(packet.payload());
                    String[] arr  = parts.split("\\|");
                    String from   = arr[0];
                    String name   = arr[1];
                    long   size   = Long.parseLong(arr[2]);
                    String id     = arr[3];
                    String flag   = arr.length>4 ? arr[4] : "N";
                    if(onFileMeta != null)
                        onFileMeta.accept(from, name, size, id+"|"+flag);
                }

                case FILE_CHUNK -> {
                    String[] hdr = packet.header().split(":");
                    String id  = hdr[0];
                    int seq    = Integer.parseInt(hdr[1]);
                    boolean last = "1".equals(hdr[2]);
                    if(onFileChunk != null)
                        onFileChunk.accept(id, seq, last, packet.payload());
                }

                case FILE_THUMB -> {
                    String id = packet.header();
                    if(onFileThumb!=null)
                        onFileThumb.accept(id, packet.payload());
                }

                case USRLIST -> {
                    String userStr = new String(packet.payload());
                    System.out.println("[DEBUG] USRLIST payload=" + userStr);
                    String[] users = userStr.split(",");

                    if (onUserListReceived != null) {
                        onUserListReceived.accept(users);
                    }

                    // Request avatars for all online users
                    for (String username : users) {
                        if (username != null && !username.isBlank() && !username.equals(currentUser)) {
                            requestAvatar(username);
                        }
                    }
                }

                case PM -> {
                    String from = packet.header();
                    String content = new String(packet.payload());

                    if (onPrivateMsgReceived != null) {
                        onPrivateMsgReceived.accept(from, content);
                    }
                }

                case HISTORY -> {
                    String header = packet.header();
                    String json = new String(packet.payload());

                    try {
                        long cid = Long.parseLong(header);
                        if (onHistory != null) onHistory.accept(cid, json);
                        if (onConvJoined != null) onConvJoined.onJoined(cid);
                    } catch (NumberFormatException ex) {
                        if (onHistoryReceived != null) onHistoryReceived.accept(json);
                    }
                }

                case CONV_LIST -> {
                    String json = new String(packet.payload());
                    if(onConvList!=null) onConvList.accept(json);
                }

                case GROUP_MSG -> {
                    long convId = Long.parseLong(packet.header());
                    String[] parts = new String(packet.payload()).split("\\|", 2);
                    if (onGroupMsg != null && parts.length == 2) {
                        onGroupMsg.accept(convId, parts[0], parts[1]);
                    }
                }

                case FRIEND_REQUEST, FRIEND_REQUEST_NOTIFICATION -> {
                    String fromUser = packet.header();
                    if (onFriendRequestReceived != null) {
                        onFriendRequestReceived.onFriendRequest(fromUser);
                    }
                }

                case FRIEND_PENDING_LIST -> {
                    String jsonData = new String(packet.payload());
                    if (onPendingListReceived != null) {
                        onPendingListReceived.accept(jsonData);
                    }
                }

                case GROUP_CREATED -> {
                    String[] parts = packet.header().split(":");
                    
                    // Kiểm tra mảng parts có đủ phần tử không để tránh IndexOutOfBoundsException
                    if (parts.length < 2) {
                        System.err.println("[ERROR] Invalid GROUP_CREATED header format: " + packet.header());
                        return;
                    }
                    
                    try {
                        String groupName = parts[0];
                        long groupId = Long.parseLong(parts[1]);
                        System.out.println("[DEBUG] Received GROUP_CREATED: " + groupName + " with ID: " + groupId);
                        
                        if (onGroupCreated != null) {
                            onGroupCreated.accept(groupName, groupId);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] Invalid group ID format in header: " + packet.header());
                        e.printStackTrace();
                    }
                }

                case AVATAR_DATA -> {
                    String username = packet.header();
                    if (onAvatarUpdated != null) {
                        handleNewAvatar(username, packet.payload());
                        onAvatarUpdated.accept(username, packet.payload());
                    } else {
                        // Vẫn lưu avatar ngay cả khi không có callback
                        handleNewAvatar(username, packet.payload());
                    }
                }

                case FRIEND_ACCEPT -> {
                    String[] parts = packet.header().split("->");
                    if (parts.length == 2 && onFriendRequestAccepted != null) {
                        onFriendRequestAccepted.accept(parts[0], parts[1]);
                    }
                }

                case FRIEND_REJECT -> {
                    String[] parts = packet.header().split("->");
                    if (parts.length == 2 && onFriendRequestRejected != null) {
                        onFriendRequestRejected.accept(parts[0], parts[1]);
                    }
                }

                default -> System.out.println("[WARN] Unhandled packet type: " + packet.type());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error processing packet: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Yêu cầu lịch sử tin nhắn của một cuộc trò chuyện
     * @param fromUser Người dùng yêu cầu
     * @param target Đích (tên người dùng hoặc nhóm)
     */
    public void requestHistory(String fromUser, String target) {
        if (fromUser == null || target == null) {
            System.err.println("[ERROR] Cannot request history: null parameters");
            return;
        }

        try {
            String header = fromUser + "->" + target;
            System.out.println("[DEBUG] Requesting history: " + header);
            Packet req = new Packet(PacketType.GET_HISTORY, header, null);
            sendPacket(req);
            System.out.println("[DEBUG] History request sent for: " + target);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to request history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void requestThumb(String id){
        sendPacket(new Packet(PacketType.GET_THUMB, id, null));
    }

    public void createGroup(String groupName, List<String> members){
        String payload = String.join(",", members);      // "bob,nam,trang"
        sendPacket(new Packet(PacketType.CREATE_GROUP, groupName, payload.getBytes()));
    }

    public void sendGroup(long convId, String text){
        sendPacket(new Packet(PacketType.GROUP_MSG, String.valueOf(convId), text.getBytes()));
    }

    /* gửi avatar mới */
    public void uploadAvatar(File f) throws IOException {
        if (f == null || !f.exists()) {
            throw new IOException("Avatar file does not exist");
        }

        System.out.println("[DEBUG] Uploading avatar: " + f.getAbsolutePath());
        byte[] data = Files.readAllBytes(f.toPath());

        // Send to server
        sendPacket(new Packet(PacketType.AVATAR_UPLOAD, f.getName(), data));

        // Also update local avatar cache immediately
        if (currentUser != null) {
            Path cacheDir = Paths.get(app.util.PathUtil.getAvatarCacheDirectory());
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            Path avatarFile = cacheDir.resolve(currentUser + ".png");
            Files.write(avatarFile, data);

            System.out.println("[DEBUG] Updated local avatar cache for current user");

            // Update the User object
            User user = ServiceLocator.userService().getUser(currentUser);
            if (user != null) {
                user.setAvatarPath(avatarFile.toString());
                user.setUseDefaultAvatar(false);
            }
        }
    }
    /* xin avatar của 1 user khác */
    public void requestAvatar(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        System.out.println("[DEBUG] Requesting avatar for: " + username);
        sendPacket(new Packet(PacketType.GET_AVATAR, username, null));
    }

    // Client yêu cầu server stream 1 file
    public void requestFile(String id){
        sendPacket(new Packet(PacketType.GET_FILE, id, null));
    }


    // setter callback
    public void setOnTextReceived(BiConsumer<String, String> cb) {
        this.onTextReceived = cb;
    }
    public void setOnFileReceived(TriConsumer<String, String, byte[]> cb) {
        this.onFileReceived = cb;
    }
    public void setOnUserListReceived(ConsumerOfList cb) {
        this.onUserListReceived = cb;
    }
    public void setOnPrivateMsgReceived(BiConsumer<String, String> cb) {
        this.onPrivateMsgReceived = cb;
    }

    public void setOnHistoryReceived(Consumer<String> cb) {
        this.onHistoryReceived = cb;
    }

    public void setOnGroupCreated(BiConsumer<String,Long> cb){ this.onGroupCreated = cb; }
    public void setOnGroupMsg(TriConsumer<Long,String,String> cb){ this.onGroupMsg  = cb;}

    public void setOnConvList(Consumer<String> cb){ this.onConvList = cb; }
    public void setOnHistory(BiConsumer<Long,String> cb){ this.onHistory = cb; }
    
    /**
     * Tham gia vào một cuộc trò chuyện
     * @param convId ID của cuộc trò chuyện
     */
    public void joinConv(long convId){
        try {
            System.out.println("[DEBUG] Joining conversation with ID: " + convId);
            if (convId <= 0) {
                System.err.println("[ERROR] Invalid conversation ID: " + convId);
                return;
            }
            
            sendPacket(new Packet(PacketType.JOIN_CONV, String.valueOf(convId), null));
            System.out.println("[DEBUG] JOIN_CONV packet sent for conversation ID: " + convId);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to join conversation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setOnFileMeta(FileMetaConsumer c){ this.onFileMeta = c; }
    public void setOnFileChunk(FileChunkConsumer c){ this.onFileChunk = c; }
    public void setOnFileThumb(FileThumbConsumer c){ this.onFileThumb = c; }

    public void setOnlineUsers(Map<String, User> onlineUsers) {
        this.onlineUsers = onlineUsers;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }

    public void setListOnlineUsers(ListView<User> listOnlineUsers) {
        this.listOnlineUsers = listOnlineUsers;
    }

    private void updateUserAvatar() {
        if (currentUser != null && onlineUsers.containsKey(currentUser)) {
            User currentUserObj = onlineUsers.get(currentUser);
            Path avatarPath = Paths.get(currentUserObj.getAvatarPath());
            if (Files.exists(avatarPath)) {
                try {
                    // Đọc file avatar thành byte[]
                    byte[] avatarData = Files.readAllBytes(avatarPath);

                    // Gọi callback để cập nhật UI
                    if (onAvatarUpdated != null) {
                        onAvatarUpdated.accept(currentUser, avatarData);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setOnAvatarUpdated(BiConsumer<String, byte[]> callback) {
        this.onAvatarUpdated = callback;
    }
    public void setOnFriendRequestReceived(FriendRequestCallback callback) {
        this.onFriendRequestReceived = callback;
    }
    // Phương thức để yêu cầu avatar của tất cả người dùng online
    public void requestAllAvatars() {
        System.out.println("[DEBUG] Requesting avatars for all online users");

        // Request user list first
        requestUserList();

        // Request avatar for current user
        if (currentUser != null) {
            requestAvatar(currentUser);
        }
    }

    // Phương thức để xử lý khi nhận được avatar mới
    private void handleNewAvatar(String username, byte[] data) {
        try {
            System.out.println("[DEBUG] Processing avatar for " + username + ", size: " + data.length + " bytes");

            // Kiểm tra thời gian cập nhật gần nhất
            File cacheFile = new File(app.util.PathUtil.getAvatarCacheDirectory(), username + ".png");
            boolean shouldUpdate = true;
            
            // Nếu file cache đã tồn tại và được cập nhật trong vòng 1 phút, bỏ qua
            if (cacheFile.exists()) {
                long lastModified = cacheFile.lastModified();
                long currentTime = System.currentTimeMillis();
                
                if (currentTime - lastModified < 60000) { // 1 phút
                    System.out.println("[DEBUG] User " + username + " avatar was recently updated, skipping");
                    shouldUpdate = false;
                }
            }
            
            if (shouldUpdate) {
                // Sử dụng utility để lưu avatar
                boolean success = app.util.AvatarFixUtil.saveAvatarFromBytes(username, data);
                
                if (success) {
                    System.out.println("[DEBUG] Successfully updated avatar for " + username);
                    
                    // Cập nhật user trong maps nếu cần
                    if (onlineUsers.containsKey(username)) {
                        User user = ServiceLocator.userService().getUser(username);
                        if (user != null) {
                            onlineUsers.put(username, user);
                        }
                    }
                } else {
                    System.out.println("[WARN] Failed to update avatar for " + username);
                }
            }
            
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to handle avatar: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Define TriConsumer
    @FunctionalInterface
    public interface TriConsumer<A,B,C> {
        void accept(A a, B b, C c);
    }

    // Define ConsumerOfList
    @FunctionalInterface
    public interface ConsumerOfList {
        void accept(String[] users);
    }
    @FunctionalInterface
    public interface FriendRequestCallback {
        void onFriendRequest(String fromUser);
    }

    @FunctionalInterface
    public interface FileMetaConsumer { void accept(String from,String name,long size,String id); }
    @FunctionalInterface
    public interface FileChunkConsumer{ void accept(String id,int seq,boolean last,byte[] data); }
    @FunctionalInterface
    public interface FileThumbConsumer{
        void accept(String id, byte[] data);
    }

    public void requestUserList() {
        sendPacket(new Packet(PacketType.GET_USERLIST, "", null));
    }

    public void sendFriendRequest(String from, String to) {
        String actualFrom = from;
        
        // Nếu from là null, thử dùng currentUser thay thế
        if (actualFrom == null || actualFrom.isBlank()) {
            if (currentUser != null && !currentUser.isBlank()) {
                actualFrom = currentUser;
                System.out.println("[INFO] Using currentUser '" + currentUser + "' instead of null 'from' parameter");
            } else {
                System.out.println("[ERROR] Invalid friend request parameters - from: " + from + ", to: " + to + ", currentUser is also null");
                throw new IllegalArgumentException("Invalid friend request parameters - sender is null");
            }
        }
        
        // Kiểm tra tham số to
        if (to == null || to.isBlank()) {
            System.out.println("[ERROR] Invalid friend request parameters - to: " + to);
            throw new IllegalArgumentException("Invalid friend request parameters - recipient is null");
        }

        if (actualFrom.equals(to)) {
            System.out.println("[ERROR] Cannot send friend request to yourself");
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }

        System.out.println("[DEBUG] Sending friend request from " + actualFrom + " to " + to);
        String header = actualFrom + "->" + to;

        try {
            sendPacket(new Packet(PacketType.FRIEND_REQUEST, header, null));
            System.out.println("[DEBUG] Friend request packet sent successfully");
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to send friend request packet: " + e.getMessage());
            throw new RuntimeException("Failed to send friend request", e);
        }
    }
    public void acceptFriendRequest(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            throw new IllegalArgumentException("Invalid accept request parameters");
        }

        String header = from + "->" + to;
        System.out.println("[DEBUG] Accepting friend request: " + header);

        try {
            sendPacket(new Packet(PacketType.FRIEND_ACCEPT, header, null));
            System.out.println("[DEBUG] Friend accept packet sent successfully");
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to send friend accept packet: " + e.getMessage());
            throw new RuntimeException("Failed to accept friend request", e);
        }
    }
    public void rejectFriendRequest(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            throw new IllegalArgumentException("Invalid reject request parameters");
        }

        String header = from + "->" + to;
        System.out.println("[DEBUG] Rejecting friend request: " + header);

        try {
            sendPacket(new Packet(PacketType.FRIEND_REJECT, header, null));
            System.out.println("[DEBUG] Friend reject packet sent successfully");
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to send friend reject packet: " + e.getMessage());
            throw new RuntimeException("Failed to reject friend request", e);
        }
    }
    public void requestPendingFriendRequests(String username) {
        if (username == null || username.isBlank()) {
            System.out.println("[ERROR] Invalid username for pending requests");
            return;
        }

        System.out.println("[DEBUG] Requesting pending friend requests for: " + username);
        sendPacket(new Packet(PacketType.FRIEND_PENDING_LIST, username, null));
    }
    public void checkPrivateConversation(String otherUser) {
        if (otherUser == null || currentUser == null) {
            System.out.println("[ERROR] Cannot check conversation: null username");
            return;
        }

        try {
            // Use the history request to implicitly create the conversation if it doesn't exist
            String header = currentUser + "->" + otherUser;
            System.out.println("[DEBUG] Ensuring conversation exists: " + header);
            Packet req = new Packet(PacketType.GET_HISTORY, header, null);
            sendPacket(req);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to check private conversation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Yêu cầu danh sách các cuộc trò chuyện
     */
    public void requestConversationList() {
        try {
            System.out.println("[SEND] Yêu cầu danh sách cuộc trò chuyện");
            sendPacket(new Packet(PacketType.GET_CONV_LIST, "", new byte[0]));
        } catch (Exception e) {
            System.err.println("Lỗi khi yêu cầu danh sách cuộc trò chuyện: " + e.getMessage());
        }
    }

}

