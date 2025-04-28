package app.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {

    // Hàm này để hash mật khẩu gốc
    public static String hashPassword(String plainTextPassword) {
        // BCrypt.gensalt() sẽ tự sinh "salt" ngẫu nhiên
        return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
    }

    // Hàm này để kiểm tra mật khẩu khi đăng nhập
    public static boolean checkPassword(String plainTextPassword, String hashedPassword) {
        return BCrypt.checkpw(plainTextPassword, hashedPassword);
    }
}
