package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

public class Player {
    /** Name-tag colour per player slot (P1 red, P2 blue, P3 green, P4 yellow). */
    public static final Color[] SLOT_COLORS = {
        new Color(0.95f, 0.30f, 0.30f, 1f),
        new Color(0.35f, 0.55f, 1.00f, 1f),
        new Color(0.35f, 0.85f, 0.40f, 1f),
        new Color(1.00f, 0.85f, 0.25f, 1f)
    };

    /** Stable player number (0 = Player 1). Never changes when roles swap; use it for scores and the HUD. */
    public final int id;
    /** The character this player picked (index into {@link Characters}). */
    public final int characterIndex;
    public int score = 0;

    public Vector2 position;
    /** Position at the start of this frame, used to slide back out of walls. */
    public final Vector2 prevPosition = new Vector2();
    public Vector2 velocity;
    public float speed;
    public boolean hasCan = false;
    public boolean hasSlipper = false;

    /** This player's own slipper (every Thrower throws their own). */
    public Slipper slipper;
    /** True once this player has thrown their slipper this round. */
    public boolean hasThrown = false;

    /** This player's input (keyboard and/or controller, merged). It stays with the player when roles swap. */
    public final PlayerInput input;

    private float minX, maxX, minY, maxY;
    private boolean moving = false;

    public float width = 48f;
    public float height = 48f;

    // Multiplies the sprite's colors (white = unchanged). Used for the placeholder character colors.
    public Color tint = new Color(Color.WHITE);

    // Ground shadow under the feet (tweak these to match the sprite)
    public float shadowWidth = 28f;
    public float shadowHeight = 10f;
    public float shadowOffsetY = -19f;   // from the player's position to the CENTER of the shadow
    public float shadowAlpha = 0.30f;

    private PlayerAnimation walkAnim;
    private PlayerAnimation walkWithSlipperAnim;
    private PlayerAnimation walkWithCanAnim;
    public boolean facingRight = true;
    public void updateFacingFromAngle(float angle) {
    // Standard normalized angle check: if pointing left-ish, flip sprite left
    float normalizedAngle = (angle % 360 + 360) % 360;
    this.facingRight = !(normalizedAngle > 90 && normalizedAngle < 270);
}

    public Player(int id, int characterIndex, float x, float y, float speed, PlayerInput input,
                  float minX, float maxX, float minY, float maxY,
                  Texture normalSheet, Texture slipperSheet, Texture canSheet) {
        this.id = id;
        this.characterIndex = characterIndex;
        this.tint.set(Characters.TINTS[characterIndex]);
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
        this.speed = speed;
        this.input = input;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;

        // Sheets may be null (unit tests run without graphics); such a player has no sprite
        if (normalSheet != null) {
            this.walkAnim = new PlayerAnimation(normalSheet, 0.12f);
        }
        if (slipperSheet != null) {
            this.walkWithSlipperAnim = new PlayerAnimation(slipperSheet, 0.12f);
        }
        if (canSheet != null) {
            this.walkWithCanAnim = new PlayerAnimation(canSheet, 0.12f);
        }
    }

    public void setXBounds(float minX, float maxX) {
        this.minX = minX;
        this.maxX = maxX;
    }

    /** Short label for the HUD and name tags: "P1" ... "P4". */
    public String label() {
        return "P" + (id + 1);
    }

    public Color slotColor() {
        return SLOT_COLORS[id % SLOT_COLORS.length];
    }

    public void handleInput(float delta) {
        // moveX / moveY are already -1..1 and normalized for diagonals (see InputManager)
        moving = input.isMoving();
        velocity.set(0, 0);

        if (moving) {
            velocity.set(input.moveX * speed, input.moveY * speed);
            position.add(velocity.x * delta, velocity.y * delta);
        }

        position.x = Math.max(minX, Math.min(maxX, position.x));
        position.y = Math.max(minY, Math.min(maxY, position.y));
    }

    /** Stands still this frame (no movement, idle animation). */
    public void stop() {
        moving = false;
        velocity.set(0, 0);
    }

    public boolean isMoving() {
        return moving;
    }

    public void update(float delta) {
        if (hasCan && walkWithCanAnim != null) {
            walkWithCanAnim.update(delta, velocity);
        } else if (hasSlipper && walkWithSlipperAnim != null) {
            walkWithSlipperAnim.update(delta, velocity);
        } else if (walkAnim != null) {
            walkAnim.update(delta, velocity);
        }
    }

    public void render(SpriteBatch batch) {
        TextureRegion currentFrame;
        if (hasCan && walkWithCanAnim != null) {
            currentFrame = walkWithCanAnim.getCurrentFrame();
        } else if (hasSlipper && walkWithSlipperAnim != null) {
            currentFrame = walkWithSlipperAnim.getCurrentFrame();
        } else if (walkAnim != null) {
            currentFrame = walkAnim.getCurrentFrame();
        } else {
            return;
        }

        float previousColor = batch.getPackedColor();
        batch.setColor(tint);
        batch.draw(
            currentFrame, 
            position.x - width / 2f, 
            position.y - height / 2f, 
            width, 
            height
        );
        batch.setPackedColor(previousColor);
    }

    /** Call inside a ShapeRenderer Filled block (with GL blending on), BEFORE the sprites are drawn. */
    public void renderShadow(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(0f, 0f, 0f, shadowAlpha);
        shapeRenderer.ellipse(
            position.x - shadowWidth / 2f,
            position.y + shadowOffsetY - shadowHeight / 2f,
            shadowWidth,
            shadowHeight
        );
    }
}