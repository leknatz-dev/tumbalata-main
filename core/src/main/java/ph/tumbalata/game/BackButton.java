package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;

/**
 * The "< BACK" button in the top-left corner of a menu, for tapping (phones) or clicking. It sits in the corner of what
 * the screen shows ({@link MenuViewport}), so on a wide phone it is in the real corner, not over the menu. Each frame:
 * {@link #clicked} in the screen's input, {@link #draw} last (outside any batch / shape block).
 */
public final class BackButton {
    private static final float W = 104f, H = 36f, MARGIN = 12f;
    private static final float TEXT_SCALE = 1.25f;

    private final Rectangle bounds = new Rectangle();
    private final Vector2 touch = new Vector2();
    private boolean hovered;
    private float time;

    private void place(MenuViewport view) {
        float top = view.bottom() + view.visibleHeight();
        bounds.set(view.left() + MARGIN, top - MARGIN - H, W, H);
    }

    /** True on the frame the button is tapped or clicked. */
    public boolean clicked(MenuViewport view) {
        place(view);
        touch.set(Gdx.input.getX(), Gdx.input.getY());
        view.unproject(touch);
        boolean over = bounds.contains(touch.x, touch.y);
        // A mouse highlights it when over it; a finger only while pressing (a touch screen remembers the last touch)
        hovered = over && (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop || Gdx.input.isTouched());
        return over && Gdx.input.justTouched();
    }

    public void draw(MenuViewport view, SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, float delta) {
        place(view);
        time += delta;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(view.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (hovered) { // the gold frame, like the other menu buttons
            shapes.setColor(1f, 0.85f, 0.2f, 0.75f + 0.25f * (MathUtils.sin(time * 6f) + 1f) / 2f);
            shapes.rect(bounds.x - 3f, bounds.y - 3f, bounds.width + 6f, bounds.height + 6f);
        }
        shapes.setColor(0f, 0f, 0f, 0.7f);
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        // The arrow
        float ax = bounds.x + 16f, ay = bounds.y + bounds.height / 2f;
        shapes.setColor(Color.WHITE);
        shapes.triangle(ax - 7f, ay, ax + 3f, ay + 9f, ax + 3f, ay - 9f);
        shapes.rect(ax + 2f, ay - 3f, 9f, 6f);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(view.getCamera().combined);
        batch.begin();
        font.getData().setScale(TEXT_SCALE);
        Fonts.drawStroked(batch, font, "BACK", bounds.x + 30f, bounds.y + bounds.height / 2f + 9f, bounds.width - 36f,
            Align.center, Color.WHITE, new Color(0f, 0f, 0f, 0.85f), 1.5f);
        font.getData().setScale(1f);
        batch.end();
    }
}
