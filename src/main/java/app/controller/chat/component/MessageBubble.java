package app.controller.chat.component;

import app.controller.chat.util.ChatUIHelper;
import com.gluonhq.emoji.Emoji;
import com.gluonhq.emoji.util.TextUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Component hiển thị tin nhắn trong chat
 */
public class MessageBubble {

    /**
     * Tạo một message bubble cho tin nhắn văn bản
     * @param content Nội dung tin nhắn
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @param username Tên người gửi
     * @param time Thời gian gửi
     * @return HBox chứa bong bóng tin nhắn
     */
    public static HBox createTextMessageBubble(String content, boolean isOutgoing, String username, LocalDateTime time) {
        HBox bubbleBox = new HBox(5);
        bubbleBox.setPrefWidth(Double.MAX_VALUE);
        bubbleBox.setAlignment(isOutgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageVBox = new VBox(2);

        if (!isOutgoing) {
            Label fromLabel = new Label(username);
            fromLabel.setStyle("-fx-text-fill:#b0b0b0; -fx-font-size:10;");
            messageVBox.getChildren().add(fromLabel);
        }

        Node msgNode = buildMessageNode(content, isOutgoing);
        messageVBox.getChildren().add(msgNode);

        if (time != null) {
            String timeText = time.format(DateTimeFormatter.ofPattern("HH:mm"));
            Label timeLabel = new Label(timeText);
            timeLabel.setStyle("-fx-text-fill:#999999; -fx-font-size:10;");
            messageVBox.getChildren().add(timeLabel);
        }

        bubbleBox.getChildren().add(messageVBox);
        return bubbleBox;
    }

    /**
     * Tạo message node với emoji
     * @param content Nội dung tin nhắn
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @return Node hiển thị nội dung tin nhắn
     */
    private static Node buildMessageNode(String content, boolean isOutgoing) {
        TextFlow flow = buildEmojiTextFlow(content);
        flow.setMaxWidth(600);
        flow.setLineSpacing(2);

        // Đặt kích thước font và icon lớn hơn
        flow.setStyle("-fx-font-size: 16px;");

        StackPane bubble = new StackPane(flow);
        bubble.setPadding(new Insets(8));
        bubble.setStyle(isOutgoing
                ? "-fx-background-color:#0078fe; -fx-background-radius:8;"
                : "-fx-background-color:#3a3a3a; -fx-background-radius:8;");
        flow.setStyle("-fx-fill:white; -fx-font-size:16;");

        return bubble;
    }

    /**
     * Tạo TextFlow với hỗ trợ emoji
     * @param message Nội dung tin nhắn
     * @return TextFlow đã được xây dựng
     */
    public static TextFlow buildEmojiTextFlow(String message) {
        TextFlow flow = new TextFlow();

        for (Object part : TextUtils.convertToStringAndEmojiObjects(message)) {
            if (part instanceof String str) {
                // Văn bản thường → tô trắng
                Text t = new Text(str);
                t.setFill(Color.WHITE);
                t.setStyle("-fx-font-size:16px;");  // Tăng kích thước chữ

                flow.getChildren().add(t);
            }
            else if (part instanceof Emoji emoji) {
                /* ① Thử Twemoji trước */
                String hex = emoji.getUnified().toLowerCase();
                String p = "/META-INF/resources/webjars/twemoji/14.0.2/assets/72x72/" + hex + ".png";
                URL u = MessageBubble.class.getResource(p);
                if (u != null) {
                    ImageView iv = new ImageView(u.toString());
                    iv.setFitWidth(16);
                    iv.setPreserveRatio(true);
                    flow.getChildren().add(iv);
                    continue;
                }

                /* ② fallback Gluon sprite */
                try {
                    flow.getChildren().add(new ImageView(emoji.getImage()));
                } catch (Exception ex) {
                    /* ③ cuối cùng: Unicode */
                    flow.getChildren().add(new Text(emoji.character()));
                }
            }
        }
        return flow;
    }

    /**
     * Tạo file message bubble
     * @param name Tên file
     * @param size Kích thước file
     * @param id ID file
     * @param thumbnailData Dữ liệu thumbnail (có thể null)
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @param onDownload Callback khi nút tải được nhấn
     * @return VBox chứa tin nhắn file
     */
    public static VBox createFileMessageBubble(String name, long size, String id, byte[] thumbnailData, 
                                               boolean isOutgoing, DownloadCallback onDownload) {
        VBox box = new VBox(6);
        box.setUserData(id);
        box.getStyleClass().addAll("file-message", isOutgoing ? "outgoing" : "incoming");

        // Kiểm tra nếu file là hình ảnh
        boolean isImage = name.matches("(?i).+\\.(png|jpe?g|gif)");
        
        /* Thumbnail nếu có */
        if (thumbnailData != null) {
            ImageView iv = new ImageView(new Image(new ByteArrayInputStream(thumbnailData)));
            iv.setFitWidth(260);
            iv.setPreserveRatio(true);
            iv.setId("thumb");
            box.getChildren().add(iv);
        } else if (isImage) {
            // Nếu là hình ảnh nhưng chưa có thumbnail
            Label loadingLabel = new Label("Đang tải hình ảnh...");
            loadingLabel.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
            loadingLabel.setId("loading-thumb");
            box.getChildren().add(loadingLabel);
        }

        Label lbl = new Label(name);
        Label sz = new Label(ChatUIHelper.formatFileSize(size));
        
        Label btn = new Label("Tải xuống");
        btn.getStyleClass().add("download-button");
        btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10; -fx-cursor: hand;");
        btn.setOnMouseClicked(e -> {
            if (onDownload != null) {
                onDownload.onDownload(id, name, btn);
            }
        });
        
        box.getChildren().addAll(lbl, sz, btn);

        return box;
    }
    
    /**
     * Interface callback cho tải xuống
     */
    public interface DownloadCallback {
        void onDownload(String fileId, String fileName, Label button);
    }
}
