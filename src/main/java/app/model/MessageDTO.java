package app.model;

import java.time.LocalDateTime;

/**
 * Model lưu thông tin tin nhắn.
 */
public class MessageDTO {
    private String user;         // Người gửi
    private String content;      // Nội dung
    private LocalDateTime time;  // Thời gian

    public MessageDTO(String user, String content, LocalDateTime time) {
        this.user = user;
        this.content = content;
        this.time = time;
    }

    public String getUser() { return user; }
    public String getContent() { return content; }
    public LocalDateTime getTime() { return time; }
}
