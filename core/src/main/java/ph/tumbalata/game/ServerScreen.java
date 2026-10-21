package ph.tumbalata.game;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;

import java.io.IOException;
import java.util.List;

import ph.tumbalata.game.net.ClientSession;
import ph.tumbalata.game.net.HostSession;
import ph.tumbalata.game.net.LanDiscovery;
import ph.tumbalata.game.net.Protocol;

/**
 * The server screen, between the main menu and the waiting room: your name, HOST A GAME (opens a room other phones on
 * the same Wi-Fi can find), and the rooms found on this Wi-Fi to join. It keeps looking for rooms while it's open.
 *
 * <p>The name: on a phone, tapping it opens the phone's keyboard; on a computer, A (or a click) starts typing and
 * Enter saves it. It's remembered for next time.
 */
public class ServerScreen implements Screen {
    private static final float MENU_WIDTH = MenuViewport.WIDTH;
    private static final float BACKDROP_DIM = 0.55f;
    private static final int MAX_ROOMS_SHOWN = 4;

    // --- LAYOUT (700 x 500, y up) ---
    private static final float TITLE_Y = 470f;
    private static final float ROW_X = 150f, ROW_W = 400f;
    private static final float NAME_Y = 370f, NAME_H = 46f;
    private static final float HOST_Y = 340f, HOST_H = 52f; // HOST_Y: top of the button
    private static final float ROOMS_LABEL_Y = 262f;
    private static final float ROOMS_TOP = 236f, ROOM_H = 40f, ROOM_GAP = 6f;
    private static final float HINT_Y = 26f;

    private final TumbalataGame game;
    private LanDiscovery.Searcher searcher;
    private List<LanDiscovery.Room> rooms = java.util.Collections.emptyList();

    private OrthographicCamera camera;
    private MenuViewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private ControlHints hints;
    private MenuBackdrop backdrop;
    private final GlyphLayout layout = new GlyphLayout();

    private int selected = 1; // 0 = name, 1 = host, 2.. = rooms
    private String name;
    private boolean typing = false;
    private float time = 0f;
    private boolean leaving = false;
    private String message;
    private float messageTime;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;

    private final InputAdapter keyboard = new InputAdapter() {
        @Override
        public boolean keyTyped(char c) {
            if (!typing) return false;
            if (c == '\b') {
                if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
            } else if (c == '\r' || c == '\n') {
                finishTyping();
            } else if ((Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '.')
                && name.length() < GameSettings.MAX_NAME_LENGTH) {
                name += Character.toUpperCase(c);
            }
            return true;
        }
    };

    public ServerScreen(TumbalataGame game) {
        this(game, null);
    }

    /** {@code message}: shown for a moment (why we came back here, e.g. the host closed the room), or null. */
    public ServerScreen(TumbalataGame game, String message) {
        this.game = game;
        this.name = game.settings().playerName();
        if (message != null) say(message);
    }

    @Override
    public void show() {
        leaving = false;
        game.audio().playMusic(Audio.Track.MENU);
        camera = new OrthographicCamera();
        viewport = new MenuViewport(camera);
        viewport.apply();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = Fonts.create();
        hints = new ControlHints();
        backdrop = game.getBackdrop();
        searcher = new LanDiscovery.Searcher(Protocol.DISCOVERY_PORT);
        Gdx.input.setInputProcessor(keyboard);
    }

    /** The name to play under: the one set here, or "PLAYER". */
    private String playerName() {
        String n = name.trim();
        return n.isEmpty() ? "PLAYER" : n;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private int itemCount() {
        return 2 + Math.min(MAX_ROOMS_SHOWN, rooms.size());
    }

    private Rectangle itemBounds(int i) {
        if (i == 0) return new Rectangle(ROW_X, NAME_Y, ROW_W, NAME_H);
        if (i == 1) return new Rectangle(ROW_X, HOST_Y - HOST_H, ROW_W, HOST_H);
        return new Rectangle(ROW_X, ROOMS_TOP - ROOM_H - (i - 2) * (ROOM_H + ROOM_GAP), ROW_W, ROOM_H);
    }

    private void handleInput(float delta) {
        InputManager input = game.input();
        if (input.menu().fullscreen) game.toggleFullscreen();
        messageTime = Math.max(0f, messageTime - delta);
        rooms = searcher.rooms();
        if (selected >= itemCount()) selected = itemCount() - 1;
        if (leaving || input.isMenuLocked()) return;

        if (typing) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) finishTyping();
            return; // the keyboard is typing the name
        }

        PlayerInput in = input.player(0);
        if (in.bPressed || in.selectPressed || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaving = true;
            game.audio().play(Audio.Sfx.UI_BACK);
            game.changeScreen(new MainMenuScreen(game));
            return;
        }
        int before = selected;
        if (in.upPressed) selected = (selected + itemCount() - 1) % itemCount();
        if (in.downPressed) selected = (selected + 1) % itemCount();
        boolean activate = in.aPressed || in.startPressed;

        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        boolean mouseMoved = Gdx.input.getX() != lastMouseX || Gdx.input.getY() != lastMouseY;
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();
        for (int i = 0; i < itemCount(); i++) {
            if (!itemBounds(i).contains(mouse.x, mouse.y)) continue;
            if (mouseMoved) selected = i;
            if (Gdx.input.justTouched()) {
                selected = i;
                activate = true;
            }
        }
        if (selected != before) game.audio().play(Audio.Sfx.UI_MOVE);
        if (activate) activate(selected);
    }

    private void activate(int item) {
        if (item == 0) editName();
        else if (item == 1) host();
        else join(rooms.get(item - 2));
    }

    private void editName() {
        game.audio().play(Audio.Sfx.UI_CONFIRM);
        if (Gdx.app.getType() == Application.ApplicationType.Android || Gdx.app.getType() == Application.ApplicationType.iOS) {
            Gdx.input.getTextInput(new Input.TextInputListener() { // the phone's own keyboard
                @Override
                public void input(String text) {
                    Gdx.app.postRunnable(() -> saveName(text));
                }

                @Override
                public void canceled() {}
            }, "YOUR NAME", name, "NAME");
        } else {
            typing = true;
            game.input().setTextEntry(true); // the keys type instead of moving Player 1
        }
    }

    private void finishTyping() {
        typing = false;
        game.input().setTextEntry(false);
        saveName(name);
        game.audio().play(Audio.Sfx.UI_CONFIRM);
    }

    private void saveName(String text) {
        StringBuilder clean = new StringBuilder();
        for (char c : text.toUpperCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '.') clean.append(c);
        }
        name = clean.length() > GameSettings.MAX_NAME_LENGTH ? clean.substring(0, GameSettings.MAX_NAME_LENGTH) : clean.toString();
        game.settings().setPlayerName(name.trim());
    }

    private void host() {
        try {
            HostSession session = new HostSession(Protocol.TCP_PORT, Protocol.DISCOVERY_PORT, playerName()); // rooms go by the host's name
            game.network().startHosting(session);
        } catch (IOException e) {
            say("COULD NOT OPEN A ROOM. IS ANOTHER ONE OPEN HERE?");
            game.audio().play(Audio.Sfx.UI_DENY);
            return;
        }
        Lobby lobby = game.lobby();
        lobby.clear();
        lobby.join(0); // the host, on this device
        lobby.setName(0, playerName());
        leaving = true;
        game.audio().play(Audio.Sfx.UI_CONFIRM);
        game.changeScreen(new WaitingRoomScreen(game));
    }

    private void join(LanDiscovery.Room room) {
        if (room.isFull()) {
            say("THAT ROOM IS FULL");
            game.audio().play(Audio.Sfx.UI_DENY);
            return;
        }
        try {
            ClientSession session = ClientSession.connect(room.address, room.port, playerName(), 1500);
            game.network().joined(session);
        } catch (IOException e) {
            say("COULD NOT REACH THAT ROOM");
            game.audio().play(Audio.Sfx.UI_DENY);
            return;
        }
        game.lobby().clear(); // filled in by the host
        leaving = true;
        game.audio().play(Audio.Sfx.UI_CONFIRM);
        game.changeScreen(new WaitingRoomScreen(game));
    }

    private void say(String text) {
        message = text;
        messageTime = 2.5f;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    public void render(float delta) {
        time += delta;
        handleInput(delta);

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);
        if (backdrop != null) backdrop.renderDimmed(delta, shapes, viewport, BACKDROP_DIM);

        float pulse = (MathUtils.sin(time * 6f) + 1f) / 2f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (backdrop == null) {
            shapes.setColor(0.18f, 0.35f, 0.25f, 1f);
            shapes.rect(viewport.left(), viewport.bottom(), viewport.visibleWidth(), viewport.visibleHeight());
        }
        for (int i = 0; i < itemCount(); i++) {
            Rectangle r = itemBounds(i);
            if (i == selected) {
                shapes.setColor(1f, 0.85f, 0.2f, 0.75f + 0.25f * pulse);
                shapes.rect(r.x - 3f, r.y - 3f, r.width + 6f, r.height + 6f);
            }
            if (i == 1) shapes.setColor(0.2f, 0.65f, 0.3f, 0.95f);
            else shapes.setColor(0f, 0f, 0f, 0.7f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        float bob = MathUtils.sin(time * 2.5f) * 4f;
        text("PLAY", 0f, TITLE_Y + bob, 2.4f, Color.WHITE, Align.center, MENU_WIDTH);

        Rectangle nameRow = itemBounds(0);
        text("YOUR NAME", nameRow.x + 12f, nameRow.y + NAME_H / 2f + 8f, 1.1f, Color.LIGHT_GRAY, Align.left, 0f);
        boolean blink = ((int) (time * 2.5f)) % 2 == 0;
        String shownName = typing ? name + (blink ? "_" : " ") : (name.isEmpty() ? "TAP TO SET" : name);
        text(shownName, nameRow.x + ROW_W - 12f, nameRow.y + NAME_H / 2f + 10f, 1.5f,
            typing ? Color.GOLD : name.isEmpty() ? Color.GRAY : Color.WHITE, Align.right, 0f);

        Rectangle hostRow = itemBounds(1);
        text("HOST A GAME", hostRow.x, hostRow.y + HOST_H / 2f + 13f, 2f, Color.WHITE, Align.center, ROW_W);

        text("ROOMS ON THIS WI-FI", 0f, ROOMS_LABEL_Y, 1.1f, Color.LIGHT_GRAY, Align.center, MENU_WIDTH);
        if (rooms.isEmpty()) {
            String dots = "...".substring(0, 1 + (int) (time * 2f) % 3);
            text("LOOKING FOR ROOMS" + dots, 0f, ROOMS_TOP - 16f, 1.3f, new Color(1f, 1f, 1f, 0.6f), Align.center, MENU_WIDTH);
        }
        for (int i = 0; i < Math.min(MAX_ROOMS_SHOWN, rooms.size()); i++) {
            LanDiscovery.Room room = rooms.get(i);
            Rectangle r = itemBounds(i + 2);
            float mid = r.y + ROOM_H / 2f;
            text("HOST: " + room.name, r.x + 12f, mid + 10f, 1.4f, Color.WHITE, Align.left, 0f);
            text(room.players + "/" + room.roomSize + (room.isFull() ? "  FULL" : "  JOIN"), r.x + ROW_W - 12f, mid + 8f,
                1.1f, room.isFull() ? Color.SALMON : Color.GOLD, Align.right, 0f);
        }
        if (messageTime > 0f) text(message, 0f, HINT_Y + 36f, 1.1f, Color.SALMON, Align.center, MENU_WIDTH);
        batch.end();

        if (typing) {
            batch.begin();
            text("TYPE YOUR NAME, ENTER TO SAVE", 0f, HINT_Y + 6f, 1f, Color.LIGHT_GRAY, Align.center, MENU_WIDTH);
            batch.end();
        } else {
            hints.draw(batch, shapes, font, MENU_WIDTH / 2f, HINT_Y, 0.9f,
                ControlHints.Icon.DPAD_UP_DOWN, "CHOOSE", ControlHints.Icon.A, "OK", ControlHints.Icon.B, "BACK");
        }
    }

    /** Outlined text. {@code width} 0 = no box (left / right aligned at x). Inside batch.begin()/end(). */
    private void text(String s, float x, float y, float scale, Color color, int align, float width) {
        font.getData().setScale(scale);
        float drawX = x;
        if (width == 0f && align == Align.right) {
            layout.setText(font, s);
            drawX = x - layout.width;
        }
        Fonts.drawStroked(batch, font, s, drawX, y, width, width == 0f ? Align.left : align, color,
            new Color(0f, 0f, 0f, 0.85f * color.a), 1.5f);
        font.getData().setScale(1f);
    }

    // ------------------------------------------------------------------

    @Override
    public void resize(int width, int height) {
        if (viewport != null) viewport.update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        if (searcher != null) { searcher.stop(); searcher = null; }
        if (typing) game.input().setTextEntry(false);
        if (Gdx.input.getInputProcessor() == keyboard) Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        if (searcher != null) { searcher.stop(); searcher = null; }
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapes != null) { shapes.dispose(); shapes = null; }
        if (font != null) { font.dispose(); font = null; }
        if (hints != null) { hints.dispose(); hints = null; }
        backdrop = null; // shared, owned by TumbalataGame
    }
}
