package ph.tumbalata.game;

import com.badlogic.gdx.math.Vector2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The can's physics: position, height in the air, rolling and bouncing. No art: {@link CanView} draws it. */
public class Can {
    public Vector2 position;
    public Vector2 velocity;
    public float zPosition = 0f;
    public float zVelocity = 0f;
    public boolean isHit = false;

    public float width = 24f;
    public float height = 36f;

    // --- TWEAKABLE FLING & PHYSICS PARAMETERS ---
    private float gravity = -850f;         // Tweak gravity (more negative = falls faster)
    private float bounceFactor = 0.2f;     // Ground bounce elasticity (0 = no bounce, 1 = max bounce); kept low so the can barely hops
    private float friction = 0.95f;        // Ground slide friction
    private float wallBounceDamping = 0.75f; // Wall rebound speed retention (0.75 = retains 75% speed)
    private float tossPowerMultiplier = 3.5f; // Vertical jump scaling (lower = less air launch height)

    public Can(float x, float y) {
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
    }

    /** True while the can flies or rolls after being hit or tossed (it spins). */
    public boolean isSpinning() {
        return isHit && (velocity.len() > 0 || zPosition > 0);
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void toss(float vx, float vy, float power) {
        this.velocity.set(vx, vy);
        // Tweak vertical popup height here
        this.zVelocity = 180f + (power * tossPowerMultiplier);
        this.isHit = true;
    }

    /**
     * Tosses the can so that its FIRST landing is exactly at (targetX, targetY).
     * Uses the current zPosition as release height, the toss power for air time, and compensates for friction.
     */
    public void tossTo(float targetX, float targetY, float power) {
        float zv0 = 180f + power * tossPowerMultiplier;
        float g = -gravity;
        // Time until the can returns to the ground: z0 + zv0*t - g/2*t^2 = 0
        float airTime = (zv0 + (float) Math.sqrt(zv0 * zv0 + 2f * g * Math.max(0f, zPosition))) / g;

        float decay = -60f * (float) Math.log(friction);
        float travelFactor = (decay > 0f) ? (1f - (float) Math.exp(-decay * airTime)) / decay : airTime;

        float dx = targetX - position.x;
        float dy = targetY - position.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            toss(0f, 0f, power);
            return;
        }
        float speed = len / travelFactor;
        toss(dx / len * speed, dy / len * speed, power);
    }

    /** Backwards-compatible: built-in outer walls from (0,0) to (screenWidth, screenHeight). */
    public void update(float delta, float screenWidth, float screenHeight) {
        update(delta, 0f, 0f, screenWidth, screenHeight);
    }

    /** Updates physics; the built-in outer walls are the rectangle (minX,minY)-(maxX,maxY). */
    public void update(float delta, float minX, float minY, float maxX, float maxY) {
        if (!isHit && velocity.len() == 0 && zPosition == 0) {
            return;
        }

        // 1. Horizontal movement. Ground friction is time-based (same feel as "friction per 60 fps frame"),
        //    so the can travels the same distance at any frame rate and tossTo() can predict it exactly.
        float decay = -60f * (float) Math.log(friction);
        float decayStep = (float) Math.exp(-decay * delta);
        float moveFactor = (decay > 0f) ? (1f - decayStep) / decay : delta;
        position.add(velocity.x * moveFactor, velocity.y * moveFactor);

        // 2. Strict Screen Wall Bounce Logic (4 Boundaries)
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        // Left wall
        if (position.x <= minX + halfWidth) {
            position.x = minX + halfWidth;
            velocity.x = -velocity.x * wallBounceDamping;
        } 
        // Right wall
        else if (position.x >= maxX - halfWidth) {
            position.x = maxX - halfWidth;
            velocity.x = -velocity.x * wallBounceDamping;
        }

        // Bottom wall
        if (position.y <= minY + halfHeight) {
            position.y = minY + halfHeight;
            velocity.y = -velocity.y * wallBounceDamping;
        } 
        // Top wall
        else if (position.y >= maxY - halfHeight) {
            position.y = maxY - halfHeight;
            velocity.y = -velocity.y * wallBounceDamping;
        }

        // 3. Ground Friction
        velocity.scl(decayStep);
        if (velocity.len() < 5f) {
            velocity.set(0, 0);
        }

        // 4. Vertical Arc Physics (Height & Ground Bounce)
        if (zPosition > 0 || zVelocity > 0) {
            zVelocity += gravity * delta;
            zPosition += zVelocity * delta;

            if (zPosition <= 0) {
                zPosition = 0;
                if (Math.abs(zVelocity) > 40f) {
                    zVelocity = -zVelocity * bounceFactor;
                } else {
                    zVelocity = 0;
                }
            }
        }
    }

    /** At this speed (px/s) the spin animation plays at its normal frame rate. */
    static final float SPIN_REFERENCE_SPEED = 300f;

    /** Animation speed multiplier: 1 at {@link #SPIN_REFERENCE_SPEED}, 0 when the can is still. */
    public float spinRate() {
        float speed = (float) Math.sqrt(velocity.len2() + zVelocity * zVelocity);
        return speed / SPIN_REFERENCE_SPEED;
    }

    public void reset(float x, float y) {
        position.set(x, y);
        velocity.set(0, 0);
        zPosition = 0f;
        zVelocity = 0f;
        isHit = false;
    }

    // Network copy (the host sends this state; a phone draws its copy)
    void writeState(DataOutputStream out) throws IOException {
        out.writeFloat(position.x);
        out.writeFloat(position.y);
        out.writeFloat(velocity.x);
        out.writeFloat(velocity.y);
        out.writeFloat(zPosition);
        out.writeFloat(zVelocity);
        out.writeBoolean(isHit);
    }

    void readState(DataInputStream in) throws IOException {
        position.set(in.readFloat(), in.readFloat());
        velocity.set(in.readFloat(), in.readFloat());
        zPosition = in.readFloat();
        zVelocity = in.readFloat();
        isHit = in.readBoolean();
    }
}
