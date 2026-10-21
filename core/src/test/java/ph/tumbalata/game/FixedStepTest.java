package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FixedStepTest {
    @Test
    void runsSixtyStepsASecondAtAnyFrameRate() {
        for (int fps : new int[] { 30, 60, 90, 120, 144 }) {
            FixedStep step = new FixedStep();
            int total = 0;
            for (int frame = 0; frame < fps * 10; frame++) total += step.advance(1f / fps);
            assertEquals(600, total, 1, fps + " fps: 10 seconds is 600 steps");
        }
    }

    @Test
    void shortFramesCarryTheirTimeOver() {
        FixedStep step = new FixedStep();
        assertEquals(0, step.advance(FixedStep.STEP * 0.6f), "not a whole step yet");
        assertEquals(1, step.advance(FixedStep.STEP * 0.6f), "the leftover adds up");
        assertEquals(0.2f, step.alpha(), 0.001f);
    }

    @Test
    void aLongStallCatchesUpOnlyALittle() {
        FixedStep step = new FixedStep();
        assertEquals(FixedStep.MAX_STEPS_PER_FRAME, step.advance(2f), "2 s in the background: no 120-step burst");
        assertEquals(0, step.advance(0f), "and the rest is dropped");
    }

    @Test
    void resetDropsTheLeftover() {
        FixedStep step = new FixedStep();
        step.advance(FixedStep.STEP * 0.9f);
        step.reset();
        assertEquals(0, step.advance(FixedStep.STEP * 0.5f));
    }
}
