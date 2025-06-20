package app.service;

import app.dao.UserDAO;
import app.model.User;
import app.util.AvatarUtil;
import app.util.EmailUtil;
import app.util.PasswordUtil;
import app.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UserService {
    private final UserDAO userDAO;
    private static String currentUsername;

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
        boolean ok = PasswordUtil.checkPassword(plainPassword, user.getPasswordHash());
        if (ok) currentUsername = username;
        return ok;
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
        
        // Kiểm tra file tồn tại
        if (imageFile == null || !imageFile.exists()) {
            System.err.println("File avatar không tồn tại: " + (imageFile != null ? imageFile.getPath() : "null"));
            return false;
        }

        try {
            // Tạo thư mục avatars với đường dẫn tuyệt đối
            Path avatarsDir = Paths.get(System.getProperty("user.dir"), "uploads", "avatars");
            if (!Files.exists(avatarsDir)) {
                Files.createDirectories(avatarsDir);
            }

            // Xóa avatar cũ nếu có
            String oldPath = user.getAvatarPath();
            if (oldPath != null && !oldPath.contains("avatar_")) { // Không xóa avatar mặc định
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                } catch (Exception e) {
                    // Bỏ qua lỗi xóa file cũ
                }
            }

            // Tạo tên file chuẩn: username.png
            String standardFileName = username + ".png";
            Path targetPath = avatarsDir.resolve(standardFileName);

            // Copy file mới (ghi đè nếu đã tồn tại)
            Files.copy(imageFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Cập nhật user
            user.setAvatarPath(PathUtil.normalizePath(targetPath.toString()));
            user.setUseDefaultAvatar(false);
            userDAO.update(user);

            // Sync to cache với đường dẫn tuyệt đối
            Path cacheDir = Paths.get(System.getProperty("user.dir"), "avatar_cache");
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            Path cacheFile = cacheDir.resolve(standardFileName);
            Files.copy(targetPath, cacheFile, StandardCopyOption.REPLACE_EXISTING);

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
                // Lưu đường dẫn tuyệt đối nhưng đảm bảo định dạng đúng
                user.setAvatarPath(PathUtil.normalizePath(avatarPath));
                user.setUseDefaultAvatar(true);
                userDAO.update(user);
                
                // Sync to cache với đường dẫn tuyệt đối
                try {
                    Path avatarFile = Paths.get(avatarPath);
                    String fileName = avatarFile.getFileName().toString();
                    
                    Path cacheDir = Paths.get(System.getProperty("user.dir"), "avatar_cache");
                    if (!Files.exists(cacheDir)) {
                        Files.createDirectories(cacheDir);
                    }
                    
                    Path cacheFile = cacheDir.resolve(fileName);
                    Files.copy(avatarFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // Bỏ qua lỗi cache
                    e.printStackTrace();
                }
                
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
            System.err.println("Đổi mật khẩu thất bại: Mật khẩu hiện tại không đúng cho người dùng " + username);
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
        if (!Objects.equals(otp, user.getOtpCode())) {
            System.err.println("Đổi mật khẩu thất bại: Mã OTP không đúng cho người dùng " + username);
            return false;
        }
        
        // Kiểm tra thời gian hết hạn của OTP
        if (System.currentTimeMillis() > user.getOtpExpiryTime()) {
            System.err.println("Đổi mật khẩu thất bại: Mã OTP đã hết hạn cho người dùng " + username);
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
        
        // Gửi mã OTP qua email
        EmailUtil.sendOTPEmail(user.getEmail(), otp);
        
        return otp;
    }

    public User getCurrentUser() {
        if (currentUsername == null) return null;
        return userDAO.findByUsername(currentUsername);
    }

    public List<User> searchUsers(String keyword) {
        return userDAO.searchByUsernamePrefix(keyword);
    }

    /**
     * Cập nhật trạng thái avatar người dùng trong database
     */
    public boolean updateUserAvatar(User user) {
        if (user == null) {
            return false;
        }
        
        try {
            userDAO.update(user);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
