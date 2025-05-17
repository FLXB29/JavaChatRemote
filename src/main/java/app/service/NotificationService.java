package app.service;

import app.dao.NotificationDAO;
import app.model.Notification;
import app.model.User;
import java.util.List;

public class NotificationService {

    private final NotificationDAO dao = new NotificationDAO();

    /** Tạo notification  (dùng trong ServerApp) */
    public void createNotification(User receiver, String type, String payload) {
        dao.save(new Notification(receiver, type, payload));
    }

    /** Lấy & đánh dấu đã đọc */
    public List<Notification> fetchUnread(User receiver) {
        List<Notification> list = dao.findUnread(receiver);
        list.forEach(n -> { n.setRead(true); dao.update(n); });
        return list;
    }
}
