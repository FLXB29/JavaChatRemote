package app.controller.chat.factory;

import app.controller.chat.util.AvatarAdapter;
import app.model.Friendship;
import app.model.User;
import app.ServiceLocator;
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

import java.time.format.DateTimeFormatter;

/**
 * Factory tạo cell cho danh sách lời mời kết bạn
 * Mỗi cell hiển thị thông tin người gửi/nhận lời mời và các nút tương ứng
 */
public class FriendRequestCellFactory implements Callback<ListView<Friendship>, ListCell<Friendship>> {

    /**
     * Interface xử lý sự kiện Accept/Reject lời mời kết bạn
     */
    public interface FriendRequestHandler {
        void onAcceptFriendRequest(Friendship friendship);
        void onRejectFriendRequest(Friendship friendship);
        void onCancelFriendRequest(Friendship friendship);
    }
    
    private final FriendRequestHandler handler;
    private final String currentUsername;
    
    /**
     * Constructor
     * @param handler Handler xử lý sự kiện Accept/Reject
     * @param currentUsername Username của người dùng hiện tại
     */
    public FriendRequestCellFactory(FriendRequestHandler handler, String currentUsername) {
        this.handler = handler;
        this.currentUsername = currentUsername;
    }
    
    @Override
    public ListCell<Friendship> call(ListView<Friendship> param) {
        return new FriendRequestCell(handler, currentUsername);
    }
    
    /**
     * Cell hiển thị lời mời kết bạn
     */
    private static class FriendRequestCell extends ListCell<Friendship> {
        private final Circle avatarCircle = new Circle(20);
        private final Label initialLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label timeLabel = new Label();
        private final Button acceptBtn = new Button("Đồng ý");
        private final Button rejectBtn = new Button("Từ chối");
        private final Button cancelBtn = new Button("Hủy lời mời");
        private final HBox buttonBox = new HBox(8);
        private final VBox contentBox = new VBox(4);
        private final HBox mainBox = new HBox(12);
        
        private final FriendRequestHandler handler;
        private final String currentUsername;
        
        /**
         * Constructor
         * @param handler Handler xử lý sự kiện Accept/Reject
         * @param currentUsername Username của người dùng hiện tại
         */
        public FriendRequestCell(FriendRequestHandler handler, String currentUsername) {
            this.handler = handler;
            this.currentUsername = currentUsername;
            
            // Styling
            acceptBtn.getStyleClass().addAll("btn", "btn-success", "btn-sm");
            acceptBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
            
            rejectBtn.getStyleClass().addAll("btn", "btn-danger", "btn-sm");
            rejectBtn.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-cursor: hand;");
            
            cancelBtn.getStyleClass().addAll("btn", "btn-warning", "btn-sm");
            cancelBtn.setStyle("-fx-background-color: #FFC107; -fx-text-fill: white; -fx-cursor: hand;");
            
            nameLabel.getStyleClass().add("friend-request-name");
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            
            timeLabel.getStyleClass().add("friend-request-time");
            timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
            
            // Event handlers
            acceptBtn.setOnAction(evt -> {
                evt.consume();
                if (handler != null) {
                    handler.onAcceptFriendRequest(getItem());
                }
            });
            
            rejectBtn.setOnAction(evt -> {
                evt.consume();
                if (handler != null) {
                    handler.onRejectFriendRequest(getItem());
                }
            });
            
            cancelBtn.setOnAction(evt -> {
                evt.consume();
                if (handler != null) {
                    handler.onCancelFriendRequest(getItem());
                }
            });
            
            // Layout
            contentBox.getChildren().addAll(nameLabel, timeLabel, buttonBox);
            mainBox.getChildren().addAll(
                    new StackPane(avatarCircle, initialLabel),
                    contentBox
            );
            mainBox.setAlignment(Pos.CENTER_LEFT);
            mainBox.setPadding(new Insets(10));
        }
        
        @Override
        protected void updateItem(Friendship friendship, boolean empty) {
            super.updateItem(friendship, empty);
            if (empty || friendship == null) {
                setGraphic(null);
                return;
            }
            
            // Clear buttonBox to update buttons based on context
            buttonBox.getChildren().clear();
            
            User user1 = friendship.getUser1(); // Người gửi lời mời
            User user2 = friendship.getUser2(); // Người nhận lời mời
            

            User otherUser;
            String actionText;

            if (currentUsername.equals(user1.getUsername())) {
                // Tôi là người gửi (user1), tôi thấy thông tin của người nhận (user2)
                otherUser = user2;
                actionText = "Đã gửi lời mời tới" ;
                buttonBox.getChildren().add(cancelBtn);
            } else {
                // Tôi là người nhận (user2), tôi thấy thông tin của người gửi (user1)
                otherUser = user1;
                actionText = "Đã nhận lời mời từ" ;
                buttonBox.getChildren().addAll(acceptBtn, rejectBtn);
            }

            nameLabel.setText(otherUser.getUsername());
            timeLabel.setText(actionText + " - " + friendship.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

            User displayUser = otherUser;
            AvatarAdapter.updateUserAvatar(displayUser, avatarCircle, initialLabel);
            setGraphic(mainBox);
        }
    }
}
