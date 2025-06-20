package app.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lớp tiện ích quản lý các đường dẫn file trong ứng dụng
 * Giúp tập trung quản lý và tránh hardcode đường dẫn
 */
public class PathUtil {

    // Thư mục gốc cho avatar
    private static final String AVATAR_DIRECTORY = "uploads/avatars";
    private static final String AVATAR_CACHE_DIRECTORY = "avatar_cache";
    
    // Thư mục gốc cho file đính kèm
    private static final String ATTACHMENT_DIRECTORY = "uploads/attachments";
    private static final String ATTACHMENT_CACHE_DIRECTORY = "attachment_cache";
    
    // Thư mục gốc cho thumbnail
    private static final String THUMBNAIL_DIRECTORY = "uploads/thumbnails";
    
    /**
     * Lấy đường dẫn thư mục avatar
     */
    public static String getAvatarDirectory() {
        return AVATAR_DIRECTORY;
    }
    
    /**
     * Lấy đường dẫn thư mục cache avatar
     */
    public static String getAvatarCacheDirectory() {
        return AVATAR_CACHE_DIRECTORY;
    }
    
    /**
     * Lấy đường dẫn thư mục file đính kèm
     */
    public static String getAttachmentDirectory() {
        return ATTACHMENT_DIRECTORY;
    }
    
    /**
     * Lấy đường dẫn thư mục cache file đính kèm
     */
    public static String getAttachmentCacheDirectory() {
        return ATTACHMENT_CACHE_DIRECTORY;
    }
    
    /**
     * Lấy đường dẫn thư mục thumbnail
     */
    public static String getThumbnailDirectory() {
        return THUMBNAIL_DIRECTORY;
    }
    
    /**
     * Tạo đường dẫn avatar chuẩn cho người dùng
     */
    public static String getStandardAvatarPath(String username) {
        return normalizePath(AVATAR_DIRECTORY + "/" + username + ".png");
    }
    
    /**
     * Tạo đường dẫn cache avatar cho người dùng
     */
    public static String getAvatarCachePath(String username) {
        return normalizePath(AVATAR_CACHE_DIRECTORY + "/" + username + ".png");
    }
    
    /**
     * Tạo đường dẫn chuẩn cho file đính kèm
     */
    public static String getStandardAttachmentPath(String fileName) {
        return normalizePath(ATTACHMENT_DIRECTORY + "/" + fileName);
    }
    
    /**
     * Tạo đường dẫn cache cho file đính kèm
     */
    public static String getAttachmentCachePath(String fileName) {
        return normalizePath(ATTACHMENT_CACHE_DIRECTORY + "/" + fileName);
    }
    
    /**
     * Tạo đường dẫn chuẩn cho thumbnail
     */
    public static String getStandardThumbnailPath(String fileName) {
        return normalizePath(THUMBNAIL_DIRECTORY + "/" + fileName);
    }
    
    /**
     * Chuẩn hóa đường dẫn để tương thích đa nền tảng
     * Chuyển đổi tất cả các dấu phân cách thư mục thành dấu "/"
     */
    public static String normalizePath(String path) {
        if (path == null) return null;
        return path.replace('\\', '/');
    }
    
    /**
     * Đảm bảo các thư mục cần thiết tồn tại
     */
    public static void ensureDirectoriesExist() {
        createDirectoryIfNotExists(AVATAR_DIRECTORY);
        createDirectoryIfNotExists(AVATAR_CACHE_DIRECTORY);
        createDirectoryIfNotExists(ATTACHMENT_DIRECTORY);
        createDirectoryIfNotExists(ATTACHMENT_CACHE_DIRECTORY);
        createDirectoryIfNotExists(THUMBNAIL_DIRECTORY);
    }
    
    /**
     * Tạo thư mục nếu chưa tồn tại
     */
    private static void createDirectoryIfNotExists(String directory) {
        try {
            Path path = Paths.get(directory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("[INFO] Đã tạo thư mục " + directory);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Không thể tạo thư mục " + directory + ": " + e.getMessage());
        }
    }
}