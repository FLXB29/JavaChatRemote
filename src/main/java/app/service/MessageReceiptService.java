package app.service;

import app.dao.MessageReceiptDAO;
import app.model.Message;
import app.model.MessageReceipt;
import app.model.User;
import java.time.LocalDateTime;
import java.util.List;

public class MessageReceiptService {

    private final MessageReceiptDAO dao = new MessageReceiptDAO();

    /* ---------- THAO TÁC ---------- */

    public void markDelivered(User user, Message msg) {
        upsert(user, msg, MessageReceipt.State.DELIVERED);
    }

    public void markSeen(User user, Message msg) {
        upsert(user, msg, MessageReceipt.State.SEEN);
    }

    /* ---------- TRUY VẤN ---------- */

    public MessageReceipt.State getReceiptState(User u, Message m) {
        MessageReceipt r = dao.find(u, m);
        return (r == null) ? null : r.getState();
    }

    public List<MessageReceipt> getReceipts(Message m) {
        return dao.findByMessage(m);
    }

    /* ---------- PRIVATE ---------- */

    private void upsert(User u, Message m, MessageReceipt.State state) {
        MessageReceipt r = dao.find(u, m);
        if (r == null) {
            dao.save(new MessageReceipt(m, u, state));
        } else if (r.getState() != state) {
            r.setState(state);
            r.setTime(LocalDateTime.now());
            dao.update(r);
        }
    }
}
