package app;

import org.hibernate.Session;
import org.hibernate.Transaction;
import app.util.HibernateUtil;
import app.model.User;

public class TestHibernate {
    public static void main(String[] args) {
        // Mở session
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();

        // Tạo user
        User u = new User("alice", "hash_of_pass");
        session.persist(u);

        tx.commit();
        session.close();

        HibernateUtil.getSessionFactory().close();
        System.out.println("Done - check DB (mychatdb) for 'users' table");
    }
}
