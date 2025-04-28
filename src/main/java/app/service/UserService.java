package app.service;

import app.dao.UserDAO;
import app.model.User;
import app.util.PasswordUtil;
import java.util.Objects;

public class UserService {
    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }

    /**
     * Đăng ký tài khoản:
     *  - Kiểm tra username đã tồn tại chưa
     *  - Nếu chưa thì hash password và lưu vào DB
     */
    public boolean register(String username, String plainPassword) {
        // check trùng username
        User existing = userDAO.findByUsername(username);
        if (existing != null) {
            return false; // user đã tồn tại
        }

        // Hash password rồi lưu
        String hash = PasswordUtil.hashPassword(plainPassword);
        User newUser = new User(username, hash);
        userDAO.save(newUser);
        return true;
    }

    /**
     * Đăng nhập:
     *  - Tìm user theo username
     *  - Check hash password
     */
    public boolean login(String username, String plainPassword) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            return false;  // không có user
        }
        // So sánh password
        return PasswordUtil.checkPassword(plainPassword, user.getPasswordHash());
    }

    /**
     * Lấy User ra (nếu cần)
     */
    public User getUser(String username) {
        return userDAO.findByUsername(username);
    }
}
