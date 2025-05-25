package app.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Quản lý khóa mã hóa database
 * Lưu và đọc cài đặt mã hóa từ file cấu hình
 */
public class DatabaseKeyManager {
    private static final String CONFIG_FILE = "encryption.properties";
    public static final String GLOBAL_CHAT_ID = "global";

    private static Properties properties;
    private static boolean initialized = false;

    /**
     * Khởi tạo DatabaseKeyManager, tạo file config nếu chưa tồn tại
     */
    public static synchronized void initialize() {
        if (initialized) return;

        properties = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("Không thể đọc file cấu hình mã hóa: " + e.getMessage());
                createDefaultConfig();
            }
        } else {
            createDefaultConfig();
        }

        initialized = true;
    }

    /**
     * Tạo file cấu hình mặc định nếu chưa tồn tại
     */
    private static void createDefaultConfig() {
        properties.setProperty("encryption.enabled", "false");
        properties.setProperty("encryption.key", generateRandomKey(32));
        saveConfig();
    }

    /**
     * Lưu cấu hình mã hóa vào file
     */
    private static void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Database Encryption Configuration");
        } catch (IOException e) {
            System.err.println("Không thể lưu file cấu hình mã hóa: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra trạng thái đã khởi tạo
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Kiểm tra trạng thái mã hóa
     */
    public static boolean isEncryptionEnabled() {
        if (!initialized) initialize();
        return Boolean.parseBoolean(properties.getProperty("encryption.enabled", "false"));
    }

    /**
     * Bật/tắt mã hóa database
     */
    public static void setEncryptionEnabled(boolean enabled) {
        if (!initialized) initialize();
        properties.setProperty("encryption.enabled", String.valueOf(enabled));
        saveConfig();

        // Cập nhật service mã hóa
        try {
            Class<?> encryptionServiceClass = Class.forName("app.util.MessageEncryptionService");
            java.lang.reflect.Method getInstance = encryptionServiceClass.getDeclaredMethod("getInstance");
            Object instance = getInstance.invoke(null);
            java.lang.reflect.Method updateSettings = encryptionServiceClass.getDeclaredMethod("updateEncryptionSettings");
            updateSettings.invoke(instance);
        } catch (Exception e) {
            System.err.println("Không thể cập nhật trạng thái mã hóa: " + e.getMessage());
        }
    }

    /**
     * Lấy khóa mã hóa
     */
    public static String getEncryptionKey() {
        if (!initialized) initialize();
        return properties.getProperty("encryption.key", "");
    }

    /**
     * Đặt khóa mã hóa mới
     */
    public static void setEncryptionKey(String key) {
        if (!initialized) initialize();
        properties.setProperty("encryption.key", key);
        saveConfig();

        // Cập nhật service mã hóa
        try {
            Class<?> encryptionServiceClass = Class.forName("app.util.MessageEncryptionService");
            java.lang.reflect.Method getInstance = encryptionServiceClass.getDeclaredMethod("getInstance");
            Object instance = getInstance.invoke(null);
            java.lang.reflect.Method updateSettings = encryptionServiceClass.getDeclaredMethod("updateEncryptionSettings");
            updateSettings.invoke(instance);
        } catch (Exception e) {
            System.err.println("Không thể cập nhật khóa mã hóa: " + e.getMessage());
        }
    }

    /**
     * Tạo khóa ngẫu nhiên với độ dài xác định
     */
    public static String generateRandomKey(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Lấy khóa cho một conversation cụ thể
     */
    public static String getKey(String conversationId) {
        if (!initialized) initialize();
        String key = properties.getProperty("key." + conversationId);
        return (key != null) ? key : getEncryptionKey(); // Fallback to main key
    }

    /**
     * Đặt khóa cho một conversation cụ thể
     */
    public static void setKey(String conversationId, String key) {
        if (!initialized) initialize();
        properties.setProperty("key." + conversationId, key);
        saveConfig();
    }

    /**
     * Tạo khóa ngẫu nhiên cho một conversation
     */
    public static String generateRandomKey(String conversationId) {
        String key = generateRandomKey(32);
        setKey(conversationId, key);
        return key;
    }

    /**
     * Kiểm tra trạng thái mã hóa Global Chat
     */
    public static boolean isGlobalChatEncryptionEnabled() {
        if (!initialized) initialize();
        return Boolean.parseBoolean(properties.getProperty("global.encryption.enabled", "false"));
    }

    /**
     * Bật/tắt mã hóa Global Chat
     */
    public static void setGlobalChatEncryptionEnabled(boolean enabled) {
        if (!initialized) initialize();
        properties.setProperty("global.encryption.enabled", String.valueOf(enabled));
        saveConfig();
    }
}