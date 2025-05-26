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
