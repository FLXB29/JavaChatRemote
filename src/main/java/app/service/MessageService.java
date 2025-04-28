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
}