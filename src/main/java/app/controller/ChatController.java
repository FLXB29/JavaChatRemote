package app.controller;

/*
 * File này đã được thay thế bằng ChatControllerRefactored trong package app.controller.chat
 * Do việc tái cấu trúc để cải thiện khả năng bảo trì và mở rộng
 * Xem: src/main/java/app/controller/chat/ChatControllerRefactored.java
 */

import app.LocalDateTimeAdapter;
import app.service.ChatService;
import app.service.UserService;
import app.util.DatabaseKeyManager;
import app.util.DatabaseEncryptionUtil;
import app.ServiceLocator;
import app.model.Conversation;
import app.model.Message;
import app.model.MessageDTO;
import app.model.User;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.ImagePattern;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import network.ClientConnection;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.paint.Color;
import com.gluonhq.emoji.util.TextUtils;
import com.gluonhq.emoji.Emoji;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.shape.Circle;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import app.service.FriendshipService;
import app.service.MessageReceiptService;
import app.service.NotificationService;
import java.util.concurrent.CompletableFuture;
import app.model.Friendship;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;


public class ChatController {

    /**
     * Data class cho cell đoạn chat gần đây
     */
    public static class RecentChatCellData {
        public final String chatName; // tên bạn bè hoặc nhóm
        public final String lastMessage;
        public final String time;
        public final String avatarPath;
        public final int unreadCount;
        public RecentChatCellData(String chatName, String lastMessage, String time, String avatarPath, int unreadCount) {
            this.chatName = chatName;
            this.lastMessage = lastMessage;
            this.time = time;
            this.avatarPath = avatarPath;
            this.unreadCount = unreadCount;
        }
    }
    
    // Toàn bộ nội dung còn lại đã được thay thế bởi ChatControllerRefactored
    
    // Cung cấp các hàm trống cho tính tương thích ngược
    public void setCurrentUser(String username) {
        // Chuyển hướng đến controller mới
        System.out.println("ChatController.setCurrentUser() đã được thay thế bởi ChatControllerRefactored");
    }
    
    public void bindUI(ChatService service) {
        // Stub cho tương thích ngược
        System.out.println("ChatController.bindUI() đã được thay thế bởi ChatControllerRefactored");
    }
}