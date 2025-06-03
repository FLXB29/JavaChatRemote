package app.service;

import app.dao.MessageDAO;
import app.dao.UserDAO;
import app.model.*;
import app.repository.ConversationRepository;
import app.util.DatabaseEncryptionUtil;
//import app.util.DatabaseKeyManager;
import jakarta.transaction.Transactional;
import network.Packet;
import network.PacketType;
import network.ServerApp;
import org.springframework.stereotype.Service;

@Service
public class GroupMessageService {

    private final ConversationRepository convRepo;
    private final MessageDAO messageDao;
    private final UserDAO userDao = new UserDAO();

    /* ★ thêm ServerApp để broadcast */
    private ServerApp server;

    public void setServer(ServerApp s) {
        this.server = s;
    }

    public GroupMessageService(ConversationRepository c, MessageDAO m) {
        this.convRepo = c;
        this.messageDao = m;
    }

    /**
     * Lưu & tự broadcast cho thành viên
     */
    @Transactional
    public void saveAndBroadcast(long convId, String fromUser, String content) {
        Conversation conv = convRepo.findByIdWithMembers(convId)
                .orElseThrow();

        // 1 ▪ MÃ HÓA trước khi lưu DB
        String encryptedContent = DatabaseEncryptionUtil.encrypt(content);
        Message msg = new Message(conv,
                userDao.findByUsername(fromUser),
                encryptedContent, null);  // ← Lưu content ĐÃ MÃ HÓA
        messageDao.save(msg);

        // 2 ▪ broadcast content GỐC (chưa mã hóa) cho online users
        String payload = fromUser + "|" + content;  // ← Gửi content GỐC
        Packet pkt = new Packet(PacketType.GROUP_MSG,
                String.valueOf(convId),
                payload.getBytes());

        for (Membership m : conv.getMemberships())
            server.sendToUser(m.getUser().getUsername(), pkt);

    }
}