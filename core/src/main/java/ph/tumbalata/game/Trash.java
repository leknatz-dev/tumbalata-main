package ph.tumbalata.game;

import com.badlogic.gdx.math.Vector2;

/**
 * One piece of street trash thrown onto the court (a street event). It goes WARNING (a red circle where it will
 * land) -> FLYING (an arc in from over the fence) -> LANDED (slippery until it fades away). Rules only, no drawing.
 */
public final class Trash {
    public static final float WARNING_SECONDS = 1.0f;
    public static final float FLIGHT_SECONDS = 0.6f;
    public static final float LIFETIME_SECONDS = 20f; // on the ground, then it is gone
    public static final float FADE_SECONDS = 1.0f;    // the last part of the lifetime, fading out
    public static final float ARC_HEIGHT = 140f;

    /** Placeholder kinds, drawn as different coloured shapes until there is art. */
    public static final int KINDS = 3;
    public static final String[] KIND_FILES = { "banana", "box", "apple" };

    public enum State { WARNING, FLYING, LANDED }

    public final Vector2 target = new Vector2();
    public final Vector2 from = new Vector2();
    public final int kind;
    public State state = State.WARNING;
    /** Seconds in the current state. */
    public float time = 0f;

    Trash(float fromX, float fromY, float targetX, float targetY, int kind) {
        this.from.set(fromX, fromY);
        this.target.set(targetX, targetY);
        this.kind = kind;
    }

    /** Lands it at once (for tests). */
    static Trash landed(float x, float y) {
        Trash t = new Trash(x, y, x, y, 0);
        t.state = State.LANDED;
        return t;
    }

    /** Advances the state. @return true on the frame it lands */
    boolean update(float delta) {
        time += delta;
        if (state == State.WARNING && time >= WARNING_SECONDS) {
            state = State.FLYING;
            time = 0f;
        } else if (state == State.FLYING && time >= FLIGHT_SECONDS) {
            state = State.LANDED;
            time = 0f;
            return true;
        }
        return false;
    }

    public boolean isLanded() {
        return state == State.LANDED;
    }

    boolean isExpired() {
        return state == State.LANDED && time >= LIFETIME_SECONDS;
    }

    /** 0..1 opacity: fades out at the end of its life. */
    public float alpha() {
        if (state != State.LANDED) return 1f;
        float left = LIFETIME_SECONDS - time;
        return left >= FADE_SECONDS ? 1f : Math.max(0f, left / FADE_SECONDS);
    }

    /** Flight progress 0..1 (1 once landed). */
    public float flightProgress() {
        if (state == State.WARNING) return 0f;
        if (state == State.LANDED) return 1f;
        return Math.min(1f, time / FLIGHT_SECONDS);
    }

    /** Where it is drawn: on the ground line between from and target, plus its height in the air. */
    public float x() {
        return from.x + (target.x - from.x) * flightProgress();
    }

    public float groundY() {
        return from.y + (target.y - from.y) * flightProgress();
    }

    public float height() {
        float p = flightProgress();
        return 4f * ARC_HEIGHT * p * (1f - p);
    }
}
