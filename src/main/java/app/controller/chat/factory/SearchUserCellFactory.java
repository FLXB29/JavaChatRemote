package app.controller.chat.factory;

import app.controller.chat.util.AvatarAdapter;
import app.model.Friendship;
import app.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Callback;
import java.io.File;
import javafx.scene.image.Image;

/**
 * Factory tạo cell cho danh sách kết quả tìm kiếm người dùng
 */
public class SearchUserCellFactory implements Callback<ListView<User>, ListCell<User>> {

    // Interface để xử lý các sự kiện
    public interface UserActionHandler {
        void onAddFriend(User user);
        void onMessage(User user);
        Friendship.Status getFriendshipStatus(User currentUser, User targetUser);
        String getCurrentUsername();
    }
    
    private final UserActionHandler handler;
    private User currentUser;
    
    /**
     * Khởi tạo factory với handler và user hiện tại
     * @param handler Handler xử lý sự kiện
     * @param currentUser User đang đăng nhập
     */
    public SearchUserCellFactory(UserActionHandler handler, User currentUser) {
        this.handler = handler;
        this.currentUser = currentUser;
    }
    
    /**
     * Khởi tạo factory chỉ với handler
     * @param handler Handler xử lý sự kiện
     */
    public SearchUserCellFactory(UserActionHandler handler) {
        this.handler = handler;
        // currentUser sẽ được lấy từ handler khi cần
    }
    
    @Override
    public ListCell<User> call(ListView<User> lv) {
        // Nếu currentUser là null, lấy từ handler
        User userToUse = currentUser;
        if (userToUse == null && handler != null) {
            String username = handler.getCurrentUsername();
            if (username != null) {
                // Tạo đối tượng User tạm thời với username
                userToUse = new User();
                userToUse.setUsername(username);
            }
        }
        
        return new SearchUserCell(handler, userToUse);
    }
    
    /**
     * Cell tùy chỉnh cho kết quả tìm kiếm người dùng
     */
    private static class SearchUserCell extends ListCell<User> {
        private final Circle avatarCircle = new Circle(18);
        private final Label initialLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label statusLabel = new Label();
        private final Button actionBtn = new Button();
        private final VBox contentBox = new VBox(3);
        private final HBox mainBox = new HBox(12);
        
        private final UserActionHandler handler;
        private final User currentUser;
        
        /**
         * Khởi tạo cell với handler và user hiện tại
         * @param handler Handler xử lý sự kiện
         * @param currentUser User đang đăng nhập
         */
        public SearchUserCell(UserActionHandler handler, User currentUser) {
            this.handler = handler;
            this.currentUser = currentUser;
            
            // Setup styling
            nameLabel.getStyleClass().add("search-user-name");
            statusLabel.getStyleClass().add("search-user-status");
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
            
            // Setup initial label (hiển thị chữ cái đầu)
            initialLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            initialLabel.setAlignment(javafx.geometry.Pos.CENTER);
            
            // Setup avatar
            avatarCircle.setStroke(javafx.scene.paint.Color.WHITE);
            avatarCircle.setStrokeWidth(1);
            
            // Setup content box
            contentBox.getChildren().addAll(nameLabel, statusLabel);
            
            // Setup main layout
            mainBox.getChildren().add(new StackPane(avatarCircle, initialLabel));
            mainBox.getChildren().add(contentBox);
            
            // Add spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            mainBox.getChildren().add(spacer);
            
            // Add action button
            mainBox.getChildren().add(actionBtn);
            
            // Layout properties
            mainBox.setAlignment(Pos.CENTER_LEFT);
            mainBox.setPadding(new Insets(8));
            
            // Button action
            actionBtn.setOnAction(evt -> {
                evt.consume();
                User user = getItem();
                if (user != null && handler != null) {
                    Friendship.Status status = handler.getFriendshipStatus(currentUser, user);
                    
                    if (status == Friendship.Status.ACCEPTED) {
                        handler.onMessage(user);
                    } else if (status == null) {
                        handler.onAddFriend(user);
                    }
                }
            });
        }
        
        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            
            if (empty || user == null) {
                setGraphic(null);
                nameLabel.setText("");
                statusLabel.setText("");
                return;
            }
            
            // Set user name with fallback
            String displayName = user.getFullName();
            if (displayName == null || displayName.isBlank()) {
                displayName = user.getUsername();
            }
            nameLabel.setText(displayName);
            
            // Luôn hiển thị tên đăng nhập (username) nếu tên hiển thị không phải là username
            if (!displayName.equals(user.getUsername())) {
                statusLabel.setText("@" + user.getUsername());
            }
            
            // Update status and button based on friendship status
            updateUserStatusAndButton(user);
            
            // Đảm bảo avatar mặc định được hiển thị ngay lập tức
            createDefaultAvatar(user);
            
            // Thử tải avatar thực nếu có
            tryLoadAvatar(user);
            
            // Đảm bảo nút hành động hiển thị đúng
            actionBtn.setVisible(true);
            
            // Đảm bảo tất cả component đều hiển thị
            contentBox.setVisible(true);
            mainBox.setVisible(true);
            
            // Set the cell content
            setGraphic(mainBox);
        }
        
        private void updateUserStatusAndButton(User user) {
            // Kiểm tra handler
            if (handler == null) {
                statusLabel.setText("Không có thông tin");
                actionBtn.setVisible(false);
                return;
            }
            
            // Lấy username hiện tại từ handler nếu cần
            String currentUsername = handler.getCurrentUsername();
            
            // Handle current user
            if (user.getUsername().equals(currentUsername)) {
                statusLabel.setText("Bạn");
                statusLabel.setStyle("-fx-text-fill: #666;");
                actionBtn.setVisible(false);
                return;
            }
            
            // Get friendship status - đảm bảo currentUser không null
            Friendship.Status status;
            if (currentUser != null) {
                status = handler.getFriendshipStatus(currentUser, user);
            } else {
                // Tạo đối tượng User tạm thời nếu currentUser là null
                User tempUser = new User();
                tempUser.setUsername(currentUsername);
                status = handler.getFriendshipStatus(tempUser, user);
            }
            
            actionBtn.setVisible(true);
            
            if (status == null) {
                // Not friends yet
                statusLabel.setText("Chưa kết bạn");
                statusLabel.setStyle("-fx-text-fill: #666;");
                actionBtn.setText("Kết bạn");
                actionBtn.getStyleClass().setAll("btn", "btn-primary");
                actionBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                actionBtn.setDisable(false);
            } else if (status == Friendship.Status.PENDING) {
                // Friend request sent
                statusLabel.setText("Đã gửi lời mời");
                statusLabel.setStyle("-fx-text-fill: #ff9500;");
                actionBtn.setText("Đã gửi");
                actionBtn.getStyleClass().setAll("btn", "btn-secondary");
                actionBtn.setStyle("-fx-background-color: #888; -fx-text-fill: white;");
                actionBtn.setDisable(true);
            } else if (status == Friendship.Status.ACCEPTED) {
                // Already friends
                statusLabel.setText("Bạn bè");
                statusLabel.setStyle("-fx-text-fill: #4CAF50;");
                actionBtn.setText("Nhắn tin");
                actionBtn.getStyleClass().setAll("btn", "btn-success");
                actionBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                actionBtn.setDisable(false);
            } else {
                // Unknown status
                statusLabel.setText("Không xác định");
                statusLabel.setStyle("-fx-text-fill: #666;");
                actionBtn.setVisible(false);
            }
        }
        
        /**
         * Tạo avatar mặc định trước khi thử tải avatar thực
         */
        private void createDefaultAvatar(User user) {
            int colorIndex = Math.abs(user.getUsername().hashCode() % 8);
            javafx.scene.paint.Color[] COLORS = {
                javafx.scene.paint.Color.rgb(41, 128, 185),  // Xanh dương
                javafx.scene.paint.Color.rgb(39, 174, 96),   // Xanh lá
                javafx.scene.paint.Color.rgb(142, 68, 173),  // Tím
                javafx.scene.paint.Color.rgb(230, 126, 34),  // Cam
                javafx.scene.paint.Color.rgb(231, 76, 60),   // Đỏ
                javafx.scene.paint.Color.rgb(52, 73, 94),    // Xám đậm
                javafx.scene.paint.Color.rgb(241, 196, 15),  // Vàng
                javafx.scene.paint.Color.rgb(26, 188, 156)   // Ngọc
            };
            
            avatarCircle.setFill(COLORS[colorIndex]);
            initialLabel.setText(user.getUsername().substring(0, 1).toUpperCase());
            initialLabel.setVisible(true);
            avatarCircle.setVisible(true);
        }
        
        /**
         * Thử tải avatar từ đường dẫn trong user
         */
        private void tryLoadAvatar(User user) {
            try {
                String username = user.getUsername();
                
                // Đường dẫn chuẩn cho avatar
                String standardPath = app.util.PathUtil.getStandardAvatarPath(username);
                File standardFile = new File(standardPath);
                
                // Đường dẫn cache
                File cacheFile = new File(app.util.PathUtil.getAvatarCacheDirectory() + "/" + username + ".png");
                
                // 1. Ưu tiên sử dụng file trong cache
                if (cacheFile.exists()) {
                    // Đã có ảnh trong cache, tải từ đây
                    Image img = new Image(cacheFile.toURI().toString(), 
                                         avatarCircle.getRadius() * 2,
                                         avatarCircle.getRadius() * 2, 
                                         true, true);
                    
                    if (!img.isError()) {
                        avatarCircle.setFill(new javafx.scene.paint.ImagePattern(img));
                        initialLabel.setVisible(false);
                        System.out.println("[AVATAR_DEBUG] Loaded avatar from cache for: " + username);
                        return; // Thoát sớm vì đã tìm thấy avatar
                    }
                }
                
                // 2. Nếu không có trong cache, thử file chuẩn
                if (standardFile.exists()) {
                    // Cập nhật thông tin avatar trong User
                    user.setAvatarPath(standardPath);
                    user.setUseDefaultAvatar(false);
                    
                    // Tải avatar từ file chuẩn
                    Image img = new Image(standardFile.toURI().toString(),
                                         avatarCircle.getRadius() * 2,
                                         avatarCircle.getRadius() * 2,
                                         true, true);
                    
                    if (!img.isError()) {
                        avatarCircle.setFill(new javafx.scene.paint.ImagePattern(img));
                        initialLabel.setVisible(false);
                        System.out.println("[AVATAR_DEBUG] Loaded avatar from standard file for: " + username);
                        
                        // Đồng bộ vào cache
                        try {
                            if (!cacheFile.exists() || 
                                cacheFile.lastModified() < standardFile.lastModified()) {
                                app.util.AvatarFixUtil.fixAvatarForUser(username);
                            }
                        } catch (Exception e) {
                            System.err.println("[ERROR] Không thể đồng bộ avatar vào cache: " + e.getMessage());
                        }
                        
                        return; // Thoát vì đã tìm thấy avatar
                    }
                }
                
                // 3. Nếu cả hai đều không có hoặc lỗi, sử dụng avatar mặc định
                System.out.println("[AVATAR_DEBUG] No avatar found for " + username + ", using default");
                createDefaultAvatar(user);
                
            } catch (Exception e) {
                System.err.println("[ERROR] Không thể tải avatar cho " + user.getUsername() + ": " + e.getMessage());
                // Sử dụng avatar mặc định trong trường hợp lỗi
                createDefaultAvatar(user);
            }
        }
    }
}
