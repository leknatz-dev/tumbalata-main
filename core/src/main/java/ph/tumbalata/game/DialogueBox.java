package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/**
 * Pokémon-style dialogue box for the intro: the speaker's portrait stands above the box, a name plate in their
 * colour, the text types itself out with little voice blips, and a blinking arrow when the line is done.
 * A / Enter: finish the line or go to the next one. Hold B: skip the whole dialogue.
 *
 * <p>Portraits: assets/portraits/fast.png, strong.png, sniper.png, sneaky.png (by character). The shirt (near-white
 * pixels in the lower part of the picture) is drawn in the player's colour. Box art (optional):
 * assets/dialogue/box.png, stretched with {@link #BOX_ART_CORNER} px corners kept sharp.
 */
public final class DialogueBox {
    public static final String[] PORTRAITS = {
        "portraits/fast.png", "portraits/strong.png", "portraits/sniper.png", "portraits/sneaky.png" };
    private static final String BOX_ART = "dialogue/box.png";
    private static final int BOX_ART_CORNER = 8;

    private static final float CHARS_PER_SECOND = 40f;
    private static final float SKIP_HOLD_SECONDS = 0.8f;
    private static final float BLIP_EVERY_CHARS = 2f;
    /** Voice pitch per character (FAST, STRONG, SNIPER, SNEAKY). */
    private static final float[] VOICE_PITCH = { 1.15f, 0.82f, 1.0f, 1.28f };

    // Layout (match-screen world units)
    private static final float BOX_MARGIN = 40f;
    private static final float BOX_H = 170f;
    private static final float BOX_BOTTOM = 20f;
    private static final float PORTRAIT_SCALE = 1.6f;   // 150 px wide portraits drawn 240 wide
    private static final float PORTRAIT_INSET = 30f;    // from the box's side
    private static final float PORTRAIT_SINK = 12f;     // how far the portrait tucks behind the box's top edge
    private static final float GROUP_SCALE = 0.8f;      // portraits when everyone talks at once
    private static final float TEXT_SCALE = 2.0f;
    private static final float PAD = 28f;
    private static final float HALF = 0.5f;             // box width (share of the full width) when one kid talks
    private static final float SLIDE_SPEED = 12f;       // how fast the box slides to the speaker's side
    private static final float RISE_SECONDS = 0.22f;    // a new speaker rises up from behind the box

    private final Array<DialogueScript.Line> lines;
    private final int[] characters;
    private final String[] names;
    private final Audio audio;
    private final Texture[] body = new Texture[Characters.COUNT];
    private final Texture[] shirt = new Texture[Characters.COUNT];
    private final Texture boxTexture;
    private final NinePatch boxPatch;
    private final GlyphLayout layout = new GlyphLayout();

    private int index = 0;
    private float shown = 0f;     // characters of the current line revealed so far
    private float blipAt = 0f;    // next character count to blip at
    private float skipHeld = 0f;
    private float time = 0f;
    private float boxLeft, boxRight; // box edges as a share of the full width (0 = far left, 1 = far right)
    private float rise = 0f;          // 0..1, the current speaker rising into view
    private boolean finished;

    /** @param characters picked character per player; @param names the players' names */
    public DialogueBox(Array<DialogueScript.Line> lines, int[] characters, String[] names, Audio audio) {
        this.lines = lines;
        this.characters = characters;
        this.names = names;
        this.audio = audio;
        this.finished = lines.size == 0;
        if (lines.size > 0) {
            boxLeft = targetLeft(lines.first());
            boxRight = targetRight(lines.first());
        }
        for (int c = 0; c < Characters.COUNT; c++) {
            if (!Gdx.files.internal(PORTRAITS[c]).exists()) continue;
            Texture[] split = splitShirt(PORTRAITS[c]);
            body[c] = split[0];
            shirt[c] = split[1];
        }
        if (Gdx.files.internal(BOX_ART).exists()) {
            boxTexture = new Texture(Gdx.files.internal(BOX_ART));
            boxTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            boxPatch = new NinePatch(boxTexture, BOX_ART_CORNER, BOX_ART_CORNER, BOX_ART_CORNER, BOX_ART_CORNER);
        } else {
            boxTexture = null;
            boxPatch = null;
        }
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * @param next     A / Enter pressed this frame
     * @param skipDown B held this frame (held long enough = skip everything)
     */
    public void update(float delta, boolean next, boolean skipDown) {
        if (finished) return;
        time += delta;
        skipHeld = skipDown ? skipHeld + delta : 0f;
        if (skipHeld >= SKIP_HOLD_SECONDS) {
            finished = true;
            return;
        }
        DialogueScript.Line line = lines.get(index);
        float slide = Math.min(1f, delta * SLIDE_SPEED); // box slides to the speaker's side, then they rise
        boxLeft += (targetLeft(line) - boxLeft) * slide;
        boxRight += (targetRight(line) - boxRight) * slide;
        if (Math.abs(targetLeft(line) - boxLeft) + Math.abs(targetRight(line) - boxRight) < 0.03f)
            rise = Math.min(1f, rise + delta / RISE_SECONDS);
        int length = line.text.length();
        if (shown < length && rise >= 1f) { // starts typing once the speaker is up
            shown = Math.min(length, shown + delta * CHARS_PER_SECOND);
            if (shown >= blipAt && audio != null) { // a little voice blip while the text types
                blipAt += BLIP_EVERY_CHARS;
                float pitch = line.speaker >= 0 ? VOICE_PITCH[characters[line.speaker]] : 1f;
                audio.play(Audio.Sfx.DIALOGUE_BLIP, pitch * MathUtils.random(0.96f, 1.04f));
            }
        }
        if (next) {
            if (shown < length) {
                shown = length; // first press finishes the line
            } else if (++index >= lines.size) {
                finished = true;
            } else {
                shown = 0f;
                blipAt = 0f;
                if (lines.get(index).speaker != line.speaker) rise = 0f; // a new speaker: slide over and rise again
            }
        }
    }

    /** Draws the portrait(s), the box, the name plate, the text and the hints. Call outside any batch / shape block. */
    public void draw(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, ControlHints hints,
                     float viewX, float viewY, float viewW, float alpha) {
        if (finished || lines.size == 0) return;
        DialogueScript.Line line = lines.get(index);
        float span = viewW - 2f * BOX_MARGIN;
        float boxX = viewX + BOX_MARGIN + span * boxLeft, boxY = viewY + BOX_BOTTOM, boxW = span * (boxRight - boxLeft);
        float boxTop = boxY + BOX_H;
        boolean left = isLeft(line.speaker);
        float up = Interpolation.swingOut.apply(rise);             // rising from behind the box
        float portraitAlpha = alpha * Math.min(1f, rise * 2f);
        float sink = PORTRAIT_SINK + (1f - up) * 120f;

        // 1. Portraits, standing on the box
        batch.begin();
        if (line.speaker >= 0) {
            float w = portraitWidth(line.speaker, PORTRAIT_SCALE);
            float x = left ? boxX + PORTRAIT_INSET : boxX + boxW - PORTRAIT_INSET - w;
            drawPortrait(batch, line.speaker, x, boxTop - sink, PORTRAIT_SCALE, portraitAlpha);
        } else { // everyone at once: all of them in a row
            float total = 0f;
            for (int p = 0; p < characters.length; p++) total += portraitWidth(p, GROUP_SCALE);
            float gap = 20f, x = boxX + (boxW - total - gap * (characters.length - 1)) / 2f;
            for (int p = 0; p < characters.length; p++) {
                drawPortrait(batch, p, x, boxTop - sink, GROUP_SCALE, portraitAlpha);
                x += portraitWidth(p, GROUP_SCALE) + gap;
            }
        }
        batch.end();

        // 2. Box (placeholder shapes, or art below) and name plate
        String speakerName = line.speaker >= 0 ? names[line.speaker] : "EVERYONE";
        Color plateColor = line.speaker >= 0 ? Characters.COLORS[characters[line.speaker]] : Color.GOLD;
        font.getData().setScale(1.8f);
        layout.setText(font, speakerName);
        float plateW = layout.width + 36f, plateH = 40f;
        float plateX;
        if (line.speaker < 0) plateX = boxX + 24f;
        else if (left) plateX = boxX + PORTRAIT_INSET + portraitWidth(line.speaker, PORTRAIT_SCALE) + 16f;
        else plateX = boxX + boxW - PORTRAIT_INSET - portraitWidth(line.speaker, PORTRAIT_SCALE) - 16f - plateW;
        float plateY = boxTop - 6f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (boxPatch == null) {
            shapes.setColor(1f, 1f, 1f, alpha);                                  // white frame
            shapes.rect(boxX - 5f, boxY - 5f, boxW + 10f, BOX_H + 10f);
            shapes.setColor(0.08f, 0.09f, 0.15f, 0.95f * alpha);                 // dark navy box
            shapes.rect(boxX, boxY, boxW, BOX_H);
        }
        shapes.setColor(0f, 0f, 0f, 0.8f * alpha);                               // name plate
        shapes.rect(plateX - 3f, plateY - 3f, plateW + 6f, plateH + 6f);
        shapes.setColor(plateColor.r, plateColor.g, plateColor.b, alpha);
        shapes.rect(plateX, plateY, plateW, plateH);
        if (skipHeld > 0f) {                                                      // skip progress along the bottom
            shapes.setColor(0.92f, 0.22f, 0.2f, alpha);
            shapes.rect(boxX, boxY - 12f, boxW * Math.min(1f, skipHeld / SKIP_HOLD_SECONDS), 6f);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 3. Box art, name, text, arrow
        batch.begin();
        if (boxPatch != null) {
            batch.setColor(1f, 1f, 1f, alpha);
            boxPatch.draw(batch, boxX, boxY, boxW, BOX_H);
            batch.setColor(Color.WHITE);
        }
        Fonts.drawStroked(batch, font, speakerName, plateX, plateY + plateH / 2f + layout.height / 2f, plateW, Align.center,
            new Color(1f, 1f, 1f, alpha), new Color(0f, 0f, 0f, 0.85f * alpha), 2f);

        font.getData().setScale(TEXT_SCALE);
        font.setColor(1f, 1f, 1f, alpha);
        String typed = line.text.substring(0, (int) shown);
        font.draw(batch, typed, boxX + PAD, boxTop - PAD, boxW - 2f * PAD, Align.left, true);
        if (shown >= line.text.length() && ((int) (time * 2.5f)) % 2 == 0) { // blinking "next" arrow
            font.setColor(Color.GOLD.r, Color.GOLD.g, Color.GOLD.b, alpha);
            font.getData().setScale(2.2f);
            font.draw(batch, "v", boxX + boxW - PAD - 20f, boxY + PAD + 22f);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();

        // 4. Controls
        if (hints != null) {
            hints.draw(batch, shapes, font, boxX + 190f, boxY + 26f, 1.1f, // inside the box, bottom left
                ControlHints.Icon.A, "NEXT", ControlHints.Icon.B, "HOLD TO SKIP");
        }
    }

    /** P1 / P3 talk from the left, P2 / P4 from the right. */
    private static boolean isLeft(int speaker) {
        return speaker % 2 == 0;
    }

    private static float targetLeft(DialogueScript.Line line) {
        return line.speaker < 0 || isLeft(line.speaker) ? 0f : 1f - HALF;
    }

    private static float targetRight(DialogueScript.Line line) {
        return line.speaker < 0 || !isLeft(line.speaker) ? 1f : HALF;
    }

    private float portraitWidth(int player, float scale) {
        Texture t = body[characters[player]];
        return t == null ? 0f : t.getWidth() * scale;
    }

    /** Portrait bottom-left at (x, y): the body as it is, then the shirt in the player's colour. Inside the batch. */
    private void drawPortrait(SpriteBatch batch, int player, float x, float y, float scale, float alpha) {
        int c = characters[player];
        if (body[c] == null) return;
        float w = body[c].getWidth() * scale, h = body[c].getHeight() * scale;
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(body[c], x, y, w, h);
        Color col = Characters.COLORS[c];
        batch.setColor(col.r, col.g, col.b, alpha);
        batch.draw(shirt[c], x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /**
     * Splits a portrait into {body, shirt}. The shirt is the light, colourless pixels (white and light grey, keeping
     * their shading) connected to the bottom of the picture, so teeth, eyes and headbands up on the face never
     * count. Tinting that layer turns a white shirt into the player's colour, folds included.
     */
    static Texture[] splitShirt(String file) {
        Pixmap src = new Pixmap(Gdx.files.internal(file));
        Pixmap bodyMap = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        Pixmap shirtMap = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        bodyMap.setBlending(Pixmap.Blending.None);
        shirtMap.setBlending(Pixmap.Blending.None);
        int w = src.getWidth(), h = src.getHeight();
        // Flood fill up from the bottom strip (pixmap y runs down) through light, colourless pixels
        boolean[] isShirt = new boolean[w * h];
        boolean[] seen = new boolean[w * h];
        int lowerPart = (int) (h * 0.6f); // see-through pixels only count as a path down here, away from the face
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int y = (int) (h * 0.9f); y < h; y++) // seed along the bottom strip, past any outline row
            for (int x = 0; x < w; x++) queue.add(new int[] { x, y });
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0], y = p[1];
            if (x < 0 || y < 0 || x >= w || y >= h || seen[y * w + x]) continue;
            int c = src.getPixel(x, y);
            boolean clear = (c & 255) <= 200 && y >= lowerPart; // walk around the outside of the shirt too
            if (!clear && !isShirtColour(c)) continue;
            seen[y * w + x] = true;
            isShirt[y * w + x] = !clear;
            queue.add(new int[] { x + 1, y });
            queue.add(new int[] { x - 1, y });
            queue.add(new int[] { x, y + 1 });
            queue.add(new int[] { x, y - 1 });
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getPixel(x, y);
                if (isShirt[y * w + x]) shirtMap.drawPixel(x, y, c);
                else bodyMap.drawPixel(x, y, c);
            }
        }
        Texture[] out = { new Texture(bodyMap), new Texture(shirtMap) };
        for (Texture t : out) t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        src.dispose();
        bodyMap.dispose();
        shirtMap.dispose();
        return out;
    }

    /** Light and colourless (white or light grey): a shirt colour. c is RGBA8888. */
    private static boolean isShirtColour(int c) {
        int r = (c >>> 24) & 255, g = (c >>> 16) & 255, b = (c >>> 8) & 255, a = c & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return a > 200 && min >= 120 && max - min <= 40;
    }

    public void dispose() {
        for (Texture t : body) if (t != null) t.dispose();
        for (Texture t : shirt) if (t != null) t.dispose();
        if (boxTexture != null) boxTexture.dispose();
    }
}
