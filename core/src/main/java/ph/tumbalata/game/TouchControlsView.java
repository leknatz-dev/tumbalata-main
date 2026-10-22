package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Draws the {@link TouchControls} over the whole screen (not just the court): a see-through joystick that follows the
 * thumb, and the yellow A and red B button art (assets/yellowbutton.png, redbutton.png; plain circles without them),
 * brighter and a little smaller while pressed. Call last in a frame; it leaves its own projection set.
 */
public final class TouchControlsView {
    private final Texture yellow = load("yellowbutton.png");
    private final Texture red = load("redbutton.png");
    private final OrthographicCamera camera = new OrthographicCamera();

    private static Texture load(String file) {
        if (!Gdx.files.internal(file).exists()) return null;
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    public void draw(TouchControls touch, SpriteBatch batch, ShapeRenderer shapes) {
        if (!touch.isEnabled()) return;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        HdpiUtils.glViewport(0, 0, (int) w, (int) h); // the whole screen, past the court's letterbox
        camera.setToOrtho(false, w, h);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // The joystick (touch y goes down, drawing y goes up)
        float held = touch.isStickHeld() ? 1f : 0.55f;
        shapes.setColor(0f, 0f, 0f, 0.25f * held);
        shapes.circle(touch.stickX(), h - touch.stickY(), touch.stickRadius() * 1.08f, 40);
        shapes.setColor(1f, 1f, 1f, 0.16f * held);
        shapes.circle(touch.stickX(), h - touch.stickY(), touch.stickRadius(), 40);
        shapes.setColor(1f, 1f, 1f, 0.55f * held);
        shapes.circle(touch.knobX(), h - touch.knobY(), touch.stickRadius() * 0.45f, 32);
        // Plain buttons when there is no art
        if (yellow == null) button(shapes, touch.aX(), h - touch.aY(), touch.aRadius(), touch.a, 1f, 0.85f, 0.2f);
        if (red == null) button(shapes, touch.bX(), h - touch.bY(), touch.bRadius(), touch.b, 0.9f, 0.25f, 0.2f);
        shapes.end();

        batch.begin();
        if (yellow != null) art(batch, yellow, touch.aX(), h - touch.aY(), touch.aRadius(), touch.a);
        if (red != null) art(batch, red, touch.bX(), h - touch.bY(), touch.bRadius(), touch.b);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private static void button(ShapeRenderer s, float x, float y, float r, boolean down, float red, float green, float blue) {
        s.setColor(0f, 0f, 0f, 0.4f);
        s.circle(x, y, r * 1.06f, 36);
        s.setColor(red, green, blue, down ? 0.95f : 0.6f);
        s.circle(x, y, down ? r * 0.92f : r, 36);
    }

    private static void art(SpriteBatch batch, Texture t, float x, float y, float r, boolean down) {
        float size = (down ? 0.92f : 1f) * r * 2f;
        batch.setColor(1f, 1f, 1f, down ? 1f : 0.7f);
        batch.draw(t, x - size / 2f, y - size / 2f, size, size);
    }

    public void dispose() {
        if (yellow != null) yellow.dispose();
        if (red != null) red.dispose();
    }
}
