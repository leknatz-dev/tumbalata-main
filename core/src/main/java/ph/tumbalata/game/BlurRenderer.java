package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * Draws the court into an off-screen buffer so it can be shown blurred (behind a pop-up sign). The blur is a cheap
 * two-pass Gaussian: horizontal into a second buffer, then vertical onto the screen.
 *
 * <p>Usage per frame: {@link #beginScene()} (returns false if unavailable: then just draw straight to the screen),
 * draw the court, {@link #endScene(float, float)}. If the shader fails to compile the scene is still shown, unblurred.
 */
public final class BlurRenderer {
    private static final String VERTEX =
        "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "  v_color = a_color;\n"
            + "  v_texCoords = a_texCoord0;\n"
            + "  gl_Position = u_projTrans * a_position;\n"
            + "}\n";

    // 9-tap Gaussian done with 5 linear-filtered samples
    private static final String FRAGMENT =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_dir;\n"
            + "void main() {\n"
            + "  vec4 sum = texture2D(u_texture, v_texCoords) * 0.2270270270;\n"
            + "  sum += texture2D(u_texture, v_texCoords + u_dir * 1.3846153846) * 0.3162162162;\n"
            + "  sum += texture2D(u_texture, v_texCoords - u_dir * 1.3846153846) * 0.3162162162;\n"
            + "  sum += texture2D(u_texture, v_texCoords + u_dir * 3.2307692308) * 0.0702702703;\n"
            + "  sum += texture2D(u_texture, v_texCoords - u_dir * 3.2307692308) * 0.0702702703;\n"
            + "  gl_FragColor = v_color * sum;\n"
            + "}\n";

    private final SpriteBatch batch = new SpriteBatch(4);
    private final Matrix4 screenProjection = new Matrix4();
    private final ShaderProgram shader;
    private FrameBuffer scene, half;
    private int width, height;

    public BlurRenderer() {
        ShaderProgram.pedantic = false;
        ShaderProgram program = new ShaderProgram(VERTEX, FRAGMENT);
        if (!program.isCompiled()) {
            Gdx.app.error("Blur", "Blur shader failed, signs will not blur the court:\n" + program.getLog());
            program.dispose();
            program = null;
        }
        shader = program;
        resize();
    }

    /** Recreates the buffers at the window's pixel size. Call on resize. */
    public void resize() {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        if (w == width && h == height && scene != null) return;
        disposeBuffers();
        width = w;
        height = h;
        if (w <= 0 || h <= 0) return; // minimized
        scene = newBuffer(w, h);
        half = newBuffer(w, h);
        screenProjection.setToOrtho2D(0, 0, w, h);
    }

    private static FrameBuffer newBuffer(int w, int h) {
        FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fb.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return fb;
    }

    /** Starts drawing the court into the buffer. False = no buffer (draw to the screen as usual). */
    public boolean beginScene() {
        if (scene == null) return false;
        scene.begin();
        return true;
    }

    /**
     * Finishes the court and shows it on the screen.
     * @param amount     0 = sharp, 1 = full blur
     * @param maxRadius  blur spread in pixels at amount 1
     */
    public void endScene(float amount, float maxRadius) {
        scene.end();
        Gdx.gl.glViewport(0, 0, width, height);
        batch.setProjectionMatrix(screenProjection);

        if (amount <= 0.01f || shader == null) {
            draw(scene.getColorBufferTexture(), null, 0f, 0f, 1f);
            return;
        }
        float spread = amount * maxRadius / 3.2f; // the outer samples sit at ~3.2 x spread
        float dim = 1f - 0.25f * amount;          // slightly darker too, so the sign pops

        half.begin();
        draw(scene.getColorBufferTexture(), shader, spread / width, 0f, 1f);
        half.end();
        Gdx.gl.glViewport(0, 0, width, height);
        draw(half.getColorBufferTexture(), shader, 0f, spread / height, dim);
    }

    private void draw(Texture texture, ShaderProgram program, float dirX, float dirY, float brightness) {
        batch.setShader(program);
        batch.begin();
        if (program != null) program.setUniformf("u_dir", dirX, dirY);
        batch.setColor(brightness, brightness, brightness, 1f);
        batch.disableBlending(); // the court is opaque; never let old pixels show through
        batch.draw(texture, 0, 0, width, height, 0, 0, texture.getWidth(), texture.getHeight(), false, true);
        batch.enableBlending();
        batch.end();
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void disposeBuffers() {
        if (scene != null) scene.dispose();
        if (half != null) half.dispose();
        scene = half = null;
    }

    public void dispose() {
        disposeBuffers();
        batch.dispose();
        if (shader != null) shader.dispose();
    }
}
