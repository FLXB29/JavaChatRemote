package app.controller.chat.factory;

import app.controller.chat.model.RecentChatCellData;
import app.controller.chat.util.AvatarAdapter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Callback;

import java.util.function.Consumer;

/**
 * Factory tạo cell cho danh sách chat gần đây
 */
public class RecentChatCellFactory implements Callback<ListView<RecentChatCellData>, ListCell<RecentChatCellData>> {
    private Consumer<RecentChatCellData> onChatSelected;
    
    /**
     * Thiết lập callback khi chat được chọn
     * @param onChatSelected Consumer xử lý khi chat được chọn
     */
    public void setOnChatSelected(Consumer<RecentChatCellData> onChatSelected) {
        this.onChatSelected = onChatSelected;
    }

    @Override
    public ListCell<RecentChatCellData> call(ListView<RecentChatCellData> lv) {
        return new RecentChatCell(onChatSelected);
    }
    
    /**
     * Cell tùy chỉnh cho danh sách chat gần đây
     */
    private static class RecentChatCell extends ListCell<RecentChatCellData> {
        private final Circle avatarCircle = new Circle(24);
        private final Label initialLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label messageLabel = new Label();
        private final Label timeLabel = new Label();
        private final Label unreadBadge = new Label();
        private final HBox mainBox = new HBox(12);
        private final VBox contentBox = new VBox(4);
        private final HBox topRow = new HBox();
        private final Consumer<RecentChatCellData> onChatSelected;
        
        public RecentChatCell(Consumer<RecentChatCellData> onChatSelected) {
            this.onChatSelected = onChatSelected;
            
            // Style components
            nameLabel.getStyleClass().add("chat-name");
            messageLabel.getStyleClass().add("chat-message");
            timeLabel.getStyleClass().add("chat-time");
            unreadBadge.getStyleClass().add("unread-badge");
            
            // Additional inline styles
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
            timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
            unreadBadge.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                    "-fx-background-radius: 12; -fx-padding: 2 6; -fx-font-size: 10px;");
            
            // Setup avatar
            StackPane avatarPane = new StackPane(avatarCircle, initialLabel);
            avatarPane.setMinWidth(48);
            
            // Setup content structure
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            topRow.getChildren().addAll(nameLabel, spacer, timeLabel);
            
            contentBox.getChildren().addAll(topRow, messageLabel);
            HBox.setHgrow(contentBox, Priority.ALWAYS);
            
            // Main layout
            mainBox.getChildren().addAll(avatarPane, contentBox, unreadBadge);
            mainBox.setAlignment(Pos.CENTER_LEFT);
            mainBox.setPadding(new Insets(8, 12, 8, 8));
            
            // Cell selection style
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            
            // Xử lý sự kiện click
            mainBox.setOnMouseClicked(this::handleClick);
        }
        
        private void handleClick(MouseEvent event) {
            if (getItem() != null && onChatSelected != null) {
                onChatSelected.accept(getItem());
                event.consume();
            }
        }
        
        @Override
        protected void updateItem(RecentChatCellData chat, boolean empty) {
            super.updateItem(chat, empty);
            
            if (empty || chat == null) {
                setGraphic(null);
                return;
            }
            
            // Set chat name
            nameLabel.setText(chat.chatName);
            
            // Set last message with reasonable length
            messageLabel.setText(chat.lastMessage);
            
            // Set time
            timeLabel.setText(chat.timeString);
            
            // Handle unread messages badge
            if (chat.unreadCount > 0) {
                unreadBadge.setText(String.valueOf(chat.unreadCount));
                unreadBadge.setVisible(true);
                
                // Highlight chat with unread messages
                mainBox.setStyle("-fx-background-color: rgba(33, 150, 243, 0.1);");
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2196F3;");
            } else {
                unreadBadge.setVisible(false);
                mainBox.setStyle("");
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            }
            
            // Handle avatar display
            AvatarAdapter.updateRecentChatAvatar(chat, avatarCircle, initialLabel);
            
            // Set cell content
            setGraphic(mainBox);
        }
    }
}
