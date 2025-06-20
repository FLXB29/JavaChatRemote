package app.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {
    private static SessionFactory sessionFactory;

    static {
        try {
            // Load hibernate.cfg.xml mặc định
            Configuration cfg = new Configuration().configure();
            sessionFactory = cfg.buildSessionFactory();
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
    public static void initialize() {
        getSessionFactory();  // Ensures the session factory is created
    }
}
