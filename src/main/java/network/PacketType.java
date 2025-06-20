package network;

public enum PacketType {
    LOGIN,          // đăng nhập
    MSG,            // chat chung
    FILE,           // gửi file
    ACK,            // xác nhận
    USRLIST,        // danh sách user online
    PM,             // private message
    GET_HISTORY,    // lấy lịch sử chat
    HISTORY,        // phản hồi lịch sử
    CREATE_GROUP,   // tạo nhóm chat
    GROUP_MSG,      // tin nhắn nhóm
    GROUP_CREATED,  // Thêm mới - Server gửi về khi nhóm được tạo thành công

    CONV_LIST,      // danh sách conversation
    JOIN_CONV,      // tham gia conversation
    FILE_META,      // thông tin file (name, size)
    FILE_CHUNK,     // từng chunk của file
    GET_FILE,       // yêu cầu tải file
    FILE_THUMB,     // thumbnail của file
    GET_THUMB,      // yêu cầu thumbnail
    AVATAR_UPLOAD,  // upload avatar mới
    AVATAR_DATA,    // dữ liệu avatar
    GET_AVATAR,     // yêu cầu avatar
    GET_USERLIST,   // yêu cầu danh sách user online
    FRIEND_REQUEST,      // gửi lời mời kết bạn
    FRIEND_ACCEPT,       // chấp nhận lời mời
    FRIEND_REJECT,       // từ chối lời mời
    FRIEND_PENDING_LIST, // trả về danh sách lời mời PENDING
    FRIEND_STATUS,        // trả về trạng thái bạn bè
    FRIEND_REQUEST_NOTIFICATION,  // thông báo có lời mời kết bạn mới
    FRIEND_REQUEST_ACCEPTED,      // thông báo lời mời được chấp nhận
    FRIEND_REQUEST_REJECTED,      // thông báo lời mời bị từ chối
    GET_CONV_LIST           // Lấy danh sách cuộc trò chuyện
}
