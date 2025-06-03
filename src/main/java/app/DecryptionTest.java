package app;

import app.util.DatabaseEncryptionUtil;
import app.util.DatabaseKeyManager;

/**
 * Simple test to verify decryption of a specific message
 * with the fixed encryption key
 */
public class DecryptionTest {

    public static void main(String[] args) {
        System.out.println("=== Message Decryption Test ===");

        // Initialize the encryption key manager
        System.out.println("Initializing key manager...");
        DatabaseKeyManager.initialize();

        // Get and display the key (partially)
        String key = DatabaseKeyManager.getEncryptionKey();
        System.out.println("Using key: " + key.substring(0, 3) + "..." + key.substring(key.length() - 3));

        // Test message - Replace this with an actual encrypted message from your database
        String encryptedMessage = "DBENC:hrHOCSgrmROECq9ITlIIN1Fd/+q2xCXw3IhbaIcgPQ==";

        System.out.println("\nEncrypted message:");
        System.out.println(encryptedMessage);

        // Check if it's actually encrypted
        if (!DatabaseEncryptionUtil.isEncrypted(encryptedMessage)) {
            System.out.println("WARNING: This doesn't appear to be an encrypted message (missing DBENC: prefix)");
        }

        try {
            // Attempt decryption
            System.out.println("\nAttempting decryption...");
            String decryptedMessage = DatabaseEncryptionUtil.decrypt(encryptedMessage);

            // Check if decryption succeeded
            if (decryptedMessage.equals("⚠️ [Không thể hiển thị tin nhắn]")) {
                System.out.println("❌ DECRYPTION FAILED");
                System.out.println("The message could not be decrypted with the current key.");
            } else {
                System.out.println("✅ DECRYPTION SUCCESSFUL");
                System.out.println("Decrypted content: " + decryptedMessage);
            }
        } catch (Exception e) {
            System.out.println("❌ EXCEPTION DURING DECRYPTION");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== Additional Debug Info ===");
        System.out.println("Key properties file location should be in classpath or resources directory");
        System.out.println("Key value from properties: MySecureMessenger2025EncryptKey!");

        // Test a freshly encrypted message with the current key
        testRoundtrip();
    }

    /**
     * Test encrypting and decrypting a message with the current key
     * This helps determine if the current key can properly encrypt/decrypt
     */
    private static void testRoundtrip() {
        System.out.println("\n=== Testing Encrypt/Decrypt Roundtrip ===");
        String testMessage = "This is a test message created at " + java.time.LocalDateTime.now();

        try {
            System.out.println("Original: " + testMessage);

            // Encrypt with current key
            String encrypted = DatabaseEncryptionUtil.encrypt(testMessage);
            System.out.println("Encrypted: " + encrypted.substring(0, Math.min(50, encrypted.length())) +
                    (encrypted.length() > 50 ? "..." : ""));

            // Decrypt with current key
            String decrypted = DatabaseEncryptionUtil.decrypt(encrypted);
            System.out.println("Decrypted: " + decrypted);

            if (testMessage.equals(decrypted)) {
                System.out.println("✅ ROUNDTRIP TEST SUCCESSFUL");
                System.out.println("The current key can properly encrypt and decrypt messages.");
            } else {
                System.out.println("❌ ROUNDTRIP TEST FAILED");
                System.out.println("The decrypted message doesn't match the original.");
            }
        } catch (Exception e) {
            System.out.println("❌ EXCEPTION DURING ROUNDTRIP TEST");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}