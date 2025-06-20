package app.controller.chat.factory;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * Factory tạo cell cho danh sách nhóm chat
 */
public class GroupChatCellFactory implements Callback<ListView<String>, ListCell<String>> {
    private Consumer<String> onGroupSelected;
    
    /**
     * Thiết lập callback khi nhóm được chọn
     * @param onGroupSelected Consumer xử lý khi nhóm được chọn
     */
    public void setOnGroupSelected(Consumer<String> onGroupSelected) {
        this.onGroupSelected = onGroupSelected;
    }

    @Override
    public ListCell<String> call(ListView<String> lv) {
        return new GroupChatCell(onGroupSelected);
    }
    
    /**
     * Cell tùy chỉnh cho danh sách nhóm
     */
    private static class GroupChatCell extends ListCell<String> {
        private final HBox mainBox = new HBox(10);
        private final Label nameLabel = new Label();
        private final Circle avatarCircle = new Circle(16);
        private final Label initialLabel = new Label();
        private final Consumer<String> onGroupSelected;
        
        public GroupChatCell(Consumer<String> onGroupSelected) {
            this.onGroupSelected = onGroupSelected;
            
            // Style components
            nameLabel.getStyleClass().add("group-name");
            nameLabel.setStyle("-fx-font-size: 14px;");
            
            // Setup avatar
            StackPane avatarPane = new StackPane();
            avatarCircle.setFill(Color.valueOf("#3f51b5"));
            
            FontIcon groupIcon = new FontIcon("fas-users");
            groupIcon.setIconColor(Color.WHITE);
            groupIcon.setIconSize(16);
            
            avatarPane.getChildren().addAll(avatarCircle, groupIcon);
            avatarPane.setMinWidth(32);
            
            // Main layout
            mainBox.getChildren().addAll(avatarPane, nameLabel);
            mainBox.setAlignment(Pos.CENTER_LEFT);
            mainBox.setPadding(new Insets(8));
            
            // Cell selection style
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            
            // Xử lý sự kiện click
            mainBox.setOnMouseClicked(this::handleClick);
        }
        
        private void handleClick(MouseEvent event) {
            if (getItem() != null && onGroupSelected != null) {
                onGroupSelected.accept(getItem());
                event.consume();
            }
        }
        
        @Override
        protected void updateItem(String groupName, boolean empty) {
            super.updateItem(groupName, empty);
            
            if (empty || groupName == null) {
                setGraphic(null);
                return;
            }
            
            // Set group name
            nameLabel.setText(groupName);
            
            // Set cell content
            setGraphic(mainBox);
        }
    }
}
