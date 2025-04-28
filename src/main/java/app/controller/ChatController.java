package app.controller;

import app.LocalDateTimeAdapter;
import app.service.ChatService;
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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
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
import java.util.stream.Collectors;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.paint.Color;
import com.gluonhq.emoji.util.TextUtils;
import com.gluonhq.emoji.Emoji;
import javafx.scene.image.ImageView;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
public class ChatController {

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private VBox messagesContainer;

    @FXML
    private TextField txtMessage, searchField;

    @FXML
    private Button btnAttachFile, btnEmoji, btnSend;

    @FXML
    private ListView<String> listOnlineUsers;

    @FXML
    private Button btnCreateGroup;

    @FXML
    private ListView<String> listGroups; // Đảm bảo fx:id khớp với FXML

    @FXML
    private SplitPane rootSplit;

    // 1) Thuộc tính
    private String lastPmTarget;

    // Username hiện tại (người dùng đang đăng nhập)
    private String currentUser;

    private String currentTarget = "Global"; // mặc định Global

    private final Map<String, Long> groupMap = new HashMap<>();


    // Kết nối client (để gửi/nhận gói tin)
    private ClientConnection clientConnection;

    // add field
    private final Map<Long, Boolean> joinedConv = new HashMap<>();
    private final Map<String, VBox> fileBubbleMap = new HashMap<>();





    @FXML
    private void initialize() {
// Set the divider position immediately
        rootSplit.setDividerPosition(0, 0.75);

        // Lock the divider position
        rootSplit.getDividers().get(0).positionProperty().addListener((obs, old, pos) -> {
            if (Math.abs(pos.doubleValue() - 0.75) > 0.001) {
                rootSplit.setDividerPosition(0, 0.75);
            }
        });

        // Prevent user from moving the divider
        rootSplit.getDividers().get(0).setPosition(0.75);

        // Add CSS style to prevent visual glitches
        rootSplit.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");
        System.out.println("ScrollPane height: " + scrollPane.getHeight());
        System.out.println("VBox height: " + messagesContainer.getHeight());
        // 1) Gọi service bind UI (nếu bạn cần)
        ServiceLocator.chat().bindUI(this);
        Platform.runLater(() -> {
            rootSplit.setDividerPosition(0, 0.75);

            /* khoá lại nếu muốn */
            rootSplit.getDividers().get(0).positionProperty().addListener((o,oldV,newV)->{
                if(Math.abs(newV.doubleValue() - 0.75) > 0.001)
                    rootSplit.setDividerPosition(0, 0.75);
            });
        });

        // 2) Lấy clientConnection từ ChatService
        this.clientConnection = ServiceLocator.chat().getClient();

        // 3) Kiểm tra null
        if (this.clientConnection == null) {
            System.out.println("ChatController: clientConnection == null trong initialize()!");
            // Có thể return hoặc xử lý chờ
            return;
        }

        // 4) Thiết lập callback user list
        clientConnection.setOnUserListReceived(users -> {
            Platform.runLater(() -> {
                // Giữ đúng 1 mục cố định
                listOnlineUsers.getItems().setAll("Global");

                // Thêm các user online
                for (String u : users) {
                    if (!u.equals(getCurrentUser())) {
                        listOnlineUsers.getItems().add(u);
                    }
                }
            });
        });





        clientConnection.setOnTextReceived((from, content) -> {
            if (!"Global".equals(currentTarget)) return;          // ② LỌC
            Platform.runLater(() -> {
                boolean out = from.equals(getCurrentUser());
                displayMessage(from, content, out, LocalDateTime.now());
            });
        });

        // callback khi server xác nhận Join
        clientConnection.setOnConvJoined(cid -> joinedConv.put(cid, true));

        listOnlineUsers.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);

                    // Nếu item == currentTarget => bôi đậm
                    if (item.equals(currentTarget)) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });


        listOnlineUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                System.out.println("Chọn target: " + newVal + ", fromUser: " + getCurrentUser());
                this.currentTarget = newVal;
                try {
                    clientConnection.requestHistory(getCurrentUser(), currentTarget);
                    System.out.println("Gửi yêu cầu lịch sử cho " + getCurrentUser() + "->" + currentTarget);
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setHeaderText("Lỗi gửi yêu cầu lịch sử");
                        alert.setContentText("Chi tiết: " + e.getMessage());
                        alert.showAndWait();
                    });
                }
                listOnlineUsers.refresh();
            }
        });


//        clientConnection.setOnHistoryReceived(json -> {
//            System.out.println("Nhận HISTORY JSON: " + json); // Xem JSON có đúng không
//            try {
//                // 1) Thử parse JSON -> list of messages
//                List<MessageDTO> msgList = parseJsonToMessageDTO(json);
//                System.out.println("Parse thành công, số tin nhắn: " + msgList.size());
//                // 2) Nếu parse OK => cập nhật UI
//                Platform.runLater(() -> {
//                    messagesContainer.getChildren().clear();
//                    for (MessageDTO m : msgList) {
//                        System.out.println("Hiển thị tin từ: " + m.getUser() + ", currentUser: " + getCurrentUser());
//                        boolean isOutgoing = m.getUser().equals(getCurrentUser());
//                        displayMessage(m.getUser(), m.getContent(), isOutgoing, m.getTime());
//                    }
//                });
//            } catch (Exception ex) {
//                // 3) In ra stacktrace để biết lỗi
//                ex.printStackTrace();
//
//                // Tuỳ ý: Hiển thị alert thông báo lỗi
//                Platform.runLater(() -> {
//                    Alert alert = new Alert(Alert.AlertType.ERROR);
//                    alert.setHeaderText("Lỗi parse JSON HISTORY");
//                    alert.setContentText("JSON nhận được:\n" + json
//                            + "\n\nChi tiết: " + ex.getMessage());
//                    alert.showAndWait();
//                });
//            }
//        });

        // Khi server xác nhận tạo nhóm

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





        /* ① đợi đến khi Node đã có Scene & Stage */
        rootSplit.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;

            /* đợi Window non-null */
            newScene.windowProperty().addListener((o, oldWin, newWin) -> {
                if (newWin != null) {                      // Stage đã có
                    ((Stage) newWin).setOnCloseRequest(ev ->
                            ServiceLocator.chat().shutdown());
                }
            });
        });




        // ngay sau khi đã đặt các callback khác
        clientConnection.setOnConvList(json -> {
            // 1) parse JSON nhận từ server
            List<Map<String,Object>> list = new Gson().fromJson(
                    json, new com.google.gson.reflect.TypeToken<
                            List<Map<String,Object>>>(){}.getType());

            Platform.runLater(() -> {
                // 2) xoá danh sách cũ
                listGroups.getItems().clear();
                groupMap.clear();

                // 3) duyệt tất cả conversation
                for (Map<String,Object> c : list) {
                    String type = (String) c.get("type");
                    if (!"GROUP".equals(type)) continue;          // chỉ quan tâm GROUP

                    String name = (String) c.get("name");
                    Long   id   = ((Number) c.get("id")).longValue();

                    // 3a. cập nhật UI tab Group
                    listGroups.getItems().add(name);
                    // 3b. lưu vào groupMap cho send/receive
                    groupMap.put(name, id);

//                    // 3c. đưa tên nhóm vào listOnlineUsers (để chọn chat)
//                    if (!listOnlineUsers.getItems().contains(name))
//                        listOnlineUsers.getItems().add(name);
                }
                listGroups.refresh();
                listOnlineUsers.refresh();
            });
        });

        listGroups.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if(newVal == null) return;

                    currentTarget = newVal;               // gán target = tên nhóm
                    long cid = groupMap.get(newVal);

                    clientConnection.joinConv(cid);       // yêu cầu SERVER gửi HISTORY
                    listGroups.refresh();
                });


// Khi nhận tin nhắn group
        clientConnection.setOnGroupMsg((convId, from, content) -> {
            // chỉ hiện nếu màn hình đang mở đúng group
            if(!groupMap.containsKey(currentTarget)
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



        // Khi ấn Enter trong txtMessage => gửi tin
        txtMessage.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                onSend();
            }
        });

        // Tùy chỉnh ScrollPane & messagesContainer
        messagesContainer.setFillWidth(true);
        scrollPane.setFitToWidth(true);
        scrollPane.vvalueProperty().bind(messagesContainer.heightProperty());

        //chinh cho scrollpane co thể cuộn
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3a3a3a;");


    }

    public void setCurrentUser(String username) {
        this.currentUser = username;

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
        for (String u : listOnlineUsers.getItems()) {
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
//    public void displayFileMessage(String from, String fileId, boolean isOutgoing, LocalDateTime sentTime, String fileName, long fileSize) {
//        // Tạo UI cho tin nhắn chứa file
//        HBox bubbleBox = new HBox(5);
//        bubbleBox.setPrefWidth(Double.MAX_VALUE);
//        bubbleBox.setAlignment(isOutgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
//
//        VBox messageVBox = new VBox(2);
//
//        if (!isOutgoing) {
//            Label fromLabel = new Label(from);
//            fromLabel.setStyle("-fx-text-fill:#b0b0b0; -fx-font-size:10;");
//            messageVBox.getChildren().add(fromLabel);
//        }
//
//        Label name = new Label(fileName);
//        Label sz = new Label(formatFileSize(fileSize));
//
//        Button btn = new Button(isOutgoing ? "Lưu về..." : "Tải xuống");
//
//        // Button action để tải file nếu người dùng muốn
//        btn.setOnAction(e -> {
//            if (!ServiceLocator.chat().hasFile(fileId)) {
//                btn.setText("Đang tải…");
//                btn.setDisable(true);
//                ServiceLocator.chat().download(fileId);
//
//                // Kiểm tra nếu file đã tải xong
//                new Thread(() -> {
//                    while (!ServiceLocator.chat().hasFile(fileId)) {
//                        try { Thread.sleep(200); } catch (Exception ex) {}
//                    }
//                    Platform.runLater(() -> {
//                        btn.setText("Lưu về…");
//                        btn.setDisable(false);
//                    });
//                }).start();
//                return;
//            }
//
//            FileChooser fc = new FileChooser();
//            fc.setInitialFileName(fileName);
//            File dest = fc.showSaveDialog(btn.getScene().getWindow());
//            if (dest != null) {
//                try {
//                    byte[] data = ServiceLocator.chat().getFileData(fileId);
//                    Files.write(dest.toPath(), data);
//                } catch (IOException ex) {
//                    showError("Lỗi lưu file", ex);
//                }
//            }
//        });
//
//        messageVBox.getChildren().addAll(name, sz, btn);
//
//        // Kiểm tra nếu file là ảnh và nhỏ hơn 2MB để hiển thị thumbnail
//        boolean isImage = fileName.matches("(?i).+\\.(png|jpe?g|gif)");
//        if (isImage && fileSize < 2 * 1024 * 1024) {  // Nếu là ảnh nhỏ hơn 2MB
//            byte[] data = ServiceLocator.chat().getFileData(fileId);  // lấy từ cache
//            if (data != null) {
//                ImageView iv = new ImageView(new Image(new ByteArrayInputStream(data)));
//                iv.setFitWidth(260); iv.setPreserveRatio(true);
//                messageVBox.getChildren().add(iv);  // Thêm thumbnail vào UI
//            }
//        }
//
//        bubbleBox.getChildren().add(messageVBox);
//
//        // Thêm vào container tin nhắn
//        messagesContainer.getChildren().add(bubbleBox);
//    }






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

            // Gửi file với conversationId
            try {
                // Tạo tin nhắn file format
//                String fileMessage = String.format("[FILE]%s|%d", file.getName(), file.length());
//                // Hiển thị cho người gửi
//                displayMessage(currentUser, fileMessage, true, LocalDateTime.now());
                // Gửi file
                ServiceLocator.chat().sendFile(conversationId, file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
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

        /* thumbnail nếu có */
        byte[] pic = ServiceLocator.chat().getThumb(id);
        System.out.println("[UI] id="+id+" thumb? "+(pic!=null));

        // Kiểm tra nếu file là hình ảnh
        boolean isImage = name.matches("(?i).+\\.(png|jpe?g|gif)");
        
        if(pic!=null){
            // Nếu đã có thumbnail trong cache, hiển thị ngay
            ImageView iv = new ImageView(new Image(new ByteArrayInputStream(pic)));
            iv.setFitWidth(260); iv.setPreserveRatio(true);
            box.getChildren().add(iv);
        } else if(isImage) {
            // Nếu là hình ảnh nhưng chưa có thumbnail, yêu cầu từ server
            ServiceLocator.chat().requestThumb(id);
            
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
        fileBubbleMap.put(id, box);          // ⬅  thêm dòng này

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
            if(isImage && ServiceLocator.chat().getThumb(key) == null) {
                // Yêu cầu thumbnail từ server nếu chưa có
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
    }

    public void refreshThumbnail(String id){
        VBox box = fileBubbleMap.get(id);
        if(box == null) return;                    // chưa kịp vẽ bubble

        // Kiểm tra xem đã có thumbnail chưa
        Node existingThumb = box.lookup("#thumb");
        if(existingThumb != null) {
            // Nếu đã có thumbnail, cập nhật nó
            box.getChildren().remove(existingThumb);
        }

        byte[] data = ServiceLocator.chat().getThumb(id);
        if(data == null) return;

        // Tạo và hiển thị thumbnail mới
        ImageView iv = new ImageView(new Image(new ByteArrayInputStream(data)));
        iv.setId("thumb");
        iv.setFitWidth(260); iv.setPreserveRatio(true);

        // Chèn thumbnail vào đầu danh sách con
        box.getChildren().add(0, iv);
        box.requestLayout();
        
        System.out.println("[UI] Đã cập nhật thumbnail cho file: " + id);
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




}