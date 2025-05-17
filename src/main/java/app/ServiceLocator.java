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
        HibernateUtil.getSessionFactory();               // mở SessionFactory

        userService          = new UserService();
        chatService          = isServer ? null : new ChatService();
        messageService       = new MessageService();
        conversationDAO      = new ConversationDAO();

        friendshipService    = new FriendshipService();
        messageReceiptService= new MessageReceiptService();
        notificationService  = new NotificationService();

        /* Bảo đảm có phòng “Global Chat” */
        Conversation global = conversationDAO.findByName("Global Chat");
        if(global==null){
            global = new Conversation("GROUP","Global Chat");
            conversationDAO.save(global);
        }
        if(chatService!=null) chatService.setConversation(global);
    }

    /* overload cho client */
    public static void init(){ init(false); }

    /* ======== getters ======== */
    public static UserService           userService()     { return userService; }
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
