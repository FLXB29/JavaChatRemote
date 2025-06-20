package app.controller.chat.component;

import app.controller.chat.util.ChatUIHelper;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Component hiển thị thông báo trên giao diện
 */
public class ToastNotification {
    
    /**
     * Hiển thị thông báo toast
     * @param message Nội dung thông báo
     * @param node Node làm điểm tham chiếu để hiển thị toast
     * @param durationSeconds Thời gian hiển thị (giây)
     * @param playSound Có phát âm thanh khi hiển thị không
     */
    public static void show(String message, Node node, double durationSeconds, boolean playSound) {
        Platform.runLater(() -> {
            try {
                // Tạo container với style đẹp mắt
                VBox toast = new VBox();
                toast.setStyle("-fx-background-color: #2196F3; " + // Nền xanh
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 12 20; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4); " +
                        "-fx-border-width: 0;");

                // Label thông báo với style
                Label messageLabel = new Label(message);
                messageLabel.setStyle("-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: 600;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(300);// Giới hạn chiều rộng để tự động xuống dòng

                // Thêm vào container
                toast.getChildren().add(messageLabel);
                toast.setAlignment(Pos.CENTER);
                toast.setMaxWidth(300);// Giới hạn chiều rộng của toast

                // Lấy root container để thêm toast
                StackPane root = findRootStackPane(node);

                // Thêm toast vào root ở góc dưới bên phải
                if (root != null) {
                    root.getChildren().add(toast);
                    StackPane.setAlignment(toast, Pos.CENTER_RIGHT);//Vị trí xuat hiện
                    StackPane.setMargin(toast, new Insets(0, 25, 60, 0));

                    // Hiệu ứng fade in
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();

                    // Tự động biến mất sau một thời gian
                    PauseTransition delay = new PauseTransition(Duration.seconds(durationSeconds));
                    StackPane finalRoot = root;
                    delay.setOnFinished(e -> {
                        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toast);
                        fadeOut.setFromValue(1);
                        fadeOut.setToValue(0);
                        fadeOut.setOnFinished(event -> finalRoot.getChildren().remove(toast));
                        fadeOut.play();
                    });
                    delay.play();

                    // Phát âm thanh thông báo nếu cần
                    if (playSound) {
                        ChatUIHelper.playNotificationSound();
                    }
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to show toast notification: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Overload hiển thị thông báo toast
     * @param message Nội dung thông báo
     * @param node Node làm điểm tham chiếu
     */
    public static void show(String message, Node node) {
        show(message, node, 2, true); // Mặc định 3 giây và có âm thanh
    }
    
    /**
     * Tìm StackPane root từ node
     * @param node Node làm điểm tham chiếu
     * @return StackPane root hoặc null
     */
    private static StackPane findRootStackPane(Node node) {
        if (node == null || node.getScene() == null) return null;
        
        // Kiểm tra nếu root đã là StackPane
        if (node.getScene().getRoot() instanceof StackPane) {
            return (StackPane) node.getScene().getRoot();
        }
        
        // Nếu không, tạo mới StackPane và wrapper root hiện tại
        Scene scene = node.getScene();
        Node oldRoot = scene.getRoot();
        
        StackPane root = new StackPane();
        scene.setRoot(root);
        root.getChildren().add(oldRoot);
        
        return root;
    }
}
