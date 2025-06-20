package app.controller.chat.util;

import app.ServiceLocator;
import app.controller.chat.model.RecentChatCellData;
import app.model.User;
import app.util.AvatarUtil;
import app.util.PathUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Lớp adapter để sử dụng AvatarUtil trong các component đã refactor
 */
public class AvatarAdapter {

    // Màu sắc cho avatar mặc định (sao chép từ AvatarUtil để đảm bảo nhất quán)
    private static final Color[] AVATAR_COLORS = {
        Color.rgb(41, 128, 185),  // Xanh dương
        Color.rgb(39, 174, 96),   // Xanh lá
        Color.rgb(142, 68, 173),  // Tím
        Color.rgb(230, 126, 34),  // Cam
        Color.rgb(231, 76, 60),   // Đỏ
        Color.rgb(52, 73, 94),    // Xám đậm
        Color.rgb(241, 196, 15),  // Vàng
        Color.rgb(26, 188, 156)   // Ngọc
    };

    /**
     * Cập nhật avatar cho người dùng
     * @param user Đối tượng người dùng
     * @param avatarCircle Circle hiển thị avatar
     * @param initialLabel Label hiển thị chữ cái đầu
     */
    public static void updateUserAvatar(User user, Circle avatarCircle, Label initialLabel) {
        if (user == null) return;

        System.out.println("[AVATAR_DEBUG] Starting avatar update for: " + user.getUsername());
        System.out.println("[AVATAR_DEBUG] Avatar path: " + user.getAvatarPath());

        // Hiển thị avatar mặc định ngay lập tức
        setDefaultAvatar(user, avatarCircle, initialLabel);

        // Load avatar thực trong background thread
        if (user.getAvatarPath() != null && !user.getAvatarPath().isBlank()) {
            new Thread(() -> {
                try {
                    File avatarFile = findAvatarFile(user);
                    if (avatarFile != null && avatarFile.exists()) {
                        // Load image synchronously trong background thread
                        BufferedImage bufferedImage = ImageIO.read(avatarFile);
                        if (bufferedImage != null) {
                            Platform.runLater(() -> {
                                try {
                                    Image img = SwingFXUtils.toFXImage(bufferedImage, null);
                                    avatarCircle.setFill(new ImagePattern(img));
                                    initialLabel.setVisible(false);
                                    System.out.println("[AVATAR_DEBUG] Successfully loaded avatar for: " + user.getUsername());

                                    // Tạo cache nếu chưa có
                                    createAvatarCache(user.getUsername(), avatarFile);
                                } catch (Exception e) {
                                    System.err.println("[AVATAR_ERROR] Failed to set avatar: " + e.getMessage());
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[AVATAR_ERROR] Failed to load avatar: " + e.getMessage());
                }
            }).start();
        }
    }

    // Thêm phương thức helper để tìm file avatar
    private static File findAvatarFile(User user) {
        if (user.getAvatarPath() == null) return null;

        // Thử với path gốc
        File file = new File(user.getAvatarPath());
        if (file.exists()) return file;

        // Thử với tên file chuẩn
        String standardPath = app.util.PathUtil.getStandardAvatarPath(user.getUsername());
        file = new File(standardPath);
        if (file.exists()) return file;

        // Thử trong cache
        file = new File(app.util.PathUtil.getAvatarCacheDirectory() + File.separator + user.getUsername() + ".png");
        if (file.exists()) return file;

        // Tìm file mới nhất cho user
        File avatarDir = new File(app.util.PathUtil.getAvatarDirectory());
        if (avatarDir.exists()) {
            File[] files = avatarDir.listFiles((dir, name) ->
                    name.startsWith(user.getUsername() + "_") || name.equals(user.getUsername() + ".png")
            );

            if (files != null && files.length > 0) {
                // Tìm file mới nhất
                File newestFile = files[0];
                for (File f : files) {
                    if (f.lastModified() > newestFile.lastModified()) {
                        newestFile = f;
                    }
                }
                return newestFile;
            }
        }

        return null;
    }
// Tách logic load từ cache thành method riêng
private static void loadFromCache(User user, Circle avatarCircle, Label initialLabel, boolean[] imageLoaded) {
    if (imageLoaded[0]) return;
    File cacheFile = new File(app.util.PathUtil.getAvatarCacheDirectory() + File.separator + user.getUsername() + ".png");
    if (cacheFile.exists()) {
        try {
            Image img = new Image(cacheFile.toURI().toString(),
                    avatarCircle.getRadius() * 2,
                    avatarCircle.getRadius() * 2,
                    true, true);
                    
            img.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() == 1.0 && !img.isError()) {
                    Platform.runLater(() -> {
                        if (!imageLoaded[0]) {
                            avatarCircle.setFill(new ImagePattern(img));
                            initialLabel.setVisible(false);
                            imageLoaded[0] = true;
                        }
                    });
                }
            });

            // Timeout ngắn cho cache - 2 giây
            PauseTransition timeout = new PauseTransition(Duration.seconds(2));
            timeout.setOnFinished(e -> {
                if (!imageLoaded[0]) {
                    System.err.println("[AVATAR_DEBUG] Loading from cache timed out, using default");
                    setDefaultAvatar(user, avatarCircle, initialLabel);
                }
            });
            timeout.play();
            
        } catch (Exception e) {
            System.err.println("[AVATAR_ERROR] Cannot load from cache: " + e.getMessage());
            setDefaultAvatar(user, avatarCircle, initialLabel);
        }
    } else {
        setDefaultAvatar(user, avatarCircle, initialLabel);
    }
}

// Tách logic tạo cache thành method riêng
    private static void createAvatarCache(String username, File sourceFile) {
        try {
            // Đảm bảo thư mục cache tồn tại
            File cacheDir = new File(PathUtil.getAvatarCacheDirectory());
            if (!cacheDir.exists()) {
                boolean created = cacheDir.mkdirs();
                if (!created) {
                    System.err.println("[CACHE_ERROR] Cannot create cache directory");
                    return;
                }
            }
            
            File cacheFile = new File(cacheDir, username + ".png");
            Files.copy(sourceFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[CACHE_DEBUG] Created cache file: " + cacheFile.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("[CACHE_ERROR] Cannot create cache: " + e.getMessage());
        }
}

private static void setDefaultAvatar(User user, Circle avatarCircle, Label initialLabel) {
    Platform.runLater(() -> {
        int colorIndex = Math.abs(user.getUsername().hashCode() % AVATAR_COLORS.length);
        avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
        initialLabel.setText(user.getUsername().substring(0, 1).toUpperCase());
        initialLabel.setVisible(true);
    });
}


    
    /**
     * Cập nhật avatar cho RecentChatCellData (chat gần đây)
     * @param chat Dữ liệu chat
     * @param avatarCircle Circle hiển thị avatar
     * @param initialLabel Label hiển thị chữ cái đầu
     */
    public static void updateRecentChatAvatar(RecentChatCellData chat, Circle avatarCircle, Label initialLabel) {
        System.out.println("[RECENT_AVATAR_DEBUG] Updating avatar for chat: " + chat.chatName);
        System.out.println("[RECENT_AVATAR_DEBUG] Avatar path: " + chat.avatarPath);
        
        boolean avatarLoaded = false;
        
        // Xử lý đặc biệt cho Global chat
        if ("Global".equals(chat.chatName)) {
            avatarCircle.setFill(Color.rgb(76, 175, 80)); // Màu xanh lá cho Global
            initialLabel.setText("G");
            initialLabel.setVisible(true);
            
            // Thử tải icon global nếu có
            try {
                String iconPath = "/icons/global.png";
                java.net.URL iconUrl = AvatarAdapter.class.getResource(iconPath);
                if (iconUrl != null) {
                    Image img = new Image(iconUrl.toExternalForm(),
                                avatarCircle.getRadius() * 2,
                                avatarCircle.getRadius() * 2,
                                true, true);
                    avatarCircle.setFill(new ImagePattern(img));
                    initialLabel.setVisible(false);
                    System.out.println("[RECENT_AVATAR_DEBUG] Đã tải icon Global thành công");
                }
            } catch (Exception e) {
                System.err.println("[RECENT_AVATAR_DEBUG] Không thể tải icon Global: " + e.getMessage());
                // Sử dụng chữ G thay thế như đã thiết lập
            }
            return;
        }

        // Ưu tiên 1: Sử dụng avatarPath từ đối tượng RecentChatCellData
        if (chat.avatarPath != null && !chat.avatarPath.isEmpty()) {
            File f = new File(chat.avatarPath);
            System.out.println("[RECENT_AVATAR_DEBUG] Kiểm tra file avatar: " + f.getAbsolutePath());
            System.out.println("[RECENT_AVATAR_DEBUG] File avatar tồn tại: " + f.exists() + " cho " + chat.chatName);
            
            if (f.exists()) {
                try {
                    System.out.println("[RECENT_AVATAR_DEBUG] Tải avatar từ: " + f.getAbsolutePath());
                    System.out.println("[RECENT_AVATAR_DEBUG] Kích thước file: " + f.length() + " bytes");
                    
                    Image img = new Image(f.toURI().toString(), 
                                    avatarCircle.getRadius() * 2, 
                                    avatarCircle.getRadius() * 2, 
                                    true, true);
                    avatarCircle.setFill(new ImagePattern(img));
                    initialLabel.setVisible(false);
                    
                    System.out.println("[RECENT_AVATAR_DEBUG] Avatar đã được tải thành công cho " + chat.chatName);
                    avatarLoaded = true;
                } catch (Exception e) {
                    System.err.println("[RECENT_AVATAR_DEBUG] Không thể tải avatar cho " + chat.chatName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("[RECENT_AVATAR_DEBUG] File avatar không tồn tại cho " + chat.chatName + ": " + f.getAbsolutePath());
            }
        } else {
            System.out.println("[RECENT_AVATAR_DEBUG] Không có đường dẫn avatar cho " + chat.chatName);
        }
        
        // Ưu tiên 2: Kiểm tra avatar trong cache nếu chưa tải được
        if (!avatarLoaded) {
            // Trường hợp chat 1-1, có thể tìm được trong thư mục cache
            if (!chat.chatName.contains("Group") && !chat.chatName.startsWith("nhóm")) {
                try {
                    File cacheFile = new File(PathUtil.getAvatarCacheDirectory() + File.separator + chat.chatName + ".png");
                    System.out.println("[RECENT_AVATAR_DEBUG] Kiểm tra avatar trong cache: " + cacheFile.getAbsolutePath());
                    
                    if (cacheFile.exists()) {
                        System.out.println("[RECENT_AVATAR_DEBUG] Tải avatar từ cache cho " + chat.chatName);
                        System.out.println("[RECENT_AVATAR_DEBUG] Kích thước file cache: " + cacheFile.length() + " bytes");
                        
                        Image img = new Image(cacheFile.toURI().toString(), 
                                        avatarCircle.getRadius() * 2, 
                                        avatarCircle.getRadius() * 2, 
                                        true, true);
                        avatarCircle.setFill(new ImagePattern(img));
                        initialLabel.setVisible(false);
                        
                        System.out.println("[RECENT_AVATAR_DEBUG] Avatar đã được tải từ cache cho " + chat.chatName);
                        avatarLoaded = true;
                        
                        // Không thể cập nhật avatarPath vì đây là trường final
                        // Nhưng điều này không ảnh hưởng đến việc hiển thị avatar hiện tại
                    }
                } catch (Exception e) {
                    System.err.println("[RECENT_AVATAR_DEBUG] Lỗi khi tải avatar từ cache cho " + chat.chatName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Mặc định: Avatar với chữ cái đầu tiên
        if (!avatarLoaded) {
            System.out.println("[RECENT_AVATAR_DEBUG] Sử dụng avatar mặc định cho " + chat.chatName);
            int colorIndex = Math.abs(chat.chatName.hashCode() % AVATAR_COLORS.length);
            avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
            initialLabel.setText(chat.chatName.substring(0, 1).toUpperCase());
            initialLabel.setVisible(true);
            
            System.out.println("[RECENT_AVATAR_DEBUG] Sử dụng avatar mặc định với màu có chỉ số: " + colorIndex + " cho " + chat.chatName);
        }
    }
    
    /**
     * Cập nhật avatar cho nhóm
     * @param groupName Tên nhóm
     * @param avatarCircle Circle hiển thị avatar
     * @param initialLabel Label hiển thị chữ cái đầu
     */
    public static void updateGroupAvatar(String groupName, Circle avatarCircle, Label initialLabel) {
        int colorIndex = Math.abs(groupName.hashCode() % AVATAR_COLORS.length);
        avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
        initialLabel.setText(groupName.substring(0, 1).toUpperCase());
        initialLabel.setVisible(true);
    }
    
    /**
     * Lấy màu cho avatar từ tên người dùng hoặc nhóm
     * @param name Tên người dùng hoặc nhóm
     * @return Màu được chọn
     */
    public static Color getAvatarColor(String name) {
        int colorIndex = Math.abs(name.hashCode() % AVATAR_COLORS.length);
        return AVATAR_COLORS[colorIndex];
    }
    
    /**
     * Tạo avatar mặc định (delegate sang AvatarUtil)
     * @param username Tên người dùng
     * @return Đường dẫn đến file avatar đã tạo
     */
    public static String createDefaultAvatar(String username) {
        return AvatarUtil.createDefaultAvatar(username);
    }
    
    /**
     * Tạo avatar từ file ảnh (delegate sang AvatarUtil)
     * @param imageFile File ảnh
     * @return Đường dẫn đến file avatar đã tạo
     */
    public static String createAvatarFromFile(File imageFile) {
        return AvatarUtil.createAvatarFromFile(imageFile);
    }
}