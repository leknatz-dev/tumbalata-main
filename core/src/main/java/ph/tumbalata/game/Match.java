package ph.tumbalata.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * The rules of a Tumbalata match: roles, round flow, tagging, scoring and the match timer.
 * No rendering or GL code: GameScreen draws what this class says, and MatchTest plays rounds through it.
 *
 * <p>One Taya guards the can; everyone else is a Thrower with their own slipper. Each round the Throwers throw in turn
 * order. Until someone hits the can, slippers stay where they land and nobody can be tagged. A hit starts the
 * scramble; if everyone misses, Taya tosses the can at the slippers instead. During the scramble Taya can tag
 * Throwers past the line once the can stands on its base again. See docs/PRD.md "Game concept and rules".
 *
 * <p>Call {@link #update(float)} once per frame. It reads each player's {@link PlayerInput}.
 */
public class Match {
    // --- WORLD (gameplay grid; the court art is bigger, see GameScreen) ---
    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 704f;
    public static final float THROW_LINE_X = WORLD_WIDTH * 0.25f;
    public static final float CAN_BASE_X = WORLD_WIDTH * 0.8f;
    public static final float CAN_BASE_Y = WORLD_HEIGHT * 0.5f;

    // Hard limits for player movement. The map's collision1 walls are the real court edges; these only stop players
    // walking off the art. The bottom limit is below the court's bottom wall (about y -44) so the walls decide there.
    public static final float PLAY_MIN_X = 20f;
    public static final float PLAY_MAX_X = WORLD_WIDTH - 20f;
    public static final float PLAY_MIN_Y = -54f;   // the art starts at y -64
    public static final float PLAY_MAX_Y = WORLD_HEIGHT - 20f;

    // Safety-net limits far outside the art (the view is x 0..1408, y -64..704)
    private static final float OUTER_MIN_X = -256f;
    private static final float OUTER_MIN_Y = -320f;
    private static final float OUTER_MAX_X = 1664f;
    private static final float OUTER_MAX_Y = 704f;

    // --- DISTANCES ---
    public static final float SLIPPER_PICKUP_DISTANCE = 45f;
    public static final float CAN_PICKUP_DISTANCE = 35f;
    public static final float CAN_PLACE_DISTANCE = 35f;
    public static final float SLIPPER_HITS_CAN_DISTANCE = 25f;
    public static final float CAN_HITS_SLIPPER_DISTANCE = 30f;
    public static final float TAG_DISTANCE = 30f;
    public static final float CAN_ON_BASE_DISTANCE = 10f;

    // --- HITBOXES AND PHYSICS ---
    public static final float PLAYER_HITBOX_W = 14f;
    public static final float PLAYER_HITBOX_H = 10f;
    public static final float PLAYER_HITBOX_OFFSET_Y = 0f;

    private static final float CAN_MIN_THROW_DISTANCE = 40f;
    private static final float CAN_MAX_THROW_DISTANCE = 500f;
    private static final float CAN_POWER_CURVE = 1.5f;
    private static final float CAN_RELEASE_HEIGHT = 30f;

    public static final float CAN_HITBOX_W = 16f;
    public static final float CAN_HITBOX_H = 16f;
    public static final float CAN_HITBOX_OFFSET_X = 0f;
    public static final float CAN_HITBOX_OFFSET_Y = 0f;
    private static final float CAN_WALL_BOUNCE = 0.5f;
    private static final float CAN_WALL_MAX_HEIGHT = 10000f;

    private static final float SLIPPER_HITBOX_W = 12f;
    private static final float SLIPPER_HITBOX_H = 12f;
    private static final float SLIPPER_WALL_BOUNCE = 0.6f;
    private static final float SLIPPER_SPEED_PER_POWER = 18f;

    private static final float TOSS_IMPACT_DELAY = 0.6f; // seconds between Taya's can hitting a slipper and the swap

    // --- SPAWNS (relative to the throw line / can base). Waiting Throwers stand behind the first one. ---
    private static final float THROWER_SPAWN_BEHIND_LINE = 50f;
    private static final float TAYA_SPAWN_RIGHT_OF_BASE = 80f;
    private static final float[][] WAITING_THROWER_OFFSETS = { { -130f, 90f }, { -130f, -90f }, { -200f, 0f } };
    private static final float EDGE_MARGIN = 20f;

    /** Where the round is. */
    public enum RoundMode {
        /** Throwers take turns. Nobody has hit the can yet: slippers stay where they land and nobody can be tagged. */
        THROWING,
        /** Everyone threw and missed: Taya picks up the can and tosses it at a slipper. */
        TAYA_TOSS,
        /** Taya's can is in the air or rolling. */
        TOSS_FLYING,
        /**
         * The can was hit, or Taya's toss missed: Throwers grab their own slipper and run home, Taya stands the can
         * back up and then may tag anyone past the line. Throwers who haven't thrown yet still may.
         */
        SCRAMBLE
    }

    public enum Aim { NONE, ANGLE, POWER }

    /** Hooks for effects, sounds and HUD pop-ups. All methods are optional. */
    public interface Events {
        default void scored(Player player, int points) {}
        default void slipperThrown(Player thrower) {}
        default void canTossed(Player taya) {}
        /** Everyone missed: it is Taya's turn to toss the can. */
        default void tayaTossTurn(Player taya) {}
        /** Taya's can stopped without hitting a slipper: the scramble starts. */
        default void tossMissed(Player taya) {}
        default void canKnocked(Player thrower) {}
        default void tagged(Player taya, Player victim) {}
        default void tossHitSlipper(Player taya, Player victim) {}
        default void roundEnded() {}
    }

    private static final Events NO_EVENTS = new Events() {};

    // Loop over players by index, never with for-each: libGDX Array iterators cannot be nested, and event handlers
    // (GameScreen) query the match, which loops over the players, while the match is in the middle of a loop.
    private final Array<Player> players;
    private final Roster roster;
    private final Can can;
    private final MapCollision playerWalls;
    private final MapCollision canBlockers;
    private final Vector2 canBase = new Vector2(CAN_BASE_X, CAN_BASE_Y);
    private Events events = NO_EVENTS;

    private RoundMode mode = RoundMode.THROWING;
    private Aim aim = Aim.NONE;
    private Player aimer;  // who is aiming (a Thrower or Taya), null when aim == NONE
    private Player taya;   // shortcut to the roster's Taya, refreshed by applyRoles()

    private float angleTimer = 0f, currentAngle = 0f;
    private float powerTimer = 0f, currentPower = 0f;

    private final Vector2 canLandingSpot = new Vector2();
    private boolean isImpactPending = false;
    private float impactDelayTimer = 0f;
    private Player impactVictim; // whose slipper Taya's can hit

    private float timeLeft;
    private boolean over = false;

    /**
     * @param players     2 to 4 players, index = player id, each with a {@link Player#slipper}
     * @param can         the can (its position is reset to the base)
     * @param playerWalls walls for players and slippers
     * @param canBlockers walls for the can
     * @param matchTime   match length in seconds
     */
    public Match(Array<Player> players, Can can, MapCollision playerWalls, MapCollision canBlockers, float matchTime) {
        for (int i = 0; i < players.size; i++) {
            if (players.get(i).id != i) throw new IllegalArgumentException("players must be in id order");
            if (players.get(i).slipper == null) throw new IllegalArgumentException("player " + i + " has no slipper");
        }
        this.players = players;
        this.roster = new Roster(players.size);
        this.can = can;
        this.playerWalls = playerWalls;
        this.canBlockers = canBlockers;
        this.timeLeft = matchTime;
        resetRound(false);
    }

    public void setEvents(Events events) {
        this.events = events == null ? NO_EVENTS : events;
    }

    // ------------------------------------------------------------------
    // State for drawing and tests
    // ------------------------------------------------------------------

    public Array<Player> players() { return players; }
    public Roster roster() { return roster; }
    public Player taya() { return taya; }
    public Can can() { return can; }
    public Vector2 canBase() { return canBase; }
    public RoundMode mode() { return mode; }
    public Aim aim() { return aim; }
    public Player aimer() { return aimer; }
    public float angle() { return currentAngle; }
    public float power() { return currentPower; }
    public float timeLeft() { return Math.max(0f, timeLeft); }
    public boolean isOver() { return over; }

    /** One score per player, index = player id. */
    public int[] scores() {
        int[] s = new int[players.size];
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            s[p.id] = p.score;
        }
        return s;
    }

    /** The Thrower whose turn it is to throw: the first one in turn order who hasn't thrown yet, or null. */
    public Player nextThrower() {
        for (int i = 0; i < roster.throwers().size; i++) {
            Player p = players.get(roster.throwers().get(i));
            if (!p.hasThrown) return p;
        }
        return null;
    }

    public boolean isCanStanding() {
        return !can.isHit && !taya.hasCan;
    }

    public boolean isCanStandingOnBase() {
        return isCanStanding() && can.position.dst(canBase) < CAN_ON_BASE_DISTANCE;
    }

    /** Taya may pick the can up when it has been knocked over (or tossed), or to toss it when everyone missed. */
    public boolean canTayaPickUpCan() {
        if (taya.hasCan) return false;
        return (mode == RoundMode.SCRAMBLE && can.isHit) || mode == RoundMode.TAYA_TOSS;
    }

    public boolean isTayaNearCan() {
        return taya.position.dst(can.position) < CAN_PICKUP_DISTANCE;
    }

    public boolean canTayaPlaceCan() {
        return mode == RoundMode.SCRAMBLE && taya.hasCan && taya.position.dst(canBase) < CAN_PLACE_DISTANCE;
    }

    /** Slippers on the ground may only be picked up once the can was hit or Taya's toss missed. */
    public boolean canPickUpSlipper(Player p) {
        return mode == RoundMode.SCRAMBLE && p != taya && !p.hasSlipper
            && p.position.dst(p.slipper.position) < SLIPPER_PICKUP_DISTANCE;
    }

    /** True for a Thrower whose slipper is on the ground or flying (so it should be drawn). */
    public boolean isSlipperOut(Player p) {
        return p != taya && !p.hasSlipper;
    }

    private boolean allSlippersStopped() {
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (isSlipperOut(p) && p.slipper.velocity.len() > 0) return false;
        }
        return true;
    }

    /** The round is over when every Thrower is back behind the line holding their own slipper. */
    private boolean everyoneHome() {
        if (aim != Aim.NONE) return false;
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (p == taya) continue;
            if (!p.hasSlipper || p.position.x >= THROW_LINE_X) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    /** Advances the match by one frame: timer, input, movement, physics and rules. */
    public void update(float delta) {
        if (over) return;
        timeLeft -= delta;
        if (timeLeft <= 0f) {
            over = true;
            return;
        }

        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            p.prevPosition.set(p.position);
        }
        handleInput(delta);
        step(delta);
    }

    private void handleInput(float delta) {
        // --- Movement: everyone, except a Thrower who is aiming and Taya holding the can for the toss ---
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (isFrozen(p)) {
                p.stop();
                continue;
            }
            p.handleInput(delta);
        }

        // --- B: Throwers pick up their own slipper ---
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (p.input.bPressed && canPickUpSlipper(p)) p.hasSlipper = true;
        }

        // --- B: Taya picks up the can, or puts it back on its base ---
        if (taya.input.bPressed) {
            if (canTayaPickUpCan() && isTayaNearCan()) {
                taya.hasCan = true;
            } else if (canTayaPlaceCan()) {
                taya.hasCan = false;
                can.reset(canBase.x, canBase.y);
            }
        }

        // --- A: aim (angle, then power, then release) ---
        if (aim == Aim.NONE) {
            Player up = nextThrower();
            if ((mode == RoundMode.THROWING || mode == RoundMode.SCRAMBLE) && up != null && up.hasSlipper
                && up.input.aPressed) {
                startAim(up);
            } else if (mode == RoundMode.TAYA_TOSS && taya.hasCan && taya.input.aPressed) {
                startAim(taya);
            }
        } else if (aimer.input.aPressed) {
            if (aim == Aim.ANGLE) {
                aim = Aim.POWER;
                powerTimer = 0f;
            } else {
                Player who = aimer;
                aim = Aim.NONE;
                aimer = null;
                if (who == taya) {
                    tayaThrowCan();
                    mode = RoundMode.TOSS_FLYING;
                } else {
                    launchSlipper(who);
                }
            }
        }

        // Reset round: Select (any player's pad) or R
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (p.input.selectPressed) {
                resetRound(false);
                break;
            }
        }
    }

    /**
     * A Thrower stands still while aiming. Taya stands still from picking up the can for the toss until throwing it,
     * so the toss always leaves from where the can was picked up.
     */
    public boolean isFrozen(Player p) {
        if (p == taya) return mode == RoundMode.TAYA_TOSS && taya.hasCan;
        return p == aimer;
    }

    private void startAim(Player p) {
        aimer = p;
        aim = Aim.ANGLE;
        angleTimer = 0f;
    }

    private void step(float delta) {
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            p.update(delta);
            playerWalls.slide(p.position, p.prevPosition.x, p.prevPosition.y,
                PLAYER_HITBOX_W, PLAYER_HITBOX_H, PLAYER_HITBOX_OFFSET_Y);
        }
        if (taya.hasCan) can.position.set(taya.position.x, taya.position.y + 15);

        if (aim == Aim.ANGLE) {
            if (aimer == taya) {
                angleTimer += delta * 4f;
                currentAngle = (angleTimer * 50f) % 360f;
            } else {
                angleTimer += delta * 3.5f;
                currentAngle = MathUtils.sin(angleTimer) * 80f;
            }
        } else if (aim == Aim.POWER) {
            powerTimer += delta * 4f;
            currentPower = ((MathUtils.sin(powerTimer) + 1f) / 2f) * 100f;
        }

        updateCan(delta);

        // Slippers on the ground or in flight; a moving slipper knocks over a standing can
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (!isSlipperOut(p)) continue;
            boolean moving = p.slipper.velocity.len() > 0;
            updateSlipper(p.slipper, delta);
            if (moving && isCanStanding() && mode != RoundMode.TOSS_FLYING
                && p.slipper.position.dst(can.position) < SLIPPER_HITS_CAN_DISTANCE) {
                triggerCanHit(p);
            }
        }

        switch (mode) {
            case THROWING:
                // Everyone threw, nothing hit the can: Taya's turn to toss
                if (nextThrower() == null && aim == Aim.NONE && allSlippersStopped()) {
                    mode = RoundMode.TAYA_TOSS;
                    events.tayaTossTurn(taya);
                }
                break;

            case TAYA_TOSS:
                break;

            case TOSS_FLYING:
                if (isImpactPending) {
                    impactDelayTimer += delta;
                    if (impactDelayTimer >= TOSS_IMPACT_DELAY) {
                        isImpactPending = false;
                        impactDelayTimer = 0f;
                        swapRoles(impactVictim);
                    }
                    return;
                }

                if (can.zPosition <= 0 && can.zVelocity <= 0) {
                    for (int pi = 0; pi < players.size; pi++) {
                        Player p = players.get(pi);
                        if (!isSlipperOut(p)) continue;
                        if (can.position.dst(p.slipper.position) < CAN_HITS_SLIPPER_DISTANCE) {
                            triggerTayaCanHitSlipper(p);
                            return;
                        }
                    }
                    if (can.velocity.len() == 0) {
                        mode = RoundMode.SCRAMBLE; // missed: grab your slippers and run!
                        events.tossMissed(taya);
                    }
                }
                break;

            case SCRAMBLE:
                if (everyoneHome()) {
                    endRoundNormally();
                    return;
                }
                checkTagging();
                break;
        }
    }

    private void updateCan(float delta) {
        if (taya.hasCan) return; // carried

        float oldX = can.position.x;
        float oldY = can.position.y;

        can.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        if (can.zPosition > CAN_WALL_MAX_HEIGHT) return;

        canBlockers.sweep(can.position, can.velocity, oldX, oldY,
            CAN_HITBOX_W, CAN_HITBOX_H, CAN_HITBOX_OFFSET_X, CAN_HITBOX_OFFSET_Y, CAN_WALL_BOUNCE);
    }

    private void updateSlipper(Slipper slipper, float delta) {
        float oldX = slipper.position.x;
        float oldY = slipper.position.y;

        slipper.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        playerWalls.sweep(slipper.position, slipper.velocity, oldX, oldY,
            SLIPPER_HITBOX_W, SLIPPER_HITBOX_H, 0f, 0f, SLIPPER_WALL_BOUNCE);
    }

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    private void award(Player p, int points) {
        if (points == 0) return;
        p.score += points;
        events.scored(p, points);
    }

    /** True if any Thrower is past the throw line right now. */
    public boolean isAnyThrowerPastLine() {
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (p != taya && p.position.x > THROW_LINE_X) return true;
        }
        return false;
    }

    /** A Thrower can be tagged while holding their slipper past the line. Only crossing back over the line is safe. */
    public boolean isTaggable(Player p) {
        return p != taya && p.hasSlipper && p.position.x > THROW_LINE_X;
    }

    /** Once the can stands on its base again, Taya can tag a taggable Thrower. */
    private void checkTagging() {
        if (!isCanStandingOnBase()) return;

        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (!isTaggable(p)) continue;
            if (taya.position.dst(p.position) < TAG_DISTANCE) {
                award(taya, Scoring.TAG);
                events.tagged(taya, p);
                swapRoles(p);
                return;
            }
        }
    }

    private void endRoundNormally() {
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            if (p != taya && p.hasThrown) award(p, Scoring.HOME_SAFE);
        }
        events.roundEnded();
        resetRound(true);
    }

    /** {@code newTaya} (a Thrower) becomes Taya; the old Taya becomes a Thrower and throws first. */
    private void swapRoles(Player newTaya) {
        roster.swapWithTaya(newTaya.id);
        resetRound(false);
    }

    private void launchSlipper(Player p) {
        // Once their slipper is thrown, a Thrower may cross the line (until the round resets)
        p.setXBounds(PLAY_MIN_X, PLAY_MAX_X);
        p.hasThrown = true;
        p.hasSlipper = false;

        p.slipper.position.set(p.position);
        float rad = currentAngle * MathUtils.degreesToRadians;
        float speed = currentPower * SLIPPER_SPEED_PER_POWER;
        p.slipper.velocity.set(MathUtils.cos(rad) * speed, MathUtils.sin(rad) * speed);
        events.slipperThrown(p);
    }

    private void triggerCanHit(Player thrower) {
        Slipper slipper = thrower.slipper;
        can.isHit = true;
        mode = RoundMode.SCRAMBLE;
        taya.hasCan = false;

        float hitAngle = MathUtils.atan2(can.position.y - slipper.position.y, can.position.x - slipper.position.x);
        float slipperImpactSpeed = slipper.velocity.len();

        float speedMultiplier = slipperImpactSpeed * 0.85f;
        float targetVx = MathUtils.cos(hitAngle) * speedMultiplier;
        float targetVy = MathUtils.sin(hitAngle) * speedMultiplier;

        float equivalentPower = MathUtils.clamp((slipperImpactSpeed / 1200f) * 100f, 20f, 100f);

        can.toss(targetVx, targetVy, equivalentPower);
        slipper.velocity.scl(0.5f);

        award(thrower, Scoring.KNOCK_CAN);
        events.canKnocked(thrower);
    }

    private void tayaThrowCan() {
        taya.hasCan = false;
        float rad = currentAngle * MathUtils.degreesToRadians;

        can.position.set(taya.position);
        can.zPosition = CAN_RELEASE_HEIGHT;
        can.zVelocity = 0f;
        can.velocity.set(0f, 0f);

        canLandingSpot.set(taya.position).add(MathUtils.cos(rad) * tossDistance(currentPower),
            MathUtils.sin(rad) * tossDistance(currentPower));

        can.tossTo(canLandingSpot.x, canLandingSpot.y, currentPower);
        events.canTossed(taya);
    }

    /** Where Taya's can first lands, measured from Taya, for a toss at {@code power} (0..100). */
    public static float tossDistance(float power) {
        float powerRatio = MathUtils.clamp(power / 100f, 0f, 1f);
        return CAN_MIN_THROW_DISTANCE
            + (CAN_MAX_THROW_DISTANCE - CAN_MIN_THROW_DISTANCE) * (float) Math.pow(powerRatio, CAN_POWER_CURVE);
    }

    private void triggerTayaCanHitSlipper(Player victim) {
        isImpactPending = true;
        impactDelayTimer = 0f;
        impactVictim = victim;

        Slipper slipper = victim.slipper;
        float hitAngle = MathUtils.atan2(slipper.position.y - can.position.y, slipper.position.x - can.position.x);
        float knockbackSpeed = 350f;
        slipper.velocity.set(MathUtils.cos(hitAngle) * knockbackSpeed, MathUtils.sin(hitAngle) * knockbackSpeed);

        can.velocity.scl(0.3f);
        can.zVelocity = 120f;

        award(taya, Scoring.TOSS_HIT);
        events.tossHitSlipper(taya, victim);
    }

    /**
     * Starts a new round: everyone back on their spawn spot, Throwers holding their slipper and not yet thrown.
     * @param nextTurn true when the round ended normally, so a different Thrower throws first next time
     */
    private void resetRound(boolean nextTurn) {
        mode = RoundMode.THROWING;
        aim = Aim.NONE;
        aimer = null;

        if (nextTurn) roster.nextTurn();
        applyRoles();

        // Throwers in turn order: the first one at the line, the rest on the waiting spots behind it
        for (int i = 0; i < roster.throwers().size; i++) {
            Player p = players.get(roster.throwers().get(i));
            p.setXBounds(PLAY_MIN_X, THROW_LINE_X - EDGE_MARGIN);
            p.hasSlipper = true;
            p.hasThrown = false;
            p.hasCan = false;
            if (i == 0) {
                p.position.set(THROW_LINE_X - THROWER_SPAWN_BEHIND_LINE, WORLD_HEIGHT * 0.5f);
            } else {
                float[] spot = WAITING_THROWER_OFFSETS[(i - 1) % WAITING_THROWER_OFFSETS.length];
                p.position.set(THROW_LINE_X - THROWER_SPAWN_BEHIND_LINE + spot[0], WORLD_HEIGHT * 0.5f + spot[1]);
            }
            p.slipper.reset(p.position.x, p.position.y);
        }

        taya.setXBounds(PLAY_MIN_X, PLAY_MAX_X);
        taya.position.set(canBase.x + TAYA_SPAWN_RIGHT_OF_BASE, canBase.y);
        taya.hasCan = false;
        taya.hasSlipper = false;
        taya.hasThrown = false;
        taya.slipper.reset(taya.position.x, taya.position.y);

        can.reset(canBase.x, canBase.y);

        angleTimer = 0f;
        powerTimer = 0f;
        isImpactPending = false;
        impactDelayTimer = 0f;
        impactVictim = null;
    }

    /** Points the taya shortcut at the roster's Taya and gives every player their role's speed. */
    private void applyRoles() {
        taya = players.get(roster.taya());
        for (int pi = 0; pi < players.size; pi++) {
            Player p = players.get(pi);
            p.speed = roster.isTaya(p.id) ? GameConstants.TAYA_SPEED : GameConstants.PLAYER_SPEED;
        }
    }
}
