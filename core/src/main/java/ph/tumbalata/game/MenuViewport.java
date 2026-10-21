package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/**
 * The menus' view on any screen shape: the 700 x 500 layout always fits and stays in the middle, and a wider (phone)
 * or taller (tablet) screen shows more around it instead of black bars. Menu layouts keep using 0..700 x 0..500;
 * anything that should fill the whole screen (backgrounds, dimming) covers {@link #left()}, {@link #bottom()},
 * {@link #visibleWidth()} x {@link #visibleHeight()}.
 */
public final class MenuViewport extends ExtendViewport {
    public static final float WIDTH = 700f;
    public static final float HEIGHT = 500f;

    public MenuViewport(Camera camera) {
        super(WIDTH, HEIGHT, camera);
    }

    /** Fits the screen, keeping the 700 x 500 layout centred (whatever {@code centerCamera} says). */
    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        super.update(screenWidth, screenHeight, false);
        centreLayout();
    }

    @Override
    public void apply(boolean centerCamera) {
        super.apply(false);
        centreLayout();
    }

    private void centreLayout() {
        getCamera().position.set(WIDTH / 2f, HEIGHT / 2f, 0f);
        getCamera().update();
    }

    /** Left edge of what the screen shows (0 or less). */
    public float left() {
        return (WIDTH - getWorldWidth()) / 2f;
    }

    /** Bottom edge of what the screen shows (0 or less). */
    public float bottom() {
        return (HEIGHT - getWorldHeight()) / 2f;
    }

    public float visibleWidth() {
        return getWorldWidth();
    }

    public float visibleHeight() {
        return getWorldHeight();
    }
}
