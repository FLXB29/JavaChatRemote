package app.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Tiện ích mã hóa và giải mã tin nhắn sử dụng AES.
 */
public class EncryptionUtil {

    // Thuật toán mã hóa
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    // Salt cố định cho tất cả người dùng (trong thực tế, có thể lưu riêng cho từng conversation)
    private static final byte[] SALT = {
            (byte)0x43, (byte)0x76, (byte)0x95, (byte)0xc7,
            (byte)0x5b, (byte)0xd7, (byte)0x45, (byte)0x17
    };

    // Số lần lặp lại quá trình tạo khóa từ mật khẩu
    private static final int ITERATION_COUNT = 65536;

    // Độ dài khóa AES (128, 192 hoặc 256 bit)
    private static final int KEY_LENGTH = 256;

    /**
     * Mã hóa tin nhắn sử dụng mật khẩu chung.
     *
     * @param message Tin nhắn cần mã hóa
     * @param password Mật khẩu dùng để mã hóa
     * @return Chuỗi đã mã hóa dưới dạng Base64
     */
    public static String encrypt(String message, String password) {
        try {
            // Tạo IV (Initialization Vector) mới cho mỗi lần mã hóa
            byte[] iv = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            // Tạo khóa từ mật khẩu
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

            // Mã hóa tin nhắn
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));

            // Kết hợp IV và dữ liệu mã hóa
            byte[] combinedBytes = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combinedBytes, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combinedBytes, iv.length, encryptedBytes.length);

            // Chuyển thành chuỗi Base64
            return Base64.getEncoder().encodeToString(combinedBytes);
        } catch (Exception e) {
            System.err.println("Lỗi khi mã hóa tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return "[ENCRYPTION_ERROR] " + message;
        }
    }

    /**
     * Giải mã tin nhắn đã mã hóa.
     *
     * @param encryptedMessage Tin nhắn đã mã hóa dưới dạng Base64
     * @param password Mật khẩu dùng để giải mã
     * @return Tin nhắn gốc
     */
    public static String decrypt(String encryptedMessage, String password) {
        try {
            // Kiểm tra xem tin nhắn có phải là đã mã hóa không
            if (encryptedMessage.startsWith("[ENCRYPTION_ERROR]")) {
                return encryptedMessage.substring("[ENCRYPTION_ERROR] ".length());
            }

            // Giải mã Base64
            byte[] combinedBytes = Base64.getDecoder().decode(encryptedMessage);

            // Tách IV và dữ liệu mã hóa
            byte[] iv = new byte[16];
            byte[] encryptedBytes = new byte[combinedBytes.length - 16];
            System.arraycopy(combinedBytes, 0, iv, 0, iv.length);
            System.arraycopy(combinedBytes, 16, encryptedBytes, 0, encryptedBytes.length);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            // Tạo khóa từ mật khẩu
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

            // Giải mã tin nhắn
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            // Chuyển thành chuỗi
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Lỗi khi giải mã tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return "[DECRYPTION_ERROR] " + encryptedMessage;
        }
    }

    /**
     * Kiểm tra xem một tin nhắn đã được mã hóa hay chưa.
     *
     * @param message Tin nhắn cần kiểm tra
     * @return true nếu tin nhắn đã được mã hóa
     */
    public static boolean isEncrypted(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        // Kiểm tra xem tin nhắn có phải là tin nhắn đặc biệt không
        if (message.startsWith("[FILE]") || message.startsWith("[ENCRYPTION_ERROR]") ||
                message.startsWith("[DECRYPTION_ERROR]")) {
            return false;
        }

        try {
            // Thử giải mã Base64
            byte[] combinedBytes = Base64.getDecoder().decode(message);
            // Nếu độ dài nhỏ hơn IV (16 bytes), có lẽ đây không phải tin nhắn được mã hóa
            return combinedBytes.length > 16;
        } catch (IllegalArgumentException e) {
            // Không phải Base64 hợp lệ => không phải tin nhắn mã hóa
            return false;
        }
    }
}