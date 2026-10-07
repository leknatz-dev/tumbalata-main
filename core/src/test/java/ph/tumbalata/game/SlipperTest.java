package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlipperTest {
    private static final float FAR = 100_000f;

    /** Throws like GameScreen.launchSlipper (power x 18 px/s) and returns how far it slid before stopping. */
    private static float slideDistance(float power, float fps) {
        Slipper s = new Slipper(0, 0);
        s.velocity.set(power * 18f, 0);
        for (int i = 0; i < fps * 20 && s.velocity.len() > 0; i++) {
            s.update(1f / fps, -FAR, -FAR, FAR, FAR);
        }
        assertEquals(0f, s.velocity.len(), 0f, "slipper stops");
        return s.position.x;
    }

    @Test
    void morePowerThrowsFurther() {
        float last = 0f;
        for (int power = 10; power <= 100; power += 10) {
            float d = slideDistance(power, 60);
            assertTrue(d > last, "power " + power + " went " + d + ", not further than " + last);
            last = d;
        }
    }

    @Test
    void fullPowerDistanceIsStable() {
        // Current tuning: 100 power slides about 1018 px. Update this number on purpose when retuning.
        assertEquals(1018f, slideDistance(100, 60), 5f);
    }

    @Test
    void outerLimitsBounceTheSlipperBack() {
        Slipper s = new Slipper(100, 100);
        s.velocity.set(2000, 0);
        for (int i = 0; i < 30; i++) {
            s.update(1f / 60f, 0, 0, 200, 200);
            assertTrue(s.position.x <= 200 - s.radius + 0.001f);
        }
    }

    @Test
    void resetStopsTheSlipper() {
        Slipper s = new Slipper(0, 0);
        s.velocity.set(500, 500);
        s.reset(10, 20);
        assertEquals(10f, s.position.x, 0f);
        assertEquals(20f, s.position.y, 0f);
        assertEquals(0f, s.velocity.len(), 0f);
    }
}
