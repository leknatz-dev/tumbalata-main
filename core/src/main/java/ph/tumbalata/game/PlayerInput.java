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

    /**
     * For a copy the match reads in its own fixed steps: takes what {@code live} holds right now, and keeps every press
     * until {@link #clearPresses()} says a step has used it. So a frame that runs no step doesn't lose a press, and a
     * frame that runs two doesn't press twice.
     */
    public void latch(PlayerInput live) {
        moveX = live.moveX;
        moveY = live.moveY;
        up = live.up;
        down = live.down;
        left = live.left;
        right = live.right;
        a = live.a;
        b = live.b;
        select = live.select;
        start = live.start;
        controllerConnected = live.controllerConnected;
        upPressed |= live.upPressed;
        downPressed |= live.downPressed;
        leftPressed |= live.leftPressed;
        rightPressed |= live.rightPressed;
        aPressed |= live.aPressed;
        bPressed |= live.bPressed;
        selectPressed |= live.selectPressed;
        startPressed |= live.startPressed;
    }

    /** The presses have been used (by a match step, or by whatever read them this frame). */
    public void clearPresses() {
        upPressed = downPressed = leftPressed = rightPressed = false;
        aPressed = bPressed = selectPressed = startPressed = false;
    }
}
