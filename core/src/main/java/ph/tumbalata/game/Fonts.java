package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * The game's font: Pixelta (assets/fonts/pixelta.ttf). Every screen gets its own copy from {@link #create()} and sizes
 * it with {@code font.getData().setScale(...)} exactly as before: scale 1 is {@link #BASE_SIZE} px.
 *
 * <p>The screens use in-between scales (0.6, 1.2, 1.5...), which make a small sharp pixel font drop or double pixel
 * rows. So the glyphs are made {@link #SUPERSAMPLE} times bigger and shrunk smoothly (mipmaps), while very big text
 * (the signs) is enlarged with hard pixel edges. Falls back to libGDX's default font if the file is missing.
 */
public final class Fonts {
    private Fonts() {}

    public static final String FILE = "fonts/pixelta.ttf";
    /** Pixel size of scale 1 (about the size of the old default font). */
    public static final int BASE_SIZE = 16;
    /** Glyphs are rendered this many times bigger than BASE_SIZE, then scaled down. */
    private static final int SUPERSAMPLE = 3;

    public static BitmapFont create() {
        FileHandle file = Gdx.files.internal(FILE);
        if (!file.exists()) return new BitmapFont();
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(file);
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = BASE_SIZE * SUPERSAMPLE;
            p.genMipMaps = true;
            p.minFilter = Texture.TextureFilter.MipMapLinearLinear; // shrinking: smooth, so no missing pixel rows
            p.magFilter = Texture.TextureFilter.Nearest;            // big sign text: chunky pixels
            BitmapFont font = generator.generateFont(p, new ScaledData());
            font.getData().setScale(1f); // = BASE_SIZE
            return font;
        } finally {
            generator.dispose();
        }
    }

    /**
     * Text with a stroke around it (drawn 8 times offset in the stroke colour, then once on top), so it reads on any
     * background. Same arguments as {@link BitmapFont#draw(Batch, CharSequence, float, float, float, int, boolean)}.
     */
    public static void drawStroked(Batch batch, BitmapFont font, String text, float x, float y, float width, int align,
                                   Color fill, Color stroke, float thickness) {
        font.setColor(stroke);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) font.draw(batch, text, x + dx * thickness, y + dy * thickness, width, align, false);
            }
        }
        font.setColor(fill);
        font.draw(batch, text, x, y, width, align, false);
    }

    /** The scale last set with setScale (what the screens think in), for any font. */
    public static float scaleOf(BitmapFont font) {
        float s = font.getData().scaleX;
        return font.getData() instanceof ScaledData ? s * SUPERSAMPLE : s;
    }

    /** Font data where scale 1 means BASE_SIZE, although the glyphs are SUPERSAMPLE times bigger. */
    private static final class ScaledData extends FreeTypeFontGenerator.FreeTypeBitmapFontData {
        @Override
        public void setScale(float scaleX, float scaleY) {
            super.setScale(scaleX / SUPERSAMPLE, scaleY / SUPERSAMPLE);
        }
    }
}
