package app.tools;

import app.dao.MessageDAO;
import app.model.Message;
import app.util.DatabaseEncryptionUtil;
import app.util.DatabaseKeyManager;
import app.util.HibernateUtil;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Công cụ để mã hóa lại các tin nhắn cũ trong database
 * Chỉ sử dụng bởi admin khi cần thiết
 */
public class DatabaseReencryptionTool extends Application {

    private ProgressBar progressBar;
    private Label statusLabel;
    private Button startButton;
    private TextField oldKeyField;
    private TextField newKeyField;
    private CheckBox encryptAllCheckbox;
    private CheckBox decryptAllCheckbox;

    @Override
    public void start(Stage primaryStage) {
        // Khởi tạo DatabaseKeyManager
        DatabaseKeyManager.initialize();

        // Tạo UI
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label titleLabel = new Label("Công cụ mã hóa lại database");
        titleLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

        Label descriptionLabel = new Label("Công cụ này cho phép mã hóa lại tin nhắn trong database. " +
                "Sử dụng với cẩn trọng - hãy sao lưu database trước khi tiến hành.");
        descriptionLabel.setWrapText(true);

        VBox keyContainer = new VBox(10);
        Label oldKeyLabel = new Label("Khóa cũ (để trống nếu tin nhắn chưa mã hóa):");
        oldKeyField = new TextField();
        oldKeyField.setPromptText("Khóa mã hóa cũ");

        Label newKeyLabel = new Label("Khóa mới (nếu muốn mã hóa lại):");
        newKeyField = new TextField(DatabaseKeyManager.getEncryptionKey());
        newKeyField.setPromptText("Khóa mã hóa mới");

        keyContainer.getChildren().addAll(oldKeyLabel, oldKeyField, newKeyLabel, newKeyField);

        encryptAllCheckbox = new CheckBox("Mã hóa tất cả tin nhắn chưa mã hóa");
        decryptAllCheckbox = new CheckBox("Giải mã tất cả tin nhắn đã mã hóa");

        // Toggle logic
        encryptAllCheckbox.selectedProperty().addListener((obs, old, newVal) -> {
            if (newVal) decryptAllCheckbox.setSelected(false);
        });

        decryptAllCheckbox.selectedProperty().addListener((obs, old, newVal) -> {
            if (newVal) encryptAllCheckbox.setSelected(false);
        });

        HBox buttonContainer = new HBox(10);
        startButton = new Button("Bắt đầu xử lý");
        startButton.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
        Button cancelButton = new Button("Đóng");
        cancelButton.setOnAction(e -> primaryStage.close());

        buttonContainer.getChildren().addAll(startButton, cancelButton);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);

        statusLabel = new Label("Sẵn sàng");
        statusLabel.setStyle("-fx-font-style: italic;");

        root.getChildren().addAll(
                titleLabel,
                descriptionLabel,
                new Separator(),
                keyContainer,
                new Separator(),
                encryptAllCheckbox,
                decryptAllCheckbox,
                progressBar,
                statusLabel,
                buttonContainer
        );

        // Set up event handlers
        startButton.setOnAction(e -> startProcessing());

        Scene scene = new Scene(root, 600, 450);
        primaryStage.setTitle("Database Reencryption Tool");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startProcessing() {
        // Validate
        if (!encryptAllCheckbox.isSelected() && !decryptAllCheckbox.isSelected()) {
            showAlert("Vui lòng chọn một hành động (mã hóa hoặc giải mã)");
            return;
        }

        if (encryptAllCheckbox.isSelected() && newKeyField.getText().isEmpty()) {
            showAlert("Vui lòng nhập khóa mới để mã hóa");
            return;
        }

        if (decryptAllCheckbox.isSelected() && oldKeyField.getText().isEmpty()) {
            showAlert("Vui lòng nhập khóa cũ để giải mã");
            return;
        }

        // Confirm
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Bạn chắc chắn muốn tiếp tục?");
        confirm.setContentText("Quá trình này sẽ thay đổi dữ liệu trong database. " +
                "Hãy đảm bảo bạn đã sao lưu database trước khi tiếp tục.");

        if (confirm.showAndWait().get() != ButtonType.OK) {
            return;
        }

        // Disable controls
        startButton.setDisable(true);
        oldKeyField.setDisable(true);
        newKeyField.setDisable(true);
        encryptAllCheckbox.setDisable(true);
        decryptAllCheckbox.setDisable(true);

        // Set up keys
        String oldKey = oldKeyField.getText();
        String newKey = newKeyField.getText();

        if (!oldKey.isEmpty()) {
            DatabaseEncryptionUtil.setDatabaseEncryptionKey(oldKey);
        }

        // Create and start task
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (encryptAllCheckbox.isSelected()) {
                    encryptAllMessages(newKey);
                } else if (decryptAllCheckbox.isSelected()) {
                    decryptAllMessages();
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            progressBar.setProgress(1.0);
            statusLabel.setText("Hoàn thành!");
            statusLabel.setStyle("-fx-text-fill: green;");

            // Re-enable controls
            startButton.setDisable(false);
            oldKeyField.setDisable(false);
            newKeyField.setDisable(false);
            encryptAllCheckbox.setDisable(false);
            decryptAllCheckbox.setDisable(false);

            // Show success alert
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Thành công");
            success.setHeaderText("Xử lý hoàn tất");
            success.setContentText("Quá trình xử lý database đã hoàn tất thành công.");
            success.show();
        });

        task.setOnFailed(e -> {
            progressBar.setProgress(0);
            statusLabel.setText("Lỗi: " + task.getException().getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");

            // Re-enable controls
            startButton.setDisable(false);
            oldKeyField.setDisable(false);
            newKeyField.setDisable(false);
            encryptAllCheckbox.setDisable(false);
            decryptAllCheckbox.setDisable(false);

            // Show error alert
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Lỗi");
            error.setHeaderText("Xử lý thất bại");
            error.setContentText("Đã xảy ra lỗi trong quá trình xử lý: " + task.getException().getMessage());
            error.show();
        });

        new Thread(task).start();
    }

    private void encryptAllMessages(String newKey) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Count total messages
            Query<Long> countQuery = session.createQuery("SELECT COUNT(m) FROM Message m WHERE m.content NOT LIKE 'DBENC:%'", Long.class);
            long total = countQuery.getSingleResult();

            if (total == 0) {
                updateStatus("Không tìm thấy tin nhắn nào cần mã hóa", 1.0);
                return;
            }

            updateStatus("Tìm thấy " + total + " tin nhắn cần mã hóa", 0.0);

            // Get messages in batches
            int batchSize = 100;
            int processedCount = 0;

            for (int offset = 0; offset < total; offset += batchSize) {
                Transaction tx = session.beginTransaction();
                try {
                    Query<Message> query = session.createQuery(
                            "FROM Message m WHERE m.content NOT LIKE 'DBENC:%' AND m.content NOT LIKE '[FILE]%'",
                            Message.class);
                    query.setFirstResult(offset);
                    query.setMaxResults(batchSize);

                    List<Message> messages = query.list();

                    // Set new key for encryption
                    DatabaseEncryptionUtil.setDatabaseEncryptionKey(newKey);

                    for (Message message : messages) {
                        // Skip file messages
                        if (message.getContent() != null && !message.getContent().startsWith("[FILE]")) {
                            String encrypted = DatabaseEncryptionUtil.encrypt(message.getContent());
                            message.setContent(encrypted);
                            session.merge(message);
                        }
                        processedCount++;

                        double progress = (double) processedCount / total;
                        updateStatus("Đã xử lý " + processedCount + "/" + total + " tin nhắn", progress);
                    }

                    tx.commit();
                } catch (Exception e) {
                    tx.rollback();
                    throw e;
                }
            }

            updateStatus("Hoàn thành: Đã mã hóa " + processedCount + " tin nhắn", 1.0);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi mã hóa tin nhắn: " + e.getMessage(), e);
        }
    }

    private void decryptAllMessages() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Count total encrypted messages
            Query<Long> countQuery = session.createQuery("SELECT COUNT(m) FROM Message m WHERE m.content LIKE 'DBENC:%'", Long.class);
            long total = countQuery.getSingleResult();

            if (total == 0) {
                updateStatus("Không tìm thấy tin nhắn nào đã mã hóa", 1.0);
                return;
            }

            updateStatus("Tìm thấy " + total + " tin nhắn đã mã hóa", 0.0);

            // Get messages in batches
            int batchSize = 100;
            int processedCount = 0;

            for (int offset = 0; offset < total; offset += batchSize) {
                Transaction tx = session.beginTransaction();
                try {
                    Query<Message> query = session.createQuery(
                            "FROM Message m WHERE m.content LIKE 'DBENC:%'",
                            Message.class);
                    query.setFirstResult(offset);
                    query.setMaxResults(batchSize);

                    List<Message> messages = query.list();

                    for (Message message : messages) {
                        if (message.getContent() != null && message.getContent().startsWith("DBENC:")) {
                            String decrypted = DatabaseEncryptionUtil.decrypt(message.getContent());
                            message.setContent(decrypted);
                            session.merge(message);
                        }
                        processedCount++;

                        double progress = (double) processedCount / total;
                        updateStatus("Đã xử lý " + processedCount + "/" + total + " tin nhắn", progress);
                    }

                    tx.commit();
                } catch (Exception e) {
                    tx.rollback();
                    throw e;
                }
            }

            updateStatus("Hoàn thành: Đã giải mã " + processedCount + " tin nhắn", 1.0);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi giải mã tin nhắn: " + e.getMessage(), e);
        }
    }

    private void updateStatus(String message, double progress) {
        javafx.application.Platform.runLater(() -> {
            statusLabel.setText(message);
            progressBar.setProgress(progress);
        });
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Cảnh báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}