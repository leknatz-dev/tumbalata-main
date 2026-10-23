package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * The square pause / settings panel in the middle of the match screen (placeholder art: plain shapes and text).
 * It only handles navigation and drawing; GameScreen decides what each button does.
 * Up / Down to move, A / Enter to press, B / Esc / Select to resume. The mouse works too.
 */
public final class PauseMenu {
    public enum Item {
        RESUME, RESTART_ROUND, SOUND, STREET_EVENTS, EXIT
    }

    private Item[] items = Item.values(); // the buttons shown, top to bottom

    // --- LAYOUT (match-screen world units) ---
    private static final float PANEL_SIZE = 400f;
    private static final float BUTTON_W = 300f;
    private static final float BUTTON_H = 44f;
    private static final float BUTTON_GAP = 12f;
    private static final float TITLE_SPACE = 80f;  // from the panel top to the first button

    private static final Color PANEL = new Color(0.10f, 0.11f, 0.16f, 0.92f);
    private static final Color BORDER = new Color(0.95f, 0.85f, 0.35f, 1f);
    private static final Color BUTTON = new Color(0.22f, 0.24f, 0.32f, 1f);
    private static final Color BUTTON_SELECTED = new Color(0.95f, 0.78f, 0.25f, 1f);

    private final Rectangle panel = new Rectangle();
    private Rectangle[] buttons = new Rectangle[items.length];
    private float centreX, centreY;
    private final GlyphLayout layout = new GlyphLayout();
    private int selected = 0;

    public PauseMenu() {
        for (int i = 0; i < items.length; i++) buttons[i] = new Rectangle();
    }

    /** Shows only these buttons, in this order (a match with other phones has no RESTART ROUND, for example). */
    public void setItems(Item... shown) {
        items = shown;
        buttons = new Rectangle[shown.length];
        for (int i = 0; i < shown.length; i++) buttons[i] = new Rectangle();
        selected = 0;
        layout(centreX, centreY);
    }

    /** Lays the panel out centred on (cx, cy). */
    public void layout(float cx, float cy) {
        centreX = cx;
        centreY = cy;
        panel.set(cx - PANEL_SIZE / 2f, cy - PANEL_SIZE / 2f, PANEL_SIZE, PANEL_SIZE);
        float y = panel.y + panel.height - TITLE_SPACE - BUTTON_H;
        for (Rectangle b : buttons) {
            b.set(cx - BUTTON_W / 2f, y, BUTTON_W, BUTTON_H);
            y -= BUTTON_H + BUTTON_GAP;
        }
    }

    /** Back to the first button (call when the menu opens). */
    public void reset() {
        selected = 0;
    }

    /**
     * Reads the menu input for one frame.
     * @param mouse     mouse position in world units
     * @param mouseMoved true if the mouse moved this frame (hover selects)
     * @param clicked   true if the mouse was clicked this frame
     * @return the item pressed this frame, or null
     */
    public Item handle(InputManager.MenuInput in, Vector2 mouse, boolean mouseMoved, boolean clicked, Audio audio) {
        int before = selected;
        if (in.up) selected = (selected + items.length - 1) % items.length;
        if (in.down) selected = (selected + 1) % items.length;

        int hovered = -1;
        for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(mouse.x, mouse.y)) hovered = i;
        if (hovered >= 0 && (mouseMoved || clicked)) selected = hovered;
        if (selected != before) audio.play(Audio.Sfx.UI_MOVE);

        if (in.confirm || (clicked && hovered >= 0)) return items[selected];
        if (in.back) return Item.RESUME;
        return null;
    }

    /**
     * Draws the panel. Call outside any batch/shape block.
     * @param title  the heading ("PAUSED", or "MENU" when the match goes on)
     * @param labels button text, one per {@link Item} (by its ordinal)
     */
    public void draw(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font, String title, String[] labels, float alpha) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BORDER.r, BORDER.g, BORDER.b, alpha);
        shapes.rect(panel.x - 4f, panel.y - 4f, panel.width + 8f, panel.height + 8f);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, PANEL.a * alpha);
        shapes.rect(panel.x, panel.y, panel.width, panel.height);
        for (int i = 0; i < buttons.length; i++) {
            Color c = i == selected ? BUTTON_SELECTED : BUTTON;
            Rectangle b = buttons[i];
            shapes.setColor(c.r, c.g, c.b, alpha);
            shapes.rect(b.x, b.y, b.width, b.height);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        float scale = Fonts.scaleOf(font); // restored after drawing
        font.getData().setScale(2.2f);
        font.setColor(1f, 1f, 1f, alpha);
        drawCentered(batch, font, title, panel.x + panel.width / 2f, panel.y + panel.height - 26f);
        font.getData().setScale(1.4f);
        for (int i = 0; i < buttons.length; i++) {
            Rectangle b = buttons[i];
            if (i == selected) font.setColor(0.1f, 0.1f, 0.12f, alpha);
            else font.setColor(1f, 1f, 1f, alpha);
            String label = labels[items[i].ordinal()];
            layout.setText(font, label);
            drawCentered(batch, font, label, b.x + b.width / 2f, b.y + (b.height + layout.height) / 2f);
        }
        font.getData().setScale(scale);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawCentered(SpriteBatch batch, BitmapFont font, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
    }
}
