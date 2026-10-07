package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
 * "ENTER YOUR NAMES": one row per player, between the player count and the character select (placeholder look).
 *
 * <ul>
 *   <li>Keyboard: type into the highlighted row, Backspace deletes, Enter confirms it and moves on, Up / Down or a
 *       mouse click changes the row, Esc goes back. While this screen is open the keyboard only types (it does not
 *       move Player 1, and M doesn't mute).</li>
 *   <li>Controllers, each player on their own row, arcade style: Up / Down change the letter, Left / Right move along,
 *       A (or Start) confirms, B deletes (or un-confirms), Select goes back.</li>
 * </ul>
 * When everyone has confirmed, the character select opens. Names start blank every game (they are not kept), and an
 * empty name becomes the default ("P1"...).
 */
public class NameEntryScreen implements Screen {
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;
    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;
    private static final float BACKDROP_DIM = 0.35f;

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";

    // --- LAYOUT ---
    private static final float HEADING_Y = 430f;
    private static final float ROW_W = 380f;
    private static final float ROW_H = 46f;
    private static final float ROW_GAP = 14f;
    private static final float ROWS_TOP = 370f;  // top edge of the first row
    private static final float HINT_Y = 80f;

    private final TumbalataGame game;
    private final int playerCount;
    private final StringBuilder[] names;
    private final int[] cursor;        // per player: character position the pad edits
    private final boolean[] ready;
    private final Rectangle[] rows;
    private int keyboardRow = 0;       // the row the keyboard types into

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private ControlHints hints;
    private MenuBackdrop backdrop;
    private float time = 0f;
    private boolean leaving = false;
    private final Vector2 mouse = new Vector2();

    private final InputAdapter typing = new InputAdapter() {
        @Override
        public boolean keyTyped(char c) {
            if (leaving || game.isTransitioning()) return false;
            if (c == '\b') {
                backspace(keyboardRow);
            } else if (c == '\r' || c == '\n') {
                confirm(keyboardRow);
            } else if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '.') {
                type(keyboardRow, Character.toUpperCase(c));
            }
            return true;
        }
    };

    public NameEntryScreen(TumbalataGame game, int playerCount) {
        this.game = game;
        this.playerCount = playerCount;
        names = new StringBuilder[playerCount];
        cursor = new int[playerCount];
        ready = new boolean[playerCount];
        rows = new Rectangle[playerCount];
        for (int p = 0; p < playerCount; p++) {
            names[p] = new StringBuilder(); // blank every game: everyone types their name again
            cursor[p] = 0;
            rows[p] = new Rectangle((MENU_WIDTH - ROW_W) / 2f, ROWS_TOP - ROW_H - p * (ROW_H + ROW_GAP), ROW_W, ROW_H);
        }
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    private void type(int p, char c) {
        if (ready[p] || names[p].length() >= GameSettings.MAX_NAME_LENGTH) return;
        names[p].insert(cursor[p], c);
        cursor[p]++;
    }

    private void backspace(int p) {
        if (ready[p]) {
            ready[p] = false;
            game.audio().play(Audio.Sfx.UI_BACK);
            return;
        }
        if (cursor[p] == 0) return;
        names[p].deleteCharAt(cursor[p] - 1);
        cursor[p]--;
    }

    private void confirm(int p) {
        if (ready[p]) return;
        ready[p] = true;
        game.audio().play(Audio.Sfx.UI_CONFIRM);
        for (int i = 1; i <= playerCount; i++) { // keyboard moves on to the next row still being typed
            int next = (p + i) % playerCount;
            if (!ready[next]) {
                keyboardRow = next;
                break;
            }
        }
    }

    /** Pad editing: the letter at the cursor steps through LETTERS (adding one at the end of the name). */
    private void cycleLetter(int p, int step) {
        if (ready[p]) return;
        StringBuilder n = names[p];
        if (cursor[p] >= n.length()) {
            if (n.length() >= GameSettings.MAX_NAME_LENGTH) return;
            n.append(step > 0 ? 'A' : LETTERS.charAt(LETTERS.length() - 2));
            return;
        }
        int i = LETTERS.indexOf(n.charAt(cursor[p]));
        if (i < 0) i = 0;
        n.setCharAt(cursor[p], LETTERS.charAt((i + step + LETTERS.length()) % LETTERS.length()));
    }

    private String finalName(int p) {
        String n = names[p].toString().trim();
        return n.isEmpty() ? GameSettings.defaultName(p) : n;
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    @Override
    public void show() {
        leaving = false;
        game.audio().playMusic(Audio.Track.MENU);
        camera = new OrthographicCamera();
        viewport = new FitViewport(MENU_WIDTH, MENU_HEIGHT, camera);
        viewport.apply(true);
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = Fonts.create();
        hints = new ControlHints();
        backdrop = game.getBackdrop();
        game.input().setTextEntry(true);
        Gdx.input.setInputProcessor(typing);
    }

    @Override
    public void render(float delta) {
        time += delta;
        handleInput();

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);
        if (backdrop != null) {
            backdrop.renderDimmed(delta, shapeRenderer, camera.combined, MENU_WIDTH, MENU_HEIGHT, BACKDROP_DIM);
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int p = 0; p < playerCount; p++) {
            Rectangle r = rows[p];
            Color c = Player.SLOT_COLORS[p];
            if (p == keyboardRow && !ready[p]) { // keyboard focus: white frame
                shapeRenderer.setColor(1f, 1f, 1f, 0.9f);
                shapeRenderer.rect(r.x - 3f, r.y - 3f, r.width + 6f, r.height + 6f);
            }
            shapeRenderer.setColor(0f, 0f, 0f, 0.7f);
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
            shapeRenderer.setColor(c.r, c.g, c.b, 1f);
            shapeRenderer.rect(r.x, r.y, 52f, r.height);                // slot colour chip
            if (ready[p]) {
                shapeRenderer.setColor(c.r, c.g, c.b, 0.35f);
                shapeRenderer.rect(r.x + 52f, r.y, r.width - 52f, r.height);
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        drawCentered("ENTER YOUR NAMES", MENU_WIDTH / 2f, HEADING_Y + MathUtils.sin(time * 2.5f) * 5f, 2f, Color.WHITE);
        boolean blink = ((int) (time * 2.5f)) % 2 == 0;
        for (int p = 0; p < playerCount; p++) {
            Rectangle r = rows[p];
            float textY = r.y + r.height / 2f + 8f;
            font.getData().setScale(1.3f);
            font.setColor(Color.BLACK);
            layout.setText(font, GameSettings.defaultName(p));
            font.draw(batch, GameSettings.defaultName(p), r.x + (52f - layout.width) / 2f, textY);

            String shown = names[p].length() == 0 && ready[p] ? GameSettings.defaultName(p) : names[p].toString();
            font.getData().setScale(1.6f);
            font.setColor(names[p].length() == 0 ? Color.GRAY : Color.WHITE);
            String text = shown.isEmpty() ? "TYPE A NAME" : shown;
            float x = r.x + 66f;
            font.draw(batch, text, x, textY + 2f);
            if (!ready[p] && blink) {
                // cursor: under the character being edited
                layout.setText(font, shown.substring(0, Math.min(cursor[p], shown.length())));
                float cx = x + (shown.isEmpty() ? 0f : layout.width);
                font.setColor(Player.SLOT_COLORS[p]);
                font.draw(batch, "_", cx, textY + 2f);
            }
            font.getData().setScale(1.1f);
            font.setColor(ready[p] ? Color.GOLD : Color.LIGHT_GRAY);
            String status = ready[p] ? "READY" : names[p].length() + "/" + GameSettings.MAX_NAME_LENGTH;
            layout.setText(font, status);
            font.draw(batch, status, r.x + r.width - layout.width - 10f, textY);
        }
        drawCentered("KEYBOARD: TYPE, ENTER = READY, UP/DOWN = ROW, BACKSPACE = DELETE", MENU_WIDTH / 2f, HINT_Y - 34f, 0.8f,
            Color.LIGHT_GRAY);
        batch.end();

        // Controller hints
        hints.draw(batch, shapeRenderer, font, MENU_WIDTH / 2f, HINT_Y, 0.95f,
            ControlHints.Icon.DPAD_UP_DOWN, "LETTER", ControlHints.Icon.DPAD_RIGHT, "NEXT SPACE",
            ControlHints.Icon.A, "READY", ControlHints.Icon.B, "UNREADY");
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
        if (game.input().menu().fullscreen) game.toggleFullscreen();
        if (leaving || game.input().isMenuLocked()) return;

        // Keyboard row and leaving
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            goBack();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            keyboardRow = (keyboardRow + 1) % playerCount;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) keyboardRow = (keyboardRow + playerCount - 1) % playerCount;
        if (Gdx.input.justTouched()) {
            mouse.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(mouse);
            for (int p = 0; p < playerCount; p++) if (rows[p].contains(mouse.x, mouse.y)) keyboardRow = p;
        }

        // Each pad edits its own row (keyboard schemes are off on this screen, so this is controllers only)
        for (int p = 0; p < playerCount; p++) {
            PlayerInput in = game.input().player(p);
            if (in.selectPressed) {
                goBack();
                return;
            }
            if (in.upPressed) cycleLetter(p, 1);
            if (in.downPressed) cycleLetter(p, -1);
            if (in.rightPressed && !ready[p] && cursor[p] < names[p].length()
                && cursor[p] < GameSettings.MAX_NAME_LENGTH - 1) cursor[p]++;
            if (in.leftPressed && !ready[p] && cursor[p] > 0) cursor[p]--;
            if (in.aPressed || in.startPressed) confirm(p);
            if (in.bPressed) backspace(p);
        }

        boolean allReady = true;
        for (boolean r : ready) allReady &= r;
        if (allReady) startCharacterSelect();
    }

    private void goBack() {
        leaving = true;
        game.audio().play(Audio.Sfx.UI_BACK);
        game.changeScreen(new PlayerSelectScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
    }

    private void startCharacterSelect() {
        leaving = true;
        String[] chosen = new String[playerCount];
        for (int p = 0; p < playerCount; p++) {
            chosen[p] = finalName(p);
        }
        game.changeScreen(new CharacterSelectScreen(game, playerCount, chosen), MENU_WINDOW_W, MENU_WINDOW_H);
    }

    @Override
    public void resize(int width, int height) {
        if (viewport != null) viewport.update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        game.input().setTextEntry(false);
        if (Gdx.input.getInputProcessor() == typing) Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        hide();
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapeRenderer != null) { shapeRenderer.dispose(); shapeRenderer = null; }
        if (font != null) { font.dispose(); font = null; }
        if (hints != null) { hints.dispose(); hints = null; }
        backdrop = null; // shared, owned by TumbalataGame
    }
}
