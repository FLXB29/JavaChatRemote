package network;

import app.LocalDateTimeAdapter;
import app.dao.*;
import app.model.*;
import app.service.GroupMessageService;
import app.util.Config;
import app.util.DatabaseEncryptionUtil;
//import app.util.DatabaseKeyManager;
import app.util.HibernateUtil;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import app.ServiceLocator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ServerApp: Lắng nghe client, xử lý login / gửi tin nhắn,
 * và lưu tin nhắn vào DB (ở server).
 */
public class ServerApp {
    private ServerSocket server;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // =========== CÁC DAO & Conversation để lưu tin ===========
    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();
    private final ConversationDAO conversationDAO = new ConversationDAO();
    private final MembershipDAO membershipDAO = new MembershipDAO();
    private final FileAttachmentDAO fileAttachmentDAO = new FileAttachmentDAO();

    private AnnotationConfigApplicationContext ctx;
    private GroupMessageService groupSvc;          // ⭐
    // Mặc định ta sẽ có 1 conversation "Global Chat" để lưu tất cả MSG
    private Conversation globalConv;

    public ServerApp() {
        // Khởi tạo ServiceLocator nếu chưa được khởi tạo
        if (ServiceLocator.userService() == null) {
            ServiceLocator.init(true);
        }
    }

    public ServerApp(int port) throws IOException {
        // Khởi tạo ServiceLocator nếu chưa được khởi tạo
        if (ServiceLocator.userService() == null) {
            ServiceLocator.init(true);
        }
        this.server = new ServerSocket(port);
    }

    public void start() {
        start(Config.getServerPort());
    }

    public void start(int port) {
        try {
            // 0 ▪ Khởi Spring
            ctx = new AnnotationConfigApplicationContext(app.config.AppConfig.class);
            groupSvc = ctx.getBean(GroupMessageService.class);
            groupSvc.setServer(this);           // <─ truyền chính ServerApp hiện tại

            // Khởi tạo các service cần thiết
            if (ServiceLocator.userService() == null) {
                System.out.println("[ERROR] UserService chưa được khởi tạo!");
                return;
            }
            // Khởi tạo DatabaseKeyManager


            if (server == null) {
                server = new ServerSocket(port);
            }
            System.out.println("Server listening on port " + port);

            // Tìm hoặc tạo Global Chat
            globalConv = conversationDAO.findByName("Global Chat");
            if (globalConv == null) {
                globalConv = new Conversation("GROUP", "Global Chat");
                conversationDAO.save(globalConv);
                System.out.println("Tạo conversation 'Global Chat' trong DB");
            }

            while (true) {
                Socket sock = server.accept();
                System.out.println("Client connected: " + sock);
                ClientHandler handler = new ClientHandler(sock);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gửi Packet tới 1 client cụ thể (theo username).
     */
    public void sendToUser(String username, Packet pkt) {
        ClientHandler ch = clients.get(username);
        if (ch != null) {
            ch.sendPacket(pkt);
        }
    }

    /**
     * Broadcast tới TẤT CẢ client (kể cả người gửi).
     */
    public void broadcast(Packet pkt) {
        for (ClientHandler ch : clients.values()) {
            ch.sendPacket(pkt);
        }
    }

    /**
     * Broadcast tới mọi client, TRỪ client "exclude" (người gửi).
     */
    public void broadcastExclude(Packet pkt, ClientHandler exclude) {
        for (ClientHandler ch : clients.values()) {
            if (ch != exclude) {
                ch.sendPacket(pkt);
            }
        }
    }

    /**
     * Gửi danh sách user online cho tất cả client.
     */
    private void broadcastUserList() {
        Set<String> userSet = clients.keySet();
        System.out.println("[DEBUG] online=" + userSet);   // <── thêm log

        String userStr = String.join(",", userSet);
        Packet pkt = new Packet(PacketType.USRLIST, "ONLINE_USERS", userStr.getBytes());
        broadcast(pkt);
    }

    // ============================
    // Lớp xử lý 1 client
    // ============================
    class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String username; // tên user (đã đăng nhập)

        public ClientHandler(Socket s) {
            this.socket = s;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in  = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    Packet pkt = (Packet) in.readObject();
                    switch (pkt.type()) {
                        case LOGIN -> {
                            String loginUsername = pkt.header();
                            if (loginUsername == null || loginUsername.isBlank()) {
                                System.out.println("Lỗi: username không hợp lệ khi login");
                                sendPacket(new Packet(PacketType.ACK, "LOGIN_FAIL: Invalid username", null));
                                break;
                            }

                            this.username = loginUsername;
                            System.out.println("User login: " + username);

                            // Thêm vào map clients
                            clients.put(username, this);

                            // Gửi phản hồi "ACK"
                            sendPacket(new Packet(PacketType.ACK, "LOGIN_OK", null));

                            // *** THÊM: Auto-send pending friend requests ***
                            sendPendingFriendRequestsOnLogin(username);

                            // Gửi conversation list
                            sendConvList(username);

                            // Gửi/broadcast danh sách user online
                            broadcastUserList();
                        }
                        case MSG -> {
                            if (username == null) {
                                System.out.println("Lỗi: username là null khi xử lý MSG");
                                break;
                            }
                            String from = username;
                            String txt = new String(pkt.payload());
                            System.out.println("MSG from " + from + ": " + txt);

                            // 1) Lưu DB với mã hóa
                            saveMessageToDb(from, txt, globalConv);

                            // 2) Gửi CHO TẤT CẢ content gốc (chưa mã hóa)
                            broadcast(new Packet(PacketType.MSG, from, pkt.payload()));
                        }
                        case FILE -> {
                            /* ──1. Giải mã header── */
                            String[] h = pkt.header().split(":",2);
                            long   convId   = Long.parseLong(h[0]);
                            String fileName = h[1];
                            byte[] data     = pkt.payload();

                            /* ──2. Tính SHA-256── */
                            String hash = HexFormat.of()
                                    .formatHex(MessageDigest.getInstance("SHA-256").digest(data));

                            /* ──3. Tìm file theo hash── */
                            FileAttachment fa = fileAttachmentDAO.findByHash(hash);
                            if(fa == null){                               // → file thật sự mới
                                /* 3.1 lưu vật lý */
                                String dir = "uploads/"+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                                Files.createDirectories(Path.of(dir));
                                String unique = System.currentTimeMillis()+"_"+fileName;
                                Path   full   = Path.of(dir,unique);
                                Files.write(full, data);

                                /* 3.2 tạo FileAttachment */
                                fa = new FileAttachment(fileName, full.toString(),
                                        Files.probeContentType(full), data.length, hash);

                                /* 3.3  nếu là ảnh → sinh thumbnail */
                                boolean isImg = fileName.matches("(?i).+\\.(png|jpe?g|gif)");   // ✔ dựa vào tên
                                if(isImg){
                                    /* DEBUG */
                                    System.out.println("[THUMB] creating for "+fileName+" size="+data.length);

                                    BufferedImage th = Thumbnails
                                            .of(new ByteArrayInputStream(data))  // không dùng ImageIO.read
                                            .size(400,400).outputQuality(0.8)
                                            .asBufferedImage();

                                    /* Lấy phần mở rộng từ tên file gốc */
                                    String extension = fileName.substring(fileName.lastIndexOf("."));
                                    String tn = "thumb_" + System.currentTimeMillis() + extension;
                                    Path  tp  = Path.of(dir, tn);         // uploads/2025/04/28/thumb_<ts>.<extension>
                                    ImageIO.write(th, extension.substring(1), tp.toFile());

                                    fa.setThumbPath(tp.toString());
                                }

                                fileAttachmentDAO.save(fa);
                            }

                            /* ──4. Lưu Message── */
                            Conversation conv = conversationDAO.findByIdWithMembers(convId);
                            User sender       = userDAO.findByUsername(username);

                            String content = "[FILE]"+fileName+"|"+data.length+"|"+fa.getId();
                            Message msg = new Message(conv, sender, content, fa);
                            messageDAO.save(msg);

                            /* ──5. Broadcast META── */
                            String flag = fa.getThumbPath()!=null ? "T" : "N";
                            String meta = String.join("|", sender.getUsername(), fileName,
                                    String.valueOf(data.length), fa.getId(), flag);
                            Packet metaPkt = new Packet(PacketType.FILE_META, String.valueOf(convId), meta.getBytes());

                            if (conv.getMemberships().isEmpty()) {
                                /* conversation chưa có danh sách thành viên
                                (ví dụ "Global Chat") → phát cho tất cả   */
                                broadcast(metaPkt);                               // ★ thay vì loop
                            } else {
                                /* nhóm / PM đã có memberships → chỉ gửi cho thành viên */
                                for (var mb : conv.getMemberships())
                                    sendToUser(mb.getUser().getUsername(), metaPkt);
                            }
                            System.out.println("[META] flag="+flag+" id="+fa.getId());


                        }





                        case GET_FILE -> {
                            String fileId = pkt.header();               // header = id
                            FileAttachment att = fileAttachmentDAO.findById(fileId);
                            if(att == null) break;

                            try(InputStream in = Files.newInputStream(Paths.get(att.getStoragePath()))){
                                byte[] buf = new byte[32*1024];
                                int seq = 0, n;
                                while((n = in.read(buf)) != -1){
                                    boolean last = (in.available()==0);
                                    byte[] slice = Arrays.copyOf(buf, n);
                                    String h = fileId + ":" + seq++ + ":" + (last?1:0);
                                    Packet chunk = new Packet(PacketType.FILE_CHUNK, h, slice);
                                    sendPacket(chunk);                   // chỉ gửi cho requester
                                }
                            }
                        }


                        case PM -> {
                            String toUser = pkt.header();
                            String content = new String(pkt.payload());
                            String from = username;

                            // 1) Tìm hoặc tạo Conversation riêng
                            Conversation conv = findOrCreatePrivateConversation(from, toUser);

                            // 2) Lưu DB với mã hóa
                            saveMessageToDb(from, content, conv);

                            // 3) Gửi content gốc (chưa mã hóa) cho người nhận
                            sendToUser(toUser, new Packet(PacketType.PM, from, content.getBytes()));
                            sendToUser(from, new Packet(PacketType.PM, from, content.getBytes()));
                        }

                        case GET_HISTORY -> {
                            String header = pkt.header();
                            System.out.println("GET_HISTORY header=" + header);
                            try {
                                String[] parts = header.split("->");
                                if (parts.length != 2) {
                                    System.out.println("Lỗi: Header GET_HISTORY không hợp lệ: " + header);
                                    break;
                                }
                                String fromUser = parts[0];
                                String target = parts[1];

                                Conversation conv;
                                if (target.equals("Global")) {
                                    conv = globalConv;
                                } else {
                                    conv = findOrCreatePrivateConversation(fromUser, target);
                                }

                                if (conv == null) {
                                    System.out.println("Lỗi: Không tìm thấy conversation");
                                    break;
                                }

                                List<Message> messages = messageDAO.findByConversationId(conv.getId());

                                // SỬ DỤNG convertMessagesToJson để giải mã
                                String json = convertMessagesToJson(messages);
                                Packet resp = new Packet(PacketType.HISTORY, String.valueOf(conv.getId()), json.getBytes());
                                sendToUser(fromUser, resp);

                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Lỗi xử lý GET_HISTORY: " + e.getMessage());
                            }
                        }                        case JOIN_CONV -> {
                            long convId = Long.parseLong(pkt.header());
                            Conversation conv = conversationDAO.findById(convId);
                            if(conv == null) break;

                            List<Message> msg = messageDAO.findByConversationId(convId);
                            String json = convertMessagesToJson(msg);
                            sendToUser(username,
                                    new Packet(PacketType.HISTORY, pkt.header(), json.getBytes()));
                        }

                        case CREATE_GROUP -> {
                            String gName = pkt.header();
                            String[] arr = new String(pkt.payload()).split(",");

                            Conversation g = new Conversation("GROUP", gName);
                            conversationDAO.save(g);

                            List<String> members = new ArrayList<>();
                            members.add(username);
                            for(String m: arr) if(!m.isBlank()) members.add(m.trim());

                            for(String u: members){
                                addMembership(u, g, u.equals(username)?"owner":"member");
                            }

                            /* gửi lại CONV_LIST cho mỗi thành viên online */
                            for(String u: members) if(clients.containsKey(u)) sendConvList(u);
                        }



                        case GROUP_MSG -> {
                            long convId = Long.parseLong(pkt.header());
                            String content = new String(pkt.payload());

                            // Lưu với mã hóa
                            groupSvc.saveAndBroadcast(convId, username, content);
                        }
                        case GET_THUMB -> {
                            String id = pkt.header();
                            FileAttachment att = fileAttachmentDAO.findById(id);
                            if(att == null || att.getThumbPath()==null){
                                System.out.println("[THUMB] not found id="+id);
                                break;
                            }
                            Path tp = Path.of(att.getThumbPath());
                            if(!Files.exists(tp)){
                                System.out.println("[THUMB] missing file "+tp);    // chỉ log, không kill thread
                                break;
                            }
                            byte[] bytes = Files.readAllBytes(tp);
                            sendPacket(new Packet(PacketType.FILE_THUMB, id, bytes));
                        }

                        case AVATAR_UPLOAD -> {
                            /* 1) xác định user gửi gói tin */
                            String username = this.username;  // Sử dụng username từ ClientHandler

                            /* 2) tạo tên file an toàn */
                            String ext  = pkt.header().substring(pkt.header().lastIndexOf('.')); // .png…
                            String fname= username + "_" + System.currentTimeMillis() + ext;
                            Path  dst   = Path.of("uploads", "avatars", fname);
                            Files.createDirectories(dst.getParent());
                            Files.write(dst, pkt.payload());                  // lưu file

                            /* 3) cập nhật DB */
                            User u = userDAO.findByUsername(username);
                            if (u == null) {
                                System.out.println("Lỗi: Không tìm thấy user " + username);
                                break;
                            }
                            u.setAvatarPath("uploads/avatars/" + fname);      // LƯU TƯƠNG ĐỐI
                            u.setUseDefaultAvatar(false);
                            userDAO.update(u);

                            /* 4) phát cho tất cả client */
                            Packet p = new Packet(PacketType.AVATAR_DATA, username, pkt.payload());
                            broadcast(p);
                        }

                        case GET_AVATAR -> {
                            String targetUser = pkt.header();                 // header = username
                            User u = userDAO.findByUsername(targetUser);
                            if(u == null || u.getAvatarPath()==null) break;

                            byte[] data = Files.readAllBytes(Path.of(u.getAvatarPath()));
                            sendPacket(new Packet(PacketType.AVATAR_DATA, targetUser, data)); // chỉ gửi requester
                        }

                        case FRIEND_REQUEST -> {
                            String[] arr = pkt.header().split("->");
                            if (arr.length != 2) {
                                System.out.println("[ERROR] FRIEND_REQUEST header không hợp lệ: " + pkt.header());
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_INVALID_FORMAT", null));
                                break;
                            }

                            String from = arr[0].trim();
                            String to = arr[1].trim();
                            System.out.println("[DEBUG] Friend request từ " + from + " đến " + to);

                            try {
                                // Validate users exist
                                User fromUser = ServiceLocator.userService().getUser(from);
                                User toUser = ServiceLocator.userService().getUser(to);

                                if (fromUser == null) {
                                    System.out.println("[ERROR] Không tìm thấy user gửi: " + from);
                                    sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_SENDER_NOT_FOUND", null));
                                    break;
                                }

                                if (toUser == null) {
                                    System.out.println("[ERROR] Không tìm thấy user nhận: " + to);
                                    sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_RECEIVER_NOT_FOUND", null));
                                    break;
                                }

                                // Check if users are the same
                                if (from.equals(to)) {
                                    System.out.println("[ERROR] User không thể tự gửi friend request cho chính mình");
                                    sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_SELF_REQUEST", null));
                                    break;
                                }

                                // Check existing friendship status
                                Friendship.Status existingStatus = ServiceLocator.friendship()
                                        .getFriendshipStatus(fromUser, toUser);

                                if (existingStatus != null) {
                                    String reason = switch(existingStatus) {
                                        case PENDING -> "ALREADY_PENDING";
                                        case ACCEPTED -> "ALREADY_FRIENDS";
                                        case BLOCKED -> "BLOCKED";
                                    };
                                    sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_" + reason, null));
                                    break;
                                }

                                // Send friend request
                                ServiceLocator.friendship().sendFriendRequest(fromUser, toUser);
                                System.out.println("[DEBUG] Friend request đã được lưu vào database");

                                // Create notification for receiver (cho cả online và offline)
                                ServiceLocator.notification().createNotification(toUser, "FRIEND_REQUEST", from);
                                System.out.println("[DEBUG] Notification đã được tạo cho " + to);

                                // Send success ACK to sender
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_SUCCESS", null));

                                // Send notification to receiver (nếu online)
                                ClientHandler receiverHandler = clients.get(to);
                                if (receiverHandler != null) {
                                    receiverHandler.sendPacket(new Packet(
                                            PacketType.FRIEND_REQUEST_NOTIFICATION,
                                            from,
                                            null
                                    ));
                                    System.out.println("[DEBUG] Real-time notification gửi tới " + to + " (online)");
                                } else {
                                    System.out.println("[DEBUG] User " + to + " offline, notification sẽ được gửi khi login");
                                }

                                // Log activity
                                logFriendshipAction("FRIEND_REQUEST", from, to, true, "Success");

                            } catch (IllegalArgumentException e) {
                                System.out.println("[ERROR] Validation error: " + e.getMessage());
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_VALIDATION_ERROR", null));
                                logFriendshipAction("FRIEND_REQUEST", from, to, false, e.getMessage());
                            } catch (IllegalStateException e) {
                                System.out.println("[ERROR] State error: " + e.getMessage());
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_STATE_ERROR", null));
                                logFriendshipAction("FRIEND_REQUEST", from, to, false, e.getMessage());
                            } catch (Exception e) {
                                System.out.println("[ERROR] Lỗi khi xử lý friend request: " + e.getMessage());
                                e.printStackTrace();
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL_SERVER_ERROR", null));
                                logFriendshipAction("FRIEND_REQUEST", from, to, false, "Server error: " + e.getMessage());
                            }
                        }
                        case FRIEND_ACCEPT -> {
                            String[] arr = pkt.header().split("->");
                            if (arr.length != 2) {
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_FAIL_INVALID_FORMAT", null));
                                break;
                            }

                            String from = arr[0].trim();
                            String to = arr[1].trim();
                            System.out.println("[DEBUG] Accept friend request từ " + from + " bởi " + to);

                            try {
                                User fromUser = ServiceLocator.userService().getUser(from);
                                User toUser = ServiceLocator.userService().getUser(to);

                                if (fromUser == null || toUser == null) {
                                    sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_FAIL_USER_NOT_FOUND", null));
                                    break;
                                }

                                // Accept the friend request
                                ServiceLocator.friendship().acceptFriendRequest(fromUser, toUser);

                                // Create notification for the original sender (cho cả online và offline)
                                ServiceLocator.notification().createNotification(fromUser, "FRIEND_ACCEPT", to);

                                // Send success ACK to accepter
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_SUCCESS", null));

                                // Send notification to original sender (nếu online)
                                ClientHandler senderHandler = clients.get(from);
                                if (senderHandler != null) {
                                    senderHandler.sendPacket(new Packet(
                                            PacketType.FRIEND_REQUEST_ACCEPTED,
                                            from + "->" + to,
                                            null
                                    ));
                                    System.out.println("[DEBUG] Real-time accept notification gửi tới " + from + " (online)");
                                } else {
                                    System.out.println("[DEBUG] User " + from + " offline, accept notification sẽ được xử lý khi login");
                                }

                                logFriendshipAction("FRIEND_ACCEPT", from, to, true, "Success");
                                System.out.println("[DEBUG] Friend request từ " + from + " đã được " + to + " chấp nhận");

                            } catch (IllegalStateException e) {
                                System.out.println("[ERROR] Không thể accept: " + e.getMessage());
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_FAIL_INVALID_STATE", null));
                                logFriendshipAction("FRIEND_ACCEPT", from, to, false, e.getMessage());
                            } catch (Exception e) {
                                System.out.println("[ERROR] Lỗi khi accept friend request: " + e.getMessage());
                                e.printStackTrace();
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_FAIL_SERVER_ERROR", null));
                                logFriendshipAction("FRIEND_ACCEPT", from, to, false, "Server error: " + e.getMessage());
                            }
                        }

                        case FRIEND_REJECT -> {
                            String[] arr = pkt.header().split("->");
                            if (arr.length != 2) {
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_FAIL_INVALID_FORMAT", null));
                                break;
                            }

                            String from = arr[0].trim();
                            String to = arr[1].trim();
                            System.out.println("[DEBUG] Reject friend request từ " + from + " bởi " + to);

                            try {
                                User fromUser = ServiceLocator.userService().getUser(from);
                                User toUser = ServiceLocator.userService().getUser(to);

                                if (fromUser == null || toUser == null) {
                                    sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_FAIL_USER_NOT_FOUND", null));
                                    break;
                                }

                                // Reject the friend request
                                ServiceLocator.friendship().rejectFriendRequest(fromUser, toUser);

                                // Send success ACK to rejecter
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_SUCCESS", null));

                                // Send notification to original sender (if online)
                                ClientHandler senderHandler = clients.get(from);
                                if (senderHandler != null) {
                                    senderHandler.sendPacket(new Packet(
                                            PacketType.FRIEND_REQUEST_REJECTED,
                                            from + "->" + to,
                                            null
                                    ));
                                    System.out.println("[DEBUG] Friend reject notification gửi tới " + from);
                                }

                                System.out.println("[DEBUG] Friend request từ " + from + " đã bị " + to + " từ chối");

                            } catch (IllegalStateException e) {
                                System.out.println("[ERROR] Không thể reject: " + e.getMessage());
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_FAIL_INVALID_STATE", null));
                            } catch (Exception e) {
                                System.out.println("[ERROR] Lỗi khi reject friend request: " + e.getMessage());
                                e.printStackTrace();
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_FAIL_SERVER_ERROR", null));
                            }
                        }
                        case FRIEND_PENDING_LIST -> {
                            String username = pkt.header();
                            System.out.println("[DEBUG] Yêu cầu danh sách pending friend requests cho: " + username);

                            try {
                                User user = ServiceLocator.userService().getUser(username);
                                if (user == null) {
                                    System.out.println("[ERROR] User không tồn tại: " + username);
                                    break;
                                }

                                List<Friendship> pending = ServiceLocator.friendship().getPendingRequests(user);
                                System.out.println("[DEBUG] Tìm thấy " + pending.size() + " pending requests cho " + username);

                                // *** FIX: Sử dụng custom Gson với exclusion strategy ***
                                Gson gson = new GsonBuilder()
                                        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                                        .addSerializationExclusionStrategy(new ExclusionStrategy() {
                                            @Override
                                            public boolean shouldSkipField(FieldAttributes field) {
                                                // Skip memberships field để tránh LazyInitializationException
                                                return field.getName().equals("memberships");
                                            }

                                            @Override
                                            public boolean shouldSkipClass(Class<?> clazz) {
                                                return false;
                                            }
                                        })
                                        .create();

                                String json = gson.toJson(pending);

                                sendPacket(new Packet(PacketType.FRIEND_PENDING_LIST, username, json.getBytes()));
                                System.out.println("[DEBUG] Đã gửi danh sách pending requests cho " + username);

                            } catch (Exception e) {
                                System.out.println("[ERROR] Lỗi khi lấy pending friend requests: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }

                        default -> {}
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Client disconnected: " + socket);
            } finally {
                if (username != null) {
                    clients.remove(username);
                }
                broadcastUserList();

                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        private void cleanupOldNotifications() {
            try {
                // Có thể implement logic để xóa notifications cũ hơn 30 ngày
                // ServiceLocator.notification().cleanupOldNotifications(30);
                System.out.println("[DEBUG] Cleanup old notifications completed");
            } catch (Exception e) {
                System.out.println("[ERROR] Error during notification cleanup: " + e.getMessage());
            }
        }

        private void sendConvList(String username){
            try {
                if (ServiceLocator.userService() == null) {
                    System.out.println("[ERROR] UserService là null khi gửi CONV_LIST cho " + username);
                    return;
                }

                List<Conversation> convs = conversationDAO.findAllOfUser(username);
                System.out.println("[DEBUG] Gửi CONV_LIST cho " + username + ": " + convs);
                List<Map<String,Object>> dto = new ArrayList<>();
                for(Conversation c : convs){
                    dto.add(Map.of("id", c.getId(), "name", c.getName(), "type", c.getType()));
                }
                String json = new Gson().toJson(dto);
                sendToUser(username, new Packet(PacketType.CONV_LIST, "LIST", json.getBytes()));
            } catch (Exception e) {
                System.out.println("[ERROR] Lỗi khi gửi CONV_LIST cho " + username + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void sendNotificationToUser(String username, String type, String payload) {
            ClientHandler handler = clients.get(username);
            if (handler != null) {
                try {
                    Packet notification = new Packet(PacketType.valueOf(type), username, payload.getBytes());
                    handler.sendPacket(notification);
                    System.out.println("[DEBUG] Notification " + type + " gửi tới " + username);
                } catch (Exception e) {
                    System.out.println("[ERROR] Không thể gửi notification tới " + username + ": " + e.getMessage());
                }
            }
        }
        private void logFriendshipAction(String action, String from, String to, boolean success, String reason) {
            String status = success ? "SUCCESS" : "FAILED";
            String message = String.format("[FRIENDSHIP] %s: %s -> %s [%s]", action, from, to, status);
            if (reason != null) {
                message += " Reason: " + reason;
            }
            System.out.println(message);
        }


        private void sendPendingFriendRequestsOnLogin(String username) {
            try {
                System.out.println("[DEBUG] Auto-sending pending friend requests cho " + username);

                User user = ServiceLocator.userService().getUser(username);
                if (user == null) {
                    System.out.println("[ERROR] User không tồn tại khi auto-send: " + username);
                    return;
                }

                List<Friendship> pending = ServiceLocator.friendship().getPendingRequests(user);
                System.out.println("[DEBUG] Tìm thấy " + pending.size() + " pending requests cho " + username);

                if (!pending.isEmpty()) {
                    // Convert to JSON với proper date handling
                    Gson gson = new GsonBuilder()
                            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                            .create();
                    String json = gson.toJson(pending);

                    // Gửi packet với delay nhỏ để đảm bảo client đã setup callbacks
                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // Delay 500ms
                            sendPacket(new Packet(PacketType.FRIEND_PENDING_LIST, username, json.getBytes()));
                            System.out.println("[DEBUG] Đã auto-send " + pending.size() + " pending requests cho " + username);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }

            } catch (Exception e) {
                System.out.println("[ERROR] Lỗi khi auto-send pending friend requests: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void createOfflineNotification(String username, String type, String payload) {
            try {
                User user = ServiceLocator.userService().getUser(username);
                if (user != null) {
                    ServiceLocator.notification().createNotification(user, type, payload);
                    System.out.println("[DEBUG] Offline notification tạo cho " + username + ": " + type);
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Không thể tạo offline notification: " + e.getMessage());
            }
        }

        private String convertMessagesToJson(List<Message> messages) {
            List<MessageDTO> dtoList = new ArrayList<>();
            for (Message m : messages) {
                // GIẢI MÃ content trước khi tạo DTO
                String decryptedContent = DatabaseEncryptionUtil.decrypt(m.getContent());

                // Log để debug
                if (m.getContent().startsWith("DBENC:")) {
                    System.out.println("[DEBUG] Giải mã tin nhắn từ " + m.getSender().getUsername());
                }

                dtoList.add(new MessageDTO(
                        m.getSender().getUsername(),
                        decryptedContent,  // Gửi content ĐÃ GIẢI MÃ
                        m.getCreatedAt()
                ));
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();

            return gson.toJson(dtoList);
        }
        private void addMembership(String username, Conversation g, String role){
            User u = userDAO.findByUsername(username);
            if(u == null) return;
            Membership m = new Membership();
            m.setUser(u);
            m.setConversation(g);
            m.setRole(role);
            membershipDAO.save(m); // tạo DAO giống MessageDAO
        }




        /**
         * Lưu tin nhắn vào DB, gắn với conversation nhất định.
         */


        private void saveMessageToDb(String senderUsername, String content, Conversation conv) {
            User sender = userDAO.findByUsername(senderUsername);
            if (sender == null) {
                System.out.println("Không tìm thấy user " + senderUsername + " trong DB!");
                return;
            }

            // LUÔN mã hóa content trước khi lưu (trừ tin nhắn file)
            String finalContent = content;
            if (!content.startsWith("[FILE]")) {
                finalContent = DatabaseEncryptionUtil.encrypt(content);
            }

            // Tạo message với content đã xử lý
            Message msg = new Message(conv, sender, finalContent, null);
            messageDAO.save(msg);

            System.out.println("[DEBUG] Đã lưu tin nhắn" +
                    (finalContent.startsWith("DBENC:") ? " (đã mã hóa)" : "") +
                    ": " + finalContent.substring(0, Math.min(30, finalContent.length())) + "...");
        }
        /**
         * Gửi 1 packet đến client này.
         */
        public void sendPacket(Packet p) {
            try {
                out.writeObject(p);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Conversation findOrCreatePrivateConversation(String userA, String userB) {
        if (userA == null || userB == null || userA.isBlank() || userB.isBlank()) {
            System.out.println("[ERROR] Invalid parameters for conversation: userA=" + userA + ", userB=" + userB);
            return null;
        }

        try {
            // Always put names in alphabetical order for consistency
            String nameA = (userA.compareTo(userB) < 0) ? userA : userB;
            String nameB = (userA.compareTo(userB) < 0) ? userB : userA;
            String convName = nameA + "|" + nameB;

            System.out.println("[DEBUG] Looking for private conversation: " + convName);

            // Try to find existing conversation
            Conversation conv = conversationDAO.findByName(convName);

            // Create if it doesn't exist
            if (conv == null) {
                conv = new Conversation("PRIVATE", convName);
                conversationDAO.save(conv);
                System.out.println("[DEBUG] Created new private conversation: " + convName + " (ID: " + conv.getId() + ")");

                // Add both users as members
                User userObjA = userDAO.findByUsername(nameA);
                User userObjB = userDAO.findByUsername(nameB);

                if (userObjA != null && userObjB != null) {
                    // Create memberships
                    Membership m1 = new Membership();
                    m1.setUser(userObjA);
                    m1.setConversation(conv);
                    m1.setRole("member");
                    m1.setJoinedAt(LocalDateTime.now());

                    Membership m2 = new Membership();
                    m2.setUser(userObjB);
                    m2.setConversation(conv);
                    m2.setRole("member");
                    m2.setJoinedAt(LocalDateTime.now());

                    // Save memberships
                    membershipDAO.save(m1);
                    membershipDAO.save(m2);

                    System.out.println("[DEBUG] Added users to conversation: " + nameA + ", " + nameB);
                } else {
                    System.out.println("[WARN] Could not find one or both users for memberships: " +
                            nameA + " (" + (userObjA != null) + "), " +
                            nameB + " (" + (userObjB != null) + ")");
                }
            } else {
                System.out.println("[DEBUG] Found existing conversation: " + convName + " (ID: " + conv.getId() + ")");

                // IMPORTANT: We need to check memberships inside a transaction to avoid LazyInitializationException
                boolean hasBothMemberships = membershipDAO.hasUserMemberships(conv.getId(), nameA, nameB);

                // If memberships are missing, add them
                if (!hasBothMemberships) {
                    System.out.println("[DEBUG] Fixing missing memberships for conversation: " + convName);

                    User userObjA = userDAO.findByUsername(nameA);
                    User userObjB = userDAO.findByUsername(nameB);

                    if (userObjA != null && userObjB != null) {
                        // Check if each user already has a membership
                        boolean userAHasMembership = membershipDAO.hasMembership(conv.getId(), userObjA.getId());
                        boolean userBHasMembership = membershipDAO.hasMembership(conv.getId(), userObjB.getId());

                        // Add membership for first user if needed
                        if (!userAHasMembership) {
                            Membership m1 = new Membership();
                            m1.setUser(userObjA);
                            m1.setConversation(conv);
                            m1.setRole("member");
                            m1.setJoinedAt(LocalDateTime.now());
                            membershipDAO.save(m1);
                            System.out.println("[DEBUG] Added missing membership for: " + nameA);
                        }

                        // Add membership for second user if needed
                        if (!userBHasMembership) {
                            Membership m2 = new Membership();
                            m2.setUser(userObjB);
                            m2.setConversation(conv);
                            m2.setRole("member");
                            m2.setJoinedAt(LocalDateTime.now());
                            membershipDAO.save(m2);
                            System.out.println("[DEBUG] Added missing membership for: " + nameB);
                        }
                    }
                }
            }

            return conv;
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to find/create conversation: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Lưu tin nhắn vào DB, gắn với conversation nhất định.
     * Nội dung sẽ được mã hóa nếu tính năng mã hóa database được bật.
     */

    /**
     * Lưu tin nhắn nhóm vào DB.
     * Được gọi từ GroupMessageService.saveAndBroadcast
     */
    public void saveGroupMessage(Conversation conv, User sender, String content) {
        // Tạo message (MessageDAO sẽ xử lý mã hóa nếu cần)
        Message msg = new Message(conv, sender, content, null);
        messageDAO.save(msg);
    }
}