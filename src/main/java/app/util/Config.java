package app.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();
    
    static {
        try (InputStream is = Config.class.getResourceAsStream("/config.properties")) {
            props.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
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