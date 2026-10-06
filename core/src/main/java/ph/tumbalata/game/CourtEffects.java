package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * "Juice" for the match screen: hit-stop (a tiny freeze), screen shake and dust puffs. Visual only: the rules in
 * {@link Match} never read anything from here.
 *
 * <p>Dust uses a fixed pool, so nothing is allocated while playing. Art: {@code assets/effects/dust.png} (optional,
 * drawn tinted and scaled); until it exists each puff is a soft circle.
 */
public final class CourtEffects {
    private static final String DUST_FILE = "effects/dust.png";

    // --- DUST ---
    private static final int MAX_PUFFS = 256;
    private static final float RUN_DUST_STEP = 18f;     // px a player runs between puffs
    private static final float TRAIL_STEP = 9f;         // px a slipper / rolling can travels between puffs
    private static final float TRAIL_MIN_SPEED = 60f;   // slower than this leaves no trail
    private static final float FEET_OFFSET_Y = -19f;    // player position -> feet (matches the shadow)
    private static final Color DUST_COLOR = new Color(0.78f, 0.74f, 0.66f, 1f);
    private static final Color TRAIL_COLOR = new Color(0.88f, 0.86f, 0.80f, 1f);

    private final float[] px = new float[MAX_PUFFS], py = new float[MAX_PUFFS];
    private final float[] vx = new float[MAX_PUFFS], vy = new float[MAX_PUFFS];
    private final float[] age = new float[MAX_PUFFS], life = new float[MAX_PUFFS], size = new float[MAX_PUFFS];
    private final float[] alpha = new float[MAX_PUFFS];
    private final Color[] color = new Color[MAX_PUFFS];
    private int next = 0;

    // Distance travelled since the last puff, per player id (feet dust) and per slipper owner id (trail)
    private final float[] runDistance = new float[InputManager.MAX_PLAYERS];
    private final float[] lastX = new float[InputManager.MAX_PLAYERS], lastY = new float[InputManager.MAX_PLAYERS];
    private final boolean[] wasMoving = new boolean[InputManager.MAX_PLAYERS];
    private final float[] trailDistance = new float[InputManager.MAX_PLAYERS];
    private float canTrailDistance = 0f;

    // --- HIT-STOP AND SHAKE ---
    private float freezeLeft = 0f;
    private float shakeTime = 0f, shakeDuration = 0f, shakeStrength = 0f;
    private float shakeX = 0f, shakeY = 0f;

    private final Texture dustTexture;

    public CourtEffects() {
        for (int i = 0; i < MAX_PUFFS; i++) {
            color[i] = new Color(DUST_COLOR);
            age[i] = life[i] = 1f; // dead
        }
        FileHandle file = Gdx.files.internal(DUST_FILE);
        if (file.exists()) {
            dustTexture = new Texture(file);
            dustTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        } else {
            dustTexture = null;
        }
    }

    // ------------------------------------------------------------------
    // Hit-stop and shake
    // ------------------------------------------------------------------

    /** Freezes the action for {@code seconds} (keeps the longer freeze if one is already running). */
    public void hitStop(float seconds) {
        freezeLeft = Math.max(freezeLeft, seconds);
    }

    /** Shakes the camera by up to {@code strength} px, fading out over {@code seconds}. A weaker shake never cuts a
     * stronger one short. */
    public void shake(float strength, float seconds) {
        if (strength < currentShakeStrength()) return;
        shakeStrength = strength;
        shakeDuration = seconds;
        shakeTime = 0f;
    }

    private float currentShakeStrength() {
        if (shakeTime >= shakeDuration) return 0f;
        float t = 1f - shakeTime / shakeDuration;
        return shakeStrength * t * t;
    }

    /**
     * Counts down the hit-stop. Call first each frame.
     * @return true while frozen: skip the match update and {@link #update} this frame
     */
    public boolean tickFreeze(float delta) {
        if (freezeLeft <= 0f) return false;
        freezeLeft -= delta;
        return true;
    }

    public float shakeX() { return shakeX; }
    public float shakeY() { return shakeY; }

    // ------------------------------------------------------------------
    // Dust
    // ------------------------------------------------------------------

    /** One puff at (x, y), drifting with (dx, dy) px/s. */
    public void puff(float x, float y, float radius, float lifetime, float dx, float dy, Color tint, float startAlpha) {
        int i = next;
        next = (next + 1) % MAX_PUFFS; // the oldest puff is reused when the pool is full
        px[i] = x;
        py[i] = y;
        vx[i] = dx;
        vy[i] = dy;
        size[i] = radius;
        life[i] = lifetime;
        age[i] = 0f;
        alpha[i] = startAlpha;
        color[i].set(tint);
    }

    /** A ring of puffs, e.g. trash landing or a player slipping. */
    public void burst(float x, float y, int count, float spread) {
        for (int k = 0; k < count; k++) {
            float a = MathUtils.random(MathUtils.PI2);
            float speed = MathUtils.random(0.5f, 1f) * spread;
            puff(x, y, MathUtils.random(4f, 7f), MathUtils.random(0.35f, 0.55f),
                MathUtils.cos(a) * speed, MathUtils.sin(a) * speed * 0.5f, DUST_COLOR, 0.6f);
        }
    }

    /** Emits dust for whatever is moving in the match: running feet, flying slippers, the rolling can. */
    public void track(Match match, float delta) {
        Player taya = match.taya();
        for (int pi = 0; pi < match.players().size; pi++) {
            Player p = match.players().get(pi);
            int id = p.id;

            // Feet: a puff every RUN_DUST_STEP px, and a little kick-off burst when starting to run
            float moved = Math.abs(p.position.x - lastX[id]) + Math.abs(p.position.y - lastY[id]);
            lastX[id] = p.position.x;
            lastY[id] = p.position.y;
            boolean moving = p.isMoving() || p.isStunned();
            if (moving && moved < 40f) { // a bigger jump is a respawn, not running
                if (!wasMoving[id]) {
                    footPuff(p, 2);
                    runDistance[id] = 0f;
                }
                runDistance[id] += moved;
                if (runDistance[id] >= RUN_DUST_STEP) {
                    runDistance[id] = 0f;
                    footPuff(p, 1);
                }
            }
            wasMoving[id] = moving;

            // Slipper trail
            if (p != taya && !p.hasSlipper && p.slipper.velocity.len() > TRAIL_MIN_SPEED) {
                trailDistance[id] += p.slipper.velocity.len() * delta;
                if (trailDistance[id] >= TRAIL_STEP) {
                    trailDistance[id] = 0f;
                    puff(p.slipper.position.x + MathUtils.random(-2f, 2f), p.slipper.position.y + MathUtils.random(-2f, 2f),
                        MathUtils.random(3f, 4.5f), 0.4f, 0f, 6f, TRAIL_COLOR, 0.55f);
                }
            } else {
                trailDistance[id] = 0f;
            }
        }

        // The can leaves dust while it rolls on the ground (not while it is in the air)
        Can can = match.can();
        if (!taya.hasCan && can.zPosition < 4f && can.velocity.len() > TRAIL_MIN_SPEED) {
            canTrailDistance += can.velocity.len() * delta;
            if (canTrailDistance >= TRAIL_STEP * 1.5f) {
                canTrailDistance = 0f;
                puff(can.position.x + MathUtils.random(-3f, 3f), can.position.y - 6f, MathUtils.random(4f, 6f), 0.45f,
                    0f, 8f, DUST_COLOR, 0.55f);
            }
        } else {
            canTrailDistance = 0f;
        }
    }

    private void footPuff(Player p, int count) {
        for (int k = 0; k < count; k++) {
            // drifts backwards, away from where the player is heading
            float dx = -p.velocity.x * 0.15f + MathUtils.random(-10f, 10f);
            float dy = -p.velocity.y * 0.15f + MathUtils.random(-4f, 8f);
            puff(p.position.x + MathUtils.random(-5f, 5f), p.position.y + FEET_OFFSET_Y + MathUtils.random(-2f, 2f),
                MathUtils.random(3.5f, 5.5f), MathUtils.random(0.3f, 0.45f), dx, dy, DUST_COLOR, 0.5f);
        }
    }

    /** Moves and fades the puffs and the shake. Not called during hit-stop, so everything freezes together. */
    public void update(float delta) {
        for (int i = 0; i < MAX_PUFFS; i++) {
            if (age[i] >= life[i]) continue;
            age[i] += delta;
            px[i] += vx[i] * delta;
            py[i] += vy[i] * delta;
            float drag = 1f - Math.min(1f, 3f * delta);
            vx[i] *= drag;
            vy[i] *= drag;
        }

        shakeTime += delta;
        float s = currentShakeStrength();
        shakeX = s > 0f ? MathUtils.random(-s, s) : 0f;
        shakeY = s > 0f ? MathUtils.random(-s, s) : 0f;
    }

    /** True when there is a dust sprite (then use {@link #drawDust(Batch)}, else {@link #drawDust(ShapeRenderer)}). */
    public boolean hasDustSprite() {
        return dustTexture != null;
    }

    /** Placeholder dust: soft circles. Call inside a Filled ShapeRenderer block with blending on. */
    public void drawDust(ShapeRenderer shapes) {
        for (int i = 0; i < MAX_PUFFS; i++) {
            if (age[i] >= life[i]) continue;
            float t = age[i] / life[i];
            float r = size[i] * (0.7f + 0.8f * t); // grows as it fades
            Color c = color[i];
            shapes.setColor(c.r, c.g, c.b, alpha[i] * (1f - t));
            shapes.circle(px[i], py[i], r, 10);
        }
    }

    /** Dust sprite version. Call inside batch.begin()/end(). */
    public void drawDust(Batch batch) {
        float previous = batch.getPackedColor();
        float tw = dustTexture.getWidth(), th = dustTexture.getHeight();
        for (int i = 0; i < MAX_PUFFS; i++) {
            if (age[i] >= life[i]) continue;
            float t = age[i] / life[i];
            float scale = (size[i] / 5f) * (0.7f + 0.8f * t);
            Color c = color[i];
            batch.setColor(c.r, c.g, c.b, alpha[i] * (1f - t));
            batch.draw(dustTexture, px[i] - tw * scale / 2f, py[i] - th * scale / 2f, tw * scale, th * scale);
        }
        batch.setPackedColor(previous);
    }

    public void dispose() {
        if (dustTexture != null) dustTexture.dispose();
    }
}
