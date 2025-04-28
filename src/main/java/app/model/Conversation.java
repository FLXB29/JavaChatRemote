package app.model;


import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="conversations")
public class Conversation {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    // "PRIVATE", "GROUP", "BROADCAST", ...
    @Column(nullable=false)
    private String type;

    private String name; // group name, etc.

    private LocalDateTime createdAt;

    @OneToMany(mappedBy="conversation", cascade=CascadeType.ALL)
    private Set<Membership> memberships = new HashSet<>();

    @OneToMany(mappedBy="conversation", cascade=CascadeType.ALL)
    private Set<Message> messages = new HashSet<>();

    public Conversation() {
        this.createdAt = LocalDateTime.now();
    }

    public Conversation(String type, String name) {
        this.type = type;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    // getters & setters ...

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(Set<Membership> memberships) {
        this.memberships = memberships;
    }

    public Set<Message> getMessages() {
        return messages;
    }

    public void setMessages(Set<Message> messages) {
        this.messages = messages;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
