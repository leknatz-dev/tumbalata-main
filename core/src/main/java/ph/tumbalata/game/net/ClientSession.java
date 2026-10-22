package ph.tumbalata.game.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

import ph.tumbalata.game.Lobby;

/**
 * This phone in someone else's room: connects to the host, says hello with the player's name, and receives the room.
 * Messages arrive on a network thread and are read on the game thread with {@link #poll()}.
 */
public final class ClientSession implements Closeable {
    /** Something that happened on the network. */
    public static final class Event {
        public enum Kind { LOBBY, GO, STATE, REJECTED, CLOSED }

        public final Kind kind;
        /** REJECTED: why. */
        public final String reason;
        private final byte[] body;

        Event(Kind kind, String reason, byte[] body) {
            this.kind = kind;
            this.reason = reason;
            this.body = body;
        }

        /** GO: which screen. */
        public int screen() {
            return body[0];
        }

        /** GO: what that screen needs (after the screen byte). STATE: the whole message. */
        public byte[] body() {
            return kind == Kind.GO ? java.util.Arrays.copyOfRange(body, 1, body.length) : body;
        }

        /** LOBBY: copies the host's room into {@code lobby} and returns which player this phone is. */
        public int applyTo(Lobby lobby) throws IOException {
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int yourId = in.readByte();
            lobby.readFrom(in);
            return yourId;
        }
    }

    private final Socket socket;
    private final DataOutputStream out;
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private volatile boolean open = true;

    private ClientSession(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /** Connects to a host and asks to join as {@code name} (blocks up to {@code timeoutMs}). */
    public static ClientSession connect(InetAddress host, int port, String name, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            ClientSession session = new ClientSession(socket);
            session.send(Protocol.HELLO, Protocol.body(o -> {
                o.writeInt(Protocol.VERSION);
                o.writeUTF(name);
            }));
            Thread reader = new Thread(session::readLoop, "client-read");
            reader.setDaemon(true);
            reader.start();
            return session;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** The next thing that happened, or null. Call from the game thread. */
    public Event poll() {
        return events.poll();
    }

    /** A move on the character select ({@code Protocol.PICK_*}; {@code card} for {@code PICK_CHOOSE}). */
    public void sendPick(int action, int card) {
        trySend(Protocol.PICK, new byte[] { (byte) action, (byte) card });
    }

    /** What this phone's player holds for one match step ({@code InputFrame.pack()}). */
    public void sendInput(int packedFrame) {
        trySend(Protocol.INPUT, Protocol.body(o -> o.writeInt(packedFrame)));
    }

    private void trySend(byte type, byte[] body) {
        if (!open) return;
        try {
            send(type, body);
        } catch (IOException e) {
            close(); // the reader notices and reports CLOSED
        }
    }

    /** Leaves the room (tells the host, then disconnects). */
    public void leave() {
        if (!open) return;
        try {
            send(Protocol.LEAVE, new byte[0]);
        } catch (IOException ignored) {
            // disconnecting anyway
        }
        close();
    }

    @Override
    public void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }

    private void send(byte type, byte[] body) throws IOException {
        synchronized (out) {
            Protocol.write(out, type, body);
        }
    }

    private void readLoop() {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            while (open) {
                Protocol.Message m = Protocol.read(in);
                if (m.type == Protocol.LOBBY) {
                    events.add(new Event(Event.Kind.LOBBY, null, m.body));
                } else if (m.type == Protocol.GO) {
                    events.add(new Event(Event.Kind.GO, null, m.body));
                } else if (m.type == Protocol.STATE) {
                    events.add(new Event(Event.Kind.STATE, null, m.body));
                } else if (m.type == Protocol.REJECT) {
                    events.add(new Event(Event.Kind.REJECTED, m.reader().readUTF(), null));
                    open = false;
                }
            }
        } catch (IOException e) {
            // the host went away (or we left)
        }
        if (open) events.add(new Event(Event.Kind.CLOSED, null, null));
        close();
    }
}
