package app.controller.chat.handler;

import app.ServiceLocator;
import app.controller.chat.component.MessageBubble;
import app.controller.chat.component.ToastNotification;
import app.controller.chat.model.RecentChatCellData;
import app.model.Friendship;
import app.model.Message;
import app.model.User;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import network.ClientConnection;
import app.model.Conversation;
import java.time.format.DateTimeFormatter;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handler xử lý các thao tác liên quan đến tin nhắn
 */
public class MessageHandler {
    
    private final ClientConnection clientConnection;
    private final String currentUsername;
    private final VBox messagesContainer;
    private final Node rootNode;
    private final FileHandler fileHandler;
    private final Consumer<List<RecentChatCellData>> onRecentChatsUpdated;
    
    // Lưu trữ số lượng tin nhắn chưa đọc
    private final Map<String, Integer> unreadMap = new HashMap<>();
    
    // Đối tượng người dùng hiện tại
    private User currentUser;
    private Long currentConversationId = null;

    // Target chat hiện tại
    private String currentTarget = "Global";
    
    // Target chat cuối cùng cho tin nhắn riêng tư
    private String lastPmTarget;
    
    // Map lưu trữ ID nhóm
    private final Map<String, Long> groupMap = new HashMap<>();
    
    /**
     * Khởi tạo MessageHandler
     * @param clientConnection Kết nối client
     * @param currentUsername Username hiện tại
     * @param messagesContainer Container hiển thị tin nhắn
     * @param rootNode Node gốc để hiển thị thông báo
     * @param fileHandler Handler xử lý file
     * @param onRecentChatsUpdated Callback khi danh sách chat gần đây thay đổi
     */
    public MessageHandler(ClientConnection clientConnection, String currentUsername, 
                         VBox messagesContainer, Node rootNode, FileHandler fileHandler,
                         Consumer<List<RecentChatCellData>> onRecentChatsUpdated) {
        this.clientConnection = clientConnection;
        this.currentUsername = currentUsername;
        this.messagesContainer = messagesContainer;
        this.rootNode = rootNode;
        this.fileHandler = fileHandler;
        this.onRecentChatsUpdated = onRecentChatsUpdated;
        
        // Khởi tạo đối tượng người dùng hiện tại
        this.currentUser = ServiceLocator.userService().getUser(currentUsername);
    }
    // Getter
    public Long getCurrentConversationId() {
        return currentConversationId;
    }
    public void setCurrentConversationId(Long currentConversationId) {
        this.currentConversationId = currentConversationId;
    }
    
    /**
     * Gửi tin nhắn văn bản
     * @param content Nội dung tin nhắn
     */
    public void sendTextMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        
        try {
            // Lưu target hiện tại để cập nhật UI sau khi gửi
            String target = currentTarget;
            
            // Gửi tin nhắn tùy theo target hiện tại
            if ("Global".equals(currentTarget)) {
                clientConnection.sendText(content);
            } else if (groupMap.containsKey(currentTarget)) {
                long gid = groupMap.get(currentTarget);
                clientConnection.sendGroup(gid, content);
                lastPmTarget = currentTarget;
            } else {
                // Kiểm tra xem có phải bạn bè chưa
                if (currentUser == null) {
                    currentUser = ServiceLocator.userService().getUser(currentUsername);
                }
                
                User targetUser = ServiceLocator.userService().getUser(currentTarget);
                if (targetUser != null && currentUser != null) {
                    Friendship.Status status = ServiceLocator.friendship().getFriendshipStatus(currentUser, targetUser);
                    if (status != Friendship.Status.ACCEPTED) {
                        // Hiển thị thông báo nếu chưa là bạn bè
                        Platform.runLater(() -> {
                            app.controller.chat.util.ChatUIHelper.showWarning("Bạn cần kết bạn với " + currentTarget + " trước khi nhắn tin");
                        });
                        return;
                    }
                }
                
                clientConnection.sendPrivate(currentTarget, content);
                lastPmTarget = currentTarget;
            }
            
            // Cập nhật real-time danh sách chat gần đây với tin nhắn vừa gửi
            String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            final String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            
            Platform.runLater(() -> {
                try {
                    if (onRecentChatsUpdated != null) {
                        // Lấy đầy đủ danh sách chat gần đây hiện tại
                        List<RecentChatCellData> currentChats = ServiceLocator.messageService().getRecentChats(currentUser);
                        
                        if (currentChats == null) {
                            System.err.println("[ERROR] Danh sách chat trả về từ MessageService là null");
                            currentChats = new ArrayList<>();
                        }
                        
                        // Sao chép để tránh thay đổi trực tiếp danh sách gốc
                        List<RecentChatCellData> updatedChats = new ArrayList<>();
                        
                        // Tìm và cập nhật chat tương ứng với target hiện tại
                        boolean found = false;
                        
                        for (RecentChatCellData chat : currentChats) {
                            if (chat.chatName.equals(target)) {
                                // Cập nhật tin nhắn mới nhất và thời gian
                                String lastMessage = currentUsername + ": " + previewText;
                                
                                updatedChats.add(new RecentChatCellData(
                                    chat.chatName,
                                    lastMessage,
                                    currentTime,
                                    chat.avatarPath,
                                    0  // Tin nhắn gửi đi luôn có unread count = 0
                                ));
                                found = true;
                            } else {
                                // Giữ nguyên các chat khác
                                updatedChats.add(new RecentChatCellData(
                                    chat.chatName,
                                    chat.lastMessage,
                                    chat.timeString,
                                    chat.avatarPath,
                                    unreadMap.getOrDefault(chat.chatName, 0)
                                ));
                            }
                        }
                        
                        // Nếu không tìm thấy, thêm mới (hiếm khi xảy ra)
                        if (!found && !target.equals("Global")) {
                            // Thử tìm avatar của người nhận nếu là chat riêng tư
                            String avatarPath = null;
                            if (!target.startsWith("Group")) {
                                User targetUser = ServiceLocator.userService().getUser(target);
                                if (targetUser != null && !targetUser.isUseDefaultAvatar() && targetUser.getAvatarPath() != null) {
                                    File avatarFile = new File(targetUser.getAvatarPath());
                                    if (avatarFile.exists()) {
                                        avatarPath = targetUser.getAvatarPath();
                                    }
                                }
                            }
                            
                            // Thêm chat mới vào danh sách
                            updatedChats.add(new RecentChatCellData(
                                target,
                                currentUsername + ": " + previewText,
                                currentTime,
                                avatarPath,
                                0
                            ));
                        }
                        
                        // Sắp xếp danh sách với tin nhắn mới nhất lên đầu (ngoại trừ Global)
                        updatedChats.sort((a, b) -> {
                            if (a.chatName.equals("Global")) return -1;
                            if (b.chatName.equals("Global")) return 1;
                            if (a.timeString.isEmpty() && b.timeString.isEmpty()) return 0;
                            if (a.timeString.isEmpty()) return 1;
                            if (b.timeString.isEmpty()) return -1;
                            return b.timeString.compareTo(a.timeString);
                        });
                        
                        // Cập nhật UI
                        onRecentChatsUpdated.accept(updatedChats);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Lỗi khi cập nhật real-time recent chats sau khi gửi tin nhắn: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Nếu có lỗi, sử dụng phương thức refreshRecentChats ban đầu
                    refreshRecentChats();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Hiển thị tin nhắn trong container
     * @param from Người gửi
     * @param content Nội dung tin nhắn
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @param sentTime Thời gian gửi
     */
    public void displayMessage(String from, String content, boolean isOutgoing, LocalDateTime sentTime) {
        Platform.runLater(() -> {
            // Kiểm tra nếu là tin nhắn file
            boolean isFileMessage = content.startsWith("[FILE]");
            
            if (isFileMessage) {
                displayFileMessage(from, content, isOutgoing, sentTime);
            } else {
                // Hiển thị tin nhắn văn bản thông thường
                Node messageBubble = MessageBubble.createTextMessageBubble(content, isOutgoing, from, sentTime);
                messagesContainer.getChildren().add(messageBubble);
            }
        });
    }
    
    /**
     * Hiển thị tin nhắn file
     * @param from Người gửi
     * @param content Nội dung tin nhắn
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @param sentTime Thời gian gửi
     */
    private void displayFileMessage(String from, String content, boolean isOutgoing, LocalDateTime sentTime) {
        String fileInfo = content.substring(6);
        String[] parts = fileInfo.split("\\|", 3);
        
        if (parts.length < 3) {
            System.err.println("[ERROR] Định dạng FILE message thiếu key: " + content);
            return;
        }
        
        String fileName = parts[0];
        long fileSize = Long.parseLong(parts[1]);
        String key = parts[2];
        
        // Tạo file message bubble
        VBox fileBubble = fileHandler.createFileMessageBubble(fileName, fileSize, key, isOutgoing);
        
        // Tạo container cho tin nhắn
        VBox messageVBox = new VBox(2);
        
        // Thêm tên người gửi nếu không phải tin nhắn gửi đi
        if (!isOutgoing) {
            javafx.scene.control.Label fromLabel = new javafx.scene.control.Label(from);
            fromLabel.setStyle("-fx-text-fill:#b0b0b0; -fx-font-size:10;");
            messageVBox.getChildren().add(fromLabel);
        }
        
        // Thêm file bubble
        messageVBox.getChildren().add(fileBubble);
        
        // Thêm thời gian
        if (sentTime != null) {
            String timeText = sentTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            javafx.scene.control.Label timeLabel = new javafx.scene.control.Label(timeText);
            timeLabel.setStyle("-fx-text-fill:#999999; -fx-font-size:10;");
            messageVBox.getChildren().add(timeLabel);
        }
        
        // Tạo container chính và thêm vào messagesContainer
        javafx.scene.layout.HBox bubbleBox = new javafx.scene.layout.HBox(5);
        bubbleBox.setPrefWidth(Double.MAX_VALUE);
        bubbleBox.setAlignment(isOutgoing ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        bubbleBox.getChildren().add(messageVBox);
        
        messagesContainer.getChildren().add(bubbleBox);
    }
    
    /**
     * Xử lý tin nhắn mới đến
     * @param sender Người gửi
     * @param content Nội dung tin nhắn
     */
    public void handleNewMessage(String sender, String content) {
        // Chỉ xử lý tin nhắn từ người khác và không phải là Global
        if (sender == null || sender.equals(currentUsername) || "Global".equals(sender)) return;
        
        // Cập nhật số lượng tin nhắn chưa đọc
        int count = unreadMap.getOrDefault(sender, 0) + 1;
        unreadMap.put(sender, count);
        
        // Hiển thị thông báo nếu không đang trong cuộc trò chuyện đó
        if (!sender.equals(currentTarget)) {
            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            ToastNotification.show(sender + ": " + previewText, rootNode);
        }
        
        // Lấy thời gian hiện tại để hiển thị real-time
        String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        
        // Tạo bản sao của danh sách chat gần đây và cập nhật entry tương ứng với tin nhắn mới nhất
        Platform.runLater(() -> {
            try {
                if (onRecentChatsUpdated != null) {
                    // Lấy đầy đủ danh sách chat gần đây hiện tại từ service
                    List<RecentChatCellData> currentChats = ServiceLocator.messageService().getRecentChats(currentUser);
                    
                    if (currentChats == null) {
                        System.err.println("[ERROR] Danh sách chat trả về từ MessageService là null");
                        currentChats = new ArrayList<>();
                    }
                    
                    // Sao chép để tránh thay đổi trực tiếp danh sách gốc
                    List<RecentChatCellData> updatedChats = new ArrayList<>();
                    
                    // Đảm bảo Global luôn có trong danh sách và ở đầu
                    boolean foundGlobal = false;
                    for (RecentChatCellData chat : currentChats) {
                        if ("Global".equals(chat.chatName)) {
                            updatedChats.add(chat);
                            foundGlobal = true;
                            break;
                        }
                    }
                    
                    if (!foundGlobal) {
                        updatedChats.add(new RecentChatCellData("Global", "Chat toàn cầu", "", null, 0));
                    }
                    
                    // Tìm và cập nhật chat tương ứng với người gửi
                    boolean found = false;
                    
                    for (RecentChatCellData chat : currentChats) {
                        if ("Global".equals(chat.chatName)) continue; // Đã xử lý riêng
                        
                        if (chat.chatName.equals(sender)) {
                            // Đảm bảo người gửi không null khi tạo tin nhắn cuối
                            String senderName = (sender != null) ? sender : "Unknown";
                            
                            // Cập nhật tin nhắn mới nhất và thời gian
                            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                            String lastMessage = senderName + ": " + previewText;
                            
                            updatedChats.add(new RecentChatCellData(
                                chat.chatName,
                                lastMessage,
                                currentTime,
                                chat.avatarPath,
                                count  // Sử dụng số tin nhắn chưa đọc đã tính toán
                            ));
                            found = true;
                        } else {
                            // Giữ nguyên các chat khác nhưng cập nhật số tin nhắn chưa đọc
                            updatedChats.add(new RecentChatCellData(
                                chat.chatName,
                                chat.lastMessage,
                                chat.timeString,
                                chat.avatarPath,
                                unreadMap.getOrDefault(chat.chatName, 0)
                            ));
                        }
                    }
                    
                    // Nếu không tìm thấy, thêm mới
                    if (!found && sender != null) {
                        // Thử tìm avatar của người gửi
                        User senderUser = ServiceLocator.userService().getUser(sender);
                        String avatarPath = null;
                        if (senderUser != null && !senderUser.isUseDefaultAvatar() && senderUser.getAvatarPath() != null) {
                            File avatarFile = new File(senderUser.getAvatarPath());
                            if (avatarFile.exists()) {
                                avatarPath = senderUser.getAvatarPath();
                            }
                        }
                        
                        String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                        String lastMessage = sender + ": " + previewText;
                        
                        // Thêm chat mới vào danh sách
                        updatedChats.add(new RecentChatCellData(
                            sender,
                            lastMessage,
                            currentTime,
                            avatarPath,
                            count
                        ));
                    }
                    
                    // Sắp xếp danh sách với tin nhắn mới nhất lên đầu (ngoại trừ Global)
                    updatedChats.sort((a, b) -> {
                        if (a.chatName.equals("Global")) return -1;
                        if (b.chatName.equals("Global")) return 1;
                        if (a.timeString.isEmpty() && b.timeString.isEmpty()) return 0;
                        if (a.timeString.isEmpty()) return 1;
                        if (b.timeString.isEmpty()) return -1;
                        return b.timeString.compareTo(a.timeString);
                    });
                    
                    // Cập nhật UI
                    onRecentChatsUpdated.accept(updatedChats);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi cập nhật real-time recent chats: " + e.getMessage());
                e.printStackTrace();
                
                // Nếu có lỗi, sử dụng phương thức refreshRecentChats ban đầu
                refreshRecentChats();
            }
        });
    }
    
    /**
     * Xử lý tin nhắn nhóm mới
     * @param groupName Tên nhóm
     * @param sender Người gửi
     * @param content Nội dung tin nhắn
     */
    public void handleNewGroupMessage(String groupName, String sender, String content) {
        // Kiểm tra tham số đầu vào
        if (groupName == null || sender == null) {
            System.err.println("[ERROR] Tham số null trong handleNewGroupMessage: groupName=" + groupName + ", sender=" + sender);
            return;
        }
        
        // Bỏ qua tin nhắn từ chính mình vì đã được hiển thị khi gửi
        if (sender.equals(currentUsername)) {
            System.out.println("[DEBUG] Bỏ qua tin nhắn nhóm từ chính mình: " + content);
            return;
        }
        
        // Cập nhật số lượng tin nhắn chưa đọc nếu không đang trong nhóm đó
        if (!groupName.equals(currentTarget)) {
            int count = unreadMap.getOrDefault(groupName, 0) + 1;
            unreadMap.put(groupName, count);
            
            // Hiển thị thông báo
            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            ToastNotification.show(groupName + " - " + sender + ": " + previewText, rootNode);
        }
        
        // Lấy thời gian hiện tại để hiển thị real-time
        String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        
        // Cập nhật danh sách chat gần đây theo thời gian thực
        Platform.runLater(() -> {
            try {
                if (onRecentChatsUpdated != null) {
                    // Lấy đầy đủ danh sách chat gần đây hiện tại từ service
                    List<RecentChatCellData> currentChats = ServiceLocator.messageService().getRecentChats(currentUser);
                    
                    if (currentChats == null) {
                        System.err.println("[ERROR] Danh sách chat trả về từ MessageService là null");
                        currentChats = new ArrayList<>();
                    }
                    
                    // Sao chép để tránh thay đổi trực tiếp danh sách gốc
                    List<RecentChatCellData> updatedChats = new ArrayList<>();
                    
                    // Tìm và cập nhật chat tương ứng với nhóm
                    boolean found = false;
                    
                    for (RecentChatCellData chat : currentChats) {
                        if (chat.chatName.equals(groupName)) {
                            // Đảm bảo người gửi không null khi tạo tin nhắn cuối
                            String senderName = (sender != null) ? sender : "Unknown";
                            
                            // Cập nhật tin nhắn mới nhất và thời gian
                            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                            String lastMessage = senderName + ": " + previewText;
                            
                            updatedChats.add(new RecentChatCellData(
                                chat.chatName,
                                lastMessage,
                                currentTime,
                                chat.avatarPath,
                                !groupName.equals(currentTarget) ? unreadMap.get(groupName) : 0
                            ));
                            found = true;
                        } else {
                            // Giữ nguyên các chat khác
                            updatedChats.add(new RecentChatCellData(
                                chat.chatName,
                                chat.lastMessage,
                                chat.timeString,
                                chat.avatarPath,
                                unreadMap.getOrDefault(chat.chatName, 0)
                            ));
                        }
                    }
                    
                    // Nếu không tìm thấy, thêm mới
                    if (!found) {
                        String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                        String lastMessage = sender + ": " + previewText;
                        
                        // Thêm chat nhóm mới vào danh sách
                        updatedChats.add(new RecentChatCellData(
                            groupName,
                            lastMessage,
                            currentTime,
                            null,  // Nhóm không có avatar riêng
                            unreadMap.getOrDefault(groupName, 0)
                        ));
                    }
                    
                    // Sắp xếp danh sách với tin nhắn mới nhất lên đầu (ngoại trừ Global)
                    updatedChats.sort((a, b) -> {
                        if (a.chatName.equals("Global")) return -1;
                        if (b.chatName.equals("Global")) return 1;
                        if (a.timeString.isEmpty() && b.timeString.isEmpty()) return 0;
                        if (a.timeString.isEmpty()) return 1;
                        if (b.timeString.isEmpty()) return -1;
                        return b.timeString.compareTo(a.timeString);
                    });
                    
                    // Cập nhật UI
                    onRecentChatsUpdated.accept(updatedChats);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi cập nhật real-time recent chats cho nhóm: " + e.getMessage());
                e.printStackTrace();
                
                // Nếu có lỗi, sử dụng phương thức refreshRecentChats ban đầu
                refreshRecentChats();
            }
        });
    }

    public void handleNewGroup(String groupName, long groupId) {
        // Cập nhật groupMap
        updateGroupMap(groupName, groupId);

        // Thêm vào recent chats nếu chưa có
        Platform.runLater(() -> {
            if (onRecentChatsUpdated != null) {
                List<RecentChatCellData> currentChats = ServiceLocator.messageService()
                        .getRecentChats(currentUser);

                // Kiểm tra xem nhóm đã có trong danh sách chưa
                boolean exists = currentChats.stream()
                        .anyMatch(chat -> chat.chatName.equals(groupName));

                if (!exists) {
                    // Thêm nhóm mới
                    currentChats.add(new RecentChatCellData(
                            groupName,
                            "Nhóm mới được tạo",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                            null,
                            0
                    ));

                    // Sắp xếp lại
                    currentChats.sort((a, b) -> {
                        if (a.chatName.equals("Global")) return -1;
                        if (b.chatName.equals("Global")) return 1;
                        return b.timeString.compareTo(a.timeString);
                    });

                    onRecentChatsUpdated.accept(currentChats);
                }
            }
        });
    }
    
    /**
     * Mở cuộc trò chuyện riêng tư với người dùng
     * @param user Đối tượng người dùng
     */
    public void openPrivateConversation(User user) {
        if (user == null) {
            System.err.println("[ERROR] Không thể mở cuộc trò chuyện với user null");
            return;
        }
        
        try {
            // Cập nhật target hiện tại
            this.currentTarget = user.getUsername();
            
            // Xóa tin nhắn cũ
            if (messagesContainer != null) {
                messagesContainer.getChildren().clear();
            }
            
            // Lấy username hiện tại một cách an toàn
            final String localUsername;
            if (currentUsername == null || currentUsername.isBlank()) {
                // Thử lấy từ currentUser
                if (currentUser != null && currentUser.getUsername() != null) {
                    localUsername = currentUser.getUsername();
                    System.out.println("[INFO] Sử dụng username từ currentUser: " + localUsername);
                } else {
                    // Thử lấy từ UserService
                    User userFromService = ServiceLocator.userService().getCurrentUser();
                    if (userFromService != null && userFromService.getUsername() != null) {
                        localUsername = userFromService.getUsername();
                        // Cập nhật luôn currentUser
                        this.currentUser = userFromService;
                        System.out.println("[INFO] Sử dụng username từ UserService: " + localUsername);
                    } else {
                        System.err.println("[ERROR] Cannot request history: null username");
                        return;
                    }
                }
            } else {
                localUsername = currentUsername;
            }
            
            // Kiểm tra một lần nữa username có hợp lệ không
            if (localUsername == null || localUsername.isBlank()) {
                System.err.println("[ERROR] Không thể xác định username hiện tại");
                return;
            }
            
            // Đảm bảo cuộc trò chuyện tồn tại trước khi yêu cầu lịch sử
            clientConnection.checkPrivateConversation(user.getUsername());
            
            // Tìm và lưu conversation ID
            Conversation conv = findPrivateConversation(localUsername, user.getUsername());
            if (conv != null) {
                currentConversationId = conv.getId();
                System.out.println("[DEBUG] Set current conversation ID: " + currentConversationId);
            } else {
                // Tạo conversation nếu chưa có
                clientConnection.checkPrivateConversation(user.getUsername());
                // Đợi một chút rồi thử lại
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        Platform.runLater(() -> {
                            Conversation newConv = findPrivateConversation(localUsername, user.getUsername());
                            if (newConv != null) {
                                currentConversationId = newConv.getId();
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
            
            // Chờ một chút để cuộc trò chuyện được tạo nếu cần
            new Thread(() -> {
                try {
                    Thread.sleep(500); // Tăng độ trễ để đảm bảo cuộc trò chuyện được tạo
                    
                    // Sau đó yêu cầu lịch sử
                    Platform.runLater(() -> {
                        clientConnection.requestHistory(localUsername, user.getUsername());
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            // Đặt lastPmTarget cho tin nhắn gửi đi
            lastPmTarget = user.getUsername();
            
            // Đặt lại số tin nhắn chưa đọc
            unreadMap.put(user.getUsername(), 0);
            
            // Cập nhật danh sách chat gần đây
            refreshRecentChats();
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to open private conversation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Làm mới danh sách chat gần đây
     */
    public void refreshRecentChats() {
        if (onRecentChatsUpdated == null || currentUser == null) return;
        
        try {
            System.out.println("[REFRESH_CHAT_DEBUG] Starting refreshRecentChats for user: " + currentUsername);
            
            // Lấy danh sách chat gần đây từ service
            List<RecentChatCellData> chats = ServiceLocator.messageService().getRecentChats(currentUser);
            
            System.out.println("[REFRESH_CHAT_DEBUG] Retrieved " + 
                (chats != null ? chats.size() : "null") + " recent chats from service");
            
            if (chats == null) {
                System.err.println("[REFRESH_CHAT_DEBUG] ERROR: getRecentChats returned null");
                return;
            }
            
            // Cập nhật số tin nhắn chưa đọc
            List<RecentChatCellData> updatedChats = new ArrayList<>();
            for (RecentChatCellData chat : chats) {
                // Debug thông tin about avatar path
                System.out.println("[REFRESH_CHAT_DEBUG] Chat: " + chat.chatName + 
                    ", Avatar path: " + chat.avatarPath + 
                    ", Unread: " + unreadMap.getOrDefault(chat.chatName, 0));
                
                // Kiểm tra sự tồn tại của file avatar
                if (chat.avatarPath != null && !chat.avatarPath.isEmpty()) {
                    File avatarFile = new File(chat.avatarPath);
                    System.out.println("[REFRESH_CHAT_DEBUG] Avatar file for " + 
                        chat.chatName + " exists: " + avatarFile.exists() + 
                        ", Path: " + avatarFile.getAbsolutePath());
                }
                
                // Tạo đối tượng RecentChatCellData mới với số tin nhắn chưa đọc cập nhật
                updatedChats.add(new RecentChatCellData(
                    chat.chatName,
                    chat.lastMessage,
                    chat.timeString,
                    chat.avatarPath,
                    unreadMap.getOrDefault(chat.chatName, 0)
                ));
            }
            
            // Đảm bảo Global Chat luôn có trong danh sách
            updatedChats = ensureGlobalChat(updatedChats);
            
            // Sắp xếp danh sách với tin nhắn mới nhất lên đầu (ngoại trừ Global)
            Collections.sort(updatedChats, (a, b) -> {
                if (a.chatName.equals("Global")) return -1;
                if (b.chatName.equals("Global")) return 1;
                if (a.timeString.isEmpty() && b.timeString.isEmpty()) return 0;
                if (a.timeString.isEmpty()) return 1;
                if (b.timeString.isEmpty()) return -1;
                return b.timeString.compareTo(a.timeString);
            });
            
            System.out.println("[REFRESH_CHAT_DEBUG] Final updated list contains " + updatedChats.size() + " chats");
            
            // Cập nhật UI trên EDT
            final List<RecentChatCellData> finalChats = updatedChats;
            Platform.runLater(() -> onRecentChatsUpdated.accept(finalChats));
            
        } catch (Exception e) {
            System.err.println("[REFRESH_CHAT_DEBUG] Error in refreshRecentChats: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Cập nhật map nhóm
     * @param groupName Tên nhóm
     * @param groupId ID nhóm
     */
    public void updateGroupMap(String groupName, long groupId) {
        groupMap.put(groupName, groupId);
    }
    
    /**
     * Lấy ID nhóm từ tên nhóm
     * @param groupName Tên nhóm
     * @return ID nhóm hoặc -1 nếu không tìm thấy
     */
    public long getGroupId(String groupName) {
        return groupMap.getOrDefault(groupName, -1L);
    }
    
    /**
     * Kiểm tra xem target hiện tại có phải là nhóm không
     * @param target Tên target
     * @return true nếu là nhóm
     */
    public boolean isGroupTarget(String target) {
        return groupMap.containsKey(target);
    }
    
    /**
     * Lấy target hiện tại
     * @return Tên target hiện tại
     */
    public String getCurrentTarget() {
        return currentTarget;
    }
    
    /**
     * Đặt target hiện tại
     * @param target Tên target mới
     */
    public void setCurrentTarget(String target) {
        this.currentTarget = target;
    }
    
    /**
     * Lấy target cuối cùng cho tin nhắn riêng tư
     * @return Tên target cuối cùng
     */
    public String getLastPmTarget() {
        return lastPmTarget;
    }
    
    /**
     * Đặt target cuối cùng cho tin nhắn riêng tư
     * @param target Tên target mới
     */
    public void setLastPmTarget(String target) {
        this.lastPmTarget = target;
    }
    
    /**
     * Lấy số lượng tin nhắn chưa đọc
     * @return Tổng số tin nhắn chưa đọc
     */
    public int getTotalUnreadCount() {
        return unreadMap.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Xóa tin nhắn chưa đọc cho một target
     * @param target Tên target
     */
    public void clearUnread(String target) {
        unreadMap.put(target, 0);
        refreshRecentChats();
    }
    
    /**
     * Tìm cuộc trò chuyện riêng tư giữa hai người dùng
     * @param user1 Tên người dùng 1
     * @param user2 Tên người dùng 2
     * @return Đối tượng Conversation hoặc null nếu không tìm thấy
     */
    public Conversation findPrivateConversation(String user1, String user2) {
        // Kiểm tra null đầu vào
        if (user1 == null || user2 == null) {
            System.err.println("[ERROR] Tham số null trong findPrivateConversation: user1=" + user1 + ", user2=" + user2);
            return null;
        }
        
        try {
            // Sắp xếp tên theo thứ tự alphabet
            String name1 = user1.compareTo(user2) < 0 ? user1 : user2;
            String name2 = user1.compareTo(user2) < 0 ? user2 : user1;
            String convName = name1 + "|" + name2;
            
            // Tìm conversation
            Conversation conversation = ServiceLocator.conversationDAO().findByName(convName);
            
            // Nếu không tìm thấy, tự động tạo cuộc trò chuyện mới
            if (conversation == null) {
                System.out.println("[INFO] Tạo mới cuộc trò chuyện riêng tư: " + convName);
                // Tạo conversation mới và lưu vào cơ sở dữ liệu
                conversation = new Conversation();
                conversation.setName(convName);
                conversation.setType("PRIVATE");
                ServiceLocator.conversationDAO().save(conversation);
                
                // Tìm lại conversation sau khi lưu để lấy ID
                conversation = ServiceLocator.conversationDAO().findByName(convName);
                
                // Đảm bảo conversation được tạo thành công
                if (conversation == null || conversation.getId() == null) {
                    System.err.println("[ERROR] Không thể tạo cuộc trò chuyện mới: " + convName);
                    return null;
                }
                
                System.out.println("[INFO] Đã tạo cuộc trò chuyện mới với ID: " + conversation.getId());
                
                // Đảm bảo ClientConnection biết về cuộc trò chuyện này
                clientConnection.checkPrivateConversation(user2);
            }
            
            return conversation;
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi tìm cuộc trò chuyện riêng tư: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Xử lý tin nhắn toàn cục mới đến
     * @param sender Người gửi
     * @param content Nội dung tin nhắn
     */
    public void handleGlobalMessage(String sender, String content) {
        // Cập nhật số lượng tin nhắn chưa đọc nếu không đang trong Global chat
        if (!"Global".equals(currentTarget)) {
            int count = unreadMap.getOrDefault("Global", 0) + 1;
            unreadMap.put("Global", count);
            
            // Hiển thị thông báo
            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            ToastNotification.show("Global - " + sender + ": " + previewText, rootNode);
        }
        
        // Lấy thời gian hiện tại để hiển thị real-time
        String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        
        // Cập nhật danh sách chat gần đây theo thời gian thực
        Platform.runLater(() -> {
            try {
                if (onRecentChatsUpdated != null) {
                    // Lấy đầy đủ danh sách chat gần đây hiện tại từ service
                    List<RecentChatCellData> currentChats = ServiceLocator.messageService().getRecentChats(currentUser);
                    
                    if (currentChats == null) {
                        System.err.println("[ERROR] Danh sách chat trả về từ MessageService là null");
                        currentChats = new ArrayList<>();
                    }
                    
                    // Sao chép để tránh thay đổi trực tiếp danh sách gốc
                    List<RecentChatCellData> updatedChats = new ArrayList<>();
                    
                    // Tìm và cập nhật chat Global
                    boolean foundGlobal = false;
                    
                    for (RecentChatCellData chat : currentChats) {
                        if ("Global".equals(chat.chatName)) {
                            // Cập nhật tin nhắn mới nhất và thời gian cho Global chat
                            String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                            String lastMessage = sender + ": " + previewText;
                            int unreadCount = !"Global".equals(currentTarget) ? unreadMap.getOrDefault("Global", 0) : 0;
                            
                            updatedChats.add(new RecentChatCellData(
                                "Global",
                                lastMessage,
                                currentTime,
                                null,  // Global chat không có avatar riêng
                                unreadCount
                            ));
                            foundGlobal = true;
                        } else {
                            // Giữ nguyên các chat khác
                            updatedChats.add(new RecentChatCellData(
                                chat.chatName,
                                chat.lastMessage,
                                chat.timeString,
                                chat.avatarPath,
                                unreadMap.getOrDefault(chat.chatName, 0)
                            ));
                        }
                    }
                    
                    // Nếu không tìm thấy Global, thêm mới (hiếm khi xảy ra)
                    if (!foundGlobal) {
                        String previewText = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                        String lastMessage = sender + ": " + previewText;
                        
                        updatedChats.add(new RecentChatCellData(
                            "Global",
                            lastMessage,
                            currentTime,
                            null,  // Global chat không có avatar riêng
                            unreadMap.getOrDefault("Global", 0)
                        ));
                    }
                    
                    // Đảm bảo danh sách có đầy đủ các cuộc trò chuyện và Global luôn ở đầu
                    final List<RecentChatCellData> finalData = ensureGlobalChat(updatedChats);
                    
                    // Cập nhật UI
                    onRecentChatsUpdated.accept(finalData);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Lỗi khi cập nhật real-time recent chats cho Global chat: " + e.getMessage());
                e.printStackTrace();
                
                // Nếu có lỗi, sử dụng phương thức refreshRecentChats ban đầu
                refreshRecentChats();
            }
        });
    }
    
    /**
     * Đảm bảo Global chat luôn hiển thị trong danh sách chat gần đây
     * @param chats Danh sách chat gần đây
     * @return Danh sách đã được cập nhật với Global chat
     */
    private List<RecentChatCellData> ensureGlobalChat(List<RecentChatCellData> chats) {
        if (chats == null) chats = new ArrayList<>();
        
        // Kiểm tra xem Global chat đã có trong danh sách chưa
        boolean hasGlobal = false;
        for (RecentChatCellData chat : chats) {
            if ("Global".equals(chat.chatName)) {
                hasGlobal = true;
                break;
            }
        }
        
        // Nếu chưa có, thêm vào
        if (!hasGlobal) {
            chats.add(0, new RecentChatCellData("Global", "Chat toàn cầu", "", null, 0));
        }
        
        // Đảm bảo Global luôn ở đầu danh sách
        chats.sort((a, b) -> {
            if (a.chatName.equals("Global")) return -1;
            if (b.chatName.equals("Global")) return 1;
            if (a.timeString.isEmpty() && b.timeString.isEmpty()) return 0;
            if (a.timeString.isEmpty()) return 1;
            if (b.timeString.isEmpty()) return -1;
            return b.timeString.compareTo(a.timeString);
        });
        
        return chats;
    }
    
    /**
     * Lấy người dùng hiện tại
     */
    public User getCurrentUser() {
        return currentUser;
    }
    
    /**
     * Thiết lập người dùng hiện tại
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
}
