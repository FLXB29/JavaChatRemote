package app;

import app.model.User;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Adapter cho Gson để xử lý các đối tượng User có chứa lazy collections
 */
public class HibernateUserAdapter implements JsonSerializer<User> {

    @Override
    public JsonElement serialize(User user, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        // Chỉ đưa vào các thông tin cần thiết, bỏ qua các collection lazy
        jsonObject.addProperty("id", user.getId());
        jsonObject.addProperty("username", user.getUsername());
        
        // Không đưa password vào JSON vì lý do bảo mật
        // jsonObject.addProperty("passwordHash", user.getPasswordHash());
        
        jsonObject.addProperty("status", user.getStatus());
        
        if (user.getEmail() != null)
            jsonObject.addProperty("email", user.getEmail());
            
        if (user.getFullName() != null)
            jsonObject.addProperty("fullName", user.getFullName());
            
        if (user.getAvatarPath() != null)
            jsonObject.addProperty("avatarPath", user.getAvatarPath());
            
        jsonObject.addProperty("useDefaultAvatar", user.isUseDefaultAvatar());
        
        // Bỏ qua OTP fields vì lý do bảo mật
        // if (user.getOtpCode() != null)
        //     jsonObject.addProperty("otpCode", user.getOtpCode());
        // if (user.getOtpExpiryTime() != null)
        //     jsonObject.addProperty("otpExpiryTime", user.getOtpExpiryTime());
            
        if (user.getLastActiveAt() != null)
            jsonObject.add("lastActiveAt", context.serialize(user.getLastActiveAt()));
        
        // Bỏ qua hoàn toàn collection memberships để tránh LazyInitializationException
        // Nếu cần thì có thể thêm một cách an toàn bằng cách kiểm tra Hibernate.isInitialized()
            
        return jsonObject;
    }
} 