package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

import java.util.EnumMap;

/**
 * Big pop-up signs in the middle of the match screen ("GAME START!", "RUN!", ...). One sign at a time: showing a new
 * one replaces the current one. Each sign pops in, holds, then fades out.
 *
 * <p>Art: put {@code assets/signs/<file>.png} (see {@link Sign}). It is drawn centred at its own size times
 * {@link #IMAGE_SCALE}. Until a file exists, the sign's text is drawn as a big outlined placeholder.
 */
public final class Signs {
    private static final String DIR = "signs/";

    /** Image size multiplier (pixel art drawn bigger). */
    public static final float IMAGE_SCALE = 1f;

    private static final float POP_IN = 0.25f;   // seconds to pop in (with a little overshoot)
    private static final float FADE_OUT = 0.3f;  // seconds to fade out at the end
    private static final float PLACEHOLDER_TEXT_SCALE = 6f;

    public enum Sign {
        GAME_START("game_start", "GAME START!", 1.4f, new Color(1f, 0.85f, 0.2f, 1f)),
        RUN("run", "RUN!", 1.1f, new Color(1f, 0.35f, 0.3f, 1f)),
        HAHA("haha", "HAHA!", 1.1f, new Color(0.45f, 0.9f, 0.45f, 1f)),
        MY_TURN("my_turn", "MY TURN!", 1.2f, new Color(1f, 0.55f, 0.2f, 1f)),
        GOTCHA("gotcha", "GOTCHA!", 1.2f, new Color(0.95f, 0.3f, 0.9f, 1f)),
        GOOD_JOB("good_job", "GOOD JOB!", 1.8f, new Color(0.4f, 0.75f, 1f, 1f));

        /** Image file name in assets/signs/ (without .png). */
        final String file;
        /** Placeholder text drawn while there is no image. */
        final String text;
        /** Total seconds on screen. */
        public final float duration;
        final Color color;

        Sign(String file, String text, float duration, Color color) {
            this.file = file;
            this.text = text;
            this.duration = duration;
            this.color = color;
        }
    }

    private final EnumMap<Sign, Texture> images = new EnumMap<>(Sign.class);
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private Sign current;
    private float age;

    public Signs() {
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        for (Sign sign : Sign.values()) {
            FileHandle file = Gdx.files.internal(DIR + sign.file + ".png");
            if (!file.exists()) continue; // placeholder text until the art is made
            Texture t = new Texture(file);
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            images.put(sign, t);
        }
    }

    public void show(Sign sign) {
        current = sign;
        age = 0f;
    }

    public boolean isShowing() {
        return current != null;
    }

    public void update(float delta) {
        if (current == null) return;
        age += delta;
        if (age >= current.duration) current = null;
    }

    /** Draws the current sign centred on (cx, cy). Call inside batch.begin()/end(). */
    public void draw(Batch batch, float cx, float cy) {
        if (current == null) return;

        float scale;
        if (age < POP_IN) {
            scale = Interpolation.swingOut.apply(0.3f, 1f, age / POP_IN);
        } else {
            scale = 1f;
        }
        float fadeStart = current.duration - FADE_OUT;
        float alpha = 1f;
        if (age > fadeStart) {
            float t = MathUtils.clamp((age - fadeStart) / FADE_OUT, 0f, 1f);
            alpha = 1f - t;
            scale *= 1f + 0.15f * t; // grows a little as it fades
        }

        Texture image = images.get(current);
        if (image != null) {
            float w = image.getWidth() * IMAGE_SCALE * scale;
            float h = image.getHeight() * IMAGE_SCALE * scale;
            float previous = batch.getPackedColor();
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(image, cx - w / 2f, cy - h / 2f, w, h);
            batch.setPackedColor(previous);
        } else {
            drawPlaceholder(batch, current, cx, cy, scale, alpha);
        }
    }

    /** Big outlined text in the sign's colour. */
    private void drawPlaceholder(Batch batch, Sign sign, float cx, float cy, float scale, float alpha) {
        font.getData().setScale(PLACEHOLDER_TEXT_SCALE * scale);
        layout.setText(font, sign.text);
        float x = cx - layout.width / 2f;
        float y = cy + layout.height / 2f;
        float o = 4f * scale; // outline thickness

        font.setColor(0f, 0f, 0f, alpha);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) font.draw(batch, sign.text, x + dx * o, y + dy * o);
            }
        }
        font.setColor(sign.color.r, sign.color.g, sign.color.b, alpha);
        font.draw(batch, sign.text, x, y);
    }

    public void dispose() {
        for (Texture t : images.values()) t.dispose();
        images.clear();
        font.dispose();
    }
}
