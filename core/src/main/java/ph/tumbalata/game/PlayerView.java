package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

/**
 * How a {@link Player} looks: the walk animations (plain, holding the slipper, carrying the can), the character tint,
 * the shadow and their slipper on the ground. It only reads the player.
 *
 * <p>The match moves in fixed steps ({@link FixedStep}); the screen may draw more often. The view remembers where the
 * player was before the last step and draws them part of the way to where they are now ({@link #follow}), so they
 * move smoothly on a 90 or 120 Hz screen. Anything drawn with the player (name tag, button prompts, aim arrow) uses
 * {@link #drawPos} too, so it all moves together.
 */
public final class PlayerView {
    public static final float WIDTH = 48f;
    public static final float HEIGHT = 48f;
    private static final float FRAME_SECONDS = 0.12f;
    private static final float SHADOW_W = 28f;
    private static final float SHADOW_H = 10f;
    private static final float SHADOW_OFFSET_Y = -19f; // from the player's position to the centre of the shadow
    private static final float SHADOW_ALPHA = 0.30f;
    private static final float SLIPPER_RADIUS = 8f;    // the placeholder slipper oval
    /** Further than this in one step is a jump (a respawn, a slipper picked up), not movement: no sliding across. */
    static final float SNAP_DISTANCE = 64f;

    public final Player player;
    /** Where to draw the player this frame (between the last two steps). */
    public final Vector2 drawPos = new Vector2();
    /** Where to draw their slipper this frame. */
    public final Vector2 slipperDrawPos = new Vector2();
    /** The slipper's colour when there is no slipper art: brown, half the player's colour. */
    public final Color slipperColor = new Color();

    private final PlayerAnimation walk, withSlipper, withCan, slipperOverlay;
    private final Color tint;
    private final Vector2 from = new Vector2(), slipperFrom = new Vector2();

    /**
     * Sheets may be null (no sprite then). {@code slipperOverlaySheet}: the held slipper on its own, white, tinted in the
     * player's colour on top of the body (see {@link SpriteSheets#splitPureWhite}).
     */
    public PlayerView(Player player, Texture walkSheet, Texture slipperSheet, Texture slipperOverlaySheet, Texture canSheet) {
        this.player = player;
        walk = walkSheet == null ? null : new PlayerAnimation(walkSheet, FRAME_SECONDS);
        withSlipper = slipperSheet == null ? null : new PlayerAnimation(slipperSheet, FRAME_SECONDS);
        slipperOverlay = slipperOverlaySheet == null ? null : new PlayerAnimation(slipperOverlaySheet, FRAME_SECONDS);
        withCan = canSheet == null ? null : new PlayerAnimation(canSheet, FRAME_SECONDS);
        tint = new Color(Characters.TINTS[player.characterIndex]);
        slipperColor.set(Color.BROWN).lerp(player.color(), 0.5f);
        snap();
    }

    /** Call right before each match step: remembers where things were. */
    public void beforeStep() {
        from.set(player.position);
        slipperFrom.set(player.slipper.position);
    }

    /** Call right after each match step: the walk animation moves on with the match. */
    public void afterStep(float delta) {
        if (player.hasCan && withCan != null) {
            withCan.update(delta, player.velocity);
        } else if (player.hasSlipper && withSlipper != null) {
            withSlipper.update(delta, player.velocity);
            if (slipperOverlay != null) slipperOverlay.update(delta, player.velocity); // same frames, kept in step
        } else if (walk != null) {
            walk.update(delta, player.velocity);
        }
    }

    /** Draw positions for this frame, {@code alpha} (0..1) of the way from before the last step to now. */
    public void follow(float alpha) {
        between(drawPos, from, player.position, alpha);
        between(slipperDrawPos, slipperFrom, player.slipper.position, alpha);
    }

    /** Draw exactly where things are (when the match isn't stepping: paused, or between stages). */
    public void snap() {
        from.set(player.position);
        drawPos.set(player.position);
        slipperFrom.set(player.slipper.position);
        slipperDrawPos.set(player.slipper.position);
    }

    /** {@code out} = alpha of the way from a to b, or b itself after a jump. Returns false when it jumped. */
    static boolean between(Vector2 out, Vector2 a, Vector2 b, float alpha) {
        if (a.dst2(b) > SNAP_DISTANCE * SNAP_DISTANCE) {
            out.set(b);
            return false;
        }
        out.set(a).lerp(b, alpha);
        return true;
    }

    /** Inside a Filled ShapeRenderer block with blending on, before the sprites. */
    public void drawShadow(ShapeRenderer shapes) {
        shapes.setColor(0f, 0f, 0f, SHADOW_ALPHA);
        shapes.ellipse(drawPos.x - SHADOW_W / 2f, drawPos.y + SHADOW_OFFSET_Y - SHADOW_H / 2f, SHADOW_W, SHADOW_H);
    }

    /** The placeholder slipper (an oval) when there is no slipper art. Inside a Filled ShapeRenderer block. */
    public void drawSlipperShape(ShapeRenderer shapes) {
        shapes.setColor(slipperColor);
        shapes.ellipse(slipperDrawPos.x - SLIPPER_RADIUS, slipperDrawPos.y - 4f, SLIPPER_RADIUS * 2f, 8f);
    }

    /** The player sprite, and the held slipper in their colour. Inside batch.begin()/end(). */
    public void draw(SpriteBatch batch) {
        PlayerAnimation body;
        if (player.hasCan && withCan != null) body = withCan;
        else if (player.hasSlipper && withSlipper != null) body = withSlipper;
        else if (walk != null) body = walk;
        else return;

        float previous = batch.getPackedColor();
        float x = drawPos.x - WIDTH / 2f, y = drawPos.y - HEIGHT / 2f;
        batch.setColor(tint);
        batch.draw(body.getCurrentFrame(), x, y, WIDTH, HEIGHT);
        if (player.hasSlipper && !player.hasCan && slipperOverlay != null) {
            batch.setColor(player.color());
            batch.draw(slipperOverlay.getCurrentFrame(), x, y, WIDTH, HEIGHT);
        }
        batch.setPackedColor(previous);
    }
}
