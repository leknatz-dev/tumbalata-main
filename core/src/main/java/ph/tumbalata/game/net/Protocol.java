package ph.tumbalata.game.net;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * How phones talk on the same Wi-Fi. Finding rooms: a phone looking for a game sends {@link #PROBE} as a UDP
 * broadcast to {@link #DISCOVERY_PORT}, and every host answers with its room ({@link LanDiscovery}). Playing: the
 * phone connects to the host over TCP ({@link #TCP_PORT}) and they send messages: an int length, a type byte, then the
 * message itself.
 */
public final class Protocol {
    /** Bumped whenever the messages change: a phone with another version can't join. */
    public static final int VERSION = 1;
    public static final int TCP_PORT = 54555;
    public static final int DISCOVERY_PORT = 54777;

    static final String PROBE = "TUMBALATA?";
    static final String REPLY = "TUMBALATA!";

    // Phone -> host
    /** Asks to join: protocol version (int), the player's name (UTF). */
    static final byte HELLO = 1;
    /** Leaving the room. */
    static final byte LEAVE = 2;

    // Host -> phone
    /** The room: your player number (byte), then {@code Lobby.writeTo}. */
    static final byte LOBBY = 10;
    /** Can't join (or removed): the reason (UTF). The connection closes after it. */
    static final byte REJECT = 11;

    /** Longest message accepted (a bad or foreign connection can't make us allocate more). */
    static final int MAX_MESSAGE = 64 * 1024;

    private Protocol() {}

    /** Writes one message. Callers synchronize on {@code out} when several threads may write. */
    static void write(DataOutputStream out, byte type, byte[] body) throws IOException {
        out.writeInt(body.length + 1);
        out.writeByte(type);
        out.write(body);
        out.flush();
    }

    /** One message read: its type and body. */
    static final class Message {
        final byte type;
        final byte[] body;

        Message(byte type, byte[] body) {
            this.type = type;
            this.body = body;
        }

        DataInputStream reader() {
            return new DataInputStream(new java.io.ByteArrayInputStream(body));
        }
    }

    /** Reads one message (blocks). */
    static Message read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > MAX_MESSAGE) throw new IOException("bad message length " + length);
        byte type = in.readByte();
        byte[] body = new byte[length - 1];
        in.readFully(body);
        return new Message(type, body);
    }

    /** Builds a message body. */
    interface BodyWriter {
        void write(DataOutputStream out) throws IOException;
    }

    static byte[] body(BodyWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // writing to memory doesn't fail
        }
    }
}
