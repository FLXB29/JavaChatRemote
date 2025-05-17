package app.service;

import app.dao.MessageDAO;
import app.dao.UserDAO;
import app.model.Conversation;
import app.model.Message;
import app.model.User;
import app.util.HibernateUtil;
import org.hibernate.Session;

import java.util.List;
import java.io.File;
import app.model.FileAttachment;
import java.util.ArrayList;
import app.controller.ChatController.RecentChatCellData;
import app.ServiceLocator;

public class MessageService {
    private final MessageDAO messageDAO;
    private final UserDAO userDAO;

    public MessageService() {
        this.messageDAO = new MessageDAO();
        this.userDAO = new UserDAO();
    }

    public void saveMessage(Conversation conv, String senderUsername, String content, String filePath) {
        // Tìm User từ username
        User sender = userDAO.findByUsername(senderUsername);
        
        // Tạo Message trước (không có attachment)
        Message msg = new Message(conv, sender, content);
        
        // Nếu có file path, tạo và gắn FileAttachment
        if (filePath != null) {
            File file = new File(filePath);
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
    }

    private String getMimeType(File file) {
        try {
            return java.nio.file.Files.probeContentType(file.toPath());
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    public List<Message> getMessagesByConversation(Long convId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Message m WHERE m.conversation.id = :cid ORDER BY m.createdAt ASC";
            return session.createQuery(hql, Message.class)
                    .setParameter("cid", convId)
                    .list();
        }
    }

    public List<Message> getMessagesWithUser(String userA, String userB) {
        // Giả sử có hàm getAllMessages() trả về tất cả message
        List<Message> all = getAllMessages();
        List<Message> result = new ArrayList<>();
        for (Message m : all) {
            String sender = m.getSender().getUsername();
            String receiver = m.getConversation().getName(); // Giả sử name là username bên kia
            if ((sender.equals(userA) && receiver.equals(userB)) ||
                (sender.equals(userB) && receiver.equals(userA))) {
                result.add(m);
            }
        }
        return result;
    }

    public List<Message> getAllMessages() {
        return messageDAO.findAll();
    }

    public List<RecentChatCellData> getRecentChats(User user) {
        // Lấy tất cả conversation của user
        List<Conversation> convs = ServiceLocator.conversationDAO().findAllOfUser(user.getUsername());
        List<RecentChatCellData> result = new ArrayList<>();
        for (Conversation c : convs) {
            List<Message> msgs = getMessagesByConversation(c.getId());
            Message lastMsg = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);
            String lastMessage = lastMsg != null ? lastMsg.getContent() : "";
            String time = lastMsg != null ? lastMsg.getCreatedAt().toLocalTime().toString() : "";
            String chatName = c.getType().equals("PRIVATE") ? c.getName() : c.getName();
            String avatarPath = null; // Có thể lấy avatar nhóm hoặc bạn bè nếu cần
            int unread = 0; // Có thể lấy từ MessageReceiptService nếu muốn
            result.add(new RecentChatCellData(chatName, lastMessage, time, avatarPath, unread));
        }
        // Sắp xếp theo lastMsg time desc
        result.sort((a, b) -> b.time.compareTo(a.time));
        return result;
    }
}