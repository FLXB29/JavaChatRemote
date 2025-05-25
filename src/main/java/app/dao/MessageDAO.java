package app.dao;

import app.model.Message;
import app.util.HibernateUtil;
import app.util.MessageEncryptionService;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MessageDAO {

    private final MessageEncryptionService encryptionService = MessageEncryptionService.getInstance();

    /**
     * Lưu tin nhắn vào database với mã hóa tự động
     */
    public void save(Message message) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            // Mã hóa nội dung tin nhắn trước khi lưu
            if (message.getContent() != null) {
                String encryptedContent = encryptionService.encrypt(message.getContent());
                message.setContent(encryptedContent);
            }

            session.persist(message);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        }
    }

    public Message findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Message message = session.get(Message.class, id);

            // Giải mã nội dung tin nhắn sau khi lấy từ database
            if (message != null && message.getContent() != null) {
                String decryptedContent = encryptionService.decrypt(message.getContent());
                message.setContent(decryptedContent);
            }

            return message;
        }
    }

    /**
     * Lấy tất cả tin nhắn của 1 conversation và tự động giải mã
     */
    public List<Message> findByConversationId(Long convId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Message m WHERE m.conversation.id = :cid ORDER BY m.createdAt ASC";
            List<Message> messages = session.createQuery(hql, Message.class)
                    .setParameter("cid", convId)
                    .list();

            // Giải mã tất cả tin nhắn
            for (Message message : messages) {
                if (message.getContent() != null) {
                    String decryptedContent = encryptionService.decrypt(message.getContent());
                    message.setContent(decryptedContent);
                }
            }

            return messages;
        }
    }

    public List<Message> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Message> messages = session.createQuery("FROM Message", Message.class).list();

            // Giải mã tất cả tin nhắn
            for (Message message : messages) {
                if (message.getContent() != null) {
                    String decryptedContent = encryptionService.decrypt(message.getContent());
                    message.setContent(decryptedContent);
                }
            }

            return messages;
        }
    }

    /**
     * Mã hóa lại tất cả tin nhắn trong database với khóa mới
     * Chỉ nên dùng khi thay đổi khóa mã hóa
     */
    public int reencryptAllMessages() {
        int count = 0;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            List<Message> messages = session.createQuery("FROM Message", Message.class).list();

            for (Message message : messages) {
                if (message.getContent() != null) {
                    // Giải mã với khóa cũ
                    String decryptedContent = encryptionService.decrypt(message.getContent());
                    // Mã hóa lại với khóa mới
                    String reencryptedContent = encryptionService.encrypt(decryptedContent);
                    message.setContent(reencryptedContent);
                    session.merge(message);
                    count++;
                }
            }

            tx.commit();
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}