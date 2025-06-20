package app.controller;

import app.ServiceLocator;
import app.controller.chat.ChatControllerRefactored;
import app.model.User;
import app.service.AuthService;
import app.service.UserService;
import app.util.DatabaseKeyManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import network.ClientConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordPlain;
    @FXML private Button btnLogin;
    @FXML private Hyperlink btnGoRegister;   // đổi Button ➜ Hyperlink
    @FXML private Label lblStatus;
    @FXML private Button btnShowPass;

    private boolean showing = false;

    @FXML
    public void initialize() {
        // Nếu cần làm gì sau khi FXML load thì làm ở đây
        lblStatus.setText("");
    }

    /* hiển/ẩn mật khẩu */
    @FXML
    private void togglePassword() {
        showing = !showing;
        if (showing) {
            txtPasswordPlain.setText(txtPassword.getText());
            txtPasswordPlain.setVisible(true);
            txtPasswordPlain.setManaged(true);

            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
        } else {
            txtPassword.setText(txtPasswordPlain.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);

            txtPasswordPlain.setVisible(false);
            txtPasswordPlain.setManaged(false);
        }
    }

    // Nhấn nút "Login"
    @FXML
    private void onLogin() {
        // gọi validate trước khi thao tác Service
        boolean okInput = validate(txtUsername) & validate(txtPassword);
        if (!okInput) return;
        String username = txtUsername.getText().trim();
        String password = showing ? txtPasswordPlain.getText().trim() : txtPassword.getText().trim();

        UserService userService = ServiceLocator.userService();
        boolean ok = userService.login(username, password);

        if (ok) {
            DatabaseKeyManager.initialize();
            
            // Thiết lập username cho ClientConnection
            ClientConnection clientConnection = ServiceLocator.chat().getClient();
            if (clientConnection != null) {
                System.out.println("[INFO] Setting currentUser in ClientConnection to: " + username);
                clientConnection.setCurrentUser(username);
            }
            
            // Đăng nhập thành công -> chuyển sang màn hình Chat
            goToChat(username);
        } else {
            lblStatus.setText("Sai tài khoản hoặc mật khẩu!");
            lblStatus.setStyle("-fx-text-fill: #ef476f;"); // Màu đỏ
        }
    }

    // Nhấn nút "Go to Register"
    @FXML
    private void onGoRegister() {
        try {
            // nạp register.fxml thành một cây Node
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/register.fxml"));
            Parent root = loader.load();

            // lấy Scene hiện tại qua bất kỳ node nào (btnGoRegister)
            Scene scene = btnGoRegister.getScene();
            scene.setRoot(root);        // thay root => giữ nguyên stylesheet, kích thước
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void goToChat(String username) {
        try {
            // Đảm bảo thư mục cache tồn tại
            File cacheDir = new File("avatar_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // Fix avatar một cách thông minh hơn
            try {
                User user = ServiceLocator.userService().getUser(username);
                if (user != null) {
                    // Chỉ fix nếu chưa có avatar hoặc file không tồn tại
                    if (user.getAvatarPath() == null ||
                            !new File(user.getAvatarPath()).exists()) {
                        app.util.AvatarFixUtil.fixAvatarForUser(username);
                    }

                    // Tạo hoặc cập nhật cache
                    updateAvatarCache(user);
                }
            } catch (Exception e) {
                System.err.println("[LOGIN] Không thể tối ưu avatar: " + e.getMessage());
            }
            
            // Tạo scene cho chat.fxml
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);

            // Lấy controller của chat.fxml, gán username
            ChatControllerRefactored chatCtrl = loader.getController();
            
            // *** LIÊN KẾT CONTROLLER VÀ SERVICE ***
            ServiceLocator.chat().bindRefactoredUI(chatCtrl);
            
            // Đặt thông tin người dùng hiện tại
            chatCtrl.setCurrentUser(username);
            
            // *** GỌI CHAT SERVICE LOGIN ***
            ServiceLocator.chat().login(username);
            
        } catch (IOException e) {
            System.out.println("ERROR loading chat.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /* ---------- HÀM MỚI ---------- */
    /** kiểm tra rỗng + gắn/loại class "invalid" */
    private boolean validate(TextField tf) {
        if (tf.getText().isBlank()) {
            if (!tf.getStyleClass().contains("invalid"))
                tf.getStyleClass().add("invalid");
            return false;
        }
        tf.getStyleClass().remove("invalid");
        return true;
    }
    private void updateAvatarCache(User user) {
        try {
            if (user.getAvatarPath() == null) return;

            File sourceFile = new File(user.getAvatarPath());
            if (!sourceFile.exists()) {
                // Tìm file avatar mới nhất
                sourceFile = findLatestAvatarFile(user.getUsername());
            }

            if (sourceFile != null && sourceFile.exists()) {
                File cacheFile = new File("avatar_cache/" + user.getUsername() + ".png");

                // Chỉ copy nếu cache chưa có hoặc cũ hơn
                if (!cacheFile.exists() ||
                        cacheFile.lastModified() < sourceFile.lastModified()) {
                    Files.copy(sourceFile.toPath(), cacheFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[LOGIN] Updated avatar cache for " + user.getUsername());
                }
            }
        } catch (Exception e) {
            System.err.println("[LOGIN] Failed to update avatar cache: " + e.getMessage());
        }
    }

    private File findLatestAvatarFile(String username) {
        File avatarDir = new File("uploads/avatars");
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
}
