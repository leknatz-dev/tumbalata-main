package ph.tumbalata.game;

import com.badlogic.gdx.math.Vector2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What happened in the match (a score, a throw, a knock, a tag, a new stage...) in a form that travels. On the host,
 * the recorders write down each event as it happens and pass it on to the host's own screen; the log goes to the
 * phones with the state after each step ({@link #take()}), and {@link #play} hands the same events to a phone's
 * screen, so everyone hears the same sounds and sees the same signs.
 */
public final class MatchEventLog {
    // Match.Events
    private static final byte SCORED = 1, SLIPPER_THROWN = 2, CAN_TOSSED = 3, TAYA_TOSS_TURN = 4, TOSS_MISSED = 5,
        CAN_KNOCKED = 6, TAGGED = 7, TOSS_HIT_SLIPPER = 8, ROUND_ENDED = 9, TRASH_INCOMING = 10, TRASH_LANDED = 11,
        SLIPPED = 12, DOG_ARRIVED = 13, DOG_POOPED = 14, STEPPED_IN_POOP = 15, THROWER_TURN = 16, CAN_SPOT_CHOSEN = 17;
    // MatchFlow.Listener
    private static final byte STAGE_STARTED = 30, MANO_CHANGED = 31, GAME_START_SHOWN = 32;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);

    /** The events written down since the last take, and starts a new log. */
    public byte[] take() {
        byte[] all = bytes.toByteArray();
        bytes.reset();
        return all;
    }

    private void write(int code, Object... args) {
        try {
            out.writeByte(code);
            for (Object a : args) {
                if (a instanceof Player) out.writeByte(((Player) a).id);
                else if (a instanceof Integer) out.writeInt((Integer) a);
                else if (a instanceof Trash) writeTrash((Trash) a);
                else if (a instanceof StrayDog) writeDog((StrayDog) a);
                else if (a instanceof Vector2) {
                    out.writeFloat(((Vector2) a).x);
                    out.writeFloat(((Vector2) a).y);
                } else if (a instanceof Enum) out.writeByte(((Enum<?>) a).ordinal());
                else throw new IllegalArgumentException("can't send " + a);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // writing to memory doesn't fail
        }
    }

    private void writeTrash(Trash t) throws IOException {
        out.writeByte(t.kind);
        out.writeFloat(t.from.x);
        out.writeFloat(t.from.y);
        out.writeFloat(t.target.x);
        out.writeFloat(t.target.y);
    }

    private void writeDog(StrayDog d) throws IOException {
        out.writeFloat(d.spot.x);
        out.writeFloat(d.spot.y);
        out.writeBoolean(d.direction > 0);
    }

    /** Writes down each match event, then passes it on to {@code to}. */
    public Match.Events recording(Match.Events to) {
        return new Match.Events() {
            @Override public void scored(Player p, int points) { write(SCORED, p, points); to.scored(p, points); }
            @Override public void slipperThrown(Player p) { write(SLIPPER_THROWN, p); to.slipperThrown(p); }
            @Override public void canTossed(Player p) { write(CAN_TOSSED, p); to.canTossed(p); }
            @Override public void tayaTossTurn(Player p) { write(TAYA_TOSS_TURN, p); to.tayaTossTurn(p); }
            @Override public void tossMissed(Player p) { write(TOSS_MISSED, p); to.tossMissed(p); }
            @Override public void canKnocked(Player p) { write(CAN_KNOCKED, p); to.canKnocked(p); }
            @Override public void tagged(Player t, Player v) { write(TAGGED, t, v); to.tagged(t, v); }
            @Override public void tossHitSlipper(Player t, Player v) { write(TOSS_HIT_SLIPPER, t, v); to.tossHitSlipper(t, v); }
            @Override public void roundEnded() { write(ROUND_ENDED); to.roundEnded(); }
            @Override public void trashIncoming(Trash t) { write(TRASH_INCOMING, t); to.trashIncoming(t); }
            @Override public void trashLanded(Trash t) { write(TRASH_LANDED, t); to.trashLanded(t); }
            @Override public void slipped(Player p, Trash t) { write(SLIPPED, p, t); to.slipped(p, t); }
            @Override public void dogArrived(StrayDog d) { write(DOG_ARRIVED, d); to.dogArrived(d); }
            @Override public void dogPooped(StrayDog d) { write(DOG_POOPED, d); to.dogPooped(d); }
            @Override public void steppedInPoop(Player p) { write(STEPPED_IN_POOP, p); to.steppedInPoop(p); }
            @Override public void throwerTurn(Player p) { write(THROWER_TURN, p); to.throwerTurn(p); }
            @Override public void canSpotChosen(Vector2 spot) { write(CAN_SPOT_CHOSEN, spot); to.canSpotChosen(spot); }
        };
    }

    /** Writes down each flow event, then passes it on to {@code to}. */
    public MatchFlow.Listener recording(MatchFlow.Listener to) {
        return new MatchFlow.Listener() {
            @Override public void stageStarted(MatchFlow.Stage stage) { write(STAGE_STARTED, stage); to.stageStarted(stage); }
            @Override public void manoChanged(Mano.Phase phase) { write(MANO_CHANGED, phase); to.manoChanged(phase); }
            @Override public void gameStartShown() { write(GAME_START_SHOWN); to.gameStartShown(); }
        };
    }

    /** A phone: plays the host's events to this screen, in order. Players are this phone's copies in {@code match}. */
    public static void play(byte[] log, Match match, Match.Events events, MatchFlow.Listener listener) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(log));
        while (in.available() > 0) {
            byte code = in.readByte();
            switch (code) {
                case SCORED: { Player p = player(in, match); events.scored(p, in.readInt()); break; }
                case SLIPPER_THROWN: events.slipperThrown(player(in, match)); break;
                case CAN_TOSSED: events.canTossed(player(in, match)); break;
                case TAYA_TOSS_TURN: events.tayaTossTurn(player(in, match)); break;
                case TOSS_MISSED: events.tossMissed(player(in, match)); break;
                case CAN_KNOCKED: events.canKnocked(player(in, match)); break;
                case TAGGED: { Player t = player(in, match); events.tagged(t, player(in, match)); break; }
                case TOSS_HIT_SLIPPER: { Player t = player(in, match); events.tossHitSlipper(t, player(in, match)); break; }
                case ROUND_ENDED: events.roundEnded(); break;
                case TRASH_INCOMING: events.trashIncoming(trash(in)); break;
                case TRASH_LANDED: events.trashLanded(trash(in)); break;
                case SLIPPED: { Player p = player(in, match); events.slipped(p, trash(in)); break; }
                case DOG_ARRIVED: events.dogArrived(dog(in)); break;
                case DOG_POOPED: events.dogPooped(dog(in)); break;
                case STEPPED_IN_POOP: events.steppedInPoop(player(in, match)); break;
                case THROWER_TURN: events.throwerTurn(player(in, match)); break;
                case CAN_SPOT_CHOSEN: events.canSpotChosen(new Vector2(in.readFloat(), in.readFloat())); break;
                case STAGE_STARTED: listener.stageStarted(MatchFlow.Stage.values()[in.readByte()]); break;
                case MANO_CHANGED: listener.manoChanged(Mano.Phase.values()[in.readByte()]); break;
                case GAME_START_SHOWN: listener.gameStartShown(); break;
                default: throw new IOException("unknown event " + code);
            }
        }
    }

    private static Player player(DataInputStream in, Match match) throws IOException {
        return match.players().get(in.readByte());
    }

    private static Trash trash(DataInputStream in) throws IOException {
        int kind = in.readByte();
        return new Trash(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), kind);
    }

    private static StrayDog dog(DataInputStream in) throws IOException {
        float x = in.readFloat(), y = in.readFloat();
        return new StrayDog(x, y, in.readBoolean());
    }
}
