package app.controller.chat.handler;

import app.controller.chat.component.MessageBubble;
import app.controller.chat.component.ToastNotification;
import app.controller.chat.util.ChatUIHelper;
import app.model.Conversation;
import app.model.User;
import app.service.ChatService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler xử lý các thao tác liên quan đến file
 */
public class FileHandler {
    
    private final ChatService chatService;
    private Window ownerWindow;
    private final Map<String, VBox> fileBubbleMap = new HashMap<>();
    private VBox messagesContainer;
    private Node rootNode;
    private String currentUsername;
    private MessageHandler messageHandler;
    
    // UI components for file upload progress
    private VBox fileUploadContainer;
    private ProgressBar fileUploadProgress;
    private Label fileUploadLabel;
    
    // UI components for file download progress
    private VBox fileDownloadContainer;
    private ProgressBar fileDownloadProgress;
    private Label fileDownloadLabel;
    
    /**
     * Khởi tạo FileHandler
     * @param chatService Service xử lý chat
     * @param ownerWindow Cửa sổ chính
     */
    public FileHandler(ChatService chatService, Window ownerWindow) {
        this.chatService = chatService;
        this.ownerWindow = ownerWindow;
    }
    
    /**
     * Cập nhật cửa sổ chính
     * @param window Cửa sổ mới
     */
    public void setOwnerWindow(Window window) {
        this.ownerWindow = window;
    }
    
    /**
     * Thiết lập các tham số cần thiết khác
     * @param messagesContainer Container hiển thị tin nhắn
     * @param rootNode Node gốc để hiển thị thông báo
     * @param currentUsername Tên người dùng hiện tại
     * @param messageHandler Handler xử lý tin nhắn
     */
    public void setup(VBox messagesContainer, Node rootNode, String currentUsername, MessageHandler messageHandler) {
        this.messagesContainer = messagesContainer;
        this.rootNode = rootNode;
        this.currentUsername = currentUsername;
        this.messageHandler = messageHandler;
    }
    
    /**
     * Chọn và gửi file
     * @param conversationId ID cuộc trò chuyện
     */
    public void selectAndSendFile(long conversationId) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi");
        
        // Thêm các bộ lọc file phổ biến
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả các file", "*.*"),
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Tài liệu", "*.pdf", "*.doc", "*.docx", "*.txt")
        );
        
        // Hiển thị hộp thoại chọn file
        File selectedFile = fileChooser.showOpenDialog(ownerWindow);
        
        if (selectedFile != null) {
            try {
                // Gửi file với đường dẫn đầy đủ
                chatService.sendFile(conversationId, selectedFile.getAbsolutePath());
                
                // Hiển thị thông báo thành công
                ChatUIHelper.showSuccess("Đã gửi file " + selectedFile.getName());
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể gửi file", e);
            }
        }
    }
    
    /**
     * Tạo bubble hiển thị tin nhắn file
     * @param fileName Tên file
     * @param fileSize Kích thước file
     * @param key Khóa của file
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @return VBox chứa bubble
     */
    /**
     * Tạo bubble cho tin nhắn file
     * @param fileName Tên file
     * @param fileSize Kích thước file
     * @param key Khóa của file
     * @param isOutgoing Có phải tin nhắn gửi đi không
     * @return VBox chứa bubble tin nhắn file
     */
    public VBox createFileMessageBubble(String fileName, long fileSize, String key, boolean isOutgoing) {
        System.out.println("[DEBUG] Tạo file message bubble cho file: " + fileName + ", key: " + key);
        
        // Tạo callback download
        MessageBubble.DownloadCallback downloadCallback = (fileId, name, button) -> {
            downloadFile(fileId, name);
        };
        
        // Kiểm tra xem đã có thumbnail trong cache chưa
        byte[] thumbData = chatService.getThumb(key);
        if (thumbData != null) {
            System.out.println("[DEBUG] Đã có sẵn thumbnail trong cache cho: " + key);
        } else {
            System.out.println("[DEBUG] Chưa có thumbnail trong cache cho: " + key);
        }
        
        // Tạo file bubble
        VBox fileBubble = MessageBubble.createFileMessageBubble(fileName, fileSize, key, thumbData, isOutgoing, downloadCallback);
        
        // Lưu lại reference để có thể cập nhật sau này
        fileBubbleMap.put(key, fileBubble);
        System.out.println("[DEBUG] Đã lưu file bubble vào map với key: " + key);
        
        // Nếu chưa có thumbnail, yêu cầu bất đồng bộ và cập nhật khi có
        if (thumbData == null) {
            System.out.println("[DEBUG] Yêu cầu thumbnail bất đồng bộ cho: " + key);
            // Sử dụng CompletableFuture để xử lý thumbnail bất đồng bộ
            chatService.getThumbFuture(key).thenAccept(data -> {
                if (data != null) {
                    System.out.println("[DEBUG] Đã nhận được thumbnail bất đồng bộ cho: " + key);
                    updateThumbnailUI(fileBubble, data, key);
                } else {
                    System.out.println("[WARNING] Nhận được thumbnail null cho: " + key);
                }
            }).exceptionally(ex -> {
                System.err.println("[ERROR] Lỗi khi xử lý thumbnail bất đồng bộ: " + ex.getMessage());
                return null;
            });
        }
        
        return fileBubble;
    }
    
    /**
     * Làm mới thumbnail cho file
     * @param fileKey Khóa của file
     */
    public void refreshThumbnail(String fileKey) {
        if (fileKey == null) return;
        
        VBox fileBubble = fileBubbleMap.get(fileKey);
        if (fileBubble == null) {
            System.err.println("[WARNING] Không tìm thấy file bubble cho key: " + fileKey);
            return;
        }
        
        System.out.println("[DEBUG] Đang làm mới thumbnail cho file: " + fileKey);
        
        // Kiểm tra nếu đã có thumbnail trong cache
        byte[] thumbData = chatService.getThumb(fileKey);
        if (thumbData != null) {
            System.out.println("[DEBUG] Đã tìm thấy thumbnail trong cache cho: " + fileKey);
            updateThumbnailUI(fileBubble, thumbData, fileKey);
            return;
        }
        
        System.out.println("[DEBUG] Thumbnail chưa có trong cache, đang yêu cầu bất đồng bộ: " + fileKey);
        
        // Sử dụng CompletableFuture để xử lý thumbnail bất đồng bộ
        chatService.getThumbFuture(fileKey).thenAccept(data -> {
            if (data != null) {
                System.out.println("[DEBUG] Đã nhận được thumbnail bất đồng bộ cho: " + fileKey);
                updateThumbnailUI(fileBubble, data, fileKey);
            } else {
                System.out.println("[WARNING] Nhận được thumbnail null cho: " + fileKey);
            }
        }).exceptionally(ex -> {
            System.err.println("[ERROR] Lỗi khi xử lý thumbnail bất đồng bộ: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * Cập nhật UI với thumbnail
     * @param fileBubble Bubble cần cập nhật
     * @param thumbData Dữ liệu thumbnail
     * @param fileKey Khóa file (chỉ dùng cho log)
     */
    private void updateThumbnailUI(VBox fileBubble, byte[] thumbData, String fileKey) {
        if (thumbData == null) {
            System.err.println("[ERROR] Dữ liệu thumbnail null cho key: " + fileKey);
            return;
        }
        
        if (fileBubble == null) {
            System.err.println("[ERROR] File bubble null cho key: " + fileKey);
            return;
        }
        
        System.out.println("[DEBUG] Bắt đầu cập nhật UI thumbnail cho: " + fileKey);
        
        Platform.runLater(() -> {
            try {
                // Tìm ImageView trong bubble và cập nhật
                ImageView thumbView = (ImageView) fileBubble.lookup("#thumb");
                if (thumbView != null) {
                    Image image = new Image(new ByteArrayInputStream(thumbData));
                    thumbView.setImage(image);
                    System.out.println("[DEBUG] Đã cập nhật thumbnail có sẵn cho " + fileKey + ", kích thước: " + image.getWidth() + "x" + image.getHeight());
                } else {
                    Label loadingLabel = (Label) fileBubble.lookup("#loading-thumb");
                    if (loadingLabel != null) {
                        int index = fileBubble.getChildren().indexOf(loadingLabel);
                        if (index >= 0) {
                            Image image = new Image(new ByteArrayInputStream(thumbData));
                            ImageView iv = new ImageView(image);
                            iv.setFitWidth(260);
                            iv.setPreserveRatio(true);
                            iv.setId("thumb");
                            fileBubble.getChildren().set(index, iv);
                            System.out.println("[DEBUG] Đã thay thế loading bằng thumbnail cho " + fileKey + ", kích thước: " + image.getWidth() + "x" + image.getHeight());
                        } else {
                            System.err.println("[WARNING] Không tìm thấy vị trí của loading label trong file bubble: " + fileKey);
                        }
                    } else {
                        System.err.println("[WARNING] Không tìm thấy loading label hoặc thumbnail view trong file bubble: " + fileKey);
                    }
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Không thể cập nhật thumbnail: " + e.getMessage() + " cho key: " + fileKey);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Tải xuống file
     * @param fileKey Khóa của file
     * @param fileName Tên file
     */
    public void downloadFile(String fileKey, String fileName) {
        // Hiển thị hộp thoại chọn vị trí lưu
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu file");
        fileChooser.setInitialFileName(fileName);
        
        // Thêm bộ lọc phù hợp với loại file
        String extension = getFileExtension(fileName);
        if (extension != null) {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(extension.toUpperCase(), "*." + extension)
            );
        }
        
        // Hiển thị hộp thoại
        File saveFile = fileChooser.showSaveDialog(ownerWindow);
        
        if (saveFile != null) {
            try {
                // Lấy dữ liệu file từ service - điều chỉnh theo API thực tế
                byte[] fileData = chatService.getFileData(fileKey);
                
                if (fileData == null) {
                    ChatUIHelper.showError("Không thể tải file", null);
                    return;
                }
                
                // Lưu file
                Files.write(saveFile.toPath(), fileData);
                
                // Hiển thị thông báo thành công
                ChatUIHelper.showSuccess("Đã tải xuống file " + fileName);
            } catch (Exception e) {
                ChatUIHelper.showError("Không thể tải xuống file", e);
            }
        }
    }
    
    /**
     * Lấy phần mở rộng của file
     * @param fileName Tên file
     * @return Phần mở rộng hoặc null
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Xử lý gửi file trong chat (cả private và group)
     */
    public void handleFileSend() {
        if (rootNode == null || messageHandler == null) {
            System.err.println("[ERROR] rootNode hoặc messageHandler null trong handleFileSend");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi");
        
        // Add file filters
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Tất cả các file", "*.*"),
            new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif"),
            new FileChooser.ExtensionFilter("Tài liệu", "*.pdf", "*.doc", "*.docx", "*.txt"),
            new FileChooser.ExtensionFilter("Lưu trữ", "*.zip", "*.rar", "*.7z")
        );
        
        File file = fileChooser.showOpenDialog(rootNode.getScene().getWindow());
        
        if (file != null && file.exists()) {
            // Check file size (limit 50MB)
            long fileSize = file.length();
            if (fileSize > 50 * 1024 * 1024) {
                ToastNotification.show("File quá lớn! Giới hạn 50MB", rootNode, 3, true);
                return;
            }
            
            // Show upload progress
            showFileUploadProgress(file.getName());
            
            // Determine target type
            String currentTarget = messageHandler.getCurrentTarget();
            boolean isGroup = messageHandler.isGroupTarget(currentTarget);
            
            if (isGroup) {
                // Send file to group
                long groupId = messageHandler.getGroupId(currentTarget);
                if (groupId > 0) {
                    sendFileToGroup(file, groupId, currentTarget);
                } else {
                    hideFileUploadProgress();
                    ToastNotification.show("Không thể gửi file: Không tìm thấy group ID", rootNode, 3, true);
                }
            } else if (currentTarget.equals("Global")) {
                // Send file to global chat
                sendFileToGlobal(file);
            } else {
                // Send file to private chat
                sendFileToUser(file, currentTarget);
            }
        }
    }

    /**
     * Gửi file đến group chat
     */
    private void sendFileToGroup(File file, long groupId, String groupName) {
        Task<Void> uploadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Read file
                    byte[] fileData = Files.readAllBytes(file.toPath());
                    String fileName = file.getName();
                    
                    // Update progress
                    updateMessage("Đang chuẩn bị file...");
                    
                    // Hiển thị tin nhắn file trên UI ngay lập tức
                    String fileKey = fileName + "_" + System.currentTimeMillis(); // Tạo key tạm thời
                    
                    Platform.runLater(() -> {
                        // Gửi file
                        chatService.sendFile(groupId, file.getAbsolutePath());
                        
                        // Hiển thị tin nhắn file
                        String fileMsg = String.format("[FILE]%s|%d|%s", fileName, file.length(), fileKey);
                        messageHandler.displayMessage(currentUsername, fileMsg, true, LocalDateTime.now());
                        
                        // Nếu là ảnh, tạo và hiển thị thumbnail
                        if (isImageFile(fileName)) {
                            try {
                                byte[] thumbData = generateThumbnail(file);
                                if (thumbData != null && fileBubbleMap.containsKey(fileKey)) {
                                    // Cập nhật thumbnail vào cache
                                    chatService.addThumbToCache(fileKey, thumbData);
                                    // Làm mới thumbnail
                                    refreshThumbnail(fileKey);
                                }
                            } catch (Exception e) {
                                System.err.println("[ERROR] Không thể tạo thumbnail: " + e.getMessage());
                            }
                        }
                    });

                    // Fake progress for UI
                    for (int i = 0; i < 100; i += 5) {
                        updateProgress(i, 100);
                        updateMessage("Đang tải lên... " + i + "%");
                        Thread.sleep(50);
                    }
                    
                    Platform.runLater(() -> {
                        hideFileUploadProgress();
                        ToastNotification.show("File đã được gửi thành công!", rootNode, 2, true);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        hideFileUploadProgress();
                        ToastNotification.show("Lỗi khi gửi file: " + e.getMessage(), rootNode, 3, true);
                    });
                    e.printStackTrace();
                }
                
                return null;
            }
        };
        
        // Bind progress to UI
        if (fileUploadProgress != null) {
            fileUploadProgress.progressProperty().bind(uploadTask.progressProperty());
            fileUploadLabel.textProperty().bind(uploadTask.messageProperty());
        }
        
        // Start upload
        Thread uploadThread = new Thread(uploadTask);
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * Gửi file đến global chat
     */
    private void sendFileToGlobal(File file) {
        Task<Void> uploadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    updateMessage("Đang chuẩn bị file...");
                    String fileName = file.getName();

                    // Lấy global conversation ID từ MessageHandler nếu có
                    Long globalConvId = null;
                    if (messageHandler != null && messageHandler.getCurrentTarget().equals("Global")) {
                        globalConvId = messageHandler.getCurrentConversationId();
                    }

                    // Nếu không có ID hợp lệ, sử dụng ID mặc định cho Global chat
                    if (globalConvId == null || globalConvId <= 0) {
                        // Thường Global chat có ID = 1 trong nhiều hệ thống
                        globalConvId = 1L;
                    }

                    // Hiển thị tin nhắn file trên UI ngay lập tức
                    String fileKey = fileName + "_" + System.currentTimeMillis(); // Tạo key tạm thời
                    
                    // Capture ID vào biến final để sử dụng trong lambda
                    final Long finalGlobalConvId = globalConvId;
                    
                    // Sử dụng chatService để gửi file đến conversation cụ thể
                    Platform.runLater(() -> {
                        System.out.println("[DEBUG] Gửi file đến Global conversation ID: " + finalGlobalConvId);
                        chatService.sendFile(finalGlobalConvId, file.getAbsolutePath());
                        
                        // Hiển thị tin nhắn file
                        String fileMsg = String.format("[FILE]%s|%d|%s", fileName, file.length(), fileKey);
                        messageHandler.displayMessage(currentUsername, fileMsg, true, LocalDateTime.now());
                        
                        // Nếu là ảnh, tạo và hiển thị thumbnail
                        if (isImageFile(fileName)) {
                            try {
                                byte[] thumbData = generateThumbnail(file);
                                if (thumbData != null && fileBubbleMap.containsKey(fileKey)) {
                                    // Cập nhật thumbnail vào cache
                                    chatService.addThumbToCache(fileKey, thumbData);
                                    // Làm mới thumbnail
                                    refreshThumbnail(fileKey);
                                }
                            } catch (Exception e) {
                                System.err.println("[ERROR] Không thể tạo thumbnail: " + e.getMessage());
                            }
                        }
                    });

                    // Fake progress for UI
                    for (int i = 0; i < 100; i += 5) {
                        updateProgress(i, 100);
                        updateMessage("Đang tải lên... " + i + "%");
                        Thread.sleep(50);
                    }
                    
                    Platform.runLater(() -> {
                        hideFileUploadProgress();
                        ToastNotification.show("File đã được gửi thành công!", rootNode, 2, true);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        hideFileUploadProgress();
                        ToastNotification.show("Lỗi khi gửi file: " + e.getMessage(), rootNode, 3, true);
                    });
                    e.printStackTrace();
                }
                
                return null;
            }
        };
        
        // Bind progress to UI
        if (fileUploadProgress != null) {
            fileUploadProgress.progressProperty().bind(uploadTask.progressProperty());
            fileUploadLabel.textProperty().bind(uploadTask.messageProperty());
        }
        
        // Start upload
        Thread uploadThread = new Thread(uploadTask);
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * Gửi file đến private chat
     */
    private void sendFileToUser(File file, String targetUsername) {
        Task<Void> uploadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    updateMessage("Đang chuẩn bị file...");
                    String fileName = file.getName();
                    
                    // Kiểm tra targetUsername
                    if (targetUsername == null || targetUsername.isBlank()) {
                        throw new IllegalArgumentException("Tên người dùng đích không hợp lệ");
                    }
                    
                    // Kiểm tra currentUsername
                    if (currentUsername == null || currentUsername.isBlank()) {
                        // Thử lấy từ MessageHandler nếu có
                        if (messageHandler != null && messageHandler.getCurrentUser() != null) {
                            currentUsername = messageHandler.getCurrentUser().getUsername();
                            System.out.println("[INFO] Đã cập nhật currentUsername từ MessageHandler: " + currentUsername);
                        } else {
                            throw new IllegalArgumentException("Tên người dùng hiện tại không hợp lệ");
                        }
                    }
                    
                    System.out.println("[DEBUG] Gửi file từ " + currentUsername + " đến user: " + targetUsername);
                    
                    // Tìm conversation ID cho chat riêng tư (trên thread hiện tại để tránh race condition)
                    Conversation conv = null;
                    try {
                        conv = messageHandler.findPrivateConversation(currentUsername, targetUsername);
                    } catch (Exception e) {
                        System.err.println("[ERROR] Lỗi khi tìm cuộc trò chuyện: " + e.getMessage());
                        e.printStackTrace();
                        throw new Exception("Không thể tìm cuộc trò chuyện với " + targetUsername);
                    }
                    
                    if (conv == null || conv.getId() == null || conv.getId() <= 0) {
                        throw new Exception("Không tìm thấy cuộc trò chuyện với " + targetUsername);
                    }
                    
                    // Lấy conversation ID
                    final long convId = conv.getId();
                    System.out.println("[DEBUG] Gửi file trong conversation ID: " + convId);
                    
                    // Gửi file
                    try {
                        chatService.sendFile(convId, file.getAbsolutePath());
                    } catch (Exception e) {
                        System.err.println("[ERROR] Lỗi khi gửi file: " + e.getMessage());
                        e.printStackTrace();
                        throw new Exception("Không thể gửi file: " + e.getMessage());
                    }
                    
                    // Hiển thị tin nhắn file trên UI
                    final String fileKey = fileName + "_" + System.currentTimeMillis(); // Tạo key tạm thời
                    final String fileMsg = String.format("[FILE]%s|%d|%s", fileName, file.length(), fileKey);
                    final String finalUsername = currentUsername; // Lưu lại để tránh race condition
                    
                    Platform.runLater(() -> {
                        messageHandler.displayMessage(finalUsername, fileMsg, true, LocalDateTime.now());
                        
                        // Nếu là ảnh, tạo và hiển thị thumbnail
                        if (isImageFile(fileName)) {
                            try {
                                byte[] thumbData = generateThumbnail(file);
                                if (thumbData != null && fileBubbleMap.containsKey(fileKey)) {
                                    // Cập nhật thumbnail vào cache
                                    chatService.addThumbToCache(fileKey, thumbData);
                                    // Làm mới thumbnail
                                    refreshThumbnail(fileKey);
                                }
                            } catch (Exception e) {
                                System.err.println("[ERROR] Không thể tạo thumbnail: " + e.getMessage());
                            }
                        }
                    });

                    // Fake progress for UI
                    for (int i = 0; i < 100; i += 5) {
                        updateProgress(i, 100);
                        updateMessage("Đang tải lên... " + i + "%");
                        Thread.sleep(50);
                    }
                    
                    Platform.runLater(() -> {
                        hideFileUploadProgress();
                        ToastNotification.show("File đã được gửi thành công!", rootNode, 2, true);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        hideFileUploadProgress();
                        ToastNotification.show("Lỗi khi gửi file: " + e.getMessage(), rootNode, 3, true);
                    });
                    e.printStackTrace();
                }
                
                return null;
            }
        };
        
        // Bind progress to UI
        if (fileUploadProgress != null) {
            fileUploadProgress.progressProperty().bind(uploadTask.progressProperty());
            fileUploadLabel.textProperty().bind(uploadTask.messageProperty());
        }
        
        // Start upload
        Thread uploadThread = new Thread(uploadTask);
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * UI components for file upload progress
     */
    private void showFileUploadProgress(String fileName) {
        Platform.runLater(() -> {
            if (fileUploadContainer == null) {
                fileUploadContainer = new VBox(5);
                fileUploadContainer.setAlignment(Pos.CENTER);
                fileUploadContainer.setPadding(new Insets(10));
                fileUploadContainer.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-border-color: #ddd; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5;"
                );
                
                fileUploadProgress = new ProgressBar();
                fileUploadProgress.setPrefWidth(200);
                
                fileUploadLabel = new Label("Đang tải lên: " + fileName);
                fileUploadLabel.setStyle("-fx-font-size: 12px;");
                
                fileUploadContainer.getChildren().addAll(fileUploadLabel, fileUploadProgress);
            } else {
                fileUploadLabel.setText("Đang tải lên: " + fileName);
            }
            
            // Add to messages container temporarily
            if (messagesContainer != null && !messagesContainer.getChildren().contains(fileUploadContainer)) {
                messagesContainer.getChildren().add(fileUploadContainer);
            }
        });
    }

    private void hideFileUploadProgress() {
        Platform.runLater(() -> {
            if (fileUploadContainer != null && messagesContainer != null && 
                messagesContainer.getChildren().contains(fileUploadContainer)) {
                messagesContainer.getChildren().remove(fileUploadContainer);
            }
        });
    }

    /**
     * Xử lý download file từ tin nhắn
     */
    public void handleFileDownload(Long fileId, String fileName) {
        if (fileId == null || fileId <= 0) {
            ToastNotification.show("File ID không hợp lệ", rootNode, 2, true);
            return;
        }
        
        // Show save dialog
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu file");
        fileChooser.setInitialFileName(fileName);
        
        File saveFile = fileChooser.showSaveDialog(rootNode.getScene().getWindow());
        
        if (saveFile != null) {
            // Show download progress
            showFileDownloadProgress(fileName);
            
            // Request file from server
            chatService.getClient().requestFile(String.valueOf(fileId));
            
            // Set up file receive handler (Implement this in ChatControllerRefactored and connect to fileHandler)
            Task<Void> downloadTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    try {
                        // Simulate download progress (since we don't have direct API to track progress)
                        for (int i = 0; i < 100; i += 5) {
                            updateProgress(i, 100);
                            updateMessage("Đang tải xuống... " + i + "%");
                            Thread.sleep(100);
                        }
                        
                        // Check if file data is available
                        byte[] fileData = chatService.getFileData(String.valueOf(fileId));
                        
                        if (fileData != null) {
                            // Save file
                            Files.write(saveFile.toPath(), fileData);
                            
                            Platform.runLater(() -> {
                                hideFileDownloadProgress();
                                ToastNotification.show("File đã được tải xuống!", rootNode, 2, true);
                                
                                // Open file location
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(saveFile.getParentFile());
                                    }
                                } catch (Exception e) {
                                    System.err.println("Không thể mở vị trí file: " + e.getMessage());
                                }
                            });
                        } else {
                            Platform.runLater(() -> {
                                hideFileDownloadProgress();
                                ToastNotification.show("Không tìm thấy dữ liệu file", rootNode, 3, true);
                            });
                        }
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            hideFileDownloadProgress();
                            ToastNotification.show("Lỗi khi tải xuống file: " + e.getMessage(), rootNode, 3, true);
                        });
                        e.printStackTrace();
                    }
                    
                    return null;
                }
            };
            
            // Bind progress
            if (fileDownloadProgress != null) {
                fileDownloadProgress.progressProperty().bind(downloadTask.progressProperty());
                fileDownloadLabel.textProperty().bind(downloadTask.messageProperty());
            }
            
            // Start download
            Thread downloadThread = new Thread(downloadTask);
            downloadThread.setDaemon(true);
            downloadThread.start();
        }
    }

    /**
     * UI cho download progress
     */
    private void showFileDownloadProgress(String fileName) {
        Platform.runLater(() -> {
            if (fileDownloadContainer == null) {
                fileDownloadContainer = new VBox(5);
                fileDownloadContainer.setAlignment(Pos.CENTER);
                fileDownloadContainer.setPadding(new Insets(10));
                fileDownloadContainer.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-border-color: #ddd; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5;"
                );
                
                fileDownloadProgress = new ProgressBar();
                fileDownloadProgress.setPrefWidth(200);
                
                fileDownloadLabel = new Label("Đang tải xuống: " + fileName);
                fileDownloadLabel.setStyle("-fx-font-size: 12px;");
                
                fileDownloadContainer.getChildren().addAll(fileDownloadLabel, fileDownloadProgress);
            } else {
                fileDownloadLabel.setText("Đang tải xuống: " + fileName);
            }
            
            // Add to messages container
            if (messagesContainer != null && !messagesContainer.getChildren().contains(fileDownloadContainer)) {
                messagesContainer.getChildren().add(fileDownloadContainer);
            }
        });
    }

    private void hideFileDownloadProgress() {
        Platform.runLater(() -> {
            if (fileDownloadContainer != null && messagesContainer != null && 
                messagesContainer.getChildren().contains(fileDownloadContainer)) {
                messagesContainer.getChildren().remove(fileDownloadContainer);
            }
        });
    }

    /**
     * Helper methods
     */
    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || 
               lower.endsWith(".jpeg") || lower.endsWith(".gif");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private byte[] generateThumbnail(File imageFile) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) return null;
            
            // Scale to max 200x200
            int maxSize = 200;
            int width = img.getWidth();
            int height = img.getHeight();
            
            double scale = Math.min((double) maxSize / width, (double) maxSize / height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);
            
            BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnail.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, newWidth, newHeight, null);
            g.dispose();
            
            // Convert to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to generate thumbnail: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lựa chọn và gửi hình ảnh
     * Phương thức này tương tự như handleFileSend nhưng chỉ cho phép chọn file ảnh
     */
    public void selectAndSendImage() {
        if (rootNode == null || messageHandler == null) {
            System.err.println("[ERROR] rootNode hoặc messageHandler null trong selectAndSendImage");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn hình ảnh để gửi");
        
        // Chỉ cho phép chọn file ảnh
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        
        File file = fileChooser.showOpenDialog(rootNode.getScene().getWindow());
        
        if (file != null && file.exists()) {
            // Check file size (limit 15MB for images)
            long fileSize = file.length();
            if (fileSize > 15 * 1024 * 1024) {
                ToastNotification.show("Hình ảnh quá lớn! Giới hạn 15MB", rootNode, 3, true);
                return;
            }
            
            // Tạo thumbnail nếu là ảnh 
            if (isImageFile(file.getName())) {
                try {
                    // Tạo thumbnail để preview
                    byte[] thumbData = generateThumbnail(file);
                    if (thumbData != null) {
                        System.out.println("[DEBUG] Đã tạo thumbnail với kích thước: " + thumbData.length + " bytes");
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Không thể tạo thumbnail: " + e.getMessage());
                }
            }
            
            // Show upload progress
            showFileUploadProgress(file.getName());
            
            // Determine target type - giống như handleFileSend
            String currentTarget = messageHandler.getCurrentTarget();
            boolean isGroup = messageHandler.isGroupTarget(currentTarget);
            
            if (isGroup) {
                // Send file to group
                long groupId = messageHandler.getGroupId(currentTarget);
                if (groupId > 0) {
                    sendFileToGroup(file, groupId, currentTarget);
                } else {
                    hideFileUploadProgress();
                    ToastNotification.show("Không thể gửi hình ảnh: Không tìm thấy group ID", rootNode, 3, true);
                }
            } else if (currentTarget.equals("Global")) {
                // Send file to global chat
                sendFileToGlobal(file);
            } else {
                // Send file to private chat
                sendFileToUser(file, currentTarget);
            }
        }
    }
}
