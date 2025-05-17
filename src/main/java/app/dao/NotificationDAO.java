package app.dao;

import app.model.Notification;
import app.model.User;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.util.List;

public class NotificationDAO {

    public void save(Notification n) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(n);
            tx.commit();
        }
    }

    public void update(Notification n) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.merge(n);
            tx.commit();
        }
    }

    /** Thông báo chưa đọc của user */
    public List<Notification> findUnread(User u) {
        String hql = "FROM Notification n WHERE n.user = :u AND n.isRead = false";
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(hql, Notification.class)
                    .setParameter("u", u)
                    .list();
        }
    }
}
