package app.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích mã hóa và giải mã dữ liệu cho database.
 * Sử dụng thuật toán AES với GCM mode để mã hóa có xác thực.
 */
public class DatabaseEncryptionUtil {

    // Thuật toán mã hóa AES với GCM mode (authenticated encryption)
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes

    // Khóa mã hóa cho database (sẽ lấy từ quản lý khóa)
    private static String databaseEncryptionKey = "DefaultSecureDBEncryptionKey123"; // Khóa mặc định

    /**
     * Đặt khóa mã hóa cho database
     * @param key Khóa mã hóa
     */
    public static void setDatabaseEncryptionKey(String key) {
        if (key != null && !key.isEmpty()) {
            databaseEncryptionKey = key;
        }
    }

    /**
     * Kiểm tra xem một chuỗi đã được mã hóa bởi hệ thống này chưa
     * @param text Chuỗi cần kiểm tra
     * @return true nếu đã mã hóa
     */
    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Kiểm tra prefix đặc biệt để nhận dạng
        return text.startsWith("DBENC:");
    }

    /**
     * Mã hóa một chuỗi văn bản để lưu vào database
     * @param plainText Văn bản gốc
     * @return Chuỗi đã mã hóa với prefix DBENC:
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        // Skip file messages và tin nhắn đặc biệt
        if (plainText.startsWith("[FILE]")) {
            return plainText;
        }

        try {
            // Tạo IV ngẫu nhiên
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Chuẩn bị khóa
            byte[] keyBytes = databaseEncryptionKey.getBytes(StandardCharsets.UTF_8);
            // Đảm bảo độ dài khóa là 16, 24 hoặc 32 bytes (128, 192 hoặc 256 bits)
            byte[] validKeyBytes = new byte[32]; // 256 bits
            System.arraycopy(keyBytes, 0, validKeyBytes, 0, Math.min(keyBytes.length, validKeyBytes.length));
            SecretKey secretKey = new SecretKeySpec(validKeyBytes, "AES");

            // Khởi tạo Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Mã hóa dữ liệu
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Kết hợp IV và dữ liệu mã hóa
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            // Chuyển sang Base64 với prefix
            String result = "DBENC:" + Base64.getEncoder().encodeToString(combined);
            System.out.println("[DEBUG] Mã hóa: " + plainText.substring(0, Math.min(20, plainText.length())) + "... -> " + result.substring(0, Math.min(30, result.length())) + "...");
            return result;
        } catch (Exception e) {
            // Log lỗi nhưng không throw exception để tránh crash
            System.err.println("Database encryption error: " + e.getMessage());
            e.printStackTrace();
            return plainText; // Trả về plaintext nếu có lỗi
        }
    }

    /**
     * Giải mã một chuỗi văn bản từ database
     * @param encryptedText Văn bản đã mã hóa (với prefix DBENC:)
     * @return Văn bản gốc
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        if (!encryptedText.startsWith("DBENC:")) {
            return encryptedText; // Không phải văn bản đã mã hóa
        }

        try {
            // Loại bỏ prefix và giải mã Base64
            String encodedData = encryptedText.substring(6); // Bỏ "DBENC:"
            byte[] combined = Base64.getDecoder().decode(encodedData);

            // Tách IV và dữ liệu mã hóa
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            // Chuẩn bị khóa
            byte[] keyBytes = databaseEncryptionKey.getBytes(StandardCharsets.UTF_8);
            byte[] validKeyBytes = new byte[32]; // 256 bits
            System.arraycopy(keyBytes, 0, validKeyBytes, 0, Math.min(keyBytes.length, validKeyBytes.length));
            SecretKey secretKey = new SecretKeySpec(validKeyBytes, "AES");

            // Khởi tạo Cipher để giải mã
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Giải mã dữ liệu
            byte[] decryptedData = cipher.doFinal(encryptedData);

            // Chuyển về chuỗi
            String result = new String(decryptedData, StandardCharsets.UTF_8);
            System.out.println("[DEBUG] Giải mã: " + encryptedText.substring(0, Math.min(30, encryptedText.length())) + "... -> " + result.substring(0, Math.min(20, result.length())) + "...");
            return result;
        } catch (Exception e) {
            // Log lỗi nhưng không throw exception để tránh crash
            System.err.println("Database decryption error: " + e.getMessage());
            e.printStackTrace();
            return "⚠️ [Không thể giải mã] " + encryptedText.substring(6, Math.min(encryptedText.length(), 20)) + "...";
        }
    }
}