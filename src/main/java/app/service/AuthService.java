package app.service;

import java.util.HashMap;
import java.util.Map;

/**
 * AuthService mô phỏng logic đăng ký, đăng nhập (in-memory).
 * Thực tế có thể thay bằng DB + Hibernate.
 */
public class AuthService {

    // Lưu user/password đơn giản trong map
    private final Map<String, String> userStore = new HashMap<>();

    public AuthService() {
        // Tạo sẵn 1 tài khoản test
        userStore.put("test", "123");
    }

    /**
     * Đăng ký: nếu user đã tồn tại thì trả về false, ngược lại lưu pass.
     */
    public boolean register(String username, String password) {
        if (userStore.containsKey(username)) {
            return false; // user đã tồn tại
        }
        userStore.put(username, password);
        return true;
    }

    /**
     * Đăng nhập: kiểm tra username và password
     */
    public boolean login(String username, String password) {
        if (!userStore.containsKey(username)) return false;
        return userStore.get(username).equals(password);
    }
}
