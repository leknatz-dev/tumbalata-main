package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Controller hints for the menus: a centred row of icon + label pairs, e.g. [yellow button] LOCK IN [red button] CHANGE.
 * Buttons use the button art (assets/yellowbutton.png = A, redbutton.png = B); the D-pad is drawn with shapes, the
 * directions that matter highlighted with arrows. Call {@link #draw} outside any batch / shape block.
 */
public final class ControlHints {
    public enum Icon { A, B, DPAD_UP_DOWN, DPAD_RIGHT, DPAD_LEFT_RIGHT }

    private static final float ICON = 22f;      // icon size at scale 1
    private static final float ICON_GAP = 6f;   // icon -> its label
    private static final float PAIR_GAP = 22f;  // between pairs
    private static final Color PAD = new Color(0.22f, 0.22f, 0.26f, 1f);
    private static final Color PAD_LIT = new Color(0.95f, 0.95f, 0.95f, 1f);

    private final Texture yellow, red;
    private final GlyphLayout layout = new GlyphLayout();

    public ControlHints() {
        yellow = load("yellowbutton.png");
        red = load("redbutton.png");
    }

    private static Texture load(String file) {
        if (!Gdx.files.internal(file).exists()) return null;
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    /**
     * Draws the row centred on (cx, y). {@code pairs} alternates {@link Icon} and label, e.g.
     * {@code draw(batch, shapes, font, 350, 60, 1f, Icon.A, "LOCK IN", Icon.B, "CHANGE")}.
     */
    public void draw(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, float cx, float y, float scale,
                     Object... pairs) {
        float icon = ICON * scale;
        font.getData().setScale(scale);
        float total = 0f;
        float[] widths = new float[pairs.length / 2];
        for (int i = 0; i < widths.length; i++) {
            layout.setText(font, (String) pairs[i * 2 + 1]);
            widths[i] = layout.width;
            total += icon + ICON_GAP * scale + layout.width + (i > 0 ? PAIR_GAP * scale : 0f);
        }
        float textH = layout.height;
        float x = cx - total / 2f;

        // 1. D-pads (shapes)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float px = x;
        for (int i = 0; i < widths.length; i++) {
            Icon kind = (Icon) pairs[i * 2];
            if (kind != Icon.A && kind != Icon.B) drawDpad(shapes, kind, px + icon / 2f, y, icon);
            else if ((kind == Icon.A ? yellow : red) == null) { // no art: a plain coloured circle
                shapes.setColor(0f, 0f, 0f, 0.7f);
                shapes.circle(px + icon / 2f, y, icon / 2f, 16);
                shapes.setColor(kind == Icon.A ? Color.GOLD : Color.FIREBRICK);
                shapes.circle(px + icon / 2f, y, icon / 2f - 2f, 16);
            }
            px += icon + ICON_GAP * scale + widths[i] + PAIR_GAP * scale;
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 2. Button art and labels
        batch.begin();
        px = x;
        for (int i = 0; i < widths.length; i++) {
            Icon kind = (Icon) pairs[i * 2];
            Texture art = kind == Icon.A ? yellow : kind == Icon.B ? red : null;
            if (art != null) batch.draw(art, px, y - icon / 2f, icon, icon);
            float tx = px + icon + ICON_GAP * scale;
            font.setColor(0f, 0f, 0f, 0.85f);
            font.draw(batch, (String) pairs[i * 2 + 1], tx + 1.5f, y + textH / 2f - 1.5f);
            font.setColor(Color.WHITE);
            font.draw(batch, (String) pairs[i * 2 + 1], tx, y + textH / 2f);
            px += icon + ICON_GAP * scale + widths[i] + PAIR_GAP * scale;
        }
        batch.end();
        font.getData().setScale(1f);
    }

    /** A plus-shaped D-pad; the arms that matter are lit, with a small arrow. */
    private static void drawDpad(ShapeRenderer s, Icon kind, float cx, float cy, float size) {
        float arm = size / 3f;
        s.setColor(0f, 0f, 0f, 0.7f);                                  // outline
        s.rect(cx - arm / 2f - 1.5f, cy - size / 2f - 1.5f, arm + 3f, size + 3f);
        s.rect(cx - size / 2f - 1.5f, cy - arm / 2f - 1.5f, size + 3f, arm + 3f);
        s.setColor(PAD);
        s.rect(cx - arm / 2f, cy - size / 2f, arm, size);
        s.rect(cx - size / 2f, cy - arm / 2f, size, arm);
        boolean up = kind == Icon.DPAD_UP_DOWN, down = up;
        boolean right = kind == Icon.DPAD_RIGHT || kind == Icon.DPAD_LEFT_RIGHT, left = kind == Icon.DPAD_LEFT_RIGHT;
        s.setColor(PAD_LIT);
        float tip = size / 2f - 1f, back = arm / 2f + 1f, w = arm / 2f - 1f;
        if (up) s.triangle(cx, cy + tip, cx - w, cy + back, cx + w, cy + back);
        if (down) s.triangle(cx, cy - tip, cx - w, cy - back, cx + w, cy - back);
        if (right) s.triangle(cx + tip, cy, cx + back, cy - w, cx + back, cy + w);
        if (left) s.triangle(cx - tip, cy, cx - back, cy - w, cx - back, cy + w);
    }

    public void dispose() {
        if (yellow != null) yellow.dispose();
        if (red != null) red.dispose();
    }
}
