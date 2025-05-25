package app.controller;

import app.util.DatabaseKeyManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controller cho màn hình quản trị hệ thống
 */
public class AdminController {

    @FXML private Button btnOpenDatabaseEncryption;
    @FXML private Button btnBackup;
    @FXML private Button btnRestore;
    @FXML private Button btnUserManagement;
    @FXML private Button btnSystemStats;
    @FXML private Button btnBack;
    @FXML private Label lblUsername;
    @FXML private Label lblStatus;

    private String adminUsername;

    @FXML
    public void initialize() {
        // Khởi tạo DatabaseKeyManager nếu chưa được khởi tạo
        if (!DatabaseKeyManager.isInitialized()) {
            DatabaseKeyManager.initialize();
        }

        // Cập nhật trạng thái mã hóa database
        updateDatabaseEncryptionStatus();
    }

    /**
     * Cập nhật hiển thị trạng thái mã hóa
     */
    private void updateDatabaseEncryptionStatus() {
        boolean isEnabled = DatabaseKeyManager.isEncryptionEnabled();
        String status = isEnabled ? "Đã bật" : "Đã tắt";
        String style = isEnabled ? "-fx-text-fill: #4caf50;" : "-fx-text-fill: #f44336;";

        // Hiển thị trạng thái ở nút
        btnOpenDatabaseEncryption.setText("Cài đặt mã hóa database (" + status + ")");

        // Nếu có Label hiển thị trạng thái riêng
        if (lblStatus != null) {
            lblStatus.setText("Mã hóa database: " + status);
            lblStatus.setStyle(style);
        }
    }

    /**
     * Thiết lập thông tin người dùng quản trị
     */
    public void setAdminUser(String username) {
        this.adminUsername = username;
        if (lblUsername != null) {
            lblUsername.setText("Admin: " + username);
        }
    }

    /**
     * Mở màn hình cài đặt mã hóa database
     */
    @FXML
    private void openDatabaseEncryptionSettings() {
        try {
            // Tạo một Stage mới cho cài đặt mã hóa
            Stage settingsStage = new Stage();
            settingsStage.setTitle("Cài đặt mã hóa database");

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/database_encryption_settings.fxml"));
            Parent root = loader.load();

            // Tạo scene
            Scene scene = new Scene(root);
            settingsStage.setScene(scene);

            // Đặt là modal dialog (chặn tương tác với cửa sổ chính)
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.initOwner(btnOpenDatabaseEncryption.getScene().getWindow());

            // Hiển thị
            settingsStage.showAndWait();

            // Cập nhật trạng thái sau khi đóng cửa sổ cài đặt
            updateDatabaseEncryptionStatus();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText("Không thể mở cài đặt mã hóa database");
            alert.setContentText("Lỗi: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Mở công cụ tái mã hóa database
     */
    @FXML
    private void openReencryptionTool() {
        try {
            // Tạo một Stage mới cho công cụ tái mã hóa
            Stage toolStage = new Stage();
            toolStage.setTitle("Công cụ tái mã hóa database");

            // Load FXML hoặc tạo UI theo code
            // Ở đây sử dụng cách tạo UI đơn giản
            VBox root = new VBox(15);
            root.setPadding(new javafx.geometry.Insets(20));

            Label titleLabel = new Label("Công cụ tái mã hóa database");
            titleLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

            Button openToolButton = new Button("Mở công cụ tái mã hóa");
            openToolButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
            openToolButton.setOnAction(e -> {
                // Khởi chạy công cụ tái mã hóa
                app.tools.DatabaseReencryptionTool tool = new app.tools.DatabaseReencryptionTool();
                try {
                    tool.start(new Stage());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Không thể khởi chạy công cụ tái mã hóa", ex.getMessage());
                }
                toolStage.close();
            });

            Label warningLabel = new Label("Chú ý: Công cụ này cho phép mã hóa lại hoặc giải mã hàng loạt tin nhắn. "
                    + "Sử dụng với cẩn trọng và chỉ khi cần thiết. Hãy sao lưu database trước khi sử dụng.");
            warningLabel.setWrapText(true);
            warningLabel.setStyle("-fx-text-fill: #f44336;");

            Button closeButton = new Button("Đóng");
            closeButton.setOnAction(e -> toolStage.close());

            root.getChildren().addAll(titleLabel, warningLabel, openToolButton, closeButton);

            // Tạo scene
            Scene scene = new Scene(root, 450, 300);
            toolStage.setScene(scene);

            // Đặt là modal dialog
            toolStage.initModality(Modality.APPLICATION_MODAL);
            toolStage.initOwner(btnOpenDatabaseEncryption.getScene().getWindow());

            // Hiển thị
            toolStage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi", "Không thể mở công cụ tái mã hóa database: " + e.getMessage());
        }
    }

    /**
     * Mở màn hình sao lưu database
     */
    @FXML
    private void openBackupTool() {
        showInfo("Thông báo", "Tính năng sao lưu database đang được phát triển");
    }

    /**
     * Mở màn hình khôi phục database
     */
    @FXML
    private void openRestoreTool() {
        showInfo("Thông báo", "Tính năng khôi phục database đang được phát triển");
    }

    /**
     * Mở màn hình quản lý người dùng
     */
    @FXML
    private void openUserManagement() {
        showInfo("Thông báo", "Tính năng quản lý người dùng đang được phát triển");
    }

    /**
     * Mở màn hình thống kê hệ thống
     */
    @FXML
    private void openSystemStats() {
        showInfo("Thông báo", "Tính năng thống kê hệ thống đang được phát triển");
    }

    /**
     * Quay lại màn hình chính
     */
    @FXML
    private void goBack() {
        try {
            // Quay lại trang chat
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load();

            // Lấy Scene hiện tại
            Scene scene = btnBack.getScene();
            scene.setRoot(root);

            // Lấy controller của chat.fxml, gán username
            ChatController chatCtrl = loader.getController();
            chatCtrl.setCurrentUser(adminUsername);

        } catch (IOException e) {
            e.printStackTrace();
            showError("Lỗi", "Không thể quay lại màn hình chính: " + e.getMessage());
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}