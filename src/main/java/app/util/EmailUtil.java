package app.util;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.Random;

/**
 * Tiện ích để gửi email OTP cho xác thực hai yếu tố
 */
public class EmailUtil {
    
    // Thông tin tài khoản email để gửi (cần thay thế bằng tài khoản thực)
    private static final String EMAIL_USERNAME = "phuclethanh2906@gmail.com";
    private static final String EMAIL_PASSWORD = "cussawihqadhnpld"; // Mật khẩu ứng dụng từ Google
    
    /**
     * Tạo mã OTP ngẫu nhiên 6 chữ số
     */
    public static String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // Số 6 chữ số
        return String.valueOf(otp);
    }
    
    /**
     * Gửi mã OTP qua email
     * @param recipientEmail Email người nhận
     * @param otp Mã OTP
     * @return true nếu gửi thành công, false nếu có lỗi
     */
    public static boolean sendOTPEmail(String recipientEmail, String otp) {
        // Cấu hình thông tin email
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        
        // Tạo phiên gửi email
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_USERNAME, EMAIL_PASSWORD);
            }
        });
        
        try {
            // Tạo tin nhắn
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_USERNAME));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("Mã xác thực OTP");
            message.setText("Mã OTP của bạn là: " + otp + "\n\nMã này sẽ hết hạn sau 5 phút.");
            
            // Gửi tin nhắn
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Kiểm tra xem mã OTP có hợp lệ không
     * @param userOtp Mã OTP người dùng nhập
     * @param storedOtp Mã OTP lưu trong cơ sở dữ liệu
     * @param expiryTime Thời gian hết hạn của mã OTP
     * @return true nếu mã OTP hợp lệ, false nếu không
     */
    public static boolean validateOTP(String userOtp, String storedOtp, Long expiryTime) {
        if (userOtp == null || storedOtp == null || expiryTime == null) {
            return false;
        }
        
        // Kiểm tra xem mã OTP có khớp không
        if (!userOtp.equals(storedOtp)) {
            return false;
        }
        
        // Kiểm tra xem mã OTP có hết hạn chưa
        long currentTime = System.currentTimeMillis();
        return currentTime <= expiryTime;
    }
} 