package ph.tumbalata.game;

import com.badlogic.gdx.utils.Array;

/**
 * The waiting room: the players in it, one {@link Slot} each (name, character, locked in, where their input comes
 * from), and the host's match {@link Settings}. Players join and leave; when the room is full the host can start.
 * The match is made from it, and after a match the players come back to it. No drawing, so the host can keep the real
 * one and the phones copies.
 *
 * <p>Player numbers are always 0..size-1 in join order (the host is 0): when someone leaves, the ones after them move
 * up. The rules for picking live here too: each character can be locked in by one player only.
 */
public final class Lobby {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = InputManager.MAX_PLAYERS;

    /** One player in the lobby. */
    public static final class Slot {
        private int id;
        private String name = "";
        private int character;
        private boolean locked;
        private final int localInput;

        Slot(int id, int localInput) {
            this.id = id;
            this.character = id % Characters.COUNT; // everyone starts on a different card
            this.localInput = localInput;
        }

        /** Player number: 0 = Player 1 (the host). Fixed for a match (scores, colours before the pick). */
        public int id() { return id; }

        /** True for the player who made the room and sets it up. */
        public boolean isHost() { return id == 0; }

        /** The name as typed ("" if none). */
        public String typedName() { return name; }

        /** The name to show everywhere: the typed one, or "P1"... */
        public String name() {
            return name.isEmpty() ? GameSettings.defaultName(id) : name;
        }

        /** The character this player is on (and has picked, once {@link #isLocked()}). */
        public int character() { return character; }

        /** True once this player has locked in their character. */
        public boolean isLocked() { return locked; }

        /** Which input slot on this device (keyboard scheme / pad) plays as this player. */
        public int localInput() { return localInput; }
    }

    /** What the host sets for the match. */
    public static final class Settings {
        public static final float DEFAULT_MATCH_SECONDS = 360f;
        /** The match lengths the host can choose from, in seconds. */
        public static final float[] MATCH_TIMES = { 60f, 120f, 180f, 240f, 300f, 360f, 480f, 600f };

        /** How many players the room waits for (2 to 4); the match can start when it is full. */
        public int roomSize = MAX_PLAYERS;
        /** Length of a match. */
        public float matchSeconds = DEFAULT_MATCH_SECONDS;
        /** Random street events: trash thrown onto the court, the stray dog. */
        public boolean streetEvents = true;
        /** Each character's strengths (speed, throw, aim, reach). */
        public boolean characterTraits = true;

        /** The next longer ({@code step} 1) or shorter (-1) match time in {@link #MATCH_TIMES}, stopping at the ends. */
        public void stepMatchTime(int step) {
            int at = 0;
            for (int i = 0; i < MATCH_TIMES.length; i++) {
                if (Math.abs(MATCH_TIMES[i] - matchSeconds) < Math.abs(MATCH_TIMES[at] - matchSeconds)) at = i;
            }
            matchSeconds = MATCH_TIMES[Math.max(0, Math.min(MATCH_TIMES.length - 1, at + step))];
        }
    }

    private final Array<Slot> slots = new Array<>();
    public final Settings settings = new Settings();

    /** Empties the room. Settings stay. */
    public void clear() {
        slots.clear();
    }

    /** Starts over with {@code players} players (2 to 4) on this device's input slots 0.., names blank. */
    public void reset(int players) {
        if (players < MIN_PLAYERS || players > MAX_PLAYERS) {
            throw new IllegalArgumentException(MIN_PLAYERS + " to " + MAX_PLAYERS + " players, got " + players);
        }
        clear();
        settings.roomSize = players;
        for (int i = 0; i < players; i++) join(i);
    }

    /**
     * A player joins, playing with this device's input slot {@code localInput}.
     * @return their slot, or null if the room is full or that input already plays
     */
    public Slot join(int localInput) {
        if (isFull() || slotFor(localInput) != null) return null;
        Slot s = new Slot(slots.size, localInput);
        slots.add(s);
        return s;
    }

    /** A player leaves; everyone after them moves up a number. */
    public void leave(int id) {
        slots.removeIndex(id);
        for (int i = 0; i < slots.size; i++) slots.get(i).id = i;
    }

    /** The player using this device's input slot, or null. */
    public Slot slotFor(int localInput) {
        for (Slot s : slots) if (s.localInput == localInput) return s;
        return null;
    }

    public int size() { return slots.size; }
    public Slot slot(int id) { return slots.get(id); }
    public Array<Slot> slots() { return slots; }

    /** The room has as many players as the host waits for. */
    public boolean isFull() {
        return slots.size >= settings.roomSize;
    }

    /** Room size: 2 to 4, and never below the players already in. Returns the size it got. */
    public int setRoomSize(int size) {
        settings.roomSize = Math.max(Math.max(MIN_PLAYERS, slots.size), Math.min(MAX_PLAYERS, size));
        return settings.roomSize;
    }

    /** Sets a player's name: trimmed, upper case, at most {@link GameSettings#MAX_NAME_LENGTH} characters. */
    public void setName(int id, String name) {
        String n = name == null ? "" : name.trim().toUpperCase();
        if (n.length() > GameSettings.MAX_NAME_LENGTH) n = n.substring(0, GameSettings.MAX_NAME_LENGTH).trim();
        slots.get(id).name = n;
    }

    /** Moves a player's choice to another character card (only while not locked in). */
    public void choose(int id, int character) {
        Slot s = slots.get(id);
        if (s.locked) return;
        s.character = Math.floorMod(character, Characters.COUNT);
    }

    /** The player who has locked in this character, or -1. */
    public int lockedBy(int character) {
        for (Slot s : slots) if (s.locked && s.character == character) return s.id;
        return -1;
    }

    /**
     * Locks the player in on the character they are on.
     * @return -1 if it worked, or the id of the player who already has that character
     */
    public int lock(int id) {
        Slot s = slots.get(id);
        int owner = lockedBy(s.character);
        if (owner >= 0 && owner != id) return owner;
        s.locked = true;
        return -1;
    }

    public void unlock(int id) {
        slots.get(id).locked = false;
    }

    /** Back from a match: everyone picks again (starting on the character they had). */
    public void unlockAll() {
        for (Slot s : slots) s.locked = false;
    }

    public boolean allLocked() {
        if (slots.size < MIN_PLAYERS) return false;
        for (Slot s : slots) if (!s.locked) return false;
        return true;
    }

    /** Each player's character, by player id. */
    public int[] characters() {
        int[] out = new int[slots.size];
        for (int i = 0; i < out.length; i++) out[i] = slots.get(i).character;
        return out;
    }

    /** Each player's name to show, by player id. */
    public String[] names() {
        String[] out = new String[slots.size];
        for (int i = 0; i < out.length; i++) out[i] = slots.get(i).name();
        return out;
    }
}
