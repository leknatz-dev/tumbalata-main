package ph.tumbalata.game;

/** What each player did during a match (index = player id). Counted by {@link Match}, used for {@link Awards}. */
public final class MatchStats {
    /** Times this player's slipper knocked the can down. */
    public final int[] knocks;
    /** Throwers this player tagged as Taya. */
    public final int[] tags;
    /** Slippers this player's tossed can landed on, as Taya. */
    public final int[] tossHits;
    /** Rounds this player made it home safe with their slipper. */
    public final int[] safeRuns;
    /** Times this player was caught (tagged, or their slipper hit by Taya's toss). */
    public final int[] caught;
    /** Times this player slipped on trash. */
    public final int[] slips;
    /** Times this player stepped in dog poop. */
    public final int[] poopSteps;

    public MatchStats(int players) {
        knocks = new int[players];
        tags = new int[players];
        tossHits = new int[players];
        safeRuns = new int[players];
        caught = new int[players];
        slips = new int[players];
        poopSteps = new int[players];
    }

    public int players() {
        return knocks.length;
    }
}
