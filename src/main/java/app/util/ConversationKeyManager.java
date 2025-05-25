package app.util;

import app.model.Conversation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý khóa mã hóa cho các cuộc trò chuyện.
 * Lớp này giúp lưu trữ và quản lý các khóa mã hóa được sử dụng cho từng cuộc trò chuyện.
 */
public class ConversationKeyManager {
    // Instance singleton
    private static ConversationKeyManager instance;

    // Map lưu trữ khóa theo conversationId
    private final Map<Long, String> conversationKeys = new ConcurrentHashMap<>();

    // Map lưu trữ trạng thái bật/tắt mã hóa theo conversationId
    private final Map<Long, Boolean> encryptionEnabled = new ConcurrentHashMap<>();

    // Thêm các map đặc biệt cho Global Chat (không có ID cụ thể)
    private String globalChatKey = "GlobalChatDefaultSecretKey";
    private boolean globalChatEncryption = false;

    // Khóa mặc định nếu không có khóa riêng
    private static final String DEFAULT_KEY = "MyMessenger2025DefaultSecretKey";

    // Chỉ định ID đặc biệt cho Global Chat
    public static final long GLOBAL_CHAT_ID = -999L;

    /**
     * Constructor riêng tư để thực hiện mẫu singleton
     */
    private ConversationKeyManager() {
        // Khởi tạo riêng tư
    }

    /**
     * Lấy instance của ConversationKeyManager
     * @return Instance duy nhất của ConversationKeyManager
     */
    public static synchronized ConversationKeyManager getInstance() {
        if (instance == null) {
            instance = new ConversationKeyManager();
        }
        return instance;
    }

    /**
     * Đặt khóa mã hóa cho một cuộc trò chuyện
     * @param conversationId ID của cuộc trò chuyện
     * @param key Khóa mã hóa
     */
    public void setKey(Long conversationId, String key) {
        if (conversationId == null) {
            return;
        }

        // Xử lý đặc biệt cho Global Chat
        if (conversationId == GLOBAL_CHAT_ID) {
            if (key == null || key.isEmpty()) {
                globalChatKey = DEFAULT_KEY;
            } else {
                globalChatKey = key;
            }
            // Khi đặt khóa, mặc định bật mã hóa
            globalChatEncryption = true;
            return;
        }

        if (key == null || key.isEmpty()) {
            // Nếu key rỗng, sử dụng key mặc định
            conversationKeys.put(conversationId, DEFAULT_KEY);
        } else {
            conversationKeys.put(conversationId, key);
        }

        // Khi đặt khóa, mặc định bật mã hóa
        encryptionEnabled.put(conversationId, true);
    }

    /**
     * Lấy khóa mã hóa cho một cuộc trò chuyện
     * @param conversationId ID của cuộc trò chuyện
     * @return Khóa mã hóa
     */
    public String getKey(Long conversationId) {
        if (conversationId == null) {
            return DEFAULT_KEY;
        }

        // Xử lý đặc biệt cho Global Chat
        if (conversationId == GLOBAL_CHAT_ID) {
            return globalChatKey;
        }

        return conversationKeys.getOrDefault(conversationId, DEFAULT_KEY);
    }

    /**
     * Lấy khóa mã hóa cho một cuộc trò chuyện
     * @param conversation Đối tượng Conversation
     * @return Khóa mã hóa
     */
    public String getKey(Conversation conversation) {
        if (conversation == null) {
            return DEFAULT_KEY;
        }

        // Xử lý đặc biệt cho Global Chat
        if ("Global Chat".equals(conversation.getName())) {
            return globalChatKey;
        }

        return getKey(conversation.getId());
    }

    /**
     * Kiểm tra xem mã hóa có được bật cho cuộc trò chuyện hay không
     * @param conversationId ID của cuộc trò chuyện
     * @return true nếu mã hóa được bật
     */
    public boolean isEncryptionEnabled(Long conversationId) {
        if (conversationId == null) {
            return false;
        }

        // Xử lý đặc biệt cho Global Chat
        if (conversationId == GLOBAL_CHAT_ID) {
            return globalChatEncryption;
        }

        return encryptionEnabled.getOrDefault(conversationId, false);
    }

    /**
     * Kiểm tra xem mã hóa có được bật cho cuộc trò chuyện hay không
     * @param conversation Đối tượng Conversation
     * @return true nếu mã hóa được bật
     */
    public boolean isEncryptionEnabled(Conversation conversation) {
        if (conversation == null) {
            return false;
        }

        // Xử lý đặc biệt cho Global Chat
        if ("Global Chat".equals(conversation.getName())) {
            return globalChatEncryption;
        }

        return isEncryptionEnabled(conversation.getId());
    }

    /**
     * Bật hoặc tắt mã hóa cho một cuộc trò chuyện
     * @param conversationId ID của cuộc trò chuyện
     * @param enabled true để bật mã hóa, false để tắt
     */
    public void setEncryptionEnabled(Long conversationId, boolean enabled) {
        if (conversationId == null) {
            return;
        }

        // Xử lý đặc biệt cho Global Chat
        if (conversationId == GLOBAL_CHAT_ID) {
            globalChatEncryption = enabled;
            return;
        }

        encryptionEnabled.put(conversationId, enabled);
    }

    /**
     * Bật hoặc tắt mã hóa cho Global Chat
     * @param enabled true để bật mã hóa, false để tắt
     */
    public void setGlobalChatEncryptionEnabled(boolean enabled) {
        globalChatEncryption = enabled;
    }

    /**
     * Kiểm tra xem mã hóa có được bật cho Global Chat hay không
     * @return true nếu mã hóa được bật
     */
    public boolean isGlobalChatEncryptionEnabled() {
        return globalChatEncryption;
    }

    /**
     * Xóa khóa cho một cuộc trò chuyện
     * @param conversationId ID của cuộc trò chuyện
     */
    public void removeKey(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        // Xử lý đặc biệt cho Global Chat
        if (conversationId == GLOBAL_CHAT_ID) {
            globalChatKey = DEFAULT_KEY;
            globalChatEncryption = false;
            return;
        }

        conversationKeys.remove(conversationId);
        encryptionEnabled.remove(conversationId);
    }

    /**
     * Tạo một khóa ngẫu nhiên cho cuộc trò chuyện
     * @param conversationId ID của cuộc trò chuyện
     * @return Khóa ngẫu nhiên đã được tạo
     */
    public String generateRandomKey(Long conversationId) {
        // Tạo một khóa ngẫu nhiên dài 32 ký tự
        StringBuilder key = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        java.util.Random random = new java.util.Random();

        for (int i = 0; i < 32; i++) {
            key.append(chars.charAt(random.nextInt(chars.length())));
        }

        // Lưu và trả về khóa
        String generatedKey = key.toString();

        // Xử lý đặc biệt cho Global Chat
        if (conversationId == GLOBAL_CHAT_ID) {
            globalChatKey = generatedKey;
            globalChatEncryption = true;
        } else {
            setKey(conversationId, generatedKey);
        }

        return generatedKey;
    }
}