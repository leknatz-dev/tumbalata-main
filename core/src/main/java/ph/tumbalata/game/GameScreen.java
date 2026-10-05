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
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Comparator;

/**
 * Draws a match. All the rules (roles, round flow, tagging, scoring, timer) live in {@link Match};
 * this screen loads the assets, feeds the match one frame at a time and draws what it reports.
 */
public class GameScreen implements Screen {
    private static final float VIEW_X = 0f;
    private static final float VIEW_Y = -64f;
    private static final float VIEW_W = 1408f;
    private static final float VIEW_H = 768f;

    private static final boolean SHOW_THROW_LINE = true;

    private static final String MAP_FILE = "MAPCOLLISION.tmx";
    private static final String PLAYER_COLLISION_LAYER = "collision1";
    private static final String CAN_COLLISION_LAYER = "collision2";
    private static final String UPPER_RING_FILE = "upperring.png";
    private static final boolean CAN_BLOCKED_BY_PLAYER_WALLS = true;

    private static final float RING_X = 0f;
    private static final float RING_Y = -64f;

    private static final float NAME_TAG_Y = 34f;

    // --- MATCH ---
    private static final float MATCH_TIME_SECONDS = 60f;
    private static final int VICTORY_WINDOW_W = 700;
    private static final int VICTORY_WINDOW_H = 500;

    // --- SCORE POP-UPS ("+3" floating up from a player) ---
    private static final float POPUP_TIME = 1.0f;
    private static final float POPUP_RISE = 40f;

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

    private MapCollision playerWalls;
    private MapCollision canWalls;

    private boolean debugCollision = false;
    private int debugView = 0;
    private float ringX = RING_X;
    private float ringY = RING_Y;

    private final int[] characters;                        // characters[playerId] = picked character
    private final Array<Player> drawOrder = new Array<>(); // players sorted back to front each frame
    private Match match;
    private boolean matchEnded = false;
    private Audio audio;

    private static final class Popup {
        Player player;
        String text;
        float age;
    }
    private final Array<Popup> popups = new Array<>();

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

        InputManager inputs = ((TumbalataGame) Gdx.app.getApplicationListener()).input();

        map = new TmxMapLoader().load(MAP_FILE);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f, spriteBatch);

        playerWalls = MapCollision.fromLayer(map, PLAYER_COLLISION_LAYER);
        canWalls = MapCollision.fromLayer(map, CAN_COLLISION_LAYER);
        MapCollision canBlockers = CAN_BLOCKED_BY_PLAYER_WALLS ? MapCollision.combine(canWalls, playerWalls) : canWalls;

        upperRingTexture = loadPixelTexture(UPPER_RING_FILE);
        playerSheet = loadPixelTexture("16x16 Walk-Sheet.png");
        playerSlipperSheet = loadPixelTexture("16x16 Walkwithslipper.png");
        playerCanSheet = loadPixelTexture("Walkwithcan.png");
        canSheet = loadPixelTexture("can_spin_sheet.png");

        Can can = new Can(Match.CAN_BASE_X, Match.CAN_BASE_Y, canSheet, 4, 1, 0.08f);

        Array<Player> players = new Array<>();
        for (int id = 0; id < characters.length; id++) {
            // Position, speed and bounds are set by the match from the player's role
            Player p = new Player(id, characters[id], 0f, 0f, GameConstants.PLAYER_SPEED, inputs.player(id),
                20f, Match.WORLD_WIDTH - 20f, 20f, Match.WORLD_HEIGHT - 20f,
                playerSheet, playerSlipperSheet, playerCanSheet);
            p.slipper = new Slipper(0f, 0f);
            p.slipper.color.set(Color.BROWN).lerp(p.slotColor(), 0.5f);
            players.add(p);
            drawOrder.add(p);
        }

        audio = ((TumbalataGame) Gdx.app.getApplicationListener()).audio();
        audio.play(Audio.Sfx.GAME_START);
        audio.playMusic(Audio.Track.GAME);

        match = new Match(players, can, playerWalls, canBlockers, MATCH_TIME_SECONDS);
        match.setEvents(new Match.Events() {
            @Override
            public void scored(Player player, int points) {
                Popup popup = new Popup();
                popup.player = player;
                popup.text = "+" + points;
                popups.add(popup);
                audio.play(Audio.Sfx.SCORE);
            }

            @Override
            public void slipperThrown(Player thrower) {
                audio.playVaried(Audio.Sfx.THROW);
            }

            @Override
            public void canTossed(Player taya) {
                audio.playVaried(Audio.Sfx.THROW);
            }

            @Override
            public void canKnocked(Player thrower) {
                audio.playVaried(Audio.Sfx.CAN_HIT);
            }

            @Override
            public void tossHitSlipper(Player taya, Player victim) {
                audio.playVaried(Audio.Sfx.CAN_HIT);
            }

            @Override
            public void tagged(Player taya, Player victim) {
                audio.play(Audio.Sfx.TAG);
            }
        });
    }

    private static Texture loadPixelTexture(String file) {
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    @Override
    public void render(float delta) {
        viewport.apply();

        handleScreenKeys();
        if (!matchEnded) {
            match.update(delta);
            if (match.isOver()) endMatch();
        }
        for (int i = popups.size - 1; i >= 0; i--) {
            popups.get(i).age += delta;
            if (popups.get(i).age >= POPUP_TIME) popups.removeIndex(i);
        }

        Player taya = match.taya();
        Can can = match.can();

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
            shapeRenderer.rectLine(Match.THROW_LINE_X, VIEW_Y, Match.THROW_LINE_X, VIEW_Y + VIEW_H, 4);
        }

        shapeRenderer.setColor(Color.LIGHT_GRAY);
        shapeRenderer.circle(match.canBase().x, match.canBase().y, 16f);

        can.renderShadow(shapeRenderer);
        for (Player p : match.players()) p.renderShadow(shapeRenderer);

        for (Player p : match.players()) {
            if (match.isSlipperOut(p)) p.slipper.render(shapeRenderer);
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
        drawPrompts();
        drawPopups();
        drawHud();

        spriteBatch.end();

        renderAimOverlay();

        if (debugCollision) {
            renderDebugCollision();
        }
    }

    private void handleScreenKeys() {
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
    }

    private void endMatch() {
        matchEnded = true;
        audio.play(Audio.Sfx.GAME_END);
        audio.stopMusic();
        if (Gdx.app.getApplicationListener() instanceof TumbalataGame) {
            TumbalataGame tumbalata = (TumbalataGame) Gdx.app.getApplicationListener();
            tumbalata.changeScreen(new VictoryScreen(tumbalata, match.scores(), characters),
                VICTORY_WINDOW_W, VICTORY_WINDOW_H);
        }
    }

    // ------------------------------------------------------------------
    // HUD and labels (inside spriteBatch.begin/end)
    // ------------------------------------------------------------------

    private static final Comparator<Player> BACK_TO_FRONT = (a, b) -> Float.compare(b.position.y, a.position.y);

    /** "P1" over each player in their slot colour; the Taya is marked, Throwers who already threw are dimmed. */
    private void drawNameTags() {
        Player taya = match.taya();
        font.getData().setScale(1f);
        for (Player p : match.players()) {
            String text = (p == taya) ? p.label() + " TAYA" : p.label();
            Color c = p.slotColor();
            font.setColor(c.r, c.g, c.b, (p != taya && p.hasThrown) ? 0.7f : 1f);
            font.draw(spriteBatch, text, p.position.x - 50f, p.position.y + NAME_TAG_Y, 100f, Align.center, false);
        }
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    private void drawPrompts() {
        Can can = match.can();
        for (Player p : match.players()) {
            if (match.canPickUpSlipper(p)) {
                font.draw(spriteBatch, p.label() + " [B] Pick Up Slipper", p.slipper.position.x - 60f, p.slipper.position.y + 25f);
            }
        }
        if (match.canTayaPickUpCan() && match.isTayaNearCan()) {
            font.draw(spriteBatch, "[B] Pick Up Can", can.position.x - 35f, can.position.y + 30f);
        }
        if (match.canTayaPlaceCan()) {
            font.draw(spriteBatch, "[B] Place Can at Base", match.canBase().x - 45f, match.canBase().y + 35f);
        }
    }

    private void drawPopups() {
        font.getData().setScale(1.6f);
        for (Popup popup : popups) {
            float t = popup.age / POPUP_TIME;
            Color c = popup.player.slotColor();
            font.setColor(c.r, c.g, c.b, 1f - t);
            Player p = popup.player;
            font.draw(spriteBatch, popup.text, p.position.x - 30f, p.position.y + NAME_TAG_Y + 22f + t * POPUP_RISE,
                60f, Align.center, false);
        }
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    /** Timer and status line at the top centre, scores at the top left. */
    private void drawHud() {
        float timerLeft = match.timeLeft();
        int totalSeconds = MathUtils.ceil(timerLeft);
        String timerText = (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
        font.getData().setScale(2.2f);
        font.setColor(timerLeft <= 10f ? Color.SCARLET : Color.WHITE);
        font.draw(spriteBatch, timerText, VIEW_X + VIEW_W / 2f - 100f, VIEW_Y + VIEW_H - 14f, 200f, Align.center, false);
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);

        font.draw(spriteBatch, statusText(), VIEW_X + VIEW_W / 2f - 300f, VIEW_Y + VIEW_H - 54f, 600f, Align.center, false);

        font.getData().setScale(1.5f);
        float y = VIEW_Y + VIEW_H - 16f;
        for (Player p : match.players()) {
            font.setColor(p.slotColor());
            font.draw(spriteBatch, p.label() + (p == match.taya() ? " (Taya)" : "") + "  " + p.score, VIEW_X + 20f, y);
            y -= 26f;
        }
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    /** One line under the timer saying what the round is waiting for. */
    private String statusText() {
        switch (match.mode()) {
            case THROWING: {
                Player up = match.nextThrower();
                return up != null ? up.label() + ", your throw!" : "Waiting for the slippers to stop...";
            }
            case TAYA_TOSS:
                return "Everyone missed! " + match.taya().label() + " (Taya): pick up the can and toss it at a slipper";
            case TOSS_FLYING:
                return "";
            case SCRAMBLE:
            default:
                return match.isCanStandingOnBase()
                    ? "The can is up: Taya can tag anyone past the line with a slipper!"
                    : "Grab your slipper and get back behind the line!";
        }
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    private void renderAimOverlay() {
        Player aimer = match.aimer();
        if (aimer == null) return;
        boolean isTaya = aimer == match.taya();

        if (match.aim() == Match.Aim.ANGLE) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(isTaya ? Color.RED : Color.BLUE);
            float rad = match.angle() * MathUtils.degreesToRadians;
            shapeRenderer.line(
                aimer.position.x,
                aimer.position.y,
                aimer.position.x + MathUtils.cos(rad) * 65f,
                aimer.position.y + MathUtils.sin(rad) * 65f
            );
            shapeRenderer.end();
        } else if (match.aim() == Match.Aim.POWER) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            shapeRenderer.setColor(Color.LIGHT_GRAY);
            shapeRenderer.rect(aimer.position.x - 25, aimer.position.y + 30, 50, 10);

            shapeRenderer.setColor(isTaya ? Color.RED : Color.GREEN);
            shapeRenderer.rect(aimer.position.x - 25, aimer.position.y + 30, 50 * (match.power() / 100f), 10);
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
        shapeRenderer.rect(0f, 0f, Match.WORLD_WIDTH, Match.WORLD_HEIGHT);
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
        for (Player p : match.players()) {
            shapeRenderer.rect(p.position.x - Match.PLAYER_HITBOX_W / 2f,
                p.position.y + Match.PLAYER_HITBOX_OFFSET_Y - Match.PLAYER_HITBOX_H / 2f,
                Match.PLAYER_HITBOX_W, Match.PLAYER_HITBOX_H);
        }

        Can can = match.can();
        shapeRenderer.setColor(Color.ORANGE);
        shapeRenderer.rect(can.position.x + Match.CAN_HITBOX_OFFSET_X - Match.CAN_HITBOX_W / 2f,
            can.position.y + Match.CAN_HITBOX_OFFSET_Y - Match.CAN_HITBOX_H / 2f, Match.CAN_HITBOX_W, Match.CAN_HITBOX_H);

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
