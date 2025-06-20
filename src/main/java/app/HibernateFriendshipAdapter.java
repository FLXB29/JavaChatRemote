package app;

import app.model.Friendship;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Adapter cho Gson để xử lý các đối tượng Friendship có reference đến User
 * để tránh LazyInitializationException
 */
public class HibernateFriendshipAdapter implements JsonSerializer<Friendship> {

    @Override
    public JsonElement serialize(Friendship friendship, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        jsonObject.addProperty("id", friendship.getId());
        
        // Xử lý thận trọng các đối tượng User
        if (friendship.getUser1() != null) {
            JsonObject user1 = new JsonObject();
            user1.addProperty("id", friendship.getUser1().getId());
            user1.addProperty("username", friendship.getUser1().getUsername());
            if (friendship.getUser1().getFullName() != null) {
                user1.addProperty("fullName", friendship.getUser1().getFullName());
            }
            if (friendship.getUser1().getAvatarPath() != null) {
                user1.addProperty("avatarPath", friendship.getUser1().getAvatarPath());
            }
            user1.addProperty("useDefaultAvatar", friendship.getUser1().isUseDefaultAvatar());
            jsonObject.add("user1", user1);
        }
        
        if (friendship.getUser2() != null) {
            JsonObject user2 = new JsonObject();
            user2.addProperty("id", friendship.getUser2().getId());
            user2.addProperty("username", friendship.getUser2().getUsername());
            if (friendship.getUser2().getFullName() != null) {
                user2.addProperty("fullName", friendship.getUser2().getFullName());
            }
            if (friendship.getUser2().getAvatarPath() != null) {
                user2.addProperty("avatarPath", friendship.getUser2().getAvatarPath());
            }
            user2.addProperty("useDefaultAvatar", friendship.getUser2().isUseDefaultAvatar());
            jsonObject.add("user2", user2);
        }
        
        jsonObject.addProperty("status", friendship.getStatus().name());
        
        if (friendship.getCreatedAt() != null) {
            jsonObject.add("createdAt", context.serialize(friendship.getCreatedAt()));
        }
        
        return jsonObject;
    }
} 