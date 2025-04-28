package app;

import app.dao.ConversationDAO;
import app.model.Conversation;
import app.service.MessageService;
import app.service.UserService;
import app.service.ChatService;
import app.util.HibernateUtil;

public class ServiceLocator {
    private static UserService userService;
    private static ChatService chatService;
    private static MessageService messageService;

    // Thêm 1 conversationDAO
    private static ConversationDAO conversationDAO;

    public static void init() {
        // Khởi tạo session factory => sẵn sàng
        HibernateUtil.getSessionFactory();

        // Tạo DAO / Service
        userService = new UserService();
        chatService = new ChatService();
        messageService = new MessageService();
        conversationDAO = new ConversationDAO();

        // Tìm hoặc tạo Global Chat
        Conversation globalConv = conversationDAO.findByName("Global Chat");
        if (globalConv == null) {
            // Nếu chưa có => tạo mới
            globalConv = new Conversation("GROUP", "Global Chat");
            conversationDAO.save(globalConv);
        }

        // Gán nó làm conversation "mặc định" cho ChatService
        chatService.setConversation(globalConv);
    }

    public static UserService userService() { return userService; }
    public static ChatService chat() { return chatService; }
    public static MessageService messageService() { return messageService; }
    public static ConversationDAO conversationDAO() { return conversationDAO; }

    public static void shutdown() {

        HibernateUtil.getSessionFactory().close();
    }
}
