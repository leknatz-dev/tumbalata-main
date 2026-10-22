package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One player's state in a match: position, speed, slipper, can, slipping. No art: {@link PlayerView} draws it, so the
 * rules can run with no screen (a host or a test).
 */
public class Player {
    /** Colour per player number (P1 red, P2 blue, P3 green, P4 yellow), for screens before characters are picked. */
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
    /** The name shown everywhere for this player (name tags, HUD, signs). Defaults to "P1"..."P4". */
    public String name;

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

    // Slipped on trash: slides for slideTime, then is stunned for stunTime. No control the whole time.
    public static final float SLIDE_SECONDS = 0.45f;
    public static final float STUN_SECONDS = 1.0f;
    private static final float SLIDE_SPEED = 320f;
    private float slideTime = 0f;
    private float stunTime = 0f;
    private final Vector2 slideVelocity = new Vector2();
    public boolean facingRight = true;
    public void updateFacingFromAngle(float angle) {
    // Standard normalized angle check: if pointing left-ish, flip sprite left
    float normalizedAngle = (angle % 360 + 360) % 360;
    this.facingRight = !(normalizedAngle > 90 && normalizedAngle < 270);
}

    public Player(int id, int characterIndex, float x, float y, float speed, PlayerInput input,
                  float minX, float maxX, float minY, float maxY) {
        this.id = id;
        this.characterIndex = characterIndex;
        this.name = GameSettings.defaultName(id);
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
        this.speed = speed;
        this.input = input;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    // Keeps this player behind the court line (a Thrower who hasn't thrown yet). The line may lean, so the limit is
    // checked at the player's own height every frame. null = free.
    private CourtLine keepBehind;
    private float keepBehindMargin;

    public void keepBehind(CourtLine line, float margin) {
        keepBehind = line;
        keepBehindMargin = margin;
    }

    public boolean isKeptBehindLine() {
        return keepBehind != null;
    }

    private void clampBehindLine() {
        if (keepBehind != null) position.x = Math.min(position.x, keepBehind.xAt(position.y) - keepBehindMargin);
    }

    public void setXBounds(float minX, float maxX) {
        this.minX = minX;
        this.maxX = maxX;
    }

    /** The player's name for the HUD, name tags and signs. */
    public String label() {
        return name;
    }

    /**
     * This player's colour in a match: their character's colour, so the slipper, aim arrow, name tag and HUD match
     * the character on screen (each character can only be picked once, so colours never clash). Before characters are
     * picked (name entry, character select), screens use {@link #SLOT_COLORS} by player number instead.
     */
    public Color color() {
        return Characters.COLORS[characterIndex];
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
        clampBehindLine();
    }

    /** Slips: slides along (dirX, dirY) (normalized here), then is stunned. */
    public void slip(float dirX, float dirY) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len == 0f) {
            dirX = facingRight ? 1f : -1f;
            dirY = 0f;
            len = 1f;
        }
        slideVelocity.set(dirX / len * SLIDE_SPEED, dirY / len * SLIDE_SPEED);
        slideTime = SLIDE_SECONDS;
        stunTime = STUN_SECONDS;
        moving = false;
        velocity.set(0, 0);
    }

    /** Stunned on the spot (no slide), e.g. after stepping in dog poop. */
    public void stun(float seconds) {
        slideTime = 0f;
        stunTime = seconds;
        moving = false;
        velocity.set(0, 0);
    }

    /** Sliding or stunned: this player can't move, pick things up, throw or tag. */
    public boolean isStunned() {
        return slideTime > 0f || stunTime > 0f;
    }

    /** True while stunned after the slide (for the dizzy stars). */
    public boolean isDizzy() {
        return slideTime <= 0f && stunTime > 0f;
    }

    /** One frame of slipping: slides (slowing down, inside the bounds), then counts the stun down. */
    public void updateSlip(float delta) {
        if (slideTime > 0f) {
            float t = slideTime / SLIDE_SECONDS; // 1 -> 0, so the slide slows down
            position.add(slideVelocity.x * t * delta, slideVelocity.y * t * delta);
            position.x = Math.max(minX, Math.min(maxX, position.x));
            position.y = Math.max(minY, Math.min(maxY, position.y));
            clampBehindLine();
            slideTime -= delta;
        } else if (stunTime > 0f) {
            stunTime -= delta;
        }
        moving = false;
        velocity.set(0, 0);
    }

    public void clearSlip() {
        slideTime = 0f;
        stunTime = 0f;
    }

    /** Stands still this frame (no movement, idle animation). */
    public void stop() {
        moving = false;
        velocity.set(0, 0);
    }

    public boolean isMoving() {
        return moving;
    }

    // ------------------------------------------------------------------
    // Network copy (the host sends this state; a phone draws its copy)
    // ------------------------------------------------------------------

    void writeState(DataOutputStream out) throws IOException {
        out.writeFloat(position.x);
        out.writeFloat(position.y);
        out.writeFloat(velocity.x);
        out.writeFloat(velocity.y);
        out.writeFloat(speed);
        out.writeInt(score);
        out.writeBoolean(hasCan);
        out.writeBoolean(hasSlipper);
        out.writeBoolean(hasThrown);
        out.writeBoolean(moving);
        out.writeBoolean(facingRight);
        out.writeFloat(slideTime);
        out.writeFloat(stunTime);
        out.writeFloat(minX);
        out.writeFloat(maxX);
        out.writeBoolean(keepBehind != null);
        out.writeFloat(keepBehindMargin);
        out.writeFloat(slipper.position.x);
        out.writeFloat(slipper.position.y);
        out.writeFloat(slipper.velocity.x);
        out.writeFloat(slipper.velocity.y);
    }

    /** {@code court}: the line a Thrower who hasn't thrown is kept behind. */
    void readState(DataInputStream in, CourtLine court) throws IOException {
        position.set(in.readFloat(), in.readFloat());
        velocity.set(in.readFloat(), in.readFloat());
        speed = in.readFloat();
        score = in.readInt();
        hasCan = in.readBoolean();
        hasSlipper = in.readBoolean();
        hasThrown = in.readBoolean();
        moving = in.readBoolean();
        facingRight = in.readBoolean();
        slideTime = in.readFloat();
        stunTime = in.readFloat();
        minX = in.readFloat();
        maxX = in.readFloat();
        keepBehind = in.readBoolean() ? court : null;
        keepBehindMargin = in.readFloat();
        slipper.position.set(in.readFloat(), in.readFloat());
        slipper.velocity.set(in.readFloat(), in.readFloat());
    }
}
