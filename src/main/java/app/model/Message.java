package app.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "conv_id", nullable = false)
    private Conversation conversation;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;  // tin nhắn text

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private FileAttachment attachment;          // FK → file_attachments

    private LocalDateTime createdAt;

    // Constructors
    public Message() {
        this.createdAt = LocalDateTime.now();
    }

    public Message(Conversation conv, User sender, String content) {
        this.conversation = conv;
        this.sender = sender;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    // Version with attachment
    public Message(Conversation conv, User sender,
                   String content, FileAttachment attachment){
        this(conv, sender, content);          // gọi ctor gốc
        this.attachment = attachment;   // gọi trực tiếp, KHÔNG thêm vào list
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public FileAttachment getAttachment() {
        return attachment;
    }

    // ───── setter attachment ─────
    public void setAttachment(FileAttachment attachment){
        /* gắn liên kết mới */
        this.attachment = attachment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Optional: Override toString() for better logging/debugging
    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", sender=" + (sender != null ? sender.getUsername() : "null") +
                ", content='" + content + '\'' +
                ", hasAttachment=" + (attachment != null) +
                ", createdAt=" + createdAt +
                '}';
    }
}