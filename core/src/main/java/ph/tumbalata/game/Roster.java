package ph.tumbalata.game;

import com.badlogic.gdx.utils.IntArray;

/**
 * Who plays which role, by player id (0 = Player 1). One Taya guards the can; everyone else is a Thrower,
 * and the Throwers take turns. With 2 players there is one Thrower, so the turn never moves.
 *
 * <ul>
 *   <li>Start: the last player is Taya, the others throw in player order (P1 first).</li>
 *   <li>{@link #nextTurn()}: the active Thrower goes to the back of the line.</li>
 *   <li>{@link #swapWithTaya(int)}: a tagged / hit Thrower becomes Taya, and the old Taya takes their place in the
 *       line, so the old Taya throws next if it was the active Thrower's turn.</li>
 * </ul>
 * No rendering or GL code, so it can be unit tested.
 */
public class Roster {
    private final int playerCount;
    private final IntArray throwers; // turn order; index 0 = the active Thrower
    private int taya;

    public Roster(int playerCount) {
        if (playerCount < 2) throw new IllegalArgumentException("need at least 2 players, got " + playerCount);
        this.playerCount = playerCount;
        this.taya = playerCount - 1;
        this.throwers = new IntArray(playerCount - 1);
        for (int id = 0; id < playerCount - 1; id++) throwers.add(id);
    }

    public int playerCount() {
        return playerCount;
    }

    public int taya() {
        return taya;
    }

    /** The Thrower whose turn it is. */
    public int activeThrower() {
        return throwers.first();
    }

    public boolean isTaya(int id) {
        return id == taya;
    }

    /** Throwers in turn order (index 0 = active). Do not modify. */
    public IntArray throwers() {
        return throwers;
    }

    /** The active Thrower moves to the back of the line. */
    public void nextTurn() {
        if (throwers.size > 1) throwers.add(throwers.removeIndex(0));
    }

    /** {@code throwerId} becomes Taya; the old Taya takes their spot in the turn order. */
    public void swapWithTaya(int throwerId) {
        int index = throwers.indexOf(throwerId);
        if (index < 0) throw new IllegalArgumentException("player " + throwerId + " is not a Thrower");
        throwers.set(index, taya);
        taya = throwerId;
    }
}
