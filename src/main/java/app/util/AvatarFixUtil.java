// AvatarFixUtil.java - Cập nhật toàn bộ class

package app.util;

import app.ServiceLocator;
import app.model.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvatarFixUtil {

    // Sử dụng PathUtil để lấy đường dẫn thay vì hardcode

    /**
     * Đồng bộ hóa avatar giữa database và file hệ thống
     */
    public static void fixAvatarIssues() {
        try {
            System.out.println("[AVATAR FIX] Bắt đầu đồng bộ avatar...");

            // Đảm bảo thư mục avatar tồn tại
            ensureDirectoriesExist();

            // Dọn dẹp và chuẩn hóa avatar
            cleanupAndStandardizeAvatars();

            System.out.println("[AVATAR FIX] Đồng bộ avatar hoàn tất!");
        } catch (Exception e) {
            System.err.println("[AVATAR FIX] Lỗi khi đồng bộ avatar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Đảm bảo các thư mục avatar tồn tại
     */
    private static void ensureDirectoriesExist() throws IOException {
        // Sử dụng PathUtil để đảm bảo các thư mục tồn tại
        PathUtil.ensureDirectoriesExist();
    }

    /**
     * Dọn dẹp và chuẩn hóa tất cả avatar thành username.png
     */
    private static void cleanupAndStandardizeAvatars() {
        try {
            List<User> allUsers = ServiceLocator.userService().searchUsers("");

            for (User user : allUsers) {
                standardizeUserAvatar(user);
            }
        } catch (Exception e) {
            System.err.println("[AVATAR FIX] Lỗi khi chuẩn hóa avatars: " + e.getMessage());
        }
    }

    /**
     * Chuẩn hóa avatar của một user thành username.png
     */
    private static void standardizeUserAvatar(User user) {
        try {
            String username = user.getUsername();
            File standardFile = new File(PathUtil.getAvatarDirectory(), username + ".png");

            // Tìm file avatar mới nhất cho user
            File newestFile = findNewestAvatarForUser(username);

            if (newestFile != null && newestFile.exists()) {
                // Nếu file mới nhất không phải là file chuẩn
                if (!newestFile.equals(standardFile)) {
                    // Copy sang file chuẩn
                    Files.copy(newestFile.toPath(), standardFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);

                    // Xóa file cũ
                    newestFile.delete();

                    System.out.println("[AVATAR FIX] Đã chuẩn hóa avatar cho " + username);
                }

                // Cập nhật database
                String standardPath = PathUtil.getStandardAvatarPath(username);
                if (!standardPath.equals(user.getAvatarPath())) {
                    user.setAvatarPath(standardPath);
                    user.setUseDefaultAvatar(false);
                    ServiceLocator.userService().updateUserAvatar(user);
                }

                // Sync to cache
                syncToCache(username, standardFile);
            }

            // Xóa tất cả file avatar cũ của user này
            cleanupOldAvatars(username);

        } catch (Exception e) {
            System.err.println("[AVATAR FIX] Lỗi khi chuẩn hóa avatar cho " +
                    user.getUsername() + ": " + e.getMessage());
        }
    }

    /**
     * Tìm file avatar mới nhất cho một user
     */
    private static File findNewestAvatarForUser(String username) {
        File avatarDir = new File(PathUtil.getAvatarDirectory());
        if (!avatarDir.exists()) return null;

        File[] files = avatarDir.listFiles((dir, name) ->
                name.startsWith(username + "_") || name.equals(username + ".png")
        );

        if (files == null || files.length == 0) return null;

        // Tìm file mới nhất
        File newest = files[0];
        for (File f : files) {
            if (f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }

        return newest;
    }

    /**
     * Xóa tất cả file avatar cũ (không phải username.png)
     */
    private static void cleanupOldAvatars(String username) {
        File avatarDir = new File(PathUtil.getAvatarDirectory());
        if (!avatarDir.exists()) return;

        File[] files = avatarDir.listFiles((dir, name) ->
                name.startsWith(username + "_") && !name.equals(username + ".png")
        );

        if (files != null) {
            for (File f : files) {
                if (f.delete()) {
                    System.out.println("[AVATAR FIX] Đã xóa file avatar cũ: " + f.getName());
                }
            }
        }
    }

    /**
     * Fix avatar cho một username cụ thể
     */
    public static void fixAvatarForUser(String username) {
        try {
            User user = ServiceLocator.userService().getUser(username);
            if (user == null) return;

            File standardFile = new File(PathUtil.getAvatarDirectory(), username + ".png");

            // Nếu đã có file chuẩn và đường dẫn đúng thì chỉ sync cache
            if (standardFile.exists() &&
                    user.getAvatarPath() != null &&
                    user.getAvatarPath().equals(PathUtil.getStandardAvatarPath(username))) {
                syncToCache(username, standardFile);
                return;
            }

            // Nếu không, chuẩn hóa avatar
            standardizeUserAvatar(user);

        } catch (Exception e) {
            System.err.println("[AVATAR FIX] Lỗi khi fix avatar cho " + username + ": " + e.getMessage());
        }
    }

    /**
     * Sync avatar vào cache
     */
    private static void syncToCache(String username, File sourceFile) {
        try {
            File cacheFile = new File(PathUtil.getAvatarCacheDirectory(), username + ".png");
            cacheFile.getParentFile().mkdirs();

            if (!cacheFile.exists() ||
                    cacheFile.lastModified() < sourceFile.lastModified()) {
                Files.copy(sourceFile.toPath(), cacheFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[AVATAR FIX] Đã sync avatar vào cache cho " + username);
            }
        } catch (Exception e) {
            System.err.println("[AVATAR FIX] Lỗi sync to cache: " + e.getMessage());
        }
    }

    /**
     * Validate và sửa tất cả đường dẫn avatar
     */
    public static void validateAvatarPaths() {
        try {
            List<User> allUsers = ServiceLocator.userService().searchUsers("");
            int fixedCount = 0;

            for (User user : allUsers) {
                String username = user.getUsername();
                String expectedPath = PathUtil.getStandardAvatarPath(username);

                if (user.getAvatarPath() != null && !user.getAvatarPath().equals(expectedPath)) {
                    // Chuẩn hóa avatar
                    fixAvatarForUser(username);
                    fixedCount++;
                }
            }

            System.out.println("[INFO] Đã kiểm tra và sửa " + fixedCount + " đường dẫn avatar");
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi validate avatar paths: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra xem một file avatar có phải là avatar mặc định không
     */
    public static boolean isDefaultAvatar(String avatarPath) {
        if (avatarPath == null || avatarPath.isEmpty()) {
            return true;
        }

        // Kiểm tra nếu file chứa "avatar_" (format của avatar mặc định)
        if (avatarPath.contains("avatar_")) {
            return true;
        }

        // Kiểm tra file có tồn tại không
        File avatarFile = new File(avatarPath);
        return !avatarFile.exists();
    }

    /**
     * Lưu avatar từ byte array
     * @param username Username của người dùng
     * @param data Dữ liệu avatar dạng byte array
     * @return true nếu lưu thành công
     */
    public static boolean saveAvatarFromBytes(String username, byte[] data) {
        try {
            // Đảm bảo thư mục tồn tại
            ensureDirectoriesExist();
            
            // Lưu vào uploads/avatars
            Path avatarPath = Paths.get(PathUtil.getAvatarDirectory(), username + ".png");
            Files.write(avatarPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Lưu vào cache
            Path cachePath = Paths.get(PathUtil.getAvatarCacheDirectory(), username + ".png");
            Files.write(cachePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Cập nhật DB
            User user = ServiceLocator.userService().getUser(username);
            if (user != null) {
                user.setAvatarPath(PathUtil.getStandardAvatarPath(username));
                user.setUseDefaultAvatar(false);
                ServiceLocator.userService().updateUserAvatar(user);
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("[AVATAR ERROR] Không thể lưu avatar từ byte array: " + e.getMessage());
            return false;
        }
    }

    /**
     * Normalize all avatar paths in database for cross-platform compatibility
     * This method should be called when there are issues with mixed path separators
     */
    public static void normalizeAllAvatarPaths() {
        try {
            List<User> allUsers = ServiceLocator.userService().searchUsers("");
            int fixedCount = 0;

            for (User user : allUsers) {
                if (user.getAvatarPath() != null) {
                    String normalizedPath = PathUtil.normalizePath(user.getAvatarPath());
                    if (!normalizedPath.equals(user.getAvatarPath())) {
                        user.setAvatarPath(normalizedPath);
                        ServiceLocator.userService().updateUserAvatar(user);
                        fixedCount++;
                    }
                }
            }

            System.out.println("[INFO] Đã chuẩn hóa " + fixedCount + " đường dẫn avatar");
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi chuẩn hóa avatar paths: " + e.getMessage());
            e.printStackTrace();
        }
    }
}