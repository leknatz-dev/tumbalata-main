package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Drawing between match steps. No sheets, so no graphics are needed. */
class PlayerViewTest {
    private static Player player() {
        Player p = new Player(0, 0, 100f, 100f, GameConstants.PLAYER_SPEED, new PlayerInput(), 0f, 2000f, 0f, 2000f);
        p.slipper = new Slipper(0f, 0f);
        return p;
    }

    @Test
    void drawsPartOfTheWayBetweenSteps() {
        Player p = player();
        PlayerView view = new PlayerView(p, null, null, null, null);
        view.beforeStep();
        p.position.set(104f, 100f); // one step of running
        view.follow(0.25f);
        assertEquals(101f, view.drawPos.x, 0.001f);
        view.follow(1f);
        assertEquals(104f, view.drawPos.x, 0.001f);
    }

    @Test
    void aJumpIsDrawnRightAwayNotSlidAcross() {
        Player p = player();
        PlayerView view = new PlayerView(p, null, null, null, null);
        view.beforeStep();
        p.position.set(900f, 400f); // respawned behind the line
        view.follow(0.25f);
        assertEquals(900f, view.drawPos.x, 0.001f);
        assertEquals(400f, view.drawPos.y, 0.001f);
    }

    @Test
    void snapDrawsExactlyWhereThePlayerIs() {
        Player p = player();
        PlayerView view = new PlayerView(p, null, null, null, null);
        view.beforeStep();
        p.position.set(110f, 100f);
        view.snap();
        assertEquals(110f, view.drawPos.x, 0.001f);
        view.follow(0f); // and the next in-between starts from there, not from before
        assertEquals(110f, view.drawPos.x, 0.001f);
    }

    @Test
    void theSlipperIsDrawnBetweenStepsToo() {
        Player p = player();
        PlayerView view = new PlayerView(p, null, null, null, null);
        view.beforeStep();
        p.slipper.position.set(20f, 0f);
        view.follow(0.5f);
        assertEquals(10f, view.slipperDrawPos.x, 0.001f);
    }
}
