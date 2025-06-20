//package app.controller.chat;
//
//import network.ClientConnection;
//
///**
// * Đây là một class giả để tham khảo API
// * KHÔNG SỬ DỤNG TRONG SẢN PHẨM CUỐI CÙNG
// */
//public class ChatController {
//    /* API cho tham khảo */
//    private void setupConnectionListeners() {
//        // Lắng nghe tin nhắn Global
//        ClientConnection conn = ServiceLocator.connection();
//        conn.onGlobalMessage((sender, msg) -> {
//            // Hiển thị tin nhắn global
//        });
//
//        // Lắng nghe tin nhắn riêng tư
//        conn.onPrivateMessage((sender, msg) -> {
//            // Hiển thị tin nhắn riêng tư
//        });
//
//        // Lắng nghe tin nhắn nhóm
//        conn.onGroupMessage((groupName, sender, content) -> {
//            // Hiển thị tin nhắn nhóm
//        });
//    }
//
//    /* Gửi tin nhắn */
//    private void sendMessage() {
//        // Lấy connection
//        ClientConnection conn = ServiceLocator.connection();
//
//        // Gửi tin nhắn tùy theo loại
//        if (isGlobalChat) {
//            conn.sendText(message);
//        } else if (isGroup) {
//            conn.sendGroup(groupId, message);
//        } else {
//            conn.sendPrivate(recipient, message);
//        }
//    }
//
//    /* Yêu cầu lịch sử chat */
//    private void requestChatHistory() {
//        if (isGlobalChat) {
//            ServiceLocator.connection().requestGlobalHistory();
//        } else if (isGroup) {
//            ServiceLocator.connection().requestGroupChatHistory(groupId);
//        } else {
//            ServiceLocator.connection().requestHistory(myUsername, otherUsername);
//        }
//    }
//
//    /* Tạo nhóm chat */
//    private void createGroup() {
//        ServiceLocator.connection().createGroup(groupName, memberList);
//    }
//}