package app;

import app.model.User;
import app.service.UserService;
import app.util.PasswordUtil;
import app.dao.UserDAO;

/**
 * Chương trình kiểm tra quá trình đổi mật khẩu với OTP
 * Sử dụng cho người dùng "anh" với email "phuclethanh2906@gmail.com"
 */
public class PasswordChangeTest {

    public static void main(String[] args) {
        // Khởi tạo các service cần thiết
        UserService userService = new UserService();
        UserDAO userDAO = new UserDAO();
        
        // Thông tin người dùng cần đổi mật khẩu
        String username = "anh";
        String currentPassword = "123";
        String newPassword = "1234";
        
        // 1. Kiểm tra thông tin người dùng trong database
        User user = userDAO.findByUsername(username);
        if (user == null) {
            System.err.println("Không tìm thấy người dùng: " + username);
            return;
        }
        
        System.out.println("Thông tin người dùng:");
        System.out.println("- Username: " + user.getUsername());
        System.out.println("- Email: " + user.getEmail());
        System.out.println("- Mật khẩu hiện tại (hash): " + user.getPasswordHash());
        
        // 2. Kiểm tra mật khẩu hiện tại
        boolean passwordCorrect = PasswordUtil.checkPassword(currentPassword, user.getPasswordHash());
        System.out.println("\nKiểm tra mật khẩu hiện tại: " + (passwordCorrect ? "Đúng" : "Sai"));
        if (!passwordCorrect) {
            System.err.println("Mật khẩu hiện tại không đúng!");
            return;
        }
        
        // 3. Tạo mã OTP
        System.out.println("\nĐang tạo mã OTP...");
        String otp = userService.generateOTP(username);
        if (otp == null) {
            System.err.println("Không thể tạo mã OTP. Kiểm tra email của người dùng.");
            return;
        }
        
        System.out.println("Đã tạo mã OTP: " + otp);
        System.out.println("Đã gửi mã OTP đến email: " + user.getEmail());
        
        // 4. Kiểm tra lại thông tin OTP trong database
        user = userDAO.findByUsername(username); // Refresh user data
        System.out.println("\nThông tin OTP trong database:");
        System.out.println("- Mã OTP: " + user.getOtpCode());
        System.out.println("- Thời gian hết hạn: " + user.getOtpExpiryTime());
        
        // 5. Thực hiện đổi mật khẩu với OTP
        System.out.println("\nĐang thực hiện đổi mật khẩu với OTP...");
        boolean success = userService.changePasswordWithOTP(username, currentPassword, newPassword, otp);
        
        if (success) {
            System.out.println("Đổi mật khẩu thành công!");
            
            // 6. Kiểm tra mật khẩu mới
            user = userDAO.findByUsername(username); // Refresh user data
            boolean newPasswordCorrect = PasswordUtil.checkPassword(newPassword, user.getPasswordHash());
            System.out.println("Kiểm tra mật khẩu mới: " + (newPasswordCorrect ? "Đúng" : "Sai"));
            
            // 7. Kiểm tra OTP đã bị xóa chưa
            System.out.println("OTP sau khi đổi mật khẩu: " + (user.getOtpCode() == null ? "Đã xóa" : "Chưa xóa"));
        } else {
            System.err.println("Đổi mật khẩu thất bại!");
            System.err.println("Kiểm tra lại mã OTP và mật khẩu hiện tại.");
        }
    }
}