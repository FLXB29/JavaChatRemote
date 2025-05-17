package app.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="message_receipts")
public class MessageReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name="message_id", nullable = false)
    private Message message;

    @ManyToOne @JoinColumn(name="user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private State state; // DELIVERED, SEEN

    private LocalDateTime time = LocalDateTime.now();

    public enum State { DELIVERED, SEEN }

    public MessageReceipt() {}

    public MessageReceipt(Message message, User user, State state) {
        this.message = message;
        this.user = user;
        this.state = state;
        this.time = LocalDateTime.now();
    }

    // Getter/setter
    public Long getId() { return id; }
    public Message getMessage() { return message; }
    public User getUser() { return user; }
    public State getState() { return state; }
    public LocalDateTime getTime() { return time; }
    public void setId(Long id) { this.id = id; }
    public void setMessage(Message message) { this.message = message; }
    public void setUser(User user) { this.user = user; }
    public void setState(State state) { this.state = state; }
    public void setTime(LocalDateTime time) { this.time = time; }
} 