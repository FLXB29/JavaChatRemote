package network;
import java.io.Serializable;

public record Packet(PacketType type, String header, byte[] payload) implements Serializable {}
