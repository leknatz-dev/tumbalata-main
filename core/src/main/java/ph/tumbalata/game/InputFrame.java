package ph.tumbalata.game;

/**
 * What one player holds during one match step: the stick and the buttons. This is all the match ever gets from a
 * player, whatever the source (keyboard, pad, touch controls, a phone across the network); presses are worked out in
 * {@link PlayerInput#apply} by comparing with the step before.
 *
 * <p>Movement is kept in steps of 1/127, so a frame packs into one int ({@link #pack()}) and plays back exactly the
 * same on every phone.
 */
public final class InputFrame {
    public static final int A = 1;
    public static final int B = 1 << 1;
    public static final int START = 1 << 2;
    private static final int ALL_BUTTONS = A | B | START;
    private static final float MOVE_STEPS = 127f;

    private int moveX, moveY; // -127..127
    private int buttons;

    /** {@code moveX}, {@code moveY}: -1..1 (clamped). {@code buttons}: {@link #A} | {@link #B} | {@link #START}. */
    public InputFrame set(float moveX, float moveY, int buttons) {
        this.moveX = quantize(moveX);
        this.moveY = quantize(moveY);
        this.buttons = buttons & ALL_BUTTONS;
        return this;
    }

    public InputFrame set(InputFrame other) {
        moveX = other.moveX;
        moveY = other.moveY;
        buttons = other.buttons;
        return this;
    }

    public InputFrame clear() {
        moveX = moveY = buttons = 0;
        return this;
    }

    public float moveX() { return moveX / MOVE_STEPS; }
    public float moveY() { return moveY / MOVE_STEPS; }
    public int buttons() { return buttons; }

    public boolean held(int button) {
        return (buttons & button) != 0;
    }

    /** The whole frame in one int (for sending): moveX, moveY and the buttons, a byte each. */
    public int pack() {
        return (moveX & 0xFF) | (moveY & 0xFF) << 8 | buttons << 16;
    }

    public InputFrame unpack(int packed) {
        moveX = (byte) packed;
        moveY = (byte) (packed >> 8);
        buttons = (packed >>> 16) & ALL_BUTTONS;
        return this;
    }

    private static int quantize(float v) {
        return Math.round(Math.max(-1f, Math.min(1f, v)) * MOVE_STEPS);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InputFrame && ((InputFrame) o).pack() == pack();
    }

    @Override
    public int hashCode() {
        return pack();
    }

    @Override
    public String toString() {
        return "InputFrame(" + moveX() + ", " + moveY() + (held(A) ? " A" : "") + (held(B) ? " B" : "")
            + (held(START) ? " START" : "") + ")";
    }
}
