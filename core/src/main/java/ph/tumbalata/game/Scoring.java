package ph.tumbalata.game;

/** Points per event. Tweak here; {@link Match} awards them and MatchTest checks the rules (not these values). */
public final class Scoring {
    private Scoring() {}

    /** Thrower whose slipper knocks the can down. */
    public static final int KNOCK_CAN = 3;
    /** Each Thrower who threw and made it home with their slipper when a round ends normally. */
    public static final int HOME_SAFE = 1;
    /** Taya tags a Thrower. */
    public static final int TAG = 2;
    /** Taya's tossed can lands on a slipper. */
    public static final int TOSS_HIT = 2;
}
