package ph.tumbalata.game;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;

import ph.tumbalata.game.net.ClientSession;
import ph.tumbalata.game.net.HostSession;
import ph.tumbalata.game.net.Protocol;

/**
 * This device's place on the network: hosting a room ({@link #host()}), in someone else's room ({@link #client()}),
 * or neither. One for the whole game ({@link TumbalataGame#network()}); {@link #update} runs every frame, before the
 * screen, whatever screen is up.
 *
 * <p><b>The host</b> keeps the real room and runs the match. Phones that join go into the {@link Lobby} (only in the
 * waiting room); every phone gets the room again whenever it changes; their character select moves are applied here
 * (the "each character once" rule is the lobby's); their input frames feed the match through a
 * {@link RemoteInputSource} each. The host's screens tell the phones where to go ({@link #hostGo}).
 *
 * <p><b>A phone</b> follows: it shows the screen the host sends it to, keeps a copy of the room, and queues the match
 * states for its match screen ({@link #nextState()}). If the host closes the room or the connection drops, it goes back
 * to the server screen.
 */
public final class Network {
    /** The screens the host moves everyone between. */
    public enum Go { WAITING_ROOM, CHARACTER_SELECT, MATCH, VICTORY }

    private HostSession host;
    private ClientSession client;
    private int myId = -1;

    // Host
    private final IntMap<RemoteInputSource> remoteInputs = new IntMap<>(); // by connection
    private final IntSet gone = new IntSet();  // phones that dropped out during a match (they leave the room after it)
    private byte[] lastLobby;                  // the room as the phones last got it

    // Phone
    private final ArrayDeque<byte[]> states = new ArrayDeque<>();
    private Screen pendingScreen;              // where the host sent us, opened as soon as no screen change is playing

    public HostSession host() { return host; }
    public ClientSession client() { return client; }
    public boolean isHosting() { return host != null; }
    public boolean isClient() { return client != null; }

    /** True when the match is played with other phones (hosting or in someone else's room). */
    public boolean isOnline() {
        return host != null || client != null;
    }

    /** In someone else's room: which player this phone is (from the host), or -1 before the room arrives. */
    public int myId() { return myId; }

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
        remoteInputs.clear();
        gone.clear();
        lastLobby = null;
        states.clear();
        pendingScreen = null;
    }

    // ------------------------------------------------------------------
    // Every frame
    // ------------------------------------------------------------------

    public void update(TumbalataGame game) {
        if (host != null) updateHost(game);
        if (client != null) updateClient(game);
        if (pendingScreen != null && !game.isTransitioning()) {
            game.changeScreen(pendingScreen);
            pendingScreen = null;
        }
    }

    private void updateHost(TumbalataGame game) {
        Lobby lobby = game.lobby();
        Screen screen = game.getScreen();
        boolean inRoom = screen instanceof WaitingRoomScreen;
        for (HostSession.Event e = host.poll(); e != null; e = host.poll()) {
            Lobby.Slot slot = lobby.slotForConnection(e.connection);
            switch (e.kind) {
                case JOINED:
                    if (!inRoom || game.isTransitioning()) {
                        host.reject(e.connection, "A MATCH IS ON. TRY AGAIN SOON");
                    } else if (lobby.joinRemote(e.connection, e.name) == null) {
                        host.reject(e.connection, "THE ROOM IS FULL");
                    } else {
                        game.audio().play(Audio.Sfx.UI_CONFIRM);
                    }
                    break;
                case LEFT:
                    if (slot == null) break;
                    if (inRoom) {
                        lobby.leave(slot.id());
                        game.audio().play(Audio.Sfx.UI_BACK);
                    } else if (screen instanceof CharacterSelectScreen) { // not enough players any more: back to the room
                        lobby.leave(slot.id());
                        hostGo(Go.WAITING_ROOM, new byte[0]);
                        game.changeScreen(new WaitingRoomScreen(game));
                    } else { // during the match: that player stands still; they leave the room after it
                        remoteInput(e.connection).disconnect();
                        gone.add(e.connection);
                    }
                    break;
                case PICK:
                    if (slot != null && screen instanceof CharacterSelectScreen) pick(game, slot, e.value, e.card);
                    break;
                case INPUT:
                    remoteInput(e.connection).add(e.value);
                    break;
            }
        }
        if (inRoom && gone.size > 0) { // phones that dropped out during the match leave now
            IntSet.IntSetIterator it = gone.iterator();
            while (it.hasNext) {
                Lobby.Slot s = lobby.slotForConnection(it.next());
                if (s != null) lobby.leave(s.id());
            }
            gone.clear();
        }
        sendLobbyIfChanged(lobby);
    }

    /** A phone's character select move, by the rules of the lobby. */
    private static void pick(TumbalataGame game, Lobby.Slot slot, int action, int card) {
        Lobby lobby = game.lobby();
        int id = slot.id();
        switch (action) {
            case Protocol.PICK_LEFT: lobby.choose(id, slot.character() - 1); break;
            case Protocol.PICK_RIGHT: lobby.choose(id, slot.character() + 1); break;
            case Protocol.PICK_CHOOSE: lobby.choose(id, card); break;
            case Protocol.PICK_LOCK:
                game.audio().play(lobby.lock(id) < 0 ? Audio.Sfx.UI_CONFIRM : Audio.Sfx.UI_DENY);
                return;
            case Protocol.PICK_UNLOCK: lobby.unlock(id); break;
            default: return;
        }
        game.audio().play(Audio.Sfx.UI_MOVE);
    }

    /** Host: the input of the player on this phone connection. */
    public RemoteInputSource remoteInput(int connection) {
        RemoteInputSource source = remoteInputs.get(connection);
        if (source == null) {
            source = new RemoteInputSource();
            remoteInputs.put(connection, source);
        }
        return source;
    }

    /** Host: every phone gets the room again when it changed (each told which player it is). */
    private void sendLobbyIfChanged(Lobby lobby) {
        if (lobby.size() == 0) return;
        host.setRoomInfo(lobby.slot(0).name(), lobby.size(), lobby.settings.roomSize);
        byte[] now = bytes(lobby::writeTo);
        if (Arrays.equals(now, lastLobby)) return;
        lastLobby = now;
        for (int i = 0; i < lobby.size(); i++) { // index loop: sending the room loops over the players too
            Lobby.Slot s = lobby.slot(i);
            if (s.connection() >= 0) host.sendLobby(s.connection(), lobby, s.id());
        }
    }

    /** Host: sends every phone in the room to this screen. */
    public void hostGo(Go where, byte[] body) {
        if (host != null) host.sendGo(where.ordinal(), body);
    }

    /** Host: the match after a step, to every phone. */
    public void hostSendState(byte[] stateAndEvents) {
        if (host != null) host.sendState(stateAndEvents);
    }

    private void updateClient(TumbalataGame game) {
        Lobby lobby = game.lobby();
        for (ClientSession.Event e = client.poll(); e != null; e = client.poll()) {
            try {
                switch (e.kind) {
                    case LOBBY:
                        myId = e.applyTo(lobby);
                        break;
                    case GO:
                        follow(game, Go.values()[e.screen()], e.body());
                        break;
                    case STATE:
                        states.add(e.body());
                        break;
                    case REJECTED:
                        leave(game, e.reason);
                        return;
                    case CLOSED:
                        leave(game, "THE HOST CLOSED THE ROOM");
                        return;
                }
            } catch (IOException | RuntimeException bad) {
                leave(game, "THE ROOM SENT SOMETHING ODD");
                return;
            }
        }
    }

    /** A phone: goes where the host went. */
    private void follow(TumbalataGame game, Go where, byte[] body) throws IOException {
        Lobby lobby = game.lobby();
        switch (where) {
            case WAITING_ROOM:
                if (!(game.getScreen() instanceof WaitingRoomScreen)) pendingScreen = new WaitingRoomScreen(game);
                break;
            case CHARACTER_SELECT:
                pendingScreen = new CharacterSelectScreen(game);
                break;
            case MATCH:
                states.clear();
                pendingScreen = new GameScreen(lobby, readLines(body), true);
                break;
            case VICTORY: {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
                int[] scores = new int[in.readByte()];
                for (int i = 0; i < scores.length; i++) scores[i] = in.readInt();
                Array<Awards.Award> awards = new Array<>();
                int count = in.readByte();
                for (int a = 0; a < count; a++) {
                    String title = in.readUTF(), detail = in.readUTF();
                    int[] winners = new int[in.readByte()];
                    for (int w = 0; w < winners.length; w++) winners[w] = in.readByte();
                    awards.add(new Awards.Award(title, detail, winners));
                }
                states.clear();
                pendingScreen = new VictoryScreen(game, scores, lobby.characters(), lobby.names(), awards);
                break;
            }
        }
    }

    private void leave(TumbalataGame game, String why) {
        close();
        game.lobby().clear();
        pendingScreen = new ServerScreen(game, why);
    }

    /** A phone: the next match state from the host (state, then the events of that step), or null. */
    public byte[] nextState() {
        return states.poll();
    }

    /** A phone: what its player holds for one match step. */
    public void sendInput(InputFrame frame) {
        if (client != null) client.sendInput(frame.pack());
    }

    /** A phone: a character select move ({@code Protocol.PICK_*}). */
    public void sendPick(int action, int card) {
        if (client != null) client.sendPick(action, card);
    }

    // ------------------------------------------------------------------
    // What the screens send
    // ------------------------------------------------------------------

    /** The intro dialogue the host picked, for {@link Go#MATCH}. */
    public static byte[] writeLines(Array<DialogueScript.Line> lines) {
        return bytes(out -> {
            out.writeByte(lines.size);
            for (int i = 0; i < lines.size; i++) {
                out.writeByte(lines.get(i).speaker);
                out.writeUTF(lines.get(i).text);
            }
        });
    }

    static Array<DialogueScript.Line> readLines(byte[] body) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        Array<DialogueScript.Line> lines = new Array<>();
        int count = in.readByte();
        for (int i = 0; i < count; i++) {
            int speaker = in.readByte();
            lines.add(new DialogueScript.Line(speaker, in.readUTF()));
        }
        return lines;
    }

    /** The scores and awards, for {@link Go#VICTORY}. */
    public static byte[] writeVictory(int[] scores, Array<Awards.Award> awards) {
        return bytes(out -> {
            out.writeByte(scores.length);
            for (int s : scores) out.writeInt(s);
            out.writeByte(awards.size);
            for (int a = 0; a < awards.size; a++) {
                Awards.Award award = awards.get(a);
                out.writeUTF(award.title);
                out.writeUTF(award.detail);
                out.writeByte(award.winners.length);
                for (int w : award.winners) out.writeByte(w);
            }
        });
    }

    interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    static byte[] bytes(Writer writer) {
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
