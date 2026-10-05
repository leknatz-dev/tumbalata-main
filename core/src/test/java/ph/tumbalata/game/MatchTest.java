package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** Plays rounds through {@link Match} with scripted input. No window or graphics needed. */
class MatchTest {
    private static final float DT = 1f / 60f;
    private static final float FINE_DT = 1f / 1200f; // while aiming, so angle and power can be hit precisely

    private Match match;
    private Array<Player> players;

    private Match newMatch(int playerCount) {
        players = new Array<>();
        for (int id = 0; id < playerCount; id++) {
            Player p = new Player(id, 0, 0f, 0f, GameConstants.PLAYER_SPEED, new PlayerInput(),
                Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y, null, null, null);
            p.slipper = new Slipper(0f, 0f);
            players.add(p);
        }
        MapCollision noWalls = new MapCollision(new Array<>());
        match = new Match(players, new Can(0f, 0f), noWalls, noWalls, 10_000f);
        return match;
    }

    private Player p(int id) {
        return players.get(id);
    }

    // ------------------------------------------------------------------
    // Scripted input
    // ------------------------------------------------------------------

    private void step(float dt) {
        match.update(dt);
        for (Player p : players) {
            PlayerInput in = p.input;
            in.aPressed = in.bPressed = in.selectPressed = in.startPressed = false;
        }
    }

    private void stepFor(float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) step(DT);
    }

    private void stepUntil(BooleanSupplier done, float dt, float maxSeconds) {
        for (float t = 0; t < maxSeconds; t += dt) {
            if (done.getAsBoolean()) return;
            step(dt);
        }
        throw new AssertionError("condition not reached within " + maxSeconds + " s");
    }

    private void pressA(Player p, float dt) {
        p.input.aPressed = true;
        step(dt);
    }

    private void pressB(Player p) {
        p.input.bPressed = true;
        step(DT);
    }

    private void walk(Player p, float moveX, float seconds) {
        p.input.moveX = moveX;
        stepFor(seconds);
        p.input.moveX = 0f;
    }

    /** A Thrower throws straight right (angle 0) with the given power. */
    private void throwRight(Player p, float power) {
        pressA(p, FINE_DT);                                                        // start aiming
        stepUntil(() -> match.angle() < 0f, FINE_DT, 5f);                          // past the top of the swing...
        stepUntil(() -> Math.abs(match.angle()) < 0.3f, FINE_DT, 5f);              // ...back to straight ahead
        pressA(p, FINE_DT);                                                        // lock angle
        stepUntil(() -> Math.abs(match.power() - power) < 0.3f, FINE_DT, 5f);
        pressA(p, FINE_DT);                                                        // throw
        assertTrue(p.hasThrown);
    }

    /** Taya (holding the can) tosses it at a point. */
    private void tossAt(Vector2 target) {
        Player taya = match.taya();
        float angle = (new Vector2(target).sub(taya.position).angleDeg());
        float distance = taya.position.dst(target);
        float power = powerForTossDistance(distance);

        pressA(taya, FINE_DT);
        stepUntil(() -> Math.abs(match.angle() - angle) < 0.3f, FINE_DT, 5f);
        pressA(taya, FINE_DT);
        stepUntil(() -> Math.abs(match.power() - power) < 0.3f, FINE_DT, 5f);
        pressA(taya, FINE_DT);
        assertEquals(Match.RoundMode.TOSS_FLYING, match.mode());
    }

    private static float powerForTossDistance(float distance) {
        for (float power = 0; power <= 100f; power += 0.1f) {
            if (Match.tossDistance(power) >= distance) return power;
        }
        return 100f;
    }

    /** Taya picks the can up from where it is. */
    private void tayaPicksUpCan() {
        Player taya = match.taya();
        taya.position.set(match.can().position);
        pressB(taya);
        assertTrue(taya.hasCan, "Taya should hold the can");
    }

    private void tayaPutsCanBack() {
        Player taya = match.taya();
        taya.position.set(match.canBase());
        pressB(taya);
        assertTrue(match.isCanStandingOnBase());
    }

    // ------------------------------------------------------------------
    // Start of a round
    // ------------------------------------------------------------------

    @Test
    void roundStartsWithLastPlayerAsTayaAndThrowersHoldingSlippers() {
        newMatch(3);
        assertSame(p(2), match.taya());
        assertSame(p(0), match.nextThrower());
        assertTrue(p(0).hasSlipper);
        assertTrue(p(1).hasSlipper);
        assertEquals(Match.RoundMode.THROWING, match.mode());
    }

    @Test
    void throwerWhoHasNotThrownCannotCrossTheLine() {
        newMatch(2);
        walk(p(0), 1f, 3f);
        assertTrue(p(0).position.x < Match.THROW_LINE_X);
    }

    // ------------------------------------------------------------------
    // Everyone missed -> Taya's toss
    // ------------------------------------------------------------------

    @Test
    void missedThrowerMayRoamButNotPickUpSlipperOrBeTagged() {
        newMatch(2);
        throwRight(p(0), 50f); // too weak to reach the can
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);

        walk(p(0), 1f, 1.5f);
        assertTrue(p(0).position.x > Match.THROW_LINE_X, "after throwing, the line no longer holds you back");

        p(0).position.set(p(0).slipper.position);
        pressB(p(0));
        assertFalse(p(0).hasSlipper, "slipper stays down until the can is hit or Taya's toss misses");

        match.taya().position.set(p(0).position);
        stepFor(0.2f);
        assertSame(p(1), match.taya(), "no tagging before the toss");
    }

    @Test
    void tayaCannotPickUpTheStandingCanWhileThrowersAreStillThrowing() {
        newMatch(2);
        tayaPicksUpCanAttempt();
        assertFalse(match.taya().hasCan);
    }

    private void tayaPicksUpCanAttempt() {
        Player taya = match.taya();
        taya.position.set(match.can().position);
        pressB(taya);
    }

    @Test
    void tossThatHitsASlipperMakesItsOwnerTaya() {
        newMatch(2);
        throwRight(p(0), 50f);
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);

        tayaPicksUpCan(); // Taya tosses from where the can stood
        Vector2 slipper = new Vector2(p(0).slipper.position);
        tossAt(slipper);

        stepUntil(() -> match.taya() == p(0), DT, 5f);
        assertEquals(Scoring.TOSS_HIT, p(1).score);
        assertSame(p(1), match.nextThrower(), "the old Taya throws first");
        assertEquals(Match.RoundMode.THROWING, match.mode());
    }

    @Test
    void tayaCannotMoveWhileHoldingTheCanForTheToss() {
        newMatch(2);
        throwRight(p(0), 50f);
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);
        tayaPicksUpCan();

        Vector2 before = new Vector2(match.taya().position);
        walk(match.taya(), -1f, 1f);
        assertEquals(before, match.taya().position, "frozen while holding the can");

        pressA(match.taya(), DT);
        walk(match.taya(), -1f, 1f);
        assertEquals(before, match.taya().position, "frozen while aiming");
    }

    @Test
    void tayaCanWalkWhileCarryingTheCanBackInTheScramble() {
        newMatch(2);
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f);
        tayaPicksUpCan();

        Vector2 before = new Vector2(match.taya().position);
        walk(match.taya(), -1f, 0.5f);
        assertTrue(match.taya().position.x < before.x - 50f);
    }

    @Test
    void tossThatMissesStartsScrambleAndHomeSafeScores() {
        newMatch(2);
        throwRight(p(0), 50f);
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);

        tayaPicksUpCan();
        Vector2 nowhere = new Vector2(match.taya().position).add(0f, 150f); // far from the slipper
        tossAt(nowhere);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 5f);

        p(0).position.set(p(0).slipper.position);
        pressB(p(0));
        assertTrue(p(0).hasSlipper, "after a missed toss, slippers can be picked up");

        p(0).position.set(Match.THROW_LINE_X - 60f, Match.CAN_BASE_Y);
        step(DT);
        assertEquals(Scoring.HOME_SAFE, p(0).score);
        assertEquals(Match.RoundMode.THROWING, match.mode(), "new round");
        assertFalse(p(0).hasThrown);
        assertSame(p(1), match.taya(), "same Taya");
    }

    // ------------------------------------------------------------------
    // Can hit -> scramble -> tag
    // ------------------------------------------------------------------

    @Test
    void knockingTheCanScoresAndStartsTheScramble() {
        newMatch(2);
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        assertTrue(match.can().isHit);
        assertEquals(Scoring.KNOCK_CAN, p(0).score);
    }

    /** P1 knocks the can over, everything settles, and P1 picks their slipper back up. */
    private void knockCanAndPickUpSlipper() {
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f); // let the can and slipper settle
        p(0).position.set(p(0).slipper.position);
        pressB(p(0));
        assertTrue(p(0).hasSlipper);
    }

    @Test
    void tayaTagsThrowerPastTheLineOnlyOnceTheCanIsBackOnBase() {
        newMatch(2);
        knockCanAndPickUpSlipper();

        Vector2 outside = new Vector2(Match.THROW_LINE_X + 200f, 200f);
        p(0).position.set(outside);
        match.taya().position.set(outside);
        stepFor(0.1f);
        assertSame(p(1), match.taya(), "can is down: no tag yet");

        tayaPicksUpCan();
        tayaPutsCanBack();

        p(0).position.set(outside);
        match.taya().position.set(outside);
        step(DT);
        assertSame(p(0), match.taya(), "tagged");
        assertEquals(Scoring.TAG, p(1).score);
    }

    @Test
    void throwerWithoutTheirSlipperCannotBeTagged() {
        newMatch(2);
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f);
        tayaPicksUpCan();
        tayaPutsCanBack();

        Vector2 outside = new Vector2(Match.THROW_LINE_X + 200f, 200f);
        p(0).position.set(outside);
        match.taya().position.set(outside);
        step(DT);
        assertFalse(p(0).hasSlipper);
        assertSame(p(1), match.taya(), "no slipper in hand: cannot be tagged");
    }

    @Test
    void throwerBehindTheLineIsSafe() {
        newMatch(2);
        knockCanAndPickUpSlipper();
        tayaPicksUpCan();
        tayaPutsCanBack();

        Vector2 safe = new Vector2(Match.THROW_LINE_X - 30f, 200f);
        p(0).position.set(safe);
        match.taya().position.set(safe);
        step(DT);
        assertSame(p(1), match.taya());
    }

    @Test
    void throwerWhoHasNotThrownMayStillThrowDuringTheScramble() {
        newMatch(3);
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);

        assertSame(p(1), match.nextThrower());
        walk(p(1), 1f, 2f);
        assertTrue(p(1).position.x < Match.THROW_LINE_X, "still held behind the line");

        throwRight(p(1), 60f);
        assertTrue(p(1).hasThrown);
        assertNotNull(p(1).slipper);
    }

    @Test
    void tayaPicksUpTheCanOnlyWhenItWasKnockedOver() {
        newMatch(2);
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f);
        tayaPicksUpCan();
        tayaPutsCanBack();

        tayaPicksUpCanAttempt();
        assertFalse(match.taya().hasCan, "a standing can stays on its base during the scramble");
    }

    // ------------------------------------------------------------------
    // Events and timer
    // ------------------------------------------------------------------

    @Test
    void scoringIsReportedToEvents() {
        newMatch(2);
        int[] reported = new int[1];
        match.setEvents(new Match.Events() {
            @Override
            public void scored(Player player, int points) {
                reported[0] += points;
            }
        });
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        assertEquals(Scoring.KNOCK_CAN, reported[0]);
    }

    @Test
    void signEventsFireForTossTurnAndMissedToss() {
        newMatch(2);
        StringBuilder log = new StringBuilder();
        match.setEvents(new Match.Events() {
            @Override public void tayaTossTurn(Player taya) { log.append("turn "); }
            @Override public void tossMissed(Player taya) { log.append("missed "); }
            @Override public void canKnocked(Player thrower) { log.append("knocked "); }
        });
        throwRight(p(0), 50f);
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);
        tayaPicksUpCan();
        tossAt(new Vector2(match.taya().position).add(0f, 150f));
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 5f);
        assertEquals("turn missed ", log.toString());
    }

    @Test
    void anyThrowerPastLineDecidesRunOrHaha() {
        newMatch(2);
        assertFalse(match.isAnyThrowerPastLine(), "everyone starts behind the line");
        throwRight(p(0), 50f);
        walk(p(0), 1f, 1.5f);
        assertTrue(match.isAnyThrowerPastLine());
    }

    @Test
    void playersCanReachTheBottomOfTheCourt() {
        newMatch(2);
        Player taya = match.taya();
        taya.input.moveY = -1f;
        stepFor(4f);
        taya.input.moveY = 0f;
        assertEquals(Match.PLAY_MIN_Y, taya.position.y, 0.01f, "only the walls (or the art edge) stop players now");
        assertTrue(taya.position.y < 0f, "below the old y=20 limit");
    }

    @Test
    void matchEndsWhenTheTimerRunsOut() {
        players = new Array<>();
        for (int id = 0; id < 2; id++) {
            Player p = new Player(id, 0, 0f, 0f, 200f, new PlayerInput(), 20f, 1260f, 20f, 684f, null, null, null);
            p.slipper = new Slipper(0f, 0f);
            players.add(p);
        }
        MapCollision noWalls = new MapCollision(new Array<>());
        match = new Match(players, new Can(0f, 0f), noWalls, noWalls, 1f);
        stepFor(0.9f);
        assertFalse(match.isOver());
        stepFor(0.2f);
        assertTrue(match.isOver());
        assertEquals(0f, match.timeLeft(), 0f);
    }
}
