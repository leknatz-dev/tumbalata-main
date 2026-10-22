package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The on-screen controls with pretend fingers on a 2400 x 1080 phone (pixels, y down). */
class TouchControlsTest {
    private static final float W = 2400f, H = 1080f;
    private final boolean[] down = new boolean[TouchControls.MAX_POINTERS];
    private final float[] x = new float[TouchControls.MAX_POINTERS], y = new float[TouchControls.MAX_POINTERS];
    private final TouchControls touch = new TouchControls();

    private void finger(int i, float fx, float fy) {
        down[i] = true;
        x[i] = fx;
        y[i] = fy;
    }

    private void lift(int i) {
        down[i] = false;
    }

    private void frame() {
        touch.update(down, x, y, W, H);
    }

    private TouchControlsTest on() {
        touch.setEnabled(true);
        frame(); // learns the screen size
        return this;
    }

    @Test
    void theStickAppearsWhereTheThumbLandsAndPushesThatWay() {
        on();
        finger(0, 400f, 700f);
        frame();
        assertEquals(0f, touch.moveX, 0f, "just landed: not moved yet");
        finger(0, 400f + TouchControls.STICK_RADIUS * H, 700f); // all the way right
        frame();
        assertEquals(1f, touch.moveX, 0.001f);
        assertEquals(0f, touch.moveY, 0.001f);
        finger(0, 400f, 700f - TouchControls.STICK_RADIUS * H * 3f); // far up: still full speed, straight up
        frame();
        assertEquals(1f, touch.moveY, 0.001f, "y is up for the game");
        lift(0);
        frame();
        assertEquals(0f, touch.moveY, 0f, "let go: stop");
    }

    @Test
    void aSmallWobbleDoesNothingAndAHalfPushWalks() {
        on();
        finger(0, 400f, 700f);
        frame();
        finger(0, 400f + TouchControls.STICK_RADIUS * H * 0.1f, 700f);
        frame();
        assertEquals(0f, touch.moveX, 0f, "inside the dead zone");
        finger(0, 400f + TouchControls.STICK_RADIUS * H * 0.5f, 700f);
        frame();
        assertEquals(0.5f, touch.moveX, 0.01f, "half way: half speed");
    }

    @Test
    void runAndPressAtTheSameTime() {
        on();
        finger(0, 400f, 700f);
        frame();
        finger(0, 600f, 700f);
        finger(1, touch.aX(), touch.aY()); // the other thumb on A
        frame();
        assertTrue(touch.moveX > 0f);
        assertTrue(touch.a);
        assertFalse(touch.b);
        lift(1);
        finger(2, touch.bX() + 10f, touch.bY() - 10f); // near B counts too
        frame();
        assertFalse(touch.a);
        assertTrue(touch.b);
    }

    @Test
    void theRightSideOfTheScreenIsNotTheStick() {
        on();
        finger(0, W * 0.7f, 300f); // a tap away from the buttons, on the right
        frame();
        assertFalse(touch.isStickHeld());
        assertFalse(touch.a || touch.b);
    }

    @Test
    void offMeansNothingHappens() {
        frame();
        finger(0, touch.aX(), touch.aY());
        finger(1, 400f, 700f);
        frame();
        assertFalse(touch.a);
        assertFalse(touch.isStickHeld());
        on();
        touch.setEnabled(false);
        assertEquals(0f, touch.moveX, 0f);
    }
}
