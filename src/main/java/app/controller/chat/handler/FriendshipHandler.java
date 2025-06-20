package app.controller.chat.handler;

import app.ServiceLocator;
import app.controller.chat.util.ChatUIHelper;
import app.model.Friendship;
import app.model.User;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import network.ClientConnection;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handler xử lý các thao tác liên quan đến kết bạn
 */
public class FriendshipHandler {
    
    private final ClientConnection clientConnection;
    private final String currentUsername;
    private final Consumer<List<Friendship>> onFriendRequestsUpdated;
    private final Runnable onFriendshipChanged;
    
    /**
     * Khởi tạo FriendshipHandler
     * @param clientConnection Kết nối client
     * @param currentUsername Username hiện tại
     * @param onFriendRequestsUpdated Callback khi danh sách lời mời kết bạn thay đổi
     * @param onFriendshipChanged Callback khi trạng thái kết bạn thay đổi
     */
    public FriendshipHandler(ClientConnection clientConnection, String currentUsername,
                             Consumer<List<Friendship>> onFriendRequestsUpdated,
                             Runnable onFriendshipChanged) {
        this.clientConnection = clientConnection;
        this.currentUsername = currentUsername;
        this.onFriendRequestsUpdated = onFriendRequestsUpdated;
        this.onFriendshipChanged = onFriendshipChanged;
    }
    
    /**
     * Gửi lời mời kết bạn
     * @param targetUser User được mời kết bạn
     */
    public void sendFriendRequest(User targetUser) {
        // Kiểm tra targetUser có hợp lệ không
        if (targetUser == null) {
            ChatUIHelper.showError("Không thể gửi lời mời kết bạn", new IllegalArgumentException("Người dùng không tồn tại"));
            return;
        }
        
        String targetUsername = targetUser.getUsername();
        
        // Kiểm tra username có hợp lệ không
        if (targetUsername == null || targetUsername.isBlank()) {
            ChatUIHelper.showError("Không thể gửi lời mời kết bạn", new IllegalArgumentException("Username không hợp lệ"));
            return;
        }
        
        // Đảm bảo currentUsername có giá trị hợp lệ
        String actualUsername = currentUsername;
        if (actualUsername == null || actualUsername.isBlank()) {
            // Thử lấy từ UserService
            User userFromService = ServiceLocator.userService().getCurrentUser();
            if (userFromService != null) {
                actualUsername = userFromService.getUsername();
                System.out.println("[INFO] Retrieved current username from UserService: " + actualUsername);
            }
        }
        
        // Kiểm tra sau khi đã thử lấy lại
        if (actualUsername == null || actualUsername.isBlank()) {
            ChatUIHelper.showError("Không thể gửi lời mời kết bạn", 
                    new IllegalStateException("Chưa đăng nhập hoặc phiên làm việc đã hết hạn"));
            return;
        }
        
        // Kiểm tra không tự kết bạn với chính mình
        if (targetUsername.equals(actualUsername)) {
            ChatUIHelper.showError("Không thể gửi lời mời kết bạn", new IllegalArgumentException("Bạn không thể kết bạn với chính mình"));
            return;
        }
        
        // Kiểm tra đã là bạn bè chưa
        User currentUser = ServiceLocator.userService().getUser(actualUsername);
        if (currentUser != null) {
            Friendship.Status status = getFriendshipStatus(currentUser, targetUser);
            if (status == Friendship.Status.ACCEPTED) {
                ChatUIHelper.showWarning("Bạn đã là bạn bè với " + targetUsername);
                return;
            } else if (status == Friendship.Status.PENDING) {
                ChatUIHelper.showWarning("Đã gửi lời mời kết bạn đến " + targetUsername + " trước đó");
                return;
            }
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Gửi lời mời kết bạn tới " +
                        (targetUser.getFullName() != null ? targetUser.getFullName() : targetUsername) + "?",
                ButtonType.OK, ButtonType.CANCEL);
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Ghi log nếu đang dùng username từ nguồn khác
                if (actualUsername != currentUsername) {
                    System.out.println("[INFO] Using username " + actualUsername + " instead of " + currentUsername);
                }
                
                System.out.println("[DEBUG] Gửi lời mời kết bạn từ " + actualUsername + " đến " + targetUsername);
                clientConnection.sendFriendRequest(actualUsername, targetUsername);
                
                Platform.runLater(() -> {
                    ChatUIHelper.showSuccess("Đã gửi lời mời kết bạn đến " + targetUsername + "!");
                    
                    // Cập nhật UI nếu cần
                    if (onFriendshipChanged != null) {
                        onFriendshipChanged.run();
                    }
                });
                
            } catch (Exception e) {
                System.err.println("[ERROR] Không thể gửi lời mời kết bạn: " + e.getMessage());
                e.printStackTrace();
                ChatUIHelper.showError("Không thể gửi lời mời kết bạn: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Chấp nhận lời mời kết bạn
     * @param friendship Lời mời kết bạn
     */
    public void acceptFriendRequest(Friendship friendship) {
        if (friendship == null) {
            System.err.println("[ERROR] Không thể chấp nhận lời mời kết bạn: đối tượng friendship là null");
            ChatUIHelper.showError("Không thể chấp nhận lời mời", new NullPointerException("Lời mời không tồn tại"));
            return;
        }
        
        try {
            // Kiểm tra trạng thái của lời mời
            if (friendship.getStatus() != Friendship.Status.PENDING) {
                System.err.println("[ERROR] Không thể chấp nhận lời mời kết bạn: trạng thái không phải PENDING mà là " + friendship.getStatus());
                ChatUIHelper.showError("Không thể chấp nhận lời mời", 
                        new IllegalStateException("Lời mời không trong trạng thái chờ chấp nhận"));
                return;
            }
            
            // Kiểm tra người gửi
            User sender = friendship.getUser1();
            if (sender == null || sender.getUsername() == null) {
                System.err.println("[ERROR] Không thể chấp nhận lời mời kết bạn: người gửi là null hoặc không có username");
                ChatUIHelper.showError("Không thể chấp nhận lời mời", 
                        new IllegalStateException("Không thể xác định người gửi lời mời"));
                return;
            }
            
            // Lấy username hiện tại - sử dụng biến cục bộ thay vì cố gắng thay đổi currentUsername
            String actualUsername = currentUsername;
            if (actualUsername == null || actualUsername.isBlank()) {
                System.err.println("[ERROR] Không thể chấp nhận lời mời kết bạn: currentUsername là null hoặc rỗng");
                
                // Thử lấy từ UserService
                User currentUser = ServiceLocator.userService().getCurrentUser();
                if (currentUser != null && currentUser.getUsername() != null) {
                    actualUsername = currentUser.getUsername();
                    System.out.println("[INFO] Đã lấy được username từ UserService: " + actualUsername);
                } else {
                    ChatUIHelper.showError("Không thể chấp nhận lời mời", 
                            new IllegalStateException("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn"));
                    return;
                }
            }
            
            String fromUser = sender.getUsername();
            String toUser = actualUsername; // Sử dụng biến cục bộ
            System.out.println("[DEBUG] Chấp nhận lời mời kết bạn từ " + fromUser + " đến " + toUser);
            
            // Thử gửi yêu cầu chấp nhận
            try {
                clientConnection.acceptFriendRequest(fromUser, toUser);
                System.out.println("[DEBUG] Đã gửi yêu cầu chấp nhận lời mời kết bạn thành công");
                
                final String finalFromUser = fromUser; // Tạo biến final để sử dụng trong lambda
                Platform.runLater(() -> {
                    // Refresh danh sách lời mời
                    refreshFriendRequests();
                    
                    // Thông báo thành công
                    ChatUIHelper.showSuccess("Đã chấp nhận lời mời kết bạn từ " + finalFromUser + "!");
                    
                    // Cập nhật UI nếu cần
                    if (onFriendshipChanged != null) {
                        onFriendshipChanged.run();
                    }
                });
            } catch (IllegalArgumentException e) {
                System.err.println("[ERROR] Tham số không hợp lệ khi chấp nhận lời mời: " + e.getMessage());
                ChatUIHelper.showError("Không thể chấp nhận lời mời", e);
            } catch (RuntimeException e) {
                System.err.println("[ERROR] Lỗi runtime khi chấp nhận lời mời: " + e.getMessage());
                ChatUIHelper.showError("Không thể chấp nhận lời mời", e);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi không xác định khi chấp nhận lời mời kết bạn: " + e.getMessage());
            e.printStackTrace();
            ChatUIHelper.showError("Không thể chấp nhận lời mời", e);
        }
    }
    
    /**
     * Từ chối lời mời kết bạn
     * @param friendship Lời mời kết bạn
     */
    public void rejectFriendRequest(Friendship friendship) {
        if (friendship == null) return;
        
        String fromUsername = friendship.getUser1().getUsername();
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Từ chối lời mời kết bạn từ " + fromUsername + "?",
                ButtonType.OK, ButtonType.CANCEL);
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Thay đổi: Gọi API từ chối lời mời
                clientConnection.rejectFriendRequest(fromUsername, currentUsername);
                
                Platform.runLater(() -> {
                    // Refresh danh sách lời mời
                    refreshFriendRequests();
                    
                    // Thông báo thành công
                    ChatUIHelper.showSuccess("Đã từ chối lời mời kết bạn từ " + fromUsername + ".");
                    
                    // Cập nhật UI nếu cần
                    if (onFriendshipChanged != null) {
                        onFriendshipChanged.run();
                    }
                });
                
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể từ chối lời mời", e);
            }
        }
    }
    
    /**
     * Hủy lời mời kết bạn đã gửi
     * @param friendship Lời mời kết bạn
     */
    public void cancelFriendRequest(Friendship friendship) {
        if (friendship == null) return;
        
        String toUsername = friendship.getUser2().getUsername();
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Hủy lời mời kết bạn đã gửi đến " + toUsername + "?",
                ButtonType.OK, ButtonType.CANCEL);
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Gọi API từ chối lời mời (cùng API với từ chối)
                clientConnection.rejectFriendRequest(currentUsername, toUsername);
                
                Platform.runLater(() -> {
                    // Refresh danh sách lời mời
                    refreshFriendRequests();
                    
                    // Thông báo thành công
                    ChatUIHelper.showSuccess("Đã hủy lời mời kết bạn đến " + toUsername + ".");
                    
                    // Cập nhật UI nếu cần
                    if (onFriendshipChanged != null) {
                        onFriendshipChanged.run();
                    }
                });
                
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể hủy lời mời", e);
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
        return ServiceLocator.friendship().getFriendshipStatus(currentUser, targetUser);
    }
    
    /**
     * Làm mới danh sách lời mời kết bạn
     */
    public void refreshFriendRequests() {
        try {
            // Tạo biến localUsername để tránh thay đổi biến thành viên
            String localUsername = currentUsername;
            
            // Nếu username là null, thử lấy từ UserService
            if (localUsername == null || localUsername.isBlank()) {
                User currentUserFromService = ServiceLocator.userService().getCurrentUser();
                if (currentUserFromService != null && currentUserFromService.getUsername() != null) {
                    localUsername = currentUserFromService.getUsername();
                    System.out.println("[INFO] Đã lấy username từ UserService: " + localUsername);
                } else {
                    System.out.println("[ERROR] Không thể làm mới lời mời kết bạn: không thể lấy thông tin người dùng hiện tại");
                    return;
                }
            }
            
            // Lấy User hiện tại từ username đã xác nhận
            User me = ServiceLocator.userService().getUser(localUsername);
            
            if (me == null) {
                System.out.println("[ERROR] Không thể làm mới lời mời kết bạn: User không tồn tại với username=" + localUsername);
                return;
            }
            
            // Lấy danh sách tất cả lời mời kết bạn (cả gửi và nhận)
            List<Friendship> pending = ServiceLocator.friendship().getAllPendingRequests(me);
            System.out.println("[DEBUG] Làm mới lời mời kết bạn cho " + me.getUsername() + 
                ": tìm thấy " + (pending != null ? pending.size() : "null") + " lời mời (cả gửi và nhận)");
            
            // In thông tin chi tiết về các lời mời kết bạn
            if (pending != null && !pending.isEmpty()) {
                for (Friendship friendship : pending) {
                    System.out.println("[DEBUG] Lời mời từ: " + 
                        (friendship.getUser1() != null ? friendship.getUser1().getUsername() : "null") + 
                        ", đến: " + (friendship.getUser2() != null ? friendship.getUser2().getUsername() : "null") + 
                        ", trạng thái: " + friendship.getStatus());
                }
            }
            
            // Cập nhật UI
            if (onFriendRequestsUpdated != null) {
                onFriendRequestsUpdated.accept(pending);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khi làm mới lời mời kết bạn: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
