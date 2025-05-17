package app.service;

import app.dao.FriendshipDAO;
import app.model.Friendship;
import app.model.User;
import java.util.List;
import java.util.stream.Collectors;

/** Service “1 lớp duy nhất” – KHÔNG còn Impl */
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
    }

    public void rejectFriendRequest(User from, User to) {
        Friendship f = dao.find(from, to);
        if (f == null || f.getStatus() != Friendship.Status.PENDING)
            throw new IllegalStateException("Không có lời mời hợp lệ");
        f.setStatus(Friendship.Status.BLOCKED);
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

    /** Lời mời gửi tới user (PENDING) */
    public List<Friendship> getPendingRequests(User u) {
        return dao.findPending(u);
    }

    /** Danh sách bạn bè (đã ACCEPTED) */
    public List<User> getFriends(User u) {
        return dao.findFriends(u).stream()
                .map(f -> f.getUser1().equals(u) ? f.getUser2() : f.getUser1())
                .collect(Collectors.toList());
    }
}
