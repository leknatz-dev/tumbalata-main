package ph.tumbalata.game;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Comparator;

public class GameScreen implements Screen {
    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 704f;


    private static final float VIEW_X = 0f;
    private static final float VIEW_Y = -64f;
    private static final float VIEW_W = 1408f;
    private static final float VIEW_H = 768f;

    private static final boolean SHOW_THROW_LINE = true;

    private static final String MAP_FILE = "MAPCOLLISION.tmx";
    private static final String PLAYER_COLLISION_LAYER = "collision1";
    private static final String CAN_COLLISION_LAYER = "collision2";
    private static final String UPPER_RING_FILE = "upperring.png";

    private static final float RING_X = 0f;
    private static final float RING_Y = -64f;

    private static final float PLAYER_HITBOX_W = 14f;
    private static final float PLAYER_HITBOX_H = 10f;
    private static final float PLAYER_HITBOX_OFFSET_Y = 0f;

    private static final float CAN_MIN_THROW_DISTANCE = 40f;
    private static final float CAN_MAX_THROW_DISTANCE = 500f;
    private static final float CAN_POWER_CURVE = 1.5f;

    private static final float CAN_RELEASE_HEIGHT = 30f;
    private static final float CAN_HITBOX_W = 16f;
    private static final float CAN_HITBOX_H = 16f;
    private static final float CAN_HITBOX_OFFSET_X = 0f;
    private static final float CAN_HITBOX_OFFSET_Y = 0f;
    private static final float CAN_WALL_BOUNCE = 0.5f;
    private static final boolean CAN_BLOCKED_BY_PLAYER_WALLS = true;
    private static final float CAN_WALL_MAX_HEIGHT = 10000f;

    private static final float SLIPPER_HITBOX_W = 12f;
    private static final float SLIPPER_HITBOX_H = 12f;
    private static final float SLIPPER_WALL_BOUNCE = 0.6f;

    private static final float OUTER_MIN_X = VIEW_X - 256f;
    private static final float OUTER_MIN_Y = VIEW_Y - 256f;
    private static final float OUTER_MAX_X = VIEW_X + VIEW_W + 256f;
    private static final float OUTER_MAX_Y = VIEW_Y + VIEW_H;

    private Viewport viewport;
    private OrthographicCamera camera;

    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private BitmapFont font;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private Texture upperRingTexture;

    private Texture playerSheet;
    private Texture playerSlipperSheet;
    private Texture playerCanSheet;
    private Texture canSheet;

    // Shared input for all players (keyboard + up to 4 controllers); owned by TumbalataGame
    private InputManager inputs;

    private MapCollision playerWalls;
    private MapCollision canWalls;
    private MapCollision canBlockers;

    private boolean debugCollision = false;
    private int debugView = 0;
    private float ringX = RING_X;
    private float ringY = RING_Y;

    // --- PLAYERS ---
    // Spawn spots, relative to the throw line / can base. Waiting Throwers stand behind the active one.
    private static final float THROWER_SPAWN_BEHIND_LINE = 50f;
    private static final float TAYA_SPAWN_RIGHT_OF_BASE = 80f;
    private static final float[][] WAITING_THROWER_OFFSETS = { { -130f, 90f }, { -130f, -90f }, { -200f, 0f } };
    private static final float NAME_TAG_Y = 34f;

    private final int[] characters;                       // characters[playerId] = picked character
    private final Array<Player> players = new Array<>();  // index = player id (0 = Player 1)
    private final Array<Player> drawOrder = new Array<>(); // same players, sorted back to front each frame
    private Roster roster;

    // --- MATCH TIMER ---
    private static final float MATCH_TIME_SECONDS = 40f;
    private static final int VICTORY_WINDOW_W = 700;
    private static final int VICTORY_WINDOW_H = 500;
    private float matchTimeLeft = MATCH_TIME_SECONDS;
    private boolean matchOver = false;

    private boolean fakeScoresForTesting = true;


    /**
     * Where the round is. Each Thrower has their own slipper and everyone throws once per round, in turn order.
     */
    private enum RoundMode {
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

    private enum Aim { NONE, ANGLE, POWER }

    private RoundMode mode = RoundMode.THROWING;
    private Aim aim = Aim.NONE;
    private Player aimer; // who is aiming (a Thrower or Taya), null when aim == NONE

    private float throwLineX, screenWidth, screenHeight;
    private Vector2 canBasePosition;
    private Player taya; // shortcut to the roster's Taya, refreshed by applyRoles()
    private Can can;

    private float angleTimer = 0f, currentAngle = 0f;
    private float powerTimer = 0f, currentPower = 0f;

    private Vector2 canLandingSpot = new Vector2();
    private boolean isImpactPending = false;
    private float impactDelayTimer = 0f;
    private Player impactVictim; // whose slipper Taya's can hit

    /** @param characters one picked character per player (2 to 4 players); characters[0] is Player 1's */
    public GameScreen(int[] characters) {
        if (characters.length < 2 || characters.length > InputManager.MAX_PLAYERS) {
            throw new IllegalArgumentException("2 to " + InputManager.MAX_PLAYERS + " players, got " + characters.length);
        }
        this.characters = new int[characters.length];
        for (int i = 0; i < characters.length; i++) {
            this.characters[i] = MathUtils.clamp(characters[i], 0, Characters.COUNT - 1);
        }
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIEW_W, VIEW_H, camera);

        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        camera.position.set(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f, 0);
        camera.update();

        shapeRenderer = new ShapeRenderer();
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);

        inputs = ((TumbalataGame) Gdx.app.getApplicationListener()).input();

        screenWidth = WORLD_WIDTH;
        screenHeight = WORLD_HEIGHT;
        throwLineX = screenWidth * 0.25f;
        canBasePosition = new Vector2(screenWidth * 0.8f, screenHeight * 0.5f);

        map = new TmxMapLoader().load(MAP_FILE);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f, spriteBatch);

        playerWalls = MapCollision.fromLayer(map, PLAYER_COLLISION_LAYER);
        canWalls = MapCollision.fromLayer(map, CAN_COLLISION_LAYER);
        canBlockers = CAN_BLOCKED_BY_PLAYER_WALLS ? MapCollision.combine(canWalls, playerWalls) : canWalls;

        upperRingTexture = new Texture(Gdx.files.internal(UPPER_RING_FILE));
        upperRingTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerSheet = new Texture(Gdx.files.internal("16x16 Walk-Sheet.png"));
        playerSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerSlipperSheet = new Texture(Gdx.files.internal("16x16 Walkwithslipper.png"));
        playerSlipperSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerCanSheet = new Texture(Gdx.files.internal("Walkwithcan.png"));
        playerCanSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        canSheet = new Texture(Gdx.files.internal("can_spin_sheet.png"));
        canSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        can = new Can(canBasePosition.x, canBasePosition.y, canSheet, 4, 1, 0.08f);

        roster = new Roster(characters.length);
        for (int id = 0; id < characters.length; id++) {
            // Position, speed and bounds are set by resetRound() below, from the player's role
            Player p = new Player(id, characters[id], 0f, 0f, GameConstants.PLAYER_SPEED, inputs.player(id),
                20f, screenWidth - 20f, 20f, screenHeight - 20f,
                playerSheet, playerSlipperSheet, playerCanSheet);
            p.slipper = new Slipper(0f, 0f);
            p.slipper.color.set(Color.BROWN).lerp(p.slotColor(), 0.5f);
            players.add(p);
            drawOrder.add(p);
        }
        resetRound(false);
    }

    // ------------------------------------------------------------------
    // Roles and rule checks
    // ------------------------------------------------------------------

    /** Points the taya shortcut at the roster's Taya and gives every player their role's speed. */
    private void applyRoles() {
        taya = players.get(roster.taya());
        for (Player p : players) {
            p.speed = roster.isTaya(p.id) ? GameConstants.TAYA_SPEED : GameConstants.PLAYER_SPEED;
        }
    }

    /** The Thrower whose turn it is to throw: the first one in turn order who hasn't thrown yet, or null. */
    private Player nextThrower() {
        for (int i = 0; i < roster.throwers().size; i++) {
            Player p = players.get(roster.throwers().get(i));
            if (!p.hasThrown) return p;
        }
        return null;
    }

    private boolean isCanStanding() {
        return !can.isHit && !taya.hasCan;
    }

    private boolean isCanStandingOnBase() {
        return isCanStanding()
            && Vector2.dst(can.position.x, can.position.y, canBasePosition.x, canBasePosition.y) < 10f;
    }

    /** Taya may pick the can up when it has been knocked over (or tossed), or to toss it when everyone missed. */
    private boolean canTayaPickUpCan() {
        if (taya.hasCan) return false;
        return (mode == RoundMode.SCRAMBLE && can.isHit) || mode == RoundMode.TAYA_TOSS;
    }

    /** Slippers on the ground may only be picked up once the can was hit or Taya's toss missed. */
    private boolean canPickUpSlipper(Player p) {
        return mode == RoundMode.SCRAMBLE && p != taya && !p.hasSlipper
            && Vector2.dst(p.position.x, p.position.y, p.slipper.position.x, p.slipper.position.y) < 45f;
    }

    private boolean allSlippersStopped() {
        for (Player p : players) {
            if (p != taya && !p.hasSlipper && p.slipper.velocity.len() > 0) return false;
        }
        return true;
    }

    /** The round is over when every Thrower is back behind the line holding their own slipper. */
    private boolean everyoneHome() {
        if (aim != Aim.NONE) return false;
        for (Player p : players) {
            if (p == taya) continue;
            if (!p.hasSlipper || p.position.x >= throwLineX) return false;
        }
        return true;
    }

    private void savePreviousPositions() {
        for (Player p : players) p.prevPosition.set(p.position);
    }

    private void resolvePlayerCollision(Player p) {
        playerWalls.slide(p.position, p.prevPosition.x, p.prevPosition.y,
            PLAYER_HITBOX_W, PLAYER_HITBOX_H, PLAYER_HITBOX_OFFSET_Y);
    }

    private void updateCan(float delta) {
        if (taya.hasCan) return; // carried: drawn at Taya's hands

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
    // Frame
    // ------------------------------------------------------------------

    @Override
    public void render(float delta) {
        viewport.apply();

        if (!matchOver) {
            updateMatchTimer(delta);
        }
        if (!matchOver) {
            savePreviousPositions();
            handleInput(delta);
            update(delta);
        }

        ScreenUtils.clear(0f, 0f, 0f, 1f);

        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);

        mapRenderer.setView(camera);
        mapRenderer.render();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        if (SHOW_THROW_LINE) {
            shapeRenderer.rectLine(throwLineX, VIEW_Y, throwLineX, VIEW_Y + VIEW_H, 4);
        }

        shapeRenderer.setColor(Color.LIGHT_GRAY);
        shapeRenderer.circle(canBasePosition.x, canBasePosition.y, 16f);

        if (taya.hasCan) {
            can.position.set(taya.position.x, taya.position.y + 15);
        }

        can.renderShadow(shapeRenderer);
        for (Player p : players) p.renderShadow(shapeRenderer);

        for (Player p : players) {
            if (p != taya && !p.hasSlipper) p.slipper.render(shapeRenderer);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        spriteBatch.begin();

        // Back to front: players higher on the court are further away and drawn first
        drawOrder.sort(BACK_TO_FRONT);
        for (Player p : drawOrder) p.render(spriteBatch);

        if (taya.hasCan) {
            TextureRegion canFrame = can.getCurrentFrame();
            float canWidth = can.getWidth();
            float canHeight = can.getHeight();

            float canX = taya.position.x - (canWidth / 2f);
            float canY = taya.position.y + 18f;

            spriteBatch.draw(
                canFrame,
                canX, canY,
                canWidth / 2f, canHeight / 2f,
                canWidth, canHeight,
                1f, 1f,
                90f
            );
        } else {
            can.render(spriteBatch);
        }

        spriteBatch.draw(upperRingTexture, ringX, ringY);

        drawNameTags();

        for (Player p : players) {
            if (canPickUpSlipper(p)) {
                font.draw(spriteBatch, p.label() + " [B] Pick Up Slipper", p.slipper.position.x - 60f, p.slipper.position.y + 25f);
            }
        }

        if (canTayaPickUpCan() && Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
            font.draw(spriteBatch, "[B] Pick Up Can", can.position.x - 35f, can.position.y + 30f);
        }

        if (mode == RoundMode.SCRAMBLE && taya.hasCan
            && Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
            font.draw(spriteBatch, "[B] Place Can at Base", canBasePosition.x - 45f, canBasePosition.y + 35f);
        }

        float timerLeft = Math.max(0f, matchTimeLeft);
        int totalSeconds = MathUtils.ceil(timerLeft);
        String timerText = (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
        font.getData().setScale(2.2f);
        font.setColor(timerLeft <= 10f ? Color.SCARLET : Color.WHITE);
        font.draw(spriteBatch, timerText, VIEW_X + VIEW_W / 2f - 100f, VIEW_Y + VIEW_H - 14f, 200f, Align.center, false);
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);

        font.draw(spriteBatch, statusText(), VIEW_X + VIEW_W / 2f - 300f, VIEW_Y + VIEW_H - 54f, 600f, Align.center, false);

        spriteBatch.end();

        renderUIOverlays();

        if (debugCollision) {
            renderDebugCollision();
        }
    }

    /** One line under the timer saying what the round is waiting for. */
    private String statusText() {
        switch (mode) {
            case THROWING: {
                Player up = nextThrower();
                return up != null ? up.label() + ", your throw!" : "Waiting for the slippers to stop...";
            }
            case TAYA_TOSS:
                return "Everyone missed! " + taya.label() + " (Taya): pick up the can and toss it at a slipper";
            case TOSS_FLYING:
                return "";
            case SCRAMBLE:
            default:
                return isCanStandingOnBase()
                    ? "The can is up: Taya can tag anyone past the line!"
                    : "Grab your slipper and get back behind the line!";
        }
    }

    private static final Comparator<Player> BACK_TO_FRONT = (a, b) -> Float.compare(b.position.y, a.position.y);

    /** "P1" over each player in their slot colour; the Taya is marked, Throwers who already threw are dimmed. */
    private void drawNameTags() {
        font.getData().setScale(1f);
        for (Player p : players) {
            String text = (p == taya) ? p.label() + " TAYA" : p.label();
            Color c = p.slotColor();
            font.setColor(c.r, c.g, c.b, (p != taya && p.hasThrown) ? 0.7f : 1f);
            font.draw(spriteBatch, text, p.position.x - 50f, p.position.y + NAME_TAG_Y, 100f, Align.center, false);
        }
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    private void updateMatchTimer(float delta) {
        matchTimeLeft -= delta;
        if (matchTimeLeft <= 0f) {
            endMatch();
        }
    }

    private void endMatch() {
        matchOver = true;

        int[] finalScores = new int[players.size];
        for (Player p : players) {
            finalScores[p.id] = fakeScoresForTesting ? MathUtils.random(0, 20) : p.score;
        }

        if (Gdx.app.getApplicationListener() instanceof TumbalataGame) {
            TumbalataGame tumbalata = (TumbalataGame) Gdx.app.getApplicationListener();
            tumbalata.changeScreen(new VictoryScreen(tumbalata, finalScores, characters),
                VICTORY_WINDOW_W, VICTORY_WINDOW_H);
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void handleInput(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (Gdx.app.getApplicationListener() instanceof TumbalataGame) {
                ((TumbalataGame) Gdx.app.getApplicationListener()).toggleFullscreen();
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) debugCollision = !debugCollision;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) debugView = (debugView + 1) % 3;

        // Ctrl + I/J/K/L nudges the ring (Ctrl, because I/J/K/L also move Player 3)
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (debugCollision && ctrl) {
            float step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 10f : 1f;
            if (Gdx.input.isKeyJustPressed(Input.Keys.I)) ringY += step;
            if (Gdx.input.isKeyJustPressed(Input.Keys.K)) ringY -= step;
            if (Gdx.input.isKeyJustPressed(Input.Keys.J)) ringX -= step;
            if (Gdx.input.isKeyJustPressed(Input.Keys.L)) ringX += step;
        }

        // --- Movement: everyone, except a Thrower who is aiming (Taya can walk while aiming, as before) ---
        for (Player p : players) {
            if (p == aimer && p != taya) continue;
            p.handleInput(delta);
        }

        // --- B: Throwers pick up their own slipper ---
        for (Player p : players) {
            if (p.input.bPressed && canPickUpSlipper(p)) p.hasSlipper = true;
        }

        // --- B: Taya picks up the can, or puts it back on its base ---
        if (taya.input.bPressed) {
            if (canTayaPickUpCan()
                && Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                taya.hasCan = true;
            } else if (mode == RoundMode.SCRAMBLE && taya.hasCan
                && Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                taya.hasCan = false;
                can.reset(canBasePosition.x, canBasePosition.y);
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
        for (Player p : players) {
            if (p.input.selectPressed) {
                resetRound(false);
                break;
            }
        }
    }

    private void startAim(Player p) {
        aimer = p;
        aim = Aim.ANGLE;
        angleTimer = 0f;
    }

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    private void update(float delta) {
        for (Player p : players) {
            p.update(delta);
            resolvePlayerCollision(p);
        }

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
        for (Player p : players) {
            if (p == taya || p.hasSlipper) continue;
            boolean moving = p.slipper.velocity.len() > 0;
            updateSlipper(p.slipper, delta);
            if (moving && isCanStanding() && mode != RoundMode.TOSS_FLYING
                && Vector2.dst(p.slipper.position.x, p.slipper.position.y, can.position.x, can.position.y) < 25f) {
                triggerCanHit(p.slipper);
            }
        }

        switch (mode) {
            case THROWING:
                // Everyone threw, nothing hit the can: Taya's turn to toss
                if (nextThrower() == null && aim == Aim.NONE && allSlippersStopped()) {
                    mode = RoundMode.TAYA_TOSS;
                }
                break;

            case TAYA_TOSS:
                break;

            case TOSS_FLYING:
                if (isImpactPending) {
                    impactDelayTimer += delta;
                    if (impactDelayTimer >= 0.6f) {
                        isImpactPending = false;
                        impactDelayTimer = 0f;
                        swapRoles(impactVictim); // Taya's can hit this Thrower's slipper
                    }
                    return;
                }

                if (can.zPosition <= 0 && can.zVelocity <= 0) {
                    for (Player p : players) {
                        if (p == taya || p.hasSlipper) continue;
                        if (Vector2.dst(can.position.x, can.position.y, p.slipper.position.x, p.slipper.position.y) < 30f) {
                            triggerTayaCanHitSlipper(p);
                            return;
                        }
                    }
                    if (can.velocity.len() == 0) {
                        mode = RoundMode.SCRAMBLE; // missed: grab your slippers and run!
                    }
                }
                break;

            case SCRAMBLE:
                if (everyoneHome()) {
                    resetRound(true);
                    return;
                }
                checkTaggingLogic();
                break;
        }
    }

    /** Once the can stands on its base again, Taya can tag any Thrower past the line. */
    private void checkTaggingLogic() {
        if (!isCanStandingOnBase()) return;

        for (Player p : players) {
            if (p == taya || p.position.x <= throwLineX) continue;
            if (Vector2.dst(taya.position.x, taya.position.y, p.position.x, p.position.y) < 30f) {
                swapRoles(p);
                return;
            }
        }
    }

    /** {@code newTaya} (a Thrower) becomes Taya; the old Taya becomes a Thrower and throws first. */
    private void swapRoles(Player newTaya) {
        roster.swapWithTaya(newTaya.id);
        resetRound(false);
    }

    private void launchSlipper(Player p) {
        // Once their slipper is thrown, a Thrower may cross the line (until the round resets)
        p.setXBounds(20, screenWidth - 20);
        p.hasThrown = true;
        p.hasSlipper = false;

        p.slipper.position.set(p.position);
        float rad = currentAngle * MathUtils.degreesToRadians;
        float speed = currentPower * 18f;
        p.slipper.velocity.set(MathUtils.cos(rad) * speed, MathUtils.sin(rad) * speed);
    }

    private void triggerCanHit(Slipper slipper) {
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
    }

    private void tayaThrowCan() {
        taya.hasCan = false;
        float rad = currentAngle * MathUtils.degreesToRadians;

        can.position.set(taya.position);
        can.zPosition = CAN_RELEASE_HEIGHT;
        can.zVelocity = 0f;
        can.velocity.set(0f, 0f);

        float powerRatio = MathUtils.clamp(currentPower / 100f, 0f, 1f);
        float throwDistance = CAN_MIN_THROW_DISTANCE
            + (CAN_MAX_THROW_DISTANCE - CAN_MIN_THROW_DISTANCE) * (float) Math.pow(powerRatio, CAN_POWER_CURVE);
        canLandingSpot.set(taya.position).add(MathUtils.cos(rad) * throwDistance, MathUtils.sin(rad) * throwDistance);

        can.tossTo(canLandingSpot.x, canLandingSpot.y, currentPower);
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
            p.setXBounds(20, throwLineX - 20);
            p.hasSlipper = true;
            p.hasThrown = false;
            p.hasCan = false;
            if (i == 0) {
                p.position.set(throwLineX - THROWER_SPAWN_BEHIND_LINE, screenHeight * 0.5f);
            } else {
                float[] spot = WAITING_THROWER_OFFSETS[(i - 1) % WAITING_THROWER_OFFSETS.length];
                p.position.set(throwLineX - THROWER_SPAWN_BEHIND_LINE + spot[0], screenHeight * 0.5f + spot[1]);
            }
            p.slipper.reset(p.position.x, p.position.y);
        }

        taya.setXBounds(20, screenWidth - 20);
        taya.position.set(canBasePosition.x + TAYA_SPAWN_RIGHT_OF_BASE, canBasePosition.y);
        taya.hasCan = false;
        taya.hasSlipper = false;
        taya.hasThrown = false;
        taya.slipper.reset(taya.position.x, taya.position.y);

        can.reset(canBasePosition.x, canBasePosition.y);

        angleTimer = 0f;
        powerTimer = 0f;
        isImpactPending = false;
        impactDelayTimer = 0f;
        impactVictim = null;
    }

    private void renderUIOverlays() {
        if (aim == Aim.ANGLE) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(aimer == taya ? Color.RED : Color.BLUE);
            float rad = currentAngle * MathUtils.degreesToRadians;
            shapeRenderer.line(
                aimer.position.x,
                aimer.position.y,
                aimer.position.x + MathUtils.cos(rad) * 65f,
                aimer.position.y + MathUtils.sin(rad) * 65f
            );
            shapeRenderer.end();
        }

        if (aim == Aim.POWER) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            shapeRenderer.setColor(Color.LIGHT_GRAY);
            shapeRenderer.rect(aimer.position.x - 25, aimer.position.y + 30, 50, 10);

            shapeRenderer.setColor(aimer == taya ? Color.RED : Color.GREEN);
            shapeRenderer.rect(aimer.position.x - 25, aimer.position.y + 30, 50 * (currentPower / 100f), 10);
            shapeRenderer.end();
        }
    }

    private void renderDebugCollision() {
        boolean showPlayerWalls = debugView != 2;
        boolean showCanWalls = debugView != 1;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        if (showPlayerWalls) {
            shapeRenderer.setColor(1f, 0f, 0f, 0.35f);
            for (Shape2D wall : playerWalls.shapes()) fillDebugShape(wall);
        }
        if (showCanWalls) {
            shapeRenderer.setColor(1f, 1f, 0f, 0.35f);
            for (Shape2D wall : canWalls.shapes()) fillDebugShape(wall);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        shapeRenderer.setColor(Color.MAGENTA);
        shapeRenderer.rect(ringX, ringY,
            upperRingTexture.getWidth(),
            upperRingTexture.getHeight());

        if (showPlayerWalls) {
            shapeRenderer.setColor(Color.RED);
            for (Shape2D wall : playerWalls.shapes()) drawDebugShape(wall);
        }
        if (showCanWalls) {
            shapeRenderer.setColor(Color.YELLOW);
            for (Shape2D wall : canWalls.shapes()) drawDebugShape(wall);
        }

        shapeRenderer.setColor(Color.CYAN);
        for (Player p : players) {
            shapeRenderer.rect(p.position.x - PLAYER_HITBOX_W / 2f,
                p.position.y + PLAYER_HITBOX_OFFSET_Y - PLAYER_HITBOX_H / 2f, PLAYER_HITBOX_W, PLAYER_HITBOX_H);
        }

        shapeRenderer.setColor(Color.ORANGE);
        shapeRenderer.rect(can.position.x + CAN_HITBOX_OFFSET_X - CAN_HITBOX_W / 2f,
            can.position.y + CAN_HITBOX_OFFSET_Y - CAN_HITBOX_H / 2f, CAN_HITBOX_W, CAN_HITBOX_H);

        shapeRenderer.end();
    }

    private void fillDebugShape(Shape2D shape) {
        if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        } else if (shape instanceof Polygon) {
            float[] v = ((Polygon) shape).getTransformedVertices();
            for (int i = 2; i + 3 < v.length; i += 2) {
                shapeRenderer.triangle(v[0], v[1], v[i], v[i + 1], v[i + 2], v[i + 3]);
            }
        }
    }

    private void drawDebugShape(Shape2D shape) {
        if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        } else if (shape instanceof Polygon) {
            shapeRenderer.polygon(((Polygon) shape).getTransformedVertices());
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f, 0);
        camera.update();
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (map != null) map.dispose();
        if (upperRingTexture != null) upperRingTexture.dispose();
        if (font != null) font.dispose();
        if (playerSheet != null) playerSheet.dispose();
        if (playerSlipperSheet != null) playerSlipperSheet.dispose();
        if (playerCanSheet != null) playerCanSheet.dispose();
        if (canSheet != null) canSheet.dispose();
    }
}