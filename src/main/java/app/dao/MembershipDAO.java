package app.dao;

import app.model.Membership;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class MembershipDAO {
    public void save(Membership m){
        try(Session s = HibernateUtil.getSessionFactory().openSession()){
            Transaction tx = s.beginTransaction();
            s.persist(m);
            tx.commit();
        }
    }
}
