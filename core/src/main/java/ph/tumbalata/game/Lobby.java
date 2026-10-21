package ph.tumbalata.game;

import com.badlogic.gdx.utils.Array;

/**
 * The players getting ready for a match: one {@link Slot} per player (name, character, locked in, where their input
 * comes from) and the host's match {@link Settings}. The menus fill it in, the match is made from it, and after a
 * match the players come back to it. No drawing, so the host can keep the real one and the phones copies.
 *
 * <p>The rules for picking live here: each character can be locked in by one player only.
 */
public final class Lobby {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = InputManager.MAX_PLAYERS;

    /** One player in the lobby. */
    public static final class Slot {
        /** Player number: 0 = Player 1. Stays the same for the whole match (scores, colours before the pick). */
        public final int id;
        private String name = "";
        private int character;
        private boolean locked;
        private int localInput;

        Slot(int id) {
            this.id = id;
            this.character = id % Characters.COUNT; // everyone starts on a different card
            this.localInput = id;
        }

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

        /** Length of a match. */
        public float matchSeconds = DEFAULT_MATCH_SECONDS;
        /** Random street events: trash thrown onto the court, the stray dog. */
        public boolean streetEvents = true;
        /** Each character's strengths (speed, throw, aim, reach). */
        public boolean characterTraits = true;
    }

    private final Array<Slot> slots = new Array<>();
    public final Settings settings = new Settings();

    /** Starts over with {@code players} new players (2 to 4), names blank, nobody locked in. Settings stay. */
    public void reset(int players) {
        if (players < MIN_PLAYERS || players > MAX_PLAYERS) {
            throw new IllegalArgumentException(MIN_PLAYERS + " to " + MAX_PLAYERS + " players, got " + players);
        }
        slots.clear();
        for (int id = 0; id < players; id++) slots.add(new Slot(id));
    }

    public int size() { return slots.size; }
    public Slot slot(int id) { return slots.get(id); }
    public Array<Slot> slots() { return slots; }

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
