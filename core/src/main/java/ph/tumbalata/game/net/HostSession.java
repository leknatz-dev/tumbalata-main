package ph.tumbalata.game.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import ph.tumbalata.game.Lobby;

/**
 * Hosting a room on the Wi-Fi: answers phones looking for rooms, accepts the ones that join, and sends them the room.
 * The network runs on its own threads; what happens arrives as {@link Event}s, which the game reads with
 * {@link #poll()} on its own thread, so the {@link Lobby} is only ever touched there.
 */
public final class HostSession implements Closeable {
    /** Something that happened on the network. */
    public static final class Event {
        public enum Kind { JOINED, LEFT, PICK, INPUT }

        public final Kind kind;
        /** Which connection (stays the same while that phone is connected). */
        public final int connection;
        /** JOINED: the name the player set on their phone. */
        public final String name;
        /** PICK: the action ({@code Protocol.PICK_*}). INPUT: the packed {@code InputFrame}. */
        public final int value;
        /** PICK: the card, for {@code PICK_CHOOSE}. */
        public final int card;

        Event(Kind kind, int connection, String name) {
            this(kind, connection, name, 0, 0);
        }

        Event(Kind kind, int connection, String name, int value, int card) {
            this.kind = kind;
            this.connection = connection;
            this.name = name;
            this.value = value;
            this.card = card;
        }
    }

    private final ServerSocket server;
    private final DatagramSocket discovery;
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Connection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private volatile boolean open = true;
    private volatile String roomName;
    private volatile int players = 1, roomSize = Lobby.MAX_PLAYERS;

    /**
     * Opens the room. Port 0 picks any free port (for tests); phones find the real one through discovery.
     * @param discoveryPort UDP port to answer room searches on, or -1 for none
     */
    public HostSession(int tcpPort, int discoveryPort, String roomName) throws IOException {
        this.roomName = roomName;
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(tcpPort));
        DatagramSocket udp = null;
        if (discoveryPort >= 0) {
            try {
                udp = new DatagramSocket(null);
                udp.setReuseAddress(true);
                udp.bind(new InetSocketAddress(discoveryPort));
            } catch (SocketException e) {
                if (udp != null) udp.close();
                server.close();
                throw e;
            }
        }
        discovery = udp;
        start("host-accept", this::acceptLoop);
        if (discovery != null) start("host-discovery", this::discoveryLoop);
    }

    private static void start(String name, Runnable loop) {
        Thread t = new Thread(loop, name);
        t.setDaemon(true);
        t.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    /** What room searches see: the room name and how full it is. */
    public void setRoomInfo(String roomName, int players, int roomSize) {
        this.roomName = roomName;
        this.players = players;
        this.roomSize = roomSize;
    }

    /** The next thing that happened, or null. Call from the game thread. */
    public Event poll() {
        return events.poll();
    }

    /** Sends a phone the room, telling it which player it is. */
    public void sendLobby(int connection, Lobby lobby, int yourId) {
        Connection c = connections.get(connection);
        if (c == null) return;
        c.send(Protocol.LOBBY, Protocol.body(out -> {
            out.writeByte(yourId);
            lobby.writeTo(out);
        }));
    }

    /** Sends every phone in the room to a screen ({@code what} = which one, {@code body} = what it needs). */
    public void sendGo(int what, byte[] body) {
        byte[] message = Protocol.body(out -> {
            out.writeByte(what);
            out.write(body);
        });
        for (Connection c : connections.values()) if (c.joined) c.send(Protocol.GO, message);
    }

    /** Sends every phone in the room the match after a step (state and events, already put together). */
    public void sendState(byte[] body) {
        for (Connection c : connections.values()) if (c.joined) c.send(Protocol.STATE, body);
    }

    /** Tells a phone why it can't play (the room is full...) and closes its connection. */
    public void reject(int connection, String reason) {
        Connection c = connections.get(connection);
        if (c == null) return;
        c.send(Protocol.REJECT, Protocol.body(out -> out.writeUTF(reason)));
        c.close(false);
    }

    /** Closes the room: every phone is disconnected. */
    @Override
    public void close() {
        open = false;
        for (Connection c : connections.values()) c.close(false);
        try {
            server.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        if (discovery != null) discovery.close();
    }

    private void acceptLoop() {
        while (open) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true); // small messages, sent right away
                Connection c = new Connection(nextId.getAndIncrement(), socket);
                connections.put(c.id, c);
                start("host-conn-" + c.id, c::readLoop);
            } catch (IOException e) {
                if (!open) return;
            }
        }
    }

    private void discoveryLoop() {
        byte[] buffer = new byte[256];
        while (open) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                discovery.receive(packet);
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (!text.equals(Protocol.PROBE)) continue;
                byte[] reply = LanDiscovery.reply(port(), players, roomSize, roomName);
                discovery.send(new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort()));
            } catch (IOException e) {
                if (!open) return;
            }
        }
    }

    /** One phone's connection. */
    private final class Connection {
        final int id;
        final Socket socket;
        final DataOutputStream out;
        private volatile boolean joined; // read by the game thread too
        private boolean closed;

        Connection(int id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        void readLoop() {
            try {
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                while (open) {
                    Protocol.Message m = Protocol.read(in);
                    if (m.type == Protocol.HELLO && !joined) {
                        DataInputStream body = m.reader();
                        int version = body.readInt();
                        String name = body.readUTF();
                        if (version != Protocol.VERSION) {
                            send(Protocol.REJECT, Protocol.body(o -> o.writeUTF("THIS ROOM IS ON ANOTHER VERSION")));
                            break;
                        }
                        joined = true;
                        events.add(new Event(Event.Kind.JOINED, id, name));
                    } else if (m.type == Protocol.LEAVE) {
                        break;
                    } else if (m.type == Protocol.INPUT && joined) {
                        events.add(new Event(Event.Kind.INPUT, id, null, m.reader().readInt(), 0));
                    } else if (m.type == Protocol.PICK && joined) {
                        DataInputStream body = m.reader();
                        int action = body.readByte();
                        events.add(new Event(Event.Kind.PICK, id, null, action, body.readByte()));
                    }
                }
            } catch (IOException e) {
                // the phone went away
            }
            close(true);
        }

        void send(byte type, byte[] body) {
            synchronized (out) {
                try {
                    Protocol.write(out, type, body);
                } catch (IOException e) {
                    close(true);
                }
            }
        }

        synchronized void close(boolean report) {
            if (closed) return;
            closed = true;
            connections.remove(id);
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
            if (report && joined) events.add(new Event(Event.Kind.LEFT, id, null));
        }
    }
}
