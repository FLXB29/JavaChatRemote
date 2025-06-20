package app.controller.chat;

import app.ServiceLocator;
import app.controller.ProfileController;
import app.controller.chat.component.MessageBubble;
import app.controller.chat.component.ToastNotification;
import app.controller.chat.factory.FriendRequestCellFactory;
import app.controller.chat.factory.GroupChatCellFactory;
import app.controller.chat.factory.RecentChatCellFactory;
import app.controller.chat.factory.SearchUserCellFactory;
import app.controller.chat.handler.FileHandler;
import app.controller.chat.handler.FriendshipHandler;
import app.controller.chat.handler.MessageHandler;
import app.controller.chat.handler.UserActionHandler;
import app.controller.chat.model.RecentChatCellData;
import app.controller.chat.util.AvatarAdapter;
import app.controller.chat.util.ChatUIHelper;
import app.model.Conversation;
import app.model.Friendship;
import app.model.Message;
import app.model.MessageDTO;
import app.model.User;
import app.service.ChatService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import network.ClientConnection;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Controller cho giao diện chat (phiên bản refactored)
 */
public class ChatControllerRefactored implements Initializable, 
        FriendRequestCellFactory.FriendRequestHandler, 
        SearchUserCellFactory.UserActionHandler {

    // UI Components
    @FXML private TabPane tabPane;
    @FXML private VBox messagesContainer;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField txtMessage;
    @FXML private TextField searchField;
    @FXML private Button btnSend;
    @FXML private Button btnAttachFile;
    @FXML private Button btnAttachImage;
    @FXML private Button btnBack;
    @FXML private Button btnRefresh;
    @FXML private Button btnLogout;
    @FXML private Button btnSettings;
    @FXML private Button btnEmoji;
    @FXML private Button btnCreateGroup;
    @FXML private VBox welcomeScreen;
    @FXML private Label chatTitleLabel;
    @FXML private ListView<RecentChatCellData> listRecentChats;
    @FXML private ListView<User> listSearchUsers;
    @FXML private ListView<String> listGroups;
    @FXML private ListView<Friendship> listFriendRequests;
    @FXML private Circle userAvatarCircle;
    @FXML private ImageView userAvatarImage;
    @FXML private Label userInitialLabel;
    @FXML private Label currentUserLabel;
    @FXML private HBox searchBox;
    @FXML private SplitPane rootSplit;
    
    // Handlers
    private MessageHandler messageHandler;
    private FileHandler fileHandler;
    private FriendshipHandler friendshipHandler;
    private UserActionHandler userActionHandler;
    
    // Connection
    private ClientConnection clientConnection;
    private ChatService chatService;
    private User currentUser;
    private String currentUsername;

    private int pendingFriendRequestCount = 0;
    private Label friendRequestBadge;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            // Khởi tạo ChatService
            chatService = ServiceLocator.chat();
            if (chatService == null) {
                throw new RuntimeException("ChatService chưa được khởi tạo");
            }
            
            // Lấy client connection từ service
            clientConnection = chatService.getClient();
            if (clientConnection == null) {
                throw new RuntimeException("ClientConnection chưa được khởi tạo");
            }
            
            // Đảm bảo service biết về controller này
            chatService.bindRefactoredUI(this);
            
            // Thiết lập welcome screen
            setupWelcomeScreen();
            
            // Khởi tạo các handler (sẽ được thiết lập đầy đủ sau trong setCurrentUser)
            initializeHandlers();
            
            // Thiết lập các thành phần UI
            setupUIComponents();
            
            // Thiết lập cell factories
            setupListCellFactories();
            
            // Thiết lập các sự kiện
            setupEventListeners();
            
            // Thiết lập sự kiện cho các ListView
            setupListViewListeners();
            
            // Thiết lập các callback từ server
            setupCallbacks();
            
            // Thiết lập trình lắng nghe cho ô tìm kiếm
            setupSearchFieldListener();
            
            // Thiết lập trình lắng nghe cho focus
            setupFocusListener();
            
            // Bảo vệ avatar khỏi bị kéo
            setupAvatarProtection();
            
            // Thiết lập hệ thống badge thông báo
            setupNotificationBadges();
            
            // Ban đầu hiển thị màn hình chào mừng
            showWelcomeScreen();
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi khởi tạo controller: " + e.getMessage());
        }
    }
    
    /**
     * Thiết lập các callbacks xử lý tin nhắn
     */
    private void setupCallbacks() {
        if (clientConnection == null) return;
        
        // Tin nhắn toàn cục
        clientConnection.setOnTextReceived((from, message) -> {
            Platform.runLater(() -> {
                System.out.println("Nhận tin nhắn toàn cục từ " + from + ": " + message);
                if (messageHandler != null) {
                    boolean isOutgoing = from.equals(currentUsername);
                    // Hiển thị tin nhắn
                    messageHandler.displayMessage(from, message, isOutgoing, LocalDateTime.now());
                    
                    // Xử lý tin nhắn mới
                    if (!isOutgoing) {
                        messageHandler.handleGlobalMessage(from, message);
                    }
                }
            });
        });
        // Callback khi join conversation thành công
        clientConnection.setOnConvJoined(convId -> {
            Platform.runLater(() -> {
                if (messageHandler != null) {
                    // Lưu conversation ID hiện tại
                    messageHandler.setCurrentConversationId(convId);
                    System.out.println("[DEBUG] Joined conversation ID: " + convId);
                }
            });
        });
        // Tin nhắn riêng tư
        clientConnection.setOnPrivateMsgReceived((from, message) -> {
            Platform.runLater(() -> {
                System.out.println("Nhận tin nhắn riêng tư từ " + from + ": " + message);
                if (messageHandler != null) {
                    boolean isOutgoing = from.equals(currentUsername);
                    
                    // Nếu đang trong chat với người gửi, hiển thị tin nhắn
                    if (messageHandler.getCurrentTarget().equals(from) || isOutgoing) {
                        messageHandler.displayMessage(from, message, isOutgoing, LocalDateTime.now());
                    } else {
                        // Nếu không, xử lý tin nhắn mới (thêm vào unread)
                        if (!isOutgoing) {
                            messageHandler.handleNewMessage(from, message);
                        }
                    }
                    
                    // Refresh danh sách chat gần đây
                    messageHandler.refreshRecentChats();
                }
            });
        });
        
        // Callback khi nhận avatar mới
        clientConnection.setOnAvatarUpdated((username, data) -> {
            Platform.runLater(() -> {
                System.out.println("[AVATAR UPDATED] Nhận avatar mới cho " + username);
                
                // Kiểm tra nếu là avatar cho người dùng hiện tại
                if (username.equals(currentUsername)) {
                    try {
                        // Lưu avatar vào cache
                        String cachePath = app.util.PathUtil.getAvatarCacheDirectory() + "/" + username + ".png";
                        File cacheDir = new File(app.util.PathUtil.getAvatarCacheDirectory());
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs();
                        }
                        
                        File cacheFile = new File(cachePath);
                        java.nio.file.Files.write(cacheFile.toPath(), data);
                        
                        // Hiển thị avatar mới
                        javafx.scene.image.Image img = new javafx.scene.image.Image(cacheFile.toURI().toString(), 
                                                                                userAvatarCircle.getRadius() * 2,
                                                                                userAvatarCircle.getRadius() * 2,
                                                                                true, true);
                        if (!img.isError()) {
                            userAvatarCircle.setFill(new javafx.scene.paint.ImagePattern(img));
                            userInitialLabel.setVisible(false);
                            System.out.println("[AVATAR DEBUG] Đã cập nhật avatar người dùng hiện tại từ server");
                        }
                        
                        // Cập nhật thông tin user
                        if (currentUser != null) {
                            // Thiết lập đường dẫn avatar chuẩn
                            String standardPath = app.util.PathUtil.getStandardAvatarPath(username);
                            currentUser.setAvatarPath(standardPath);
                            currentUser.setUseDefaultAvatar(false);
                            ServiceLocator.userService().updateUserAvatar(currentUser);
                        }
                        
                    } catch (Exception e) {
                        System.out.println("[ERROR] Lỗi khi cập nhật avatar từ server: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Cập nhật UI cho danh sách chat gần đây
                if (messageHandler != null) {
                    messageHandler.refreshRecentChats();
                }
                
                // Cập nhật UI cho danh sách tìm kiếm
                if (listSearchUsers != null && !listSearchUsers.getItems().isEmpty()) {
                    listSearchUsers.refresh();
                }
            });
        });
        
        // Tin nhắn nhóm
        clientConnection.setOnGroupMsg((groupId, from, message) -> {
            Platform.runLater(() -> {
                System.out.println("Nhận tin nhắn nhóm từ " + from + " trong nhóm " + groupId + ": " + message);
                if (messageHandler != null) {
                    boolean isOutgoing = from.equals(currentUsername);
                    
                    // Xử lý tin nhắn nhóm
                    String groupName = "Group " + groupId; // Tạm thời
                    
                    // Cập nhật groupMap nếu cần
                    messageHandler.updateGroupMap(groupName, groupId);
                    
                    // Nếu đang trong chat nhóm này
                    if (messageHandler.isGroupTarget(messageHandler.getCurrentTarget()) 
                            && messageHandler.getGroupId(messageHandler.getCurrentTarget()) == groupId) {
                        messageHandler.displayMessage(from, message, isOutgoing, LocalDateTime.now());
                    } else {
                        // Nếu không, xử lý tin nhắn mới nhóm
                        if (!isOutgoing) {
                            messageHandler.handleNewGroupMessage(groupName, from, message);
                        }
                    }
                    
                    // Refresh danh sách chat gần đây chỉ khi không phải tin nhắn của mình
                    if (!isOutgoing) {
                        messageHandler.refreshRecentChats();
                    }
                }
            });
        });
        
        // Nhận kết quả tạo nhóm
        clientConnection.setOnGroupCreated((groupName, groupId) -> {
            Platform.runLater(() -> {
                System.out.println("Nhóm " + groupName + " đã được tạo với ID: " + groupId);
                if (messageHandler != null) {
                    // Cập nhật groupMap
                    messageHandler.updateGroupMap(groupName, groupId);

                    // Tự động join và mở chat nhóm
                    clientConnection.joinConv(groupId);
                    
                    // Làm mới danh sách chat gần đây
                    messageHandler.refreshRecentChats();

                    // Làm mới danh sách nhóm
                    loadGroups();

                    // Tự động mở chat nhóm mới
                    openGroupChat(groupName);
                    
                    // Hiển thị thông báo
                    showToast("Đã tạo nhóm " + groupName);
                }
            });
        });
        
        // Thiết lập callback cho lời mời kết bạn
// Callback cho lời mời kết bạn
        clientConnection.setOnFriendRequestReceived(fromUser -> {
            Platform.runLater(() -> {
                System.out.println("Nhận lời mời kết bạn từ: " + fromUser);

                // Hiển thị toast notification
                showToast("Bạn có lời mời kết bạn mới từ " + fromUser + "!");

                // Phát âm thanh thông báo
                ChatUIHelper.playNotificationSound();

                // Làm mới danh sách lời mời
                if (friendshipHandler != null) {
                    friendshipHandler.refreshFriendRequests();
                }

                // Tăng badge count
                updateFriendRequestBadge(pendingFriendRequestCount + 1);

                // Highlight tab
                highlightFriendRequestTab();
            });
        });
        // Callback khi lời mời kết bạn được chấp nhận
        clientConnection.setOnFriendRequestAccepted((fromUser, toUser) -> {
            Platform.runLater(() -> {
                System.out.println("Lời mời kết bạn từ " + fromUser + " đến " + toUser + " đã được chấp nhận");
                if (friendshipHandler != null) {
                    // Làm mới dữ liệu
                    refreshAllData();
                    
                    // Hiển thị thông báo phù hợp
                    if (fromUser.equals(currentUsername)) {
                        showToast(toUser + " đã chấp nhận lời mời kết bạn của bạn");
                    } else {
                        showToast("Bạn đã chấp nhận lời mời kết bạn từ " + fromUser);
                    }
                }
            });
        });
        
        // Callback khi lời mời kết bạn bị từ chối
        clientConnection.setOnFriendRequestRejected((fromUser, toUser) -> {
            Platform.runLater(() -> {
                System.out.println("Lời mời kết bạn từ " + fromUser + " đến " + toUser + " đã bị từ chối");
                if (friendshipHandler != null) {
                    // Làm mới dữ liệu
                    refreshAllData();
                    
                    // Hiển thị thông báo phù hợp
                    if (fromUser.equals(currentUsername)) {
                        showToast(toUser + " đã từ chối lời mời kết bạn của bạn");
                    } else {
                        showToast("Bạn đã từ chối lời mời kết bạn từ " + fromUser);
                    }
                }
            });
        });
        
        // Danh sách lịch sử tin nhắn
        clientConnection.setOnHistory((convId, jsonMessages) -> {
            Platform.runLater(() -> {
                System.out.println("Nhận lịch sử tin nhắn cho hội thoại " + convId);
                if (messageHandler != null) {
                    try {
                        // Parse JSON
                        Gson gson = new GsonBuilder()
                            .registerTypeAdapter(LocalDateTime.class, new app.LocalDateTimeAdapter())
                            .create();
                        Type listType = new com.google.gson.reflect.TypeToken<List<app.model.MessageDTO>>(){}.getType();
                        List<app.model.MessageDTO> messages = gson.fromJson(jsonMessages, listType);
                        
                        // Xóa tin nhắn cũ
                        if (messagesContainer != null) {
                            messagesContainer.getChildren().clear();
                        }
                        
                        // Hiển thị tin nhắn
                        for (app.model.MessageDTO msg : messages) {
                            boolean isOutgoing = msg.getUser().equals(currentUsername);
                            messageHandler.displayMessage(
                                msg.getUser(), 
                                msg.getContent(), 
                                isOutgoing, 
                                msg.getTime()
                            );
                        }
                        
                        // Hiển thị màn hình chat sau khi có tin nhắn
                        showChatScreen();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        });
    }
    
    /**
     * Thiết lập màn hình chào mừng
     */
    private void setupWelcomeScreen() {
        // Tạo welcome screen nếu chưa tồn tại
        if (welcomeScreen == null) {
            welcomeScreen = new VBox(20);
            welcomeScreen.setAlignment(Pos.CENTER);
            welcomeScreen.setPadding(new Insets(30));
            welcomeScreen.getStyleClass().add("welcome-screen");
            
            // Thêm welcomeScreen vào cùng container với messagesContainer
            if (messagesContainer != null && messagesContainer.getParent() instanceof Pane) {
                Pane parent = (Pane) messagesContainer.getParent();
                parent.getChildren().add(welcomeScreen);
            }
        }
        
        // Thiết lập nội dung
        welcomeScreen.getChildren().clear();
        
        // Logo hoặc icon
        FontIcon chatIcon = new FontIcon("fas-comments");
        chatIcon.setIconSize(80);
        chatIcon.setIconColor(Color.rgb(33, 150, 243));
        
        // Tiêu đề
        Label titleLabel = new Label("Chào mừng đến với MyMessenger");
        titleLabel.getStyleClass().add("welcome-screen-title");
        
        // Thông điệp
        Label messageLabel = new Label("Hãy chọn một cuộc trò chuyện từ danh sách bên trái để bắt đầu chat");
        messageLabel.getStyleClass().add("welcome-screen-message");
        
        // Thêm một vài dòng thông tin hướng dẫn
        VBox tipsBox = new VBox(8);
        tipsBox.getStyleClass().add("tips-box");
        
        Label tipsTitle = new Label("Tips & Tricks:");
        tipsTitle.getStyleClass().add("tips-title");
        
        Label tip1 = new Label("• Tìm kiếm bạn bè mới bằng cách nhập tên vào ô tìm kiếm");
        Label tip2 = new Label("• Tạo nhóm chat mới với nút \"Tạo nhóm\" ở tab Nhóm");
        Label tip3 = new Label("• Gửi emoji hoặc đính kèm file trong cuộc trò chuyện");
        Label tip4 = new Label("• Kiểm tra lời mời kết bạn trong tab \"Lời mời\"");
        
        tip1.getStyleClass().add("tip-item");
        tip2.getStyleClass().add("tip-item");
        tip3.getStyleClass().add("tip-item");
        tip4.getStyleClass().add("tip-item");
        
        tipsBox.getChildren().addAll(tipsTitle, tip1, tip2, tip3, tip4);
        
        // Thêm tất cả vào welcome screen
        welcomeScreen.getChildren().addAll(chatIcon, titleLabel, messageLabel, tipsBox);
    }
    
    /**
     * Hiển thị màn hình chào mừng
     */
    private void showWelcomeScreen() {
        if (welcomeScreen != null) {
            welcomeScreen.setVisible(true);
            welcomeScreen.setManaged(true);
        }
        
        if (messagesContainer != null) {
            messagesContainer.setVisible(false);
            messagesContainer.setManaged(false);
        }
        
        // Ẩn txMessage và các nút liên quan
        if (txtMessage != null) {
            txtMessage.setVisible(false);
            txtMessage.setManaged(false);
        }
        
        if (btnSend != null) {
            btnSend.setVisible(false);
            btnSend.setManaged(false);
        }
        
        if (btnAttachFile != null) {
            btnAttachFile.setVisible(false);
            btnAttachFile.setManaged(false);
        }
        
        if (btnEmoji != null) {
            btnEmoji.setVisible(false);
            btnEmoji.setManaged(false);
        }
        
        // Cập nhật tiêu đề
        if (chatTitleLabel != null) {
            chatTitleLabel.setText("Chào mừng!");
        }
    }
    
    /**
     * Hiển thị màn hình chat
     */
    private void showChatScreen() {
        if (welcomeScreen != null) {
            welcomeScreen.setVisible(false);
            welcomeScreen.setManaged(false);
        }
        
        if (messagesContainer != null) {
            messagesContainer.setVisible(true);
            messagesContainer.setManaged(true);
        }
        
        // Hiện txMessage và các nút liên quan
        if (txtMessage != null) {
            txtMessage.setVisible(true);
            txtMessage.setManaged(true);
        }
        
        if (btnSend != null) {
            btnSend.setVisible(true);
            btnSend.setManaged(true);
        }
        
        if (btnAttachFile != null) {
            btnAttachFile.setVisible(true);
            btnAttachFile.setManaged(true);
        }
        
        if (btnEmoji != null) {
            btnEmoji.setVisible(true);
            btnEmoji.setManaged(true);
        }
    }
    
    /**
     * Khởi tạo các handler xử lý
     */
    private void initializeHandlers() {
        if (fileHandler != null && messageHandler != null &&
             friendshipHandler != null && userActionHandler != null) {
            // Handlers đã được khởi tạo
            return;
        }
        
        try {
            // Lấy root node từ scene nếu có
            Node rootNode = (rootSplit != null) ? rootSplit : null;
            
            // Đảm bảo currentUsername đã được đặt
            if (currentUsername == null || currentUsername.isBlank()) {
                if (currentUser != null && currentUser.getUsername() != null) {
                    currentUsername = currentUser.getUsername();
                    System.out.println("[INFO] Đặt currentUsername từ currentUser: " + currentUsername);
                } else {
                    System.err.println("[WARN] currentUsername null hoặc trống trong initializeHandlers");
                    // Thử lấy từ UserService nếu có thể
                    User userFromService = ServiceLocator.userService().getCurrentUser();
                    if (userFromService != null && userFromService.getUsername() != null) {
                        currentUsername = userFromService.getUsername();
                        currentUser = userFromService;
                        System.out.println("[INFO] Đặt currentUsername từ UserService: " + currentUsername);
                    }
                }
            }
            
            // Khởi tạo FileHandler với window null - sẽ được cập nhật sau
            fileHandler = new FileHandler(chatService, null);
            
            // Khởi tạo MessageHandler nếu đủ điều kiện
            if (messagesContainer != null) {
                messageHandler = new MessageHandler(clientConnection, currentUsername, 
                    messagesContainer, rootNode, fileHandler, this::updateRecentChats);
                
                // Đảm bảo MessageHandler có currentUser
                if (currentUser != null) {
                    messageHandler.setCurrentUser(currentUser);
                }
            }
            
            // Khởi tạo các handler khác
            friendshipHandler = new FriendshipHandler(clientConnection, currentUsername,
                    this::updateFriendRequests, this::refreshAllData);
                    
            // Đảm bảo client connection đăng ký callback cho xử lý lời mời kết bạn
            if (clientConnection != null) {
                clientConnection.setOnPendingListReceived(jsonData -> {
                    System.out.println("[DEBUG] Received pending friend requests JSON: " + 
                        (jsonData.length() > 100 ? jsonData.substring(0, 97) + "..." : jsonData));
                    
                    // Làm mới dữ liệu trên UI
                    friendshipHandler.refreshFriendRequests();
                });
                
                // Đảm bảo clientConnection biết currentUsername
                if (currentUsername != null && !currentUsername.isBlank()) {
                    clientConnection.setCurrentUser(currentUsername);
                }
            }
            
            userActionHandler = new UserActionHandler(clientConnection, currentUsername,
                    rootNode, messageHandler, friendshipHandler, this::updateSearchResults);
                
            // Set up bổ sung cho FileHandler
            fileHandler.setup(messagesContainer, rootNode, currentUsername, messageHandler);
                 
            // Cập nhật trạng thái
            System.out.println("[DEBUG] Handlers được khởi tạo: " +
                "messageHandler=" + (messageHandler != null) +
                ",fileHandler=" + (fileHandler != null) +
                ",friendshipHandler=" + (friendshipHandler != null) +
                ",userActionHandler=" + (userActionHandler != null) +
                ",currentUsername=" + currentUsername);
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo handlers: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Cập nhật handler sau khi scene đã được tạo
     * Phương thức này sẽ được gọi từ User() sau khi scene đã sẵn sàng
     */
    private void updateHandlersWithScene() {
        try {
            // Kiểm tra xem scene đã được tạo chưa
            if (txtMessage != null && txtMessage.getScene() != null && 
                txtMessage.getScene().getWindow() != null) {
                
                // Cập nhật FileHandler với window thực
                if (fileHandler != null) {
                    fileHandler.setOwnerWindow(txtMessage.getScene().getWindow());
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể cập nhật handlers với scene: " + e.getMessage());
        }
    }
    
    /**
     * Thiết lập các thành phần UI
     */
    private void setupUIComponents() {
        // Message input
        if (txtMessage != null) {
            txtMessage.setOnKeyPressed(event -> {
                if (event.getCode().toString().equals("ENTER")) {
                    onSend();
                }
            });
        }

        // Messages container
        if (messagesContainer != null) {
            messagesContainer.setFillWidth(true);
        }
        if (scrollPane != null) {
            scrollPane.setFitToWidth(true);
            if (messagesContainer != null) {
                scrollPane.vvalueProperty().bind(messagesContainer.heightProperty());
            }
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3a3a3a;");
        }
        
        // Ẩn nút back mặc định và hiển thị khi tìm kiếm
        if (btnBack != null) {
            btnBack.setVisible(false);
        }
        
        // Hiển thị thông tin người dùng hiện tại
        if (currentUserLabel != null) {
            currentUserLabel.setText(currentUsername != null ? currentUsername : "");
        }
        if (currentUser != null && userAvatarCircle != null && userInitialLabel != null) {
            AvatarAdapter.updateUserAvatar(currentUser, userAvatarCircle, userInitialLabel);
        }
        
        // Ẩn danh sách tìm kiếm ban đầu
        if (listSearchUsers != null) {
            listSearchUsers.setVisible(false);
            listSearchUsers.setManaged(false);
        }
        
        // Thiết lập cell factories
        setupListCellFactories();
    }
    
    /**
     * Thiết lập cell factories cho các ListView
     */
    private void setupListCellFactories() {
        if (listRecentChats != null) {
            // Thiết lập cell factory cho danh sách chat gần đây
            RecentChatCellFactory recentChatCellFactory = new RecentChatCellFactory();
            
            // Thêm xử lý click vào cell thay vì dùng selection model
            recentChatCellFactory.setOnChatSelected(chatData -> {
                try {
                    if (chatData != null) {
                        // Xóa selection để tránh lỗi IndexOutOfBoundsException
                        Platform.runLater(() -> listRecentChats.getSelectionModel().clearSelection());
                        
                        // Mở cuộc trò chuyện
                        openConversation(chatData);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Lỗi khi xử lý sự kiện chọn chat: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            listRecentChats.setCellFactory(recentChatCellFactory);
        }
        
        if (listGroups != null) {
            // Thiết lập cell factory cho danh sách nhóm
            GroupChatCellFactory groupChatCellFactory = new GroupChatCellFactory();
            
            // Thêm xử lý click vào cell thay vì dùng selection model
            groupChatCellFactory.setOnGroupSelected(groupName -> {
                try {
                    if (groupName != null && !groupName.isEmpty()) {
                        // Xóa selection để tránh lỗi IndexOutOfBoundsException
                        Platform.runLater(() -> listGroups.getSelectionModel().clearSelection());
                        
                        // Mở nhóm chat
                        openGroupChat(groupName);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Lỗi khi xử lý sự kiện chọn nhóm: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            listGroups.setCellFactory(groupChatCellFactory);
        }
        
        if (listSearchUsers != null) {
            // Thiết lập cell factory cho danh sách kết quả tìm kiếm
            listSearchUsers.setCellFactory(new SearchUserCellFactory(this));
        }
        
        if (listFriendRequests != null) {
            // Thiết lập cell factory cho danh sách lời mời kết bạn (cả gửi và nhận)
            listFriendRequests.setCellFactory(new FriendRequestCellFactory(this, currentUsername));
        }
    }
    
    /**
     * Thiết lập các sự kiện lắng nghe
     */
    private void setupEventListeners() {
        // Setup button actions
        btnSend.setOnAction(e -> onSend());
        btnBack.setOnAction(e -> onBack());
        btnLogout.setOnAction(e -> onLogout());
        btnSettings.setOnAction(e -> onOpenSettings());
        btnRefresh.setOnAction(e -> refreshEverything());
        btnEmoji.setOnAction(e -> onChooseEmoji());
        btnCreateGroup.setOnAction(e -> onCreateGroup());
        
        // Kết nối handler cho nút đính kèm file
        btnAttachFile.setOnAction(e -> onAttachFile());
        
        // Kết nối handler cho nút đính kèm ảnh
        if (btnAttachImage != null) {
            btnAttachImage.setOnAction(e -> onAttachImage());
        }
        
        // Setup Enter key for sending
        txtMessage.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                onSend();
                e.consume();
            }
        });
        
        // Setup tab listener
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                String tabId = newTab.getId();
                if ("tabRecentChats".equals(tabId)) {
                    showWelcomeScreen();
                } else if ("tabGroups".equals(tabId)) {
                    showWelcomeScreen();
                    loadGroups();
                } else if ("tabFriendRequests".equals(tabId)) {
                    showWelcomeScreen();
                    refreshAllData();
                }
            }
        });
        
        // Thiết lập sự kiện cho các ListView
        setupListViewListeners();
        
        // Setup search field
        setupSearchFieldListener();
        
        // Listener for window focus
        setupFocusListener();
    }
    
    /**
     * Thiết lập sự kiện cho các ListView
     */
    private void setupListViewListeners() {
        // Vô hiệu hóa sự kiện selection cho các ListView để tránh lỗi
        // Thay vào đó, chúng ta sẽ xử lý click thông qua cell factory
        if (listRecentChats != null) {
            // Vô hiệu hóa sự kiện selection
            listRecentChats.setOnMouseClicked(event -> {
                // Xử lý thông qua cell factory
                event.consume();
            });
        }
        
        if (listGroups != null) {
            // Vô hiệu hóa sự kiện selection
            listGroups.setOnMouseClicked(event -> {
                // Xử lý thông qua cell factory
                event.consume();
            });
        }
    }
    
    /**
     * Thiết lập sự kiện lắng nghe cho trường tìm kiếm
     */
    private void setupSearchFieldListener() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                boolean isSearching = newVal != null && !newVal.isBlank();
                
                // Hiển thị/ẩn các danh sách
                if (listRecentChats != null) {
                    listRecentChats.setVisible(!isSearching);
                    listRecentChats.setManaged(!isSearching);
                }
                if (listSearchUsers != null) {
                    listSearchUsers.setVisible(isSearching);
                    listSearchUsers.setManaged(isSearching);
                }
                
                // Hiển thị/ẩn nút back
                if (btnBack != null) {
                    btnBack.setVisible(isSearching);
                }
                
                // Thực hiện tìm kiếm
                if (isSearching && newVal.length() >= 2 && userActionHandler != null) {
                    userActionHandler.searchUsers(newVal);
                } else {
                    // Xóa kết quả tìm kiếm khi ít ký tự
                    updateSearchResults(List.of());
                }
            });
        }
    }
    
    /**
     * Thiết lập listener cho focus của cửa sổ
     */
    private void setupFocusListener() {
        // Lấy stage để theo dõi focus
        if (rootSplit != null) {
            rootSplit.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    Stage stage = (Stage) newScene.getWindow();
                    if (stage != null) {
                        // Thêm listener focus
                        stage.focusedProperty().addListener((prop, wasFocused, isNowFocused) -> {
                            if (isNowFocused) {
                                // Ứng dụng có focus, làm mới dữ liệu
                                refreshAllData();
                            }
                        });
                    }
                }
            });
        }
    }
    
    /**
     * Làm mới tất cả dữ liệu
     */
    @FXML
    public void refreshEverything() {
        // Làm mới dữ liệu
        refreshAllData();
        showToast("Đã làm mới dữ liệu");
    }
    
    /**
     * Làm mới tất cả dữ liệu
     */
    private void refreshAllData() {
        if (clientConnection != null) {
            // Yêu cầu danh sách người dùng online
            clientConnection.requestUserList();
            
            // Yêu cầu danh sách cuộc trò chuyện (đảm bảo hiển thị tất cả cuộc trò chuyện)
            clientConnection.requestConversationList();
            
            // Yêu cầu danh sách lời mời kết bạn đang chờ
            if (currentUsername != null) {
                clientConnection.requestPendingFriendRequests(currentUsername);
            }
        }
        
        // Làm mới danh sách chat gần đây
        if (messageHandler != null) {
            // Đảm bảo messageHandler có currentUser
            if (messageHandler.getCurrentUser() == null && currentUser != null) {
                try {
                    messageHandler.setCurrentUser(currentUser);
                    System.out.println("[DEBUG] Đã thiết lập currentUser cho messageHandler");
                } catch (Exception e) {
                    System.err.println("[ERROR] Lỗi khi thiết lập currentUser cho messageHandler: " + e.getMessage());
                }
            }
            messageHandler.refreshRecentChats();
        }
        
        // Làm mới danh sách lời mời kết bạn
        if (friendshipHandler != null) {
            friendshipHandler.refreshFriendRequests();
        }
        
        // Làm mới danh sách nhóm
        if (tabPane != null && tabPane.getSelectionModel().getSelectedIndex() == 1) {
            loadGroups();
        }
    }
    
    /**
     * Hiển thị thông báo toast
     * @param message Nội dung thông báo
     */
    private void showToast(String message) {
        if (txtMessage != null && txtMessage.getScene() != null) {
            ToastNotification.show(message, txtMessage.getScene().getRoot());
        }
    }
    
    /**
     * Mở cuộc trò chuyện
     * @param chatData Dữ liệu cuộc trò chuyện
     */
    private void openConversation(RecentChatCellData chatData) {
        if (chatData == null || messageHandler == null) {
            System.err.println("[ERROR] Không thể mở cuộc trò chuyện: chatData=" + 
                (chatData == null ? "null" : chatData.chatName) + ", messageHandler=" + 
                (messageHandler == null ? "null" : "ok"));
            return;
        }
        
        try {
            // Hiển thị màn hình chat - phải gọi trước khi thiết lập chat
            showChatScreen();
            
            chatTitleLabel.setText(chatData.chatName);
            
            if ("Global".equals(chatData.chatName)) {
                // Mở chat toàn cục
                messageHandler.setCurrentTarget("Global");
                
                if (messagesContainer != null) {
                    messagesContainer.getChildren().clear();
                }
                
                messageHandler.setCurrentConversationId(null); // Reset

                // Lấy username hiện tại nếu currentUsername là null
                String localUsername = currentUsername;
                if (localUsername == null || localUsername.isBlank()) {
                    // Thử lấy từ currentUser
                    if (currentUser != null && currentUser.getUsername() != null) {
                        localUsername = currentUser.getUsername();
                        System.out.println("[INFO] Sử dụng username từ currentUser: " + localUsername);
                    } else {
                        // Thử lấy từ UserService
                        User userFromService = ServiceLocator.userService().getCurrentUser();
                        if (userFromService != null && userFromService.getUsername() != null) {
                            localUsername = userFromService.getUsername();
                            currentUsername = localUsername;
                            currentUser = userFromService;
                            System.out.println("[INFO] Sử dụng username từ UserService: " + localUsername);
                        } else {
                            showToast("Lỗi: Không thể xác định người dùng hiện tại");
                            System.err.println("[ERROR] Không thể mở Global chat: currentUsername là null");
                            return;
                        }
                    }
                }
                
                // Yêu cầu lịch sử chat toàn cục
                if (clientConnection != null && localUsername != null) {
                    clientConnection.requestHistory(localUsername, "Global");
                } else {
                    System.err.println("[ERROR] Không thể request lịch sử: clientConnection=" + 
                        (clientConnection == null ? "null" : "ok") + ", localUsername=" + localUsername);
                }
            } else if (messageHandler.isGroupTarget(chatData.chatName)) {
                // Mở chat nhóm
                openGroupChat(chatData.chatName);
            } else {
                // Mở chat riêng tư
                User user = ServiceLocator.userService().getUser(chatData.chatName);
                if (user != null) {
                    messageHandler.openPrivateConversation(user);
                } else {
                    System.err.println("[ERROR] Không thể lấy thông tin user cho: " + chatData.chatName);
                    showToast("Lỗi: Không thể tìm thấy người dùng " + chatData.chatName);
                }
            }
            
            // Đánh dấu đã đọc
            messageHandler.clearUnread(chatData.chatName);
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi mở cuộc trò chuyện: " + e.getMessage());
            e.printStackTrace();
            showToast("Lỗi khi mở cuộc trò chuyện: " + e.getMessage());
        }
    }
    
    /**
     * Mở chat nhóm
     * @param groupName Tên nhóm
     */
    private void openGroupChat(String groupName) {
        if (groupName == null || messageHandler == null) return;

        // Cập nhật UI
        chatTitleLabel.setText(groupName);
        messageHandler.setCurrentTarget(groupName);

        // Hiển thị màn hình chat
        showChatScreen();

        // Lấy groupId và join
        long groupId = messageHandler.getGroupId(groupName);
        if (groupId > 0 && clientConnection != null) {
            // Lưu conversation ID
            messageHandler.setCurrentConversationId(groupId);
            
            // Xóa tin nhắn cũ
            if (messagesContainer != null) {
                messagesContainer.getChildren().clear();
            }
            
            System.out.println("[DEBUG] Mở group chat: " + groupName + " với ID: " + groupId);
            
            try {
                // Đầu tiên join vào nhóm
                clientConnection.joinConv(groupId);
                
                // Đợi lâu hơn để đảm bảo join thành công
                PauseTransition pause = new PauseTransition(Duration.millis(800));
                pause.setOnFinished(e -> {
                    try {
                        // Sau đó request lịch sử tin nhắn
                        System.out.println("[DEBUG] Request lịch sử tin nhắn cho nhóm: " + groupName);
                        
                        // Kiểm tra lại container trước khi request lịch sử
                        if (messagesContainer != null && messagesContainer.getChildren().isEmpty()) {
                            clientConnection.requestHistory(currentUsername, groupName);
                            
                            // Thêm một thông báo tạm thời để người dùng biết đang tải
                            Label loadingLabel = new Label("Đang tải tin nhắn...");
                            loadingLabel.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
                            loadingLabel.setAlignment(Pos.CENTER);
                            loadingLabel.setMaxWidth(Double.MAX_VALUE);
                            
                            // Thêm vào container
                            Platform.runLater(() -> {
                                if (messagesContainer.getChildren().isEmpty()) {
                                    messagesContainer.getChildren().add(loadingLabel);
                                    
                                    // Tự động xóa sau 5 giây nếu không có tin nhắn nào
                                    PauseTransition removeLoading = new PauseTransition(Duration.seconds(5));
                                    removeLoading.setOnFinished(event -> {
                                        if (messagesContainer.getChildren().contains(loadingLabel)) {
                                            messagesContainer.getChildren().remove(loadingLabel);
                                        }
                                    });
                                    removeLoading.play();
                                }
                            });
                        }
                    } catch (Exception ex) {
                        System.err.println("[ERROR] Lỗi khi request lịch sử nhóm: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
                pause.play();
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi mở nhóm chat: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("[ERROR] Không tìm thấy ID cho nhóm: " + groupName);
        }

        // Đánh dấu đã đọc
        messageHandler.clearUnread(groupName);
    }
    /**
     * Cập nhật danh sách chat gần đây
     * @param chats Danh sách chat
     */
    private void updateRecentChats(List<RecentChatCellData> chats) {
        Platform.runLater(() -> {
            try {
                if (chats != null && listRecentChats != null) {
                    // Tạo danh sách mới để tránh xung đột
                    ObservableList<RecentChatCellData> newItems = FXCollections.observableArrayList();
                    if (!chats.isEmpty()) {
                        newItems.addAll(chats);
                    }
                    
                    // Cập nhật ListView với danh sách mới tạo
                    listRecentChats.setItems(newItems);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi cập nhật recent chats: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Cập nhật danh sách lời mời kết bạn
     * @param requests Danh sách lời mời
     */
    private void updateFriendRequests(List<Friendship> requests) {
        Platform.runLater(() -> {
            try {
                if (listFriendRequests != null) {
                    // Tạo danh sách mới thay vì xóa danh sách hiện tại
                    ObservableList<Friendship> newItems = FXCollections.observableArrayList();
                    if (requests != null && !requests.isEmpty()) {
                        newItems.addAll(requests);
                    }
                    
                    // Cập nhật ListView với danh sách mới
                    listFriendRequests.setItems(newItems);
                    
                    // Cập nhật badge với số lượng thực tế
                    updateFriendRequestBadge(requests != null ? requests.size() : 0);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi cập nhật friend requests: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Cập nhật kết quả tìm kiếm người dùng
     * @param users Danh sách người dùng
     */
    private void updateSearchResults(List<User> users) {
        Platform.runLater(() -> {
            try {
                if (listSearchUsers != null) {
                    // Tạo danh sách mới thay vì xóa danh sách hiện tại
                    ObservableList<User> newItems = FXCollections.observableArrayList();
                    if (users != null && !users.isEmpty()) {
                        newItems.addAll(users);
                    }
                    
                    // Cập nhật ListView với danh sách mới
                    listSearchUsers.setItems(newItems);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi cập nhật kết quả tìm kiếm: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Xử lý sự kiện khi nhấn nút gửi
     */
    @FXML
    private void onSend() {
        String content = txtMessage.getText().trim();
        if (content.isEmpty() || messageHandler == null) return;
        
        // Đảm bảo màn hình chat hiển thị
        showChatScreen();
        
        // Sử dụng MessageHandler để gửi tin nhắn
        messageHandler.sendTextMessage(content);
        
        // Xóa nội dung đã gửi
        txtMessage.clear();
    }
    
    /**
     * Xử lý sự kiện khi nhấn nút đính kèm file
     */
    @FXML
    private void onAttachFile() {
        if (fileHandler != null) {
            fileHandler.handleFileSend();
        } else {
            System.err.println("FileHandler chưa được khởi tạo trong onAttachFile");
        }
    }

    /**
     * Xử lý sự kiện khi nhấn nút đính kèm ảnh
     */
    @FXML
    private void onAttachImage() {
        if (fileHandler != null) {
            fileHandler.selectAndSendImage();
        } else {
            System.err.println("FileHandler chưa được khởi tạo trong onAttachImage");
        }
    }

    // Thêm phương thức helper
    private Conversation findPrivateConversation(String user1, String user2) {
        if (user1 == null || user2 == null) return null;

        // Sắp xếp tên theo thứ tự alphabet
        String name1 = user1.compareTo(user2) < 0 ? user1 : user2;
        String name2 = user1.compareTo(user2) < 0 ? user2 : user1;
        String convName = name1 + "|" + name2;

        // Tìm conversation
        return ServiceLocator.conversationDAO().findByName(convName);
    }
    
    /**
     * Xử lý sự kiện khi nhấn nút chọn emoji
     */
    @FXML
    private void onChooseEmoji() {
        Stage emojiStage = new Stage();
        if (btnEmoji.getScene() != null && btnEmoji.getScene().getWindow() != null) {
            emojiStage.initOwner(btnEmoji.getScene().getWindow());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Mảng ký tự Unicode để chèn vào TextField
        String[] emojis = { "😊", "😂", "👍", "🎉", "😎", "😭", "😡",
                "🍀", "🔥", "🤔", "😴" };

        // Mảng iconLiteral để hiển thị nút
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
                if (txtMessage != null) {
                    txtMessage.appendText(emojis[finalI]);
                }
                emojiStage.close();
            });

            grid.add(b, i % cols, i / cols);
        }

        Scene scene = new Scene(grid, 200, 150);
        emojiStage.setTitle("Chọn Emoji");
        emojiStage.setScene(scene);
        emojiStage.show();
    }
    
    /**
     * Mở trang cài đặt người dùng
     */
    @FXML
    public void onOpenSettings() {
        if (currentUsername == null) return;
        
        try {
            // Nạp profile.fxml
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/profile.fxml"));
            Parent root = loader.load();

            // Lấy Scene hiện tại
            Scene scene = btnSettings.getScene();
            scene.setRoot(root);

            // Lấy controller của profile.fxml, gán username
            ProfileController profileCtrl = loader.getController();
            profileCtrl.setCurrentUser(currentUsername);
        } catch (IOException e) {
            e.printStackTrace();
            showToast("Không thể mở trang cài đặt: " + e.getMessage());
        }
    }
    
    /**
     * Xử lý sự kiện khi nhấn nút quay lại
     */
    @FXML
    private void onBack() {
        // Xóa tìm kiếm
        if (searchField != null) {
            searchField.clear();
        }
        
        // Ẩn danh sách tìm kiếm
        if (listSearchUsers != null) {
            listSearchUsers.setVisible(false);
            listSearchUsers.setManaged(false);
        }
        
        // Hiển thị danh sách chat gần đây
        if (listRecentChats != null) {
            listRecentChats.setVisible(true);
            listRecentChats.setManaged(true);
        }
        
        // Ẩn nút back
        if (btnBack != null) {
            btnBack.setVisible(false);
        }
    }
    
    /**
     * Xử lý sự kiện khi nhấn nút đăng xuất
     */
    @FXML
    private void onLogout() {
        // Hiển thị hộp thoại xác nhận
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận đăng xuất");
        alert.setHeaderText("Bạn có chắc chắn muốn đăng xuất không?");
        alert.setContentText("Tất cả tin nhắn chưa gửi sẽ bị mất.");
        
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                // Đóng kết nối
                if (clientConnection != null) {
                    clientConnection.close();
                }
                
                // Chuyển về màn hình đăng nhập
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
                    Parent root = loader.load();
                    
                    if (txtMessage != null && txtMessage.getScene() != null) {
                        Scene scene = txtMessage.getScene();
                        scene.getStylesheets().clear();
                        scene.getStylesheets().addAll(
                                getClass().getResource("/css/style.css").toExternalForm(),
                                getClass().getResource("/css/auth.css").toExternalForm());
                        scene.setRoot(root);
                    }
                } catch (IOException e) {
                    showToast("Lỗi khi chuyển màn hình: " + e.getMessage());
                }
            } catch (Exception e) {
                showToast("Lỗi khi đăng xuất: " + e.getMessage());
            }
        }
    }
    
    /**
     * Xử lý sự kiện khi nhấn nút tạo nhóm
     */
    @FXML
    private void onCreateGroup() {
        if (clientConnection == null || currentUsername == null) {
            showToast("Không thể tạo nhóm: Lỗi kết nối");
            return;
        }

        // Bước 1: Hiển thị dialog nhập tên nhóm
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Tạo nhóm chat mới");
        nameDialog.setHeaderText("Nhập tên nhóm chat");
        nameDialog.setContentText("Tên nhóm:");
        
        Optional<String> nameResult = nameDialog.showAndWait();
        if (!nameResult.isPresent() || nameResult.get().trim().isEmpty()) {
            return; // Người dùng đã hủy hoặc không nhập tên
        }
        
        String groupName = nameResult.get().trim();
        
        // Bước 2: Lấy danh sách người dùng online để chọn thành viên
        try {
            // Yêu cầu danh sách người dùng mới nhất
            clientConnection.requestUserList();
            
            // Lấy danh sách từ UserService
            List<User> allUsers = ServiceLocator.userService().searchUsers(""); // Tìm tất cả
            
            // Nếu không có kết quả, thử lấy trực tiếp từ UserDAO
            if (allUsers == null || allUsers.isEmpty()) {
                try {
                    app.dao.UserDAO userDAO = new app.dao.UserDAO();
                    allUsers = userDAO.findAll();
                    System.out.println("Lấy người dùng từ UserDAO: " + allUsers.size());
                } catch (Exception ex) {
                    System.err.println("Không thể lấy danh sách người dùng: " + ex.getMessage());
                    ex.printStackTrace();
                    allUsers = new ArrayList<>();
                }
            }
            
            // Lọc bỏ người dùng hiện tại
            List<User> otherUsers = allUsers.stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .collect(Collectors.toList());
            
            if (otherUsers.isEmpty()) {
                showToast("Không có người dùng khác để thêm vào nhóm");
                
                // Vẫn tạo nhóm chỉ với mình
                List<String> members = new ArrayList<>();
                members.add(currentUsername);
                clientConnection.createGroup(groupName, members);
                showToast("Đang tạo nhóm " + groupName + " (chỉ có bạn)...");
                return;
            }
            
            // Bước 3: Hiển thị dialog chọn thành viên
            Dialog<List<User>> memberDialog = new Dialog<>();
            memberDialog.setTitle("Chọn thành viên nhóm");
            memberDialog.setHeaderText("Chọn thành viên cho nhóm " + groupName);
            
            // Set the button types
            ButtonType selectButtonType = new ButtonType("Chọn", ButtonBar.ButtonData.OK_DONE);
            memberDialog.getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);
            
            // Create a ListView for user selection with checkboxes
            VBox content = new VBox(10);
            content.setPadding(new Insets(20, 10, 10, 10));
            
            Label instructionLabel = new Label("Chọn thành viên để thêm vào nhóm:");
            ListView<User> userListView = new ListView<>();
            userListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            userListView.getItems().addAll(otherUsers);
            
            // Custom cell factory với tên người dùng
            userListView.setCellFactory(lv -> new ListCell<User>() {
                @Override
                protected void updateItem(User user, boolean empty) {
                    super.updateItem(user, empty);
                    if (empty || user == null) {
                        setText(null);
                    } else {
                        if (user.getFullName() != null && !user.getFullName().isEmpty()) {
                            setText(user.getFullName() + " (" + user.getUsername() + ")");
                        } else {
                            setText(user.getUsername());
                        }
                    }
                }
            });
            
            content.getChildren().addAll(instructionLabel, userListView);
            memberDialog.getDialogPane().setContent(content);
            
            // Convert the result when the select button is clicked
            memberDialog.setResultConverter(dialogButton -> {
                if (dialogButton == selectButtonType) {
                    return new ArrayList<>(userListView.getSelectionModel().getSelectedItems());
                }
                return null;
            });
            
            // Show the dialog and process the result
            Optional<List<User>> membersResult = memberDialog.showAndWait();
            if (membersResult.isPresent()) {
                List<User> selectedUsers = membersResult.get();
                
                // Tạo danh sách usernames cho API
                List<String> memberUsernames = new ArrayList<>();
                memberUsernames.add(currentUsername); // Luôn thêm người tạo nhóm
                
                // Thêm các thành viên được chọn
                for (User user : selectedUsers) {
                    memberUsernames.add(user.getUsername());
                }
                
                // Bước 4: Gửi yêu cầu tạo nhóm
                clientConnection.createGroup(groupName, memberUsernames);
                
                showToast("Đang tạo nhóm " + groupName + " với " + memberUsernames.size() + " thành viên...");
            }
        } catch (Exception e) {
            showToast("Không thể tạo nhóm: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Triển khai FriendRequestCellFactory.FriendRequestHandler
    
    @Override
    public void onAcceptFriendRequest(Friendship friendship) {
        if (friendshipHandler != null) {
            friendshipHandler.acceptFriendRequest(friendship);
        }
    }
    
    @Override
    public void onRejectFriendRequest(Friendship friendship) {
        if (friendshipHandler != null) {
            friendshipHandler.rejectFriendRequest(friendship);
        }
    }
    
    @Override
    public void onCancelFriendRequest(Friendship friendship) {
        if (friendshipHandler != null) {
            friendshipHandler.cancelFriendRequest(friendship);
        }
    }
    
    // Triển khai SearchUserCellFactory.UserActionHandler
    
    @Override
    public void onAddFriend(User user) {
        // Kiểm tra xem currentUsername có bị null không
        if (currentUsername == null) {
            System.out.println("[WARN] currentUsername is null in onAddFriend, attempting to fix");
            
            // Thử lấy tên người dùng hiện tại từ nhiều nguồn
            if (currentUser != null) {
                currentUsername = currentUser.getUsername();
                System.out.println("[INFO] Retrieved username from currentUser: " + currentUsername);
            } else {
                // Thử lấy từ ServiceLocator
                User userFromService = ServiceLocator.userService().getCurrentUser();
                if (userFromService != null) {
                    currentUsername = userFromService.getUsername();
                    currentUser = userFromService;
                    System.out.println("[INFO] Retrieved username from UserService: " + currentUsername);
                } else {
                    // Nếu vẫn không có, hiển thị lỗi
                    showToast("Lỗi: Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
                    System.err.println("[ERROR] Cannot retrieve current username from any source");
                    return;
                }
            }
            
            // Khởi tạo lại handlers với username đã lấy được
            initializeHandlers();
        }
        
        if (friendshipHandler != null) {
            System.out.println("[DEBUG] Sending friend request from " + currentUsername + " to " + user.getUsername());
            friendshipHandler.sendFriendRequest(user);
        } else {
            showToast("Lỗi: Không thể gửi lời mời kết bạn do thiếu handler");
        }
    }
    
    @Override
    public void onMessage(User user) {
        if (messageHandler != null) {
            messageHandler.openPrivateConversation(user);
            showChatScreen();
        }
    }
    
    @Override
    public Friendship.Status getFriendshipStatus(User currentUser, User targetUser) {
        return friendshipHandler != null ? 
            friendshipHandler.getFriendshipStatus(currentUser, targetUser) : null;
    }
    
    @Override
    public String getCurrentUsername() {
        return currentUsername;
    }
    
    /**
     * Đặt người dùng hiện tại và cập nhật UI
     * Được gọi khi đăng nhập thành công
     * @param username Tên người dùng
     */
    public void setCurrentUser(String username) {
        this.currentUsername = username;
        
        if (currentUserLabel != null) {
            currentUserLabel.setText(username);
        }
        
        // Lấy thông tin user từ service
        this.currentUser = ServiceLocator.userService().getUser(username);
        
        // Đảm bảo liên kết với ChatService và kết nối client
        ChatService service = ServiceLocator.chat();
        service.bindRefactoredUI(this);
        this.chatService = service;
        this.clientConnection = service.getClient();
        
        System.out.println("Thiết lập người dùng hiện tại: " + username + 
            ", chatService=" + (chatService != null) + 
            ", clientConnection=" + (clientConnection != null));
        
        // Khởi tạo lại các handler với user mới
        initializeHandlers();
        final boolean [] avatarLoaded = {false};
        
        // Cập nhật avatar người dùng
        if (currentUser != null && userAvatarCircle != null && userInitialLabel != null) {
            System.out.println("[AVATAR DEBUG] Cập nhật avatar cho người dùng " + username);
            System.out.println("[AVATAR DEBUG] useDefaultAvatar: " + currentUser.isUseDefaultAvatar());
            System.out.println("[AVATAR DEBUG] avatarPath: " + currentUser.getAvatarPath());
            
            // Thiết lập đường dẫn avatar chuẩn
            String standardPath = app.util.PathUtil.getStandardAvatarPath(username);
            File standardFile = new File(standardPath);
            
            if (standardFile.exists()) {
                // Nếu có file chuẩn, cập nhật DB và sử dụng file chuẩn
                currentUser.setAvatarPath(standardPath);
                currentUser.setUseDefaultAvatar(false);
                ServiceLocator.userService().updateUserAvatar(currentUser);
                
                // Hiển thị ảnh từ file
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(standardFile.toURI().toString(), 
                                                                            userAvatarCircle.getRadius() * 2,
                                                                            userAvatarCircle.getRadius() * 2,
                                                                            true, true);
                    if (!img.isError()) {
                        userAvatarCircle.setFill(new javafx.scene.paint.ImagePattern(img));
                        userInitialLabel.setVisible(false);
                        avatarLoaded[0] = true;
                        System.out.println("[AVATAR DEBUG] Successfully loaded avatar from file: " + standardPath);
                    } else {
                        // Nếu không thể tải ảnh, sử dụng avatar mặc định
                        setDefaultAvatar(username);
                    }
                } catch (Exception e) {
                    System.out.println("[AVATAR ERROR] Error loading avatar: " + e.getMessage());
                    setDefaultAvatar(username);
                }
            } else {
                // Kiểm tra trong cache
                File cacheFile = new File(app.util.PathUtil.getAvatarCacheDirectory() + "/" + username + ".png");
                if (cacheFile.exists()) {
                    try {
                        javafx.scene.image.Image img = new javafx.scene.image.Image(cacheFile.toURI().toString(), 
                                                                                userAvatarCircle.getRadius() * 2, 
                                                                                userAvatarCircle.getRadius() * 2, 
                                                                                true, true);
                        if (!img.isError()) {
                            userAvatarCircle.setFill(new javafx.scene.paint.ImagePattern(img));
                            userInitialLabel.setVisible(false);
                            System.out.println("[AVATAR DEBUG] Successfully loaded avatar from cache: " + cacheFile.getPath());
                        } else {
                            setDefaultAvatar(username);
                        }
                    } catch (Exception e) {
                        System.out.println("[AVATAR ERROR] Error loading avatar from cache: " + e.getMessage());
                        setDefaultAvatar(username);
                    }
                } else {
                    // Nếu không có file trong cache, hiển thị avatar mặc định
                    setDefaultAvatar(username);
                    
                    // Yêu cầu avatar từ server
                    if (clientConnection != null) {
                        System.out.println("[AVATAR DEBUG] Yêu cầu avatar từ server cho " + username);
                        clientConnection.requestAvatar(username);
                    }
                }
            }
        }
        
        // Thiết lập lại cell factory cho search users (cần user hiện tại)
        if (listSearchUsers != null && currentUser != null) {
            listSearchUsers.setCellFactory(new SearchUserCellFactory(this, currentUser));
        }
        
        // Thiết lập lại callbacks sau khi đã có đủ handlers
        setupCallbacks();
        
        // Hiện màn hình chào mừng
        showWelcomeScreen();
        
        // Cập nhật handlers với scene sau khi UI đã được thiết lập
        Platform.runLater(() -> {
            updateHandlersWithScene();
            setupNotificationBadges();

            // Tạo Global Chat mặc định nếu chưa có trong danh sách
            addGlobalChatIfNeeded();
            
            // Đảm bảo messageHandler có currentUser trước khi gọi refreshRecentChats
            if (messageHandler != null && messageHandler.getCurrentUser() == null) {
                messageHandler.setCurrentUser(currentUser);
            }
            
            // Làm mới dữ liệu ngay lập tức
            refreshAllData();
            
            // Đảm bảo tải danh sách cuộc trò chuyện trước
            if (clientConnection != null) {
                clientConnection.requestConversationList();
                
                // Đợi một chút để cho server xử lý và phản hồi
                PauseTransition delay = new PauseTransition(Duration.millis(300));
                delay.setOnFinished(e -> {
                    // Yêu cầu lịch sử tin nhắn Global Chat
                    clientConnection.requestHistory(username, "Global");
                
                    // Yêu cầu pending friend requests
                    clientConnection.requestPendingFriendRequests(username);
                });
                delay.play();
            }
            
            // Hiển thị thông báo chào mừng
            showToast("Chào mừng " + username + "!");
            
            // Tải và hiển thị danh sách các nhóm
            loadGroups();
        });
        setupAvatarProtection();

    }
    private void setupAvatarProtection() {
        // Tạo một listener để đảm bảo avatar không bị ghi đè
        userAvatarCircle.fillProperty().addListener((obs, oldFill, newFill) -> {
            if (newFill instanceof javafx.scene.paint.Color && !(oldFill instanceof javafx.scene.paint.Color)) {
                // Nếu đang thay đổi từ ImagePattern sang Color, tức là đang ghi đè bằng màu mặc định
                System.out.println("[AVATAR PROTECTION] Ngăn chặn việc ghi đè avatar bằng màu mặc định");

                // Kiểm tra một lần nữa và khôi phục avatar từ file nếu có
                String path = app.util.PathUtil.getStandardAvatarPath(currentUsername);
                File avatarFile = new File(path);

                if (avatarFile.exists()) {
                    try {
                        javafx.scene.image.Image img = new javafx.scene.image.Image(
                                avatarFile.toURI().toString(),
                                userAvatarCircle.getRadius() * 2,
                                userAvatarCircle.getRadius() * 2,
                                true, true
                        );

                        if (!img.isError()) {
                            Platform.runLater(() -> {
                                userAvatarCircle.setFill(new javafx.scene.paint.ImagePattern(img));
                                userInitialLabel.setVisible(false);
                            });
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Không thể khôi phục avatar: " + e.getMessage());
                    }
                }
            }
        });
    }
    private void setupNotificationBadges() {
        // Tìm tab lời mời kết bạn
        Tab friendRequestTab = null;
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().contains("Lời mời")) {
                friendRequestTab = tab;
                break;
            }
        }

        if (friendRequestTab != null) {
            // Tạo custom header cho tab với badge
            HBox tabHeader = new HBox(5);
            tabHeader.setAlignment(Pos.CENTER);

            Label tabLabel = new Label("Lời mời");

            // Tạo badge
            friendRequestBadge = new Label();
            friendRequestBadge.getStyleClass().add("tab-badge");
            friendRequestBadge.setVisible(false);
            friendRequestBadge.setManaged(false);

            // Stack để overlay badge
            StackPane badgeContainer = new StackPane();
            badgeContainer.getChildren().addAll(tabLabel, friendRequestBadge);
            StackPane.setAlignment(friendRequestBadge, Pos.TOP_RIGHT);
            StackPane.setMargin(friendRequestBadge, new Insets(-5, -5, 0, 0));

            tabHeader.getChildren().add(badgeContainer);
            friendRequestTab.setGraphic(tabHeader);
            friendRequestTab.setText(""); // Xóa text cũ
        }
    }

    // Phương thức cập nhật badge
    private void updateFriendRequestBadge(int count) {
        Platform.runLater(() -> {
            pendingFriendRequestCount = count;

            if (friendRequestBadge != null) {
                if (count > 0) {
                    friendRequestBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                    friendRequestBadge.setVisible(true);
                    friendRequestBadge.setManaged(true);

                    // Thêm animation pulse cho badge mới
                    friendRequestBadge.getStyleClass().add("new");

                    // Xóa animation sau 1.5s
                    PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                    pause.setOnFinished(e -> friendRequestBadge.getStyleClass().remove("new"));
                    pause.play();
                } else {
                    friendRequestBadge.setVisible(false);
                    friendRequestBadge.setManaged(false);
                }
            }
        });
    }
    
    /**
     * Đặt avatar mặc định cho người dùng hiện tại
     */
    private void setDefaultAvatar(String username) {
        // Màu avatar mặc định
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
        
        // Chọn màu dựa trên username
        int colorIndex = Math.abs(username.hashCode() % COLORS.length);
        userAvatarCircle.setFill(COLORS[colorIndex]);
        
        // Hiển thị chữ cái đầu
        userInitialLabel.setText(username.substring(0, 1).toUpperCase());
        userInitialLabel.setVisible(true);
    }
    private void showFriendRequestNotification(String fromUser) {
        // Tạo notification popup
        VBox notificationBox = new VBox(10);
        notificationBox.getStyleClass().add("friend-request-notification");
        notificationBox.setPadding(new Insets(15));

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon("fas-user-plus");
        icon.setIconSize(20);
        icon.setIconColor(Color.web("#2196F3"));

        Label title = new Label("Lời mời kết bạn mới");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        header.getChildren().addAll(icon, title);

        // Content
        Label message = new Label(fromUser + " muốn kết bạn với bạn");
        message.setWrapText(true);

        // Buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button viewBtn = new Button("Xem");
        viewBtn.getStyleClass().add("btn-primary");
        viewBtn.setOnAction(e -> {
            // Chuyển đến tab lời mời
            tabPane.getSelectionModel().select(2);
            closeNotification(notificationBox);
        });

        Button dismissBtn = new Button("Để sau");
        dismissBtn.getStyleClass().add("btn-secondary");
        dismissBtn.setOnAction(e -> closeNotification(notificationBox));

        buttons.getChildren().addAll(dismissBtn, viewBtn);

        notificationBox.getChildren().addAll(header, message, buttons);

        // Hiển thị notification
        showNotificationPopup(notificationBox);
    }

    private void showNotificationPopup(VBox content) {
        // Lấy root pane
        StackPane root = (StackPane) rootSplit.getScene().getRoot();

        // Wrapper với shadow
        VBox wrapper = new VBox(content);
        wrapper.setStyle("-fx-background-color: white; " +
                "-fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");
        wrapper.setMaxWidth(300);

        // Position
        StackPane.setAlignment(wrapper, Pos.TOP_RIGHT);
        StackPane.setMargin(wrapper, new Insets(60, 20, 0, 0));

        // Animation slide in
        wrapper.setTranslateX(320);
        root.getChildren().add(wrapper);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), wrapper);
        slideIn.setToX(0);
        slideIn.play();

        // Auto close sau 5s
        PauseTransition autoClose = new PauseTransition(Duration.seconds(5));
        autoClose.setOnFinished(e -> closeNotification(wrapper));
        autoClose.play();
    }

    private void closeNotification(Node notification) {
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), notification);
        slideOut.setToX(320);
        slideOut.setOnFinished(e -> {
            ((Pane) notification.getParent()).getChildren().remove(notification);
        });
        slideOut.play();
    }
    /**
     * Tải danh sách các nhóm mà người dùng tham gia
     */
    private void loadGroups() {
        if (currentUser == null || listGroups == null) return;

        try {
            // Tải các nhóm của user từ conversationDAO
            Platform.runLater(() -> {
                try {
                    // Lấy tất cả nhóm của user
                    List<Conversation> groupConversations = ServiceLocator.conversationDAO()
                            .findAllOfUserByType(currentUser.getUsername(), "GROUP");
                    
                    List<String> groupNames = new ArrayList<>();
                    if (groupConversations != null) {
                        for (Conversation conv : groupConversations) {
                            if (!conv.getName().equals("Global Chat")) {
                                groupNames.add(conv.getName());
                                
                                // Cập nhật groupMap
                                if (messageHandler != null) {
                                    messageHandler.updateGroupMap(conv.getName(), conv.getId());
                                }
                            }
                        }
                    }
                    
                    // Lưu lại lựa chọn hiện tại nếu có
                    int selectedIndex = listGroups.getSelectionModel().getSelectedIndex();
                    String selectedItem = selectedIndex >= 0 && selectedIndex < listGroups.getItems().size() 
                            ? listGroups.getItems().get(selectedIndex) : null;
                    
                    // Tạo danh sách mới để cập nhật ListView
                    ObservableList<String> newItems = FXCollections.observableArrayList();
                    if (!groupNames.isEmpty()) {
                        newItems.addAll(groupNames);
                    }
                    
                    // Tạm thời tắt lắng nghe sự kiện selection
                    SelectionModel<String> selectionModel = listGroups.getSelectionModel();
                    selectionModel.clearSelection();
                    
                    // Cập nhật ListView với danh sách mới
                    listGroups.setItems(newItems);
                    
                    // Đảm bảo Global chat được thêm vào
                    addGlobalChatIfNeeded();
                    
                    // Không cần khôi phục lựa chọn vì chúng ta đã xử lý click thông qua cell factory
                } catch (Exception e) {
                    System.err.println("[ERROR] Lỗi khi tải danh sách nhóm: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi tải danh sách nhóm: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Thêm Global Chat vào danh sách nếu cần
     */
    private void addGlobalChatIfNeeded() {
        try {
            if (listRecentChats == null || messageHandler == null) return;
            
            // Kiểm tra xem có Global Chat trong danh sách chưa
            boolean hasGlobal = false;
            for (RecentChatCellData chat : listRecentChats.getItems()) {
                if ("Global".equals(chat.chatName)) {
                    hasGlobal = true;
                    break;
                }
            }
            
            // Nếu chưa có, thêm vào
            if (!hasGlobal) {
                System.out.println("Thêm Global Chat vào danh sách chat gần đây");
                RecentChatCellData globalChat = new RecentChatCellData(
                    "Global",
                    "Chat toàn cầu",
                    "",
                    null,
                    0
                );
                
                Platform.runLater(() -> {
                    listRecentChats.getItems().add(0, globalChat);
                });
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi thêm Global Chat: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Lấy message handler
     * @return MessageHandler
     */
    public MessageHandler getMessageHandler() {
        return messageHandler;
    }
    
    /**
     * Lấy file handler
     * @return FileHandler
     */
    public FileHandler getFileHandler() {
        return fileHandler;
    }

    /**
     * Highlight tab lời mời kết bạn để thông báo cho người dùng
     */
    private void highlightFriendRequestTab() {
        // Tìm tab lời mời
        for (Tab tab : tabPane.getTabs()) {
            Node graphic = tab.getGraphic();
            if (graphic instanceof HBox && ((HBox) graphic).getChildren().stream()
                    .anyMatch(node -> node instanceof StackPane)) {
                // Thêm style class để highlight
                tab.getStyleClass().add("tab-notification");

                // Animation nhấp nháy
                FadeTransition fade = new FadeTransition(Duration.millis(500), tab.getGraphic());
                fade.setFromValue(1.0);
                fade.setToValue(0.3);
                fade.setCycleCount(6);
                fade.setAutoReverse(true);
                fade.play();

                break;
            }
        }
    }
}