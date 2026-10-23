package ph.tumbalata.game.net;

import ph.tumbalata.game.GameVersion;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding rooms on the same Wi-Fi: {@link #search} asks everyone on the network (a UDP broadcast) and collects the
 * hosts that answer. The answering side is in {@link HostSession}. {@link Searcher} keeps searching in the background
 * so a screen can show the list as it changes.
 */
public final class LanDiscovery {
    /** A room found on the network. */
    public static final class Room {
        public final String name;
        public final InetAddress address;
        public final int port;
        public final int players;
        public final int roomSize;
        /** The host's game build, and its name (like 0.1.0). */
        public final int build;
        public final String versionName;

        Room(String name, InetAddress address, int port, int players, int roomSize, int build, String versionName) {
            this.build = build;
            this.versionName = versionName;
            this.name = name;
            this.address = address;
            this.port = port;
            this.players = players;
            this.roomSize = roomSize;
        }

        public boolean isFull() {
            return players >= roomSize;
        }

        /** On the same build as this game: only then can we join. */
        public boolean sameVersion() {
            return build == Protocol.VERSION;
        }

        @Override
        public String toString() {
            return name + " " + players + "/" + roomSize + " @" + address.getHostAddress() + ":" + port;
        }
    }

    private LanDiscovery() {}

    /**
     * The host's answer to a probe: build, TCP port, players, room size, version name, room name. Keep this layout in
     * every build, so a game on another build can still list the room and say who has to update.
     */
    static byte[] reply(int tcpPort, int players, int roomSize, String roomName) {
        String text = Protocol.REPLY + "|" + Protocol.VERSION + "|" + tcpPort + "|" + players + "|" + roomSize + "|"
            + GameVersion.NAME + "|" + roomName;
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Reads a host's answer (on any build), or null if it isn't one. */
    static Room parseReply(String text, InetAddress from) {
        String[] parts = text.split("\\|", 7);
        if (parts.length < 7 || !parts[0].equals(Protocol.REPLY)) return null;
        try {
            return new Room(parts[6], from, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                Integer.parseInt(parts[1]), parts[5]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Asks the network for rooms and waits {@code timeoutMs} for answers (blocks). The probe goes to every network's
     * broadcast address and to this device itself (a host on the same computer).
     */
    public static List<Room> search(int discoveryPort, int timeoutMs) throws IOException {
        Map<String, Room> found = new LinkedHashMap<>(); // one entry per room, even if it answers on two networks
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] probe = Protocol.PROBE.getBytes(StandardCharsets.UTF_8);
            for (InetAddress target : targets()) {
                try {
                    socket.send(new DatagramPacket(probe, probe.length, target, discoveryPort));
                } catch (IOException ignored) {
                    // a network that can't broadcast: the others still can
                }
            }
            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buffer = new byte[512];
            while (true) {
                int left = (int) (end - System.currentTimeMillis());
                if (left <= 0) break;
                socket.setSoTimeout(left);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                Room room = parseReply(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8),
                    packet.getAddress());
                if (room == null) continue;
                String key = room.name + "|" + room.port; // a host on this computer answers as itself and on the Wi-Fi
                Room seen = found.get(key);
                if (seen == null || seen.address.isLoopbackAddress()) found.put(key, room); // keep the Wi-Fi address
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Where to send the probe: each network's broadcast address, the general one, and this device. */
    private static List<InetAddress> targets() {
        List<InetAddress> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : all == null ? Collections.<NetworkInterface>emptyList() : Collections.list(all)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                    if (a.getBroadcast() != null && !out.contains(a.getBroadcast())) out.add(a.getBroadcast());
                }
            }
        } catch (IOException ignored) {
            // fall back to the general broadcast address below
        }
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
        } catch (IOException ignored) {
            // can't happen for a literal address
        }
        out.add(InetAddress.getLoopbackAddress());
        return out;
    }

    /**
     * This device's address on the Wi-Fi (like 192.168.1.17), for others to join by address when the room search
     * doesn't reach them, or null if there is none.
     */
    public static String localAddress() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : all == null ? Collections.<NetworkInterface>emptyList() : Collections.list(all)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                    InetAddress ip = a.getAddress();
                    if (!(ip instanceof java.net.Inet4Address) || !ip.isSiteLocalAddress()) continue;
                    String text = ip.getHostAddress();
                    if (text.startsWith("192.168.")) return text; // home Wi-Fi: the likeliest one
                    if (fallback == null) fallback = text;
                }
            }
        } catch (IOException ignored) {
            // no network
        }
        return fallback;
    }

    /** Keeps searching in the background; {@link #rooms()} is the latest list. Stop it with {@link #stop()}. */
    public static final class Searcher {
        private volatile List<Room> rooms = Collections.emptyList();
        private volatile boolean running = true;
        private final Thread thread;

        public Searcher(int discoveryPort) {
            thread = new Thread(() -> {
                while (running) {
                    try {
                        rooms = Collections.unmodifiableList(search(discoveryPort, 700));
                    } catch (IOException e) {
                        rooms = Collections.emptyList();
                    }
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "lan-search");
            thread.setDaemon(true);
            thread.start();
        }

        public List<Room> rooms() {
            return rooms;
        }

        public void stop() {
            running = false;
            thread.interrupt();
        }
    }
}
