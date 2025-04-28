package app.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;


@Entity
@Table(name = "file_attachments")
public class FileAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)   // ✅

    @Column(length = 36)          // UUID 36 ký tự
    private String id;            // dùng luôn làm PK (thay @GeneratedValue)

    public FileAttachment() { }

    @Column(nullable = false)
    private String fileName;    // Tên file gốc

    @Column(nullable = false)
    private String storagePath; // Đường dẫn lưu trữ

    private String mimeType;    // Loại file (vd: image/jpeg, application/pdf)
    private long fileSize;      // Kích thước (bytes)

    @OneToMany(mappedBy = "attachment", fetch = FetchType.LAZY)
    @JsonIgnore                // tránh vòng lặp khi JSON-hoá
    private java.util.List<Message> messages = new java.util.ArrayList<>();

       /* ➊  SHA-256 của file – duy nhất */
       @Column(length = 64, nullable = false, unique = true)
    private String sha256;

    /* ➋  Đường dẫn thumbnail (có thể null nếu không phải ảnh) */
    private String thumbPath;
    // Constructor

    public FileAttachment(String fileName, String storagePath, String mimeType, long fileSize) {
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    public FileAttachment(String fileName, String storagePath, String mimeType, long fileSize, String sha256) {
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
    public String getThumbPath() {
        return thumbPath;
    }
    public void setThumbPath(String thumbPath) {
        this.thumbPath = thumbPath;
    }
// Getters & Setters
    // ... [thêm các getter/setter cho các trường]

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public java.util.List<Message> getMessages() {
        return messages;
    }
    public void setMessages(java.util.List<Message> messages) {
        this.messages = messages;
    }
}