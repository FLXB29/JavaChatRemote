package app.controller.chat.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Class tiện ích cho UI chat
 */
public class ChatUIHelper {

    /**
     * Hiển thị thông báo lỗi
     *
     * @param message Tiêu đề thông báo
     * @param exception Ngoại lệ (có thể null)
     */
    public static void showError(String message, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(message);
        if (exception != null) {
            alert.setContentText(exception.getMessage());
        }
        alert.showAndWait();
    }

    /**
     * Hiển thị thông báo cảnh báo
     * @param message Nội dung cảnh báo
     */
    public static void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Cảnh báo");
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    /**
     * Hiển thị thông báo thành công
     * @param message Nội dung thông báo
     */
    public static void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thành công");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Tạo và cấu hình một container HBox cho các tin nhắn
     * @param inner Nội dung bên trong
     * @param outgoing Có phải tin nhắn gửi đi không
     * @return HBox đã được cấu hình
     */
    public static HBox createMessageContainer(Node inner, boolean outgoing) {
        HBox row = new HBox(inner);
        row.setFillHeight(true);
        row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setSpacing(4);
        row.setPadding(new Insets(2, 8, 2, 8));
        return row;
    }

    /**
     * Phát âm thanh thông báo
     */
    public static void playNotificationSound() {
        try {
            // Thử tìm file âm thanh trong resources
            String soundFile = "/sounds/message.mp4";
            URL soundUrl = ChatUIHelper.class.getResource(soundFile);

            if (soundUrl != null) {
                // Phát âm thanh
                Media sound = new Media(soundUrl.toString());
                MediaPlayer mediaPlayer = new MediaPlayer(sound);
                mediaPlayer.setVolume(0.3);
                mediaPlayer.play();
            } else {
                // Thử các file âm thanh thay thế
                String[] alternatives = {"/sounds/notification.mp3", "/sounds/alert.mp3", "/sounds/ding.mp3"};
                for (String alt : alternatives) {
                    URL altUrl = ChatUIHelper.class.getResource(alt);
                    if (altUrl != null) {
                        Media sound = new Media(altUrl.toString());
                        MediaPlayer mediaPlayer = new MediaPlayer(sound);
                        mediaPlayer.setVolume(0.3);
                        mediaPlayer.play();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Bỏ qua lỗi âm thanh
            System.out.println("[WARN] Không thể phát âm thanh thông báo: " + e.getMessage());
        }
    }

    /**
     * Thêm badge thông báo vào tab
     * @param tabPane TabPane chứa tab
     * @param tabText Văn bản của tab cần thêm badge
     * @param count Số lượng thông báo (nếu > 0)
     */
    public static void addNotificationBadgeToTab(TabPane tabPane, String tabText, int count) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().equals(tabText) || tab.getText().startsWith(tabText)) {
                String badgeText = count > 9 ? "(" + tabText + " 9+)" : "(" + tabText + " " + count + ")";
                tab.setText(badgeText);

                // Style tab để highlight
                tab.getStyleClass().add("tab-notification");
                break;
            }
        }
    }

    /**
     * Xóa badge thông báo khỏi tab
     * @param tabPane TabPane chứa tab
     * @param tabText Văn bản của tab cần xóa badge
     */
    public static void removeNotificationBadgeFromTab(TabPane tabPane, String tabText) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().startsWith(tabText)) {
                tab.setText(tabText);
                tab.getStyleClass().remove("tab-notification");
                break;
            }
        }
    }

    /**
     * Cập nhật badge số tin nhắn chưa đọc trên tiêu đề ứng dụng
     * @param stage Stage của ứng dụng
     * @param unreadCount Số lượng tin nhắn chưa đọc
     */
    public static void updateAppBadge(Stage stage, int unreadCount) {
        if (unreadCount > 0) {
            stage.setTitle("MyMessenger (" + unreadCount + ")");
        } else {
            stage.setTitle("MyMessenger");
        }
    }
    
    /**
     * Format kích thước file
     * @param bytes Kích thước file tính bằng byte
     * @return Chuỗi đã được format
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp-1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
