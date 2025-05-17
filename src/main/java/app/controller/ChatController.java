package app.controller;

import app.LocalDateTimeAdapter;
import app.service.ChatService;
import app.service.UserService;
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
import network.ClientConnection;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.paint.Color;
import com.gluonhq.emoji.util.TextUtils;
import com.gluonhq.emoji.Emoji;
import javafx.scene.image.ImageView;

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
    private Button btnAttachFile, btnEmoji, btnSend, btnSettings, btnBack;

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
    private String lastPmTarget;

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
        // 1) Gọi service bind UI (nếu bạn cần)
        ServiceLocator.chat().bindUI(this);

        // 2) Lấy clientConnection từ ChatService
        this.clientConnection = ServiceLocator.chat().getClient();

        // 3) Kiểm tra null
        if (this.clientConnection == null) {
            System.out.println("ChatController: clientConnection == null trong initialize()!");
            return;
        }
        System.out.println("[DEBUG] searchField = " + searchField);


        clientConnection.setOnTextReceived((from, content) -> {
            if (!"Global".equals(currentTarget)) return;          // ② LỌC
            Platform.runLater(() -> {
                if (from == null) return; // Thêm kiểm tra null
                boolean out = from.equals(getCurrentUser());
                displayMessage(from, content, out, LocalDateTime.now());
            });
        });

        // callback khi server xác nhận Join
        clientConnection.setOnConvJoined(cid -> joinedConv.put(cid, true));

        listGroups.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) return;

                    currentTarget = newVal;               // gán target = tên nhóm
                    chatTitleLabel.setText(newVal);       // Cập nhật tiêu đề chat
                    long cid = groupMap.get(newVal);

                    clientConnection.joinConv(cid);       // yêu cầu SERVER gửi HISTORY
                    listGroups.refresh();
                });

        clientConnection.setOnHistory((convId, json) -> {
            var msgList = parseJsonToMessageDTO(json);
            Platform.runLater(() -> {
                messagesContainer.getChildren().clear();
                for (var m : msgList) {
                    boolean out = m.getUser().equals(getCurrentUser());
                    /* gọi 1 hàm duy nhất, hàm tự nhận dạng [FILE] */
                    displayMessage(m.getUser(), m.getContent(), out, m.getTime());   // ➋
                }
            });
        });

        rootSplit.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;

            newScene.windowProperty().addListener((o, oldWin, newWin) -> {
                if (newWin != null) {                      // Stage đã có
                    ((Stage) newWin).setOnCloseRequest(ev ->
                            ServiceLocator.chat().shutdown());
                }
            });
        });

        clientConnection.setOnConvList(json -> {
            // 1) parse JSON nhận từ server
            List<Map<String, Object>> list = new Gson().fromJson(
                    json, new com.google.gson.reflect.TypeToken<
                            List<Map<String, Object>>>() {
                    }.getType());

            Platform.runLater(() -> {
                // 2) xoá danh sách cũ
                listGroups.getItems().clear();
                groupMap.clear();

                // 3) duyệt tất cả conversation
                for (Map<String, Object> c : list) {
                    String type = (String) c.get("type");
                    if (!"GROUP".equals(type)) continue;          // chỉ quan tâm GROUP

                    String name = (String) c.get("name");
                    Long id = ((Number) c.get("id")).longValue();

                    // 3a. cập nhật UI tab Group
                    listGroups.getItems().add(name);
                    // 3b. lưu vào groupMap cho send/receive
                    groupMap.put(name, id);
                }
                listGroups.refresh();
            });
        });

        clientConnection.setOnGroupMsg((convId, from, content) -> {
            // chỉ hiện nếu màn hình đang mở đúng group
            if (!groupMap.containsKey(currentTarget)
                    || groupMap.get(currentTarget) != convId) return;

            boolean isOutgoing = from.equals(getCurrentUser());
            Platform.runLater(() ->
                    displayMessage(from, content, isOutgoing, LocalDateTime.now()));
        });

        clientConnection.setOnPrivateMsgReceived((from, content) -> {
            boolean out = from.equals(getCurrentUser());

            // ③ LỌC
            if (out) {                         // tin do chính bạn gửi
                if (!currentTarget.equals(lastPmTarget)) return;
            } else {                           // tin người khác gửi
                if (!currentTarget.equals(from)) return;
            }

            Platform.runLater(() ->
                    displayMessage(from, content, out, LocalDateTime.now()));
        });

        txtMessage.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                onSend();
            }
        });

        messagesContainer.setFillWidth(true);
        scrollPane.setFitToWidth(true);
        scrollPane.vvalueProperty().bind(messagesContainer.heightProperty());

        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3a3a3a;");

        // Thiết lập callback cho sự kiện avatar được cập nhật
        clientConnection.setOnAvatarUpdated((username, avatarData) -> {
            Platform.runLater(() -> {
                try {
                    // Cập nhật thông tin user trong onlineUsers map
                    User user = ServiceLocator.userService().getUser(username);
                    if (user != null) {
                        onlineUsers.put(username, user);
                    }

                    // Nếu là current user, cập nhật avatar trong header
                    if (username.equals(currentUser)) {
                        updateUserAvatar();
                    }

                    // Refresh danh sách để cập nhật avatar trong list online users
                    listRecentChats.refresh();
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Không thể cập nhật avatar", e);
                }
            });
        });
        // Xử lý click vào user trong listSearchUsers
        listSearchUsers.setOnMouseClicked(ev -> {

            /* Bỏ qua click nếu nó nằm bên trong Button (hoặc con của ButtonBase) */
            Node node = ev.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof ListCell)) {
                /* Button, Label, Region, Text bên trong Button đều kế thừa ButtonBase/Labeled */
                if (node instanceof ButtonBase || node instanceof Labeled) return;
                node = node.getParent();
            }

            /* ---------- phần mở chat khi click row ---------- */
            User selected = listSearchUsers.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            User me = ServiceLocator.userService().getUser(currentUser);
            Friendship.Status st =
                    ServiceLocator.friendship().getFriendshipStatus(me, selected);

            if (st == Friendship.Status.ACCEPTED) {
                openPrivateConversation(selected);
            } else {
                showWarn("Bạn cần kết bạn với người này để bắt đầu trò chuyện.");
            }
        });


        clientConnection.setOnPendingListReceived(json -> {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class,
                            new LocalDateTimeAdapter())   // ← dùng lại adapter
                    .create();

            Type type = new TypeToken<List<Friendship>>(){}.getType();
            List<Friendship> list = gson.fromJson(json, type);

            updateFriendRequests(list);   // hàm đã viết
        });



        tabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {
                    if (newTab.getText().equals("Lời mời")) {
                        refreshFriendRequests();
                    }
                });


        // Khi searchField rỗng hoặc không focus, hiển thị recentChats, ẩn searchUsers
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSearching = newVal != null && !newVal.isBlank();
            listRecentChats.setVisible(!isSearching);
            listRecentChats.setManaged(!isSearching);
            listSearchUsers.setVisible(isSearching);
            listSearchUsers.setManaged(isSearching);
            if (isSearching) {
                CompletableFuture.runAsync(() -> {
                    List<User> found = ServiceLocator.userService().searchUsers(newVal);
                    System.out.println("[DEBUG] search '" + newVal + "' => " + found.size() + " users");

                    Platform.runLater(() -> {
                        listSearchUsers.getItems().setAll(found);
                    });
                });
            }
        });
        // Khi mất focus, quay lại recentChats
        searchField.focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) {
                listRecentChats.setVisible(true);
                listRecentChats.setManaged(true);
                listSearchUsers.setVisible(false);
                listSearchUsers.setManaged(false);
            }
        });
        // Load recent chats khi khởi động
        loadRecentChats();

        // Khi vào tab Lời mời, gửi yêu cầu lấy danh sách lời mời lên server
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs,o,t)->{
            if ("Lời mời".equals(t.getText())) {
                clientConnection.requestPendingFriendRequests(currentUser);
            }
        });

        // Custom cell cho listFriendRequests
        listSearchUsers.setCellFactory(lv -> new ListCell<User>() {

            private final Circle avatarCircle = new Circle(15);
            private final Label initialLabel = new Label();
            private final Label nameLabel = new Label();
            private final Button addFriendBtn = new Button("Kết bạn");
            private final HBox hbox = new HBox(
                    10,
                    new StackPane(avatarCircle, initialLabel),
                    nameLabel,
                    addFriendBtn
            );




            {   /*––– KHỞI-TẠO: chạy MỘT lần cho cell –––*/
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.setPadding(new Insets(5, 10, 5, 10));
                hbox.setPickOnBounds(false);   // ← THÊM

                avatarCircle.getStyleClass().add("search-user-avatar");
                nameLabel.getStyleClass().add("search-user-name");
                addFriendBtn.getStyleClass().add("add-friend-btn");

                /* gắn handler duy nhất */
                addFriendBtn.setOnAction(evt -> {
                    User target = getItem();          // user hiện tại của cell
                    if (target == null) return;

                    System.out.println("[DEBUG] Click Kết bạn → " + target.getUsername());

                    if (clientConnection == null) {
                        showError("Chưa kết nối server – không gửi được!", null);
                        return;
                    }

                    Alert cf = new Alert(Alert.AlertType.CONFIRMATION,
                            "Gửi lời mời kết bạn tới "
                                    + (target.getFullName() != null ? target.getFullName()
                                    : target.getUsername()) + "?",
                            ButtonType.OK, ButtonType.CANCEL);

                    if (cf.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        clientConnection.sendFriendRequest(currentUser,
                                target.getUsername());
                        System.out.println("[DEBUG] Packet FRIEND_REQUEST đã gửi");

                        addFriendBtn.setText("Đã gửi");
                        addFriendBtn.setDisable(true);
                        addFriendBtn.getStyleClass().add("friend-badge");
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

                /* reset nút mỗi lần cell tái dụng */
                addFriendBtn.setText("Kết bạn");
                addFriendBtn.setDisable(false);
                addFriendBtn.getStyleClass().remove("friend-badge");

                nameLabel.setText(user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername());

                if (!user.isUseDefaultAvatar() && user.getAvatarPath() != null) {
                    File f = new File(user.getAvatarPath());
                    if (f.exists()) {
                        avatarCircle.setFill(
                                new ImagePattern(new Image(f.toURI().toString(), false)));
                        initialLabel.setVisible(false);
                    } else setDefaultAvatar(user.getUsername());
                } else setDefaultAvatar(user.getUsername());

                setGraphic(hbox);
            }

            private void setDefaultAvatar(String username) {
                int c = Math.abs(username.hashCode() % AVATAR_COLORS.length);
                avatarCircle.setFill(AVATAR_COLORS[c]);
                initialLabel.setText(username.substring(0, 1).toUpperCase());
                initialLabel.setVisible(true);
            }
        });
        refreshFriendRequests();       // thêm ở cuối

    }

        private void loadRecentChats() {
        CompletableFuture.runAsync(() -> {
            // Lấy danh sách bạn bè và nhóm, sắp xếp theo lastMessageAt (giả sử có hàm ServiceLocator.messageService().getRecentChats())
            List<RecentChatCellData> data = ServiceLocator.messageService().getRecentChats(ServiceLocator.userService().getCurrentUser());
            Platform.runLater(() -> {
                listRecentChats.getItems().setAll(data);
            });
        });
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

        // Nếu có conversation cứng, lấy tin nhắn cũ
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
    }

    public String getCurrentUser() {
        return currentUser;
    }

    @FXML private void onCreateGroup() {
        /* BƯỚC 1: nhập tên nhóm */
        TextInputDialog nameDlg = new TextInputDialog();
        nameDlg.setHeaderText("Tên nhóm:");
        String gName = nameDlg.showAndWait().orElse(null);
        if (gName == null || gName.isBlank()) return;

        /* BƯỚC 2: chọn thành viên */
        ListView<CheckBox> lv = new ListView<>();
        // Lấy danh sách bạn bè từ recentChats
        for (RecentChatCellData chat : listRecentChats.getItems()) {
            String u = chat.chatName;
            if (u.equals("Global") || u.equals(getCurrentUser())) continue;
            lv.getItems().add(new CheckBox(u));
        }
        Dialog<java.util.List<String>> dlg = new Dialog<>();
        dlg.setTitle("Chọn thành viên");
        dlg.getDialogPane().setContent(lv);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                return lv.getItems().stream()
                        .filter(CheckBox::isSelected)
                        .map(CheckBox::getText)
                        .collect(Collectors.toList());
            }
            return null;
        });
        java.util.List<String> members = dlg.showAndWait().orElse(null);
        if (members == null) return;

        /* BƯỚC 3: gửi packet CREATE_GROUP */
        clientConnection.createGroup(gName, members);
    }

    @FXML
    private void onSend() {
        String content = txtMessage.getText().trim();
        if (!content.isEmpty()) {
            if ("Global".equals(currentTarget)) {
                // Gửi tin broadcast
                clientConnection.sendText(content);
            }
            else if (groupMap.containsKey(currentTarget)) {
                long gid = groupMap.get(currentTarget);

                // ① chạy network IO ngoài FX thread
                new Thread(() -> {
                    try { clientConnection.sendGroup(gid, content); }
                    catch (Exception ex) {                       // log & báo
                        Platform.runLater(() -> showError("SendGroup", ex));
                    }
                }).start();
                lastPmTarget = currentTarget;                    // vẫn ghi nhớ
            }
            else {
                // Gửi tin PM
                clientConnection.sendPrivate(currentTarget, content);
                lastPmTarget = currentTarget;            // ① GHI NHỚ
            }
            txtMessage.clear();
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

    public void displayIncomingMessage(String from, String content) {
        HBox bubbleBox = new HBox(5);
        bubbleBox.setMaxWidth(400);
        bubbleBox.setStyle("-fx-alignment: CENTER_LEFT;");

        Label lbl = new Label(from + ": " + content);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: white; "
                + "-fx-padding: 8; -fx-background-radius: 8;");

        bubbleBox.getChildren().add(lbl);
        messagesContainer.getChildren().add(bubbleBox);
    }

    public void displayOutgoingMessage(String from, String content) {
        HBox bubbleBox = new HBox(5);
        bubbleBox.setMaxWidth(400);
        bubbleBox.setStyle("-fx-alignment: CENTER_RIGHT;");

        Label lbl = new Label(content);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-background-color: #0078fe; -fx-text-fill: white; "
                + "-fx-padding: 8; -fx-background-radius: 8;");

        bubbleBox.getChildren().add(lbl);
        messagesContainer.getChildren().add(bubbleBox);
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
            // Ví dụ: "[FILE]filename.txt|1024|id" -> lấy filename, size và id
            String fileInfo = content.substring(6); // bỏ "[FILE]"
            String[] parts = fileInfo.split("\\|",3);
            System.out.println("-> parse [FILE] len="+parts.length+" : "+ Arrays.toString(parts));

            if(parts.length < 3){          // thiếu key  -> bỏ qua / cảnh báo
                showWarn("Định dạng FILE message thiếu key: " + content);
                return;
            }
            String fileName = parts[0];
            long fileSize = Long.parseLong(parts[1]);
            String key = parts[2];

            // Tạo node hiển thị file
            msgNode = createFileMessageNode(fileName, fileSize, key, isOutgoing);
            
            // Nếu là hình ảnh, đảm bảo yêu cầu thumbnail
            boolean isImage = fileName.matches("(?i).+\\.(png|jpe?g|gif)");
            if(isImage) {
                // Yêu cầu thumbnail từ server nếu chưa có
                if(ServiceLocator.chat().getThumb(key) == null) {
                    System.out.println("[UI] Yêu cầu thumbnail cho file: " + key);
                    ServiceLocator.chat().requestThumb(key);
                } else {
                    System.out.println("[UI] Đã có thumbnail cho file: " + key);
                }
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
            // Quay lại trang chat
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load();
            
            // Lấy Scene hiện tại
            Scene scene = btnBack.getScene();
            scene.setRoot(root);
            
            // Lấy controller của chat.fxml, gán username và yêu cầu cập nhật
            ChatController chatCtrl = loader.getController();
            chatCtrl.setCurrentUser(currentUser); // Sử dụng currentUser thay vì currentUsername
            
            // Yêu cầu danh sách user online mới từ server
            ServiceLocator.chat().getClient().requestUserList();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Không thể quay lại trang chat", e);
        }
    }

    // Thêm hàm mở conversation riêng tư
    private void openPrivateConversation(User user) {
        // Đặt currentTarget là username của user đó
        this.currentTarget = user.getUsername();
        chatTitleLabel.setText(user.getFullName() != null ? user.getFullName() : user.getUsername());
        // Có thể load lịch sử tin nhắn với user này nếu muốn
        // ...
        // Ví dụ: clear messagesContainer và load lại tin nhắn với user này
        messagesContainer.getChildren().clear();
        List<Message> oldMessages = ServiceLocator.messageService().getMessagesWithUser(currentUser, user.getUsername());
        for (Message m : oldMessages) {
            boolean isOutgoing = m.getSender().getUsername().equals(currentUser);
            displayMessage(
                m.getSender().getUsername(),
                m.getContent(),
                isOutgoing,
                m.getCreatedAt()
            );
        }
    }
}