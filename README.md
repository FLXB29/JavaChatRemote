# MyMessenger - Ứng dụng Chat

## Hướng dẫn sử dụng cho Server (người host)

1. Cấu hình port forwarding trên router của bạn (port 5000)
2. Chạy class `network.MainServer` để khởi động server
3. Chạy ứng dụng chính để đăng nhập và sử dụng

## Hướng dẫn sử dụng cho Client (người kết nối)

1. Clone repository về máy
2. Mở project trong IDE (IntelliJ IbảnhDEA, Eclipse, etc.)
3. Mở class `app.ClientApp.java`
4. Thay đổi dòng `String serverIP = "14.226.123.235";` thành IP của server
5. Chạy class `app.ClientApp` để khởi động ứng dụng
6. Đăng nhập và bắt đầu trò chuyện

## Yêu cầu hệ thống

- Java 11+
- JavaFX
- Các thư viện đã được cung cấp trong `pom.xml` (nếu sử dụng Maven)


# ChatController Refactoring

Đây là dự án refactoring ChatController để chia thành nhiều thành phần nhỏ hơn, dễ bảo trì và mở rộng.

## Cấu trúc thư mục

```
src/main/java/app/controller/chat/
├── component/            # Các thành phần UI
│   ├── MessageBubble.java
│   └── ToastNotification.java
├── factory/              # Các cell factory
│   ├── FriendRequestCellFactory.java
│   ├── GroupCellFactory.java
│   ├── RecentChatCellFactory.java
│   └── SearchUserCellFactory.java
├── handler/              # Các handler xử lý sự kiện
│   ├── FileHandler.java
│   ├── FriendshipHandler.java
│   ├── MessageHandler.java
│   └── UserActionHandler.java
├── model/                # Model riêng cho chat
│   └── RecentChatCellData.java
├── util/                 # Các tiện ích
│   ├── AvatarHelper.java
│   └── ChatUIHelper.java
└── ChatControllerRefactored.java  # Controller chính đã refactor
```

## Các thành phần chính

### Components

- **MessageBubble**: Tạo và hiển thị các tin nhắn trong chat
- **ToastNotification**: Hiển thị thông báo popup

### Factories

- **FriendRequestCellFactory**: Tạo cell cho danh sách lời mời kết bạn
- **GroupCellFactory**: Tạo cell cho danh sách nhóm
- **RecentChatCellFactory**: Tạo cell cho danh sách chat gần đây
- **SearchUserCellFactory**: Tạo cell cho danh sách kết quả tìm kiếm

### Handlers

- **FileHandler**: Xử lý các thao tác liên quan đến file
- **FriendshipHandler**: Xử lý các thao tác liên quan đến kết bạn
- **MessageHandler**: Xử lý các thao tác liên quan đến tin nhắn
- **UserActionHandler**: Xử lý các thao tác liên quan đến người dùng

### Models

- **RecentChatCellData**: Dữ liệu cho cell trong danh sách chat gần đây

### Utils

- **AvatarHelper**: Tiện ích xử lý avatar
- **ChatUIHelper**: Tiện ích cho UI chat

## Cách sử dụng

Thay vì sử dụng ChatController trực tiếp, bạn có thể sử dụng ChatControllerRefactored và các thành phần của nó:

```java
// Tạo các handler
FileHandler fileHandler = new FileHandler(chatService, window);
MessageHandler messageHandler = new MessageHandler(clientConnection, username, messagesContainer, rootNode, fileHandler, this::updateRecentChats);
FriendshipHandler friendshipHandler = new FriendshipHandler(clientConnection, username, this::updateFriendRequests, this::refreshAllData);
UserActionHandler userActionHandler = new UserActionHandler(clientConnection, username, rootNode, messageHandler, friendshipHandler, this::updateSearchResults);

// Sử dụng các handler
messageHandler.sendTextMessage("Hello world!");
fileHandler.selectAndSendFile(conversationId);
friendshipHandler.sendFriendRequest(user);
userActionHandler.searchUsers("john");
```

## Lưu ý

- Hiện tại, ChatControllerRefactored đang ở phiên bản đơn giản để demo cấu trúc
- Cần tiếp tục hoàn thiện các thành phần và tích hợp vào ứng dụng
- Một số phương thức trong các handler có thể cần điều chỉnh để phù hợp với API hiện tại
