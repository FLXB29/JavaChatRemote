package app.dao;

import app.model.Membership;
import app.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class MembershipDAO {
    public void save(Membership m){
        try(Session s = HibernateUtil.getSessionFactory().openSession()){
            Transaction tx = s.beginTransaction();
            s.persist(m);
            tx.commit();
        } catch (Exception e) {
            System.out.println("[ERROR] Error saving membership: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Check if a user has membership in a conversation
     */
    public boolean hasMembership(Long conversationId, Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(m) FROM Membership m " +
                    "WHERE m.conversation.id = :convId AND m.user.id = :userId";

            Long count = session.createQuery(hql, Long.class)
                    .setParameter("convId", conversationId)
                    .setParameter("userId", userId)
                    .uniqueResult();

            return count != null && count > 0;
        } catch (Exception e) {
            System.out.println("[ERROR] Error checking membership: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Check if both users have memberships in a conversation
     */
    public boolean hasUserMemberships(Long conversationId, String usernameA, String usernameB) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // First, get the count of memberships for these users in this conversation
            String hql = "SELECT COUNT(m) FROM Membership m " +
                    "JOIN m.user u " +
                    "WHERE m.conversation.id = :convId " +
                    "AND u.username IN (:usernameA, :usernameB)";

            Long count = session.createQuery(hql, Long.class)
                    .setParameter("convId", conversationId)
                    .setParameter("usernameA", usernameA)
                    .setParameter("usernameB", usernameB)
                    .uniqueResult();

            // If we have 2 memberships, both users are members
            return count != null && count == 2;
        } catch (Exception e) {
            System.out.println("[ERROR] Error checking memberships: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Get all memberships for a conversation
     */
    public List<Membership> findByConversation(Long conversationId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Membership m WHERE m.conversation.id = :convId";

            return session.createQuery(hql, Membership.class)
                    .setParameter("convId", conversationId)
                    .list();
        } catch (Exception e) {
            System.out.println("[ERROR] Error finding memberships: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    /**
     * Get membership for a specific user in a conversation
     */
    public Membership findByUserAndConversation(Long userId, Long conversationId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Membership m " +
                    "WHERE m.user.id = :userId AND m.conversation.id = :convId";

            return session.createQuery(hql, Membership.class)
                    .setParameter("userId", userId)
                    .setParameter("convId", conversationId)
                    .uniqueResult();
        } catch (Exception e) {
            System.out.println("[ERROR] Error finding membership: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

}
