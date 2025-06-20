package app.controller.chat.model;

import java.time.LocalDateTime;

/**
 * Model class cho các cell trong danh sách chat gần đây
 */
public class RecentChatCellData {
    public final String chatName; // tên bạn bè hoặc nhóm
    public final String lastMessage;
    public final String timeString;
    public final String avatarPath;
    public final int unreadCount;
    
    // Thêm trường để lưu thời gian cho việc sắp xếp
    private LocalDateTime extraTime;

    /**
     * Tạo một đối tượng dữ liệu cho cell trong recent chat
     * @param chatName Tên chat (tên người dùng hoặc nhóm)
     * @param lastMessage Tin nhắn cuối cùng
     * @param timeString Thời gian tin nhắn cuối
     * @param avatarPath Đường dẫn đến avatar
     * @param unreadCount Số tin nhắn chưa đọc
     */
    public RecentChatCellData(String chatName, String lastMessage, String timeString, String avatarPath, int unreadCount) {
        this.chatName = chatName;
        this.lastMessage = lastMessage;
        this.timeString = timeString;
        this.avatarPath = avatarPath;
        this.unreadCount = unreadCount;
        this.extraTime = null;
    }
    
    /**
     * Thiết lập thời gian bổ sung cho mục đích sắp xếp
     */
    public void setExtraTime(LocalDateTime time) {
        this.extraTime = time;
    }
    
    /**
     * Lấy thời gian bổ sung
     */
    public LocalDateTime getExtraTime() {
        return extraTime;
    }
    
    /**
     * Trả về biểu diễn dạng chuỗi của đối tượng
     */
    @Override
    public String toString() {
        return String.format("%s [%s] (%s) - Unread: %d", 
            chatName, 
            lastMessage.isEmpty() ? "Không có tin nhắn" : lastMessage, 
            timeString.isEmpty() ? "Không có thời gian" : timeString, 
            unreadCount);
    }
}
