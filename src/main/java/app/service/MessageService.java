package app.service;

import app.ServiceLocator;
import app.controller.ChatController;
import app.controller.chat.model.RecentChatCellData;
import app.dao.ConversationDAO;
import app.dao.MessageDAO;
import app.dao.UserDAO;
import app.model.Conversation;
import app.model.Message;
import app.model.User;
//import app.util.ConversationKeyManager;
import app.util.DatabaseEncryptionUtil;
import app.util.HibernateUtil;
import org.hibernate.Session;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.io.File;
import app.model.FileAttachment;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * MessageService: lưu/đọc tin nhắn
 */
public class MessageService {
    private final MessageDAO messageDAO;
    private final UserDAO userDAO;
    private final ConversationDAO conversationDAO;

    public MessageService() {
        this.messageDAO = new MessageDAO();
        this.userDAO = new UserDAO();
        this.conversationDAO = new ConversationDAO();
    }

    public void saveMessage(Conversation conv, String senderUsername, String content, String filePath) {
        try {
            // Tìm User từ username
            User sender = userDAO.findByUsername(senderUsername);
            if (sender == null) {
                System.out.println("[ERROR] Không tìm thấy user: " + senderUsername);
                return;
            }

            // Kiểm tra conversation
            if (conv == null) {
                System.out.println("[ERROR] Conversation không được phép null");
                return;
            }

            // MÃ HÓA content nếu không phải file
            String finalContent = content;
            if (!content.startsWith("[FILE]")) {
                finalContent = DatabaseEncryptionUtil.encrypt(content);
                System.out.println("[DEBUG] Đã mã hóa tin nhắn trước khi lưu");
            }

            // Tạo Message với content đã xử lý
            Message msg = new Message(conv, sender, finalContent);

            // Nếu có file path, tạo và gắn FileAttachment
            if (filePath != null && !filePath.isEmpty()) {
                File file = new File(filePath);
                if (!file.exists()) {
                    System.out.println("[ERROR] File không tồn tại: " + filePath);
                    return;
                }

                FileAttachment attachment = new FileAttachment(
                        file.getName(),
                        filePath,
                        getMimeType(file),
                        file.length()
                );
                msg.setAttachment(attachment);
            }

            // Save
            messageDAO.save(msg);
            System.out.println("[DEBUG] Đã lưu tin nhắn thành công" +
                    (finalContent.startsWith("DBENC:") ? " (đã mã hóa)" : ""));
        } catch (Exception e) {
            System.out.println("[ERROR] Lỗi khi lưu tin nhắn: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private String getMimeType(File file) {
        try {
            String mimeType = java.nio.file.Files.probeContentType(file.toPath());
            return mimeType != null ? mimeType : "application/octet-stream";
        } catch (Exception e) {
            System.out.println("[WARN] Không thể xác định MIME type, sử dụng mặc định: " + e.getMessage());
            return "application/octet-stream";
        }
    }

    public List<Message> getMessagesByConversation(Long convId) {
        if (convId == null) {
            System.out.println("[WARN] convId null, trả về danh sách rỗng");
            return new ArrayList<>();
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Message m WHERE m.conversation.id = :cid ORDER BY m.createdAt ASC";
            List<Message> messages = session.createQuery(hql, Message.class)
                    .setParameter("cid", convId)
                    .list();

            System.out.println("[DEBUG] Tìm thấy " + messages.size() + " tin nhắn cho conversation " + convId);
            return messages;
        } catch (Exception e) {
            System.out.println("[ERROR] Lỗi khi lấy tin nhắn theo conversation: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Message> getMessagesWithUser(String userA, String userB) {
        if (userA == null || userB == null || userA.isBlank() || userB.isBlank()) {
            System.out.println("[WARN] Thiếu username, trả về danh sách rỗng");
            return new ArrayList<>();
        }

        try {
            // Determine the conversation name (usernames in alphabetical order)
            String name1 = userA.compareTo(userB) < 0 ? userA : userB;
            String name2 = userA.compareTo(userB) < 0 ? userB : userA;
            String convName = name1 + "|" + name2;
            System.out.println("[DEBUG] Tìm conversation với tên: " + convName);

            // Find the conversation by name
            Conversation conv = conversationDAO.findByName(convName);
            if (conv == null) {
                System.out.println("[DEBUG] Không tìm thấy conversation giữa " + userA + " và " + userB);
                return new ArrayList<>();
            }

            // Return all messages in this conversation
            List<Message> messages = getMessagesByConversation(conv.getId());
            System.out.println("[DEBUG] Tìm thấy " + messages.size() + " tin nhắn giữa " + userA + " và " + userB);
            return messages;
        } catch (Exception e) {
            System.out.println("[ERROR] Lỗi khi lấy tin nhắn giữa hai người dùng: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Message> getAllMessages() {
        try {
            List<Message> messages = messageDAO.findAll();
            System.out.println("[DEBUG] Lấy tất cả tin nhắn: " + messages.size() + " tin");
            return messages;
        } catch (Exception e) {
            System.out.println("[ERROR] Lỗi khi lấy tất cả tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    public List<RecentChatCellData> getRecentChats(User currentUser) {
        if (currentUser == null) {
            System.out.println("[ERROR] Không thể lấy recent chats: currentUser là null");
            return new ArrayList<>();
        }

        List<RecentChatCellData> result = new ArrayList<>();

        try {
            // 1. Thêm Global Chat
            Conversation globalConv = conversationDAO.findByName("Global Chat");
            if (globalConv != null) {
                List<Message> globalMessages = getMessagesByConversation(globalConv.getId());

                String lastMessage = "Chat toàn cầu";
                String timeString = "";

                if (!globalMessages.isEmpty()) {
                    Message lastMsg = globalMessages.get(globalMessages.size() - 1);

                    // Giải mã content
                    String decryptedContent = DatabaseEncryptionUtil.decrypt(lastMsg.getContent());

                    // Format tin nhắn
                    if (decryptedContent.startsWith("[FILE]")) {
                        String[] parts = decryptedContent.substring(6).split("\\|", 2);
                        lastMessage = lastMsg.getSender().getUsername() + ": 📎 " + parts[0];
                    } else {
                        lastMessage = lastMsg.getSender().getUsername() + ": " + decryptedContent;
                        if (lastMessage.length() > 50) {
                            lastMessage = lastMessage.substring(0, 47) + "...";
                        }
                    }

                    timeString = lastMsg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm"));
                }

                result.add(new RecentChatCellData("Global", lastMessage, timeString, null, 0));
            }

            // 2. Lấy tất cả các cuộc hội thoại
            List<Conversation> conversations = conversationDAO.findAllOfUser(currentUser.getUsername());

            for (Conversation conv : conversations) {
                if ("Global Chat".equals(conv.getName())) continue;

                String chatName;
                String avatarPath = null;

                if ("GROUP".equals(conv.getType())) {
                    chatName = conv.getName();
                } else if ("PRIVATE".equals(conv.getType())) {
                    String[] parts = conv.getName().split("\\|");
                    if (parts.length == 2) {
                        chatName = parts[0].equals(currentUser.getUsername()) ? parts[1] : parts[0];

                        User otherUser = ServiceLocator.userService().getUser(chatName);
                        if (otherUser != null && !otherUser.isUseDefaultAvatar() && otherUser.getAvatarPath() != null) {
                            File avatarFile = new File(otherUser.getAvatarPath());
                            if (avatarFile.exists()) {
                                avatarPath = otherUser.getAvatarPath();
                            }
                        }
                    } else {
                        chatName = conv.getName();
                    }
                } else {
                    chatName = conv.getName();
                }

                String lastMessage = "";
                String timeString = "";

                List<Message> messages = getMessagesByConversation(conv.getId());
                if (!messages.isEmpty()) {
                    Message lastMsg = messages.get(messages.size() - 1);

                    // Giải mã content
                    String decryptedContent = DatabaseEncryptionUtil.decrypt(lastMsg.getContent());

                    if (decryptedContent.startsWith("[FILE]")) {
                        String[] parts = decryptedContent.substring(6).split("\\|", 2);
                        lastMessage = lastMsg.getSender().getUsername() + ": 📎 " + parts[0];
                    } else {
                        lastMessage = lastMsg.getSender().getUsername() + ": " + decryptedContent;
                        if (lastMessage.length() > 50) {
                            lastMessage = lastMessage.substring(0, 47) + "...";
                        }
                    }

                    timeString = lastMsg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm"));
                }

                // Thêm cuộc trò chuyện vào danh sách kết quả, kể cả khi không có tin nhắn
                result.add(new RecentChatCellData(chatName, lastMessage, timeString, avatarPath, 0));
            }

            // 3. Thêm bạn bè chưa có cuộc trò chuyện
            List<User> friends = ServiceLocator.friendship().getFriends(currentUser);
            for (User friend : friends) {
                boolean alreadyAdded = result.stream()
                        .anyMatch(chat -> chat.chatName.equals(friend.getUsername()));

                if (!alreadyAdded && !friend.getUsername().equals(currentUser.getUsername())) {
                    String avatarPath = null;
                    if (!friend.isUseDefaultAvatar() && friend.getAvatarPath() != null) {
                        File avatarFile = new File(friend.getAvatarPath());
                        if (avatarFile.exists()) {
                            avatarPath = friend.getAvatarPath();
                        }
                    }

                    result.add(new RecentChatCellData(
                            friend.getUsername(), "", "", avatarPath, 0
                    ));
                }
            }

            // 4. Sắp xếp theo thời gian
            result.sort((a, b) -> {
                if (a.chatName.equals("Global")) return -1;
                if (b.chatName.equals("Global")) return 1;
                if (a.timeString.isEmpty() && b.timeString.isEmpty()) return 0;
                if (a.timeString.isEmpty()) return 1;
                if (b.timeString.isEmpty()) return -1;
                return b.timeString.compareTo(a.timeString);
            });

        } catch (Exception e) {
            System.out.println("[ERROR] Lỗi khi tải recent chats: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Lấy danh sách tin nhắn từ một cuộc trò chuyện với giới hạn
     * @param conversationId ID cuộc trò chuyện
     * @param limit Số lượng tin nhắn tối đa
     * @return Danh sách tin nhắn
     */
    public List<Message> findByConversationId(Long conversationId, int limit) {
        try {
            MessageDAO dao = new MessageDAO();
            List<Message> messages = dao.findByConversationId(conversationId, limit);
            return messages;
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy tin nhắn từ cuộc trò chuyện: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}