package app.controller;

import app.ServiceLocator;
import app.service.AuthService;
import app.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private Button btnRegister;
    @FXML private Hyperlink btnGoLogin;   // đổi Button ➜ Hyperlink
    @FXML private Label lblStatus;
    @FXML private TextField     txtPasswordPlain;

    private boolean showing = false;


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
    @FXML
    public void initialize() {
        lblStatus.setText("");
    }

    /* REGISTER */
    @FXML
    private void onRegister() {
        boolean ok = validate(txtUsername)
                & validate(txtPassword)
                & validate(txtConfirm)
                & matchPassword();

        if (!ok) return;

        // gọi service
        String pwd = showing ? txtPasswordPlain.getText() : txtPassword.getText();

        boolean created = ServiceLocator.userService()
                .register(txtUsername.getText().trim(),
                        pwd);
        lblStatus.setText(created ?
                "Đăng ký thành công! Vui lòng đăng nhập."
                : "Tên người dùng đã tồn tại!");
    }

    @FXML
    private void onGoLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = btnGoLogin.getScene(); // lấy scene hiện tại
            scene.setRoot(root);                 // thay root
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /* --- validate giống Login --- */
    private boolean validate(TextField tf) {
        if (tf.getText().isBlank()) {
            if (!tf.getStyleClass().contains("invalid"))
                tf.getStyleClass().add("invalid");
            return false;
        }
        tf.getStyleClass().remove("invalid");
        return true;
    }

    /* kiểm tra 2 password khớp */
    private boolean matchPassword() {
        String pwd = showing ? txtPasswordPlain.getText() : txtPassword.getText();

        boolean same = pwd.equals(txtConfirm.getText());
        if (!same) {
            lblStatus.setText("Mật khẩu không khớp!");
            txtConfirm.getStyleClass().add("invalid");
        } else {
            txtConfirm.getStyleClass().remove("invalid");
        }
        return same;
    }
}
