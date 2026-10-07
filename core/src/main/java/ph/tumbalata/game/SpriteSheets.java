package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/** Small helpers for the character sprite sheets, shared by the match and victory screens. */
public final class SpriteSheets {
    private SpriteSheets() {}

    /** The walk sheets: 4 columns x 5 rows of 24 x 24 frames; row 0 faces the camera. */
    public static final int FRAME = 24;

    /**
     * Splits a sheet into {body, slipper}: every pure white pixel (#FFFFFF) is the held slipper, everything else the
     * body. The slipper layer is tinted in the player's colour, the body in the character's.
     */
    public static Texture[] splitPureWhite(String file) {
        Pixmap src = new Pixmap(Gdx.files.internal(file));
        Pixmap body = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        Pixmap white = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        body.setBlending(Pixmap.Blending.None);
        white.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getPixel(x, y);
                if (c == 0xFFFFFFFF) white.drawPixel(x, y, c);
                else body.drawPixel(x, y, c);
            }
        }
        Texture[] out = { new Texture(body), new Texture(white) };
        for (Texture t : out) t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        src.dispose();
        body.dispose();
        white.dispose();
        return out;
    }
}
