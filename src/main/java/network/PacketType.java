package network;

public enum PacketType {
    LOGIN, ACK,
    /* broadcast */
    MSG, FILE,
    /* PM */
    PM,
    /* conversation */
    CONV_LIST,     // server → client (ngay sau LOGIN)
    JOIN_CONV,     // client → server (khi chọn 1 conv)
    HISTORY,       // server → client (payload = JSON + header = convId)
    /* group */
    CREATE_GROUP,  // client → server
    GROUP_MSG,      // 2‑chiều (header = convId),
    USRLIST ,     // server → client (header = "ONLINE_USERS", payload = userList)
    GET_HISTORY, // client → server (header = "alice->bob" hoặc "alice->Global")
    FILE_META,   // server → client (metadata)
    GET_FILE,    // client → server
    FILE_CHUNK,   // server → client (mỗi 32 KB)
    GET_THUMB,   // client → server
    FILE_THUMB   // server → client
}
