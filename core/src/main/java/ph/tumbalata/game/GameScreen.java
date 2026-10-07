package ph.tumbalata.game;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
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

    private static final String MAP_FILE = "MAPCOLLISION.tmx";
    private static final String PLAYER_COLLISION_LAYER = "collision1";
    private static final String CAN_COLLISION_LAYER = "collision2";
    private static final String UPPER_RING_FILE = "upperring.png";
    private static final boolean CAN_BLOCKED_BY_PLAYER_WALLS = true;

    private static final float RING_X = 0f;
    private static final float RING_Y = -64f;

    private static final float NAME_TAG_Y = 34f;

    // --- MATCH ---
    // Centre of the court's centre circle in the art (world units): the can's base when the map has no "can base" layer
    private static final float COURT_CENTRE_X = 704f;
    private static final float COURT_CENTRE_Y = 177f;
    private static final float MATCH_TIME_SECONDS = 30f;
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
    private static final float SMALL_ART_SCALE = 2f;        // 16 px art (trash, slipper) drawn this many times bigger
    private static final float WARNING_SIGN_SCALE = 1f;
    private static final Color[] TRASH_COLORS = {
        new Color(0.98f, 0.85f, 0.25f, 1f), // banana
        new Color(0.62f, 0.45f, 0.28f, 1f), // box
        new Color(0.85f, 0.18f, 0.15f, 1f)  // apple
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
    private Texture playerSlipperOverlay; // just the held slipper, tinted in each player's colour
    private Texture playerCanSheet;
    private Texture canSheet;

    private MapCollision playerWalls;
    private MapCollision canWalls;

    private boolean debugCollision = false;
    private int debugView = 0;
    private float ringX = RING_X;
    private float ringY = RING_Y;

    private final int[] characters;                        // characters[playerId] = picked character
    private final String[] names;                          // names[playerId]
    private final Array<Player> drawOrder = new Array<>(); // players sorted back to front each frame
    private Match match;
    private Audio audio;
    private TumbalataGame game;
    private Signs signs;
    private CourtEffects effects;
    private BlurRenderer blur;
    private final Texture[] trashTextures = new Texture[Trash.KINDS];
    private Texture dogTexture, poopTexture; // optional art: assets/street/dog.png (facing right), poop.png
    private Animation<TextureRegion> dogWalk, dogSit;
    private static final int DOG_COLUMNS = 8, DOG_ROWS = 9;
    private static final int DOG_WALK_ROW = 4, DOG_SIT_ROW = 1; // 0-based: the 5th and 2nd rows
    private static final float DOG_SCALE = 1f;
    private static final float DOG_FEET_IN_FRAME = 6f;   // px from the frame's bottom up to the dog's feet
    private Texture slipperTexture; // optional art: assets/slipper.png, drawn white-on-transparent and tinted per player
    private Texture warningTexture; // optional art: assets/warning_sign.png, over each spot where trash will land
    private Texture arrowDefault, arrowSniper; // optional art: assets/arrow_default.png, arrow_sniper.png (white, 45 degrees)
    private static final float ARROW_SCALE = 2f;
    // Where each arrow's tail is in its image, in pixels from the bottom-left (it turns around this point)
    private static final float ARROW_DEFAULT_TAIL_X = 6f, ARROW_DEFAULT_TAIL_Y = 6f;
    private static final float ARROW_SNIPER_TAIL_X = 0f, ARROW_SNIPER_TAIL_Y = 0f;

    /** INTRO: waiting for the transition + "GAME START!" sign. OUTRO: "GOOD JOB!" before the victory screen. */
    private enum Stage { DIALOGUE, MANO, MANO_RESULT, CAN_SPOT, INTRO, PLAYING, OUTRO, DONE }
    private Stage stage = Stage.DIALOGUE;
    private DialogueBox dialogue;    // the kids calling each other to play (assets/dialogue/intro.txt), then the mano
    private float dialogueFade = 0f; // 0..1, the dialogue box and the blur behind it
    private ControlHints hints;
    private Mano mano;           // picks the first Taya before GAME START (MANO -> "NAME IS TAYA!" -> INTRO)
    private float manoFade = 0f; // 0..1, the mano widget and the blur behind it
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
    private Player pendingTurn; // "NAME THROW!" waiting for the current sign to finish
    private float signFreeTime = 0f;
    private static final float TURN_SIGN_GAP = 0.5f; // seconds of no other sign (and no hit-stop) before "NAME THROW!"

    private static final class Popup {
        Player player;
        String text;
        float age;
    }
    private final Array<Popup> popups = new Array<>();

    /** @param characters one picked character per player (2 to 4 players); characters[0] is Player 1's */
    public GameScreen(int[] characters) {
        this(characters, null);
    }

    /** @param names each player's name (index 0 = Player 1), or null for "P1"..."P4" */
    public GameScreen(int[] characters, String[] names) {
        if (characters.length < 2 || characters.length > InputManager.MAX_PLAYERS) {
            throw new IllegalArgumentException("2 to " + InputManager.MAX_PLAYERS + " players, got " + characters.length);
        }
        this.characters = new int[characters.length];
        for (int i = 0; i < characters.length; i++) {
            this.characters[i] = MathUtils.clamp(characters[i], 0, Characters.COUNT - 1);
        }
        this.names = new String[characters.length];
        for (int i = 0; i < characters.length; i++) {
            this.names[i] = (names != null && i < names.length && names[i] != null) ? names[i] : GameSettings.defaultName(i);
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
        font = Fonts.create();
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
        Texture[] slipperSheets = SpriteSheets.splitPureWhite("16x16 Walkwithslipper.png"); // body + the white slipper on its own
        playerSlipperSheet = slipperSheets[0];
        playerSlipperOverlay = slipperSheets[1];
        playerCanSheet = loadPixelTexture("Walkwithcan.png");
        canSheet = loadPixelTexture("can_spin_sheet.png");

        Can can = new Can(Match.CAN_BASE_X, Match.CAN_BASE_Y, canSheet, 4, 1, 0.08f);

        Array<Player> players = new Array<>();
        for (int id = 0; id < characters.length; id++) {
            // Position, speed and bounds are set by the match from the player's role
            Player p = new Player(id, characters[id], 0f, 0f, GameConstants.PLAYER_SPEED, inputs.player(id),
                Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y,
                playerSheet, playerSlipperSheet, playerCanSheet);
            p.name = names[id];
            p.setSlipperOverlay(playerSlipperOverlay);
            p.slipper = new Slipper(0f, 0f);
            p.slipper.color.set(Color.BROWN).lerp(p.color(), 0.5f);
            players.add(p);
            drawOrder.add(p);
        }

        game = (TumbalataGame) Gdx.app.getApplicationListener();
        audio = game.audio();
        audio.playMusic(Audio.Track.GAME);
        audio.playBackgroundMusic(Audio.Track.MENU); // the menu song, quietly underneath the game music
        signs = new Signs(audio);
        mano = new Mano(players.size, new java.util.Random());
        hints = new ControlHints();
        dialogue = loadIntroDialogue();
        if (dialogue.isFinished()) stage = Stage.MANO; // no script for this many players: straight to the mano
        if (Gdx.files.internal("mano/back.png").exists()) manoBack = loadPixelTexture("mano/back.png");
        if (Gdx.files.internal("mano/select.png").exists()) manoSelect = loadPixelTexture("mano/select.png");
        if (Gdx.files.internal("mano/hand_fist.png").exists()) handFist = loadPixelTexture("mano/hand_fist.png");
        if (Gdx.files.internal("mano/hand_up.png").exists()) handUp = loadPixelTexture("mano/hand_up.png");
        if (Gdx.files.internal("mano/hand_down.png").exists()) handDown = loadPixelTexture("mano/hand_down.png");
        pauseMenu = new PauseMenu();
        pauseMenu.layout(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f);
        effects = new CourtEffects();
        blur = new BlurRenderer();
        for (int k = 0; k < Trash.KINDS; k++) {
            String file = "trash/" + Trash.KIND_FILES[k] + ".png";
            if (Gdx.files.internal(file).exists()) trashTextures[k] = loadPixelTexture(file);
        }
        if (Gdx.files.internal("street/dog.png").exists()) dogTexture = loadPixelTexture("street/dog.png");
        if (dogTexture != null) {
            // street/dog.png: 8 columns x 9 rows of frames; row 5 = walking, row 2 = sitting (used while it poops)
            TextureRegion[][] cells = TextureRegion.split(dogTexture, dogTexture.getWidth() / DOG_COLUMNS, dogTexture.getHeight() / DOG_ROWS);
            dogWalk = new Animation<>(0.09f, cells[DOG_WALK_ROW]);
            dogSit = new Animation<>(0.15f, cells[DOG_SIT_ROW]);
        }
        if (Gdx.files.internal("street/poop.png").exists()) poopTexture = loadPixelTexture("street/poop.png");
        if (Gdx.files.internal("slipper.png").exists()) slipperTexture = loadPixelTexture("slipper.png");
        if (Gdx.files.internal("warning_sign.png").exists()) warningTexture = loadPixelTexture("warning_sign.png");
        if (Gdx.files.internal("yellowbutton.png").exists()) buttonA = loadPixelTexture("yellowbutton.png");
        if (Gdx.files.internal("redbutton.png").exists()) buttonB = loadPixelTexture("redbutton.png");
        if (Gdx.files.internal("arrow_default.png").exists()) arrowDefault = loadPixelTexture("arrow_default.png");
        if (Gdx.files.internal("arrow_sniper.png").exists()) arrowSniper = loadPixelTexture("arrow_sniper.png");

        match = new Match(players, can, playerWalls, canBlockers, MATCH_TIME_SECONDS);
        Vector2 canBase = CourtLine.canBaseFromMap(map); // the "can base" layer in Tiled, if there is one
        if (canBase == null) canBase = new Vector2(COURT_CENTRE_X, COURT_CENTRE_Y); // otherwise the centre circle
        match.setCanBase(canBase.x, canBase.y);
        match.setCourt(CourtLine.fromMap(map, Match.THROW_LINE_X)); // the court line and throw area drawn in Tiled
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
                // Someone past the line: RUN! First knock this round with everyone safe: HAHA!. Another knock in the same
                // round with everyone safe: the streak sign
                if (match.isAnyThrowerPastLine()) signs.show(Signs.Sign.RUN);
                else if (match.knocksThisRound() > 1) {
                    signs.show(Signs.Sign.STREAK, "STREAK x" + match.knocksThisRound() + "!", Signs.Sign.STREAK.color);
                } else signs.show(Signs.Sign.HAHA);
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
                signs.show(Signs.Sign.TAGGED);
                effects.hitStop(CATCH_FREEZE);
                effects.shake(CATCH_SHAKE, CATCH_SHAKE_TIME);
            }

            @Override
            public void trashLanded(Trash trash) {
                audio.playVaried(Audio.Sfx.TRASH_LAND);
                effects.burst(trash.target.x, trash.target.y, 6, 50f);
            }

            @Override
            public void canSpotChosen(Vector2 spot) {
                audio.play(Audio.Sfx.UI_CONFIRM);
                effects.burst(spot.x, spot.y, 6, 40f);
                stage = Stage.INTRO; // GAME START
            }

            @Override
            public void throwerTurn(Player thrower) {
                pendingTurn = thrower; // shown as soon as no other sign is up
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
        dialogueFade = stage == Stage.DIALOGUE ? Math.min(1f, dialogueFade + delta / PAUSE_FADE_SECONDS)
            : Math.max(0f, dialogueFade - delta / PAUSE_FADE_SECONDS);
        manoFade = stage == Stage.MANO ? Math.min(1f, manoFade + delta / PAUSE_FADE_SECONDS)
            : Math.max(0f, manoFade - delta / PAUSE_FADE_SECONDS);

        if (!paused) { // paused: the whole match holds still (timer, players, signs, effects)
            clock += delta;
            signs.update(delta);
            boolean frozen = effects.tickFreeze(delta); // hit-stop: the action holds still for a moment
            // How long the screen has been free of signs (and not frozen): the turn sign waits for a short gap
            signFreeTime = (signs.isShowing() || frozen) ? 0f : signFreeTime + delta;
            switch (stage) {
                case DIALOGUE:
                    if (game.isTransitioning()) break;
                    dialogue.update(delta, game.input().menu().confirm, anyoneHolds());
                    if (dialogue.isFinished()) stage = Stage.MANO;
                    break;
                case MANO:
                    if (!game.isTransitioning()) updateMano(delta);
                    break;
                case MANO_RESULT:
                    if (signs.isShowing()) break; // "NAME IS TAYA!" first
                    if (match.court().hasCanZone()) { // Taya chooses where the can stands for this match
                        match.beginCanPlacement();
                        signs.show(Signs.Sign.CHOOSE_SPOT);
                        stage = Stage.CAN_SPOT;
                    } else {
                        stage = Stage.INTRO;
                    }
                    break;
                case CAN_SPOT:
                    match.update(delta); // only Taya moves; canSpotChosen() moves on to GAME START
                    break;
                case INTRO:
                    // Wait for the screen transition, then "GAME START!"; the match starts when the sign is gone
                    if (!introSignShown && !game.isTransitioning()) {
                        introSignShown = true;
                        signs.show(Signs.Sign.GAME_START);
                    } else if (introSignShown && !signs.isShowing()) {
                        stage = Stage.PLAYING;
                    }
                    break;
                case PLAYING:
                    if (frozen) break;
                    match.update(delta);
                    effects.track(match, delta);
                    showTurnSignWhenFree();
                    if (match.isOver()) {
                        stage = Stage.OUTRO;
                        signs.show(Signs.Sign.GOOD_JOB);
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
        if (offscreen) blur.endScene(Math.max(Math.max(signs.blurAmount(), pauseFade), Math.max(manoFade, dialogueFade)),
            SIGN_BLUR_RADIUS);

        // 2. HUD, sign and aim meter on top: steady and sharp
        viewport.apply();
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        drawHud();
        drawCanCountdown();
        if (pauseFade == 0f) signs.draw(spriteBatch, VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f + 40f); // hidden behind the pause menu
        spriteBatch.end();

        renderAimOverlay();

        if (debugCollision) {
            renderDebugCollision();
        }

        if (manoFade > 0f) drawMano(manoFade);
        if (dialogueFade > 0f) dialogue.draw(spriteBatch, shapeRenderer, font, hints, VIEW_X, VIEW_Y, VIEW_W, dialogueFade);
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
        if (match.mode() == Match.RoundMode.PLACE_CAN) {
            drawCanZone(); // the box Taya may stand the can in
        } else {
            shapeRenderer.setColor(Color.LIGHT_GRAY);
            shapeRenderer.circle(match.canBase().x, match.canBase().y, 16f);
        }

        drawTrashOnGround();
        drawPoopAndDogShapes();
        if (!effects.hasDustSprite()) effects.drawDust(shapeRenderer);

        if (!taya.hasCan) can.renderShadow(shapeRenderer); // a carried can has no shadow of its own
        for (Player p : match.players()) p.renderShadow(shapeRenderer);

        if (slipperTexture == null) { // placeholder: an oval in the owner's colour
            for (Player p : match.players()) {
                if (match.isSlipperOut(p)) p.slipper.render(shapeRenderer);
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        spriteBatch.begin();
        if (effects.hasDustSprite()) effects.drawDust(spriteBatch);
        drawTrashSprites(true);
        drawPoopAndDogSprites();
        drawSlipperSprites();

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
        drawWarningSigns();
        spriteBatch.end();

        drawTrashInAirAndDizzyStars();
        drawButtonPrompts();

        spriteBatch.begin();
        drawNameTags();
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
        if (dog == null) return;
        if (dogTexture != null) { // the sprite dog only needs its shadow here
            shapeRenderer.setColor(0f, 0f, 0f, 0.28f);
            shapeRenderer.ellipse(dog.position.x - 16f, dog.position.y - 4f, 32f, 8f);
            return;
        }
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
            float w = poopTexture.getWidth() * SMALL_ART_SCALE, h = poopTexture.getHeight() * SMALL_ART_SCALE;
            spriteBatch.draw(poopTexture, poop.x - w / 2f, poop.y - h * 0.3f, w, h); // resting on its ground point
        }
        StrayDog dog = match.dog();
        if (dog != null && dogWalk != null) {
            TextureRegion frame = dog.isMoving() ? dogWalk.getKeyFrame(clock, true) : dogSit.getKeyFrame(dog.time, true);
            float w = frame.getRegionWidth() * DOG_SCALE, h = frame.getRegionHeight() * DOG_SCALE;
            float flip = dog.direction > 0 ? -1f : 1f; // the art faces left: mirror it when walking right
            spriteBatch.draw(frame, dog.position.x - flip * w / 2f, dog.position.y - DOG_FEET_IN_FRAME * DOG_SCALE, flip * w, h);
        }
    }

    /** Slipper art on the ground or flying, tinted in the owner's player colour. Inside batch.begin()/end(). */
    private void drawSlipperSprites() {
        if (slipperTexture == null) return;
        float previous = spriteBatch.getPackedColor();
        float w = slipperTexture.getWidth() * SMALL_ART_SCALE, h = slipperTexture.getHeight() * SMALL_ART_SCALE;
        for (Player p : match.players()) {
            if (!match.isSlipperOut(p)) continue;
            float spin = p.slipper.velocity.len() > 0f ? clock * 900f + p.id * 90f : 0f; // spins while flying
            spriteBatch.setColor(p.color());
            spriteBatch.draw(slipperTexture, p.slipper.position.x - w / 2f, p.slipper.position.y - h / 2f,
                w / 2f, h / 2f, w, h, 1f, 1f, spin, 0, 0, slipperTexture.getWidth(), slipperTexture.getHeight(), false, false);
        }
        spriteBatch.setPackedColor(previous);
    }

    /** Placeholder trash: a simple coloured shape per kind, drawn TRASH_SCALE times its base size. */
    private void drawTrashShape(Trash t, float x, float y, float alpha) {
        float k = TRASH_SCALE;
        Color c = TRASH_COLORS[t.kind];
        shapeRenderer.setColor(0f, 0f, 0f, 0.25f * alpha);
        shapeRenderer.ellipse(x - 10f * k, y - 5f * k, 20f * k, 8f * k);
        shapeRenderer.setColor(c.r, c.g, c.b, alpha);
        switch (t.kind) {
            case 0: // banana: middle plus three flaps
                shapeRenderer.ellipse(x - 5f * k, y - 3f * k, 10f * k, 7f * k);
                shapeRenderer.triangle(x - 3f * k, y, x - 13f * k, y + 4f * k, x - 4f * k, y + 3f * k);
                shapeRenderer.triangle(x + 3f * k, y, x + 13f * k, y + 4f * k, x + 4f * k, y + 3f * k);
                shapeRenderer.triangle(x - 2f * k, y - 2f * k, x + 2f * k, y - 2f * k, x, y - 9f * k);
                break;
            case 1: // box
                shapeRenderer.rect(x - 7f * k, y - 3f * k, 14f * k, 11f * k);
                break;
            default: // apple
                shapeRenderer.circle(x, y + 2f * k, 5f * k, 12);
                break;
        }
    }

    /** Trash sprites (when the art exists): landed ones under the players, flying ones on top. */
    private void drawTrashSprites(boolean landed) {
        float previous = spriteBatch.getPackedColor();
        for (Trash t : match.trash()) {
            Texture tex = trashTextures[t.kind];
            if (tex == null || t.state == Trash.State.WARNING || t.isLanded() != landed) continue;
            float w = tex.getWidth() * SMALL_ART_SCALE, h = tex.getHeight() * SMALL_ART_SCALE;
            spriteBatch.setColor(1f, 1f, 1f, t.alpha());
            spriteBatch.draw(tex, t.x() - w / 2f, t.groundY() + t.height() - h * 0.3f, w, h); // resting on its ground point
        }
        spriteBatch.setPackedColor(previous);
    }

    /** The warning sign (art) bobbing over each spot where trash is about to land. Inside batch.begin()/end(). */
    private void drawWarningSigns() {
        if (warningTexture == null) return;
        float w = warningTexture.getWidth() * WARNING_SIGN_SCALE, h = warningTexture.getHeight() * WARNING_SIGN_SCALE;
        float bob = Math.abs(MathUtils.sin(clock * 6f)) * 6f;
        for (Trash t : match.trash()) {
            if (t.isLanded()) continue;
            spriteBatch.draw(warningTexture, t.target.x - w / 2f, t.target.y + 6f + bob, w, h);
        }
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

    /** Shows "NAME THROW!" once no other sign is up, if it is still that Thrower's turn. */
    private void showTurnSignWhenFree() {
        if (pendingTurn == null || signFreeTime < TURN_SIGN_GAP) return; // a short gap after any other sign or hit-stop
        Player p = pendingTurn;
        pendingTurn = null;
        if (p != match.nextThrower() || p.hasThrown) return; // the turn already passed
        signs.show(Signs.Sign.THROW_TURN, p.label() + " THROW!", p.color());
    }

    // ------------------------------------------------------------------
    // Mano ("maiba taya"): picks the first Taya before GAME START
    // ------------------------------------------------------------------

    // Hands in a ring, reaching into the middle from each player's side (placeholder shapes, or art from
    // assets/mano/hand_fist.png, hand_up.png, hand_down.png: white, fingers pointing up, tinted per player)
    private static final float MANO_RING = 175f;     // centre of the court -> where each arm starts
    private static final float MANO_BOB = 14f;       // how far the fists bob in and out while counting down
    private static final float MANO_ART_SCALE = 3f;
    private Texture handFist, handUp, handDown;
    private Texture manoBack; // assets/mano/back.png: the round backing behind the ring of hands
    private Texture manoSelect; // assets/mano/select.png: the red starburst behind the hand picked as Taya
    private static final float MANO_SELECT_SCALE = 2.5f; // 50 px art drawn at 125 px
    private static final float MANO_BACK_SCALE = 13f; // 32 px art drawn at 416 px
    private int manoLastTick = -1;

    /** Direction (degrees) from the centre to player i's hand: P1 at the bottom, then around clockwise. */
    private static float manoAngle(int i, int n) {
        if (n == 2) return i == 0 ? 180f : 0f;
        if (n == 3) return 270f - i * 120f;
        return 270f - i * 90f;
    }

    /** The intro dialogue for this many players (a random variant), with the players' names filled in. */
    private DialogueBox loadIntroDialogue() {
        Array<DialogueScript.Line> lines = new Array<>();
        if (Gdx.files.internal("dialogue/intro.txt").exists()) {
            DialogueScript script = DialogueScript.parse(Gdx.files.internal("dialogue/intro.txt").readString("UTF-8"));
            if (script.problems() > 0) Gdx.app.error("Dialogue", script.problems() + " line(s) in dialogue/intro.txt could not be read");
            lines = script.pick(characters.length, names, new java.util.Random());
        }
        return new DialogueBox(lines, characters, names, audio);
    }

    /** True while anyone holds B (skips the dialogue when held long enough). */
    private boolean anyoneHolds() {
        for (int i = 0; i < match.players().size; i++) if (match.players().get(i).input.b) return true;
        return false;
    }

    /** Before the mano has picked the Taya: nobody is shown as Taya yet. */
    private boolean beforeTaya() {
        return stage == Stage.DIALOGUE || stage == Stage.MANO;
    }

    private void updateMano(float delta) {
        for (int i = 0; i < match.players().size; i++) {
            PlayerInput in = match.players().get(i).input;
            if (in.aPressed) mano.choose(i, true);   // A = palm up
            if (in.bPressed) mano.choose(i, false);  // B = palm down (also the default)
        }
        Mano.Phase happened = mano.update(delta);

        int tick = MathUtils.ceil(mano.secondsLeft());
        if (mano.phase() == Mano.Phase.CHOOSING && tick != manoLastTick && tick > 0) audio.play(Audio.Sfx.UI_MOVE);
        manoLastTick = tick;

        if (happened == Mano.Phase.REVEAL) {
            audio.playVaried(Audio.Sfx.THROW);
            audio.play(mano.isAgain() ? Audio.Sfx.UI_DENY : Audio.Sfx.TAG);
        } else if (happened == Mano.Phase.DONE) {
            Player taya = match.players().get(mano.taya());
            match.chooseFirstTaya(taya.id);
            signs.show(Signs.Sign.TAYA_PICKED, taya.label() + " IS TAYA!", taya.color());
            stage = Stage.MANO_RESULT;
        }
    }

    /** The ring of hands, the countdown and the result. HUD layer (the court is blurred behind it). */
    private void drawMano(float alpha) {
        float cx = VIEW_X + VIEW_W / 2f, cy = VIEW_Y + VIEW_H / 2f - 10f;
        int n = mano.players();
        boolean choosing = mano.phase() == Mano.Phase.CHOOSING;
        float bob = choosing ? Math.abs(MathUtils.sin(clock * 9f)) * MANO_BOB : -8f; // shaking, then pushed in
        float pulse = (MathUtils.sin(clock * 10f) + 1f) / 2f;

        if (manoBack != null) { // the round backing art behind the hands
            float size = manoBack.getWidth() * MANO_BACK_SCALE;
            spriteBatch.begin();
            spriteBatch.setColor(1f, 1f, 1f, alpha);
            spriteBatch.draw(manoBack, cx - size / 2f, cy - size / 2f, size, size);
            spriteBatch.setColor(Color.WHITE);
            spriteBatch.end();
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        if (manoBack == null) { // placeholder backing: a dark disc
            shapeRenderer.setColor(0f, 0f, 0f, 0.35f * alpha);
            shapeRenderer.circle(cx, cy, MANO_RING + 40f, 48);
        }
        for (int i = 0; i < n; i++) {
            float a = manoAngle(i, n);
            float baseX = cx + MathUtils.cosDeg(a) * (MANO_RING - bob), baseY = cy + MathUtils.sinDeg(a) * (MANO_RING - bob);
            if (!choosing && i == mano.taya() && manoSelect == null) {          // the odd hand glows (placeholder)
                shapeRenderer.setColor(1f, 0.25f, 0.2f, (0.35f + 0.35f * pulse) * alpha);
                float gx = cx + MathUtils.cosDeg(a) * (MANO_RING * 0.6f), gy = cy + MathUtils.sinDeg(a) * (MANO_RING * 0.6f);
                shapeRenderer.circle(gx, gy, 62f, 32);
            }
            if (handFist == null) drawPlaceholderHand(baseX, baseY, a + 180f, match.players().get(i).color(), choosing, mano.isPalmUp(i), alpha);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        spriteBatch.begin();
        if (!choosing && manoSelect != null && mano.taya() >= 0) { // the red starburst behind the Taya's hand
            float a = manoAngle(mano.taya(), n);
            float r = MANO_RING * 0.65f;                                   // about the middle of the hand
            float size = manoSelect.getWidth() * MANO_SELECT_SCALE * (1f + 0.08f * pulse);
            spriteBatch.setColor(1f, 1f, 1f, alpha);
            spriteBatch.draw(manoSelect, cx + MathUtils.cosDeg(a) * r - size / 2f, cy + MathUtils.sinDeg(a) * r - size / 2f,
                size / 2f, size / 2f, size, size, 1f, 1f, clock * 40f,
                0, 0, manoSelect.getWidth(), manoSelect.getHeight(), false, false);
            spriteBatch.setColor(Color.WHITE);
        }
        if (handFist != null) { // hand art: pointing up in the image, turned to point at the centre
            for (int i = 0; i < n; i++) {
                float a = manoAngle(i, n);
                Texture art = choosing ? handFist : (mano.isPalmUp(i) ? handUp : handDown);
                if (art == null) art = handFist;
                float w = art.getWidth() * MANO_ART_SCALE, h = art.getHeight() * MANO_ART_SCALE;
                float baseX = cx + MathUtils.cosDeg(a) * (MANO_RING - bob), baseY = cy + MathUtils.sinDeg(a) * (MANO_RING - bob);
                Color c = match.players().get(i).color();
                spriteBatch.setColor(c.r, c.g, c.b, alpha);
                spriteBatch.draw(art, baseX - w / 2f, baseY, w / 2f, 0f, w, h, 1f, 1f, a + 90f,
                    0, 0, art.getWidth(), art.getHeight(), false, false);
            }
            spriteBatch.setColor(Color.WHITE);
        }
        for (int i = 0; i < n; i++) { // names on the outside of the ring
            float a = manoAngle(i, n);
            Player p = match.players().get(i);
            float nameR = MANO_RING + 52f; // just outside the backing disc
            float nx = cx + MathUtils.cosDeg(a) * nameR, ny = cy + MathUtils.sinDeg(a) * nameR + 8f;
            String label = (!choosing && i == mano.taya()) ? p.label() + "  TAYA!" : p.label();
            shadowText(label, nx, ny, 1.4f, p.color(), alpha);
        }
        shadowText("MAIBA TAYA!", cx, cy + MANO_RING + 112f, 2.6f, Color.GOLD, alpha);
        if (choosing) {
            shadowText(String.valueOf(MathUtils.ceil(mano.secondsLeft())), cx, cy + 22f, 4f, Color.WHITE, alpha);
            drawManoButtonHint(cx, cy - MANO_RING - 88f, alpha);
        } else if (mano.isAgain()) {
            shadowText("AGAIN!", cx, cy + 18f, 3.4f, Color.ORANGE, alpha);
        }
        spriteBatch.end();
    }

    /** "[yellow] PALM UP   [red] PALM DOWN" with the button art (plain text if the art is missing). Inside the batch. */
    private void drawManoButtonHint(float cx, float y, float alpha) {
        if (buttonA == null || buttonB == null) {
            shadowText("A = PALM UP     B = PALM DOWN", cx, y, 1.3f, Color.WHITE, alpha);
            return;
        }
        float size = buttonA.getWidth() * 2f;
        spriteBatch.setColor(1f, 1f, 1f, alpha);
        spriteBatch.draw(buttonA, cx - 170f, y - size / 2f - 8f, size, size);  // yellow = palm up
        spriteBatch.draw(buttonB, cx + 25f, y - size / 2f - 8f, size, size);   // red = palm down
        spriteBatch.setColor(Color.WHITE);
        shadowText("PALM UP", cx - 170f + size + 60f, y, 1.3f, Color.WHITE, alpha);
        shadowText("PALM DOWN", cx + 25f + size + 70f, y, 1.3f, Color.WHITE, alpha);
    }

    /** Centred text with a drop shadow. Inside batch.begin()/end(). */
    private void shadowText(String text, float cx, float y, float scale, Color color, float alpha) {
        font.getData().setScale(scale);
        font.setColor(0f, 0f, 0f, 0.85f * alpha);
        font.draw(spriteBatch, text, cx - 300f + 2f, y - 2f, 600f, Align.center, false);
        font.setColor(color.r, color.g, color.b, alpha);
        font.draw(spriteBatch, text, cx - 300f, y, 600f, Align.center, false);
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    /**
     * Placeholder hand from shapes, from (baseX, baseY) pointing along {@code dir} degrees: a fist while counting
     * down, then an open hand: palm up (light, with a pink palm) or palm down (the back, with knuckles).
     */
    private void drawPlaceholderHand(float baseX, float baseY, float dir, Color c, boolean fist, boolean palmUp, float alpha) {
        float dx = MathUtils.cosDeg(dir), dy = MathUtils.sinDeg(dir);
        Color skin = palmUp && !fist ? new Color(c).lerp(Color.WHITE, 0.45f) : c;
        handPart(baseX, baseY, dx, dy, 0f, 55f, 0f, 30f, dir, skin, 0.85f, alpha);              // arm
        if (fist) {
            handPart(baseX, baseY, dx, dy, 55f, 38f, 0f, 44f, dir, skin, 1f, alpha);            // closed fist
            for (int k = -1; k <= 1; k++) handPart(baseX, baseY, dx, dy, 90f, 5f, k * 12f, 9f, dir, skin, 0.7f, alpha);
            return;
        }
        handPart(baseX, baseY, dx, dy, 55f, 40f, 0f, 48f, dir, skin, 1f, alpha);                // palm
        for (int k = 0; k < 4; k++) {                                                            // fingers
            handPart(baseX, baseY, dx, dy, 95f, 26f, -16.5f + k * 11f, 9f, dir, skin, 1f, alpha);
        }
        handPart(baseX, baseY, dx, dy, 62f, 22f, palmUp ? 30f : -30f, 10f, dir + (palmUp ? 35f : -35f), skin, 1f, alpha); // thumb
        if (palmUp) {
            shapeRenderer.setColor(0.95f, 0.6f, 0.65f, 0.8f * alpha);
            shapeRenderer.circle(baseX + dx * 75f, baseY + dy * 75f, 13f, 16);
        } else {
            for (int k = 0; k < 4; k++) handPart(baseX, baseY, dx, dy, 92f, 4f, -16.5f + k * 11f, 7f, dir, skin, 0.6f, alpha);
        }
    }

    /** One rotated rectangle of a placeholder hand: {@code along} px from the base towards the centre, {@code len}
     * long, shifted {@code across} sideways, {@code wid} wide. */
    private void handPart(float baseX, float baseY, float dx, float dy, float along, float len, float across, float wid,
                          float deg, Color c, float shade, float alpha) {
        float mid = along + len / 2f;
        float x = baseX + dx * mid - dy * across, y = baseY + dy * mid + dx * across;
        shapeRenderer.setColor(c.r * shade, c.g * shade, c.b * shade, alpha);
        shapeRenderer.rect(x - len / 2f, y - wid / 2f, len / 2f, wid / 2f, len, wid, 1f, 1f, deg);
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
            tumbalata.changeScreen(new VictoryScreen(tumbalata, match.scores(), characters, names, Awards.compute(match.stats())),
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
            String text = (p == taya && !beforeTaya()) ? p.label() + " TAYA" : p.label();
            Color c = p.color();
            float a = (p != taya && p.hasThrown) ? 0.7f : 1f;
            // the player's colour with a dark stroke, readable on any part of the court
            Fonts.drawStroked(spriteBatch, font, text, p.position.x - 50f, p.position.y + NAME_TAG_Y, 100f, Align.center,
                new Color(c.r, c.g, c.b, a), new Color(0f, 0f, 0f, 0.85f * a), 1.5f);
        }
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    /** After a missed toss: 3, 2, 1 over the can (HUD layer, so it stays sharp behind the RUN! sign). */
    private void drawCanCountdown() {
        if (match.canLockTimeLeft() <= 0f) return;
        Can can = match.can();
        String count = String.valueOf(MathUtils.ceil(match.canLockTimeLeft()));
        font.getData().setScale(3f);
        font.setColor(0f, 0f, 0f, 0.85f);
        font.draw(spriteBatch, count, can.position.x - 20f + 2f, can.position.y + 52f - 2f, 40f, Align.center, false);
        font.setColor(Color.GOLD);
        font.draw(spriteBatch, count, can.position.x - 20f, can.position.y + 52f, 40f, Align.center, false);
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
    }

    /** The can zone on the ground while Taya chooses the spot: pulsing fill and a border, green when Taya can put it
     * down right here. Inside a Filled shape block with blending on. */
    private void drawCanZone() {
        float[] v = match.court().canZoneVertices();
        if (v == null) return;
        boolean here = match.canPlaceCanHere();
        float pulse = (MathUtils.sin(clock * 5f) + 1f) / 2f;
        if (here) shapeRenderer.setColor(0.4f, 1f, 0.45f, 0.18f + 0.1f * pulse);
        else shapeRenderer.setColor(1f, 1f, 1f, 0.12f + 0.1f * pulse);
        for (int i = 2; i + 3 < v.length; i += 2) shapeRenderer.triangle(v[0], v[1], v[i], v[i + 1], v[i + 2], v[i + 3]);
        shapeRenderer.setColor(here ? 0.4f : 1f, here ? 1f : 0.85f, here ? 0.45f : 0.25f, 0.9f);
        for (int i = 0; i < v.length; i += 2) {
            int j = (i + 2) % v.length;
            shapeRenderer.rectLine(v[i], v[i + 1], v[j], v[j + 1], 3f);
        }
    }

    // Button prompts: a circle in the controller button's colour over the player who can press it now
    private static final Color BUTTON_B = new Color(0.92f, 0.22f, 0.2f, 1f);  // red: pick up / put down
    private static final Color BUTTON_A = new Color(1f, 0.82f, 0.15f, 1f);   // yellow: aim and throw
    private static final float BUTTON_Y = 56f;                                 // above the name tag
    private static final float BUTTON_ART_SCALE = 1.5f; // 18 px art drawn at 27 px
    private Texture buttonA, buttonB; // assets/yellowbutton.png (A), redbutton.png (B)

    /** Red (B) and yellow (A) circles over whoever can press them right now. Draws its own shape pass. */
    private void drawButtonPrompts() {
        if (stage != Stage.PLAYING && stage != Stage.CAN_SPOT) return; // not behind the mano or the signs before play
        Player taya = match.taya();
        boolean art = buttonA != null && buttonB != null; // the button art, else drawn circles
        if (art) {
            spriteBatch.begin();
        } else {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        }
        for (Player p : match.players()) {
            boolean b = match.canPickUpSlipper(p)
                || (p == taya && ((match.canTayaPickUpCan() && match.isTayaNearCan()) || match.canTayaPlaceCan()
                    || match.canPlaceCanHere()));
            boolean a = canPressA(p);
            if (a && b) {
                buttonCircle(p.position.x - 10f, p.position.y + BUTTON_Y, BUTTON_A);
                buttonCircle(p.position.x + 10f, p.position.y + BUTTON_Y, BUTTON_B);
            } else if (a) {
                buttonCircle(p.position.x, p.position.y + BUTTON_Y, BUTTON_A);
            } else if (b) {
                buttonCircle(p.position.x, p.position.y + BUTTON_Y, BUTTON_B);
            }
        }
        if (art) {
            spriteBatch.end();
        } else {
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /** True when A does something for this player now: start aiming, lock the aim, throw, re-throw or toss. */
    private boolean canPressA(Player p) {
        if (match.aimer() != null) return p == match.aimer();
        Match.RoundMode mode = match.mode();
        if (p == match.taya()) return mode == Match.RoundMode.TAYA_TOSS && p.hasCan && !p.isStunned();
        if (mode != Match.RoundMode.THROWING && mode != Match.RoundMode.SCRAMBLE) return false;
        return (p == match.nextThrower() && p.hasSlipper && !p.isStunned()) || match.canRethrow(p);
    }

    /** One pulsing button prompt: the button art (inside batch.begin/end), or a drawn circle (inside a Filled shape block). */
    private void buttonCircle(float x, float y, Color c) {
        if (buttonA != null && buttonB != null) { // the button art: yellow for A, red for B, pulsing a little
            Texture t = c == BUTTON_A ? buttonA : buttonB;
            float s = BUTTON_ART_SCALE * (1f + 0.08f * (MathUtils.sin(clock * 8f) + 1f) / 2f);
            float w = t.getWidth() * s, h = t.getHeight() * s;
            spriteBatch.draw(t, x - w / 2f, y - h / 2f, w, h);
            return;
        }
        float r = 8f + 1.5f * (MathUtils.sin(clock * 8f) + 1f) / 2f;
        shapeRenderer.setColor(0f, 0f, 0f, 0.6f);
        shapeRenderer.circle(x, y, r + 2.5f, 20);
        shapeRenderer.setColor(c);
        shapeRenderer.circle(x, y, r, 20);
        shapeRenderer.setColor(1f, 1f, 1f, 0.45f);
        shapeRenderer.circle(x - r * 0.3f, y + r * 0.3f, r * 0.3f, 10);
    }

    private void drawPopups() {
        font.getData().setScale(1.6f);
        for (Popup popup : popups) {
            float t = popup.age / POPUP_TIME;
            Color c = popup.player.color();
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

        if (!beforeTaya()) {
            font.draw(spriteBatch, statusText(), VIEW_X + VIEW_W / 2f - 300f, VIEW_Y + VIEW_H - 54f, 600f, Align.center, false);
        }

        font.getData().setScale(1.5f);
        float y = VIEW_Y + VIEW_H - 16f;
        for (Player p : match.players()) {
            String role = (p == match.taya() && !beforeTaya()) ? " (Taya)" : ""; // no Taya until the mano picks one
            String line = p.label() + " " + Characters.TRAITS[p.characterIndex] + role + "  " + p.score;
            font.setColor(0f, 0f, 0f, 0.85f); // drop shadow, so it reads on the busy background
            font.draw(spriteBatch, line, VIEW_X + 22f, y - 2f);
            font.setColor(p.color());
            font.draw(spriteBatch, line, VIEW_X + 20f, y);
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
            case PLACE_CAN:
                return match.taya().label() + " (Taya): stand the can anywhere in the box";
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

        // The aim arrow (art): white, tinted in the player's colour; the long one for SNIPER characters.
        // The art points up-right (45 degrees) with its tail at ARROW_TAIL_*, so it is turned by (angle - 45) around
        // the tail, which sits on the player. It stays while the power is chosen.
        Texture arrow = Characters.LONG_ARROW[aimer.characterIndex] ? arrowSniper : arrowDefault;
        if (arrow != null) {
            boolean longArrow = arrow == arrowSniper;
            float tailX = (longArrow ? ARROW_SNIPER_TAIL_X : ARROW_DEFAULT_TAIL_X) * ARROW_SCALE;
            float tailY = (longArrow ? ARROW_SNIPER_TAIL_Y : ARROW_DEFAULT_TAIL_Y) * ARROW_SCALE;
            float w = arrow.getWidth() * ARROW_SCALE, h = arrow.getHeight() * ARROW_SCALE;
            spriteBatch.begin();
            spriteBatch.setColor(aimer.color());
            spriteBatch.draw(arrow, aimer.position.x - tailX, aimer.position.y - tailY, tailX, tailY, w, h, 1f, 1f,
                match.angle() - 45f, 0, 0, arrow.getWidth(), arrow.getHeight(), false, false);
            spriteBatch.setColor(Color.WHITE);
            spriteBatch.end();
        }

        if (match.aim() == Match.Aim.ANGLE && arrow == null) {
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
        // court line (green) and throw area (light blue), from the map
        float[] line = match.court().endpoints();
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.line(match.court().xAt(VIEW_Y + VIEW_H), VIEW_Y + VIEW_H, match.court().xAt(VIEW_Y), VIEW_Y);
        shapeRenderer.circle(line[0], line[1], 4f);
        shapeRenderer.circle(line[2], line[3], 4f);
        float[] area = match.court().throwAreaVertices();
        if (area != null) {
            shapeRenderer.setColor(Color.SKY);
            shapeRenderer.polygon(area);
        }
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
        if (playerSlipperOverlay != null) playerSlipperOverlay.dispose();
        if (playerCanSheet != null) playerCanSheet.dispose();
        if (canSheet != null) canSheet.dispose();
        if (signs != null) signs.dispose();
        for (Texture t : new Texture[] { handFist, handUp, handDown, manoBack, manoSelect }) if (t != null) t.dispose();
        if (dialogue != null) dialogue.dispose();
        if (hints != null) hints.dispose();
        if (audio != null) audio.playBackgroundMusic(null);
        if (effects != null) effects.dispose();
        if (blur != null) blur.dispose();
        for (Texture t : trashTextures) if (t != null) t.dispose();
        if (dogTexture != null) dogTexture.dispose();
        if (slipperTexture != null) slipperTexture.dispose();
        if (warningTexture != null) warningTexture.dispose();
        if (buttonA != null) buttonA.dispose();
        if (buttonB != null) buttonB.dispose();
        if (arrowDefault != null) arrowDefault.dispose();
        if (arrowSniper != null) arrowSniper.dispose();
        if (poopTexture != null) poopTexture.dispose();
    }
}
