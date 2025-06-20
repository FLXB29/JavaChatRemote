package app;

import app.model.User;
import app.service.UserService;
import app.util.PasswordUtil;
import app.dao.UserDAO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Chương trình debug chi tiết quá trình xác thực OTP
 * Sử dụng cho người dùng "anh" với email "phuclethanh2906@gmail.com"
 */
public class OTPDebugger {

    public static void main(String[] args) {
        // Khởi tạo các service cần thiết
        UserDAO userDAO = new UserDAO();
        
        // Thông tin người dùng cần kiểm tra
        String username = "anh";
        String currentPassword = "123";
        
        // 1. Lấy thông tin người dùng từ database
        User user = userDAO.findByUsername(username);
        if (user == null) {
            System.err.println("Không tìm thấy người dùng: " + username);
            return;
        }
        
        System.out.println("===== THÔNG TIN NGƯỜI DÙNG =====");
        System.out.println("Username: " + user.getUsername());
        System.out.println("Email: " + user.getEmail());
        System.out.println("Password Hash: " + user.getPasswordHash());
        
        // 2. Kiểm tra mật khẩu hiện tại
        boolean passwordCorrect = PasswordUtil.checkPassword(currentPassword, user.getPasswordHash());
        System.out.println("\n===== KIỂM TRA MẬT KHẨU HIỆN TẠI =====");
        System.out.println("Mật khẩu nhập vào: " + currentPassword);
        System.out.println("Mật khẩu hash trong DB: " + user.getPasswordHash());
        System.out.println("Kết quả kiểm tra: " + (passwordCorrect ? "ĐÚNG" : "SAI"));
        
        // 3. Kiểm tra thông tin OTP
        System.out.println("\n===== THÔNG TIN OTP HIỆN TẠI =====");
        String otpCode = user.getOtpCode();
        Long otpExpiryTime = user.getOtpExpiryTime();
        
        System.out.println("Mã OTP trong DB: " + (otpCode != null ? otpCode : "<không có>"));
        
        if (otpExpiryTime != null) {
            // Chuyển đổi timestamp thành định dạng dễ đọc
            LocalDateTime expiryDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(otpExpiryTime), 
                ZoneId.systemDefault()
            );
            String formattedTime = expiryDateTime.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );
            
            System.out.println("Thời gian hết hạn: " + formattedTime);
            
            // Kiểm tra OTP có còn hiệu lực không
            boolean otpValid = System.currentTimeMillis() <= otpExpiryTime;
            System.out.println("OTP còn hiệu lực: " + (otpValid ? "CÒN" : "HẾT HẠN"));
            
            // Tính thời gian còn lại
            if (otpValid) {
                long remainingTime = (otpExpiryTime - System.currentTimeMillis()) / 1000; // seconds
                System.out.println("Thời gian còn lại: " + remainingTime + " giây");
            }
        } else {
            System.out.println("Thời gian hết hạn: <không có>");
        }
        
        // 4. Hướng dẫn kiểm tra OTP
        System.out.println("\n===== HƯỚNG DẪN KIỂM TRA OTP =====");
        System.out.println("1. Mở ProfileController.java và đặt điểm dừng (breakpoint) tại dòng sau:");
        System.out.println("   boolean otpOk = currentOtp != null && otpCode.equals(currentOtp) && LocalDateTime.now().isBefore(otpExpiryTime);");
        System.out.println("2. Chạy ứng dụng và thực hiện đổi mật khẩu");
        System.out.println("3. Khi breakpoint được kích hoạt, kiểm tra các giá trị:");
        System.out.println("   - currentOtp: Mã OTP được lưu trong bộ nhớ của ProfileController");
        System.out.println("   - otpCode: Mã OTP người dùng nhập vào");
        System.out.println("   - otpExpiryTime: Thời gian hết hạn của OTP");
        System.out.println("4. Đảm bảo rằng các giá trị này khớp với nhau");
        
        // 5. Hướng dẫn sửa lỗi
        System.out.println("\n===== HƯỚNG DẪN SỬA LỖI =====");
        System.out.println("1. Nếu mã OTP không khớp, kiểm tra xem có khoảng trắng thừa không");
        System.out.println("2. Nếu OTP đã hết hạn, yêu cầu mã OTP mới");
        System.out.println("3. Nếu mật khẩu hiện tại không đúng, kiểm tra lại mật khẩu");
        System.out.println("4. Nếu tất cả đều đúng nhưng vẫn gặp lỗi, kiểm tra lại logic trong UserService.changePasswordWithOTP()");
    }
}