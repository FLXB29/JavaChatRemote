package app.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service để mã hóa và giải mã tin nhắn
 * Sử dụng thuật toán AES/GCM cho mã hóa an toàn
 */
public class MessageEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes

    private static MessageEncryptionService instance;
    private boolean encryptionEnabled = false;
    private String encryptionKey = null;

    private MessageEncryptionService() {
        // Kiểm tra xem mã hóa có được bật không
        this.encryptionEnabled = DatabaseKeyManager.isEncryptionEnabled();
        this.encryptionKey = DatabaseKeyManager.getEncryptionKey();
    }

    public static synchronized MessageEncryptionService getInstance() {
        if (instance == null) {
            instance = new MessageEncryptionService();
        }
        return instance;
    }

    /**
     * Mã hóa nội dung tin nhắn
     * @param plainText Tin nhắn gốc
     * @return Chuỗi đã mã hóa dạng Base64
     */
    public String encrypt(String plainText) {
        if (!encryptionEnabled || plainText == null || encryptionKey == null) {
            return plainText;
        }

        try {
            // Tạo IV ngẫu nhiên
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            // Chuyển khóa thành SecretKey
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            // Khởi tạo cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Mã hóa
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Ghép IV và cipherText
            byte[] encryptedData = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encryptedData, 0, iv.length);
            System.arraycopy(cipherText, 0, encryptedData, iv.length, cipherText.length);

            // Mã hóa Base64
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            System.err.println("Lỗi khi mã hóa tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return plainText; // Trả về tin nhắn gốc nếu lỗi
        }
    }

    /**
     * Giải mã nội dung tin nhắn
     * @param encryptedText Tin nhắn đã mã hóa
     * @return Tin nhắn gốc
     */
    public String decrypt(String encryptedText) {
        if (!encryptionEnabled || encryptedText == null || encryptionKey == null) {
            return encryptedText;
        }

        try {
            // Kiểm tra xem chuỗi có phải đã được mã hóa không
            if (!isBase64(encryptedText)) {
                return encryptedText;
            }

            // Giải mã Base64
            byte[] encryptedData = Base64.getDecoder().decode(encryptedText);

            // Tách IV và cipherText
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            // Chuyển khóa thành SecretKey
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            // Khởi tạo cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Giải mã
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Lỗi khi giải mã tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return encryptedText; // Trả về tin nhắn đã mã hóa nếu lỗi
        }
    }

    /**
     * Kiểm tra nhanh xem chuỗi có phải dạng Base64 không
     */
    private boolean isBase64(String text) {
        try {
            return text.matches("^[A-Za-z0-9+/]+={0,2}$");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cập nhật trạng thái mã hóa
     */
    public void updateEncryptionSettings() {
        this.encryptionEnabled = DatabaseKeyManager.isEncryptionEnabled();
        this.encryptionKey = DatabaseKeyManager.getEncryptionKey();
    }
}