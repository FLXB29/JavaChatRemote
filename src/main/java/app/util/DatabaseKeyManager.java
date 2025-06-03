package app.util;

import java.io.InputStream;
import java.util.Properties;

public class DatabaseKeyManager {
    private static final String KEY_PROPERTY = "encryption.key";
    private static final String DEFAULT_KEY = "MySecureMessenger2025EncryptKey!";
    private static String encryptionKey;

    public static void initialize() {
        try {
            Properties props = new Properties();
            InputStream inputStream = DatabaseKeyManager.class.getClassLoader()
                    .getResourceAsStream("encryption_key.properties");

            if (inputStream != null) {
                props.load(inputStream);
                encryptionKey = props.getProperty(KEY_PROPERTY, DEFAULT_KEY);
                inputStream.close();
                System.out.println("[INFO] Loaded encryption key from properties file");
            } else {
                encryptionKey = DEFAULT_KEY;
                System.out.println("[WARN] Could not find encryption_key.properties, using default key");
            }
        } catch (Exception e) {
            encryptionKey = DEFAULT_KEY;
            System.err.println("[ERROR] Failed to load encryption key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getEncryptionKey() {
        if (encryptionKey == null) {
            initialize();
        }
        return encryptionKey;
    }
}