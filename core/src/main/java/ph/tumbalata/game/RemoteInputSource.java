package ph.tumbalata.game;

import com.badlogic.gdx.utils.IntArray;

/**
 * A player on another phone, on the host: the phone sends one {@link InputFrame} per match step, and each host step
 * takes the next one. If none has arrived yet the player keeps holding what they held (no new presses). If the frames
 * pile up (that phone ran ahead for a moment), the extra ones are folded into the next, keeping their button presses,
 * so the player never lags further behind than {@link #MAX_WAITING} steps and a quick tap is never lost. A player whose
 * phone dropped out just stands still.
 */
public final class RemoteInputSource implements InputSource {
    static final int MAX_WAITING = 3;

    private final IntArray frames = new IntArray(); // packed frames, oldest first
    private final InputFrame last = new InputFrame();
    private final InputFrame scratch = new InputFrame();
    private boolean gone;

    /** A frame from the phone (call on the game thread). */
    public void add(int packedFrame) {
        if (!gone) frames.add(packedFrame);
    }

    /** The phone dropped out: from now on the player stands still. */
    public void disconnect() {
        gone = true;
        frames.clear();
        last.clear();
    }

    @Override
    public void sample(int player, InputFrame out) {
        if (frames.size == 0) {
            out.set(last); // nothing new: keep holding
            return;
        }
        int buttons = 0;
        while (frames.size > MAX_WAITING) buttons |= scratch.unpack(frames.removeIndex(0)).buttons();
        out.unpack(frames.removeIndex(0));
        if (buttons != 0) out.set(out.moveX(), out.moveY(), out.buttons() | buttons);
        last.set(out);
    }
}
