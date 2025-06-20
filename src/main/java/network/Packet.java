package network;
import java.io.Serializable;

public record Packet(PacketType type, String header, byte[] payload) implements Serializable {
    private static final long serialVersionUID = 1L;
}
