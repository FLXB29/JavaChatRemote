package app.dao;

import app.model.Conversation;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class ConversationDAO {
    public void save(Conversation conv) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(conv);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        }
    } // hệt như UserDAO

    public Conversation findByName(String name) {
        // Tìm conversation có `name` trùng
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Conversation c WHERE c.name = :n";
            return session.createQuery(hql, Conversation.class)
                    .setParameter("n", name)
                    .uniqueResult();
        }
    }
    public Conversation findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Conversation.class, id);
        }
    }
    public List<Conversation> findAllOfUser(String username) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = """
                SELECT DISTINCT c FROM Conversation c
                JOIN c.memberships m
                JOIN m.user u
                WHERE u.username = :username""";

            return session.createQuery(hql, Conversation.class)
                    .setParameter("username", username)
                    .getResultList();
        }
    }
    /** Lấy Conversation + memberships + user (join-fetch) */
    public Conversation findByIdWithMembers(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = """
            select distinct c
            from Conversation c
            left join fetch c.memberships m
            left join fetch m.user
            where c.id = :cid
            """;
            return session.createQuery(hql, Conversation.class)
                    .setParameter("cid", id)
                    .uniqueResult();
        }
    }
    public List<Conversation> findAllOfUserByType(String username, String type) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = """
                SELECT DISTINCT c FROM Conversation c
                JOIN c.memberships m
                JOIN m.user u
                WHERE u.username = :username
                AND c.type = :type""";

            return session.createQuery(hql, Conversation.class)
                    .setParameter("username", username)
                    .setParameter("type", type)
                    .getResultList();
        }
    }
}
