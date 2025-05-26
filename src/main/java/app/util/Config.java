package app.util;

import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try {
            // Đọc từ classpath thay vì file system
            InputStream input = Config.class.getClassLoader()
                    .getResourceAsStream("config.properties");

            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                // Sử dụng giá trị mặc định
                setDefaultProperties();
            } else {
                properties.load(input);
                input.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            setDefaultProperties();
        }
    }

    private static void setDefaultProperties() {
        // Giá trị mặc định nếu không đọc được file
        properties.setProperty("server.host", "0.0.0.0");
        properties.setProperty("server.port", "5000");
        properties.setProperty("client.host", "13.229.231.49");
        properties.setProperty("client.port", "5000");
        properties.setProperty("db.url", "jdbc:mysql://13.229.231.49:3306/chatdb?serverTimezone=UTC");
        properties.setProperty("db.username", "chatuser");
        properties.setProperty("db.password", "123456");
        properties.setProperty("db.driver", "com.mysql.cj.jdbc.Driver");
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getDbUrl() {
        return properties.getProperty("db.url");
    }

    public static String getDbUsername() {
        return properties.getProperty("db.username");
    }

    public static String getDbPassword() {
        return properties.getProperty("db.password");
    }

    public static String getDbDriver() {
        return properties.getProperty("db.driver");
    }

    public static String getServerHost() {
        return properties.getProperty("server.host", "0.0.0.0");
    }

    public static int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "5000"));
    }

    public static String getClientHost() {
        return properties.getProperty("client.host", "localhost");
    }

    public static int getClientPort() {
        return Integer.parseInt(properties.getProperty("client.port", "5000"));
    }
}