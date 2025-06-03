package app.dao;

import app.model.User;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.util.List;

public class UserDAO {

    public UserDAO() {
    }

    public void save(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(user);
            tx.commit();
        }
    }

    public void update(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(user);
            tx.commit();
        }
    }

    public User findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(User.class, id);
        }
    }

    public User findByUsername(String username) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User WHERE username = :uname";
            return session.createQuery(hql, User.class)
                    .setParameter("uname", username)
                    .uniqueResult();
        }
    }

    public List<User> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM User", User.class).list();
        }
    }

    /**
     * Tìm kiếm người dùng theo tiền tố của username, sử dụng LIKE.
     * @param keyword Tiền tố username cần tìm.
     * @return Danh sách người dùng phù hợp.
     */
    public List<User> searchByUsernamePrefix(String keyword) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Sử dụng Native SQL với LIKE
            // Lưu ý: tên bảng và tên cột phải khớp với định nghĩa trong CSDL
            String sql = "SELECT * FROM users WHERE username LIKE :kw LIMIT 10";
            return session.createNativeQuery(sql, User.class)
                    // Thêm ký tự '%' vào cuối keyword để tìm kiếm tiền tố
                    .setParameter("kw", keyword + "%")
                    .list();
        }
    }
}