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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Victory screen (placeholder art): a podium where the highest score stands in the MIDDLE and is the biggest.
 * Order left to right: 2nd, 1st, 3rd, (4th).
 */
public class VictoryScreen implements Screen {
    // --- SCREEN SIZE (same as the other menus) ---
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;
    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;

    // --- ASSET FILES (all optional - placeholders are drawn if missing) ---
    // Background: the same live map as the main menu (shared MenuBackdrop); this image only if that can't load
    private static final String BACKGROUND_FILE = "menu_background.png";
    private static final float BACKDROP_DIM = 0.35f; // same darkening as the main menu
    private static final String TITLE_FILE = "victory_title.png";

    // --- PODIUM LAYOUT (index = rank: 0 = 1st place) ---
    private static final float BASE_Y = 70f;                                          // ground line of the podium
    private static final float[] SLOT_CENTER_X = { 350f, 190f, 500f, 610f };          // 1st is in the exact middle
    private static final float[] BLOCK_W       = { 120f,  90f,  90f,  70f };          // 1st is the widest...
    private static final float[] BLOCK_H       = { 150f, 105f,  75f,  52f };          // ...and the tallest
    private static final float[] FIGURE_SCALE  = { 1.5f, 1.15f, 1.0f, 0.85f };        // ...with the biggest character
    private static final String[] RANK_LABELS  = { "1ST", "2ND", "3RD", "4TH" };

    // --- ANIMATION ---
    private static final float RISE_TIME = 0.6f;       // seconds for a podium block to rise
    private static final float RISE_STAGGER = 0.7f;    // delay between ranks (lowest rank rises first, winner last)
    private static final float POP_TIME = 0.3f;        // character "pop in" after its block has risen
    private static final float INPUT_DELAY = 1.0f;     // ignore keys at first so a button mash in the game can't skip this

    private static final float TITLE_CENTER_Y = 458f;
    private static final float TITLE_BOB_AMOUNT = 7f;
    private static final float TITLE_BOB_SPEED = 2.5f;

    // --- AWARDS (one at a time under the podium, after the winner is revealed) ---
    private static final float AWARD_SECONDS = 2.6f;   // each award is shown this long
    private static final float AWARD_FADE = 0.3f;
    private static final float AWARD_Y = 60f;
    private static final float HINT_Y = 22f;

    private final TumbalataGame game;
    private final int playerCount;
    private final int[] scores;
    private final int[] order;        // order[slot] = player index (slot 0 = highest score, middle of the podium)
    private final int[] places;       // places[slot] = shared rank (0 = 1st); tied scores share a place
    private final String winnerText;  // "PLAYER 2 WINS!" or "P1 & P3 WIN!"
    private final String[] names;     // names[player]
    private final Color[] colors;     // placeholder color per player
    private final int[] picks;        // picks[player] = character index (for the sprite and its colours)

    // Podium figures: the front-facing standing frame of the walk sheets, tinted like in the match. 1st place holds
    // the slipper (its white pixels drawn in the player's colour). Falls back to drawn figures if the sheets are missing.
    private static final float SPRITE_SCALE = 2.5f;        // 24 px frame drawn at 60 px (times the rank's FIGURE_SCALE)
    private static final int TOP_ROW_DEFAULT = 8;          // first drawn row of the plain frame (from the top)
    private static final int TOP_ROW_SLIPPER = 0;          // the slipper frame reaches the top (slipper on the head)
    private Texture walkSheet, slipperBody, slipperOverlay;
    private final Array<Awards.Award> awards;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    private Texture background;
    private MenuBackdrop backdrop; // shared with the menus, owned by TumbalataGame
    private Texture titleTexture;

    private float time = 0f;
    private boolean leaving = false;
    private boolean fanfarePlayed = false;

    /**
     * @param scores     one score per player (index 0 = Player 1); its length is the player count
     * @param characters the character each player picked (used for their placeholder color)
     */
    public VictoryScreen(TumbalataGame game, int[] scores, int[] characters) {
        this(game, scores, characters, null, new Array<>());
    }

    /** @param awards end-of-match awards to show under the podium (may be empty) */
    /** @param names each player's name (index 0 = Player 1), or null for "P1"..."P4" */
    public VictoryScreen(TumbalataGame game, int[] scores, int[] characters, String[] names, Array<Awards.Award> awards) {
        this.game = game;
        this.awards = awards;
        this.playerCount = MathUtils.clamp(scores.length, 1, 4);
        this.scores = new int[this.playerCount];
        for (int i = 0; i < this.playerCount; i++) {
            this.scores[i] = scores[i];
        }

        order = orderByScore(this.scores);
        places = places(this.scores, order);
        this.names = new String[this.playerCount];
        for (int i = 0; i < this.playerCount; i++) {
            this.names[i] = (names != null && i < names.length && names[i] != null) ? names[i] : GameSettings.defaultName(i);
        }
        winnerText = names == null ? winnerText(order, places) : winnerText(order, places, this.names);

        // Each player is drawn as their own character, in its colours
        colors = new Color[this.playerCount];
        picks = new int[this.playerCount];
        for (int p = 0; p < this.playerCount; p++) {
            int pick = (characters != null && p < characters.length) ? characters[p] : p;
            picks[p] = MathUtils.clamp(pick, 0, Characters.COUNT - 1);
            colors[p] = Characters.CARD_COLORS[picks[p]];
        }
    }

    /** Player indexes sorted by score, highest first; ties keep the lower player number first. */
    static int[] orderByScore(int[] scores) {
        int n = scores.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (scores[order[j]] < scores[order[j + 1]]) {
                    int tmp = order[j];
                    order[j] = order[j + 1];
                    order[j + 1] = tmp;
                }
            }
        }
        return order;
    }

    /** Shared ranks for each podium slot: the number of players with a strictly higher score (so ties share). */
    static int[] places(int[] scores, int[] order) {
        int[] places = new int[order.length];
        for (int slot = 0; slot < order.length; slot++) {
            int better = 0;
            for (int s : scores) if (s > scores[order[slot]]) better++;
            places[slot] = better;
        }
        return places;
    }

    /** "MAYA WINS!" or "MAYA & JOJO WIN!" */
    static String winnerText(int[] order, int[] places, String[] names) {
        StringBuilder text = new StringBuilder();
        int winners = 0;
        for (int slot = 0; slot < order.length && places[slot] == 0; slot++) {
            if (winners > 0) text.append(" & ");
            text.append(names[order[slot]]);
            winners++;
        }
        return text + (winners == 1 ? " WINS!" : " WIN!");
    }

    static String winnerText(int[] order, int[] places) {
        StringBuilder names = new StringBuilder();
        int winners = 0;
        for (int slot = 0; slot < order.length && places[slot] == 0; slot++) {
            if (winners > 0) names.append(" & ");
            names.append("P").append(order[slot] + 1);
            winners++;
        }
        return winners == 1 ? "PLAYER " + (order[0] + 1) + " WINS!" : names + " WIN!";
    }

    @Override
    public void show() {
        leaving = false;
        game.audio().playMusic(Audio.Track.VICTORY);

        camera = new OrthographicCamera();
        viewport = new FitViewport(MENU_WIDTH, MENU_HEIGHT, camera);
        viewport.apply(true);

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = Fonts.create();

        backdrop = game.getBackdrop();
        if (backdrop == null) background = loadTexture(BACKGROUND_FILE);
        titleTexture = loadTexture(TITLE_FILE);
        if (Gdx.files.internal("16x16 Walk-Sheet.png").exists() && Gdx.files.internal("16x16 Walkwithslipper.png").exists()) {
            walkSheet = loadTexture("16x16 Walk-Sheet.png");
            Texture[] split = SpriteSheets.splitPureWhite("16x16 Walkwithslipper.png");
            slipperBody = split[0];
            slipperOverlay = split[1];
        }
    }

    /** Loads a texture, or returns null (silently - these are optional) if the file isn't there. */
    private Texture loadTexture(String file) {
        if (!Gdx.files.internal(file).exists()) return null;
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    private float riseDelay(int rank) {
        return (playerCount - 1 - rank) * RISE_STAGGER;      // lowest rank first, winner last
    }

    private float riseProgress(int rank) {
        float p = MathUtils.clamp((time - riseDelay(rank)) / RISE_TIME, 0f, 1f);
        return 1f - (1f - p) * (1f - p) * (1f - p);          // ease-out
    }

    private float popProgress(int rank) {
        return MathUtils.clamp((time - riseDelay(rank) - RISE_TIME) / POP_TIME, 0f, 1f);
    }

    private float revealEndTime() {
        return riseDelay(0) + RISE_TIME + POP_TIME;
    }

    private Color blockColor(int rank) {
        switch (rank) {
            case 0:  return new Color(0.95f, 0.78f, 0.20f, 1f); // gold
            case 1:  return new Color(0.75f, 0.77f, 0.80f, 1f); // silver
            case 2:  return new Color(0.80f, 0.50f, 0.25f, 1f); // bronze
            default: return new Color(0.40f, 0.42f, 0.46f, 1f); // dark gray
        }
    }

    @Override
    public void render(float delta) {
        time += delta;
        if (!fanfarePlayed && time >= revealEndTime()) { // the winner has just popped in
            fanfarePlayed = true;
            game.audio().play(Audio.Sfx.VICTORY);
        }
        handleInput();

        ScreenUtils.clear(0.10f, 0.12f, 0.20f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // 1. Background: the live map like the main menu, else the image
        if (backdrop != null) {
            backdrop.renderDimmed(delta, shapeRenderer, camera.combined, MENU_WIDTH, MENU_HEIGHT, BACKDROP_DIM);
        } else if (background != null) {
            batch.begin();
            batch.draw(background, 0, 0, MENU_WIDTH, MENU_HEIGHT);
            batch.end();
        }

        // 2. Podium blocks, shadows and placeholder characters
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        if (backdrop == null && background == null) { // simple placeholder floor
            shapeRenderer.setColor(0.16f, 0.18f, 0.28f, 1f);
            shapeRenderer.rect(0, 0, MENU_WIDTH, BASE_Y);
        }

        // slot = podium position (by score order); place = shared rank, so tied players get the same block and label
        for (int slot = 0; slot < playerCount; slot++) {
            int rank = places[slot];
            float cx = SLOT_CENTER_X[slot];
            float w = BLOCK_W[rank];
            float h = BLOCK_H[rank] * riseProgress(slot);

            Color bc = blockColor(rank);
            shapeRenderer.setColor(bc);
            shapeRenderer.rect(cx - w / 2f, BASE_Y, w, h);
            shapeRenderer.setColor(Math.min(1f, bc.r + 0.15f), Math.min(1f, bc.g + 0.15f), Math.min(1f, bc.b + 0.15f), 1f);
            shapeRenderer.rect(cx - w / 2f, BASE_Y + h - 6f, w, 6f);   // lighter top edge

            float pop = popProgress(slot);
            if (pop > 0f) {
                float s = FIGURE_SCALE[rank] * pop;
                float footY = BASE_Y + BLOCK_H[rank] + ((rank == 0) ? MathUtils.sin(time * 4f) * 3f + 3f : 0f);

                // shadow on the podium top
                shapeRenderer.setColor(0f, 0f, 0f, 0.30f);
                shapeRenderer.ellipse(cx - 18f * s, BASE_Y + BLOCK_H[rank] - 4f * s, 36f * s, 8f * s);

                if (walkSheet == null) { // placeholder character: body + head in the player's color
                    Color pc = colors[order[slot]];
                    shapeRenderer.setColor(pc);
                    shapeRenderer.rect(cx - 14f * s, footY, 28f * s, 38f * s);
                    shapeRenderer.circle(cx, footY + 50f * s, 12f * s);
                    shapeRenderer.setColor(0f, 0f, 0f, 0.25f);
                    shapeRenderer.rect(cx - 14f * s, footY, 28f * s, 6f * s);   // darker "feet"
                }

                if (rank == 0) { // little crown for the winner
                    shapeRenderer.setColor(1f, 0.85f, 0.2f, 1f);
                    float crownY = footY + figureHeight(rank, s) + 2f; // just above the head (or the slipper)
                    shapeRenderer.rect(cx - 11f * s, crownY, 22f * s, 6f * s);
                    shapeRenderer.triangle(cx - 11f * s, crownY + 6f * s, cx - 6f * s, crownY + 6f * s, cx - 11f * s, crownY + 15f * s);
                    shapeRenderer.triangle(cx - 4f * s, crownY + 6f * s, cx + 4f * s, crownY + 6f * s, cx, crownY + 16f * s);
                    shapeRenderer.triangle(cx + 6f * s, crownY + 6f * s, cx + 11f * s, crownY + 6f * s, cx + 11f * s, crownY + 15f * s);
                }
            }
        }
        // dark band behind the award line, so it reads on any background
        float awardTime = time - revealEndTime();
        if (awards.size > 0 && awardTime >= 0f) {
            shapeRenderer.setColor(0f, 0f, 0f, 0.6f * awardAlpha(awardTime));
            shapeRenderer.rect(0f, AWARD_Y - 21f, MENU_WIDTH, 28f);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 3. Text
        batch.begin();
        drawFigures();

        float titleBob = MathUtils.sin(time * TITLE_BOB_SPEED) * TITLE_BOB_AMOUNT;
        if (titleTexture != null) {
            float tw = titleTexture.getWidth();
            float th = titleTexture.getHeight();
            batch.draw(titleTexture, (MENU_WIDTH - tw) / 2f, TITLE_CENTER_Y - th / 2f + titleBob, tw, th);
        } else {
            drawCentered("VICTORY!", MENU_WIDTH / 2f, TITLE_CENTER_Y + 14f + titleBob, 3f, Color.GOLD);
        }

        for (int slot = 0; slot < playerCount; slot++) {
            int rank = places[slot];
            float cx = SLOT_CENTER_X[slot];
            float riseP = riseProgress(slot);
            float blockTopY = BASE_Y + BLOCK_H[rank] * riseP;

            // rank label inside the block
            if (riseP > 0.6f) {
                drawCentered(RANK_LABELS[rank], cx, blockTopY - 14f, rank == 0 ? 1.8f : 1.3f, Color.DARK_GRAY);
            }

            // name and score above the character
            if (popProgress(slot) >= 1f) {
                float s = FIGURE_SCALE[rank];
                float aboveY = BASE_Y + BLOCK_H[rank] + figureHeight(rank, s) + (rank == 0 ? 42f : 10f); // clear of the crown
                int player = order[slot];
                drawCentered(names[player], cx, aboveY + (rank == 0 ? 26f : 18f), rank == 0 ? 1.8f : 1.2f, Color.WHITE);
                drawCentered(scores[player] + " pts", cx, aboveY, rank == 0 ? 1.3f : 1f, Color.LIGHT_GRAY);
            }
        }

        if (time >= revealEndTime()) {
            drawCentered(winnerText, MENU_WIDTH / 2f, 418f, 1.8f, Color.WHITE);
            if (((int) (time * 2f)) % 2 == 0) { // blinking hint
                drawCentered("PRESS ENTER TO CONTINUE", MENU_WIDTH / 2f, HINT_Y, 1.1f, Color.LIGHT_GRAY);
            }
            drawAward(time - revealEndTime());
        }

        batch.end();
    }

    /** Opacity of the current award: fades in and out within its time slot. */
    private static float awardAlpha(float t) {
        float local = t % AWARD_SECONDS;
        return Math.min(1f, Math.min(local, AWARD_SECONDS - local) / AWARD_FADE);
    }

    /** The current award, cycling through all of them; fades in and out. {@code t} = seconds since the reveal. */
    private void drawAward(float t) {
        if (awards.size == 0 || t < 0f) return;
        int index = (int) (t / AWARD_SECONDS) % awards.size;
        float alpha = awardAlpha(t);
        Awards.Award a = awards.get(index);

        String line = a.title + "  " + a.winnerNames(names) + "  (" + a.detail + ")";
        font.getData().setScale(1.25f);
        layout.setText(font, line);
        font.setColor(1f, 0.85f, 0.3f, alpha);
        font.draw(batch, line, (MENU_WIDTH - layout.width) / 2f, AWARD_Y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    /** Height of a podium figure from its feet (sprite or drawn placeholder), at figure scale s. */
    private float figureHeight(int rank, float s) {
        if (walkSheet == null) return 62f * s;
        int top = rank == 0 ? TOP_ROW_SLIPPER : TOP_ROW_DEFAULT;
        return (SpriteSheets.FRAME - top) * SPRITE_SCALE * s;
    }

    /**
     * The players as their characters on the podium: front-facing standing frame, tinted like in the match. 1st place
     * holds the slipper, its white pixels in the player's colour. Inside batch.begin()/end().
     */
    private void drawFigures() {
        if (walkSheet == null) return;
        for (int slot = 0; slot < playerCount; slot++) {
            float pop = popProgress(slot);
            if (pop <= 0f) continue;
            int rank = places[slot];
            float s = FIGURE_SCALE[rank] * pop;
            float size = SpriteSheets.FRAME * SPRITE_SCALE * s;
            float cx = SLOT_CENTER_X[slot];
            float footY = BASE_Y + BLOCK_H[rank] + ((rank == 0) ? MathUtils.sin(time * 4f) * 3f + 3f : 0f);
            int pick = picks[order[slot]];
            batch.setColor(Characters.TINTS[pick]);
            batch.draw(rank == 0 ? slipperBody : walkSheet, cx - size / 2f, footY, size, size,
                0, 0, SpriteSheets.FRAME, SpriteSheets.FRAME, false, false);
            if (rank == 0) {
                batch.setColor(Characters.COLORS[pick]);
                batch.draw(slipperOverlay, cx - size / 2f, footY, size, size,
                    0, 0, SpriteSheets.FRAME, SpriteSheets.FRAME, false, false);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawCentered(String text, float cx, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void handleInput() {
        InputManager.MenuInput in = game.input().menu(); // keyboard + every controller, merged
        if (in.fullscreen) game.toggleFullscreen();
        if (leaving) return; // a screen change was requested; the screen keeps drawing while the transition plays

        if (time < INPUT_DELAY) return;

        boolean confirm = in.confirm || in.back || Gdx.input.justTouched();
        if (confirm) {
            leaving = true;
            game.audio().play(Audio.Sfx.UI_CONFIRM);
            game.changeScreen(new MainMenuScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
        }
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
        backdrop = null; // owned by TumbalataGame and shared with the menu screens
        if (titleTexture != null) { titleTexture.dispose(); titleTexture = null; }
        for (Texture t : new Texture[] { walkSheet, slipperBody, slipperOverlay }) if (t != null) t.dispose();
        walkSheet = slipperBody = slipperOverlay = null;
    }
}