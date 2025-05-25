package app.controller;

import app.util.DatabaseKeyManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller cho màn hình cài đặt mã hóa database
 * Cho phép admin bật/tắt tính năng và quản lý khóa mã hóa
 */
public class DatabaseEncryptionSettingsController {

    @FXML private CheckBox chkEnableEncryption;
    @FXML private TextField txtEncryptionKey;
    @FXML private Button btnGenerateKey;
    @FXML private Button btnSave;
    @FXML private Button btnExportKey;
    @FXML private Button btnImportKey;
    @FXML private Button btnClose;
    @FXML private Label lblStatus;
    @FXML private TextArea txtInfo;
    @FXML private ProgressIndicator progressIndicator;

    @FXML
    public void initialize() {
        // Khởi tạo DatabaseKeyManager nếu cần
        if (!DatabaseKeyManager.isEncryptionEnabled()) {
            DatabaseKeyManager.initialize();
        }

        // Cập nhật UI với cài đặt hiện tại
        chkEnableEncryption.setSelected(DatabaseKeyManager.isEncryptionEnabled());
        txtEncryptionKey.setText(DatabaseKeyManager.getEncryptionKey());

        // Khóa ô nhập key nếu mã hóa chưa được bật
        updateUIState();

        // Thêm listener cho checkbox
        chkEnableEncryption.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updateUIState();

            // Hiển thị cảnh báo nếu bật mã hóa
            if (newVal && !oldVal) {
                Alert warning = new Alert(Alert.AlertType.WARNING);
                warning.setTitle("Cảnh báo bật mã hóa database");
                warning.setHeaderText("Xác nhận bật mã hóa");
                warning.setContentText("Khi bật mã hóa database, tất cả tin nhắn mới sẽ được mã hóa. Vui lòng đảm bảo "
                        + "lưu giữ khóa mã hóa ở nơi an toàn. Nếu mất khóa, dữ liệu đã mã hóa sẽ không thể khôi phục được.");
                warning.showAndWait();
            }
        });

        // Ẩn progress indicator ban đầu
        progressIndicator.setVisible(false);

        // Thêm thông tin vào TextArea
        txtInfo.setText(
                "Thông tin về mã hóa database:\n\n" +
                        "- Tính năng này mã hóa nội dung tin nhắn trước khi lưu vào database\n" +
                        "- Giúp bảo vệ dữ liệu khi database bị truy cập trái phép\n" +
                        "- Mã hóa được thực hiện với thuật toán AES/GCM (mã hóa có xác thực)\n" +
                        "- Tin nhắn đã lưu trước khi bật tính năng này sẽ không được mã hóa\n\n" +
                        "Lưu ý quan trọng: Đảm bảo lưu trữ khóa mã hóa ở nơi an toàn. Nếu mất khóa, dữ liệu đã mã hóa " +
                        "sẽ không thể khôi phục được. Hãy sử dụng chức năng 'Xuất khóa' để sao lưu khóa."
        );
    }

    private void updateUIState() {
        boolean isEnabled = chkEnableEncryption.isSelected();
        txtEncryptionKey.setDisable(!isEnabled);
        btnGenerateKey.setDisable(!isEnabled);
        btnExportKey.setDisable(!isEnabled);
        btnImportKey.setDisable(!isEnabled);
    }

    @FXML
    private void onGenerateKey() {
        // Hiển thị cảnh báo
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Tạo khóa mới");
        confirm.setHeaderText("Xác nhận tạo khóa mã hóa mới");
        confirm.setContentText("Tạo khóa mới sẽ khiến tất cả dữ liệu đã mã hóa trước đây không thể giải mã được. " +
                "Chỉ nên thực hiện điều này khi bạn muốn bắt đầu mã hóa mới hoặc khi có vấn đề bảo mật. Bạn có chắc chắn không?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            // Tạo khóa mới
            String newKey = DatabaseKeyManager.generateRandomKey(32);
            txtEncryptionKey.setText(newKey);

            showStatus("Đã tạo khóa mã hóa mới", true);
        }
    }

    @FXML
    private void onSave() {
        try {
            progressIndicator.setVisible(true);

            // Lưu trạng thái bật/tắt
            DatabaseKeyManager.setEncryptionEnabled(chkEnableEncryption.isSelected());

            // Lưu khóa mới nếu có
            String key = txtEncryptionKey.getText();
            if (key != null && !key.isEmpty()) {
                DatabaseKeyManager.setEncryptionKey(key);
            }

            progressIndicator.setVisible(false);
            showStatus("Đã lưu cài đặt mã hóa database", true);
        } catch (Exception e) {
            progressIndicator.setVisible(false);
            showStatus("Lỗi: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    @FXML
    private void onExportKey() {
        try {
            // Tạo file chooser để chọn nơi lưu
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Xuất khóa mã hóa");

            // Đặt tên file mặc định
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            fileChooser.setInitialFileName("database_key_backup_" + timestamp + ".txt");

            // Chọn file đích
            File keyFile = fileChooser.showSaveDialog(btnExportKey.getScene().getWindow());
            if (keyFile == null) {
                return; // Người dùng đã hủy
            }

            // Ghi khóa và thông tin vào file
            try (FileWriter writer = new FileWriter(keyFile)) {
                writer.write("DATABASE ENCRYPTION KEY BACKUP\n");
                writer.write("Created: " + LocalDateTime.now() + "\n");
                writer.write("-----------------------------------\n");
                writer.write("KEY: " + txtEncryptionKey.getText() + "\n");
                writer.write("-----------------------------------\n");
                writer.write("IMPORTANT: Keep this file in a secure location.\n");
                writer.write("If you lose this key, encrypted messages cannot be recovered.\n");
            }

            showStatus("Đã xuất khóa vào file: " + keyFile.getAbsolutePath(), true);
        } catch (Exception e) {
            showStatus("Lỗi khi xuất khóa: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    @FXML
    private void onImportKey() {
        try {
            // Tạo file chooser để chọn file khóa
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Nhập khóa mã hóa");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );

            // Chọn file nguồn
            File keyFile = fileChooser.showOpenDialog(btnImportKey.getScene().getWindow());
            if (keyFile == null) {
                return; // Người dùng đã hủy
            }

            // Đọc file
            String content = Files.readString(keyFile.toPath());

            // Tìm dòng chứa khóa
            String key = null;
            for (String line : content.split("\n")) {
                if (line.startsWith("KEY:")) {
                    key = line.substring(4).trim();
                    break;
                }
            }

            if (key == null || key.isEmpty()) {
                showStatus("Không tìm thấy khóa trong file", false);
                return;
            }

            // Hiển thị khóa trong text field
            txtEncryptionKey.setText(key);
            showStatus("Đã nhập khóa từ file", true);

        } catch (Exception e) {
            showStatus("Lỗi khi nhập khóa: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    @FXML
    private void onClose() {
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    private void showStatus(String message, boolean success) {
        lblStatus.setText(message);
        if (success) {
            lblStatus.setStyle("-fx-text-fill: #4caf50;"); // Green
        } else {
            lblStatus.setStyle("-fx-text-fill: #f44336;"); // Red
        }
    }
}