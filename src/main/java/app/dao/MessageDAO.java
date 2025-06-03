package app.dao;

import app.model.Message;
import app.util.DatabaseEncryptionUtil;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageDAO {

    /**
     * Lưu tin nhắn vào database với mã hóa tự động
     */
    public void save(Message message) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            // Content should already be encrypted in MessageService
            // Avoid double encryption by checking if it already has a prefix
            if (message.getContent() != null && !message.getContent().startsWith("[FILE]")
                    && !message.getContent().startsWith("DBENC:")
                    && !message.getContent().startsWith("ENCRYPTED:")) {
                System.out.println("[WARN] Message content was not encrypted before reaching DAO - encrypting now");
                String encryptedContent = DatabaseEncryptionUtil.encrypt(message.getContent());
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
                String decryptedContent = DatabaseEncryptionUtil.decrypt(message.getContent());
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
                    String decryptedContent = DatabaseEncryptionUtil.decrypt(message.getContent());
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
                    String decryptedContent = DatabaseEncryptionUtil.decrypt(message.getContent());
                    message.setContent(decryptedContent);
                }
            }

            return messages;
        }
    }
}