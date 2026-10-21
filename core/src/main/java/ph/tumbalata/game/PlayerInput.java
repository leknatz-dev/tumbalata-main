package ph.tumbalata.game;

/**
 * One player's input: what they hold, and what went down just now. Two uses:
 * <ul>
 * <li>Live, for this frame: filled in by {@link InputManager#update()} from the keyboard and that player's pad (the
 * menus read this).</li>
 * <li>In a match: filled in by {@link #apply} once per match step from an {@link InputFrame}, whatever the source
 * (this device, touch, the network). The match only reads the stick, A, B and Start.</li>
 * </ul>
 * No allocations; safe to read from anywhere.
 */
public final class PlayerInput {
    /** Movement, each -1..1 (diagonals are normalized so they are not faster). */
    public float moveX, moveY;

    /** Digital directions, held this frame. */
    public boolean up, down, left, right;

    /** Buttons held this frame. */
    public boolean a, b, select, start;

    /** True only on the frame (or match step) the button/direction went down. */
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean aPressed, bPressed, selectPressed, startPressed;

    /** True while a controller is plugged into this player's slot. */
    public boolean controllerConnected;

    public boolean isMoving() {
        return moveX != 0f || moveY != 0f;
    }

    /** One match step: takes what the frame holds; a button is pressed on the step it goes down. */
    public void apply(InputFrame frame) {
        boolean wasA = a, wasB = b, wasStart = start;
        moveX = frame.moveX();
        moveY = frame.moveY();
        a = frame.held(InputFrame.A);
        b = frame.held(InputFrame.B);
        start = frame.held(InputFrame.START);
        aPressed = a && !wasA;
        bPressed = b && !wasB;
        startPressed = start && !wasStart;
    }

    /**
     * Takes what the frame holds without any presses: those buttons were used elsewhere (the pause menu), so letting
     * go and pressing again is needed before they count in the match.
     */
    public void hold(InputFrame frame) {
        apply(frame);
        clearPresses();
    }

    /** Nothing went down just now. */
    public void clearPresses() {
        upPressed = downPressed = leftPressed = rightPressed = false;
        aPressed = bPressed = selectPressed = startPressed = false;
    }
}
