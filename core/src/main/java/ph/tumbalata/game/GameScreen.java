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
    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;

    // --- PAUSE / SETTINGS MENU (Select or Start on a pad, Esc or P on the keyboard) ---
    private static final float PAUSE_FADE_SECONDS = 0.2f; // blur and panel fade in / out

    // --- SCORE POP-UPS ("+3" floating up from a player) ---
    private static final float POPUP_TIME = 1.0f;
    private static final float POPUP_RISE = 40f;

    // --- HIT-STOP, SHAKE AND BLUR (feel only; see CourtEffects / BlurRenderer) ---
    private static final float KNOCK_FREEZE = 0.07f;       // seconds the action freezes when the can is knocked
    private static final float KNOCK_SHAKE_MIN = 3f;       // px, weakest knock
    private static final float KNOCK_SHAKE_MAX = 9f;       // px, full-power knock
    private static final float KNOCK_SHAKE_TIME = 0.35f;
    private static final float CATCH_FREEZE = 0.05f;       // a tag, or Taya's can landing on a slipper
    private static final float CATCH_SHAKE = 4f;
    private static final float CATCH_SHAKE_TIME = 0.25f;
    private static final float SIGN_BLUR_RADIUS = 9f;      // px of blur behind a pop-up sign (0 = off)

    // --- THE CAN WHILE TAYA CARRIES IT (drawing only) ---
    private static final float CARRIED_CAN_Y = -10f;       // can centre relative to Taya's centre: at hand height
    private static final float CARRIED_CAN_SCALE = 0.6f;   // smaller than on the ground, so Taya's head still shows

    // --- TRASH PLACEHOLDERS (art: assets/trash/<Trash.KIND_FILES>.png) ---
    private static final float TRASH_WARNING_RADIUS = 18f;
    private static final float TRASH_SCALE = 1.5f;          // placeholder trash size
    private static final Color[] TRASH_COLORS = {
        new Color(0.98f, 0.85f, 0.25f, 1f), // banana peel
        new Color(0.92f, 0.94f, 0.97f, 1f), // plastic bag
        new Color(0.75f, 0.30f, 0.25f, 1f)  // sardine can
    };

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
    private Audio audio;
    private TumbalataGame game;
    private Signs signs;
    private CourtEffects effects;
    private BlurRenderer blur;
    private final Texture[] trashTextures = new Texture[Trash.KINDS];
    private Texture dogTexture, poopTexture; // optional art: assets/street/dog.png (facing right), poop.png

    /** INTRO: waiting for the transition + "GAME START!" sign. OUTRO: "GOOD JOB!" before the victory screen. */
    private enum Stage { INTRO, PLAYING, OUTRO, DONE }
    private Stage stage = Stage.INTRO;
    private boolean introSignShown = false;
    private PauseMenu pauseMenu;
    private boolean paused = false;
    private boolean pauseJustOpened = false; // the button that opened it must not also press a menu button
    private boolean exiting = false;
    private float pauseFade = 0f;          // 0..1, blur and panel opacity
    private final Vector2 mouse = new Vector2();
    private int lastMouseX = -1, lastMouseY = -1;
    private final String[] pauseLabels = new String[PauseMenu.Item.values().length];
    private float clock = 0f; // seconds since the screen opened, for pulsing and spinning effects

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
                Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y,
                playerSheet, playerSlipperSheet, playerCanSheet);
            p.slipper = new Slipper(0f, 0f);
            p.slipper.color.set(Color.BROWN).lerp(p.slotColor(), 0.5f);
            players.add(p);
            drawOrder.add(p);
        }

        game = (TumbalataGame) Gdx.app.getApplicationListener();
        audio = game.audio();
        audio.playMusic(Audio.Track.GAME);
        audio.playBackgroundMusic(Audio.Track.MENU); // the menu song, quietly underneath the game music
        signs = new Signs();
        pauseMenu = new PauseMenu();
        pauseMenu.layout(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f);
        effects = new CourtEffects();
        blur = new BlurRenderer();
        for (int k = 0; k < Trash.KINDS; k++) {
            String file = "trash/" + Trash.KIND_FILES[k] + ".png";
            if (Gdx.files.internal(file).exists()) trashTextures[k] = loadPixelTexture(file);
        }
        if (Gdx.files.internal("street/dog.png").exists()) dogTexture = loadPixelTexture("street/dog.png");
        if (Gdx.files.internal("street/poop.png").exists()) poopTexture = loadPixelTexture("street/poop.png");

        match = new Match(players, can, playerWalls, canBlockers, MATCH_TIME_SECONDS);
        match.setCharacterTraits(true);
        match.setStreetEvents(game.settings().streetEvents());
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
                // Harder hits shake harder (the can flies off at ~0.85x the slipper's speed)
                float strength = MathUtils.clamp(match.can().velocity.len() / 1300f, 0f, 1f);
                effects.hitStop(KNOCK_FREEZE);
                effects.shake(MathUtils.lerp(KNOCK_SHAKE_MIN, KNOCK_SHAKE_MAX, strength), KNOCK_SHAKE_TIME);
                // Someone is out past the line: they have to run. Everyone is safe behind it: laugh at Taya.
                signs.show(match.isAnyThrowerPastLine() ? Signs.Sign.RUN : Signs.Sign.HAHA);
            }

            @Override
            public void tayaTossTurn(Player taya) {
                signs.show(Signs.Sign.MY_TURN);
            }

            @Override
            public void tossMissed(Player taya) {
                signs.show(Signs.Sign.RUN);
            }

            @Override
            public void tossHitSlipper(Player taya, Player victim) {
                audio.playVaried(Audio.Sfx.CAN_HIT);
                signs.show(Signs.Sign.GOTCHA);
                effects.hitStop(CATCH_FREEZE);
                effects.shake(CATCH_SHAKE, CATCH_SHAKE_TIME);
            }

            @Override
            public void tagged(Player taya, Player victim) {
                audio.play(Audio.Sfx.TAG);
                effects.hitStop(CATCH_FREEZE);
                effects.shake(CATCH_SHAKE, CATCH_SHAKE_TIME);
            }

            @Override
            public void trashLanded(Trash trash) {
                audio.playVaried(Audio.Sfx.TRASH_LAND);
                effects.burst(trash.target.x, trash.target.y, 6, 50f);
            }

            @Override
            public void dogArrived(StrayDog dog) {
                audio.play(Audio.Sfx.DOG_BARK);
            }

            @Override
            public void dogPooped(StrayDog dog) {
                effects.burst(dog.spot.x, dog.spot.y, 3, 25f);
            }

            @Override
            public void steppedInPoop(Player player) {
                audio.play(Audio.Sfx.POOP_SQUISH);
                effects.burst(player.position.x, player.position.y + Match.FEET_OFFSET_Y, 6, 40f);
                effects.shake(2f, 0.2f);
            }

            @Override
            public void slipped(Player player, Trash trash) {
                audio.play(Audio.Sfx.SLIP);
                effects.burst(player.position.x, player.position.y + Match.FEET_OFFSET_Y, 8, 70f);
                effects.shake(2.5f, 0.2f);
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
        handleScreenKeys();
        handlePause();
        pauseFade = paused ? Math.min(1f, pauseFade + delta / PAUSE_FADE_SECONDS)
            : Math.max(0f, pauseFade - delta / PAUSE_FADE_SECONDS);

        if (!paused) { // paused: the whole match holds still (timer, players, signs, effects)
            clock += delta;
            signs.update(delta);
            boolean frozen = effects.tickFreeze(delta); // hit-stop: the action holds still for a moment
            switch (stage) {
                case INTRO:
                    // Wait for the screen transition, then "GAME START!"; the match starts when the sign is gone
                    if (!introSignShown && !game.isTransitioning()) {
                        introSignShown = true;
                        signs.show(Signs.Sign.GAME_START);
                        audio.play(Audio.Sfx.GAME_START);
                    } else if (introSignShown && !signs.isShowing()) {
                        stage = Stage.PLAYING;
                    }
                    break;
                case PLAYING:
                    if (frozen) break;
                    match.update(delta);
                    effects.track(match, delta);
                    if (match.isOver()) {
                        stage = Stage.OUTRO;
                        signs.show(Signs.Sign.GOOD_JOB);
                        audio.play(Audio.Sfx.GAME_END);
                        audio.stopMusic();
                    }
                    break;
                case OUTRO:
                    if (!signs.isShowing()) {
                        stage = Stage.DONE;
                        endMatch();
                    }
                    break;
                case DONE:
                    break;
            }
            if (!frozen) effects.update(delta);
            for (int i = popups.size - 1; i >= 0; i--) {
                popups.get(i).age += delta;
                if (popups.get(i).age >= POPUP_TIME) popups.removeIndex(i);
            }
        }

        ScreenUtils.clear(0f, 0f, 0f, 1f);

        // 1. The court (shaken, and blurred behind a pop-up sign)
        boolean offscreen = blur.beginScene();
        if (offscreen) ScreenUtils.clear(0f, 0f, 0f, 1f);
        viewport.apply();
        camera.position.set(VIEW_X + VIEW_W / 2f + effects.shakeX(), VIEW_Y + VIEW_H / 2f + effects.shakeY(), 0);
        camera.update();
        drawCourt();
        camera.position.set(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f, 0);
        camera.update();
        if (offscreen) blur.endScene(Math.max(signs.blurAmount(), pauseFade), SIGN_BLUR_RADIUS);

        // 2. HUD, sign and aim meter on top: steady and sharp
        viewport.apply();
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        drawHud();
        if (pauseFade == 0f) signs.draw(spriteBatch, VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f + 40f); // hidden behind the pause menu
        spriteBatch.end();

        renderAimOverlay();

        if (debugCollision) {
            renderDebugCollision();
        }

        if (pauseFade > 0f) pauseMenu.draw(shapeRenderer, spriteBatch, font, pauseLabels(), pauseFade);
    }

    /** Everything on the court: map, shadows, trash, dust, slippers, players, can, ring and the labels over them. */
    private void drawCourt() {
        Player taya = match.taya();
        Can can = match.can();

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

        drawTrashOnGround();
        drawPoopAndDogShapes();
        if (!effects.hasDustSprite()) effects.drawDust(shapeRenderer);

        if (!taya.hasCan) can.renderShadow(shapeRenderer); // a carried can has no shadow of its own
        for (Player p : match.players()) p.renderShadow(shapeRenderer);

        for (Player p : match.players()) {
            if (match.isSlipperOut(p)) p.slipper.render(shapeRenderer);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        spriteBatch.begin();
        if (effects.hasDustSprite()) effects.drawDust(spriteBatch);
        drawTrashSprites(true);
        drawPoopAndDogSprites();

        // Back to front: things higher on the court are further away and drawn first. The can on the ground is
        // sorted in with the players (by where it touches the ground), so it hides behind or in front of them.
        // A carried can is drawn right after Taya, in Taya's hands.
        drawOrder.sort(BACK_TO_FRONT);
        boolean canDrawn = taya.hasCan;
        for (Player p : drawOrder) {
            if (!canDrawn && p.position.y + Match.FEET_OFFSET_Y < can.position.y) {
                can.render(spriteBatch); // this player stands in front of the can
                canDrawn = true;
            }
            p.render(spriteBatch);
            if (p == taya && taya.hasCan) drawCarriedCan(taya, can);
        }
        if (!canDrawn) can.render(spriteBatch); // in front of everyone

        spriteBatch.draw(upperRingTexture, ringX, ringY);
        drawTrashSprites(false);
        spriteBatch.end();

        drawTrashInAirAndDizzyStars();

        spriteBatch.begin();
        drawNameTags();
        drawPrompts();
        drawPopups();
        spriteBatch.end();
    }

    /** The can lying sideways in Taya's hands. */
    private void drawCarriedCan(Player taya, Can can) {
        TextureRegion canFrame = can.getCurrentFrame();
        float w = can.getWidth();
        float h = can.getHeight();
        float cx = taya.position.x;
        float cy = taya.position.y + CARRIED_CAN_Y;
        float s = CARRIED_CAN_SCALE;
        spriteBatch.draw(canFrame, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, s, s, 90f);
    }

    // ------------------------------------------------------------------
    // Trash (street event)
    // ------------------------------------------------------------------

    /** Warning circles, shadows of trash in the air, and placeholder trash lying on the ground. Inside a Filled
     * shape block with blending on. */
    private void drawTrashOnGround() {
        float pulse = (MathUtils.sin(clock * 14f) + 1f) / 2f;
        for (Trash t : match.trash()) {
            if (!t.isLanded()) {
                // Red warning circle where it will land, pulsing
                shapeRenderer.setColor(1f, 0.15f, 0.1f, 0.25f + 0.25f * pulse);
                shapeRenderer.circle(t.target.x, t.target.y, TRASH_WARNING_RADIUS * (0.85f + 0.15f * pulse), 20);
                // Shadow on the ground, growing as it falls
                if (t.state == Trash.State.FLYING) {
                    float s = 0.4f + 0.6f * t.flightProgress();
                    shapeRenderer.setColor(0f, 0f, 0f, 0.3f * s);
                    shapeRenderer.ellipse(t.x() - 10f * s, t.groundY() - 4f * s, 20f * s, 8f * s);
                }
            } else if (trashTextures[t.kind] == null) {
                drawTrashShape(t, t.x(), t.groundY(), t.alpha());
            }
        }
    }

    // ------------------------------------------------------------------
    // Stray dog and its poop (street event)
    // ------------------------------------------------------------------

    private static final Color DOG_COLOR = new Color(0.62f, 0.42f, 0.24f, 1f);
    private static final Color POOP_COLOR = new Color(0.40f, 0.25f, 0.12f, 1f);

    /** Placeholder poop and dog, drawn on the ground under the players. Inside a Filled shape block, blending on. */
    private void drawPoopAndDogShapes() {
        Vector2 poop = match.poop();
        if (poop != null && poopTexture == null) {
            float x = poop.x, y = poop.y;
            shapeRenderer.setColor(0f, 0f, 0f, 0.25f);
            shapeRenderer.ellipse(x - 11f, y - 4f, 22f, 8f);
            shapeRenderer.setColor(POOP_COLOR);
            shapeRenderer.ellipse(x - 10f, y - 2f, 20f, 8f);   // a little swirl, three layers
            shapeRenderer.ellipse(x - 7f, y + 4f, 14f, 7f);
            shapeRenderer.ellipse(x - 4f, y + 9f, 8f, 6f);
            shapeRenderer.setColor(0.55f, 0.75f, 0.30f, 0.7f); // stink lines drifting up
            for (int k = -1; k <= 1; k += 2) {
                float wave = MathUtils.sin(clock * 4f + k) * 2f;
                shapeRenderer.rect(x + k * 6f + wave, y + 18f + (clock * 10f + k * 3f) % 8f, 2f, 6f);
            }
        }

        StrayDog dog = match.dog();
        if (dog == null || dogTexture != null) return;
        float x = dog.position.x, y = dog.position.y;
        int d = dog.direction;
        boolean squat = dog.state == StrayDog.State.POOPING;
        float step = dog.isMoving() ? MathUtils.sin(clock * 22f) * 3f : 0f;
        float bodyY = y + (squat ? 4f : 8f);

        shapeRenderer.setColor(0f, 0f, 0f, 0.28f);
        shapeRenderer.ellipse(x - 16f, y - 4f, 32f, 8f);
        shapeRenderer.setColor(DOG_COLOR.r * 0.8f, DOG_COLOR.g * 0.8f, DOG_COLOR.b * 0.8f, 1f);
        shapeRenderer.rect(x - 11f + step, y, 3f, bodyY - y + 2f);         // legs
        shapeRenderer.rect(x + 8f - step, y, 3f, bodyY - y + 2f);
        shapeRenderer.setColor(DOG_COLOR);
        shapeRenderer.ellipse(x - 15f, bodyY, 30f, 13f);                    // body
        shapeRenderer.circle(x + d * 15f, bodyY + 13f, 7f, 12);             // head
        shapeRenderer.ellipse(x + d * 20f - 3f, bodyY + 9f, 9f, 5f);       // snout
        shapeRenderer.setColor(DOG_COLOR.r * 0.6f, DOG_COLOR.g * 0.6f, DOG_COLOR.b * 0.6f, 1f);
        shapeRenderer.triangle(x + d * 11f, bodyY + 17f, x + d * 15f, bodyY + 25f, x + d * 18f, bodyY + 17f); // ear
        float tailUp = squat ? 10f : 4f + MathUtils.sin(clock * 18f) * 3f;  // wagging, straight up while pooping
        shapeRenderer.rectLine(x - d * 14f, bodyY + 8f, x - d * 20f, bodyY + 8f + tailUp, 3f);
    }

    /** Dog and poop sprites, when the art exists. Inside batch.begin()/end(), before the players. */
    private void drawPoopAndDogSprites() {
        Vector2 poop = match.poop();
        if (poop != null && poopTexture != null) {
            spriteBatch.draw(poopTexture, poop.x - poopTexture.getWidth() / 2f, poop.y - 4f);
        }
        StrayDog dog = match.dog();
        if (dog != null && dogTexture != null) {
            float w = dogTexture.getWidth(), h = dogTexture.getHeight();
            float bob = dog.isMoving() ? Math.abs(MathUtils.sin(clock * 11f)) * 2f : 0f;
            // the art faces right; flip it when walking left
            spriteBatch.draw(dogTexture, dog.position.x - dog.direction * w / 2f, dog.position.y - 4f + bob,
                dog.direction * w, h);
        }
    }

    /** Placeholder trash: a simple coloured shape per kind, drawn TRASH_SCALE times its base size. */
    private void drawTrashShape(Trash t, float x, float y, float alpha) {
        float k = TRASH_SCALE;
        Color c = TRASH_COLORS[t.kind];
        shapeRenderer.setColor(0f, 0f, 0f, 0.25f * alpha);
        shapeRenderer.ellipse(x - 10f * k, y - 5f * k, 20f * k, 8f * k);
        shapeRenderer.setColor(c.r, c.g, c.b, alpha);
        switch (t.kind) {
            case 0: // banana peel: middle plus three flaps
                shapeRenderer.ellipse(x - 5f * k, y - 3f * k, 10f * k, 7f * k);
                shapeRenderer.triangle(x - 3f * k, y, x - 13f * k, y + 4f * k, x - 4f * k, y + 3f * k);
                shapeRenderer.triangle(x + 3f * k, y, x + 13f * k, y + 4f * k, x + 4f * k, y + 3f * k);
                shapeRenderer.triangle(x - 2f * k, y - 2f * k, x + 2f * k, y - 2f * k, x, y - 9f * k);
                break;
            case 1: // plastic bag
                shapeRenderer.ellipse(x - 9f * k, y - 4f * k, 18f * k, 11f * k);
                shapeRenderer.rect(x - 6f * k, y + 6f * k, 3f * k, 4f * k);
                shapeRenderer.rect(x + 3f * k, y + 6f * k, 3f * k, 4f * k);
                break;
            default: // sardine can
                shapeRenderer.rect(x - 8f * k, y - 3f * k, 16f * k, 7f * k);
                shapeRenderer.setColor(0.8f, 0.8f, 0.82f, alpha);
                shapeRenderer.rect(x - 8f * k, y + 2f * k, 16f * k, 2f * k);
                break;
        }
    }

    /** Trash sprites (when the art exists): landed ones under the players, flying ones on top. */
    private void drawTrashSprites(boolean landed) {
        float previous = spriteBatch.getPackedColor();
        for (Trash t : match.trash()) {
            Texture tex = trashTextures[t.kind];
            if (tex == null || t.state == Trash.State.WARNING || t.isLanded() != landed) continue;
            spriteBatch.setColor(1f, 1f, 1f, t.alpha());
            spriteBatch.draw(tex, t.x() - tex.getWidth() / 2f, t.groundY() + t.height() - tex.getHeight() / 2f);
        }
        spriteBatch.setPackedColor(previous);
    }

    /** Placeholder trash in the air, and spinning stars over stunned players. */
    private void drawTrashInAirAndDizzyStars() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Trash t : match.trash()) {
            if (t.state == Trash.State.FLYING && trashTextures[t.kind] == null) {
                drawTrashShape(t, t.x(), t.groundY() + t.height(), 1f);
            }
        }
        float spin = clock * 6f;
        for (Player p : match.players()) {
            if (!p.isDizzy()) continue;
            for (int k = 0; k < 3; k++) {
                float a = spin + k * MathUtils.PI2 / 3f;
                float sx = p.position.x + MathUtils.cos(a) * 12f;
                float sy = p.position.y + 20f + MathUtils.sin(a) * 4f; // around the head, under the name tag
                shapeRenderer.setColor(1f, 0.9f, 0.2f, 1f);
                shapeRenderer.circle(sx, sy, 3f, 8);
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // ------------------------------------------------------------------
    // Pause / settings menu
    // ------------------------------------------------------------------

    /** Opens the pause menu on Select / Start / Esc / P, and handles its buttons while it is open. */
    private void handlePause() {
        if (exiting || game.isTransitioning() || stage == Stage.OUTRO || stage == Stage.DONE) return;

        if (!paused) {
            if (pausePressed()) {
                paused = true;
                pauseJustOpened = true;
                pauseMenu.reset();
                audio.play(Audio.Sfx.UI_CONFIRM);
            }
            return;
        }
        if (pauseJustOpened) { // same press: don't let it also resume or press a button
            pauseJustOpened = false;
            return;
        }

        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        boolean mouseMoved = Gdx.input.getX() != lastMouseX || Gdx.input.getY() != lastMouseY;
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();

        PauseMenu.Item item = pauseMenu.handle(game.input().menu(), mouse, mouseMoved, Gdx.input.justTouched(), audio);
        if (item == null) return;
        switch (item) {
            case RESUME:
                paused = false;
                audio.play(Audio.Sfx.UI_BACK);
                break;
            case RESTART_ROUND:
                match.restartRound();
                popups.clear();
                paused = false;
                audio.play(Audio.Sfx.UI_CONFIRM);
                break;
            case SOUND:
                audio.toggleMute();
                audio.play(Audio.Sfx.UI_CONFIRM); // silent when it just muted, which is the point
                break;
            case STREET_EVENTS:
                boolean on = !game.settings().streetEvents();
                game.settings().setStreetEvents(on);
                match.setStreetEvents(on);
                audio.play(Audio.Sfx.UI_CONFIRM);
                break;
            case EXIT:
                exiting = true;
                audio.play(Audio.Sfx.UI_BACK);
                audio.playBackgroundMusic(null);
                game.changeScreen(new MainMenuScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
                break;
        }
    }

    /** Select or Start on any player's pad (R on the keyboard is Select for P1 / P2), or Esc / P. */
    private boolean pausePressed() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.P)) return true;
        for (int i = 0; i < match.players().size; i++) {
            PlayerInput in = match.players().get(i).input;
            if (in.selectPressed || in.startPressed) return true;
        }
        return false;
    }

    private String[] pauseLabels() {
        pauseLabels[PauseMenu.Item.RESUME.ordinal()] = "RESUME";
        pauseLabels[PauseMenu.Item.RESTART_ROUND.ordinal()] = "RESTART ROUND";
        pauseLabels[PauseMenu.Item.SOUND.ordinal()] = "SOUND: " + (audio.isMuted() ? "OFF" : "ON");
        pauseLabels[PauseMenu.Item.STREET_EVENTS.ordinal()] = "STREET EVENTS: " + (game.settings().streetEvents() ? "ON" : "OFF");
        pauseLabels[PauseMenu.Item.EXIT.ordinal()] = "EXIT TO MENU";
        return pauseLabels;
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

    /** Time is up and the "GOOD JOB!" sign has played: on to the victory screen. */
    private void endMatch() {
        if (Gdx.app.getApplicationListener() instanceof TumbalataGame) {
            TumbalataGame tumbalata = (TumbalataGame) Gdx.app.getApplicationListener();
            tumbalata.changeScreen(new VictoryScreen(tumbalata, match.scores(), characters, Awards.compute(match.stats())),
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
            String role = p == match.taya() ? " (Taya)" : "";
            font.draw(spriteBatch, p.label() + " " + Characters.TRAITS[p.characterIndex] + role + "  " + p.score, VIEW_X + 20f, y);
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
        // player movement limits (the collision1 walls are the real court edges)
        shapeRenderer.rect(Match.PLAY_MIN_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_X - Match.PLAY_MIN_X, Match.PLAY_MAX_Y - Match.PLAY_MIN_Y);
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
        if (blur != null) blur.resize();
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
        if (signs != null) signs.dispose();
        if (audio != null) audio.playBackgroundMusic(null);
        if (effects != null) effects.dispose();
        if (blur != null) blur.dispose();
        for (Texture t : trashTextures) if (t != null) t.dispose();
        if (dogTexture != null) dogTexture.dispose();
        if (poopTexture != null) poopTexture.dispose();
    }
}
