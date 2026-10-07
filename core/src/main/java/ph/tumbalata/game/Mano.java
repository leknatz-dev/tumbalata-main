package ph.tumbalata.game;

import java.util.Random;

/**
 * The mano ("maiba taya") that picks the first Taya before the match. Rules only, no drawing.
 *
 * <p>While the countdown runs, every player chooses palm up (A) or palm down (B); a player who presses nothing shows
 * palm down. Then all hands flip: the one hand that is different from all the others is Taya (3/1 with 4 players,
 * 2/1 with 3). No odd one out (2/2, all the same) means AGAIN. With 2 players there is never an odd one out, so
 * different hands make the palm-up player Taya, and the same hands go again. After {@link #MAX_REDOS} redos a Taya
 * is picked at random, so the game can never get stuck.
 */
public final class Mano {
    public static final float CHOOSE_SECONDS = 3f;   // countdown while the hands bob
    public static final float REVEAL_SECONDS = 1.6f; // hands shown flipped (and "AGAIN!" or the Taya highlighted)
    public static final int MAX_REDOS = 5;

    public enum Phase { CHOOSING, REVEAL, DONE }

    private final boolean[] palmUp;
    private final Random rng;
    private Phase phase = Phase.CHOOSING;
    private float time = 0f;
    private int result = -1; // the Taya after a reveal, or -1 for "again"
    private int redos = 0;

    public Mano(int players, Random rng) {
        if (players < 2) throw new IllegalArgumentException("need at least 2 players");
        this.palmUp = new boolean[players];
        this.rng = rng;
    }

    public int players() { return palmUp.length; }
    public Phase phase() { return phase; }
    public float time() { return time; }

    /** Countdown seconds left while choosing (3.0 ... 0). */
    public float secondsLeft() {
        return phase == Phase.CHOOSING ? Math.max(0f, CHOOSE_SECONDS - time) : 0f;
    }

    /** What this player's hand shows. Only meaningful once revealed (during choosing it is the secret choice). */
    public boolean isPalmUp(int player) {
        return palmUp[player];
    }

    /** True while the flipped hands are shown and nobody was the odd one out. */
    public boolean isAgain() {
        return phase == Phase.REVEAL && result < 0;
    }

    /** The picked Taya: shown during the reveal, final once DONE. -1 if none (yet). */
    public int taya() {
        return result;
    }

    /** A player's choice; only counts while the countdown runs. */
    public void choose(int player, boolean up) {
        if (phase == Phase.CHOOSING) palmUp[player] = up;
    }

    /**
     * Advances the mano.
     * @return what happened this frame: REVEAL on the flip, DONE when the Taya is final, CHOOSING when a new round
     *         starts after "again", or null
     */
    public Phase update(float delta) {
        if (phase == Phase.DONE) return null;
        time += delta;
        if (phase == Phase.CHOOSING) {
            if (time < CHOOSE_SECONDS) return null;
            result = oddOneOut(palmUp);
            if (result < 0 && redos >= MAX_REDOS) result = rng.nextInt(palmUp.length); // never get stuck
            phase = Phase.REVEAL;
            time = 0f;
            return Phase.REVEAL;
        }
        if (time < REVEAL_SECONDS) return null;
        if (result >= 0) {
            phase = Phase.DONE;
            return Phase.DONE;
        }
        redos++;
        java.util.Arrays.fill(palmUp, false);
        phase = Phase.CHOOSING;
        time = 0f;
        return Phase.CHOOSING;
    }

    /**
     * The player whose hand differs from all the others, or -1. With 2 players: the palm-up one when the hands differ.
     */
    static int oddOneOut(boolean[] up) {
        int n = up.length, ups = 0, lastUp = -1, lastDown = -1;
        for (int i = 0; i < n; i++) {
            if (up[i]) { ups++; lastUp = i; } else { lastDown = i; }
        }
        if (n == 2) return ups == 1 ? lastUp : -1;
        if (ups == 1) return lastUp;
        if (ups == n - 1) return lastDown;
        return -1;
    }
}
