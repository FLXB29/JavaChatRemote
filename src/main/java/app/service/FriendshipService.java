package app.service;

import app.dao.ConversationDAO;
import app.dao.FriendshipDAO;
import app.dao.MembershipDAO;
import app.model.Conversation;
import app.model.Friendship;
import app.model.Membership;
import app.model.User;
import java.util.List;
import java.util.stream.Collectors;

/** Service "1 lớp duy nhất" – KHÔNG còn Impl */
public class FriendshipService {

    private final FriendshipDAO dao = new FriendshipDAO();

    /* Sắp xếp cặp theo id để giữ UNIQUE(user1_id,user2_id) */
    private static User[] ordered(User a, User b) {
        return (a.getId() < b.getId()) ? new User[]{a, b} : new User[]{b, a};
    }

    /* ============ THAO TÁC ============ */

    public void sendFriendRequest(User from, User to) {
        if (from.equals(to)) throw new IllegalArgumentException("Không thể tự kết bạn");
        if (dao.find(from, to) != null)
            throw new IllegalStateException("Đã có quan hệ hoặc đang chờ xác nhận");

        User[] p = ordered(from, to);
        dao.save(new Friendship(p[0], p[1], Friendship.Status.PENDING));
    }

    public void acceptFriendRequest(User from, User to) {
        Friendship f = dao.find(from, to);
        if (f == null || f.getStatus() != Friendship.Status.PENDING)
            throw new IllegalStateException("Không có lời mời hợp lệ");
        f.setStatus(Friendship.Status.ACCEPTED);
        dao.update(f);

        // Create private conversation if it doesn't exist
        ConversationDAO convDAO = new ConversationDAO();
        String nameA = (from.getUsername().compareTo(to.getUsername()) < 0) ? from.getUsername() : to.getUsername();
        String nameB = (from.getUsername().compareTo(to.getUsername()) < 0) ? to.getUsername() : from.getUsername();
        String convName = nameA + "|" + nameB;

        Conversation conv = convDAO.findByName(convName);
        if (conv == null) {
            conv = new Conversation("PRIVATE", convName);
            convDAO.save(conv);

            // Add both users as members
            MembershipDAO membershipDAO = new MembershipDAO();
            membershipDAO.save(new Membership(from, conv, "member"));
            membershipDAO.save(new Membership(to, conv, "member"));
        }
    }

    public void rejectFriendRequest(User from, User to) {
        Friendship f = dao.find(from, to);
        if (f == null || f.getStatus() != Friendship.Status.PENDING)
            throw new IllegalStateException("Không có lời mời hợp lệ");
        f.setStatus(Friendship.Status.REJECTED);
        dao.update(f);
    }

    public void unfriend(User a, User b) {
        Friendship f = dao.find(a, b);
        if (f != null) dao.delete(f);
    }

    /* ============ TRUY VẤN ============ */

    public Friendship.Status getFriendshipStatus(User a, User b) {
        Friendship f = dao.find(a, b);
        return f == null ? null : f.getStatus();
    }

    /**
     * Lấy đối tượng Friendship giữa hai người dùng
     * @param user1 User thứ nhất
     * @param user2 User thứ hai
     * @return Đối tượng Friendship, hoặc null nếu không tồn tại
     */
    public Friendship getFriendshipBetween(User user1, User user2) {
        return dao.find(user1, user2);
    }

    /** Lời mời gửi tới user (PENDING) - chỉ lấy lời mời mà user là người nhận */
    public List<Friendship> getPendingRequests(User u) {
        return dao.findPending(u);
    }
    
    /** Lấy tất cả lời mời kết bạn liên quan đến user (cả gửi và nhận) */
    public List<Friendship> getAllPendingRequests(User u) {
        return dao.findAllPending(u);
    }

    /** Danh sách bạn bè (đã ACCEPTED) */
    public List<User> getFriends(User u) {
        return dao.findFriends(u).stream()
                .map(f -> f.getUser1().equals(u) ? f.getUser2() : f.getUser1())
                .collect(Collectors.toList());
    }
}
