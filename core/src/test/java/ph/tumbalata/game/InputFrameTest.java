package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Input frames, presses worked out per match step, and taps from this device between steps. */
class InputFrameTest {
    @Test
    void packsIntoOneIntAndBackExactly() {
        InputFrame f = new InputFrame().set(-0.7071f, 1f, InputFrame.A | InputFrame.START);
        InputFrame back = new InputFrame().unpack(f.pack());
        assertEquals(f, back);
        assertEquals(f.moveX(), back.moveX(), 0f);
        assertEquals(1f, back.moveY(), 0f);
        assertTrue(back.held(InputFrame.A) && back.held(InputFrame.START));
        assertFalse(back.held(InputFrame.B));
    }

    @Test
    void movementIsKeptInSmallStepsSoItPlaysBackTheSameEverywhere() {
        InputFrame f = new InputFrame().set(0.70710677f, -0.70710677f, 0);
        assertEquals(90 / 127f, f.moveX(), 0f);
        assertEquals(-90 / 127f, f.moveY(), 0f);
        assertEquals(1f, new InputFrame().set(3f, 0f, 0).moveX(), 0f, "clamped");
    }

    @Test
    void aButtonIsPressedOnTheStepItGoesDown() {
        PlayerInput in = new PlayerInput();
        InputFrame up = new InputFrame(), aDown = new InputFrame().set(0f, 0f, InputFrame.A);
        in.apply(up);
        in.apply(aDown);
        assertTrue(in.aPressed, "goes down");
        in.apply(aDown);
        assertFalse(in.aPressed, "held: not again");
        assertTrue(in.a);
        in.apply(up);
        in.apply(aDown);
        assertTrue(in.aPressed, "let go and pressed again");
    }

    @Test
    void holdTakesTheButtonsWithoutPressingThem() {
        PlayerInput in = new PlayerInput();
        InputFrame aDown = new InputFrame().set(0f, 0f, InputFrame.A);
        in.hold(aDown); // A was used to close the pause menu
        assertFalse(in.aPressed);
        in.apply(aDown);
        assertFalse(in.aPressed, "still the same press");
    }

    @Test
    void aQuickTapBetweenTwoStepsStillCounts() {
        PlayerInput live = new PlayerInput();
        LocalInputSource source = new LocalInputSource(slot -> slot == 0 ? live : null);
        PlayerInput sim = new PlayerInput();
        InputFrame f = new InputFrame();

        live.a = live.aPressed = true; // frame 1: down
        source.update();
        live.a = live.aPressed = false; // frame 2: up again, before any match step
        source.update();

        source.sample(0, f);
        sim.apply(f);
        assertTrue(sim.aPressed, "the tap is seen");
        source.sample(0, f);
        sim.apply(f);
        assertFalse(sim.a, "and let go on the next step");
    }

    @Test
    void theStickAndHeldButtonsComeFromTheLiveInput() {
        PlayerInput live = new PlayerInput();
        LocalInputSource source = new LocalInputSource(slot -> live);
        live.moveX = 1f;
        live.b = true;
        InputFrame f = new InputFrame();
        source.sample(2, f);
        assertEquals(1f, f.moveX(), 0f);
        assertTrue(f.held(InputFrame.B));
    }
}
