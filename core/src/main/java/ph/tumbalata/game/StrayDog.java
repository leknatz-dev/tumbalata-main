package ph.tumbalata.game;

import com.badlogic.gdx.math.Vector2;

/**
 * Street event: a stray dog trots in from one side of the court, stops at a spot, poops, and runs off the other side.
 * Rules only, no drawing. The poop itself is kept by {@link Match} (it stays until someone steps in it).
 */
public final class StrayDog {
    public static final float SPEED = 150f;          // px/s while trotting in
    public static final float RUN_OFF_SPEED = 260f;  // px/s when leaving
    public static final float POOP_SECONDS = 1.2f;   // squatting
    public static final float OFF_COURT_X_LEFT = -60f, OFF_COURT_X_RIGHT = 1470f; // just outside the view

    public enum State { ENTERING, POOPING, LEAVING, GONE }

    public final Vector2 position = new Vector2();
    /** Where it poops. */
    public final Vector2 spot = new Vector2();
    /** +1 = walking right, -1 = walking left. */
    public final int direction;
    public State state = State.ENTERING;
    /** Seconds in the current state. */
    public float time = 0f;

    StrayDog(float spotX, float spotY, boolean fromLeft) {
        spot.set(spotX, spotY);
        direction = fromLeft ? 1 : -1;
        position.set(fromLeft ? OFF_COURT_X_LEFT : OFF_COURT_X_RIGHT, spotY);
    }

    /** Advances the dog. @return true on the frame it finishes pooping (the poop appears at {@link #spot}) */
    boolean update(float delta) {
        time += delta;
        switch (state) {
            case ENTERING:
                position.x += direction * SPEED * delta;
                if ((spot.x - position.x) * direction <= 0f) {
                    position.x = spot.x;
                    state = State.POOPING;
                    time = 0f;
                }
                return false;
            case POOPING:
                if (time >= POOP_SECONDS) {
                    state = State.LEAVING;
                    time = 0f;
                    return true;
                }
                return false;
            case LEAVING:
                position.x += direction * RUN_OFF_SPEED * delta;
                if (position.x < OFF_COURT_X_LEFT || position.x > OFF_COURT_X_RIGHT) state = State.GONE;
                return false;
            default:
                return false;
        }
    }

    public boolean isMoving() {
        return state == State.ENTERING || state == State.LEAVING;
    }
}
