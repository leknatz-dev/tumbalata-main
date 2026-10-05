package ph.tumbalata.game;

/**
 * One player's input for THIS frame, from the keyboard and/or that player's controller, already merged.
 * Game code only reads this and never cares where the input came from.
 * Filled in by InputManager.update(); no allocations, safe to read from anywhere during a frame.
 */
public final class PlayerInput {
    /** Movement, each -1..1 (diagonals are normalized so they are not faster). */
    public float moveX, moveY;

    /** Digital directions, held this frame. */
    public boolean up, down, left, right;

    /** Buttons held this frame. */
    public boolean a, b, select, start;

    /** True only on the frame the button/direction went down. */
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean aPressed, bPressed, selectPressed, startPressed;

    /** True while a controller is plugged into this player's slot. */
    public boolean controllerConnected;

    public boolean isMoving() {
        return moveX != 0f || moveY != 0f;
    }
}
