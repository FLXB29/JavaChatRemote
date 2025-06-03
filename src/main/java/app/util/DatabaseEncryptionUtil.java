package app.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích mã hóa và giải mã đơn giản cho database.
 * Tất cả tin nhắn đều được mã hóa, không cần phân quyền.
 */
public class DatabaseEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes

    // Khóa mã hóa cố định - trong thực tế nên lưu ở nơi an toàn
    private static final String ENCRYPTION_KEY = "MySecureMessenger2025EncryptKey!"; // 32 chars

    /**
     * Mã hóa tin nhắn
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        // Không mã hóa tin nhắn file
        if (plainText.startsWith("[FILE]")) {
            return plainText;
        }

        try {
            // Tạo IV ngẫu nhiên
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Chuẩn bị khóa
            byte[] keyBytes = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] validKeyBytes = new byte[32]; // 256 bits
            System.arraycopy(keyBytes, 0, validKeyBytes, 0, Math.min(keyBytes.length, validKeyBytes.length));
            SecretKey secretKey = new SecretKeySpec(validKeyBytes, "AES");

            // Mã hóa
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Kết hợp IV và dữ liệu mã hóa
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            // Trả về với prefix
            return "DBENC:" + Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            System.err.println("Lỗi mã hóa: " + e.getMessage());
            return plainText; // Trả về plaintext nếu lỗi
        }
    }

    /**
     * Giải mã tin nhắn
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        if (!encryptedText.startsWith("DBENC:")) {
            return encryptedText; // Không phải tin nhắn đã mã hóa
        }

        try {
            // Loại bỏ prefix và giải mã Base64
            String encodedData = encryptedText.substring(6);
            byte[] combined = Base64.getDecoder().decode(encodedData);

            // Tách IV và dữ liệu
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            // Chuẩn bị khóa
            byte[] keyBytes = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] validKeyBytes = new byte[32];
            System.arraycopy(keyBytes, 0, validKeyBytes, 0, Math.min(keyBytes.length, validKeyBytes.length));
            SecretKey secretKey = new SecretKeySpec(validKeyBytes, "AES");

            // Giải mã
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            byte[] decryptedData = cipher.doFinal(encryptedData);

            return new String(decryptedData, StandardCharsets.UTF_8);

        } catch (Exception e) {
            // Enhanced error logging
            System.err.println("Lỗi giải mã: " + e.getMessage());
            e.printStackTrace();

            // Store information about failed decryption for debugging
            logDecryptionFailure(encryptedText, e);

            return "⚠️ [Không thể hiển thị tin nhắn]";
        }

    }

    /**
     * Kiểm tra xem tin nhắn đã được mã hóa chưa
     */
    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith("DBENC:");
    }
    private static void logDecryptionFailure(String encryptedText, Exception e) {
        try {
            // You could log to a file or database here
            // For now we'll just print to console with more details
            System.err.println("===== DECRYPTION FAILURE =====");
            System.err.println("Text: " + (encryptedText.length() > 20 ?
                    encryptedText.substring(0, 20) + "..." : encryptedText));
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            System.err.println("=============================");
        } catch (Exception ex) {
            // Prevent any errors in logging from affecting the app
        }
    }

    public static String safeDecryptForDisplay(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return "";
        }

        if (!encryptedText.startsWith("DBENC:")) {
            return encryptedText;
        }

        try {
            return decrypt(encryptedText);
        } catch (Exception e) {
            return "⚠️ [Tin nhắn mã hóa]";
        }
    }

}