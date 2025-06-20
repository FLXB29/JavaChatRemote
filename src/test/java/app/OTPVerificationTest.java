package app;

import app.model.User;
import app.service.UserService;
import app.dao.UserDAO;
import app.util.EmailUtil;
import java.time.LocalDateTime;
import java.util.Scanner;

/**
 * Chương trình kiểm tra trực tiếp việc xác thực OTP
 * mô phỏng logic trong ProfileController
 */
public class OTPVerificationTest {

    public static void main(String[] args) {
        // Khởi tạo các service cần thiết
        UserService userService = new UserService();
        UserDAO userDAO = new UserDAO();
        Scanner scanner = new Scanner(System.in);
        
        // Thông tin người dùng cần kiểm tra
        String username = "anh";
        
        // 1. Kiểm tra thông tin người dùng trong database
        User user = userDAO.findByUsername(username);
        if (user == null) {
            System.err.println("Không tìm thấy người dùng: " + username);
            return;
        }
        
        System.out.println("Thông tin người dùng:");
        System.out.println("- Username: " + user.getUsername());
        System.out.println("- Email: " + user.getEmail());
        
        // 2. Tạo mã OTP mới
        System.out.println("\nĐang tạo mã OTP mới...");
        String currentOtp = EmailUtil.generateOTP();
        LocalDateTime otpExpiryTime = LocalDateTime.now().plusMinutes(5);
        
        // Gửi email chứa mã OTP
        boolean success = EmailUtil.sendOTPEmail(user.getEmail(), currentOtp);
        
        if (success) {
            System.out.println("Đã tạo mã OTP: " + currentOtp);
            System.out.println("Đã gửi mã OTP đến email: " + user.getEmail());
            System.out.println("Thời gian hết hạn: " + otpExpiryTime);
            System.out.println("Vui lòng kiểm tra email và nhập mã OTP nhận được:");
        } else {
            System.err.println("Không thể gửi mã OTP. Vui lòng thử lại sau.");
            scanner.close();
            return;
        }
        
        // 3. Nhập mã OTP từ người dùng
        System.out.print("Nhập mã OTP: ");
        String otpCode = scanner.nextLine().trim();
        
        // 4. Xác thực OTP (mô phỏng logic trong ProfileController.onVerifyOtp)
        System.out.println("\nĐang xác thực OTP...");
        System.out.println("- Mã OTP đã tạo: " + currentOtp);
        System.out.println("- Mã OTP nhập vào: " + otpCode);
        System.out.println("- Thời gian hiện tại: " + LocalDateTime.now());
        System.out.println("- Thời gian hết hạn: " + otpExpiryTime);
        
        // Kiểm tra OTP có hợp lệ không
        boolean valid = currentOtp != null
                && otpCode.equals(currentOtp)
                && LocalDateTime.now().isBefore(otpExpiryTime);
        
        System.out.println("\nKết quả xác thực OTP:");
        System.out.println("- OTP có tồn tại: " + (currentOtp != null));
        System.out.println("- OTP có khớp: " + (currentOtp != null && otpCode.equals(currentOtp)));
        System.out.println("- OTP còn hiệu lực: " + (LocalDateTime.now().isBefore(otpExpiryTime)));
        System.out.println("- Kết quả cuối cùng: " + (valid ? "HỢP LỆ" : "KHÔNG HỢP LỆ"));
        
        // 5. Kiểm tra OTP trong database
        System.out.println("\nThông tin OTP trong database:");
        System.out.println("- Mã OTP trong DB: " + user.getOtpCode());
        if (user.getOtpExpiryTime() != null) {
            long currentTime = System.currentTimeMillis();
            boolean expired = currentTime > user.getOtpExpiryTime();
            System.out.println("- OTP đã hết hạn: " + (expired ? "Đã hết hạn" : "Còn hiệu lực"));
            if (!expired) {
                long remainingSeconds = (user.getOtpExpiryTime() - currentTime) / 1000;
                System.out.println("- Thời gian còn lại: " + remainingSeconds + " giây");
            }
        }
        
        // 6. So sánh OTP trong bộ nhớ và trong database
        System.out.println("\nSo sánh OTP trong bộ nhớ và trong database:");
        System.out.println("- OTP trong bộ nhớ: " + currentOtp);
        System.out.println("- OTP trong database: " + user.getOtpCode());
        System.out.println("- Có khớp nhau không: " + 
                (currentOtp != null && user.getOtpCode() != null && 
                 currentOtp.equals(user.getOtpCode())));
        
        scanner.close();
    }
}