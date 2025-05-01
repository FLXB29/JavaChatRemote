package app.service;

import app.dao.UserDAO;
import app.model.User;
import app.util.AvatarUtil;
import app.util.PasswordUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public boolean register(String username, String plainPassword, String email, String fullName) {
        // check trùng username
        User existing = userDAO.findByUsername(username);
        if (existing != null) {
            return false; // user đã tồn tại
        }

        // Hash password rồi lưu
        String hash = PasswordUtil.hashPassword(plainPassword);
        User newUser = new User(username, hash);
        newUser.setEmail(email);
        newUser.setFullName(fullName);
        newUser.setUseDefaultAvatar(true);
        
        // Tạo avatar mặc định
        String avatarPath = AvatarUtil.createDefaultAvatar(username);
        if (avatarPath != null) {
            newUser.setAvatarPath(avatarPath);
        }
        
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
    
    /**
     * Cập nhật thông tin người dùng
     */
    public boolean updateUserInfo(String username, String fullName, String email) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            return false;
        }
        
        user.setFullName(fullName);
        user.setEmail(email);
        userDAO.update(user);
        return true;
    }
    
    /**
     * Cập nhật avatar người dùng
     */
    public boolean updateAvatar(String username, File imageFile) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            return false;
        }
        
        try {
            // Tạo thư mục avatars nếu chưa tồn tại
            Path avatarsDir = Paths.get("uploads", "avatars");
            if (!Files.exists(avatarsDir)) {
                Files.createDirectories(avatarsDir);
            }
            
            // Tạo tên file duy nhất
            String fileName = username + "_" + System.currentTimeMillis() + 
                    imageFile.getName().substring(imageFile.getName().lastIndexOf("."));
            Path targetPath = avatarsDir.resolve(fileName);
            
            // Sao chép file
            Files.copy(imageFile.toPath(), targetPath);
            
            // Cập nhật user với đường dẫn tương đối
            user.setAvatarPath(targetPath.toString().replace('\\', '/'));
            user.setUseDefaultAvatar(false);
            userDAO.update(user);
            
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Chuyển sang sử dụng avatar mặc định
     */
    public boolean useDefaultAvatar(String username) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            return false;
        }
        
        try {
            // Tạo avatar mặc định
            String avatarPath = AvatarUtil.createDefaultAvatar(username);
            if (avatarPath != null) {
                // Đảm bảo đường dẫn tương đối
                user.setAvatarPath(avatarPath.replace('\\', '/'));
                user.setUseDefaultAvatar(true);
                userDAO.update(user);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Thay đổi mật khẩu
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            return false;
        }
        
        // Kiểm tra mật khẩu cũ
        if (!PasswordUtil.checkPassword(oldPassword, user.getPasswordHash())) {
            return false;
        }
        
        // Hash mật khẩu mới
        String hash = PasswordUtil.hashPassword(newPassword);
        user.setPasswordHash(hash);
        userDAO.update(user);
        return true;
    }
    
    /**
     * Thay đổi mật khẩu với xác thực hai yếu tố
     */
    public boolean changePasswordWithOTP(String username, String oldPassword, String newPassword, String otp) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            return false;
        }
        
        // Kiểm tra mật khẩu cũ
        if (!PasswordUtil.checkPassword(oldPassword, user.getPasswordHash())) {
            return false;
        }
        
        // Kiểm tra mã OTP
        if (!Objects.equals(otp, user.getOtpCode()) || 
            System.currentTimeMillis() > user.getOtpExpiryTime()) {
            return false;
        }
        
        // Hash mật khẩu mới
        String hash = PasswordUtil.hashPassword(newPassword);
        user.setPasswordHash(hash);
        
        // Xóa mã OTP
        user.setOtpCode(null);
        user.setOtpExpiryTime(null);
        
        userDAO.update(user);
        return true;
    }
    
    /**
     * Tạo mã OTP cho xác thực hai yếu tố
     */
    public String generateOTP(String username) {
        User user = userDAO.findByUsername(username);
        if (user == null || user.getEmail() == null) {
            return null;
        }
        
        // Tạo mã OTP
        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        
        // Lưu mã OTP và thời gian hết hạn (5 phút)
        user.setOtpCode(otp);
        user.setOtpExpiryTime(System.currentTimeMillis() + 5 * 60 * 1000);
        
        userDAO.update(user);
        
        // Gửi mã OTP qua email (tạm thời bỏ qua)
        // EmailUtil.sendOTPEmail(user.getEmail(), otp);
        
        return otp;
    }
}
