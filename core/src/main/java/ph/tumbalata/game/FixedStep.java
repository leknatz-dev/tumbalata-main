package ph.tumbalata.game;

/**
 * Runs the match in fixed steps of {@link #STEP} seconds, however fast the screen draws, so a throw lands the same on
 * a 30 fps phone, a 120 Hz phone and the host of a network game. Each frame, {@link #advance} says how many steps to
 * run; leftover time carries over to the next frame.
 */
public final class FixedStep {
    /** Seconds per simulation step (60 steps per second). */
    public static final float STEP = 1f / 60f;
    /** After a long stall (loading, the app in the background), catch up at most this many steps, then drop the rest. */
    static final int MAX_STEPS_PER_FRAME = 5;

    private float leftover = 0f;

    /** Adds this frame's time and returns how many steps to run now (0 when the frame was shorter than a step). */
    public int advance(float delta) {
        leftover += delta;
        int steps = (int) (leftover / STEP);
        if (steps > MAX_STEPS_PER_FRAME) {
            leftover = 0f; // too far behind: skip ahead instead of running in slow motion
            return MAX_STEPS_PER_FRAME;
        }
        leftover -= steps * STEP;
        return steps;
    }

    /** Forget leftover time (when the match starts or comes back from a pause). */
    public void reset() {
        leftover = 0f;
    }

    /** 0..1: how far between the last step and the next one this frame is (for smooth drawing later). */
    public float alpha() {
        return leftover / STEP;
    }
}
