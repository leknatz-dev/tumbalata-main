package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The match camera on the 1408 x 768 court (from y = -64), as GameScreen uses it. */
class CourtCameraTest {
    private static CourtCamera camera() {
        return new CourtCamera(0f, -64f, 1408f, 768f);
    }

    @Test
    void startsOnTheWholeCourt() {
        CourtCamera c = camera();
        assertEquals(1f, c.zoom);
        assertEquals(704f, c.x);
        assertEquals(320f, c.y);
    }

    @Test
    void closeToTheCanItZoomsInAsFarAsItGoes() {
        CourtCamera c = camera();
        c.follow(700f, 300f, 760f, 300f);
        c.snap();
        assertEquals(CourtCamera.CLOSEST, c.zoom, 0.001f);
        assertTrue(c.sees(700f, 300f, 0f) && c.sees(760f, 300f, 0f), "both in view");
    }

    @Test
    void fartherApartItZoomsOutToKeepBoth() {
        CourtCamera c = camera();
        c.follow(400f, 200f, 900f, 200f);
        c.snap();
        assertTrue(c.zoom > CourtCamera.CLOSEST && c.zoom <= CourtCamera.WIDEST_FOLLOW);
        assertTrue(c.sees(400f, 200f, 0f), "the player");
        assertTrue(c.sees(900f, 200f, 0f), "and the can");
    }

    @Test
    void tooFarApartThePlayerStaysAndTheCanGoes() {
        CourtCamera c = camera();
        c.follow(150f, 100f, 1250f, 500f);
        c.snap();
        assertEquals(CourtCamera.WIDEST_FOLLOW, c.zoom, 0.001f);
        assertTrue(c.sees(150f, 100f, 0f), "never loses the player");
        assertFalse(c.sees(1250f, 500f, 0f), "the can is off-screen (the screen points to it)");
    }

    @Test
    void neverShowsPastTheMap() {
        CourtCamera c = camera();
        c.follow(20f, -40f, 60f, -30f); // a corner
        c.snap();
        float halfW = c.zoom * 1408f / 2f, halfH = c.zoom * 768f / 2f;
        assertTrue(c.x - halfW >= -0.01f && c.y - halfH >= -64.01f);
    }

    @Test
    void itEasesInsteadOfJumping() {
        CourtCamera c = camera();
        c.follow(700f, 300f, 760f, 300f);
        c.update(1f / 60f);
        assertTrue(c.zoom < 1f && c.zoom > 0.9f, "a little closer after one frame: " + c.zoom);
        for (int i = 0; i < 300; i++) c.update(1f / 60f);
        assertEquals(CourtCamera.CLOSEST, c.zoom, 0.01f);
    }

    @Test
    void mapsWorldPointsToTheFixedLayer() {
        CourtCamera c = camera();
        assertEquals(100f, c.toScreenX(100f), 0.001f, "whole court: the same place");
        c.follow(700f, 300f, 760f, 300f);
        c.snap();
        assertEquals(704f, c.toScreenX(c.x), 0.001f, "the middle of the view is the middle of the screen");
    }
}
