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

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        this.onPendingListReceived = cb;
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
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            System.out.println("Attempting to reconnect... (Attempt " + reconnectAttempts + ")");
            try {
                Thread.sleep(5000); // Wait 5 seconds before retrying
                connect();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } else {
            System.out.println("Max reconnection attempts reached. Please check your connection.");
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
        // header format: "convId:fileName"
        String header = conversationId + ":" + fileName;
        sendPacket(new Packet(PacketType.FILE, header, data));
    }
    private void sendPacket(Packet p) {
        if (out == null) {
            throw new RuntimeException("Connection not initialized");
        }

        try {
            System.out.println("[SEND] " + p.type() + " header=" + p.header());
            out.writeObject(p);
            out.flush();
        } catch (IOException e) {
            System.out.println("[ERROR] Failed to send packet: " + e.getMessage());
            throw new RuntimeException("Failed to send packet", e);
        }
    }

    private void listen() {
        try {
            while (true) {
                Packet p = (Packet) in.readObject();

                // Debug output for tracking received packets
                System.out.println("[RECV] " + p.type() + " header=" + p.header());

                switch (p.type()) {
                    case MSG -> {
                        String from = p.header();
                        String content = new String(p.payload());
                        if (onTextReceived != null) {
                            onTextReceived.accept(from, content);
                        }
                    }

                    case FILE -> {
                        String[] parts = p.header().split(":", 2);
                        String from = parts[0];
                        String filename = parts[1];
                        if (onFileReceived != null) {
                            onFileReceived.accept(from, filename, p.payload());
                        }
                    }

                    case ACK -> {
                        String header = p.header();
                        System.out.println("[DEBUG] Nhận ACK: " + header);

                        if ("LOGIN_OK".equals(header)) {
                            if (currentUser != null) {
                                // Request pending friend requests after login
                                requestPendingFriendRequests(currentUser);

                                // Request all avatars for online users
                                requestUserList();
                            }

                            if (onLoginSuccess != null) {
                                Platform.runLater(onLoginSuccess);
                            }
                        }
                    }

                    case USRLIST -> {
                        if (onUserListReceived != null) {
                            String userStr = new String(p.payload());
                            System.out.println("[DEBUG] USRLIST payload=" + userStr);

                            String[] arr = userStr.split(",");
                            onUserListReceived.accept(arr);

                            // Request avatars for all online users
                            for (String username : arr) {
                                if (username != null && !username.isBlank() && !username.equals(currentUser)) {
                                    requestAvatar(username);
                                }
                            }
                        }
                    }

                    case AVATAR_DATA -> {
                        String username = p.header();
                        byte[] data = p.payload();

                        // Process avatar data
                        handleNewAvatar(username, data);

                        // Notify UI through callback
                        if (onAvatarUpdated != null) {
                            onAvatarUpdated.accept(username, data);
                        }
                    }

                    case PM -> {
                        String from = p.header();
                        String content = new String(p.payload());
                        if (onPrivateMsgReceived != null) {
                            onPrivateMsgReceived.accept(from, content);
                        }
                    }

                    case CONV_LIST -> {
                        if (onConvList != null) {
                            onConvList.accept(new String(p.payload()));
                        }
                    }

                    case HISTORY -> {
                        String header = p.header();
                        String json = new String(p.payload());

                        try {
                            long cid = Long.parseLong(header);
                            if (onHistory != null) onHistory.accept(cid, json);
                            if (onConvJoined != null) onConvJoined.onJoined(cid);
                        } catch (NumberFormatException ex) {
                            if (onHistoryReceived != null) onHistoryReceived.accept(json);
                        }
                    }

                    case GROUP_MSG -> {
                        long convId = Long.parseLong(p.header());
                        String[] parts = new String(p.payload()).split("\\|", 2);
                        if (onGroupMsg != null && parts.length == 2) {
                            onGroupMsg.accept(convId, parts[0], parts[1]);
                        }
                    }

                    case FILE_META -> {
                        String[] pa = new String(p.payload()).split("\\|");
                        if (onFileMeta != null && pa.length >= 4) {
                            String idFlag = (pa.length >= 5) ? pa[3] + "|" + pa[4] : pa[3];
                            onFileMeta.accept(pa[0], pa[1], Long.parseLong(pa[2]), idFlag);
                        }
                    }

                    case FILE_CHUNK -> {
                        String[] h = p.header().split(":");
                        if (onFileChunk != null && h.length >= 3) {
                            onFileChunk.accept(h[0], Integer.parseInt(h[1]), "1".equals(h[2]), p.payload());
                        }
                    }

                    case FILE_THUMB -> {
                        String id = p.header();
                        if (onFileThumb != null) {
                            onFileThumb.accept(id, p.payload());
                        }
                    }

                    case FRIEND_REQUEST_NOTIFICATION, FRIEND_REQUEST -> {
                        String fromUser = p.header();
                        if (onFriendRequestReceived != null) {
                            onFriendRequestReceived.onFriendRequest(fromUser);
                        }
                    }

                    case FRIEND_REQUEST_ACCEPTED -> {
                        String[] parts = p.header().split("->");
                        if (parts.length == 2 && onFriendRequestAccepted != null) {
                            onFriendRequestAccepted.accept(parts[0], parts[1]);
                        }
                    }

                    case FRIEND_REQUEST_REJECTED -> {
                        String[] parts = p.header().split("->");
                        if (parts.length == 2 && onFriendRequestRejected != null) {
                            onFriendRequestRejected.accept(parts[0], parts[1]);
                        }
                    }

                    case FRIEND_PENDING_LIST -> {
                        if (onPendingListReceived != null) {
                            String json = new String(p.payload());
                            onPendingListReceived.accept(json);
                        }
                    }

                    default -> {
                        System.out.println("[WARN] Unhandled packet type: " + p.type());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Disconnected from server: " + e.getMessage());
            e.printStackTrace();
            handleConnectionError();
        }
    }
    public void requestHistory(String fromUser, String target) {
        if (fromUser == null || target == null) {
            System.out.println("[ERROR] Cannot request history: null parameters");
            return;
        }

        try {
            String header = fromUser + "->" + target;
            System.out.println("[DEBUG] Requesting history: " + header);
            Packet req = new Packet(PacketType.GET_HISTORY, header, null);
            sendPacket(req);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to request history: " + e.getMessage());
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

        // Broadcast notification to all clients that avatar has changed
        // (this is actually handled by the server automatically)

        // Also update local avatar cache immediately
        if (currentUser != null) {
            Path cacheDir = Paths.get("avatar_cache");
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
    public void joinConv(long convId){
        sendPacket(new Packet(PacketType.JOIN_CONV,String.valueOf(convId),null));
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

            // Create avatar cache directory if it doesn't exist
            Path cacheDir = Paths.get("avatar_cache");
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            // Save avatar file
            Path avatarFile = cacheDir.resolve(username + ".png");
            Files.write(avatarFile, data);
            System.out.println("[DEBUG] Saved avatar for " + username + " to " + avatarFile);

            // Update user information
            User user = ServiceLocator.userService().getUser(username);
            if (user != null) {
                // Update avatar info in the User object
                user.setAvatarPath(avatarFile.toString());
                user.setUseDefaultAvatar(false);

                // Update user in database
                ServiceLocator.userService().updateAvatar(username, avatarFile.toFile());

                // Update in our maps
                if (onlineUsers.containsKey(username)) {
                    onlineUsers.put(username, user);
                }

                System.out.println("[DEBUG] Updated avatar for user: " + username);
            } else {
                System.out.println("[WARN] Cannot find user for avatar update: " + username);
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
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            System.out.println("[ERROR] Invalid friend request parameters - from: " + from + ", to: " + to);
            throw new IllegalArgumentException("Invalid friend request parameters");
        }

        if (from.equals(to)) {
            System.out.println("[ERROR] Cannot send friend request to yourself");
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }

        System.out.println("[DEBUG] Sending friend request from " + from + " to " + to);
        String header = from + "->" + to;

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

}

