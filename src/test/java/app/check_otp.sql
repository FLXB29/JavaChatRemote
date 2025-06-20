-- Script SQL để kiểm tra và cập nhật thông tin OTP cho người dùng "anh"

-- 1. Kiểm tra thông tin người dùng hiện tại
SELECT id, username, email, password_hash, otp_code, otp_expiry_time 
FROM users 
WHERE username = 'anh';

-- 2. Kiểm tra mật khẩu hiện tại (chỉ để tham khảo, không thể kiểm tra trực tiếp từ SQL)
-- Mật khẩu hiện tại: "123"
-- Cần sử dụng PasswordUtil.checkPassword("123", password_hash) trong Java

-- 3. Cập nhật mã OTP mới (nếu cần)
-- Thay thế '123456' bằng mã OTP bạn muốn sử dụng
-- Thời gian hết hạn: 5 phút từ thời điểm hiện tại
UPDATE users 
SET otp_code = '123456', 
    otp_expiry_time = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP + INTERVAL '5 minutes')) * 1000 
WHERE username = 'anh';

-- 4. Kiểm tra lại thông tin OTP sau khi cập nhật
SELECT id, username, email, otp_code, otp_expiry_time, 
       TO_TIMESTAMP(otp_expiry_time / 1000) AS expiry_time_readable
FROM users 
WHERE username = 'anh';

-- 5. Cập nhật mật khẩu trực tiếp (chỉ sử dụng trong trường hợp khẩn cấp)
-- Lưu ý: Điều này bỏ qua việc kiểm tra OTP và mật khẩu hiện tại
-- Thay thế '$2a$10$newPasswordHash' bằng hash BCrypt thực tế của mật khẩu mới
/*
UPDATE users 
SET password_hash = '$2a$10$newPasswordHash', 
    otp_code = NULL, 
    otp_expiry_time = NULL 
WHERE username = 'anh';
*/

-- 6. Kiểm tra lại thông tin người dùng sau khi cập nhật mật khẩu
/*
SELECT id, username, email, password_hash, otp_code, otp_expiry_time 
FROM users 
WHERE username = 'anh';
*/