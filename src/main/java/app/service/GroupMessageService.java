package app.service;

import app.dao.MessageDAO;
import app.dao.UserDAO;
import app.model.*;
import app.repository.ConversationRepository;
import app.util.DatabaseEncryptionUtil;
import app.util.DatabaseKeyManager;
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
    public void setServer(ServerApp s){ this.server = s; }

    public GroupMessageService(ConversationRepository c, MessageDAO m) {
        this.convRepo = c;
        this.messageDao = m;
    }

    /** Lưu & tự broadcast cho thành viên */
    @Transactional
    public void saveAndBroadcast(long convId, String fromUser, String content){
        Conversation conv = convRepo.findByIdWithMembers(convId)
                .orElseThrow();

        // Mã hóa nội dung nếu bật tính năng mã hóa
        String finalContent = content;
        if (DatabaseKeyManager.isEncryptionEnabled() && content != null && !content.startsWith("[FILE]")) {
            finalContent = DatabaseEncryptionUtil.encrypt(content);
            System.out.println("[DEBUG] Đã mã hóa tin nhắn nhóm: " + content + " -> " + finalContent);
        }

        // 1 ▪ lưu DB với nội dung đã mã hóa
        Message msg = new Message(conv,
                userDao.findByUsername(fromUser),
                finalContent, null);
        messageDao.save(msg);

        // 2 ▪ broadcast vẫn dùng nội dung gốc để người dùng đọc được
        String payload = fromUser + "|" + content;
        Packet pkt = new Packet(PacketType.GROUP_MSG,
                String.valueOf(convId),
                payload.getBytes());

        for(Membership m : conv.getMemberships())
            server.sendToUser(m.getUser().getUsername(), pkt);
    }
}