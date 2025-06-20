package app.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="friendships", uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id"}))
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name="user1_id", nullable = false)
    private User user1;

    @ManyToOne @JoinColumn(name="user2_id", nullable = false)
    private User user2;

    @Enumerated(EnumType.STRING)
    private Status status; // PENDING, ACCEPTED, BLOCKED, REJECTED

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status { PENDING, ACCEPTED, BLOCKED, REJECTED }

    public Friendship() {}

    public Friendship(User user1, User user2, Status status) {
        this.user1 = user1;
        this.user2 = user2;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getter/setter
    public Long getId() { return id; }
    public User getUser1() { return user1; }
    public User getUser2() { return user2; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setId(Long id) { this.id = id; }
    public void setUser1(User user1) { this.user1 = user1; }
    public void setUser2(User user2) { this.user2 = user2; }
    public void setStatus(Status status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
} 