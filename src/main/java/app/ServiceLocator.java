package app;

import app.dao.ConversationDAO;
import app.model.Conversation;
import app.service.*;
import app.util.HibernateUtil;

public final class ServiceLocator {
    private static UserService             userService;
    private static ChatService             chatService;
    private static MessageService          messageService;
    private static ConversationDAO         conversationDAO;

    /* NEW: service lớp duy nhất */
    private static FriendshipService       friendshipService;
    private static MessageReceiptService   messageReceiptService;
    private static NotificationService     notificationService;

    /** Khởi tạo.  isServer=true → không cần ChatService */
    public static void init(boolean isServer){
        try {
            HibernateUtil.getSessionFactory();               // mở SessionFactory

            userService          = new UserService();
            chatService          = isServer ? null : new ChatService();
            messageService       = new MessageService();
            conversationDAO      = new ConversationDAO();

            friendshipService    = new FriendshipService();
            messageReceiptService= new MessageReceiptService();
            notificationService  = new NotificationService();

            /* Bảo đảm có phòng "Global Chat" */
            Conversation global = conversationDAO.findByName("Global Chat");
            if(global==null){
                global = new Conversation("GROUP","Global Chat");
                conversationDAO.save(global);
            }
            if(chatService!=null) chatService.setConversation(global);
            
            // Kiểm tra và log trạng thái khởi tạo
            System.out.println("[DEBUG] ServiceLocator init: " +
                "userService=" + (userService != null) + ", " +
                "messageService=" + (messageService != null) + ", " +
                "friendshipService=" + (friendshipService != null));
                
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi khởi tạo ServiceLocator: " + e.getMessage());
            e.printStackTrace();
            
            // Thử lại việc khởi tạo các service cơ bản nếu có lỗi
            try {
                if (userService == null) userService = new UserService();
                if (messageService == null) messageService = new MessageService();
                if (friendshipService == null) friendshipService = new FriendshipService();
                if (conversationDAO == null) conversationDAO = new ConversationDAO();
            } catch (Exception ex) {
                System.err.println("[ERROR] Lỗi khởi tạo lại các service: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /* overload cho client */
    public static void init(){ init(false); }

    /* ======== getters ======== */
    public static UserService           userService()     { 
        if (userService == null) {
            System.out.println("[WARN] userService đang null, đang tự động khởi tạo lại");
            userService = new UserService();
        }
        return userService; 
    }
    public static ChatService           chat()            { return chatService; }
    public static MessageService        messageService()  { return messageService; }
    public static ConversationDAO       conversationDAO() { return conversationDAO; }
    public static FriendshipService     friendship()      { return friendshipService; }
    public static MessageReceiptService messageReceipt()  { return messageReceiptService; }
    public static NotificationService   notification()    { return notificationService; }

    public static void shutdown(){
        HibernateUtil.getSessionFactory().close();
    }
}
