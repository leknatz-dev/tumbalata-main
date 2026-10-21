package ph.tumbalata.game;

import ph.tumbalata.game.net.ClientSession;
import ph.tumbalata.game.net.HostSession;

/**
 * This device's place on the network: hosting a room ({@link #host()}), in someone else's room ({@link #client()}),
 * or neither. One for the whole game ({@link TumbalataGame#network()}), so the room stays open across screens.
 */
public final class Network {
    private HostSession host;
    private ClientSession client;
    private int myId = -1;

    public HostSession host() { return host; }
    public ClientSession client() { return client; }
    public boolean isHosting() { return host != null; }
    public boolean isClient() { return client != null; }

    /** In someone else's room: which player this phone is (from the host), or -1 before the room arrives. */
    public int myId() { return myId; }

    void setMyId(int id) { myId = id; }

    /** Now hosting this room (closes any other session). */
    public void startHosting(HostSession session) {
        close();
        host = session;
    }

    /** Now in someone else's room (closes any other session). */
    public void joined(ClientSession session) {
        close();
        client = session;
    }

    /** Leaves or closes the room, whichever this device is doing. */
    public void close() {
        if (host != null) host.close();
        if (client != null) client.leave();
        host = null;
        client = null;
        myId = -1;
    }
}
