package app.dao;

import app.model.FileAttachment;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class FileAttachmentDAO {
    public void save(FileAttachment fa){
        try(Session s = HibernateUtil.getSessionFactory().openSession()){
            Transaction tx = s.beginTransaction();
            s.persist(fa); tx.commit();
        }
    }
    public FileAttachment findById(String id){
        try(Session s = HibernateUtil.getSessionFactory().openSession()){
            return s.get(FileAttachment.class, id);
        }
    }

    /* ➌  tìm file đã có qua sha256 */
    public FileAttachment findByHash(String hash){
        try(Session s = HibernateUtil.getSessionFactory().openSession()){
            return s
                    .createQuery("from FileAttachment where sha256 = :h", FileAttachment.class)
                    .setParameter("h", hash)
                    .uniqueResult();
        }
    }
}
