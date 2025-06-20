package app;

import app.model.Friendship;
import app.model.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;

/**
 * Utility class để tạo các instance Gson đã được cấu hình đúng
 * với các type adapter cần thiết để xử lý lazy loading và các kiểu dữ liệu đặc biệt
 */
public class GsonUtils {

    /**
     * Tạo một instance Gson cơ bản với LocalDateTime adapter
     * @return Gson instance
     */
    public static Gson createBasicGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
    }
    
    /**
     * Tạo một instance Gson đầy đủ với tất cả các adapter cần thiết
     * @return Gson instance
     */
    public static Gson createFullGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(User.class, new HibernateUserAdapter())
                .registerTypeAdapter(Friendship.class, new HibernateFriendshipAdapter())
                .create();
    }
    
    /**
     * Tạo instance Gson để sử dụng cho serialization của User và các đối tượng liên quan
     * @return Gson instance
     */
    public static Gson createUserSafeGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(User.class, new HibernateUserAdapter())
                .create();
    }

    /**
     * Tạo instance Gson để sử dụng cho serialization của Friendship và các đối tượng liên quan
     * @return Gson instance
     */
    public static Gson createFriendshipSafeGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter()) 
                .registerTypeAdapter(Friendship.class, new HibernateFriendshipAdapter())
                .create();
    }
} 