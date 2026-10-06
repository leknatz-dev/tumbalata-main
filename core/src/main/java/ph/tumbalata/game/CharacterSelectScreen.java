package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * "Choose your character" screen: 4 placeholder cards. Every player moves their own cursor with their own pad or keys
 * and locks in with A (B to change their mind). The game starts when everyone is locked in. A character can only be
 * locked by one player; others can still hover over it.
 * The mouse drives Player 1's cursor.
 */
public class CharacterSelectScreen implements Screen {
    // --- SCREEN SIZE (same as the other menus) ---
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;
    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;
    private static final int GAME_WINDOW_W = 1280;
    private static final int GAME_WINDOW_H = 698;

    // --- ASSET FILES (optional - placeholders are drawn if missing) ---
    private static final String BACKGROUND_FILE = "menu_background.png";
    private static final String SLIPPER_FILE = "menu_slipper.png";
    private static final String HEADING_FILE = "select_character_title.png";

    // --- HEADING ---
    private static final float HEADING_CENTER_Y = 420f;
    private static final float HEADING_BOB_AMOUNT = 6f;
    private static final float HEADING_BOB_SPEED = 2.5f;

    // --- CARD LAYOUT: each card's BOTTOM-LEFT corner on the 700 x 500 screen (x right, y UP) ---
    private static final float CARD_W = 110f;
    private static final float CARD_H = 150f;
    private static final float CARD_GAP = 30f;
    private static final float CARDS_Y = 190f;
    private static final float CARDS_START_X = (MENU_WIDTH - (Characters.COUNT * CARD_W + (Characters.COUNT - 1) * CARD_GAP)) / 2f;

    // Player tags ("P1".."P4") above the card each player is on; solid when locked in
    private static final float TAG_W = 26f;
    private static final float TAG_H = 18f;
    private static final float TAG_GAP = 2f;
    private static final float TAGS_ABOVE_CARD = 8f;
    private static final float HINT_Y = 120f;

    // Slipper indicator sits under Player 1's card and bobs up and down
    private static final float SLIPPER_GAP = 10f;
    private static final float SLIPPER_BOB_AMOUNT = 5f;
    private static final float SLIPPER_BOB_SPEED = 6f;
    private static final Color UNPICKED_TINT = new Color(0.65f, 0.65f, 0.65f, 1f);

    private final TumbalataGame game;
    private final int playerCount;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    private Texture background;
    private MenuBackdrop backdrop; // shared live background (null = use the image background)
    private static final float BACKDROP_DIM = 0.35f;
    private Texture slipperTexture;
    private Texture headingTexture;
    private final Texture[] portraits = new Texture[Characters.COUNT];

    private final Rectangle[] cardBounds = new Rectangle[Characters.COUNT];
    private final int[] cursor;      // cursor[player] = card that player is on
    private final boolean[] locked;  // locked[player] = player has confirmed their pick

    private static final float TAKEN_MESSAGE_SECONDS = 1.5f;
    private String takenMessage = "";
    private float takenMessageTime = 0f;
    private float time = 0f;
    private boolean leaving = false;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;

    public CharacterSelectScreen(TumbalataGame game, int playerCount) {
        this.game = game;
        this.playerCount = playerCount;
        this.cursor = new int[playerCount];
        this.locked = new boolean[playerCount];
        for (int p = 0; p < playerCount; p++) cursor[p] = p % Characters.COUNT; // everyone starts on a different card
    }

    /** Player who has locked in on this card, or -1. Each character can only be locked by one player. */
    private int lockedBy(int card) {
        for (int p = 0; p < playerCount; p++) if (locked[p] && cursor[p] == card) return p;
        return -1;
    }

    /** Locks player p on their card, unless someone else already has it (then shows a short "taken" message). */
    private void tryLock(int p) {
        int owner = lockedBy(cursor[p]);
        if (owner >= 0 && owner != p) {
            takenMessage = "P" + (p + 1) + ": " + Characters.NAMES[cursor[p]] + " IS TAKEN BY P" + (owner + 1);
            takenMessageTime = TAKEN_MESSAGE_SECONDS;
            game.audio().play(Audio.Sfx.UI_DENY);
            return;
        }
        locked[p] = true;
        game.audio().play(Audio.Sfx.UI_CONFIRM);
    }

    private boolean anyCursorOn(int card) {
        for (int p = 0; p < playerCount; p++) if (cursor[p] == card) return true;
        return false;
    }

    @Override
    public void show() {
        leaving = false;
        game.audio().playMusic(Audio.Track.MENU); // keeps playing if it already is

        camera = new OrthographicCamera();
        viewport = new FitViewport(MENU_WIDTH, MENU_HEIGHT, camera);
        viewport.apply(true);

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();

        background = loadTexture(BACKGROUND_FILE);
        backdrop = game.getBackdrop();
        slipperTexture = loadTexture(SLIPPER_FILE);
        headingTexture = loadTexture(HEADING_FILE);
        for (int i = 0; i < Characters.COUNT; i++) {
            portraits[i] = loadTexture(Characters.PORTRAIT_FILES[i]);
            cardBounds[i] = new Rectangle(CARDS_START_X + i * (CARD_W + CARD_GAP), CARDS_Y, CARD_W, CARD_H);
        }
    }

    /** Loads a texture, or returns null (silently - these are optional) if the file isn't there. */
    private Texture loadTexture(String file) {
        if (!Gdx.files.internal(file).exists()) {
            return null;
        }
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    @Override
    public void render(float delta) {
        time += delta;
        takenMessageTime = Math.max(0f, takenMessageTime - delta);
        handleInput();

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Live background (the map with the 4 characters walking around)
        if (backdrop != null) {
            backdrop.renderDimmed(delta, shapeRenderer, camera.combined, MENU_WIDTH, MENU_HEIGHT, BACKDROP_DIM);
        }

        Rectangle sel = cardBounds[cursor[0]];
        float bob = (MathUtils.sin(time * SLIPPER_BOB_SPEED) + 1f) / 2f * SLIPPER_BOB_AMOUNT;

        // --- Shapes: placeholder background, cards, selection border, player tags, placeholder slipper ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        if (backdrop == null && background == null) {
            shapeRenderer.setColor(0.18f, 0.35f, 0.25f, 1f);
            shapeRenderer.rect(0, 0, MENU_WIDTH, MENU_HEIGHT);
        }
        for (int i = 0; i < Characters.COUNT; i++) {
            Rectangle r = cardBounds[i];
            boolean picked = anyCursorOn(i);
            if (picked) { // white border behind every card someone is on
                shapeRenderer.setColor(Color.WHITE);
                shapeRenderer.rect(r.x - 5f, r.y - 5f, r.width + 10f, r.height + 10f);
            }
            if (portraits[i] == null) {
                Color c = Characters.CARD_COLORS[i];
                float dim = picked ? 1f : 0.65f; // cards nobody is on are a bit darker
                shapeRenderer.setColor(c.r * dim, c.g * dim, c.b * dim, 1f);
                shapeRenderer.rect(r.x, r.y, r.width, r.height);
                // simple placeholder "head" so the card doesn't look empty
                shapeRenderer.setColor(0f, 0f, 0f, 0.25f);
                shapeRenderer.circle(r.x + r.width / 2f, r.y + r.height * 0.62f, 24f);
                shapeRenderer.rect(r.x + r.width / 2f - 30f, r.y + 12f, 60f, 48f);
            }
        }
        for (int i = 0; i < Characters.COUNT; i++) drawStatBars(cardBounds[i], i);
        for (int p = 0; p < playerCount; p++) {
            Color c = Player.SLOT_COLORS[p];
            shapeRenderer.setColor(c.r, c.g, c.b, locked[p] ? 1f : 0.45f);
            shapeRenderer.rect(tagX(p), tagY(), TAG_W, TAG_H);
        }
        if (slipperTexture == null) {
            shapeRenderer.setColor(Color.BROWN);
            shapeRenderer.ellipse(sel.x + (CARD_W - 40f) / 2f, sel.y - SLIPPER_GAP - 16f - 5f + bob, 40f, 16f);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- Sprites and text ---
        batch.begin();
        if (backdrop == null && background != null) batch.draw(background, 0, 0, MENU_WIDTH, MENU_HEIGHT);

        float headingBob = MathUtils.sin(time * HEADING_BOB_SPEED) * HEADING_BOB_AMOUNT;
        if (headingTexture != null) {
            float hw = headingTexture.getWidth();
            float hh = headingTexture.getHeight();
            batch.draw(headingTexture, (MENU_WIDTH - hw) / 2f, HEADING_CENTER_Y - hh / 2f + headingBob, hw, hh);
        } else {
            font.getData().setScale(2f);
            layout.setText(font, "CHOOSE YOUR CHARACTER");
            font.draw(batch, "CHOOSE YOUR CHARACTER", (MENU_WIDTH - layout.width) / 2f,
                HEADING_CENTER_Y + layout.height / 2f + headingBob);
            font.getData().setScale(1f);
        }

        for (int i = 0; i < Characters.COUNT; i++) {
            Rectangle r = cardBounds[i];
            if (portraits[i] != null) {
                batch.setColor(anyCursorOn(i) ? Color.WHITE : UNPICKED_TINT);
                batch.draw(portraits[i], r.x, r.y, r.width, r.height);
                batch.setColor(Color.WHITE);
            }
            layout.setText(font, Characters.NAMES[i]);
            font.draw(batch, Characters.NAMES[i], r.x + (r.width - layout.width) / 2f, r.y + 22f);
            drawStatLabels(r, i);

            int owner = lockedBy(i);
            if (owner >= 0) { // "TAKEN" in the owner's colour across the card
                String taken = "TAKEN";
                layout.setText(font, taken);
                font.setColor(Player.SLOT_COLORS[owner]);
                font.draw(batch, taken, r.x + (r.width - layout.width) / 2f, r.y + r.height / 2f);
                font.setColor(Color.WHITE);
            }
        }

        for (int p = 0; p < playerCount; p++) {
            String label = "P" + (p + 1);
            layout.setText(font, label);
            font.setColor(locked[p] ? Color.BLACK : Color.WHITE);
            font.draw(batch, label, tagX(p) + (TAG_W - layout.width) / 2f, tagY() + (TAG_H + layout.height) / 2f);
        }
        font.setColor(Color.WHITE);

        String hint = allLocked() ? "GET READY!"
            : takenMessageTime > 0f ? takenMessage
            : "MOVE TO CHOOSE   A: LOCK IN   B: CHANGE";
        layout.setText(font, hint);
        font.draw(batch, hint, (MENU_WIDTH - layout.width) / 2f, HINT_Y);

        if (slipperTexture != null) {
            float sw = slipperTexture.getWidth();
            float sh = slipperTexture.getHeight();
            batch.draw(slipperTexture, sel.x + (CARD_W - sw) / 2f, sel.y - SLIPPER_GAP - 5f - sh + bob, sw, sh);
        }
        batch.end();
    }

    // --- TRAIT STATS (placeholder panel at the bottom of each card) ---
    private static final String[] STAT_LABELS = { "SPD", "PWR", "AIM", "REACH" };
    private static final float STATS_Y = 34f;        // first bar row, from the card's bottom
    private static final float STAT_ROW = 10f;
    private static final float BAR_X = 44f, BAR_W = 56f, BAR_H = 5f;

    /** 0..1 bar length; a normal stat (1x) is half full. AIM is the aim meter speed, so slower shows as better. */
    private static float statBar(int character, int stat) {
        float v;
        switch (stat) {
            case 0: v = Characters.SPEED[character]; break;
            case 1: v = Characters.THROW[character]; break;
            case 2: v = 1f / Characters.AIM[character]; break;
            default: v = Characters.REACH[character]; break;
        }
        return MathUtils.clamp(0.5f + (v - 1f) * 2f, 0.1f, 1f);
    }

    /** Dark panel and stat bars. Inside the Filled shape block. */
    private void drawStatBars(Rectangle r, int character) {
        shapeRenderer.setColor(0f, 0f, 0f, 0.45f);
        shapeRenderer.rect(r.x + 4f, r.y + STATS_Y - 6f, r.width - 8f, STAT_ROW * STAT_LABELS.length + 4f);
        for (int s = 0; s < STAT_LABELS.length; s++) {
            float y = r.y + STATS_Y + s * STAT_ROW;
            shapeRenderer.setColor(1f, 1f, 1f, 0.2f);
            shapeRenderer.rect(r.x + BAR_X, y - BAR_H + 1f, BAR_W, BAR_H);
            float fill = statBar(character, s);
            shapeRenderer.setColor(fill > 0.55f ? Color.GOLD : (fill < 0.45f ? Color.SALMON : Color.LIGHT_GRAY));
            shapeRenderer.rect(r.x + BAR_X, y - BAR_H + 1f, BAR_W * fill, BAR_H);
        }
    }

    /** Trait name at the top of the card, stat names next to the bars. Inside batch.begin()/end(). */
    private void drawStatLabels(Rectangle r, int character) {
        font.getData().setScale(0.9f);
        font.setColor(Color.GOLD);
        layout.setText(font, Characters.TRAITS[character]);
        font.draw(batch, Characters.TRAITS[character], r.x + (r.width - layout.width) / 2f, r.y + r.height - 6f);
        font.getData().setScale(0.6f);
        font.setColor(Color.WHITE);
        for (int s = 0; s < STAT_LABELS.length; s++) {
            font.draw(batch, STAT_LABELS[s], r.x + 8f, r.y + STATS_Y + s * STAT_ROW + 2f);
        }
        font.getData().setScale(1f);
    }

    /** Left edge of player p's tag: tags of players on the same card sit side by side above it. */
    private float tagX(int p) {
        int slot = 0;
        for (int q = 0; q < p; q++) if (cursor[q] == cursor[p]) slot++;
        return cardBounds[cursor[p]].x + slot * (TAG_W + TAG_GAP);
    }

    private float tagY() {
        return CARDS_Y + CARD_H + TAGS_ABOVE_CARD;
    }

    private boolean allLocked() {
        for (int p = 0; p < playerCount; p++) if (!locked[p]) return false;
        return true;
    }

    private void handleInput() {
        InputManager input = game.input();
        InputManager.MenuInput in = input.menu(); // used for F11 and Esc only; picking uses each player's own input
        if (in.fullscreen) game.toggleFullscreen();
        if (leaving || input.isMenuLocked()) return; // ignore input while a screen transition plays

        boolean back = in.back; // Esc
        for (int p = 0; p < playerCount; p++) {
            PlayerInput pin = input.player(p);
            if (locked[p]) {
                if (pin.bPressed) {
                    locked[p] = false;
                    game.audio().play(Audio.Sfx.UI_BACK);
                }
                continue;
            }
            int before = cursor[p];
            if (pin.leftPressed) cursor[p] = (cursor[p] + Characters.COUNT - 1) % Characters.COUNT;
            if (pin.rightPressed) cursor[p] = (cursor[p] + 1) % Characters.COUNT;
            if (cursor[p] != before) game.audio().play(Audio.Sfx.UI_MOVE);
            if (pin.aPressed || pin.startPressed) tryLock(p);
            else if (pin.bPressed) back = true; // B with nothing to cancel goes back a screen
        }

        if (back) {
            leaving = true;
            game.audio().play(Audio.Sfx.UI_BACK);
            game.changeScreen(new PlayerSelectScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
            return;
        }

        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        boolean mouseMoved = Gdx.input.getX() != lastMouseX || Gdx.input.getY() != lastMouseY;
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();

        int hovered = -1;
        for (int i = 0; i < cardBounds.length; i++) {
            if (cardBounds[i].contains(mouse.x, mouse.y)) hovered = i;
        }
        // The mouse drives Player 1: hover to move, click to lock in
        if (!locked[0]) {
            if (hovered != -1 && mouseMoved && cursor[0] != hovered) {
                cursor[0] = hovered;
                game.audio().play(Audio.Sfx.UI_MOVE);
            }
            if (Gdx.input.justTouched() && hovered != -1) {
                cursor[0] = hovered;
                tryLock(0);
            }
        }

        if (allLocked()) startGame();
    }

    private void startGame() {
        leaving = true;
        game.changeScreen(new GameScreen(cursor.clone()), GAME_WINDOW_W, GAME_WINDOW_H);
    }

    @Override
    public void resize(int width, int height) {
        if (viewport != null) viewport.update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapeRenderer != null) { shapeRenderer.dispose(); shapeRenderer = null; }
        if (font != null) { font.dispose(); font = null; }
        if (background != null) { background.dispose(); background = null; }
        backdrop = null; // owned by TumbalataGame and shared with the other menu screens
        if (slipperTexture != null) { slipperTexture.dispose(); slipperTexture = null; }
        if (headingTexture != null) { headingTexture.dispose(); headingTexture = null; }
        for (int i = 0; i < portraits.length; i++) {
            if (portraits[i] != null) { portraits[i].dispose(); portraits[i] = null; }
        }
    }
}