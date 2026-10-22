package ph.tumbalata.game;

/**
 * On-screen controls for a phone, as input: a floating joystick on the left part of the screen (it appears where the
 * thumb lands and follows the drag) and two buttons on the right, A (yellow) and B (red). Several fingers at once:
 * run with one thumb and press with the other. {@link InputManager} merges this into this device's player
 * (slot 0) while it is {@link #setEnabled enabled}; {@code TouchControlsView} draws it.
 *
 * <p>Sizes are shares of the screen height, so the controls feel the same on any phone. Screen coordinates here are
 * pixels with y going down (as touches come in).
 */
public final class TouchControls {
    /** Where the thumb can grab the joystick: the left part of the screen (share of the width). */
    static final float STICK_ZONE = 0.45f;
    /** How far the stick moves for full speed (share of the screen height). */
    static final float STICK_RADIUS = 0.15f;
    /** A small push does nothing (share of {@link #STICK_RADIUS}). */
    static final float DEAD_ZONE = 0.18f;
    /** Button sizes and how far from the corner (shares of the screen height). */
    static final float A_RADIUS = 0.12f, B_RADIUS = 0.095f;
    /** A touch counts on a button a bit outside its drawing (thumbs are not precise). */
    static final float HIT_SLOP = 1.3f;
    public static final int MAX_POINTERS = 10;

    private boolean enabled;
    private float screenW, screenH;

    // The joystick: which finger has it, where it went down (its middle), and where the finger is now
    private int stickPointer = -1;
    private float originX, originY, knobX, knobY;
    private final boolean[] wasDown = new boolean[MAX_POINTERS];

    /** Movement, -1..1 each (y up), 0 when the stick is let go. */
    public float moveX, moveY;
    /** Buttons held now. */
    public boolean a, b;

    /** On during a match on a phone; off elsewhere (taps on a menu are not button presses). */
    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        release();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** True when this device has a touch screen and no keyboard to play with (a phone or tablet). */
    public static boolean wantedOn(com.badlogic.gdx.Application.ApplicationType type) {
        return type == com.badlogic.gdx.Application.ApplicationType.Android
            || type == com.badlogic.gdx.Application.ApplicationType.iOS
            || Boolean.getBoolean("tumbalata.touch"); // -Dtumbalata.touch=true: try them on a computer with the mouse
    }

    private void release() {
        stickPointer = -1;
        moveX = moveY = 0f;
        a = b = false;
        java.util.Arrays.fill(wasDown, false);
    }

    /**
     * One frame of touches. {@code down[i]}, {@code x[i]}, {@code y[i]}: finger i (pixels, y down).
     * The screen is {@code width} x {@code height} pixels.
     */
    public void update(boolean[] down, float[] x, float[] y, float width, float height) {
        screenW = width;
        screenH = height;
        if (!enabled) return;

        // The joystick: kept by its finger until it lifts; a new finger landing on the left part grabs it
        if (stickPointer >= 0 && !down[stickPointer]) stickPointer = -1;
        if (stickPointer < 0) {
            for (int i = 0; i < MAX_POINTERS; i++) {
                if (down[i] && !wasDown[i] && x[i] < width * STICK_ZONE && !onButton(x[i], y[i])) {
                    stickPointer = i;
                    originX = x[i];
                    originY = y[i];
                    break;
                }
            }
        }
        moveX = moveY = 0f;
        if (stickPointer >= 0) {
            knobX = x[stickPointer];
            knobY = y[stickPointer];
            float radius = STICK_RADIUS * height;
            float dx = (knobX - originX) / radius, dy = (originY - knobY) / radius; // y up
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1f) { // past the edge: the knob stays on the edge, the middle stays put
                dx /= len;
                dy /= len;
                len = 1f;
            }
            if (len > DEAD_ZONE) {
                moveX = dx;
                moveY = dy;
            }
        }

        // The buttons: any other finger on them
        a = b = false;
        for (int i = 0; i < MAX_POINTERS; i++) {
            wasDown[i] = down[i];
            if (!down[i] || i == stickPointer) continue;
            if (inside(x[i], y[i], aX(), aY(), A_RADIUS * height * HIT_SLOP)) a = true;
            else if (inside(x[i], y[i], bX(), bY(), B_RADIUS * height * HIT_SLOP)) b = true;
        }
    }

    private boolean onButton(float x, float y) {
        return inside(x, y, aX(), aY(), A_RADIUS * screenH * HIT_SLOP) || inside(x, y, bX(), bY(), B_RADIUS * screenH * HIT_SLOP);
    }

    private static boolean inside(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    // Layout (pixels, y down): A in the bottom right corner, B to its left and a little lower, like a pad
    public float aX() { return screenW - 0.19f * screenH; }
    public float aY() { return screenH - 0.33f * screenH; }
    public float aRadius() { return A_RADIUS * screenH; }
    public float bX() { return screenW - 0.43f * screenH; }
    public float bY() { return screenH - 0.17f * screenH; }
    public float bRadius() { return B_RADIUS * screenH; }

    /** True while a thumb holds the joystick. */
    public boolean isStickHeld() { return stickPointer >= 0; }
    /** The joystick's middle (where the thumb landed) and the knob (where it is now), pixels y down. */
    public float stickX() { return stickPointer >= 0 ? originX : 0.22f * screenH; }
    public float stickY() { return stickPointer >= 0 ? originY : screenH - 0.26f * screenH; }
    public float knobX() { return stickPointer >= 0 ? clampKnob(true) : stickX(); }
    public float knobY() { return stickPointer >= 0 ? clampKnob(false) : stickY(); }
    public float stickRadius() { return STICK_RADIUS * screenH; }

    private float clampKnob(boolean xAxis) {
        float dx = knobX - originX, dy = knobY - originY, r = stickRadius();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float k = len > r ? r / len : 1f;
        return xAxis ? originX + dx * k : originY + dy * k;
    }
}
