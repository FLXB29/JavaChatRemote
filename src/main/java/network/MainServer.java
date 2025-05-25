package network;

import app.util.Config;
import app.util.DatabaseKeyManager;
import app.util.HibernateUtil;
import network.ServerApp;

public class MainServer {

    public static void main(String[] args) {
        try {
            /* 1 ▪ Khởi tạo cấu hình (nếu lớp Config của bạn có phương thức init/load) */
            // Config.load();          // ← Bỏ comment nếu cần

            /* 2 ▪ Khởi tạo mã hóa database */
            System.out.println("Initializing database encryption...");
            DatabaseKeyManager.initialize();

            /* 3 ▪ (Tuỳ chọn) khởi tạo Hibernate sớm để bắt lỗi cấu hình sớm */
            HibernateUtil.getSessionFactory();

            /* 4 ▪ Xác định cổng: ưu tiên tham số dòng lệnh, nếu không lấy từ config.properties */
            int port = args.length > 0
                    ? Integer.parseInt(args[0])
                    : Config.getServerPort();      // server.port trong config.properties

            /* 5 ▪ Khởi chạy server */
            ServerApp server = new ServerApp(port);
            server.start();                        // blocking loop

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Không khởi động được Server – kiểm tra cấu hình & cổng!");
        }
    }
}