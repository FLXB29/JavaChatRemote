package app.dao;

import app.model.Friendship;
import app.model.User;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.util.List;

public class FriendshipDAO {

    /* ---------- C R U D ---------- */

    public void save(Friendship f) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(f);
            tx.commit();
        }
    }

    public void update(Friendship f) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.merge(f);
            tx.commit();
        }
    }

    public void delete(Friendship f) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.remove(s.contains(f) ? f : s.merge(f));
            tx.commit();
        }
    }

    /* ---------- TRUY VẤN ---------- */

    /** Quan hệ giữa a và b (kể cả đảo chiều) */
    public Friendship find(User a, User b) {
        String hql = """
            FROM Friendship f
            WHERE (f.user1 = :u1 AND f.user2 = :u2)
               OR (f.user1 = :u2 AND f.user2 = :u1)
            """;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(hql, Friendship.class)
                    .setParameter("u1", a).setParameter("u2", b)
                    .uniqueResult();
        }
    }

    /** Danh sách bạn bè (đã ACCEPTED) của user */
    public List<Friendship> findFriends(User u) {
        String hql = """
            FROM Friendship f
            WHERE (f.user1 = :u OR f.user2 = :u)
              AND f.status = 'ACCEPTED'
            """;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(hql, Friendship.class)
                    .setParameter("u", u)
                    .list();
        }
    }

    /** Danh sách lời mời đến user (PENDING, user đóng vai user2 – người nhận) */
    public List<Friendship> findPending(User u) {
        String hql = "FROM Friendship f WHERE f.user2 = :u AND f.status = 'PENDING'";
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(hql, Friendship.class)
                    .setParameter("u", u)
                    .list();
        }
    }
}
