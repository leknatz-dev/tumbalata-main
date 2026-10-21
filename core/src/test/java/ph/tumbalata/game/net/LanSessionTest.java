package ph.tumbalata.game.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import ph.tumbalata.game.Lobby;

/** A host and phones on this computer's own network (localhost). */
class LanSessionTest {
    private static final InetAddress HERE = InetAddress.getLoopbackAddress();

    /** Waits up to 3 seconds for something to arrive. */
    private static <T> T await(Supplier<T> poll) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            T t = poll.get();
            if (t != null) return t;
            Thread.sleep(10);
        }
        throw new AssertionError("nothing arrived");
    }

    @Test
    void theRoomTravelsToThePhonesAsTheHostHasIt() throws Exception {
        Lobby host = new Lobby();
        host.join(0);
        host.setName(0, "maya");
        host.settings.roomSize = 3;
        host.settings.matchSeconds = 120f;
        host.settings.streetEvents = false;
        host.joinRemote(7, "jojo");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        host.writeTo(new DataOutputStream(bytes));
        Lobby copy = new Lobby();
        copy.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(java.util.Arrays.toString(host.names()), java.util.Arrays.toString(copy.names()));
        assertEquals(3, copy.settings.roomSize);
        assertEquals(120f, copy.settings.matchSeconds);
        assertEquals(false, copy.settings.streetEvents);
        assertTrue(copy.slot(0).isHost());
    }

    @Test
    void aPhoneJoinsGetsTheRoomAndLeaves() throws Exception {
        try (HostSession hostSession = new HostSession(0, -1, "MAYA'S ROOM")) {
            Lobby lobby = new Lobby();
            lobby.join(0);
            lobby.setName(0, "maya");

            ClientSession phone = ClientSession.connect(HERE, hostSession.port(), "Jojo", 2000);
            HostSession.Event joined = await(hostSession::poll);
            assertSame(HostSession.Event.Kind.JOINED, joined.kind);
            assertEquals("Jojo", joined.name);

            Lobby.Slot slot = lobby.joinRemote(joined.connection, joined.name);
            hostSession.sendLobby(joined.connection, lobby, slot.id());
            ClientSession.Event got = await(phone::poll);
            assertSame(ClientSession.Event.Kind.LOBBY, got.kind);
            Lobby copy = new Lobby();
            assertEquals(1, got.applyTo(copy), "this phone is P2");
            assertEquals("JOJO", copy.slot(1).name());
            assertEquals("MAYA", copy.slot(0).name());

            phone.leave();
            HostSession.Event left = await(hostSession::poll);
            assertSame(HostSession.Event.Kind.LEFT, left.kind);
            assertEquals(joined.connection, left.connection);
        }
    }

    @Test
    void aFullRoomTurnsAPhoneAway() throws Exception {
        try (HostSession hostSession = new HostSession(0, -1, "ROOM")) {
            ClientSession phone = ClientSession.connect(HERE, hostSession.port(), "Late", 2000);
            HostSession.Event joined = await(hostSession::poll);
            hostSession.reject(joined.connection, "THE ROOM IS FULL");
            ClientSession.Event got = await(phone::poll);
            assertSame(ClientSession.Event.Kind.REJECTED, got.kind);
            assertEquals("THE ROOM IS FULL", got.reason);
        }
    }

    @Test
    void phonesHearWhenTheHostClosesTheRoom() throws Exception {
        HostSession hostSession = new HostSession(0, -1, "ROOM");
        ClientSession phone = ClientSession.connect(HERE, hostSession.port(), "Kim", 2000);
        await(hostSession::poll);
        hostSession.close();
        ClientSession.Event got = await(phone::poll);
        assertSame(ClientSession.Event.Kind.CLOSED, got.kind);
    }

    @Test
    void roomSearchesFindTheHost() throws Exception {
        int discoveryPort = 54000 + (int) (Math.random() * 900); // not the real one, in case a game is running
        try (HostSession hostSession = new HostSession(0, discoveryPort, "MAYA'S ROOM")) {
            hostSession.setRoomInfo("MAYA'S ROOM", 2, 4);
            List<LanDiscovery.Room> rooms = LanDiscovery.search(discoveryPort, 500);
            LanDiscovery.Room room = rooms.stream().filter(r -> r.port == hostSession.port()).findFirst().orElse(null);
            assertNotNull(room, "found: " + rooms);
            assertEquals("MAYA'S ROOM", room.name);
            assertEquals(2, room.players);
            assertEquals(4, room.roomSize);
        }
    }

    @Test
    void answersFromOtherVersionsAreIgnored() {
        assertEquals(null, LanDiscovery.parseReply("TUMBALATA!|999|54555|1|4|OLD ROOM", HERE));
        assertEquals(null, LanDiscovery.parseReply("SOMETHING ELSE", HERE));
        assertNotNull(LanDiscovery.parseReply(new String(LanDiscovery.reply(54555, 1, 4, "A|B")), HERE), "a | in the name");
    }
}
