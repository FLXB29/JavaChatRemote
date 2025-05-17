package network;

import app.LocalDateTimeAdapter;
import app.dao.*;
import app.model.*;
import app.service.GroupMessageService;
import app.util.Config;
import app.util.HibernateUtil;
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
                            // handle login
                            String loginUsername = pkt.header(); // header = "username"
                            if (loginUsername == null || loginUsername.isBlank()) {
                                System.out.println("Lỗi: username không hợp lệ khi login");
                                sendPacket(new Packet(PacketType.ACK, "LOGIN_FAIL: Invalid username", null));
                                break;
                            }
                            this.username = loginUsername;
                            System.out.println("User login: " + username);

                            // Ở đây giả định user đã tồn tại trong DB do client đã register.
                            // Nếu cần, có thể kiểm tra/truy vấn DB:
                            //   User u = userDAO.findByUsername(username);
                            //   if(u == null) { ... } else {...}

                            // Thêm vào map clients
                            clients.put(username, this);

                            // Gửi phản hồi "ACK"
                            sendPacket(new Packet(PacketType.ACK, "LOGIN_OK", null));
                            sendConvList(username); // <── thêm dòng này

                            // Gửi/broadcast danh sách user online
                            broadcastUserList();
                        }
                        case MSG -> {
                            // Nhận tin MSG (chat chung)
                            if (username == null) {
                                System.out.println("Lỗi: username là null khi xử lý MSG");
                                break;
                            }
                            String from = username;
                            String txt = new String(pkt.payload());
                            System.out.println("MSG from " + from + ": " + txt);

                            // 1) Lưu DB
                            saveMessageToDb(from, txt, globalConv);

                            // 2) Gửi CHO TẤT CẢ, kể cả người gửi
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
                            String toUser  = pkt.header();  // e.g. "bob"
                            String content = new String(pkt.payload());
                            String from    = username;      // e.g. "alice"
                            System.out.println("PM from " + from + " to " + toUser + ": " + content);

                            // 1) Tìm hoặc tạo Conversation riêng
                            Conversation conv = findOrCreatePrivateConversation(from, toUser);

                            // 2) Lưu DB
                            saveMessageToDb(from, content, conv);

                            // 3) Gửi cho người nhận
                            sendToUser(toUser, new Packet(PacketType.PM, from, content.getBytes()));
                            // 4) Gửi cho chính người gửi (để họ cũng nhận & hiển thị tin)
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
                                System.out.println("Xử lý GET_HISTORY: fromUser=" + fromUser + ", target=" + target);

                                Conversation conv;
                                if (target.equals("Global")) {
                                    conv = globalConv;
                                } else {
                                    conv = findOrCreatePrivateConversation(fromUser, target);
                                }
                                if (conv == null) {
                                    System.out.println("Lỗi: Không tìm thấy hoặc tạo được conversation cho " + fromUser + "->" + target);
                                    break;
                                }

                                List<Message> messages = messageDAO.findByConversationId(conv.getId());
                                System.out.println("Số tin nhắn tìm thấy: " + messages.size());

                                String json = convertMessagesToJson(messages);
                                System.out.println("Gửi HISTORY JSON: " + json);

                                // header = convId   (giữ nguyên kiểu String)
                                Packet resp = new Packet(
                                        PacketType.HISTORY,
                                        String.valueOf(conv.getId()),   // <── thay "HISTORY" bằng convId
                                        json.getBytes()
                                );
                                sendToUser(fromUser, resp);

                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Lỗi xử lý GET_HISTORY: " + e.getMessage());
                            }
                        }
                        case JOIN_CONV -> {
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

                            groupSvc.saveAndBroadcast(convId, username, content);   // ✔

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
                            // header: from->to
                            System.out.println("[DEBUG] Nhận FRIEND_REQUEST packet");
                            String[] arr = pkt.header().split("->");
                            if (arr.length != 2) {
                                System.out.println("[ERROR] FRIEND_REQUEST header không hợp lệ: " + pkt.header());
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL: Invalid header format", null));
                                break;
                            }
                            String from = arr[0], to = arr[1];
                            System.out.println("[DEBUG] Friend request từ " + from + " đến " + to);

                            User fromUser = ServiceLocator.userService().getUser(from);
                            if (fromUser == null) {
                                System.out.println("[ERROR] Không tìm thấy user gửi: " + from);
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL: Sender not found", null));
                                break;
                            }

                            User toUser = ServiceLocator.userService().getUser(to);
                            if (toUser == null) {
                                System.out.println("[ERROR] Không tìm thấy user nhận: " + to);
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL: Receiver not found", null));
                                break;
                            }

                            try {
                                System.out.println("[DEBUG] Gọi friendship.sendFriendRequest");
                                ServiceLocator.friendship().sendFriendRequest(fromUser, toUser);
                                System.out.println("[DEBUG] Tạo notification cho user nhận");
                                ServiceLocator.notification().createNotification(toUser, "FRIEND_REQUEST", from);
                                System.out.println("[DEBUG] Gửi ACK thành công");
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_OK", null));
                            } catch (Exception e) {
                                System.out.println("[ERROR] Lỗi khi xử lý friend request: " + e.getMessage());
                                e.printStackTrace();
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REQUEST_FAIL: " + e.getMessage(), null));
                            }
                        }
                        case FRIEND_ACCEPT -> {
                            String[] arr = pkt.header().split("->");
                            String from = arr[0], to = arr[1];
                            User fromUser = ServiceLocator.userService().getUser(from);
                            User toUser = ServiceLocator.userService().getUser(to);
                            try {
                                ServiceLocator.friendship().acceptFriendRequest(fromUser, toUser);
                                // Tạo notification cho user gửi
                                ServiceLocator.notification().createNotification(toUser, "FRIEND_ACCEPT", from);
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_OK", null));
                            } catch (Exception e) {
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_ACCEPT_FAIL: " + e.getMessage(), null));
                            }
                        }
                        case FRIEND_REJECT -> {
                            String[] arr = pkt.header().split("->");
                            String from = arr[0], to = arr[1];
                            User fromUser = ServiceLocator.userService().getUser(from);
                            User toUser = ServiceLocator.userService().getUser(to);
                            try {
                                ServiceLocator.friendship().rejectFriendRequest(fromUser, toUser);
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_OK", null));
                            } catch (Exception e) {
                                sendPacket(new Packet(PacketType.ACK, "FRIEND_REJECT_FAIL: " + e.getMessage(), null));
                            }
                        }
                        case FRIEND_PENDING_LIST -> {
                            String username = pkt.header();
                            User user = ServiceLocator.userService().getUser(username);
                            var pending = ServiceLocator.friendship().getPendingRequests(user);
                            String json = new Gson().toJson(pending);
                            sendPacket(new Packet(PacketType.FRIEND_PENDING_LIST, username, json.getBytes()));
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


        private String convertMessagesToJson(List<Message> messages) {
            // Tạo list DTO
            List<MessageDTO> dtoList = new ArrayList<>();
            for (Message m : messages) {
                dtoList.add(new MessageDTO(
                        m.getSender().getUsername(),
                        m.getContent(),
                        m.getCreatedAt() // kiểu LocalDateTime
                ));
            }

            // Tạo Gson với adapter LocalDateTime
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();

            // Trả về chuỗi JSON
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
            // Tìm user (đảm bảo chắc chắn user đã tồn tại do login/register)
            User sender = userDAO.findByUsername(senderUsername);
            if (sender == null) {
                System.out.println("Không tìm thấy user " + senderUsername + " trong DB!");
                return; // không lưu được
            }

            // Tạo message
            Message msg = new Message(conv, sender, content, null);
            messageDAO.save(msg);
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
            System.out.println("Lỗi: userA hoặc userB không hợp lệ (userA=" + userA + ", userB=" + userB + ")");
            return null;
        }
        String nameA = (userA.compareTo(userB) < 0) ? userA : userB;
        String nameB = (userA.compareTo(userB) < 0) ? userB : userA;
        String convName = nameA + "|" + nameB;
        System.out.println("Tạo convName: " + convName);

        try {
            Conversation c = conversationDAO.findByName(convName);
            if (c == null) {
                c = new Conversation("PRIVATE", convName);
                conversationDAO.save(c);
                System.out.println("Tạo conversation riêng: " + convName);
            }
            return c;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi tìm/tạo conversation: " + convName + ", lỗi: " + e.getMessage());
            return null;
        }
    }
}