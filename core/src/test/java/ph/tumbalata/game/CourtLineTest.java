package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

/** The court line and throw area, with the numbers from MAPCOLLISION.tmx (converted to world coordinates, y up). */
class CourtLineTest {
    // "courtline" polygon: a thin strip leaning slightly (x ~409 at the top, ~394 at the bottom)
    private static final float[] LINE_STRIP = { 404f, 373f, 389.5f, -46.5f, 399f, -46.5f, 413.5f, 375f };
    // "throw area" polygon
    private static final float[] THROW_AREA = { 198.667f, 370.667f, 402.667f, 371.333f, 389.333f, -41.333f, 142.667f, -42.667f };

    private static CourtLine mapCourt() {
        float[] c = CourtLine.centreLine(LINE_STRIP);
        return CourtLine.of(c[0], c[1], c[2], c[3], THROW_AREA);
    }

    @Test
    void centreLineOfTheStripRunsDownItsMiddle() {
        float[] c = CourtLine.centreLine(LINE_STRIP);
        assertEquals(408.75f, c[0], 0.01f);
        assertEquals(374f, c[1], 0.01f);
        assertEquals(394.25f, c[2], 0.01f);
        assertEquals(-46.5f, c[3], 0.01f);
    }

    @Test
    void theLineLeansWithThePerspective() {
        CourtLine court = mapCourt();
        assertTrue(court.xAt(374f) > court.xAt(-46.5f));
        assertTrue(court.isPast(405f, -40f), "past the line near the bottom");
        assertFalse(court.isPast(405f, 370f), "but not yet near the top, where the line is further right");
    }

    @Test
    void throwersSpawnInsideTheThrowAreaBehindTheLine() {
        for (int count = 2; count <= 4; count++) {
            Array<Player> players = new Array<>();
            for (int id = 0; id < count; id++) {
                Player p = new Player(id, 0, 0f, 0f, GameConstants.PLAYER_SPEED, new PlayerInput(),
                    Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y, null, null, null);
                p.slipper = new Slipper(0f, 0f);
                players.add(p);
            }
            MapCollision noWalls = new MapCollision(new Array<>());
            Match match = new Match(players, new Can(0f, 0f), noWalls, noWalls, 100f);
            match.setCourt(mapCourt());
            CourtLine court = match.court();
            for (Player p : players) {
                if (p == match.taya()) continue;
                float feetY = p.position.y + Match.FEET_OFFSET_Y;
                assertTrue(court.inThrowArea(p.position.x, feetY), p.label() + " spawns in the throw area: " + p.position);
                assertFalse(court.isPast(p.position), p.label() + " spawns behind the line");
            }
        }
    }

    // "can zone" polygon from the map: a tall strip on Taya's side
    private static final float[] CAN_ZONE = { 767.333f, 370f, 769.333f, -42f, 1000.667f, -42.667f, 999.333f, 372f };

    private static Match matchOnMapCourt(int count) {
        Array<Player> players = new Array<>();
        for (int id = 0; id < count; id++) {
            Player p = new Player(id, 0, 0f, 0f, GameConstants.PLAYER_SPEED, new PlayerInput(),
                Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y, null, null, null);
            p.slipper = new Slipper(0f, 0f);
            players.add(p);
        }
        MapCollision noWalls = new MapCollision(new Array<>());
        Match match = new Match(players, new Can(0f, 0f), noWalls, noWalls, 100f);
        match.setCourt(mapCourt().withCanZone(CAN_ZONE));
        return match;
    }

    @Test
    void tayaChoosesTheCanSpotInsideTheZoneAndItStaysForTheMatch() {
        Match match = matchOnMapCourt(2);
        match.beginCanPlacement();
        assertEquals(Match.RoundMode.PLACE_CAN, match.mode());
        Player taya = match.taya();
        assertTrue(taya.hasCan);
        float clock = match.timeLeft();

        taya.position.set(600f, 200f);                   // outside the zone: B does nothing
        taya.input.bPressed = true;
        match.update(1f / 60f);
        taya.input.bPressed = false;
        assertEquals(Match.RoundMode.PLACE_CAN, match.mode());
        assertEquals(clock, match.timeLeft(), 0.0001f, "the clock is stopped while choosing");

        float[] spot = new float[2];
        match.setEvents(new Match.Events() {
            @Override public void canSpotChosen(com.badlogic.gdx.math.Vector2 s) { spot[0] = s.x; spot[1] = s.y; }
        });
        taya.position.set(900f, 100f - Match.FEET_OFFSET_Y);  // feet at (900, 100), inside the zone
        taya.input.bPressed = true;
        match.update(1f / 60f);
        taya.input.bPressed = false;
        assertEquals(Match.RoundMode.THROWING, match.mode(), "the first round starts");
        assertEquals(900f, match.canBase().x, 0.01f);
        assertEquals(100f, match.canBase().y, 0.01f);
        assertEquals(900f, spot[0], 0.01f);
        assertTrue(match.isCanStandingOnBase(), "the can stands on the chosen spot");
        assertFalse(match.taya().hasCan);

        match.restartRound();
        assertEquals(900f, match.canBase().x, 0.01f, "and stays there for the rest of the match");
    }

    @Test
    void streetEventsNeverDropTrashInTheThrowArea() {
        Match match = matchOnMapCourt(2);
        match.setStreetEvents(true, 3L);
        for (int i = 0; i < 60 * 300; i++) {
            match.update(1f / 60f);
            for (Trash t : match.trash()) {
                assertFalse(match.court().inThrowArea(t.target.x, t.target.y), "trash in the throw area at " + t.target);
            }
        }
    }

    @Test
    void aThrowerWhoHasNotThrownStaysBehindTheLeaningLine() {
        Array<Player> players = new Array<>();
        for (int id = 0; id < 2; id++) {
            Player p = new Player(id, 0, 0f, 0f, GameConstants.PLAYER_SPEED, new PlayerInput(),
                Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y, null, null, null);
            p.slipper = new Slipper(0f, 0f);
            players.add(p);
        }
        MapCollision noWalls = new MapCollision(new Array<>());
        Match match = new Match(players, new Can(0f, 0f), noWalls, noWalls, 100f);
        match.setCourt(mapCourt());
        Player thrower = match.nextThrower();

        for (float y : new float[] { 350f, 0f }) {
            thrower.position.set(300f, y);
            thrower.input.moveX = 1f;
            for (int i = 0; i < 120; i++) match.update(1f / 60f);
            thrower.input.moveX = 0f;
            assertFalse(match.court().isPast(thrower.position), "stopped at the line at y " + y);
            assertEquals(match.court().xAt(thrower.position.y) - 20f, thrower.position.x, 1f, "right up to the line");
        }
    }
}
