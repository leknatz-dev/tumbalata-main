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

    /** Sign art is drawn this many times bigger (81 x 37 art = 405 x 185 on the 1408 x 768 court view). */
    public static final float IMAGE_SCALE = 7f;

    private static final float POP_IN = 0.35f;   // seconds to pop in from nothing, overshooting a bit
    private static final Interpolation POP = new Interpolation.SwingOut(3f); // the overshoot: bigger number = more
    private static final float WOBBLE_DEGREES = 8f; // tilt shake while popping in
    private static final float BOB = 4f;            // px the sign floats up and down while it is up
    private static final float FADE_OUT = 0.3f;  // seconds to fade out at the end
    private static final float PLACEHOLDER_TEXT_SCALE = 6f;

    public enum Sign {
        GAME_START("game_start", "GAME START!", 2.0f, new Color(1f, 0.85f, 0.2f, 1f), Audio.Sfx.GAME_START),
        RUN("run", "RUN!", 1.6f, new Color(1f, 0.35f, 0.3f, 1f), Audio.Sfx.SIGN_RUN),
        HAHA("haha", "HAHA!", 1.6f, new Color(0.45f, 0.9f, 0.45f, 1f), Audio.Sfx.SIGN_HAHA),
        MY_TURN("my_turn", "MY TURN!", 1.7f, new Color(1f, 0.55f, 0.2f, 1f), Audio.Sfx.SIGN_MY_TURN),
        GOTCHA("gotcha", "GOTCHA!", 1.7f, new Color(0.95f, 0.3f, 0.9f, 1f), Audio.Sfx.SIGN_GOTCHA),
        GOOD_JOB("good_job", "GOOD JOB!", 2.5f, new Color(0.4f, 0.75f, 1f, 1f), Audio.Sfx.GAME_END),
        /** "NAME THROW!": shown with {@link #show(Sign, String, Color)}. Smaller, and the court is not blurred. */
        /** Taya picks where the can stands (after the mano). The court stays sharp so the box is visible. */
        CHOOSE_SPOT("choose_spot", "CHOOSE CAN SPOT!", 2.0f, new Color(1f, 0.85f, 0.25f, 1f), false, 4.5f, Audio.Sfx.SIGN_CHOOSE_SPOT),
        /** Taya tagged a Thrower. */
        TAGGED("tagged", "TAGGED!", 1.6f, new Color(1f, 0.3f, 0.25f, 1f), Audio.Sfx.SIGN_TAGGED),
        /** The can knocked again in the same round, everyone safe: "STREAK x2!" via {@link #show(Sign, String, Color)}. */
        STREAK("streak", "STREAK!", 1.6f, new Color(1f, 0.6f, 0.15f, 1f), Audio.Sfx.SIGN_STREAK),
        /** "NAME IS TAYA!" after the mano: shown with {@link #show(Sign, String, Color)}. */
        TAYA_PICKED("taya_picked", "IS TAYA!", 2.0f, new Color(1f, 1f, 1f, 1f), true, 5f, Audio.Sfx.SIGN_TAYA_PICKED),
        THROW_TURN("throw_turn", "THROW!", 1.6f, new Color(1f, 1f, 1f, 1f), false, 4f, Audio.Sfx.SIGN_THROW_TURN);

        /** Image file name in assets/signs/ (without .png). */
        final String file;
        /** Placeholder text drawn while there is no image. */
        final String text;
        /** Total seconds on screen. */
        public final float duration;
        final Color color;
        /** Blur the court behind it (off for quick in-play signs). */
        final boolean blur;
        final float textScale;
        /** Played when the sign pops up (assets/audio/sfx/sign_*.wav and friends). */
        final Audio.Sfx sound;

        Sign(String file, String text, float duration, Color color, Audio.Sfx sound) {
            this(file, text, duration, color, true, PLACEHOLDER_TEXT_SCALE, sound);
        }

        Sign(String file, String text, float duration, Color color, boolean blur, float textScale, Audio.Sfx sound) {
            this.sound = sound;
            this.file = file;
            this.text = text;
            this.duration = duration;
            this.color = color;
            this.blur = blur;
            this.textScale = textScale;
        }
    }

    private final EnumMap<Sign, Texture> images = new EnumMap<>(Sign.class);
    private final BitmapFont font = Fonts.create();
    private final GlyphLayout layout = new GlyphLayout();

    private Sign current;
    private float age;
    // Text and colour of the current sign (a sign with a player's name in it overrides its default text)
    private String currentText;
    private final Color currentColor = new Color();

    private final Audio audio; // plays each sign's sound when it pops up (null = silent, e.g. tests)

    public Signs(Audio audio) {
        this.audio = audio;
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
        show(sign, sign.text, sign.color);
    }

    /**
     * Shows a sign with its own text and colour, e.g. a player's turn: show(THROW_TURN, "MAYA THROW!", her colour).
     * With art, the image is drawn and the text goes underneath it.
     */
    public void show(Sign sign, String text, Color color) {
        current = sign;
        age = 0f;
        if (audio != null && sign.sound != null) audio.play(sign.sound);
        currentText = text;
        currentColor.set(color);
    }

    public boolean isShowing() {
        return current != null;
    }

    /** 0..1: how much the court behind the sign should blur. Eases in as the sign pops in and out as it fades. */
    public float blurAmount() {
        if (current == null || !current.blur) return 0f;
        float in = Math.min(1f, age / POP_IN);
        float fadeStart = current.duration - FADE_OUT;
        float out = age > fadeStart ? 1f - (age - fadeStart) / FADE_OUT : 1f;
        return MathUtils.clamp(Math.min(in, out), 0f, 1f);
    }

    public void update(float delta) {
        if (current == null) return;
        age += delta;
        if (age >= current.duration) current = null;
    }

    /** Draws the current sign centred on (cx, cy). Call inside batch.begin()/end(). */
    public void draw(Batch batch, float cx, float cy) {
        if (current == null) return;

        // Pop: springs in from nothing with an overshoot and a tilt wobble, then floats gently
        float pop = Math.min(1f, age / POP_IN);
        float scale = Math.max(0.05f, POP.apply(0f, 1f, pop)); // never 0: the font refuses scale 0
        float rotation = MathUtils.sin(age * 28f) * WOBBLE_DEGREES * (1f - pop);
        cy += MathUtils.sin(age * 4f) * BOB * pop;
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
            batch.draw(image, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, rotation,
                0, 0, image.getWidth(), image.getHeight(), false, false);
            batch.setPackedColor(previous);
            // a sign with its own text (a player's name) shows it under the art
            if (!currentText.equals(current.text)) outlinedText(batch, currentText, cx, cy - h / 2f - 20f, 3f * scale, alpha);
        } else {
            outlinedText(batch, currentText, cx, cy, current.textScale * scale, alpha);
        }
    }

    /** Big outlined text in the current colour, centred on (cx, cy). */
    private void outlinedText(Batch batch, String text, float cx, float cy, float textScale, float alpha) {
        font.getData().setScale(textScale);
        layout.setText(font, text);
        float x = cx - layout.width / 2f;
        float y = cy + layout.height / 2f;
        float o = textScale * 0.66f; // outline thickness

        font.setColor(0f, 0f, 0f, alpha);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) font.draw(batch, text, x + dx * o, y + dy * o);
            }
        }
        font.setColor(currentColor.r, currentColor.g, currentColor.b, alpha);
        font.draw(batch, text, x, y);
    }

    public void dispose() {
        for (Texture t : images.values()) t.dispose();
        images.clear();
        font.dispose();
    }
}
