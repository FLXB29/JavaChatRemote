package app.dao;

import app.model.Message;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository                 // ⭐ thêm dòng này

public class MessageDAO {

    public void save(Message message) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(message);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        }
    }

    public Message findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Message.class, id);
        }
    }

    // Lấy tất cả tin nhắn của 1 conversation (hoặc tuỳ logic)
    public List<Message> findByConversationId(Long convId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Message m WHERE m.conversation.id = :cid ORDER BY m.createdAt ASC";
            return session.createQuery(hql, Message.class)
                    .setParameter("cid", convId)
                    .list();
        }
    }
}
