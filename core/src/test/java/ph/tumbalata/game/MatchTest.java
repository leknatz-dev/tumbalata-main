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

    /** @param characters picked character per player (default: everyone character 0) */
    private Match newMatch(int playerCount, int... characters) {
        players = new Array<>();
        for (int id = 0; id < playerCount; id++) {
            int character = id < characters.length ? characters[id] : 0;
            Player p = new Player(id, character, 0f, 0f, GameConstants.PLAYER_SPEED, new PlayerInput(),
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
        knockCanAndPickUpSlipper(); // a Thrower holds a slipper, so this is not a toss turn (see tayaGetsToToss...)
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

    /**
     * Regression: GameScreen's handlers query the match (which loops over the players) while the match itself is in
     * the middle of a loop over the players. libGDX Array iterators cannot be nested, so that crashed on a can hit.
     */
    @Test
    void eventHandlersMayQueryTheMatchWhileItIsUpdating() {
        newMatch(3);
        int[] queries = new int[1];
        match.setEvents(new Match.Events() {
            // Exactly what GameScreen does on a can hit (one loop over the players)
            @Override public void canKnocked(Player thrower) {
                match.isAnyThrowerPastLine();
                queries[0]++;
            }
            @Override public void scored(Player player, int points) {
                match.scores();
                queries[0]++;
            }
        });
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(1f);
        assertEquals(2, queries[0]);
    }

    @Test
    void anEventHandlerLoopingOverPlayersDoesNotCrashTheMatch() {
        newMatch(3);
        match.setEvents(new Match.Events() {
            @Override public void canKnocked(Player thrower) { match.isAnyThrowerPastLine(); }
        });
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(0.5f);
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

    // ------------------------------------------------------------------
    // Street event: trash
    // ------------------------------------------------------------------

    /** Walks p right until they slip on trash dropped just ahead of their feet. */
    private Trash walkIntoTrash(Player p) {
        Trash t = match.dropTrash(p.position.x + 20f, p.position.y + Match.FEET_OFFSET_Y);
        p.input.moveX = 1f;
        stepUntil(p::isStunned, DT, 1f);
        p.input.moveX = 0f;
        return t;
    }

    @Test
    void steppingOnTrashSlidesStunsAndUsesItUp() {
        newMatch(2);
        StringBuilder log = new StringBuilder();
        match.setEvents(new Match.Events() {
            @Override public void slipped(Player player, Trash trash) { log.append("slipped P").append(player.id + 1); }
        });
        Player taya = match.taya();
        float startX = taya.position.x;
        walkIntoTrash(taya);

        assertEquals("slipped P2", log.toString());
        assertEquals(0, match.trash().size, "used up");
        assertEquals(1, match.stats().slips[taya.id]);

        stepFor(Player.SLIDE_SECONDS + 0.05f);
        assertTrue(taya.position.x > startX + 40f, "slid along the way it was walking");
        assertTrue(taya.isDizzy());

        float x = taya.position.x;
        taya.input.moveX = -1f;
        stepFor(0.5f);
        assertEquals(x, taya.position.x, 0.01f, "no control while stunned");
        stepFor(Player.STUN_SECONDS);
        assertFalse(taya.isStunned());
        assertTrue(taya.position.x < x, "moves again once the stun wears off");
    }

    @Test
    void standingStillOnTrashDoesNotSlip() {
        newMatch(2);
        Player taya = match.taya();
        match.dropTrash(taya.position.x, taya.position.y + Match.FEET_OFFSET_Y);
        stepFor(1f);
        assertFalse(taya.isStunned());
        assertEquals(1, match.trash().size);
    }

    @Test
    void stunnedTayaCannotTagButAStunnedThrowerCanBeTagged() {
        newMatch(2);
        knockCanAndPickUpSlipper();
        tayaPicksUpCan();
        tayaPutsCanBack();
        Player taya = match.taya();

        walkIntoTrash(taya);
        stepFor(Player.SLIDE_SECONDS + 0.05f);
        p(0).position.set(taya.position);
        step(DT);
        assertSame(taya, match.taya(), "a stunned Taya can't tag");

        stepFor(Player.STUN_SECONDS);
        p(0).position.set(Match.THROW_LINE_X + 300f, 150f);
        walkIntoTrash(p(0));                                 // now the Thrower slips, past the line
        taya.position.set(p(0).position);
        step(DT);
        assertSame(p(0), match.taya(), "a stunned Thrower is still caught");
    }

    @Test
    void trashFadesAwayAfterItsLifetime() {
        newMatch(2);
        match.dropTrash(600f, 100f);
        stepFor(Trash.LIFETIME_SECONDS - 0.5f);
        assertEquals(1, match.trash().size);
        stepFor(1f);
        assertEquals(0, match.trash().size);
    }

    @Test
    void streetEventsThrowTrashNeverMoreThanTheLimit() {
        newMatch(3);
        int[] incoming = new int[1], landed = new int[1];
        match.setEvents(new Match.Events() {
            @Override public void trashIncoming(Trash t) { incoming[0]++; }
            @Override public void trashLanded(Trash t) { landed[0]++; }
        });
        match.setStreetEvents(true, 42L);
        for (int i = 0; i < Math.round(120f / DT); i++) {
            step(DT);
            assertTrue(match.trash().size <= Match.TRASH_MAX);
        }
        assertTrue(landed[0] >= 4, "about one every 15-25 s, got " + landed[0]);
        assertTrue(incoming[0] >= landed[0]);
    }

    @Test
    void strayDogPoopsOnceAndThePoopStaysUntilSteppedIn() {
        newMatch(2);
        StringBuilder log = new StringBuilder();
        match.setEvents(new Match.Events() {
            @Override public void dogArrived(StrayDog dog) { log.append("dog "); }
            @Override public void dogPooped(StrayDog dog) { log.append("poop "); }
        });
        match.setStreetEvents(true, 7L);
        stepUntil(() -> match.poop() != null, DT, 60f);
        stepUntil(() -> match.dog() == null, DT, 15f);
        assertEquals("dog poop ", log.toString());

        Vector2 spot = new Vector2(match.poop());
        stepFor(120f); // never fades, and no second dog while it is there
        assertEquals(spot, match.poop());
        assertEquals("dog poop ", log.toString());
    }

    @Test
    void steppingInPoopStunsOnTheSpotAndRemovesIt() {
        newMatch(2);
        Player taya = match.taya();
        match.dropPoop(taya.position.x + 20f, taya.position.y + Match.FEET_OFFSET_Y);
        taya.input.moveX = 1f;
        stepUntil(taya::isStunned, DT, 1f);
        float x = taya.position.x;
        stepFor(0.5f);
        assertEquals(x, taya.position.x, 0.01f, "no slide, just stunned");
        assertTrue(taya.isDizzy());
        assertEquals(null, match.poop());
        assertEquals(1, match.stats().poopSteps[taya.id]);
        stepFor(Match.POOP_STUN_SECONDS);
        assertFalse(taya.isStunned());
    }

    @Test
    void noTrashWhenStreetEventsAreOff() {
        newMatch(2);
        stepFor(60f);
        assertEquals(0, match.trash().size);
    }

    // ------------------------------------------------------------------
    // Character traits and stats
    // ------------------------------------------------------------------

    @Test
    void traitsMultiplyTheRoleSpeedOnlyWhenEnabled() {
        newMatch(2, 0, 1); // P1 FAST, P2 STRONG
        assertEquals(GameConstants.PLAYER_SPEED, p(0).speed, 0.01f, "traits are off by default");

        match.setCharacterTraits(true);
        assertEquals(GameConstants.PLAYER_SPEED * Characters.SPEED[0], p(0).speed, 0.01f);
        assertEquals(GameConstants.TAYA_SPEED * Characters.SPEED[1], p(1).speed, 0.01f, "Taya keeps its own base speed");
        assertTrue(p(0).speed > GameConstants.PLAYER_SPEED);
    }

    @Test
    void strongCharacterThrowsHarder() {
        newMatch(2, 1, 0); // P1 STRONG
        match.setCharacterTraits(true);
        throwRight(p(0), 50f);
        float expected = 50f * 18f * Characters.THROW[1];
        assertEquals(expected, p(0).slipper.velocity.len(), expected * 0.03f);
    }

    @Test
    void accurateCharacterHasASlowerAimMeter() {
        float[] angles = new float[2];
        for (int run = 0; run < 2; run++) {
            newMatch(2, 2, 0); // P1 ACCURATE
            match.setCharacterTraits(run == 1);
            pressA(p(0), DT);
            stepFor(0.1f);
            angles[run] = Math.abs(match.angle());
        }
        assertTrue(angles[1] < angles[0] * 0.85f, "normal " + angles[0] + ", accurate " + angles[1]);
    }

    @Test
    void traitStaysWithThePlayerWhenTheyBecomeTaya() {
        newMatch(2, 2, 0); // P1 ACCURATE (Thrower), P2 FAST (Taya)
        match.setCharacterTraits(true);
        float aim = Characters.AIM[2];

        // P1 knocks the can, Taya puts it back and tags P1: P1 becomes Taya
        knockCanAndPickUpSlipper();
        tayaPicksUpCan();
        tayaPutsCanBack();
        Vector2 outside = new Vector2(Match.THROW_LINE_X + 200f, 200f);
        p(0).position.set(outside);
        match.taya().position.set(outside);
        step(DT);
        assertSame(p(0), match.taya());
        assertEquals(GameConstants.TAYA_SPEED * Characters.SPEED[2], p(0).speed, 0.01f, "P1 is still ACCURATE");
        assertEquals(GameConstants.PLAYER_SPEED * Characters.SPEED[0], p(1).speed, 0.01f, "P2 is still FAST");

        // P2 misses, so P1 (now Taya) aims the toss: the arrow turns at 200 deg/s, slowed by P1's trait
        throwRight(p(1), 10f);
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);
        tayaPicksUpCan();
        pressA(p(0), DT);
        stepFor(0.1f);
        assertEquals((0.1f + DT) * 200f * aim, match.angle(), 2f, "slower aim as Taya too");
    }

    @Test
    void sneakyCharacterReachesTheirSlipperFromFurther() {
        newMatch(2, 3, 0); // P1 SNEAKY
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f);
        p(0).position.set(p(0).slipper.position).add(Match.SLIPPER_PICKUP_DISTANCE + 10f, 0f);
        assertFalse(match.canPickUpSlipper(p(0)), "too far for a normal reach");
        match.setCharacterTraits(true);
        assertTrue(match.canPickUpSlipper(p(0)));
    }

    @Test
    void statsCountKnocksTagsAndCatches() {
        newMatch(2);
        knockCanAndPickUpSlipper();
        assertEquals(1, match.stats().knocks[0]);

        tayaPicksUpCan();
        tayaPutsCanBack();
        Vector2 outside = new Vector2(Match.THROW_LINE_X + 200f, 200f);
        p(0).position.set(outside);
        match.taya().position.set(outside);
        step(DT);
        assertEquals(1, match.stats().tags[1]);
        assertEquals(1, match.stats().caught[0]);
    }

    @Test
    void aThrowerWhoIsHomeMayThrowAgainToKnockTheCanForATeammate() {
        newMatch(3);
        Player out = match.nextThrower();
        throwRight(out, 15f);                         // misses: this one will still be out, slipper on the ground
        Player first = match.nextThrower();
        knockCanAndPickUpSlipperFor(first);           // the other knocks the can and grabs the slipper...
        first.position.set(Match.THROW_LINE_X - 60f, Match.WORLD_HEIGHT * 0.5f); // ...and is home again
        step(DT);
        assertEquals(Match.RoundMode.SCRAMBLE, match.mode(), "the round goes on: one Thrower is still out");
        assertFalse(match.canRethrow(out), "no slipper in hand");
        assertTrue(match.canRethrow(first));

        tayaPicksUpCan();
        tayaPutsCanBack();
        assertTrue(match.isCanStandingOnBase());

        first.position.y = Match.CAN_BASE_Y;          // in line with the can
        throwRight(first, 90f);
        stepUntil(() -> match.can().isHit, DT, 3f);
        assertEquals(2, match.stats().knocks[first.id], "knocked the can a second time");
        assertFalse(first.hasSlipper, "and has to fetch the slipper again");
    }

    @Test
    void tayaGetsToTossWhenEverySlipperLiesInTayasArea() {
        newMatch(2);
        throwRight(p(0), 90f);                                   // knocks the can; the slipper ends up past the line
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f);
        assertTrue(match.allSlippersDownPastLine());
        assertEquals(Match.RoundMode.SCRAMBLE, match.mode(), "can is down: no toss yet");

        StringBuilder log = new StringBuilder();
        match.setEvents(new Match.Events() {
            @Override public void tayaTossTurn(Player taya) { log.append("toss "); }
        });
        tayaPicksUpCan();
        tayaPutsCanBack();
        step(DT);
        assertEquals(Match.RoundMode.TAYA_TOSS, match.mode(), "every slipper is in Taya's area and the can is up");
        assertEquals("toss ", log.toString());
        assertTrue(match.canTayaPickUpCan(), "Taya picks the can up to toss it, as after a full miss");
    }

    @Test
    void afterAMissedTossTayaWaitsBeforePickingUpTheCan() {
        newMatch(2);
        throwRight(p(0), 50f);
        stepUntil(() -> match.mode() == Match.RoundMode.TAYA_TOSS, DT, 10f);
        tayaPicksUpCan();
        tossAt(new Vector2(match.taya().position).add(0f, 150f)); // misses on purpose
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 5f);
        assertTrue(match.canLockTimeLeft() > Match.CAN_LOCK_AFTER_MISS - 0.1f, "the head start starts at the miss");

        Player taya = match.taya();
        taya.position.set(match.can().position);
        pressB(taya);
        assertFalse(taya.hasCan, "can't grab the can straight away to toss again");

        stepFor(Match.CAN_LOCK_AFTER_MISS);
        assertEquals(0f, match.canLockTimeLeft(), 0.001f);
        taya.position.set(match.can().position);
        pressB(taya);
        assertTrue(taya.hasCan, "after the head start Taya can fetch the can as usual");
    }

    @Test
    void aKnockedCanIsNotLocked() {
        newMatch(2);
        throwRight(p(0), 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        assertEquals(0f, match.canLockTimeLeft(), 0.001f, "the head start is only after a toss");
    }

    @Test
    void noTossWhileAThrowerHoldsTheirSlipper() {
        newMatch(2);
        knockCanAndPickUpSlipper();                              // P1 holds the slipper again
        tayaPicksUpCan();
        tayaPutsCanBack();
        step(DT);
        assertFalse(match.allSlippersDownPastLine());
        assertEquals(Match.RoundMode.SCRAMBLE, match.mode());
    }

    @Test
    void knocksAreCountedPerRoundForStreaks() {
        newMatch(3);
        Player out = match.nextThrower();
        throwRight(out, 15f);
        Player first = match.nextThrower();
        knockCanAndPickUpSlipperFor(first);
        assertEquals(1, match.knocksThisRound());
        first.position.set(Match.THROW_LINE_X - 60f, Match.CAN_BASE_Y);
        tayaPicksUpCan();
        tayaPutsCanBack();
        throwRight(first, 90f);                            // re-throw: knocks it again
        stepUntil(() -> match.can().isHit, DT, 3f);
        assertEquals(2, match.knocksThisRound(), "second knock in the same round: a streak");
        match.restartRound();
        assertEquals(0, match.knocksThisRound(), "a new round starts the count over");
    }

    @Test
    void aThrowerPastTheLineCannotRethrow() {
        newMatch(2);
        knockCanAndPickUpSlipper();
        p(0).position.set(Match.THROW_LINE_X + 100f, 200f);
        assertFalse(match.canRethrow(p(0)));
    }

    /** Like knockCanAndPickUpSlipper, for whoever throws first. */
    private void knockCanAndPickUpSlipperFor(Player thrower) {
        thrower.position.y = Match.CAN_BASE_Y;
        throwRight(thrower, 90f);
        stepUntil(() -> match.mode() == Match.RoundMode.SCRAMBLE, DT, 3f);
        stepFor(3f);
        thrower.position.set(thrower.slipper.position);
        pressB(thrower);
        assertTrue(thrower.hasSlipper);
    }

    @Test
    void eachThrowersTurnIsAnnouncedInOrder() {
        newMatch(4);
        StringBuilder log = new StringBuilder();
        match.setEvents(new Match.Events() {
            @Override public void throwerTurn(Player thrower) { log.append(thrower.label()).append(' '); }
        });
        StringBuilder expected = new StringBuilder();
        step(DT);
        for (int i = 0; i < 3; i++) {
            Player up = match.nextThrower();
            expected.append(up.label()).append(' ');
            throwRight(up, 15f); // weak: nobody hits the can, the round goes on
            step(DT);
        }
        assertEquals(expected.toString(), log.toString(), "start of the round, then after every throw, once each");
    }

    @Test
    void restartRoundPutsEveryoneBackWithTheSameRoles() {
        newMatch(3);
        Player taya = match.taya();
        throwRight(p(0), 30f);
        match.restartRound();
        assertSame(taya, match.taya(), "same Taya");
        assertFalse(p(0).hasThrown);
        assertTrue(p(0).hasSlipper);
        assertSame(p(0), match.nextThrower(), "same Thrower goes first");
        assertEquals(Match.RoundMode.THROWING, match.mode());
    }

    @Test
    void selectNoLongerRestartsTheRound() {
        newMatch(2);
        throwRight(p(0), 30f);
        p(0).input.selectPressed = true; // Select opens the pause menu now (GameScreen), not a restart
        step(DT);
        assertTrue(p(0).hasThrown);
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
