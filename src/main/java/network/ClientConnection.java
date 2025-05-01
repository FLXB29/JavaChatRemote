package network;

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
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private int reconnectAttempts = 0;

    // Callback khi nhận MSG(String from, String content)
    private BiConsumer<String, String> onTextReceived;

    // Callback khi nhận FILE(String from, String fileName, byte[] data)
    private TriConsumer<String, String, byte[]> onFileReceived;

    // Callback khi nhận danh sách user
    private ConsumerOfList onUserListReceived;

    private Consumer<String> onHistoryReceived;


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
        // gửi Packet LOGIN
        sendPacket(new Packet(PacketType.LOGIN, username, null));
    }

    // Chat chung (broadcast)
    public void sendText(String text) {
        sendPacket(new Packet(PacketType.MSG, "", text.getBytes()));
    }

    // Chat riêng
    public void sendPrivate(String toUser, String content) {
        // PM: header = toUser, payload = content
        sendPacket(new Packet(PacketType.PM, toUser, content.getBytes()));
    }

    // Trong ClientConnection
    public void sendFile(long conversationId, String fileName, byte[] data) {
        // header format: "convId:fileName"
        String header = conversationId + ":" + fileName;
        sendPacket(new Packet(PacketType.FILE, header, data));
    }
    private void sendPacket(Packet p) {
        try {
            out.writeObject(p);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listen() {
        try {
            while(true) {
                Packet p = (Packet) in.readObject();
                switch(p.type()) {
                    case MSG -> {
                        // p.header() = from, p.payload() = text
                        String from = p.header();
                        String content = new String(p.payload());
                        if(onTextReceived != null) {
                            onTextReceived.accept(from, content);
                        }
                    }
                    case FILE -> {
                        // p.header() = from + ":" + fileName
                        String[] parts = p.header().split(":", 2);
                        String from = parts[0];
                        String filename = parts[1];
                        if(onFileReceived != null) {
                            onFileReceived.accept(from, filename, p.payload());
                        }
                    }
                    case ACK -> {
                        System.out.println("ACK: " + p.header());
                    }
                    case USRLIST -> {
                        // p.header() = "ONLINE_USERS"
                        // p.payload() = chuỗi "alice,bob,nam"
                        if (onUserListReceived != null) {
                            String userStr = new String(p.payload());
                            System.out.println("[DEBUG] USRLIST payload=" + userStr);   // <── thêm

                            String[] arr = userStr.split(",");
                            onUserListReceived.accept(arr);
                        }
                    }
                    case PM -> {
                        // p.header() = fromUser, p.payload() = content
                        String from = p.header();
                        String content = new String(p.payload());
                        if (onPrivateMsgReceived != null) {
                            onPrivateMsgReceived.accept(from, content);
                        }
                    }

                    case CONV_LIST -> {
                        if(onConvList!=null) onConvList.accept(new String(p.payload()));
                    }
                    case HISTORY -> {
                        String header = p.header();
                        String json   = new String(p.payload());

                        try {                               // header = convId
                            long cid = Long.parseLong(header);
                            if(onHistory != null) onHistory.accept(cid, json);
                            // xem HISTORY như Join‑OK
                            if(onConvJoined != null) onConvJoined.onJoined(cid);   // ← thêm dòng này
                        } catch (NumberFormatException ex) { // phòng server cũ
                            if(onHistoryReceived != null) onHistoryReceived.accept(json);
                        }
                    }



                    case GROUP_MSG -> {
                        long convId = Long.parseLong(p.header());
                        // payload format: "from|content"  (đơn giản)
                        String[] parts = new String(p.payload()).split("\\|",2);
                        if(onGroupMsg != null){
                            onGroupMsg.accept(convId, parts[0], parts[1]);
                        }
                    }
                    case FILE_META -> {
                        // payload: from|fileName|size|fileId|flag
                        String[] pa = new String(p.payload()).split("\\|");
                        if(onFileMeta != null && pa.length >= 4){
                            /* gộp fileId và flag lại thành 1 chuỗi "id|T" hoặc "id|N" */
                            String idFlag = (pa.length >= 5) ? pa[3] + "|" + pa[4]  // server mới
                                    : pa[3];              // phòng server cũ
                            onFileMeta.accept(pa[0],            // from
                                    pa[1],            // fileName
                                    Long.parseLong(pa[2]),   // size
                                    idFlag);         // "id|flag"
                        }
                    }


                    case FILE_CHUNK -> {
                        String[] h = p.header().split(":");
                        if(onFileChunk != null)
                            onFileChunk.accept(h[0], Integer.parseInt(h[1]),
                                    "1".equals(h[2]), p.payload());
                    }
                    case FILE_THUMB -> {
                        String id   = p.header();
                        if(onFileThumb!=null)
                            onFileThumb.accept(id, p.payload());
                    }

                    case AVATAR_DATA -> {
                        String username = p.header();
                        byte[] data = p.payload();
                        
                        // Lưu avatar vào cache
                        Path cacheDir = Paths.get("avatar_cache");
                        if(!Files.exists(cacheDir)) Files.createDirectories(cacheDir);
                        
                        Path file = cacheDir.resolve(username + ".png");
                        Files.write(file, data);

                        // Thông báo cho UI cập nhật qua callback
                        if(onAvatarUpdated != null) {
                            onAvatarUpdated.accept(username, data);
                        }
                    }

                    default -> {}
                }
            }
        } catch (Exception e) {
            System.out.println("Disconnected from server: " + e.getMessage());
            handleConnectionError();
        }
    }

    public void requestHistory(String fromUser, String target) {
        if (fromUser == null || target == null) {
            System.out.println("Lỗi: fromUser hoặc target là null (fromUser=" + fromUser + ", target=" + target + ")");
            return;
        }
        String header = fromUser + "->" + target;
        System.out.println("Gửi GET_HISTORY: " + header);
        Packet req = new Packet(PacketType.GET_HISTORY, header, null);
        sendPacket(req);
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
        byte[] data = Files.readAllBytes(f.toPath());
        sendPacket(new Packet(PacketType.AVATAR_UPLOAD, f.getName(), data));
    }

    /* xin avatar của 1 user khác */
    public void requestAvatar(String username){
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

    // Phương thức để yêu cầu avatar của tất cả người dùng online
    public void requestAllAvatars() {
        if (onlineUsers != null) {
            for (String username : onlineUsers.keySet()) {
                if (!username.equals(currentUser)) {
                    requestAvatar(username);
                }
            }
        }
    }

    // Phương thức để xử lý khi nhận được avatar mới
    private void handleNewAvatar(String username, byte[] data) {
        try {
            Path cacheDir = Paths.get("avatar_cache");
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            Path avatarFile = cacheDir.resolve(username + ".png");
            Files.write(avatarFile, data);

            Platform.runLater(() -> {
                User user = onlineUsers.get(username);
                if (user != null) {
                    user.setAvatarPath(avatarFile.toString());
                    user.setUseDefaultAvatar(false);
                    if (listOnlineUsers != null) {
                        listOnlineUsers.refresh();
                    }
                }
                if (username.equals(currentUser)) {
                    updateUserAvatar();
                }
            });
        } catch (IOException e) {
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
}
