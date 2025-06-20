package app.controller.chat.handler;

import app.ServiceLocator;
import app.controller.chat.util.ChatUIHelper;
import app.model.Friendship;
import app.model.User;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import network.ClientConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handler xử lý các thao tác liên quan đến người dùng
 */
public class UserActionHandler {
    
    private final ClientConnection clientConnection;
    private final String currentUsername;
    private final Node rootNode;
    private final MessageHandler messageHandler;
    private final FriendshipHandler friendshipHandler;
    private final Consumer<List<User>> onSearchResultsUpdated;
    
    // Cache lưu thời gian yêu cầu avatar gần nhất cho mỗi user
    private final Map<String, Long> lastAvatarRequests = new HashMap<>();
    private static final long AVATAR_REQUEST_INTERVAL = 5000; // 5 giây
    
    /**
     * Khởi tạo UserActionHandler
     * @param clientConnection Kết nối client
     * @param currentUsername Username hiện tại
     * @param rootNode Node gốc để hiển thị thông báo
     * @param messageHandler Handler xử lý tin nhắn
     * @param friendshipHandler Handler xử lý kết bạn
     * @param onSearchResultsUpdated Callback khi kết quả tìm kiếm thay đổi
     */
    public UserActionHandler(ClientConnection clientConnection, String currentUsername, Node rootNode,
                             MessageHandler messageHandler, FriendshipHandler friendshipHandler,
                             Consumer<List<User>> onSearchResultsUpdated) {
        this.clientConnection = clientConnection;
        this.currentUsername = currentUsername;
        this.rootNode = rootNode;
        this.messageHandler = messageHandler;
        this.friendshipHandler = friendshipHandler;
        this.onSearchResultsUpdated = onSearchResultsUpdated;
    }
    
    /**
     * Tìm kiếm người dùng theo tên
     * @param query Từ khóa tìm kiếm
     */
    public void searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            if (onSearchResultsUpdated != null) {
                onSearchResultsUpdated.accept(List.of());
            }
            return;
        }
        
        try {
            // Gửi yêu cầu tìm kiếm - Sử dụng ServiceLocator thay vì ClientConnection trực tiếp
            List<User> results = ServiceLocator.userService().searchUsers(query);
            
            // Đảm bảo avatar path đúng cho mỗi user tìm thấy
            if (results != null && !results.isEmpty()) {
                for (User user : results) {
                    // Thực hiện fix cho từng user nếu cần
                    try {
                        String username = user.getUsername();
                        
                        // Chuẩn hóa đường dẫn avatar thành username.png
                        String standardPath = app.util.PathUtil.getStandardAvatarPath(username);
                        File standardFile = new File(standardPath);
                        File cacheFile = new File(app.util.PathUtil.getAvatarCacheDirectory() + "/" + username + ".png");
                        
                        // Cập nhật đường dẫn nếu file chuẩn tồn tại
                        if (standardFile.exists()) {
                            user.setAvatarPath(standardPath);
                            user.setUseDefaultAvatar(false);
                        }
                        
                        // Đồng bộ avatar vào cache
                        app.util.AvatarFixUtil.fixAvatarForUser(username);
                        
                        // Chỉ yêu cầu avatar từ server khi:
                        // 1. Không có file trong cache HOẶC
                        // 2. Đã qua khoảng thời gian tối thiểu kể từ lần yêu cầu trước
                        Long lastRequest = lastAvatarRequests.get(username);
                        long currentTime = System.currentTimeMillis();
                        boolean shouldRequest = lastRequest == null || 
                                              (currentTime - lastRequest > AVATAR_REQUEST_INTERVAL);
                        
                        if (shouldRequest && !cacheFile.exists() && clientConnection != null) {
                            System.out.println("[AVATAR DEBUG] Yêu cầu avatar từ server cho " + username);
                            clientConnection.requestAvatar(username);
                            lastAvatarRequests.put(username, currentTime);
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Không thể fix avatar cho user " + user.getUsername() + ": " + e.getMessage());
                    }
                }
            }
            
            if (onSearchResultsUpdated != null) {
                Platform.runLater(() -> onSearchResultsUpdated.accept(results));
            }
        } catch (Exception e) {
            ChatUIHelper.showError("Không thể tìm kiếm người dùng", e);
        }
    }
    
    /**
     * Gửi lời mời kết bạn
     * @param user Người dùng nhận lời mời
     */
    public void sendFriendRequest(User user) {
        friendshipHandler.sendFriendRequest(user);
    }
    
    /**
     * Mở cuộc trò chuyện với người dùng
     * @param user Người dùng cần chat
     */
    public void openChat(User user) {
        messageHandler.openPrivateConversation(user);
    }
    
    /**
     * Tạo nhóm chat mới
     */
    public void createGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Tạo nhóm chat mới");
        dialog.setHeaderText("Nhập tên nhóm chat");
        dialog.setContentText("Tên nhóm:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String groupName = result.get().trim();
            try {
                // Tạo nhóm với danh sách thành viên ban đầu chỉ có người tạo
                clientConnection.createGroup(groupName, List.of(currentUsername));
                ChatUIHelper.showSuccess("Đã tạo nhóm " + groupName + " thành công!");
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể tạo nhóm", e);
            }
        }
    }
    
    /**
     * Thêm người dùng vào nhóm
     * @param groupName Tên nhóm
     */
    public void addUserToGroup(String groupName) {
        if (groupName == null || !messageHandler.isGroupTarget(groupName)) {
            ChatUIHelper.showWarning("Vui lòng chọn một nhóm chat trước");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText("Thêm người dùng vào nhóm " + groupName);
        dialog.setContentText("Username:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String username = result.get().trim();
            
            try {
                long groupId = messageHandler.getGroupId(groupName);
                if (groupId <= 0) {
                    ChatUIHelper.showError("Không tìm thấy ID nhóm", null);
                    return;
                }
                
                // Tạo nhóm mới với thành viên mới
                // Lưu ý: Đây là cách tạm thời để thêm người dùng vào nhóm
                // vì ClientConnection không có phương thức addToGroup
                List<String> members = new ArrayList<>();
                members.add(currentUsername);
                members.add(username);
                
                // Tạo nhóm mới với tên và thành viên đã cập nhật
                clientConnection.createGroup(groupName, members);
                
                ChatUIHelper.showSuccess("Đã thêm " + username + " vào nhóm " + groupName + "!");
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể thêm người dùng vào nhóm", e);
            }
        }
    }
    
    /**
     * Rời khỏi nhóm chat
     * @param groupName Tên nhóm
     */
    public void leaveGroup(String groupName) {
        if (groupName == null || !messageHandler.isGroupTarget(groupName)) {
            ChatUIHelper.showWarning("Vui lòng chọn một nhóm chat trước");
            return;
        }
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc chắn muốn rời khỏi nhóm " + groupName + "?",
                ButtonType.YES, ButtonType.NO);
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                // Hiện tại không có phương thức để rời nhóm trong ClientConnection
                // Chúng ta sẽ hiển thị thông báo và chuyển về Global chat
                ChatUIHelper.showWarning("Chức năng rời nhóm hiện chưa được hỗ trợ.");
                
                // Chuyển về Global chat
                messageHandler.setCurrentTarget("Global");
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể rời khỏi nhóm", e);
            }
        }
    }
    
    /**
     * Lấy trạng thái kết bạn giữa hai người dùng
     * @param currentUser User hiện tại
     * @param targetUser User cần kiểm tra
     * @return Trạng thái kết bạn
     */
    public Friendship.Status getFriendshipStatus(User currentUser, User targetUser) {
        return friendshipHandler.getFriendshipStatus(currentUser, targetUser);
    }
    
    /**
     * Lấy username hiện tại
     * @return Username hiện tại
     */
    public String getCurrentUsername() {
        return currentUsername;
    }
}
