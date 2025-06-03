package app.controller;

import app.LocalDateTimeAdapter;
import app.service.ChatService;
import app.service.UserService;
//import app.util.ConversationKeyManager;
import app.util.DatabaseEncryptionUtil;
//import app.util.DatabaseKeyManager;
import app.util.DatabaseKeyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
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


public class ChatController {

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private VBox messagesContainer;

    @FXML
    private TextField txtMessage, searchField;

    @FXML
    private Button btnAttachFile, btnEmoji, btnSend, btnSettings, btnBack,btnCreateGroup, btnLogout;

    @FXML
    private ListView<String> listGroups;

    @FXML
    private SplitPane rootSplit;
    
    @FXML
    private Circle userAvatarCircle;
    
    @FXML
    private ImageView userAvatarImage;
    
    @FXML
    private Label userInitialLabel;
    
    @FXML
    private Label currentUserLabel;

    @FXML
    private Label chatTitleLabel;

    // 1) Thuộc tính
    public String lastPmTarget;
    private boolean hasUnreadFriendRequests = false;


    // Username hiện tại (người dùng đang đăng nhập)
    private String currentUser;

    private String currentTarget = "Global"; // mặc định Global

    private final Map<String, Long> groupMap = new HashMap<>();

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

    // Kết nối client (để gửi/nhận gói tin)
    private ClientConnection clientConnection;

    // add field
    private final Map<Long, Boolean> joinedConv = new HashMap<>();
    private final Map<String, VBox> fileBubbleMap = new HashMap<>();
    private final Map<String, User> onlineUsers = new HashMap<>();

    @FXML
    private ListView<User> listSearchUsers;

    // Thay vì onlineUsers, dùng map bạn bè
    private final Map<String, User> friendMap = new HashMap<>();
    private final Map<String, Integer> unreadMap = new HashMap<>();

    @FXML
    private ListView<RecentChatCellData> listRecentChats;

    @FXML
    private ListView<Friendship> listFriendRequests;

    @FXML
    private TabPane tabPane;

    // Data class cho cell đoạn chat gần đây
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

    @FXML
    private void initialize() {
        // 1) Gọi service bind UI
        DatabaseKeyManager.initialize();
        ServiceLocator.chat().bindUI(this);
        this.clientConnection = ServiceLocator.chat().getClient();

        if (this.clientConnection == null) {
            System.out.println("ChatController: clientConnection == null trong initialize()!");
            return;
        }

        // ===== SETUP BASIC CALLBACKS =====
        setupBasicCallbacks();
        setupMessageHandlers(); // Add this new method call

        // ===== SETUP UI COMPONENTS =====
        setupUIComponents();

        // ===== SETUP CUSTOM CELL FACTORIES =====
        setupCellFactories();
        setupSearchUsersCellFactory();  // Add this line
        setupRecentChatsCellFactory();  // Add this if you want to refactor the recent chats cell factory too
        setupGroupListCellFactory(); // Add this line
        setupCreateGroupButton();    // Add this line

        // ===== SETUP EVENT LISTENERS =====
        setupEventListeners();
        setupRecentChatsClickHandler(); // Add this line
        setupFocusListener(); // Add this line



        // ===== INITIAL LOAD =====
        loadRecentChats();
        refreshRecentChats();
        refreshFriendRequests();
    }

    private void setupRecentChatsClickHandler() {
        listRecentChats.setOnMouseClicked(event -> {
            RecentChatCellData selected = listRecentChats.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            String chatName = selected.chatName;
            System.out.println("[DEBUG] Selected chat: " + chatName);

            if ("Global".equals(chatName)) {
                // Handle Global chat
                currentTarget = "Global";
                chatTitleLabel.setText("Global Chat");
                messagesContainer.getChildren().clear();

                // Request global chat history
                clientConnection.requestHistory(currentUser, "Global");

                // Cập nhật UI cho Global Chat
//                updateUIForGlobalChat();
            } else {
                // For other chats, check if it's a group or private
                boolean isGroup = false;
                for (Map.Entry<String, Long> entry : groupMap.entrySet()) {
                    if (entry.getKey().equals(chatName)) {
                        isGroup = true;
                        currentTarget = chatName;
                        chatTitleLabel.setText(chatName);
                        messagesContainer.getChildren().clear();

                        // Join group conversation
                        clientConnection.joinConv(entry.getValue());

                        // Xóa UI Global Chat
                        handleChatSelection(chatName);
                        break;
                    }
                }

                // If not a group, it's a private chat
                if (!isGroup) {
                    User targetUser = ServiceLocator.userService().getUser(chatName);
                    if (targetUser != null) {
                        openPrivateConversation(targetUser);

                        // Xóa UI Global Chat
                        handleChatSelection(chatName);

                        // Clear unread count
                        if (unreadMap.containsKey(chatName)) {
                            unreadMap.put(chatName, 0);
                            loadRecentChats(); // Update UI
                        }
                    }
                }
            }
        });
    }
    private void setupBasicCallbacks() {
        clientConnection.setOnTextReceived((from, content) -> {
            if (!"Global".equals(currentTarget)) return;
            Platform.runLater(() -> {
                if (from == null) return;
                boolean out = from.equals(getCurrentUser());
                displayMessage(from, content, out, LocalDateTime.now());
            });
        });

        clientConnection.setOnLoginSuccess(() -> {
            System.out.println("[DEBUG] Login thành công, kiểm tra friend requests...");

            // Tự động request pending friend requests khi login
            if (currentUser != null) {
                clientConnection.requestPendingFriendRequests(currentUser);
            }

            // Kiểm tra unread notifications
            checkUnreadNotifications();
        });

        clientConnection.setOnConvJoined(cid -> joinedConv.put(cid, true));

        clientConnection.setOnHistory((convId, json) -> {
            var msgList = parseJsonToMessageDTO(json);
            Platform.runLater(() -> {
                messagesContainer.getChildren().clear();
                for (var m : msgList) {
                    boolean out = m.getUser().equals(getCurrentUser());
                    displayMessage(m.getUser(), m.getContent(), out, m.getTime());
                }
            });
        });

        clientConnection.setOnConvList(json -> {
            List<Map<String, Object>> list = new Gson().fromJson(
                    json, new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType());

            Platform.runLater(() -> {
                listGroups.getItems().clear();
                groupMap.clear();
                for (Map<String, Object> c : list) {
                    String type = (String) c.get("type");
                    if (!"GROUP".equals(type)) continue;
                    String name = (String) c.get("name");
                    Long id = ((Number) c.get("id")).longValue();
                    listGroups.getItems().add(name);
                    groupMap.put(name, id);
                }
                listGroups.refresh();
            });
        });

        clientConnection.setOnGroupMsg((convId, from, content) -> {
            if (!groupMap.containsKey(currentTarget) || groupMap.get(currentTarget) != convId) return;
            boolean isOutgoing = from.equals(getCurrentUser());
            Platform.runLater(() -> displayMessage(from, content, isOutgoing, LocalDateTime.now()));
        });

        clientConnection.setOnPrivateMsgReceived((from, content) -> {
            boolean out = from.equals(getCurrentUser());
            String otherUser = out ? lastPmTarget : from;

            // Always process incoming messages to update chats list
            Platform.runLater(() -> {
                // If we're currently chatting with this person, display the message
                if ((out && currentTarget.equals(lastPmTarget)) ||
                        (!out && currentTarget.equals(from))) {

                    displayMessage(from, content, out, LocalDateTime.now());
                }
                // If we're not actively chatting with this person
                else if (!out) {
                    // 1. Increment unread count for this conversation
                    int count = unreadMap.getOrDefault(from, 0) + 1;
                    unreadMap.put(from, count);

                    // 2. Show notification for incoming message
                    showNotification("Tin nhắn mới", "Tin nhắn mới từ " + from);

                    // 3. Update recent chats list to show new message
                    loadRecentChats();
                }
            });
        });
        clientConnection.setOnPendingListReceived(json -> {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();
            Type type = new TypeToken<List<Friendship>>(){}.getType();
            List<Friendship> list = gson.fromJson(json, type);

            // Update friend requests list
            updateFriendRequests(list);
            loadRecentChats();
            refreshAfterFriendshipChange();
            // Update badge if có requests
            Platform.runLater(() -> {
                if (!list.isEmpty()) {
                    addNotificationBadgeToTab("Lời mời", list.size());
                    hasUnreadFriendRequests = true;

                    // Show notification nếu có requests mới
                    showWelcomeNotification(list.size());
                } else {
                    removeNotificationBadgeFromTab("Lời mời");
                    hasUnreadFriendRequests = false;
                }
            });
        });

        // NEW: Friend request received callback
        clientConnection.setOnFriendRequestReceived((fromUser) -> {
            Platform.runLater(() -> {
                showNotification("Lời mời kết bạn", fromUser + " đã gửi lời mời kết bạn cho bạn");
                if ("Lời mời".equals(tabPane.getSelectionModel().getSelectedItem().getText())) {
                    refreshFriendRequests();
                }
                addNotificationBadgeToTab("Lời mời");
            });
        });
    }
    private void setupSearchFieldListener() {
        /* === Lắng nghe khi người dùng nhập vào trường tìm kiếm === */
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isBlank()) {
                // Nếu rỗng, ẩn danh sách tìm kiếm và hiện danh sách chat gần đây
                Platform.runLater(() -> {
                    listRecentChats.setVisible(true);
                    listRecentChats.setManaged(true);
                    listSearchUsers.setVisible(false);
                    listSearchUsers.setManaged(false);
                    
                    // Ẩn nút back khi không tìm kiếm
                    btnBack.setVisible(false);
                    btnBack.setManaged(false);
                });
                return;
            }

            // Tìm kiếm người dùng theo newText
            String query = newText.trim();
            if (query.length() < 2) return; // Cần ít nhất 2 ký tự để tìm kiếm

            // Thực hiện tìm kiếm
            CompletableFuture.runAsync(() -> {
                try {
                    // Tìm kiếm danh bạ
                    List<User> results = ServiceLocator.userService().searchUsers(query);
                    
                    // Hiển thị kết quả tìm kiếm
                    if (!results.isEmpty()) {
                        Platform.runLater(() -> {
                            listSearchUsers.getItems().setAll(results);
                            listSearchUsers.setVisible(true);
                            listSearchUsers.setManaged(true);
                            listRecentChats.setVisible(false);
                            listRecentChats.setManaged(false);
                            
                            // Hiển thị nút back khi đang tìm kiếm
                            btnBack.setVisible(true);
                            btnBack.setManaged(true);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Lỗi tìm kiếm người dùng", e);
                }
            });
        });

        searchField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && (searchField.getText() == null || searchField.getText().isBlank())) {
                // Switch back to recent chats when focus is lost and search is empty
                Platform.runLater(() -> {
                    listRecentChats.setVisible(true);
                    listRecentChats.setManaged(true);
                    listSearchUsers.setVisible(false);
                    listSearchUsers.setManaged(false);
                    
                    // Ẩn nút back khi không còn tìm kiếm
                    btnBack.setVisible(false);
                    btnBack.setManaged(false);
                });
            }
        });
    }

    private void setupSearchUsersCellFactory() {
        listSearchUsers.setCellFactory(lv -> new ListCell<User>() {
            private final Circle avatarCircle = new Circle(18);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label statusLabel = new Label();
            private final Button actionBtn = new Button();
            private final VBox contentBox = new VBox(3);
            private final HBox mainBox = new HBox(12);

            {
                // Setup styling
                nameLabel.getStyleClass().add("search-user-name");
                statusLabel.getStyleClass().add("search-user-status");
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

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
                    if (user != null) {
                        handleUserAction(user);
                    }
                });
            }

            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);

                if (empty || user == null) {
                    setGraphic(null);
                    return;
                }

                // Set user name with fallback
                String displayName = user.getFullName();
                if (displayName == null || displayName.isBlank()) {
                    displayName = user.getUsername();
                }
                nameLabel.setText(displayName);

                // Update status and button based on friendship status
                updateUserStatusAndButton(user);

                // Update avatar
                updateUserAvatar(user);

                // Set the cell content
                setGraphic(mainBox);
            }

            private void updateUserStatusAndButton(User user) {
                // Handle current user
                if (user.getUsername().equals(currentUser)) {
                    statusLabel.setText("Bạn");
                    statusLabel.setStyle("-fx-text-fill: #666;");
                    actionBtn.setVisible(false);
                    return;
                }

                // Get friendship status
                User currentUserObj = ServiceLocator.userService().getUser(currentUser);
                Friendship.Status status = ServiceLocator.friendship()
                        .getFriendshipStatus(currentUserObj, user);

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
                } else if (status == Friendship.Status.BLOCKED) {
                    // Blocked
                    statusLabel.setText("Đã chặn");
                    statusLabel.setStyle("-fx-text-fill: #f44336;");
                    actionBtn.setText("Bỏ chặn");
                    actionBtn.getStyleClass().setAll("btn", "btn-danger");
                    actionBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
                    actionBtn.setDisable(false);
                }
            }

            private void updateUserAvatar(User user) {
                if (!user.isUseDefaultAvatar() && user.getAvatarPath() != null) {
                    File f = new File(user.getAvatarPath());
                    if (f.exists()) {
                        try {
                            Image img = new Image(f.toURI().toString(), 36, 36, true, true);
                            avatarCircle.setFill(new ImagePattern(img));
                            initialLabel.setVisible(false);
                            return;
                        } catch (Exception e) {
                            // Fall back to default avatar on error
                            System.out.println("[ERROR] Failed to load avatar: " + e.getMessage());
                        }
                    }
                }

                // Default avatar with first letter and color
                int colorIndex = Math.abs(user.getUsername().hashCode() % AVATAR_COLORS.length);
                avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
                initialLabel.setText(user.getUsername().substring(0, 1).toUpperCase());
                initialLabel.setVisible(true);
            }
        });
    }
    private void setupMessageHandlers() {
        // Handle private messages
        clientConnection.setOnPrivateMsgReceived((from, content) -> {
            boolean out = from.equals(getCurrentUser());
            String targetUser = out ? lastPmTarget : from;

            // Always process new messages properly
            if (!out) {
                handleNewMessage(from, content);
            }

            // If we're in the current conversation, display the message
            Platform.runLater(() -> {
                if ((out && currentTarget.equals(lastPmTarget)) ||
                        (!out && currentTarget.equals(from))) {
                    displayMessage(from, content, out, LocalDateTime.now());

                    // Clear unread when displaying
                    if (!out) {
                        unreadMap.put(from, 0);
                        refreshRecentChats();
                    }
                }

                // Always refresh recent chats after any private message
                refreshRecentChats();
            });
        });

        // Handle group messages similarly
        clientConnection.setOnGroupMsg((convId, from, content) -> {
            // Find group name from ID
            String groupName = null;
            for (Map.Entry<String, Long> entry : groupMap.entrySet()) {
                if (entry.getValue() == convId) {
                    groupName = entry.getKey();
                    break;
                }
            }

            final String finalGroupName = groupName;
            boolean isCurrentGroup = groupMap.containsKey(currentTarget) &&
                    groupMap.get(currentTarget) == convId;
            boolean isOutgoing = from.equals(getCurrentUser());

            // Process new message from others
            if (!isOutgoing && finalGroupName != null) {
                handleNewGroupMessage(finalGroupName, from, content);
            }

            Platform.runLater(() -> {
                // Display if in current group
                if (isCurrentGroup) {
                    displayMessage(from, content, isOutgoing, LocalDateTime.now());

                    // Clear unread count if showing the message
                    if (!isOutgoing && finalGroupName != null) {
                        unreadMap.put(finalGroupName, 0);
                    }
                }

                // Always refresh recent chats
                refreshRecentChats();
            });
        });
    }
    private void setupUIComponents() {
        // Message input
        txtMessage.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                onSend();
            }
        });

        // Messages container
        messagesContainer.setFillWidth(true);
        scrollPane.setFitToWidth(true);
        scrollPane.vvalueProperty().bind(messagesContainer.heightProperty());
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3a3a3a;");
        
        // Ẩn nút back mặc định
        btnBack.setVisible(false);
        btnBack.setManaged(false);

        // Avatar callback
        clientConnection.setOnAvatarUpdated((username, avatarData) -> {
            Platform.runLater(() -> {
                try {
                    User user = ServiceLocator.userService().getUser(username);
                    if (user != null) {
                        onlineUsers.put(username, user);
                    }
                    if (username.equals(currentUser)) {
                        updateUserAvatar();
                    }
                    listRecentChats.refresh();
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Không thể cập nhật avatar", e);
                }
            });
        });

        // Window close handler
        rootSplit.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((o, oldWin, newWin) -> {
                if (newWin != null) {
                    newWin.setOnCloseRequest(ev -> ServiceLocator.chat().shutdown());
                }
            });
        });
    }    private void setupClientCallbacks() {
        clientConnection.setOnLoginSuccess(() -> {
            System.out.println("[DEBUG] Login thành công, kiểm tra friend requests...");

            // Tự động request pending friend requests khi login
            if (currentUser != null) {
                clientConnection.requestPendingFriendRequests(currentUser);
            }

            // Kiểm tra unread notifications
            checkUnreadNotifications();

            // Load recent chats after login
            refreshRecentChats();
        });
    }

    private void setupCellFactories() {
        // ===== FRIEND REQUESTS CELL FACTORY =====
        listFriendRequests.setCellFactory(lv -> new ListCell<Friendship>() {
            private final Circle avatarCircle = new Circle(20);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label timeLabel = new Label();
            private final Button acceptBtn = new Button("Chấp nhận");
            private final Button rejectBtn = new Button("Từ chối");
            private final HBox buttonBox = new HBox(8, acceptBtn, rejectBtn);
            private final VBox contentBox = new VBox(4);
            private final HBox mainBox = new HBox(12);

            {
                // Styling
                acceptBtn.getStyleClass().addAll("btn", "btn-success");
                rejectBtn.getStyleClass().addAll("btn", "btn-secondary");
                nameLabel.getStyleClass().add("friend-request-name");
                timeLabel.getStyleClass().add("friend-request-time");

                // Event handlers
                acceptBtn.setOnAction(evt -> {
                    evt.consume();
                    handleAcceptFriend(getItem());
                });

                rejectBtn.setOnAction(evt -> {
                    evt.consume();
                    handleRejectFriend(getItem());
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

                User sender = friendship.getUser1();
                nameLabel.setText(sender.getFullName() != null ?
                        sender.getFullName() : sender.getUsername());

                timeLabel.setText(friendship.getCreatedAt()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

                updateFriendRequestAvatar(sender);
                setGraphic(mainBox);
            }

            private void updateFriendRequestAvatar(User user) {
                if (!user.isUseDefaultAvatar() && user.getAvatarPath() != null) {
                    File f = new File(user.getAvatarPath());
                    if (f.exists()) {
                        avatarCircle.setFill(new ImagePattern(new Image(f.toURI().toString())));
                        initialLabel.setVisible(false);
                        return;
                    }
                }

                int colorIndex = Math.abs(user.getUsername().hashCode() % AVATAR_COLORS.length);
                avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
                initialLabel.setText(user.getUsername().substring(0, 1).toUpperCase());
                initialLabel.setVisible(true);
            }
        });

        // ===== SEARCH USERS CELL FACTORY =====
        listSearchUsers.setCellFactory(lv -> new ListCell<User>() {
            private final Circle avatarCircle = new Circle(18);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label statusLabel = new Label();
            private final Button actionBtn = new Button();
            private final VBox contentBox = new VBox(3, nameLabel, statusLabel);
            private final HBox mainBox = new HBox(12);

            {
                nameLabel.getStyleClass().add("search-user-name");
                statusLabel.getStyleClass().add("search-user-status");

                mainBox.getChildren().addAll(
                        new StackPane(avatarCircle, initialLabel),
                        contentBox
                );

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                mainBox.getChildren().addAll(spacer, actionBtn);

                mainBox.setAlignment(Pos.CENTER_LEFT);
                mainBox.setPadding(new Insets(8));

                actionBtn.setOnAction(evt -> {
                    evt.consume();
                    handleUserAction(getItem());
                });
            }

            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(user.getFullName() != null ?
                        user.getFullName() : user.getUsername());

                updateUserStatus(user);
                updateSearchUserAvatar(user);
                setGraphic(mainBox);
            }

            private void updateUserStatus(User user) {
                if (user.getUsername().equals(currentUser)) {
                    statusLabel.setText("Bạn");
                    statusLabel.setStyle("-fx-text-fill: #666;");
                    actionBtn.setVisible(false);
                    return;
                }

                User currentUserObj = ServiceLocator.userService().getUser(currentUser);
                Friendship.Status status = ServiceLocator.friendship()
                        .getFriendshipStatus(currentUserObj, user);

                actionBtn.setVisible(true);

                if (status == null) {
                    statusLabel.setText("Chưa kết bạn");
                    statusLabel.setStyle("-fx-text-fill: #666;");
                    actionBtn.setText("Kết bạn");
                    actionBtn.getStyleClass().setAll("btn", "btn-primary");
                    actionBtn.setDisable(false);
                } else if (status == Friendship.Status.PENDING) {
                    statusLabel.setText("Đã gửi lời mời");
                    statusLabel.setStyle("-fx-text-fill: #ff9500;");
                    actionBtn.setText("Đã gửi");
                    actionBtn.getStyleClass().setAll("btn", "btn-secondary");
                    actionBtn.setDisable(true);
                } else if (status == Friendship.Status.ACCEPTED) {
                    statusLabel.setText("Bạn bè");
                    statusLabel.setStyle("-fx-text-fill: #4CAF50;");
                    actionBtn.setText("Nhắn tin");
                    actionBtn.getStyleClass().setAll("btn", "btn-success");
                    actionBtn.setDisable(false);
                }
            }

            private void updateSearchUserAvatar(User user) {
                if (!user.isUseDefaultAvatar() && user.getAvatarPath() != null) {
                    File f = new File(user.getAvatarPath());
                    if (f.exists()) {
                        avatarCircle.setFill(new ImagePattern(new Image(f.toURI().toString())));
                        initialLabel.setVisible(false);
                        return;
                    }
                }

                int colorIndex = Math.abs(user.getUsername().hashCode() % AVATAR_COLORS.length);
                avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
                initialLabel.setText(user.getUsername().substring(0, 1).toUpperCase());
                initialLabel.setVisible(true);
            }
        });

        // ===== RECENT CHATS CELL FACTORY =====
        listRecentChats.setCellFactory(lv -> new ListCell<RecentChatCellData>() {
            private final Circle avatarCircle = new Circle(22);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label messageLabel = new Label();
            private final Label timeLabel = new Label();
            private final Label unreadBadge = new Label();
            private final VBox contentBox = new VBox(3);
            private final HBox topBox = new HBox();
            private final HBox mainBox = new HBox(12);

            {
                nameLabel.getStyleClass().add("chat-name");
                messageLabel.getStyleClass().add("chat-message");
                timeLabel.getStyleClass().add("chat-time");
                unreadBadge.getStyleClass().add("unread-badge");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                topBox.getChildren().addAll(nameLabel, spacer, timeLabel);

                contentBox.getChildren().addAll(topBox, messageLabel);
                mainBox.getChildren().addAll(
                        new StackPane(avatarCircle, initialLabel),
                        contentBox,
                        unreadBadge
                );
                mainBox.setPadding(new Insets(10));
                mainBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(RecentChatCellData chat, boolean empty) {
                super.updateItem(chat, empty);
                if (empty || chat == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(chat.chatName);
                messageLabel.setText(chat.lastMessage.length() > 50 ?
                        chat.lastMessage.substring(0, 50) + "..." : chat.lastMessage);
                timeLabel.setText(chat.time);

                // Check unread count from our map
                int unreadCount = unreadMap.getOrDefault(chat.chatName, 0);
                if (unreadCount > 0) {
                    unreadBadge.setText(String.valueOf(unreadCount));
                    unreadBadge.setVisible(true);
                } else {
                    unreadBadge.setVisible(false);
                }

                // Use our improved avatar handling method
                updateRecentChatAvatar(chat, avatarCircle, initialLabel);
                setGraphic(mainBox);
            }
        });
    }

    private void setupRecentChatsCellFactory() {
        listRecentChats.setCellFactory(lv -> new ListCell<RecentChatCellData>() {
            private final Circle avatarCircle = new Circle(24);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label messageLabel = new Label();
            private final Label timeLabel = new Label();
            private final Label unreadBadge = new Label();
            private final HBox mainBox = new HBox(12);
            private final VBox contentBox = new VBox(4);
            private final HBox topRow = new HBox();

            {
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
                timeLabel.setText(chat.time);

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
                updateRecentChatAvatar(chat);

                // Set cell content
                setGraphic(mainBox);
            }

            private void updateRecentChatAvatar(RecentChatCellData chat) {
                // Special case for Global chat
                if ("Global".equals(chat.chatName)) {
                    avatarCircle.setFill(Color.rgb(76, 175, 80)); // Green color
                    initialLabel.setText("G");
                    initialLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                    initialLabel.setVisible(true);
                    return;
                }

                // Try to use custom avatar if available
                if (chat.avatarPath != null && !chat.avatarPath.isEmpty()) {
                    File f = new File(chat.avatarPath);
                    if (f.exists()) {
                        try {
                            Image img = new Image(f.toURI().toString(), 48, 48, true, true);
                            avatarCircle.setFill(new ImagePattern(img));
                            initialLabel.setVisible(false);
                            return;
                        } catch (Exception e) {
                            // Fall back to default on error
                            System.out.println("[ERROR] Failed to load avatar: " + e.getMessage());
                        }
                    }
                }

                // Default avatar based on first letter
                int colorIndex = Math.abs(chat.chatName.hashCode() % AVATAR_COLORS.length);
                avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
                initialLabel.setText(chat.chatName.substring(0, 1).toUpperCase());
                initialLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                initialLabel.setVisible(true);
            }
        });
    }

    private void setupEventListeners() {
        // Groups selection
        listGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) return;
                    currentTarget = newVal;
                    chatTitleLabel.setText(newVal);
                    long cid = groupMap.get(newVal);
                    clientConnection.joinConv(cid);
                    listGroups.refresh();
                });

        // Search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSearching = newVal != null && !newVal.isBlank();
            listRecentChats.setVisible(!isSearching);
            listRecentChats.setManaged(!isSearching);
            listSearchUsers.setVisible(isSearching);
            listSearchUsers.setManaged(isSearching);

            if (isSearching) {
                CompletableFuture.runAsync(() -> {
                    List<User> found = ServiceLocator.userService().searchUsers(newVal);
                    Platform.runLater(() -> listSearchUsers.getItems().setAll(found));
                });
            }
        });

        // Search field focus
        searchField.focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow && searchField.getText().isBlank()) {
                listRecentChats.setVisible(true);
                listRecentChats.setManaged(true);
                listSearchUsers.setVisible(false);
                listSearchUsers.setManaged(false);
            }
        });

        // Tab selection
        tabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {
                    if ("Lời mời".equals(newTab.getText()) ||
                            (newTab.getText().startsWith("Lời mời") && newTab.getText().contains("("))) {

                        // Request fresh data
                        clientConnection.requestPendingFriendRequests(currentUser);

                        // Remove badge khi vào tab
                        removeNotificationBadgeFromTab("Lời mời");
                        hasUnreadFriendRequests = false;
                    }
                });
        setupSearchFieldListener();

        // Recent chats click
        listRecentChats.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double click
                RecentChatCellData selected = listRecentChats.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openChatWithUser(selected.chatName);
                }
            }
        });
    }

// ===== NEW HANDLER METHODS =====

    private void handleUserAction(User target) {
        if (target == null || target.getUsername().equals(currentUser)) return;

        User currentUserObj = ServiceLocator.userService().getUser(currentUser);
        Friendship.Status status = ServiceLocator.friendship()
                .getFriendshipStatus(currentUserObj, target);

        if (status == null) {
            sendFriendRequest(target);
        } else if (status == Friendship.Status.ACCEPTED) {
            openPrivateConversation(target);
            tabPane.getSelectionModel().select(0); // Switch to friends tab
        }
    }

    private void sendFriendRequest(User target) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Gửi lời mời kết bạn tới " +
                        (target.getFullName() != null ? target.getFullName() : target.getUsername()) + "?",
                ButtonType.OK, ButtonType.CANCEL);

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                clientConnection.sendFriendRequest(currentUser, target.getUsername());

                Platform.runLater(() -> {
                    listSearchUsers.refresh();
                    showSuccess("Đã gửi lời mời kết bạn!");
                });

            } catch (Exception e) {
                showError("Không thể gửi lời mời kết bạn", e);
            }
        }
    }

    private void handleAcceptFriend(Friendship friendship) {
        if (friendship == null) return;

        try {
            String fromUser = friendship.getUser1().getUsername();
            clientConnection.acceptFriendRequest(fromUser, currentUser);

            Platform.runLater(() -> {
                // Refresh friend requests
                refreshFriendRequests();

                // Refresh recent chats to show the new friend
                refreshRecentChats();

                // Update badge count
                updateFriendRequestBadge();

                showSuccess("Đã chấp nhận lời mời kết bạn từ " + fromUser + "!");
            });

        } catch (Exception e) {
            showError("Không thể chấp nhận lời mời", e);
        }
    }
    private void handleRejectFriend(Friendship friendship) {
        if (friendship == null) return;

        String fromUsername = friendship.getUser1().getUsername();
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Từ chối lời mời kết bạn từ " + fromUsername + "?",
                ButtonType.OK, ButtonType.CANCEL);

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                clientConnection.rejectFriendRequest(fromUsername, currentUser);

                Platform.runLater(() -> {
                    // Refresh friend requests
                    refreshFriendRequests();

                    // Update badge count
                    updateFriendRequestBadge();

                    showSuccess("Đã từ chối lời mời kết bạn từ " + fromUsername + ".");
                });

            } catch (Exception e) {
                showError("Không thể từ chối lời mời", e);
            }
        }
    }


    private void openChatWithUser(String username) {
        // Check if user is friend first
        User currentUserObj = ServiceLocator.userService().getUser(currentUser);
        User targetUser = ServiceLocator.userService().getUser(username);

        if (targetUser == null) return;

        Friendship.Status status = ServiceLocator.friendship()
                .getFriendshipStatus(currentUserObj, targetUser);

        if (status == Friendship.Status.ACCEPTED) {
            // Clear unread count when opening the chat
            unreadMap.put(username, 0);
            loadRecentChats(); // Refresh to update unread badges

            openPrivateConversation(targetUser);
        } else {
            showWarn("Bạn cần kết bạn với " + username + " để có thể trò chuyện.");
        }
    }
// ===== UTILITY METHODS =====

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thành công");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showNotification(String title, String message) {
        Alert notification = new Alert(Alert.AlertType.INFORMATION);
        notification.setTitle(title);
        notification.setHeaderText(null);
        notification.setContentText(message);

        // Auto close after 3 seconds
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> notification.close()));
        timeline.play();

        notification.show();
    }

    private void addNotificationBadgeToTab(String tabText) {
        // Tự động đếm số lượng friend requests hiện tại
        int count = listFriendRequests.getItems().size();
        if (count > 0) {
            addNotificationBadgeToTab(tabText, count);
        }
    }



    private void addNotificationBadgeToTab(String tabText, int count) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().equals(tabText) || tab.getText().startsWith(tabText)) {
                String badgeText = count > 9 ? "(9+)" : "(" + count + ")";
                tab.setText(tabText + " " + badgeText);

                // Style tab để highlight
                tab.getStyleClass().add("tab-notification");
                break;
            }
        }
    }
    private void checkUnreadNotifications() {
        CompletableFuture.runAsync(() -> {
            try {
                // Kiểm tra pending friend requests
                User me = ServiceLocator.userService().getUser(currentUser);
                List<Friendship> pending = ServiceLocator.friendship().getPendingRequests(me);

                Platform.runLater(() -> {
                    if (!pending.isEmpty()) {
                        addNotificationBadgeToTab("Lời mời", pending.size());
                        hasUnreadFriendRequests = true;

                        // Hiển thị welcome notification
                        showWelcomeNotification(pending.size());
                    }
                });

            } catch (Exception e) {
                System.out.println("[ERROR] Không thể kiểm tra notifications: " + e.getMessage());
            }
        });
    }

    /**
     * Hiển thị notification chào mừng với số lượng friend requests
     */
    private void showWelcomeNotification(int count) {
        if (count <= 0) return;

        String message = count == 1
                ? "Bạn có 1 lời mời kết bạn mới!"
                : "Bạn có " + count + " lời mời kết bạn mới!";

        // Tạo notification với action button
        Alert notification = new Alert(Alert.AlertType.INFORMATION);
        notification.setTitle("Lời mời kết bạn");
        notification.setHeaderText("Chào mừng quay lại!");
        notification.setContentText(message);

        // Custom buttons
        ButtonType viewButton = new ButtonType("Xem ngay", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButton = new ButtonType("Để sau", ButtonBar.ButtonData.CANCEL_CLOSE);
        notification.getButtonTypes().setAll(viewButton, laterButton);

        // Show và xử lý response
        notification.showAndWait().ifPresent(response -> {
            if (response == viewButton) {
                // Chuyển sang tab Lời mời
                tabPane.getSelectionModel().select(1); // Index 1 = "Lời mời"
            }
        });
    }

    private void removeNotificationBadgeFromTab(String tabText) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().startsWith(tabText)) {
                tab.setText(tabText);
                tab.getStyleClass().remove("tab-notification");
                break;
            }
        }
    }
//    private void loadRecentChats() {
//        // Show loading indicator
//        Platform.runLater(() -> {
//            if (listRecentChats.getItems().isEmpty()) {
//                Label loadingLabel = new Label("Đang tải...");
//                loadingLabel.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
//                listRecentChats.setPlaceholder(loadingLabel);
//            }
//        });
//
//        CompletableFuture.runAsync(() -> {
//            try {
//                // Get current user
//                User currentUserObj = ServiceLocator.userService().getCurrentUser();
//                if (currentUserObj == null) {
//                    System.out.println("[ERROR] Cannot load recent chats: current user is null");
//                    return;
//                }
//
//                // Get recent chats
//                List<RecentChatCellData> data = ServiceLocator.messageService()
//                        .getRecentChats(currentUserObj);
//
//                // Apply unread counts from our map
//                List<RecentChatCellData> updatedData = data.stream()
//                        .map(chat -> {
//                            int unreadCount = unreadMap.getOrDefault(chat.chatName, 0);
//
//                            // Hiển thị indicator mã hóa nếu tin nhắn là "[Tin nhắn mã hóa]"
//                            String displayMessage = chat.lastMessage;
//                            if ("[Tin nhắn mã hóa]".equals(chat.lastMessage)) {
//                                displayMessage = "🔒 " + chat.lastMessage;
//                            }
//
//                            return new RecentChatCellData(
//                                    chat.chatName,
//                                    displayMessage,
//                                    chat.time,
//                                    chat.avatarPath,
//                                    unreadCount
//                            );
//                        })
//                        .collect(Collectors.toList());
//
//                Platform.runLater(() -> {
//                    listRecentChats.setPlaceholder(null);
//                    listRecentChats.getItems().setAll(updatedData);
//                    listRecentChats.refresh();
//                });
//            } catch (Exception e) {
//                System.out.println("[ERROR] Failed to load recent chats: " + e.getMessage());
//                e.printStackTrace();
//
//                Platform.runLater(() -> {
//                    Label errorLabel = new Label("Không thể tải danh sách chat");
//                    errorLabel.setStyle("-fx-text-fill: #ff4444;");
//                    listRecentChats.setPlaceholder(errorLabel);
//                });
//            }
//        });
//    }

    private void loadRecentChats() {
        // Show loading indicator
        Platform.runLater(() -> {
            if (listRecentChats.getItems().isEmpty()) {
                Label loadingLabel = new Label("Đang tải...");
                loadingLabel.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
                listRecentChats.setPlaceholder(loadingLabel);
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                // Get current user
                User currentUserObj = ServiceLocator.userService().getCurrentUser();
                if (currentUserObj == null) {
                    System.out.println("[ERROR] Cannot load recent chats: current user is null");
                    return;
                }

                // Get recent chats
                List<RecentChatCellData> data = ServiceLocator.messageService()
                        .getRecentChats(currentUserObj);

                // Apply unread counts from our map
                List<RecentChatCellData> updatedData = data.stream()
                        .map(chat -> {
                            int unreadCount = unreadMap.getOrDefault(chat.chatName, 0);
                            return new RecentChatCellData(
                                    chat.chatName,
                                    chat.lastMessage,
                                    chat.time,
                                    chat.avatarPath,
                                    unreadCount
                            );
                        })
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    listRecentChats.setPlaceholder(null);
                    listRecentChats.getItems().setAll(updatedData);
                    listRecentChats.refresh();
                });
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to load recent chats: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    Label errorLabel = new Label("Không thể tải danh sách chat");
                    errorLabel.setStyle("-fx-text-fill: #ff4444;");
                    listRecentChats.setPlaceholder(errorLabel);
                });
            }
        });
    }
    private void updateFriendRequestBadge() {
        int count = listFriendRequests.getItems().size();
        if (count > 0) {
            addNotificationBadgeToTab("Lời mời", count);
            hasUnreadFriendRequests = true;
        } else {
            removeNotificationBadgeFromTab("Lời mời");
            hasUnreadFriendRequests = false;
        }
    }

    private void refreshFriendRequests() {
        CompletableFuture.runAsync(() -> {
            User me = ServiceLocator.userService().getUser(currentUser);
            List<Friendship> pending = ServiceLocator.friendship()
                    .getPendingRequests(me);
            Platform.runLater(() ->
                    listFriendRequests.getItems().setAll(pending));
        });
    }

    /* Dùng từ clientConnection */
    public void updateFriendRequests(List<Friendship> list) {
        Platform.runLater(() ->
                listFriendRequests.getItems().setAll(list));
    }



    public void setCurrentUser(String username) {
        this.currentUser = username;
        currentUserLabel.setText(username);

        // Hiển thị avatar người dùng
        updateUserAvatar();

        // Load data cũ nếu có
        Conversation conv = ServiceLocator.chat().getConversation();
        if (conv != null) {
            List<Message> oldMessages = ServiceLocator.messageService()
                    .getMessagesByConversation(conv.getId());
            for (Message m : oldMessages) {
                boolean isOutgoing = m.getSender().getUsername().equals(username);
                displayMessage(
                        m.getSender().getUsername(),
                        m.getContent(),
                        isOutgoing,
                        m.getCreatedAt()
                );
            }
        }

        // Auto-check friend requests khi set user
        Platform.runLater(() -> {
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                checkUnreadNotifications();
            }));
            timeline.play();
        });
    }
    public String getCurrentUser() {
        return currentUser;
    }

    @FXML
    private void onCreateGroup() {
        /* BƯỚC 1: nhập tên nhóm */
        TextInputDialog nameDlg = new TextInputDialog();
        nameDlg.setHeaderText("Tên nhóm:");
        nameDlg.setTitle("Tạo nhóm mới");
        String gName = nameDlg.showAndWait().orElse(null);
        if (gName == null || gName.isBlank()) return;

        /* BƯỚC 2: chọn thành viên */
        ListView<CheckBox> lv = new ListView<>();

        // Improve UI for member selection
        VBox content = new VBox(10);
        content.setPadding(new Insets(20, 10, 10, 10));

        Label titleLabel = new Label("Chọn thành viên nhóm:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        content.getChildren().add(titleLabel);

        // Create search field for filtering
        TextField searchBox = new TextField();
        searchBox.setPromptText("Tìm kiếm bạn bè...");
        searchBox.setPrefWidth(300);
        content.getChildren().add(searchBox);

        // Get friend list
        List<String> friendNames = new ArrayList<>();
        for (RecentChatCellData chat : listRecentChats.getItems()) {
            String u = chat.chatName;
            if (!u.equals("Global") && !u.equals(getCurrentUser())) {
                friendNames.add(u);
            }
        }

        // Add checkboxes for each friend
        for (String name : friendNames) {
            CheckBox cb = new CheckBox(name);
            cb.setPadding(new Insets(5, 0, 5, 0));
            lv.getItems().add(cb);
        }

        // Filter list based on search text
        searchBox.textProperty().addListener((obs, old, text) -> {
            for (int i = 0; i < lv.getItems().size(); i++) {
                CheckBox cb = lv.getItems().get(i);
                Node row = lv.lookup(".list-cell:nth-child(" + (i+1) + ")");

                if (text == null || text.isEmpty() || cb.getText().toLowerCase().contains(text.toLowerCase())) {
                    if (row != null) row.setVisible(true);
                    if (row != null) row.setManaged(true);
                } else {
                    if (row != null) row.setVisible(false);
                    if (row != null) row.setManaged(false);
                }
            }
        });

        content.getChildren().add(lv);

        // Create dialog
        Dialog<List<String>> dlg = new Dialog<>();
        dlg.setTitle("Chọn thành viên nhóm");
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(350);
        dlg.getDialogPane().setPrefHeight(450);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                return lv.getItems().stream()
                        .filter(CheckBox::isSelected)
                        .map(CheckBox::getText)
                        .collect(Collectors.toList());
            }
            return null;
        });

        List<String> members = dlg.showAndWait().orElse(null);
        if (members == null || members.isEmpty()) {
            showWarn("Vui lòng chọn ít nhất một thành viên để tạo nhóm.");
            return;
        }

        /* BƯỚC 3: gửi packet CREATE_GROUP */
        clientConnection.createGroup(gName, members);

        // Show loading toast
        showToast("Đang tạo nhóm...");

        // Wait a bit then refresh UI
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    // Switch to Groups tab
                    tabPane.getSelectionModel().select(2); // Index for Groups tab

                    // Refresh the group list
                    if (clientConnection != null) {
                        clientConnection.requestUserList();
                    }

                    // Show success message
                    showToast("Đã tạo nhóm " + gName + " thành công!");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @FXML
    private void onSend() {
        String content = txtMessage.getText().trim();
        if (content.isEmpty()) return;

        try {
            // Gửi tin nhắn - server sẽ tự động mã hóa trước khi lưu
            if ("Global".equals(currentTarget)) {
                clientConnection.sendText(content);
            } else if (groupMap.containsKey(currentTarget)) {
                long gid = groupMap.get(currentTarget);
                clientConnection.sendGroup(gid, content);
                lastPmTarget = currentTarget;
            } else {
                clientConnection.sendPrivate(currentTarget, content);
                lastPmTarget = currentTarget;
            }

            txtMessage.clear();
            refreshRecentChats();
        } catch (Exception e) {
            showError("Không thể gửi tin nhắn", e);
        }
    }

    @FXML
    private void onAttachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Tất cả", "*.*")
        );
        File file = fileChooser.showOpenDialog(btnAttachFile.getScene().getWindow());
        if (file != null) {
            // Lấy conversation ID dựa vào target hiện tại
            long conversationId;
            if (groupMap.containsKey(currentTarget)) {
                conversationId = groupMap.get(currentTarget);
            } else {
                Conversation conv = ServiceLocator.chat().getConversation();
                conversationId = conv != null ? conv.getId() : 0L;
            }

            // Hiển thị thông báo đang gửi file
            Platform.runLater(() -> {
                Label loadingLabel = new Label("Đang gửi file...");
                loadingLabel.setStyle("-fx-text-fill:#999999; -fx-font-style:italic;");
                messagesContainer.getChildren().add(loadingLabel);
            });

            // Gửi file với conversationId
            try {
                // Gửi file
                ServiceLocator.chat().sendFile(conversationId, file.getAbsolutePath());

                // Xóa thông báo đang gửi
                Platform.runLater(() -> {
                    messagesContainer.getChildren().remove(messagesContainer.getChildren().size() - 1);

                    // Refresh recent chats after file is sent
                    refreshRecentChats();
                });
            } catch (Exception e) {
                e.printStackTrace();
                // Xóa thông báo đang gửi
                Platform.runLater(() -> {
                    messagesContainer.getChildren().remove(messagesContainer.getChildren().size() - 1);
                });
                showError("Lỗi gửi file", e);
            }
        }
    }

    @FXML
    private void onChooseEmoji() {
        Stage emojiStage = new Stage();
        emojiStage.initOwner(btnEmoji.getScene().getWindow());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // 1) Mảng ký tự Unicode để chèn vào TextField
        String[] emojis = { "😊", "😂",  "👍", "🎉", "😎", "😭", "😡",
                "🍀", "🔥", "🤔", "😴" };

        // 2) Mảng iconLiteral để hiển thị nút
        String[] iconLiterals = {
                "far-smile",        // 😊
                "far-laugh-beam",   // 😂
                "fas-thumbs-up",    // 👍
                "fas-smile-beam",   // 🎉
                "fas-smile-wink",   // 😎
                "far-sad-tear",     // 😭
                "fas-angry",        // 😡
                "fas-seedling",     // 🍀
                "fas-fire",         // 🔥
                "far-meh",          // 🤔
                "fas-bed"           // 😴
        };

        int cols = 4;
        for (int i = 0; i < iconLiterals.length; i++) {
            FontIcon icon = new FontIcon(iconLiterals[i]);
            icon.setIconSize(22);
            icon.setIconColor(Color.web("#ffaa00"));

            Button b = new Button();
            b.setGraphic(icon);

            int finalI = i;
            b.setOnAction(e -> {
                txtMessage.appendText(emojis[finalI]); // giờ biến emojis đã tồn tại
                emojiStage.close();
            });

            grid.add(b, i % cols, i / cols);
        }

        Scene scene = new Scene(grid, 200, 150);
        emojiStage.setTitle("Chọn Emoji");
        emojiStage.setScene(scene);
        emojiStage.show();
    }

    private Node buildMsgNode(String content, boolean isOutgoing) {
        TextFlow flow = buildEmojiTextFlow(content);
        flow.setMaxWidth(600);
        flow.setLineSpacing(2);

        // Đặt kích thước font và icon lớn hơn
        flow.setStyle("-fx-font-size: 16px;");  // Tăng kích thước font chữ

        StackPane bubble = new StackPane(flow);
        bubble.setPadding(new Insets(8));
        bubble.setStyle(isOutgoing
                ? "-fx-background-color:#0078fe; -fx-background-radius:8;"
                : "-fx-background-color:#3a3a3a; -fx-background-radius:8;");
        flow.setStyle("-fx-fill:white; -fx-font-size:16;");

        return bubble;
    }

    private List<MessageDTO> parseJsonToMessageDTO(String json) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();

        Type listType = new TypeToken<List<MessageDTO>>(){}.getType();
        return gson.fromJson(json, listType);
    }


    /** Xây TextFlow có emoji màu */
    public static TextFlow buildEmojiTextFlow(String message) {
        TextFlow flow = new TextFlow();

        for (Object part : TextUtils.convertToStringAndEmojiObjects(message)) {
            if (part instanceof String str) {
                // văn bản thường → tô trắng
                Text t = new Text(str);
                t.setFill(Color.WHITE);
                t.setStyle("-fx-font-size:16px;");  // Tăng kích thước chữ

                flow.getChildren().add(t);
            }
            else if (part instanceof Emoji emoji) {
                /* ① Thử Twemoji trước */
                String hex = emoji.getUnified().toLowerCase();
                String p  = "/META-INF/resources/webjars/twemoji/14.0.2/assets/72x72/" + hex + ".png";
                System.out.println("Twemoji: " + p);
                URL u = ChatController.class.getResource(p);
                if (u != null) {
                    ImageView iv = new ImageView(u.toString());

                    iv.setFitWidth(16); iv.setPreserveRatio(true);
                    flow.getChildren().add(iv);
                    continue;                       // done
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

    // hiển thị Alert lỗi
    private void showError(String msg, Throwable ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(msg);
        if (ex != null) a.setContentText(ex.getMessage());
        a.showAndWait();
    }

    // cảnh báo (không fatal)
    private void showWarn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(msg);
        a.showAndWait();
    }

    /** Tạo bubble file (ảnh, pdf, doc…) */
    private Node createFileMessageNode(String name,long size,
                                       String id, boolean out){
        VBox box = new VBox(6); box.setUserData(id);
        box.getStyleClass().addAll("file-message", out? "outgoing":"incoming");

        // Kiểm tra nếu file là hình ảnh
        boolean isImage = name.matches("(?i).+\\.(png|jpe?g|gif)");
        
        /* thumbnail nếu có */
        byte[] pic = ServiceLocator.chat().getThumb(id);
        System.out.println("[UI] id="+id+" thumb? "+(pic!=null));

        if(pic!=null){
            // Nếu đã có thumbnail trong cache, hiển thị ngay
            ImageView iv = new ImageView(new Image(new ByteArrayInputStream(pic)));
            iv.setFitWidth(260); iv.setPreserveRatio(true);
            iv.setId("thumb"); // Đánh dấu để có thể cập nhật sau
            box.getChildren().add(iv);
        } else if(isImage) {
            // Nếu là hình ảnh nhưng chưa có thumbnail, yêu cầu từ server
            // Kiểm tra xem đã yêu cầu thumbnail chưa để tránh yêu cầu trùng lặp
            if(!ServiceLocator.chat().isThumbRequested(id)) {
                ServiceLocator.chat().requestThumb(id);
                System.out.println("[UI] Yêu cầu thumbnail cho file: " + id);
            }
            
            // Hiển thị thông báo đang tải thumbnail
            Label loadingLabel = new Label("Đang tải hình ảnh...");
            loadingLabel.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
            loadingLabel.setId("loading-thumb");
            box.getChildren().add(loadingLabel);
            
            // Tạm thời hiển thị file đầy đủ nếu có và nhỏ hơn 2MB
            if(size < 2*1024*1024) {
                byte[] full = ServiceLocator.chat().getFileData(id);
                if(full!=null){
                    ImageView iv = new ImageView(new Image(new ByteArrayInputStream(full)));
                    iv.setFitWidth(260); iv.setPreserveRatio(true);
                    box.getChildren().add(iv);
                }
            }
        }

        Label lbl = new Label(name);
        Label sz  = new Label(formatFileSize(size));
        Button btn= new Button(ServiceLocator.chat().hasFile(id)? "Lưu về…" : "Tải xuống");
        btn.setOnAction(e -> handleDownload(btn,id,name));
        box.getChildren().addAll(lbl,sz,btn);

        HBox row = new HBox(box);
        row.setAlignment(out? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setSpacing(4);
        fileBubbleMap.put(id, box);          // Lưu vào map để có thể cập nhật sau

        return row;
    }

    private String getFileIconPath(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
            case "png":
                return "/icons/image.png";
            case "pdf":
                return "/icons/pdf.png";
            default:
                return "/icons/file.png";
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp-1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * Hiển thị tin nhắn với khả năng xử lý mã hóa
     */
    public void displayMessage(String from, String content, boolean isOutgoing, LocalDateTime sentTime) {
        HBox bubbleBox = new HBox(5);
        bubbleBox.setPrefWidth(Double.MAX_VALUE);
        bubbleBox.setAlignment(isOutgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageVBox = new VBox(2);

        if (!isOutgoing) {
            Label fromLabel = new Label(from);
            fromLabel.setStyle("-fx-text-fill:#b0b0b0; -fx-font-size:10;");
            messageVBox.getChildren().add(fromLabel);
        }

        // Kiểm tra nếu là tin nhắn file
        boolean isFileMessage = content.startsWith("[FILE]");

        Node msgNode;
        if (isFileMessage) {
            String fileInfo = content.substring(6);
            String[] parts = fileInfo.split("\\|", 3);

            if (parts.length < 3) {
                showWarn("Định dạng FILE message thiếu key: " + content);
                return;
            }

            String fileName = parts[0];
            long fileSize = Long.parseLong(parts[1]);
            String key = parts[2];

            msgNode = createFileMessageNode(fileName, fileSize, key, isOutgoing);

            boolean isImage = fileName.matches("(?i).+\\.(png|jpe?g|gif)");
            if (isImage && ServiceLocator.chat().getThumb(key) == null) {
                ServiceLocator.chat().requestThumb(key);
            }
        } else {
            msgNode = buildMsgNode(content, isOutgoing);
        }

        messageVBox.getChildren().add(msgNode);

        if (sentTime != null) {
            String timeText = sentTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            Label timeLabel = new Label(timeText);
            timeLabel.setStyle("-fx-text-fill:#999999; -fx-font-size:10;");
            messageVBox.getChildren().add(timeLabel);
        }

        bubbleBox.getChildren().add(messageVBox);
        messagesContainer.getChildren().add(bubbleBox);
    }    /**
     * Xây dựng node tin nhắn với chỉ báo trạng thái mã hóa
     */
    private Node buildMsgNodeWithEncryptionStatus(String content, boolean isOutgoing, boolean wasEncrypted) {
        TextFlow flow = buildEmojiTextFlow(content);
        flow.setMaxWidth(600);
        flow.setLineSpacing(2);
        flow.setStyle("-fx-font-size: 16px;");  // Tăng kích thước font chữ

        // Thêm biểu tượng khóa nếu tin nhắn đã được mã hóa thành công
        if (wasEncrypted) {
            HBox container = new HBox(5);
            Label lockIcon = new Label("🔒");
            lockIcon.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12px;");
            container.getChildren().addAll(lockIcon, flow);

            StackPane bubble = new StackPane(container);
            bubble.setPadding(new Insets(8));
            bubble.setStyle(isOutgoing
                    ? "-fx-background-color:#0078fe; -fx-background-radius:8;"
                    : "-fx-background-color:#3a3a3a; -fx-background-radius:8;");
            return bubble;
        }
        // Thêm biểu tượng khóa mở nếu tin nhắn không giải mã được
        else if (content.startsWith("DBENC:")) {
            // Tạo text flow với nội dung "Không thể giải mã tin nhắn"
            TextFlow errorFlow = new TextFlow();
            Text errorText = new Text("Không thể giải mã tin nhắn. Kiểm tra cài đặt mã hóa.");
            errorText.setFill(Color.WHITE);
            errorText.setStyle("-fx-font-size:14px;");
            errorFlow.getChildren().add(errorText);

            HBox container = new HBox(5);
            Label errorIcon = new Label("🔓");
            errorIcon.setStyle("-fx-text-fill: #F44336; -fx-font-size: 12px;");
            container.getChildren().addAll(errorIcon, errorFlow);

            StackPane bubble = new StackPane(container);
            bubble.setPadding(new Insets(8));
            bubble.setStyle(isOutgoing
                    ? "-fx-background-color:#0078fe; -fx-background-radius:8;"
                    : "-fx-background-color:#3a3a3a; -fx-background-radius:8;");
            return bubble;
        }
        // Tin nhắn thường không mã hóa
        else {
            StackPane bubble = new StackPane(flow);
            bubble.setPadding(new Insets(8));
            bubble.setStyle(isOutgoing
                    ? "-fx-background-color:#0078fe; -fx-background-radius:8;"
                    : "-fx-background-color:#3a3a3a; -fx-background-radius:8;");
            flow.setStyle("-fx-fill:white; -fx-font-size:16;");
            return bubble;
        }
    }

    private void updateRecentChatAvatar(RecentChatCellData chat, Circle avatarCircle, Label initialLabel) {
        // Special handling for Global chat
        if ("Global".equals(chat.chatName)) {
            avatarCircle.setFill(Color.rgb(76, 175, 80)); // Green color for Global
            initialLabel.setText("G");
            initialLabel.setVisible(true);
            return;
        }

        // Check if we have a custom avatar path for this chat
        if (chat.avatarPath != null && !chat.avatarPath.isEmpty()) {
            File f = new File(chat.avatarPath);
            if (f.exists()) {
                avatarCircle.setFill(new ImagePattern(new Image(f.toURI().toString())));
                initialLabel.setVisible(false);
                return;
            }
        }

        // Default avatar with first letter
        int colorIndex = Math.abs(chat.chatName.hashCode() % AVATAR_COLORS.length);
        avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
        initialLabel.setText(chat.chatName.substring(0, 1).toUpperCase());
        initialLabel.setVisible(true);
    }

    public void refreshThumbnail(String id){
        VBox box = fileBubbleMap.get(id);
        if(box == null) return;                    // chưa kịp vẽ bubble

        // Xóa thông báo đang tải nếu có
        Node loadingLabel = box.lookup("#loading-thumb");
        if(loadingLabel != null) {
            box.getChildren().remove(loadingLabel);
        }

        // Kiểm tra xem đã có thumbnail chưa
        Node existingThumb = box.lookup("#thumb");
        if(existingThumb != null) {
            // Nếu đã có thumbnail, cập nhật nó
            box.getChildren().remove(existingThumb);
        }

        byte[] data = ServiceLocator.chat().getThumb(id);
        if(data == null) {
            // Nếu không nhận được thumbnail, hiển thị thông báo lỗi
            Label errorLabel = new Label("Không thể tải hình ảnh");
            errorLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-style: italic;");
            errorLabel.setId("thumb-error");
            box.getChildren().add(0, errorLabel);
            return;
        }

        try {
            // Tạo và hiển thị thumbnail mới
            ImageView iv = new ImageView(new Image(new ByteArrayInputStream(data)));
            iv.setId("thumb");
            iv.setFitWidth(260); iv.setPreserveRatio(true);

            // Chèn thumbnail vào đầu danh sách con
            box.getChildren().add(0, iv);
            box.requestLayout();
            
            System.out.println("[UI] Đã cập nhật thumbnail cho file: " + id);
        } catch (Exception e) {
            // Nếu có lỗi khi tạo ImageView, hiển thị thông báo lỗi
            Label errorLabel = new Label("Lỗi hiển thị hình ảnh");
            errorLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-style: italic;");
            errorLabel.setId("thumb-error");
            box.getChildren().add(0, errorLabel);
            System.out.println("[UI] Lỗi hiển thị thumbnail cho file: " + id + ", lỗi: " + e.getMessage());
        }
    }

    /*  tiện ích bọc node tin-nhắn vào một dòng HBox  */
    private HBox hboxWrap(Node inner, boolean outgoing){
        HBox row = new HBox(inner);
        row.setFillHeight(true);
        row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setSpacing(4);
        return row;
    }
    private void handleDownload(Button btn,String id,String fileName){
        ChatService svc = ServiceLocator.chat();

        if(!svc.hasFile(id)){                 // chưa có file gốc
            btn.setText("Đang tải…"); btn.setDisable(true);
            svc.download(id);                 // gửi GET_FILE

            new Thread(() -> {                // chờ tải xong
                while(!svc.hasFile(id)){
                    try{ Thread.sleep(200);} catch(Exception ignored){}
                }
                Platform.runLater(() -> {
                    btn.setText("Lưu về…"); btn.setDisable(false);
                });
            }).start();
            return;
        }

        /* Đã cache → cho lưu ra ổ đĩa */
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(fileName);
        File dest = fc.showSaveDialog(btn.getScene().getWindow());
        if(dest != null){
            try{
                Files.write(dest.toPath(), svc.getFileData(id));
            }catch(IOException ex){ showError("Lưu file lỗi", ex);}
        }
    }

    /**
     * Cập nhật hiển thị avatar người dùng
     */
    private void updateUserAvatar() {
        if (currentUser == null) return;

        UserService userService = ServiceLocator.userService();
        User user = userService.getUser(currentUser);

        /* --- MẶC ĐỊNH ẩn ImageView, chỉ dùng Circle --- */
        userAvatarImage.setVisible(false);
        userAvatarCircle.setStroke(Color.WHITE);
        userAvatarCircle.setStrokeWidth(2);
        userAvatarCircle.setRadius(20); // Đặt kích thước cố định cho avatar

        if (user != null && !user.isUseDefaultAvatar() && user.getAvatarPath() != null) {
            /* === Avatar tuỳ chỉnh === */
            File avatarFile = new File(user.getAvatarPath());
            if (avatarFile.exists()) {
                try {
                    Image img = new Image(avatarFile.toURI().toString(), 
                                       40, 40, true, true); // Đặt kích thước và cho phép smooth scaling
                    ImagePattern pattern = new ImagePattern(img);
                    userAvatarCircle.setFill(pattern);
                    userInitialLabel.setVisible(false);
                    userAvatarCircle.setVisible(true);
                    return;
                } catch (Exception e) {
                    System.out.println("Lỗi khi tải avatar: " + e.getMessage());
                }
            }
        }

        /* === Avatar mặc định (chữ cái đầu) === */
        int colorIndex = Math.abs(currentUser.hashCode() % AVATAR_COLORS.length);
        userAvatarCircle.setFill(AVATAR_COLORS[colorIndex]);
        userInitialLabel.setText(currentUser.substring(0, 1).toUpperCase());
        userInitialLabel.setVisible(true);
        userAvatarCircle.setVisible(true);
    }

    /**
     * Mở trang cài đặt người dùng
     */
    @FXML
    private void onOpenSettings() {
        try {
            // Nạp profile.fxml
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/profile.fxml"));
            Parent root = loader.load();

            // Lấy Scene hiện tại
            Scene scene = btnSettings.getScene();
            scene.setRoot(root);

            // Lấy controller của profile.fxml, gán username
            ProfileController profileCtrl = loader.getController();
            profileCtrl.setCurrentUser(currentUser);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Không thể mở trang cài đặt", e);
        }
    }

    @FXML
    private void onBack() {
        try {
            // Xóa kết quả tìm kiếm và trở về danh sách chat gần đây
            Platform.runLater(() -> {
                searchField.clear();
                listRecentChats.setVisible(true);
                listRecentChats.setManaged(true);
                listSearchUsers.setVisible(false);
                listSearchUsers.setManaged(false);
                
                // Ẩn nút back
                btnBack.setVisible(false);
                btnBack.setManaged(false);
            });
            
            // Yêu cầu danh sách user online mới từ server
            ServiceLocator.chat().getClient().requestUserList();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể quay lại trang chat", e);
        }
    }

    // Thêm hàm mở conversation riêng tư
    private void openPrivateConversation(User user) {
        if (user == null) {
            showError("Không thể mở cuộc trò chuyện", new Exception("User là null"));
            return;
        }

        try {
            // Update UI
            this.currentTarget = user.getUsername();
            chatTitleLabel.setText(user.getFullName() != null ? user.getFullName() : user.getUsername());
            messagesContainer.getChildren().clear();

            // Ensure the conversation exists before requesting history
            clientConnection.checkPrivateConversation(user.getUsername());

            // Wait a bit for the conversation to be created if needed
            new Thread(() -> {
                try {
                    Thread.sleep(300); // Small delay

                    // Then request history
                    Platform.runLater(() -> {
                        clientConnection.requestHistory(currentUser, user.getUsername());
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            // Set lastPmTarget for outgoing messages
            lastPmTarget = user.getUsername();

            // Reset unread count
            unreadMap.put(user.getUsername(), 0);

            // Refresh recent chats to show this conversation
            refreshRecentChats();
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to open private conversation: " + e.getMessage());
            e.printStackTrace();
            showError("Không thể mở cuộc trò chuyện", e);
        }
    }
    private void refreshRecentChats() {
        CompletableFuture.runAsync(() -> {
            try {
                // Get current user
                User currentUserObj = ServiceLocator.userService().getUser(currentUser);
                if (currentUserObj == null) {
                    System.out.println("[ERROR] Cannot refresh recent chats: current user is null");
                    return;
                }

                // Get recent chats with fresh data
                List<RecentChatCellData> data = ServiceLocator.messageService()
                        .getRecentChats(currentUserObj);

                // Apply unread counts from our map
                List<RecentChatCellData> updatedData = data.stream()
                        .map(chat -> {
                            int unreadCount = unreadMap.getOrDefault(chat.chatName, 0);
                            return new RecentChatCellData(
                                    chat.chatName,
                                    chat.lastMessage,
                                    chat.time,
                                    chat.avatarPath,
                                    unreadCount
                            );
                        })
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    listRecentChats.getItems().setAll(updatedData);
                    listRecentChats.refresh();
                });

                // Update notifications
                updateAppBadge();
                updateTabBadges();
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to refresh recent chats: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    private void showToast(String message) {
        Platform.runLater(() -> {
            try {
                // Create a more stylish toast container
                VBox toast = new VBox();
                toast.setStyle("-fx-background-color: #2196F3; " + // Blue background
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 12 20; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4); " +
                        "-fx-border-width: 0;");

                // Create message label with better styling
                Label messageLabel = new Label(message);
                messageLabel.setStyle("-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: 600;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(300);

                // Add to container
                toast.getChildren().add(messageLabel);
                toast.setAlignment(Pos.CENTER);
                toast.setMaxWidth(300);

                // Get the root container to add our toast
                StackPane root = null;
                if (rootSplit.getScene() != null && rootSplit.getScene().getRoot() instanceof StackPane) {
                    root = (StackPane) rootSplit.getScene().getRoot();
                } else {
                    // Create a new StackPane to hold everything
                    root = new StackPane();
                    if (rootSplit.getScene() != null) {
                        Scene scene = rootSplit.getScene();
                        Node oldRoot = scene.getRoot();
                        scene.setRoot(root);
                        root.getChildren().add(oldRoot);
                    }
                }

                // Add toast to root at bottom right corner
                if (root != null) {
                    root.getChildren().add(toast);
                    StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
                    StackPane.setMargin(toast, new Insets(0, 25, 60, 0));

                    // Make animations smoother
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();

                    // Auto dismiss after 3 seconds
                    PauseTransition delay = new PauseTransition(Duration.seconds(3));
                    StackPane finalRoot = root;
                    delay.setOnFinished(e -> {
                        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toast);
                        fadeOut.setFromValue(1);
                        fadeOut.setToValue(0);
                        fadeOut.setOnFinished(event -> finalRoot.getChildren().remove(toast));
                        fadeOut.play();
                    });
                    delay.play();

                    // Play notification sound
                    playMessageSound();
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to show toast notification: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    private void handleNewMessage(String sender, String content) {
        // Only handle messages from others
        if (sender.equals(currentUser)) return;

        // 1. Update unread counter
        int count = unreadMap.getOrDefault(sender, 0) + 1;
        unreadMap.put(sender, count);

        // 2. Show visual notification if not in the current conversation
        if (!sender.equals(currentTarget)) {
            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            showToast(sender + ": " + previewText);
        }

        // 3. Update the badges
        updateAppBadge();
    }
    private void updateAppBadge() {
        try {
            // Count total unread messages
            int totalUnread = unreadMap.values().stream().mapToInt(Integer::intValue).sum();

            // Add unread friend requests
            if (hasUnreadFriendRequests) {
                totalUnread += listFriendRequests.getItems().size();
            }

            // Update window title with unread count
            if (totalUnread > 0) {
                int finalTotalUnread = totalUnread;
                Platform.runLater(() -> {
                    Stage stage = (Stage) rootSplit.getScene().getWindow();
                    if (stage != null) {
                        stage.setTitle("MyMessenger (" + finalTotalUnread + ")");
                    }
                });

                // Also update dock badge
                updateDockBadge();
            } else {
                Platform.runLater(() -> {
                    Stage stage = (Stage) rootSplit.getScene().getWindow();
                    if (stage != null) {
                        stage.setTitle("MyMessenger");
                    }
                });
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to update app badge: " + e.getMessage());
        }
    }
    @FXML
    private void refreshEverything() {
        System.out.println("[DEBUG] Manual refresh requested");

        // Show loading indicator
        showToast("Đang làm mới...");

        // Request fresh data in the background
        CompletableFuture.runAsync(() -> {
            try {
                // Request all avatars
                clientConnection.requestAllAvatars();

                // Request user list to update online status
                clientConnection.requestUserList();

                // Request pending friend requests
                clientConnection.requestPendingFriendRequests(currentUser);

                // Refresh recent chats data
                Platform.runLater(() -> {
                    refreshRecentChats();
                    refreshFriendRequests();

                    // Show success message
                    showToast("Đã làm mới xong!");
                });
            } catch (Exception e) {
                System.out.println("[ERROR] Refresh failed: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> showToast("Làm mới thất bại: " + e.getMessage()));
            }
        });
    }
    private void updateTabBadges() {
        try {
            // Update friend requests tab
            int requestCount = listFriendRequests.getItems().size();
            if (requestCount > 0 && hasUnreadFriendRequests) {
                addNotificationBadgeToTab("Lời mời", requestCount);
            } else {
                removeNotificationBadgeFromTab("Lời mời");
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to update tab badges: " + e.getMessage());
        }
    }

    private void setupFocusListener() {
        // Get the stage
        rootSplit.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Stage stage = (Stage) newScene.getWindow();
                if (stage != null) {
                    // Add focus listener
                    stage.focusedProperty().addListener((prop, wasFocused, isNowFocused) -> {
                        if (isNowFocused) {
                            // Application got focus, refresh everything
                            System.out.println("[DEBUG] Application focused - refreshing data");
                            refreshRecentChats();
                            refreshFriendRequests();
                            clientConnection.requestUserList(); // Update online users
                        }
                    });
                }
            }
        });
    }

    private void playMessageSound() {
        try {
            // Try to find a sound file in resources
            String soundFile = "/sounds/message.mp4";
            URL soundUrl = getClass().getResource(soundFile);

            if (soundUrl != null) {
                // Play the sound
                Media sound = new Media(soundUrl.toString());
                MediaPlayer mediaPlayer = new MediaPlayer(sound);
                mediaPlayer.setVolume(0.3);
                mediaPlayer.play();
            } else {
                // Try alternative sound files
                String[] alternatives = {"/sounds/notification.mp3", "/sounds/alert.mp3", "/sounds/ding.mp3"};
                for (String alt : alternatives) {
                    URL altUrl = getClass().getResource(alt);
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
            // Silently ignore sound errors
            System.out.println("[WARN] Could not play notification sound: " + e.getMessage());
        }
    }    private void updateDockBadge() {
        // Count total unread messages
        int totalUnread = unreadMap.values().stream().mapToInt(Integer::intValue).sum();

        if (totalUnread > 0) {
            try {
                // This is just a placeholder - different platforms require different implementations
                // For macOS - use com.apple.eawt.Application
                // For Windows - can use custom window styles or third-party libraries
                System.out.println("Setting application badge to: " + totalUnread);
                // You would implement platform-specific code here
            } catch (Exception e) {
                System.out.println("Could not update dock badge: " + e.getMessage());
            }
        }
    }
    private void handleNewGroupMessage(String groupName, String sender, String content) {
        // Skip messages from current user
        if (sender.equals(currentUser)) return;

        // Update unread count if not in the current group view
        if (!groupName.equals(currentTarget)) {
            int count = unreadMap.getOrDefault(groupName, 0) + 1;
            unreadMap.put(groupName, count);

            // Show notification
            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            showToast(groupName + " - " + sender + ": " + previewText);
        }

        // Update app badge and notifications
        updateAppBadge();
    }
    private void setupGroupListCellFactory() {
        listGroups.setCellFactory(lv -> new ListCell<String>() {
            private final Circle avatarCircle = new Circle(24);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label descriptionLabel = new Label("Nhóm chat");
            private final HBox mainBox = new HBox(12);
            private final VBox contentBox = new VBox(4);

            {
                // Style components
                nameLabel.getStyleClass().add("group-name");
                descriptionLabel.getStyleClass().add("group-description");

                // Additional inline styles
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                descriptionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

                // Set up avatar
                StackPane avatarPane = new StackPane(avatarCircle, initialLabel);
                avatarPane.setMinWidth(48);

                // Set up content structure
                contentBox.getChildren().addAll(nameLabel, descriptionLabel);
                HBox.setHgrow(contentBox, Priority.ALWAYS);

                // Main layout
                mainBox.getChildren().addAll(avatarPane, contentBox);
                mainBox.setAlignment(Pos.CENTER_LEFT);
                mainBox.setPadding(new Insets(8, 12, 8, 8));

                // Cell selection style
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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

                // Group avatar (circle with first letter)
                int colorIndex = Math.abs(groupName.hashCode() % AVATAR_COLORS.length);
                avatarCircle.setFill(AVATAR_COLORS[colorIndex]);
                initialLabel.setText(groupName.substring(0, 1).toUpperCase());
                initialLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

                // Show member count if available
                Long groupId = groupMap.get(groupName);
                if (groupId != null) {
                    // In a real app, you'd query the database for member count
                    // For now, we'll just show a generic description
                    descriptionLabel.setText("Nhóm chat");
                }

                // Highlight selected group
                if (groupName.equals(currentTarget)) {
                    mainBox.setStyle("-fx-background-color: rgba(33, 150, 243, 0.1);");
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2196F3;");
                } else {
                    mainBox.setStyle("");
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                }

                setGraphic(mainBox);
            }
        });
    }
    private void setupCreateGroupButton() {
        // Find the "Tạo nhóm mới" button
        if (btnCreateGroup != null) {
            // Make it look better
            btnCreateGroup.getStyleClass().add("create-group-btn");
            btnCreateGroup.setStyle(
                    "-fx-background-color: #2196F3; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 4; " +
                            "-fx-padding: 8 12; " +
                            "-fx-cursor: hand;"
            );

            // Add an icon
            FontIcon addIcon = new FontIcon("fas-plus-circle");
            addIcon.setIconColor(Color.WHITE);
            addIcon.setIconSize(16);
            btnCreateGroup.setGraphic(addIcon);
            btnCreateGroup.setGraphicTextGap(8);
        }
    }
    /**
     * Kiểm tra xem target hiện tại có phải là nhóm không
     */
    public boolean isGroupTarget(String target) {
        return groupMap.containsKey(target);
    }

    /**
     * Lấy ID của nhóm từ tên nhóm
     */
    public long getGroupId(String groupName) {
        return groupMap.getOrDefault(groupName, -1L);
    }

    /**
     * Lấy target hiện tại
     */
    public String getCurrentTarget() {
        return currentTarget;
    }

    /**
     * Tìm cuộc trò chuyện riêng tư giữa hai người dùng
     */
    public Conversation findPrivateConversation(String userA, String userB) {
        String name1 = userA.compareTo(userB) < 0 ? userA : userB;
        String name2 = userA.compareTo(userB) < 0 ? userB : userA;
        String convName = name1 + "|" + name2;

        return ServiceLocator.conversationDAO().findByName(convName);
    }

    /**
     * Mở dialog cài đặt mã hóa cho Global Chat
     */
//    private void openGlobalEncryptionSettings() {
//        // Tạo dialog
//        Dialog<ButtonType> dialog = new Dialog<>();
//        dialog.setTitle("Cài đặt mã hóa - Global Chat");
//        dialog.setHeaderText("Cài đặt mã hóa tin nhắn cho Global Chat");
//
//        // Tạo nút đóng
//        ButtonType closeButtonType = new ButtonType("Đóng", ButtonBar.ButtonData.OK_DONE);
//        dialog.getDialogPane().getButtonTypes().add(closeButtonType);
//
//        // Tạo grid layout
//        GridPane grid = new GridPane();
//        grid.setHgap(10);
//        grid.setVgap(10);
//        grid.setPadding(new Insets(20, 150, 10, 10));
//
//        // Tạo checkbox bật/tắt mã hóa
//        CheckBox enableEncryptionCheckbox = new CheckBox("Bật mã hóa tin nhắn");
//        boolean isEnabled = ConversationKeyManager.getInstance().isGlobalChatEncryptionEnabled();
//        enableEncryptionCheckbox.setSelected(isEnabled);
//
//        // Tạo trường nhập khóa
//        TextField keyField = new TextField();
//        keyField.setText(ConversationKeyManager.getInstance().getKey(ConversationKeyManager.GLOBAL_CHAT_ID));
//        keyField.setPromptText("Nhập khóa mã hóa (ít nhất 16 ký tự)");
//        keyField.setPrefWidth(300);
//        keyField.setDisable(!isEnabled);
//
//        // Theo dõi trạng thái checkbox
//        enableEncryptionCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
//            keyField.setDisable(!newVal);
//
//            // Nếu bật mã hóa mà không có khóa, tự động tạo khóa
//            if (newVal && (keyField.getText() == null || keyField.getText().isEmpty())) {
//                String key = ConversationKeyManager.getInstance().generateRandomKey(ConversationKeyManager.GLOBAL_CHAT_ID);
//                keyField.setText(key);
//            }
//        });
//
//        // Nút tạo khóa ngẫu nhiên
//        Button generateKeyButton = new Button("Tạo khóa ngẫu nhiên");
//        generateKeyButton.setOnAction(e -> {
//            String key = ConversationKeyManager.getInstance().generateRandomKey(ConversationKeyManager.GLOBAL_CHAT_ID);
//            keyField.setText(key);
//        });
//
//        // Nút sao chép khóa vào clipboard
//        Button copyKeyButton = new Button("Sao chép khóa");
//        copyKeyButton.setOnAction(e -> {
//            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
//            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
//            content.putString(keyField.getText());
//            clipboard.setContent(content);
//
//            // Hiển thị thông báo
//            Tooltip tooltip = new Tooltip("Đã sao chép vào clipboard!");
//            tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
//            tooltip.show(copyKeyButton,
//                    copyKeyButton.localToScreen(copyKeyButton.getBoundsInLocal()).getMinX(),
//                    copyKeyButton.localToScreen(copyKeyButton.getBoundsInLocal()).getMaxY());
//
//            // Tự động ẩn sau 2 giây
//            PauseTransition delay = new PauseTransition(javafx.util.Duration.seconds(2));
//            delay.setOnFinished(evt -> tooltip.hide());
//            delay.play();
//        });
//
//        // Nút lưu cài đặt
//        Button saveButton = new Button("Lưu cài đặt");
//        saveButton.setDefaultButton(true);
//        saveButton.setOnAction(e -> {
//            boolean enableEncryption = enableEncryptionCheckbox.isSelected();
//            String key = keyField.getText();
//
//            // Kiểm tra key hợp lệ nếu bật mã hóa
//            if (enableEncryption) {
//                if (key == null || key.length() < 16) {
//                    Alert alert = new Alert(Alert.AlertType.WARNING);
//                    alert.setTitle("Cảnh báo");
//                    alert.setHeaderText(null);
//                    alert.setContentText("Khóa mã hóa phải có ít nhất 16 ký tự để đảm bảo an toàn.");
//                    alert.showAndWait();
//                    return;
//                }
//
//                // Lưu key và bật mã hóa
//                ConversationKeyManager.getInstance().setKey(ConversationKeyManager.GLOBAL_CHAT_ID, key);
//                ConversationKeyManager.getInstance().setGlobalChatEncryptionEnabled(true);
//            } else {
//                // Tắt mã hóa
//                ConversationKeyManager.getInstance().setGlobalChatEncryptionEnabled(false);
//            }
//
//            // Hiển thị thông báo thành công
//            Alert alert = new Alert(Alert.AlertType.INFORMATION);
//            alert.setTitle("Thành công");
//            alert.setHeaderText(null);
//            alert.setContentText("Đã lưu cài đặt mã hóa cho Global Chat thành công!");
//            alert.showAndWait();
//
//            // Cập nhật hiển thị trạng thái mã hóa
//            updateGlobalEncryptionStatusDisplay();
//        });
//
//        // Thêm các thành phần vào grid
//        grid.add(new Label("Trạng thái:"), 0, 0);
//        grid.add(enableEncryptionCheckbox, 1, 0);
//
//        grid.add(new Label("Khóa mã hóa:"), 0, 1);
//        grid.add(keyField, 1, 1);
//
//        HBox buttonBox = new HBox(10);
//        buttonBox.setAlignment(Pos.CENTER_RIGHT);
//        buttonBox.getChildren().addAll(generateKeyButton, copyKeyButton, saveButton);
//        grid.add(buttonBox, 1, 2);
//
//        // Thêm mô tả
//        VBox content = new VBox(15);
//        content.getChildren().add(grid);
//
//        TextArea infoArea = new TextArea(
//                "Lưu ý về mã hóa tin nhắn Global Chat:\n\n" +
//                        "- Mã hóa sẽ chỉ áp dụng cho tin nhắn văn bản, không áp dụng cho file.\n" +
//                        "- Tất cả người tham gia Global Chat phải có cùng khóa để đọc được tin nhắn.\n" +
//                        "- Khóa nên có độ dài ít nhất 16 ký tự để đảm bảo an toàn.\n" +
//                        "- Việc mã hóa sẽ chỉ áp dụng cho tin nhắn mới, không áp dụng cho tin nhắn cũ.\n" +
//                        "- Hãy sao chép và chia sẻ khóa này qua kênh an toàn khác với mọi người."
//        );
//        infoArea.setEditable(false);
//        infoArea.setWrapText(true);
//        infoArea.setPrefHeight(120);
//        infoArea.setStyle("-fx-control-inner-background: #f8f8f8;");
//
//        content.getChildren().add(infoArea);
//        dialog.getDialogPane().setContent(content);
//
//        // Đặt kích thước cho dialog
//        dialog.getDialogPane().setPrefSize(550, 400);
//
//        // Hiển thị dialog
//        dialog.showAndWait();
//    }

    /**
     * Cập nhật hiển thị trạng thái mã hóa cho Global Chat
     */
//    private void updateGlobalEncryptionStatusDisplay() {
//        if (!"Global".equals(currentTarget)) {
//            return; // Chỉ cập nhật khi đang ở Global Chat
//        }
//
//        // Tìm HBox header trong vùng chat
//        HBox chatHeader = (HBox) chatTitleLabel.getParent();
//
//        // Tìm và xóa label trạng thái mã hóa cũ nếu có
//        chatHeader.getChildren().removeIf(node -> node.getId() != null && node.getId().equals("global-encryption-status"));
//
//        // Kiểm tra trạng thái mã hóa
//        if (ConversationKeyManager.getInstance().isGlobalChatEncryptionEnabled()) {
//            // Tạo label hiển thị trạng thái mã hóa
//            Label encryptionStatus = new Label("🔒 Đã mã hóa");
//            encryptionStatus.setId("global-encryption-status");
//            encryptionStatus.setStyle("-fx-text-fill: #4CAF50; -fx-font-style: italic; -fx-font-size: 12px;");
//
//            // Thêm vào sau title
//            int titleIndex = chatHeader.getChildren().indexOf(chatTitleLabel);
//            if (titleIndex >= 0) {
//                chatHeader.getChildren().add(titleIndex + 1, encryptionStatus);
//            }
//
//            // Cập nhật icon nút mã hóa
//            for (Node node : chatHeader.getChildren()) {
//                if (node.getId() != null && node.getId().equals("btnGlobalEncryption") && node instanceof Button) {
//                    Button btn = (Button) node;
//                    FontIcon lockIcon = new FontIcon("fas-lock");
//                    lockIcon.setIconColor(Color.rgb(76, 175, 80)); // Màu xanh
//                    lockIcon.setIconSize(16);
//                    btn.setGraphic(lockIcon);
//                    break;
//                }
//            }
//        } else {
//            // Cập nhật icon nút mã hóa thành khóa mở
//            for (Node node : chatHeader.getChildren()) {
//                if (node.getId() != null && node.getId().equals("btnGlobalEncryption") && node instanceof Button) {
//                    Button btn = (Button) node;
//                    FontIcon lockIcon = new FontIcon("fas-lock-open");
//                    lockIcon.setIconColor(Color.WHITE);
//                    lockIcon.setIconSize(16);
//                    btn.setGraphic(lockIcon);
//                    break;
//                }
//            }
//        }
//    }
    /**
     * Sửa đổi phương thức setupUIComponents để thêm nút mã hóa cho Global Chat
     */
//    private void updateUIForGlobalChat() {
//        if ("Global".equals(currentTarget)) {
//            addGlobalEncryptionButton();
//        }
//    }
    private void handleChatSelection(String targetName) {
        // Cập nhật UI khi chuyển sang Global Chat
        if ("Global".equals(targetName)) {
//            updateUIForGlobalChat();
        } else {
            // Xóa hiển thị trạng thái mã hóa Global khi chuyển sang chat khác
            HBox chatHeader = (HBox) chatTitleLabel.getParent();
            chatHeader.getChildren().removeIf(node ->
                    node.getId() != null &&
                            (node.getId().equals("global-encryption-status") || node.getId().equals("btnGlobalEncryption")));
        }
    }
//    private boolean isAdmin() {
//        System.out.println("[DEBUG] Checking if user is admin: " + currentUser);
//        if (currentUser == null) return false;
//
//        // Thêm log để debug
//        System.out.println("[DEBUG] Checking admin status for: '" + currentUser + "'");
//
//        // Chuyển tất cả về chữ thường để so sánh
//        String currentUserLower = currentUser.toLowerCase().trim();
//        String[] adminUsers = {"an", "bảo", "bảnh"};
//
//        for (String adminUser : adminUsers) {
//            if (adminUser.equals(currentUserLower)) {
//                System.out.println("[DEBUG] User is admin: " + currentUser);
//                return true;
//            }
//        }
//        return false;
//    }

//    @FXML
//    private void openAdminPanel() {
//        if (!isAdmin()) {
//            showWarn("Bạn không có quyền truy cập vào phần quản trị hệ thống.");
//            return;
//        }
//
//        try {
//            // Nạp admin.fxml
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin.fxml"));
//            Parent root = loader.load();
//
//            // Lấy Scene hiện tại
//            Scene scene = btnSettings.getScene();
//            scene.setRoot(root);
//
//            // Lấy controller của admin.fxml, gán username
//            AdminController adminCtrl = loader.getController();
//            adminCtrl.setAdminUser(currentUser);
//        } catch (IOException e) {
//            e.printStackTrace();
//            showError("Không thể mở trang quản trị", e);
//        }
//    }
//    private void setupAdminMenu() {
//        // Thêm log để debug
//        System.out.println("[DEBUG] Setting up admin menu, isAdmin: " + isAdmin());
//
//        if (isAdmin()) {
//            System.out.println("[DEBUG] User is admin, creating admin menu");
//
//            // Tạo menu cho admin
//            ContextMenu adminMenu = new ContextMenu();
//            MenuItem adminItem = new MenuItem("Quản trị hệ thống");
//            adminItem.setOnAction(e -> openAdminPanel());
//            adminMenu.getItems().add(adminItem);
//
//            // Thêm nút gọi menu thay vì dùng chuột phải
//            Button adminButton = new Button("Admin");
//            adminButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white;");
//            adminButton.setOnAction(e -> openAdminPanel());
//
//            // Tìm hbox chứa nút settings
//            if (btnSettings.getParent() instanceof HBox) {
//                HBox headerBox = (HBox) btnSettings.getParent();
//                // Thêm nút admin vào trước nút settings
//                headerBox.getChildren().add(headerBox.getChildren().indexOf(btnSettings), adminButton);
//            }
//        }
//    }
    public void refreshAfterFriendshipChange() {
        // Request updated conversation list
        if (clientConnection != null) {
            clientConnection.requestUserList();
            // Also update recent chats
            loadRecentChats();
        }
    }

    @FXML
    private void onLogout() {
        try {
            // Hiển thị hộp thoại xác nhận đăng xuất
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xác nhận đăng xuất");
            alert.setHeaderText("Bạn có chắc chắn muốn đăng xuất không?");
            alert.setContentText("Tất cả tin nhắn chưa gửi sẽ bị mất.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Đóng kết nối với server
                if (clientConnection != null) {
                    clientConnection.close();
                }

                // Chuyển sang màn hình đăng nhập
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
                Parent root = loader.load();
                
                // Lấy scene hiện tại và thay đổi root
                Scene scene = btnLogout.getScene();
                scene.setRoot(root);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể đăng xuất", e);
        }
    }

}