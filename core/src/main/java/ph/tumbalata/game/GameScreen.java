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

    private enum GamePhase {
        THROWER_ROAMING,
        THROWER_SELECTING_ANGLE,
        THROWER_SELECTING_POWER,
        SLIPPER_FLYING,
        CAN_HIT_SCRAMBLE,
        TAYA_WAITING_PICKUP,
        TAYA_SELECTING_ANGLE,
        TAYA_SELECTING_POWER,
        TAYA_CAN_FLYING,
        RETRIEVAL_PHASE
    }
    private GamePhase currentPhase = GamePhase.THROWER_ROAMING;

    private float throwLineX, screenWidth, screenHeight;
    private Vector2 canBasePosition;
    // Shortcuts to the players in the two active roles, refreshed by applyRoles() from the roster
    private Player thrower;
    private Player taya;
    private Can can;
    private Slipper slipper;

    private float angleTimer = 0f, currentAngle = 0f;
    private float powerTimer = 0f, currentPower = 0f;

    private Vector2 canLandingSpot = new Vector2();
    private boolean isImpactPending = false;
    private float impactDelayTimer = 0f;

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
        slipper = new Slipper(throwLineX - 50, canBasePosition.y);

        roster = new Roster(characters.length);
        for (int id = 0; id < characters.length; id++) {
            // Position, speed and bounds are set by resetRound() below, from the player's role
            Player p = new Player(id, characters[id], 0f, 0f, GameConstants.PLAYER_SPEED, inputs.player(id),
                20f, screenWidth - 20f, 20f, screenHeight - 20f,
                playerSheet, playerSlipperSheet, playerCanSheet);
            players.add(p);
            drawOrder.add(p);
        }
        resetRound(false);
    }

    /** Points the thrower / taya shortcuts at the roster's players and gives every player their role's speed. */
    private void applyRoles() {
        thrower = players.get(roster.activeThrower());
        taya = players.get(roster.taya());
        for (Player p : players) {
            p.speed = roster.isTaya(p.id) ? GameConstants.TAYA_SPEED : GameConstants.PLAYER_SPEED;
        }
    }

    private boolean isWaitingThrower(Player p) {
        return p != thrower && p != taya;
    }

    private void savePreviousPositions() {
        for (Player p : players) p.prevPosition.set(p.position);
    }

    private void resolvePlayerCollision(Player p) {
        playerWalls.slide(p.position, p.prevPosition.x, p.prevPosition.y,
            PLAYER_HITBOX_W, PLAYER_HITBOX_H, PLAYER_HITBOX_OFFSET_Y);
    }

    private void updateCan(float delta) {
        float oldX = can.position.x;
        float oldY = can.position.y;

        can.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        if (taya.hasCan || can.zPosition > CAN_WALL_MAX_HEIGHT) return;

        canBlockers.sweep(can.position, can.velocity, oldX, oldY,
            CAN_HITBOX_W, CAN_HITBOX_H, CAN_HITBOX_OFFSET_X, CAN_HITBOX_OFFSET_Y, CAN_WALL_BOUNCE);
    }

    private void updateSlipper(float delta) {
        float oldX = slipper.position.x;
        float oldY = slipper.position.y;

        slipper.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        playerWalls.sweep(slipper.position, slipper.velocity, oldX, oldY,
            SLIPPER_HITBOX_W, SLIPPER_HITBOX_H, 0f, 0f, SLIPPER_WALL_BOUNCE);
    }

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

        if (!thrower.hasSlipper) {
            slipper.render(shapeRenderer);
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

        if (!thrower.hasSlipper && Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
            font.draw(spriteBatch, "[E] or [B] Pick Up Slipper", slipper.position.x - 45f, slipper.position.y + 25f);
        }

        if ((currentPhase == GamePhase.TAYA_WAITING_PICKUP || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) && !taya.hasCan) {
            if (Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                font.draw(spriteBatch, "[E] or [B] Pick Up Can", can.position.x - 35f, can.position.y + 30f);
            }
        }

        if ((currentPhase == GamePhase.RETRIEVAL_PHASE || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) && taya.hasCan) {
            if (Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                font.draw(spriteBatch, "[E] or [B] Place Can at Base", canBasePosition.x - 45f, canBasePosition.y + 35f);
            }
        }

        float timerLeft = Math.max(0f, matchTimeLeft);
        int totalSeconds = MathUtils.ceil(timerLeft);
        String timerText = (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
        font.getData().setScale(2.2f);
        font.setColor(timerLeft <= 10f ? Color.SCARLET : Color.WHITE);
        font.draw(spriteBatch, timerText, VIEW_X + VIEW_W / 2f - 100f, VIEW_Y + VIEW_H - 14f, 200f, Align.center, false);
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);

        spriteBatch.end();

        renderUIOverlays();

        if (debugCollision) {
            renderDebugCollision();
        }
    }

    private static final Comparator<Player> BACK_TO_FRONT = (a, b) -> Float.compare(b.position.y, a.position.y);

    /** "P1" over each player in their slot colour; the Taya is marked, and waiting Throwers are dimmed. */
    private void drawNameTags() {
        font.getData().setScale(1f);
        for (Player p : players) {
            String text = (p == taya) ? p.label() + " TAYA" : p.label();
            Color c = p.slotColor();
            font.setColor(c.r, c.g, c.b, isWaitingThrower(p) ? 0.6f : 1f);
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

    private void handleInput(float delta) {
        // Each player brings their own input (keyboard and/or their controller), so roles can swap freely
        PlayerInput throwerIn = thrower.input;
        PlayerInput tayaIn = taya.input;

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

        // --- Movement (keyboard or controller, bounds + collision are handled the same for both) ---
        if (currentPhase == GamePhase.THROWER_ROAMING || currentPhase == GamePhase.CAN_HIT_SCRAMBLE || currentPhase == GamePhase.RETRIEVAL_PHASE) {
            // The active Thrower and the Throwers waiting their turn (who stay behind the line) move in the same phases
            for (Player p : players) {
                if (p != taya) p.handleInput(delta);
            }

            // Thrower picks up the slipper: B
            if (throwerIn.bPressed && !thrower.hasSlipper) {
                if (Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
                    thrower.hasSlipper = true;
                }
            }
        }

        taya.handleInput(delta);

        // Taya picks up the can: B
        if (currentPhase == GamePhase.TAYA_WAITING_PICKUP || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) {
            if (!taya.hasCan && tayaIn.bPressed) {
                if (Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                    taya.hasCan = true;
                }
            }
        }

        // Taya places the can back on its base: B
        if ((currentPhase == GamePhase.CAN_HIT_SCRAMBLE || currentPhase == GamePhase.RETRIEVAL_PHASE) && taya.hasCan) {
            if (tayaIn.bPressed) {
                if (Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                    taya.hasCan = false;
                    can.reset(canBasePosition.x, canBasePosition.y);
                }
            }
        }

        // --- Primary action (aim / power / launch): A from the player whose turn it is ---
        if (currentPhase == GamePhase.THROWER_ROAMING && thrower.hasSlipper && throwerIn.aPressed) {
            currentPhase = GamePhase.THROWER_SELECTING_ANGLE;
            angleTimer = 0f;
        } else if (currentPhase == GamePhase.THROWER_SELECTING_ANGLE && throwerIn.aPressed) {
            currentPhase = GamePhase.THROWER_SELECTING_POWER;
            powerTimer = 0f;
        } else if (currentPhase == GamePhase.THROWER_SELECTING_POWER && throwerIn.aPressed) {
            launchSlipper();
            thrower.hasSlipper = false;
            currentPhase = GamePhase.SLIPPER_FLYING;
        } else if (currentPhase == GamePhase.TAYA_WAITING_PICKUP && taya.hasCan && tayaIn.aPressed) {
            currentPhase = GamePhase.TAYA_SELECTING_ANGLE;
            angleTimer = 0f;
        } else if (currentPhase == GamePhase.TAYA_SELECTING_ANGLE && tayaIn.aPressed) {
            currentPhase = GamePhase.TAYA_SELECTING_POWER;
            powerTimer = 0f;
        } else if (currentPhase == GamePhase.TAYA_SELECTING_POWER && tayaIn.aPressed) {
            tayaThrowCan();
            currentPhase = GamePhase.TAYA_CAN_FLYING;
        }

        // Reset round: Select (any player's pad) or R
        for (Player p : players) {
            if (p.input.selectPressed) {
                resetRound(false);
                break;
            }
        }
    }

    private void update(float delta) {
        for (Player p : players) {
            p.update(delta);
            resolvePlayerCollision(p);
        }

        if (currentPhase == GamePhase.THROWER_SELECTING_ANGLE) {
            angleTimer += delta * 3.5f;
            currentAngle = MathUtils.sin(angleTimer) * 80f;
        } else if (currentPhase == GamePhase.TAYA_SELECTING_ANGLE) {
            angleTimer += delta * 4f;
            currentAngle = (angleTimer * 50f) % 360f;
        } else if (currentPhase == GamePhase.THROWER_SELECTING_POWER || currentPhase == GamePhase.TAYA_SELECTING_POWER) {
            powerTimer += delta * 4f;
            currentPower = ((MathUtils.sin(powerTimer) + 1f) / 2f) * 100f;
        } else if (currentPhase == GamePhase.SLIPPER_FLYING) {
            updateSlipper(delta);

            if (slipper.velocity.len() == 0 && !can.isHit) {
                currentPhase = GamePhase.TAYA_WAITING_PICKUP;
            }

            if (!can.isHit && Vector2.dst(slipper.position.x, slipper.position.y, can.position.x, can.position.y) < 25f) {
                triggerCanHit();
            }
        } else if (currentPhase == GamePhase.CAN_HIT_SCRAMBLE) {
            updateCan(delta);
            updateSlipper(delta);

            if (thrower.hasSlipper && thrower.position.x < throwLineX) {
                resetRound(true);
                return;
            }

            checkTaggingLogic();
        } else if (currentPhase == GamePhase.TAYA_CAN_FLYING) {
            updateCan(delta);

            if (isImpactPending) {
                impactDelayTimer += delta;
                updateSlipper(delta);
                if (impactDelayTimer >= 0.6f) {
                    isImpactPending = false;
                    impactDelayTimer = 0f;
                    swapRoles(thrower); // Taya's can hit the active Thrower's slipper
                }
                return;
            }

            if (can.zPosition <= 0 && can.zVelocity <= 0) {
                float distToSlipper = Vector2.dst(can.position.x, can.position.y, slipper.position.x, slipper.position.y);

                if (distToSlipper < 30f) {
                    triggerTayaCanHitSlipper();
                } else if (can.velocity.len() == 0) {
                    currentPhase = GamePhase.RETRIEVAL_PHASE;
                }
            }
        } else if (currentPhase == GamePhase.RETRIEVAL_PHASE) {
            updateCan(delta);

            if (thrower.hasSlipper && thrower.position.x < throwLineX) {
                resetRound(true);
                return;
            }

            checkTaggingLogic();
        }
    }

    private void checkTaggingLogic() {
        boolean isCanStandingAtBase = !can.isHit && Vector2.dst(can.position.x, can.position.y, canBasePosition.x, canBasePosition.y) < 10f;
        if (!isCanStandingAtBase) return;

        // Any Thrower past the line can be tagged (waiting Throwers are kept behind it, so in practice the active one)
        for (Player p : players) {
            if (p == taya || p.position.x <= throwLineX) continue;
            if (Vector2.dst(taya.position.x, taya.position.y, p.position.x, p.position.y) < 30f) {
                swapRoles(p);
                return;
            }
        }
    }

    /** {@code newTaya} (a Thrower) becomes Taya; the old Taya becomes a Thrower and throws next. */
    private void swapRoles(Player newTaya) {
        roster.swapWithTaya(newTaya.id);
        resetRound(false);
    }

    private void launchSlipper() {
        // Once the slipper is thrown, the Thrower may cross the line to fetch it (until the round resets)
        thrower.setXBounds(20, screenWidth - 20);
        slipper.position.set(thrower.position);
        float rad = currentAngle * MathUtils.degreesToRadians;
        float speed = currentPower * 18f;
        slipper.velocity.set(MathUtils.cos(rad) * speed, MathUtils.sin(rad) * speed);
    }

    private void triggerCanHit() {
        can.isHit = true;
        currentPhase = GamePhase.CAN_HIT_SCRAMBLE;
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

    private void triggerTayaCanHitSlipper() {
        isImpactPending = true;
        impactDelayTimer = 0f;

        float hitAngle = MathUtils.atan2(slipper.position.y - can.position.y, slipper.position.x - can.position.x);
        float knockbackSpeed = 350f;
        slipper.velocity.set(MathUtils.cos(hitAngle) * knockbackSpeed, MathUtils.sin(hitAngle) * knockbackSpeed);

        can.velocity.scl(0.3f);
        can.zVelocity = 120f;
    }

    /**
     * Puts everyone back on their spawn spot for their role.
     * @param nextTurn true when the round ended normally, so the next Thrower in line gets the turn
     */
    private void resetRound(boolean nextTurn) {
        currentPhase = GamePhase.THROWER_ROAMING;

        if (nextTurn) roster.nextTurn();
        applyRoles();

        // Throwers in turn order: the active one at the line, the rest on the waiting spots behind it
        for (int i = 0; i < roster.throwers().size; i++) {
            Player p = players.get(roster.throwers().get(i));
            p.setXBounds(20, throwLineX - 20);
            p.hasSlipper = false;
            p.hasCan = false;
            if (i == 0) {
                p.position.set(throwLineX - THROWER_SPAWN_BEHIND_LINE, screenHeight * 0.5f);
            } else {
                float[] spot = WAITING_THROWER_OFFSETS[(i - 1) % WAITING_THROWER_OFFSETS.length];
                p.position.set(throwLineX - THROWER_SPAWN_BEHIND_LINE + spot[0], screenHeight * 0.5f + spot[1]);
            }
        }

        taya.setXBounds(20, screenWidth - 20);
        taya.position.set(canBasePosition.x + TAYA_SPAWN_RIGHT_OF_BASE, canBasePosition.y);
        taya.hasCan = false;
        taya.hasSlipper = false;

        slipper.reset(thrower.position.x, thrower.position.y);
        can.reset(canBasePosition.x, canBasePosition.y);

        angleTimer = 0f;
        powerTimer = 0f;
        isImpactPending = false;
        impactDelayTimer = 0f;
    }

    private void renderUIOverlays() {
        if (currentPhase == GamePhase.THROWER_SELECTING_ANGLE || currentPhase == GamePhase.TAYA_SELECTING_ANGLE) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Player activePlayer = (currentPhase == GamePhase.THROWER_SELECTING_ANGLE) ? thrower : taya;
            shapeRenderer.setColor(currentPhase == GamePhase.THROWER_SELECTING_ANGLE ? Color.BLUE : Color.RED);
            float rad = currentAngle * MathUtils.degreesToRadians;
            shapeRenderer.line(
                activePlayer.position.x,
                activePlayer.position.y,
                activePlayer.position.x + MathUtils.cos(rad) * 65f,
                activePlayer.position.y + MathUtils.sin(rad) * 65f
            );
            shapeRenderer.end();
        }

        if (currentPhase == GamePhase.THROWER_SELECTING_POWER || currentPhase == GamePhase.TAYA_SELECTING_POWER) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            Player activePlayer = (currentPhase == GamePhase.THROWER_SELECTING_POWER) ? thrower : taya;

            shapeRenderer.setColor(Color.LIGHT_GRAY);
            shapeRenderer.rect(activePlayer.position.x - 25, activePlayer.position.y + 30, 50, 10);

            shapeRenderer.setColor(currentPhase == GamePhase.THROWER_SELECTING_POWER ? Color.GREEN : Color.RED);
            shapeRenderer.rect(activePlayer.position.x - 25, activePlayer.position.y + 30, 50 * (currentPower / 100f), 10);
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