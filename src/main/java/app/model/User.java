package app.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

@Entity
@Table(name="users")
public class User {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(unique=true, nullable=false)
    private String username;

    @Column(nullable=false)
    private String passwordHash; // BCrypt hash

    private String status; // "ONLINE", "OFFLINE", ...
    
    @Column(unique=true)
    private String email; // Email cho xác thực hai yếu tố
    
    private String fullName; // Tên đầy đủ của người dùng
    
    private String avatarPath; // Đường dẫn đến avatar (nếu có)
    
    private boolean useDefaultAvatar; // Sử dụng avatar mặc định (hiển thị tên)
    
    private String otpCode; // Mã OTP tạm thời cho xác thực hai yếu tố
    
    private Long otpExpiryTime; // Thời gian hết hạn của mã OTP

    private LocalDateTime lastActiveAt;

    @OneToMany(mappedBy="user", cascade=CascadeType.ALL)
    private Set<Membership> memberships = new HashSet<>();

    public User() {}

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.useDefaultAvatar = true; // Mặc định sử dụng avatar hiển thị tên
    }

    // Chú ý: cần getter/setter cho passwordHash
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(Set<Membership> memberships) {
        this.memberships = memberships;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getAvatarPath() {
        return avatarPath;
    }
    
    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }
    
    public boolean isUseDefaultAvatar() {
        return useDefaultAvatar;
    }
    
    public void setUseDefaultAvatar(boolean useDefaultAvatar) {
        this.useDefaultAvatar = useDefaultAvatar;
    }
    
    public String getOtpCode() {
        return otpCode;
    }
    
    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
    
    public Long getOtpExpiryTime() {
        return otpExpiryTime;
    }
    
    public void setOtpExpiryTime(Long otpExpiryTime) {
        this.otpExpiryTime = otpExpiryTime;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    // ... getter, setter khác ...
}
