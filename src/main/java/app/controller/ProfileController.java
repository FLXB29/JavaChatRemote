package app.controller;

import app.ServiceLocator;
import app.controller.chat.ChatControllerRefactored;
import app.model.User;
import app.service.UserService;
import app.util.EmailUtil;
import app.util.PasswordUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class ProfileController {

    @FXML private Label lblUsername;
    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtCurrentPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private Button btnSaveProfile;
    @FXML private Button btnChangePassword;
    @FXML private Button btnRequestOTP;
    @FXML private Button btnChangeAvatar;
    @FXML private Button btnUseDefaultAvatar;
    @FXML private Button btnBack;
    @FXML private Label lblStatus;
    @FXML private VBox otpContainer;
    @FXML private TextField txtOTP;
    @FXML private Button btnVerifyOTP;
    @FXML private ImageView imgAvatar;
    @FXML private Circle avatarCircle;
    @FXML private Label lblInitial;
    
    private UserService userService;
    private String currentUsername;
    private User currentUser;
    private String currentAvatarPath;
    private boolean useDefaultAvatar = true;
    
    // Biến để lưu mã OTP và thời gian hết hạn
    private String currentOtp = null;
    private LocalDateTime otpExpiryTime = null;
    private Timer otpTimer = null;
    private int countdownSeconds = 0;
    // cờ để biết OTP đã xác minh chưa
    private boolean otpVerified = false;
    private boolean otpRequested  = false;   // đã bấm "Gửi OTP" chưa


    // Màu sắc cho avatar mặc định
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
    
    @FXML
    public void initialize() {
        userService = ServiceLocator.userService();
        
//        // Thiết lập sự kiện cho nút gửi OTP
//        btnRequestOTP.setOnAction(e -> requestOtp());
//
//        // Thiết lập sự kiện cho nút đổi mật khẩu
//        btnChangePassword.setOnAction(e -> changePassword());
    }
    
    /**
     * Thiết lập người dùng hiện tại
     */
    public void setCurrentUser(String username) {
        this.currentUsername = username;
        this.currentUser = userService.getUser(username);
        
        if (currentUser != null) {
            // Hiển thị thông tin người dùng
            lblUsername.setText(currentUser.getUsername());
            if (currentUser.getFullName() != null && !currentUser.getFullName().isEmpty()) {
                txtFullName.setText(currentUser.getFullName());
            }
            if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                txtEmail.setText(currentUser.getEmail());
            }
            
            // Hiển thị avatar
            useDefaultAvatar = currentUser.isUseDefaultAvatar();
            if (useDefaultAvatar) {
                showDefaultAvatar(currentUser.getUsername());
            } else if (currentUser.getAvatarPath() != null) {
                showCustomAvatar(currentUser.getAvatarPath());
            }
        }
    }

    private void showDefaultAvatar(String username) {
        imgAvatar.setVisible(false);
        lblInitial.setVisible(true);
        lblInitial.setText(username.substring(0, 1).toUpperCase());
        
        // Chọn màu ngẫu nhiên cho avatar
        int colorIndex = Math.abs(username.hashCode() % AVATAR_COLORS.length);
        avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
        avatarCircle.setRadius(50); // Đặt kích thước lớn hơn cho profile
        avatarCircle.setStroke(Color.WHITE);
        avatarCircle.setStrokeWidth(2);
    }

    private void showCustomAvatar(String avatarPath) {
        try {
            File avatarFile = new File(avatarPath);
            if (avatarFile.exists()) {
                Image img = new Image(avatarFile.toURI().toString(), false);
                ImagePattern pattern = new ImagePattern(img, 0, 0, 1, 1, true);
                avatarCircle.setFill(pattern);
                avatarCircle.setRadius(50); // Đặt kích thước lớn hơn cho profile
                avatarCircle.setStroke(Color.WHITE);
                avatarCircle.setStrokeWidth(2);
                imgAvatar.setVisible(false);
                lblInitial.setVisible(false);
                currentAvatarPath = avatarPath;
            } else {
                System.err.println("Avatar file không tồn tại: " + avatarPath);
                // Thử tìm file trong thư mục avatar_cache
                try {
                    String fileName = Paths.get(avatarPath).getFileName().toString();
                    Path cachePath = Paths.get(System.getProperty("user.dir"), "avatar_cache", fileName);
                    
                    if (Files.exists(cachePath)) {
                        Image img = new Image(cachePath.toUri().toString(), false);
                        ImagePattern pattern = new ImagePattern(img, 0, 0, 1, 1, true);
                        avatarCircle.setFill(pattern);
                        avatarCircle.setRadius(50);
                        avatarCircle.setStroke(Color.WHITE);
                        avatarCircle.setStrokeWidth(2);
                        imgAvatar.setVisible(false);
                        lblInitial.setVisible(false);
                        currentAvatarPath = cachePath.toString().replace('\\', '/');
                    } else {
                        showDefaultAvatar(currentUsername);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showDefaultAvatar(currentUsername);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showDefaultAvatar(currentUsername);
        }
    }

    @FXML
    private void onSaveProfile() {
        String fullName = txtFullName.getText().trim();
        String email = txtEmail.getText().trim();
        
        // Kiểm tra email
        if (!validateEmail(email)) {
            return;
        }
        
        // Cập nhật thông tin người dùng
        boolean success = userService.updateUserInfo(currentUsername, fullName, email);
        
        if (success) {
            // Cập nhật avatar nếu cần
            if (useDefaultAvatar) {
                userService.useDefaultAvatar(currentUsername);
            } else if (currentAvatarPath != null) {
                userService.updateAvatar(currentUsername, new File(currentAvatarPath));
            }
            
            showSuccess("Cập nhật thông tin thành công!");
            // Tải lại dữ liệu người dùng
            setCurrentUser(currentUsername);
        } else {
            showError("Không thể cập nhật thông tin. Vui lòng thử lại.");
        }
    }

    @FXML
    private void onChangeAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh đại diện");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File selectedFile = fileChooser.showOpenDialog(btnChangeAvatar.getScene().getWindow());
        if (selectedFile != null) {
            try {
                // Update avatar với logic mới
                if (userService.updateAvatar(currentUsername, selectedFile)) {
                    // Upload avatar to server
                    ServiceLocator.chat().getClient().uploadAvatar(selectedFile);

                    // Hiển thị avatar mới với đường dẫn tuyệt đối
                    String avatarFileName = currentUsername + ".png";
                    Path avatarPath = Paths.get(System.getProperty("user.dir"), "uploads", "avatars", avatarFileName);
                    currentAvatarPath = avatarPath.toString().replace('\\', '/');
                    useDefaultAvatar = false;
                    showCustomAvatar(currentAvatarPath);

                    // Request avatars để đồng bộ
                    ServiceLocator.chat().getClient().requestAllAvatars();

                    showSuccess("Đã thay đổi ảnh đại diện!");
                } else {
                    showError("Không thể cập nhật ảnh đại diện");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showError("Không thể thay đổi ảnh đại diện: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onUseDefaultAvatar() {
        useDefaultAvatar = true;
        currentAvatarPath = null;
        
        // Tạo avatar mặc định và cập nhật
        if (userService.useDefaultAvatar(currentUsername)) {
            showDefaultAvatar(currentUsername);
            
            // Lấy file avatar mặc định và gửi lên server
            User user = userService.getUser(currentUsername);
            if (user != null && user.getAvatarPath() != null) {
                try {
                    // Sử dụng đường dẫn tuyệt đối
                    File defaultAvatarFile = new File(user.getAvatarPath());
                    if (defaultAvatarFile.exists()) {
                        ServiceLocator.chat().getClient().uploadAvatar(defaultAvatarFile);
                    } else {
                        // Thử tìm trong thư mục cache
                        String fileName = Paths.get(user.getAvatarPath()).getFileName().toString();
                        Path cachePath = Paths.get(System.getProperty("user.dir"), "avatar_cache", fileName);
                        
                        if (Files.exists(cachePath)) {
                            ServiceLocator.chat().getClient().uploadAvatar(cachePath.toFile());
                        } else {
                            throw new IOException("Không tìm thấy file avatar: " + user.getAvatarPath());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    showError("Không thể gửi avatar mặc định lên server: " + e.getMessage());
                    return;
                }
            }
            
            showSuccess("Đã chuyển sang ảnh đại diện mặc định!");
        } else {
            showError("Không thể cập nhật avatar mặc định");
        }
    }

    @FXML
    private void requestOtp() {
        // Kiểm tra xem có đang đếm ngược không
        if (countdownSeconds > 0) {
            return;
        }
        
        // Lấy email của người dùng
        User user = userService.getUser(currentUsername);
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            showError("Không thể gửi mã OTP: Email không tồn tại");
            return;
        }
        
        // Tạo mã OTP và lưu vào database
        currentOtp = userService.generateOTP(currentUsername);
        
        if (currentOtp == null) {
            showError("Không thể tạo mã OTP. Vui lòng thử lại sau.");
            return;
        }
        
        // Đặt thời gian hết hạn (5 phút)
        otpExpiryTime = LocalDateTime.now().plusMinutes(5);
        otpRequested = true;                         // ĐÁNH DẤU đã yêu cầu
        otpVerified = false;                        // reset
        
        // OTP đã được gửi bởi userService.generateOTP()
        boolean success = true;
        
        if (success) {
            // Bắt đầu đếm ngược 60 giây
            startOtpCountdown();
            otpContainer.setManaged(true);
            otpContainer.setVisible(true);      // ← hiện khung OTP
            txtOTP.clear();                             // xoá bất cứ thứ gì cũ
            txtOTP.requestFocus();
            startOtpCountdown();                        // 60 s đếm ngược nút gửi lại
            showSuccess("Đã gửi mã OTP đến email của bạn!, nó có hiệu lực trong 5 phút");
        } else {
            showError("Không thể gửi mã OTP. Vui lòng thử lại sau.");
        }
    }
    
    private void startOtpCountdown() {
        // Hủy timer cũ nếu có
        if (otpTimer != null) {
            otpTimer.cancel();
        }
        
        // Tạo timer mới
        otpTimer = new Timer();
        countdownSeconds = 60;
        
        // Cập nhật nút gửi OTP
        updateSendOtpButton();
        
        // Lên lịch đếm ngược
        otpTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    countdownSeconds--;
                    if (countdownSeconds <= 0) {
                        otpTimer.cancel();
                    }
                    updateSendOtpButton();
                });
            }
        }, 1000, 1000);
    }
    
    private void updateSendOtpButton() {
        if (countdownSeconds > 0) {
            btnRequestOTP.setText("Gửi lại (" + countdownSeconds + "s)");
            btnRequestOTP.setDisable(true);
        } else {
            btnRequestOTP.setText("Gửi mã OTP");
            btnRequestOTP.setDisable(false);
        }
    }

    @FXML
    private void onVerifyOtp() {           // ← khớp với FXML
        String otpCode = txtOTP.getText().trim();

        if (otpCode.isEmpty()) {
            showError("Bạn chưa nhập mã OTP");
            txtOTP.requestFocus();
            return;
        }
        
        // Lấy thông tin OTP từ database
        User user = userService.getUser(currentUsername);
        if (user == null) {
            showError("Không thể xác thực OTP: Không tìm thấy thông tin người dùng");
            return;
        }
        
        // Kiểm tra OTP từ database
        boolean valid = user.getOtpCode() != null
                && otpCode.equals(user.getOtpCode())
                && System.currentTimeMillis() <= user.getOtpExpiryTime();

        if (valid) {
            otpVerified = true;            // bật cờ
            showSuccess("OTP hợp lệ! Bạn có thể đổi mật khẩu");
            // ẩn khung OTP sau khi thành công (tuỳ bạn)
            otpContainer.setManaged(false);
            otpContainer.setVisible(false);
            txtOTP.clear();
        } else {
            showError("OTP không hợp lệ hoặc đã hết hạn");
        }
    }


    @FXML
    private void changePassword() {
        String currentPassword = txtCurrentPassword.getText().trim();
        String newPassword = txtNewPassword.getText().trim();
        String confirmPassword = txtConfirmPassword.getText().trim();
        String otpCode = txtOTP.getText().trim();
        
        // Kiểm tra mật khẩu hiện tại
        if (currentPassword.isEmpty()) {
            showError("Vui lòng nhập mật khẩu hiện tại");
            txtCurrentPassword.requestFocus();
            return;
        }
        
        // Kiểm tra mật khẩu mới
        if (newPassword.isEmpty()) {
            showError("Vui lòng nhập mật khẩu mới");
            txtNewPassword.requestFocus();
            return;
        }
        
        // Kiểm tra xác nhận mật khẩu
        if (confirmPassword.isEmpty()) {
            showError("Vui lòng xác nhận mật khẩu mới");
            txtConfirmPassword.requestFocus();
            return;
        }
        
        // Kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp nhau không
        if (!newPassword.equals(confirmPassword)) {
            showError("Mật khẩu mới và xác nhận mật khẩu không khớp");
            txtConfirmPassword.requestFocus();
            return;
        }

        /* 2. bắt buộc đã yêu cầu OTP */
        if (!otpRequested) {
            showError("Bạn phải bấm Gửi OTP trước khi đổi mật khẩu");
            return;
        }

        /* 3. kiểm tra OTP đã được yêu cầu */
        // Không cần kiểm tra OTP ở đây vì UserService.changePasswordWithOTP sẽ kiểm tra
        // OTP từ database. Chỉ cần đảm bảo người dùng đã yêu cầu OTP.
        if (otpCode.isEmpty()) {
            showError("Vui lòng nhập mã OTP");
            txtOTP.requestFocus();
            return;
        }

        /* 4. Kiểm tra mật khẩu hiện tại trước */
        // Kiểm tra mật khẩu hiện tại trước khi thực hiện đổi mật khẩu
        User user = userService.getUser(currentUsername);
        if (user != null && !PasswordUtil.checkPassword(currentPassword, user.getPasswordHash())) {
            showError("Mật khẩu hiện tại không đúng. Vui lòng kiểm tra lại.");
            txtCurrentPassword.requestFocus();
            return;
        }
        
        /* 5. OTP hợp lệ và mật khẩu hiện tại đúng → đổi mật khẩu với xác thực OTP */
        boolean success = userService.changePasswordWithOTP(currentUsername,
                currentPassword,
                newPassword,
                otpCode);
        if (success) {
            showSuccess("Đổi mật khẩu thành công!");
            // reset mọi thứ
            txtCurrentPassword.clear();
            txtNewPassword.clear();
            txtConfirmPassword.clear();
            txtOTP.clear();
            otpRequested = false;
            currentOtp   = null;
            otpContainer.setManaged(false);
            otpContainer.setVisible(false);
        } else {
            // Hiển thị thông báo lỗi chi tiết hơn
            showError("Không thể đổi mật khẩu. Mã OTP không hợp lệ hoặc đã hết hạn.");
            // Focus vào trường OTP để người dùng nhập lại
            txtOTP.requestFocus();
        }
    }

    @FXML
    private void onBack() {
        try {
            // Quay lại trang chat
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load();
            
            // Lấy Scene hiện tại
            Scene scene = btnBack.getScene();
            scene.setRoot(root);
            
            // Lấy controller của chat.fxml, gán username và yêu cầu cập nhật
            ChatControllerRefactored chatCtrl = loader.getController();
            
            // Liên kết controller với service
            ServiceLocator.chat().bindRefactoredUI(chatCtrl);
            
            chatCtrl.setCurrentUser(currentUsername);
            
            // Yêu cầu danh sách user online mới từ server
            ServiceLocator.chat().getClient().requestUserList();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Không thể quay lại trang chat", e);
        }
    }

    private boolean validateEmail(String email) {
        if (email.isEmpty()) {
            showError("Email không được để trống");
            txtEmail.requestFocus();
            return false;
        }
        
        // Kiểm tra định dạng email
        if (!email.matches("^[A-Za-z0-9+_.-]+@gmail\\.com$")) {
            showError("Email phải có định dạng @gmail.com");
            txtEmail.requestFocus();
            return false;
        }
        
        return true;
    }

    private void showSuccess(String message) {
        lblStatus.setText(message);
        lblStatus.setStyle("-fx-text-fill: #4caf50; -fx-font-style: italic;");
    }

    private void showError(String message) {
        lblStatus.setText(message);
        lblStatus.setStyle("-fx-text-fill: #f44336; -fx-font-style: italic;");
    }
    
    private void showError(String message, Exception e) {
        Platform.runLater(() -> {
            lblStatus.setStyle("-fx-text-fill: red;");
            lblStatus.setText(message + ": " + e.getMessage());
        });
    }
}