package ph.tumbalata.game;

/**
 * Where a player's input comes from, one match step at a time: this device's keyboard and pads
 * ({@link LocalInputSource}), and later the touch controls and players on other phones. The match only sees the
 * {@link InputFrame}s.
 */
public interface InputSource {
    /** What {@code player} holds for the next match step. Called once per player per step. */
    void sample(int player, InputFrame out);
}
