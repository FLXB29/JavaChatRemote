package app;

import app.model.User;
import app.service.UserService;
import app.util.PasswordUtil;
import app.dao.UserDAO;
import java.util.Scanner;

/**
 * Chương trình kiểm tra trực tiếp việc đổi mật khẩu với OTP
 * bằng cách gọi trực tiếp phương thức changePasswordWithOTP của UserService
 */
public class DirectPasswordChangeTest {

    public static void main(String[] args) {
        // Khởi tạo các service cần thiết
        UserService userService = new UserService();
        UserDAO userDAO = new UserDAO();
        Scanner scanner = new Scanner(System.in);
        
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
        
        // 2. Kiểm tra mật khẩu hiện tại
        boolean passwordCorrect = PasswordUtil.checkPassword(currentPassword, user.getPasswordHash());
        System.out.println("\nKiểm tra mật khẩu hiện tại: " + (passwordCorrect ? "Đúng" : "Sai"));
        if (!passwordCorrect) {
            System.err.println("Mật khẩu hiện tại không đúng!");
            return;
        }
        
        // 3. Tạo mã OTP mới
        System.out.println("\nĐang tạo mã OTP mới...");
        String otp = userService.generateOTP(username);
        if (otp == null) {
            System.err.println("Không thể tạo mã OTP. Kiểm tra email của người dùng.");
            return;
        }
        
        System.out.println("Đã tạo mã OTP: " + otp);
        System.out.println("Đã gửi mã OTP đến email: " + user.getEmail());
        System.out.println("Vui lòng kiểm tra email và nhập mã OTP nhận được:");
        
        // 4. Nhập mã OTP từ người dùng
        System.out.print("Nhập mã OTP: ");
        String inputOtp = scanner.nextLine().trim();
        
        // 5. Thực hiện đổi mật khẩu với OTP
        System.out.println("\nĐang thực hiện đổi mật khẩu với OTP...");
        System.out.println("- Username: " + username);
        System.out.println("- Mật khẩu hiện tại: " + currentPassword);
        System.out.println("- Mật khẩu mới: " + newPassword);
        System.out.println("- Mã OTP nhập vào: " + inputOtp);
        
        boolean success = userService.changePasswordWithOTP(username, currentPassword, newPassword, inputOtp);
        
        if (success) {
            System.out.println("\nĐổi mật khẩu thành công!");
            
            // 6. Kiểm tra mật khẩu mới
            user = userDAO.findByUsername(username); // Refresh user data
            boolean newPasswordCorrect = PasswordUtil.checkPassword(newPassword, user.getPasswordHash());
            System.out.println("Kiểm tra mật khẩu mới: " + (newPasswordCorrect ? "Đúng" : "Sai"));
        } else {
            System.err.println("\nĐổi mật khẩu thất bại!");
            
            // 7. Kiểm tra lại thông tin OTP trong database
            user = userDAO.findByUsername(username); // Refresh user data
            System.out.println("\nThông tin OTP trong database:");
            System.out.println("- Mã OTP trong DB: " + user.getOtpCode());
            System.out.println("- Mã OTP nhập vào: " + inputOtp);
            System.out.println("- OTP có khớp không: " + (user.getOtpCode() != null && user.getOtpCode().equals(inputOtp)));
            
            if (user.getOtpExpiryTime() != null) {
                long currentTime = System.currentTimeMillis();
                boolean expired = currentTime > user.getOtpExpiryTime();
                System.out.println("- OTP đã hết hạn: " + (expired ? "Đã hết hạn" : "Còn hiệu lực"));
                if (!expired) {
                    long remainingSeconds = (user.getOtpExpiryTime() - currentTime) / 1000;
                    System.out.println("- Thời gian còn lại: " + remainingSeconds + " giây");
                }
            }
        }
        
        scanner.close();
    }
}