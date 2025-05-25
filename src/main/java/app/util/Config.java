package app.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();

    static {
        try (InputStream is = Config.class.getResourceAsStream("/config.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.println("Warning: config.properties not found, using defaults");
            }
        } catch (IOException e) {
            System.err.println("Warning: Error loading config.properties, using defaults");
            e.printStackTrace();
        }

        // Ưu tiên thuộc tính hệ thống (System properties)
        String serverHost = System.getProperty("server.host");
        String serverPort = System.getProperty("server.port");
        String clientHost = System.getProperty("client.host");
        String clientPort = System.getProperty("client.port");

        if (serverHost != null) props.setProperty("server.host", serverHost);
        if (serverPort != null) props.setProperty("server.port", serverPort);
        if (clientHost != null) props.setProperty("client.host", clientHost);
        if (clientPort != null) props.setProperty("client.port", clientPort);
    }

    public static String getServerHost() {
        return props.getProperty("server.host", "localhost");
    }

    public static int getServerPort() {
        return Integer.parseInt(props.getProperty("server.port", "5000"));
    }

    public static String getClientHost() {
        return props.getProperty("client.host", "localhost");
    }

    public static int getClientPort() {
        return Integer.parseInt(props.getProperty("client.port", "5000"));
    }
}