package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

/**
 * How the {@link Can} looks: standing, spinning while it flies or rolls (faster the faster it goes), its shadow, and
 * lying in Taya's hands. Drawn between match steps like {@link PlayerView}.
 */
public final class CanView {
    private static final float SPIN_DEGREES_PER_SECOND = 600f;

    public final Can can;
    /** Where to draw the can this frame (its spot on the ground), and how high above it. */
    public final Vector2 drawPos = new Vector2();
    public float drawZ;

    private final Animation<TextureRegion> spin;
    private final TextureRegion front;
    private float spinTime = 0f;
    private float rotation = 0f;
    private final Vector2 from = new Vector2();
    private float fromZ;

    /** The sheet is {@code cols} x {@code rows} frames; the first one is the can standing, facing front. */
    public CanView(Can can, Texture sheet, int cols, int rows, float frameSeconds) {
        this.can = can;
        TextureRegion[][] grid = TextureRegion.split(sheet, sheet.getWidth() / cols, sheet.getHeight() / rows);
        TextureRegion[] frames = new TextureRegion[cols * rows];
        int i = 0;
        for (TextureRegion[] row : grid) for (TextureRegion f : row) frames[i++] = f;
        spin = new Animation<>(frameSeconds, frames);
        spin.setPlayMode(Animation.PlayMode.LOOP);
        front = frames[0];
        snap();
    }

    /** Call right before each match step. */
    public void beforeStep() {
        from.set(can.position);
        fromZ = can.zPosition;
    }

    /** Call right after each match step: the spin moves on with the match, as fast as the can moves. */
    public void afterStep(float delta) {
        if (!can.isHit) { // standing again (put back, or a new round): the next spin starts from the front
            spinTime = 0f;
            rotation = 0f;
        } else if (can.velocity.len() > 0 || can.zPosition > 0) {
            float rate = can.spinRate();
            spinTime += delta * rate;
            rotation = (rotation + delta * SPIN_DEGREES_PER_SECOND * rate) % 360f;
        }
    }

    /** Draw position for this frame, {@code alpha} (0..1) of the way from before the last step to now. */
    public void follow(float alpha) {
        boolean slid = PlayerView.between(drawPos, from, can.position, alpha);
        drawZ = slid ? fromZ + (can.zPosition - fromZ) * alpha : can.zPosition;
    }

    /** Draw exactly where the can is (when the match isn't stepping). */
    public void snap() {
        from.set(can.position);
        drawPos.set(can.position);
        fromZ = drawZ = can.zPosition;
    }

    /** The frame to draw: spinning while it flies or rolls, else standing. */
    public TextureRegion frame() {
        return can.isSpinning() ? spin.getKeyFrame(spinTime, true) : front;
    }

    /** Inside a Filled ShapeRenderer block. The shadow shrinks as the can goes up. */
    public void drawShadow(ShapeRenderer shapes) {
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.35f);
        float scale = Math.max(0.4f, 1.0f - (drawZ / 250f));
        float w = can.width * scale, h = 8f * scale;
        shapes.ellipse(drawPos.x - w / 2f, drawPos.y - h / 2f, w, h);
    }

    /** On the court, lifted off its shadow by its height. Inside batch.begin()/end(). */
    public void draw(SpriteBatch batch) {
        float w = can.width, h = can.height;
        batch.draw(frame(), drawPos.x - w / 2f, drawPos.y - h / 2f + drawZ, w / 2f, h / 2f, w, h, 1f, 1f,
            can.isSpinning() ? rotation : 0f);
    }

    /** Lying sideways in a carrier's hands, centred on (x, y) at {@code scale}. Inside batch.begin()/end(). */
    public void drawCarried(SpriteBatch batch, float x, float y, float scale) {
        float w = can.width, h = can.height;
        batch.draw(frame(), x - w / 2f, y - h / 2f, w / 2f, h / 2f, w, h, scale, scale, 90f);
    }
}
