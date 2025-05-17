package app.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name="user_id", nullable = false)
    private User user;

    private String type; // MESSAGE, FRIEND_REQUEST, ...

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON string

    private boolean isRead = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(User user, String type, String payload) {
        this.user = user;
        this.type = type;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    // Getter/setter
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public boolean isRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setId(Long id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public void setType(String type) { this.type = type; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setRead(boolean read) { isRead = read; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
} 