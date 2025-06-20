package app.util;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Tiện ích để tạo avatar mặc định hiển thị tên người dùng
 */
public class AvatarUtil {
    
    // Màu sắc cho avatar mặc định
    private static final Color[] COLORS = {
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
     * Tạo avatar mặc định hiển thị tên người dùng
     * @param username Tên người dùng
     * @return Đường dẫn đến file avatar đã tạo
     */
    public static String createDefaultAvatar(String username) {
        // Tạo canvas để vẽ avatar
        Canvas canvas = new Canvas(200, 200);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Chọn màu ngẫu nhiên
        Random random = new Random();
        Color backgroundColor = COLORS[random.nextInt(COLORS.length)];
        
        // Vẽ hình tròn nền
        gc.setFill(backgroundColor);
        gc.fillOval(0, 0, 200, 200);
        
        // Lấy chữ cái đầu tiên của tên người dùng
        String initial = username.substring(0, 1).toUpperCase();
        
        // Vẽ chữ cái đầu tiên
        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Arial", 100));
        gc.fillText(initial, 100, 120);
        
        // Chuyển canvas thành hình ảnh
        WritableImage writableImage = canvas.snapshot(null, null);
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);
        
        // Lưu hình ảnh vào thư mục
        try {
            // Tạo thư mục nếu chưa tồn tại với đường dẫn tuyệt đối
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "avatars", dateDir);
            Files.createDirectories(dir);
            
            // Tạo tên file
            String fileName = "avatar_" + System.currentTimeMillis() + ".png";
            Path filePath = dir.resolve(fileName);
            
            // Lưu hình ảnh
            ImageIO.write(bufferedImage, "png", filePath.toFile());
            
            return filePath.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Tạo avatar từ file ảnh
     * @param imageFile File ảnh
     * @return Đường dẫn đến file avatar đã tạo
     */
    public static String createAvatarFromFile(File imageFile) {
        try {
            // Tạo thư mục nếu chưa tồn tại với đường dẫn tuyệt đối
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "avatars", dateDir);
            Files.createDirectories(dir);
            
            // Tạo tên file
            String fileName = "avatar_" + System.currentTimeMillis() + ".png";
            Path filePath = dir.resolve(fileName);
            
            // Sao chép file
            // Kiểm tra xem file nguồn có tồn tại không
            if (Files.exists(imageFile.toPath())) {
                Files.copy(imageFile.toPath(), filePath);
            } else {
                throw new IOException("Source image file does not exist: " + imageFile.getAbsolutePath());
            }
            
            return filePath.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}