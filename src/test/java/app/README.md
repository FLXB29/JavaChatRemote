# Hướng dẫn kiểm tra và sửa lỗi đổi mật khẩu với OTP

File `PasswordChangeTest.java` đã được tạo để kiểm tra quá trình đổi mật khẩu với OTP. Dưới đây là hướng dẫn cách sử dụng và các bước để xác định vấn đề.

## Cách chạy chương trình kiểm tra

1. Mở project trong IntelliJ IDEA
2. Tìm file `src/test/java/app/PasswordChangeTest.java`
3. Nhấp chuột phải vào file và chọn "Run 'PasswordChangeTest.main()'"

## Các bước kiểm tra và sửa lỗi

### 1. Kiểm tra thông tin người dùng

Chương trình sẽ hiển thị thông tin người dùng "anh" từ database, bao gồm:
- Username
- Email
- Mật khẩu hiện tại (dạng hash)

### 2. Kiểm tra mật khẩu hiện tại

Chương trình sẽ kiểm tra xem mật khẩu hiện tại "123" có khớp với hash trong database không.

### 3. Tạo và gửi mã OTP

Chương trình sẽ tạo mã OTP mới và gửi đến email của người dùng. Kiểm tra email `phuclethanh2906@gmail.com` để lấy mã OTP.

### 4. Kiểm tra thông tin OTP trong database

Chương trình sẽ hiển thị thông tin OTP đã lưu trong database, bao gồm:
- Mã OTP
- Thời gian hết hạn

### 5. Thực hiện đổi mật khẩu với OTP

Chương trình sẽ thử đổi mật khẩu từ "123" sang "1234" sử dụng mã OTP đã tạo.

### 6. Kiểm tra kết quả

Nếu thành công, chương trình sẽ hiển thị:
- Xác nhận mật khẩu mới đã được cập nhật
- Xác nhận OTP đã bị xóa sau khi sử dụng

Nếu thất bại, chương trình sẽ hiển thị thông báo lỗi.

## Các vấn đề thường gặp và cách khắc phục

### 1. Mã OTP không đúng

Nếu gặp lỗi "Mã OTP không đúng", hãy kiểm tra:

- **So sánh mã OTP**: Kiểm tra mã OTP hiển thị trong console với mã OTP nhận được qua email. Chúng phải giống hệt nhau.

- **Kiểm tra phương thức so sánh OTP**: Trong `UserService.java`, phương thức `changePasswordWithOTP` sử dụng `Objects.equals(otp, user.getOtpCode())` để so sánh OTP. Đảm bảo rằng không có khoảng trắng thừa hoặc sự khác biệt về chữ hoa/chữ thường.

- **Kiểm tra thời gian hết hạn**: Mã OTP có thời hạn 5 phút. Đảm bảo rằng bạn sử dụng mã OTP trước khi nó hết hạn.

### 2. Mật khẩu hiện tại không đúng

Nếu gặp lỗi "Mật khẩu hiện tại không đúng", hãy kiểm tra:

- **Xác minh mật khẩu trong database**: Đảm bảo rằng mật khẩu hiện tại ("123") khớp với hash trong database.

- **Kiểm tra phương thức kiểm tra mật khẩu**: Trong `PasswordUtil.java`, phương thức `checkPassword` sử dụng BCrypt để kiểm tra mật khẩu. Đảm bảo rằng nó hoạt động chính xác.

## Sửa đổi mã nguồn

Nếu bạn cần sửa đổi mã nguồn để khắc phục vấn đề, hãy xem xét các file sau:

1. **ProfileController.java**: Phương thức `changePassword` xử lý việc đổi mật khẩu từ giao diện người dùng.

2. **UserService.java**: Phương thức `changePasswordWithOTP` xử lý việc đổi mật khẩu với xác thực OTP.

3. **EmailUtil.java**: Phương thức `sendOTPEmail` xử lý việc gửi mã OTP qua email.

## Ghi chú

- Đảm bảo rằng email `phuclethanh2906@gmail.com` có thể nhận được email từ ứng dụng.
- Kiểm tra cả thư mục Spam/Junk nếu không thấy email OTP.
- Nếu bạn thay đổi mật khẩu thành công, hãy nhớ mật khẩu mới ("1234") để đăng nhập sau này.