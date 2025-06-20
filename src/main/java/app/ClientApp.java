package app;

import app.controller.LoginController;
import app.util.AvatarFixUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Đặt IP server từ tham số dòng lệnh hoặc hardcode
        String serverIP = "14.226.123.235";  // IP công khai của bạn

        // Cập nhật cấu hình kết nối
        System.setProperty("client.host", serverIP);
        System.setProperty("client.port", "5000");
        System.setProperty("server.host", serverIP);
        System.setProperty("server.port", "5000");
        
        // Đồng bộ hóa avatar khi khởi động
        try {
            System.out.println("[INFO] Đang đồng bộ avatar...");
            // Chuẩn hóa đường dẫn avatar để tương thích đa nền tảng
            AvatarFixUtil.normalizeAllAvatarPaths();
            // Đồng bộ avatar giữa DB và file system
            AvatarFixUtil.fixAvatarIssues();
            System.out.println("[INFO] Đồng bộ avatar hoàn tất");
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi đồng bộ avatar: " + e.getMessage());
            e.printStackTrace();
        }

        // Khởi động giao diện đăng nhập
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("MyMessenger - Kết nối đến " + serverIP);
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }
}