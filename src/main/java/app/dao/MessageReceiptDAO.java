package app.dao;

import app.model.Message;
import app.model.MessageReceipt;
import app.model.User;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.util.List;

public class MessageReceiptDAO {

    public void save(MessageReceipt r) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(r);
            tx.commit();
        }
    }

    public void update(MessageReceipt r) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.merge(r);
            tx.commit();
        }
    }

    public MessageReceipt find(User u, Message m) {
        String hql = "FROM MessageReceipt r WHERE r.user = :u AND r.message = :m";
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(hql, MessageReceipt.class)
                    .setParameter("u", u).setParameter("m", m)
                    .uniqueResult();
        }
    }

    public List<MessageReceipt> findByMessage(Message m) {
        String hql = "FROM MessageReceipt r WHERE r.message = :m";
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(hql, MessageReceipt.class)
                    .setParameter("m", m)
                    .list();
        }
    }
}
