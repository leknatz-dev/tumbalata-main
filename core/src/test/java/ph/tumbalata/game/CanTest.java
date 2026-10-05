package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class CanTest {
    private static final float FAR = 100_000f; // outer limits far away, so only the physics matter
    private static final float RELEASE_HEIGHT = 30f;

    /** Simulates at a fixed frame rate and returns where the can first touches the ground. */
    private static Vector2 firstLanding(Can can, float fps) {
        float dt = 1f / fps;
        for (int i = 0; i < fps * 10; i++) {
            float zBefore = can.zPosition;
            can.update(dt, -FAR, -FAR, FAR, FAR);
            if (zBefore > 0f && can.zPosition == 0f) return new Vector2(can.position);
        }
        throw new AssertionError("can never landed");
    }

    private static Can thrownCan(float targetX, float targetY, float power) {
        Can can = new Can(0, 0);
        can.zPosition = RELEASE_HEIGHT;
        can.tossTo(targetX, targetY, power);
        return can;
    }

    @ParameterizedTest(name = "power {0}, distance {1}")
    @CsvSource({ "0, 40", "25, 97.5", "50, 202.6", "75, 338.8", "100, 500" })
    void tossToLandsOnTargetAt60Fps(float power, float distance) {
        Vector2 landing = firstLanding(thrownCan(distance, 0, power), 60);
        assertEquals(distance, landing.x, Math.max(4f, distance * 0.03f), "landing x");
        assertEquals(0f, landing.y, 0.001f, "stays on the aim line");
    }

    @Test
    void tossToFollowsTheAimDirection() {
        Vector2 landing = firstLanding(thrownCan(-200, 150, 60), 60);
        assertEquals(-0.8f, landing.nor().x, 0.001f);
    }

    @ParameterizedTest(name = "{0} fps")
    @ValueSource(floats = { 30, 60, 144, 240 })
    void landingDoesNotDependOnFrameRate(float fps) {
        Vector2 at60 = firstLanding(thrownCan(400, 0, 80), 60);
        Vector2 atFps = firstLanding(thrownCan(400, 0, 80), fps);
        assertEquals(at60.x, atFps.x, 12f, "within a few pixels of the 60 fps landing");
    }

    @Test
    void canComesToRestAfterBouncing() {
        Can can = thrownCan(300, 0, 70);
        for (int i = 0; i < 60 * 10; i++) can.update(1f / 60f, -FAR, -FAR, FAR, FAR);
        assertEquals(0f, can.velocity.len(), 0.001f);
        assertEquals(0f, can.zPosition, 0.001f);
        assertEquals(0f, can.zVelocity, 0.001f);
    }

    @Test
    void outerLimitsKeepTheCanInside() {
        Can can = new Can(50, 50);
        can.toss(-5000, 0, 50);
        for (int i = 0; i < 60; i++) {
            can.update(1f / 60f, 0, 0, 1000, 1000);
            assertTrue(can.position.x >= can.width / 2f - 0.001f, "x=" + can.position.x);
        }
    }

    @Test
    void spinAnimationIsProportionalToSpeed() {
        Can can = new Can(0, 0);
        can.velocity.set(Can.SPIN_REFERENCE_SPEED, 0);
        assertEquals(1f, can.spinRate(), 0.001f, "normal speed at the reference speed");

        can.velocity.set(Can.SPIN_REFERENCE_SPEED / 2f, 0);
        assertEquals(0.5f, can.spinRate(), 0.001f, "half speed spins half as fast");

        can.velocity.set(0, 0);
        assertEquals(0f, can.spinRate(), 0f, "a still can does not spin");
    }

    @Test
    void bounceIsSmall() {
        Can can = thrownCan(300, 0, 70);
        float maxBounceHeight = 0f;
        boolean landed = false;
        for (int i = 0; i < 60 * 5; i++) {
            float zBefore = can.zPosition;
            can.update(1f / 60f, -FAR, -FAR, FAR, FAR);
            if (zBefore > 0 && can.zPosition == 0) landed = true;
            if (landed) maxBounceHeight = Math.max(maxBounceHeight, can.zPosition);
        }
        assertTrue(maxBounceHeight < 10f, "bounces stay low after landing, got " + maxBounceHeight);
    }

    @Test
    void resetStandsTheCanUp() {
        Can can = thrownCan(300, 0, 70);
        can.update(1f / 60f, -FAR, -FAR, FAR, FAR);
        can.reset(10, 20);
        assertEquals(new Vector2(10, 20), can.position);
        assertEquals(0f, can.velocity.len(), 0f);
        assertEquals(0f, can.zPosition, 0f);
        assertFalse(can.isHit);
    }
}
