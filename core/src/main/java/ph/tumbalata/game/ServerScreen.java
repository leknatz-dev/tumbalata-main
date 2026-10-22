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
import java.net.InetAddress;
import java.util.List;

import ph.tumbalata.game.net.ClientSession;
import ph.tumbalata.game.net.HostSession;
import ph.tumbalata.game.net.LanDiscovery;
import ph.tumbalata.game.net.Protocol;

/**
 * The server screen, between the main menu and the waiting room: your name, HOST A GAME (opens a room other phones on
 * the same Wi-Fi can find), JOIN BY ADDRESS (typing the address the host's waiting room shows, for networks where the
 * room search doesn't get through), and the rooms found on this Wi-Fi to join. It keeps looking for rooms while it's open.
 *
 * <p>The name and the address: on a phone, tapping them opens the phone's keyboard; on a computer, A (or a click)
 * starts typing and Enter saves the name or joins the address. Both are remembered for next time.
 */
public class ServerScreen implements Screen {
    private static final float MENU_WIDTH = MenuViewport.WIDTH;
    private static final float BACKDROP_DIM = 0.55f;
    private static final int MAX_ROOMS_SHOWN = 3;
    private static final int MAX_ADDRESS_LENGTH = 40;

    // --- LAYOUT (700 x 500, y up) ---
    private static final float TITLE_Y = 470f;
    private static final float ROW_X = 150f, ROW_W = 400f;
    private static final float NAME_Y = 380f, NAME_H = 42f;
    private static final float HOST_Y = 368f, HOST_H = 52f;       // HOST_Y: top of the button
    private static final float ADDRESS_Y = 308f, ADDRESS_H = 40f; // ADDRESS_Y: top of the row
    private static final float ROOMS_LABEL_Y = 250f;
    private static final float ROOMS_TOP = 226f, ROOM_H = 40f, ROOM_GAP = 6f;

    // The items, top to bottom; the rooms found come after these
    private static final int NAME = 0, HOST = 1, ADDRESS = 2, FIRST_ROOM = 3;
    private static final float HINT_Y = 26f;

    private final TumbalataGame game;
    private LanDiscovery.Searcher searcher;
    private List<LanDiscovery.Room> rooms = java.util.Collections.emptyList();

    private OrthographicCamera camera;
    private MenuViewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private final BackButton backButton = new BackButton(); // top left: back to the main menu
    private MenuBackdrop backdrop;
    private final GlyphLayout layout = new GlyphLayout();

    private int selected = HOST;
    private String name;
    private String address;     // the host address to join by
    private int typing = -1;    // the item the keyboard types into (NAME or ADDRESS), or -1
    private float time = 0f;
    private boolean leaving = false;
    private String message;
    private float messageTime;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;

    private final InputAdapter keyboard = new InputAdapter() {
        @Override
        public boolean keyTyped(char c) {
            if (typing < 0) return false;
            if (c == '\r' || c == '\n') {
                finishTyping();
            } else if (typing == NAME) {
                if (c == '\b') {
                    if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
                } else if ((Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '.')
                    && name.length() < GameSettings.MAX_NAME_LENGTH) {
                    name += Character.toUpperCase(c);
                }
            } else if (c == '\b') {
                if (!address.isEmpty()) address = address.substring(0, address.length() - 1);
            } else if ((Character.isLetterOrDigit(c) || c == '.' || c == ':' || c == '-')
                && address.length() < MAX_ADDRESS_LENGTH) {
                address += Character.toUpperCase(c);
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
        this.address = game.settings().lastAddress();
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
        return FIRST_ROOM + Math.min(MAX_ROOMS_SHOWN, rooms.size());
    }

    private Rectangle itemBounds(int i) {
        if (i == NAME) return new Rectangle(ROW_X, NAME_Y, ROW_W, NAME_H);
        if (i == HOST) return new Rectangle(ROW_X, HOST_Y - HOST_H, ROW_W, HOST_H);
        if (i == ADDRESS) return new Rectangle(ROW_X, ADDRESS_Y - ADDRESS_H, ROW_W, ADDRESS_H);
        return new Rectangle(ROW_X, ROOMS_TOP - ROOM_H - (i - FIRST_ROOM) * (ROOM_H + ROOM_GAP), ROW_W, ROOM_H);
    }

    private void handleInput(float delta) {
        InputManager input = game.input();
        if (input.menu().fullscreen) game.toggleFullscreen();
        messageTime = Math.max(0f, messageTime - delta);
        rooms = searcher.rooms();
        if (selected >= itemCount()) selected = itemCount() - 1;
        if (leaving || input.isMenuLocked()) return;

        if (typing >= 0) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) stopTyping(); // Esc: never mind
            return; // the keyboard is typing
        }

        PlayerInput in = input.player(0);
        if (backButton.clicked(viewport) || in.bPressed || in.selectPressed || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
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
        if (item == NAME || item == ADDRESS) type(item);
        else if (item == HOST) host();
        else join(rooms.get(item - FIRST_ROOM));
    }

    /** Starts typing the name or the address: the phone's own keyboard, or this computer's. */
    private void type(int item) {
        game.audio().play(Audio.Sfx.UI_CONFIRM);
        if (Gdx.app.getType() == Application.ApplicationType.Android || Gdx.app.getType() == Application.ApplicationType.iOS) {
            boolean isName = item == NAME;
            Gdx.input.getTextInput(new Input.TextInputListener() {
                @Override
                public void input(String text) {
                    Gdx.app.postRunnable(() -> {
                        if (isName) saveName(text);
                        else joinAddress(text);
                    });
                }

                @Override
                public void canceled() {}
            }, isName ? "YOUR NAME" : "HOST ADDRESS", isName ? name : address, isName ? "NAME" : "192.168.1.17");
        } else {
            typing = item;
            game.input().setTextEntry(true); // the keys type instead of moving Player 1
        }
    }

    /** Enter: saves the name, or joins the address. */
    private void finishTyping() {
        int was = typing;
        stopTyping();
        if (was == NAME) {
            saveName(name);
            game.audio().play(Audio.Sfx.UI_CONFIRM);
        } else {
            joinAddress(address);
        }
    }

    private void stopTyping() {
        typing = -1;
        game.input().setTextEntry(false);
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

    /**
     * Joins the room at this address: the host's Wi-Fi address as its waiting room shows it (192.168.1.17), with
     * {@code :port} if the room isn't on the usual one.
     */
    private void joinAddress(String text) {
        String typed = text == null ? "" : text.trim();
        if (typed.isEmpty()) return;
        address = typed.toUpperCase();
        game.settings().setLastAddress(address);
        String host = typed;
        int port = Protocol.TCP_PORT;
        int colon = typed.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(typed.substring(colon + 1));
                host = typed.substring(0, colon);
            } catch (NumberFormatException e) {
                say("THAT ADDRESS LOOKS WRONG");
                game.audio().play(Audio.Sfx.UI_DENY);
                return;
            }
        }
        try {
            ClientSession session = ClientSession.connect(InetAddress.getByName(host), port, playerName(), 2000);
            game.network().joined(session);
        } catch (IOException e) {
            say("NO ROOM AT " + address + ". SAME WI-FI?");
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
            if (i == HOST) shapes.setColor(0.2f, 0.65f, 0.3f, 0.95f);
            else shapes.setColor(0f, 0f, 0f, 0.7f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        float bob = MathUtils.sin(time * 2.5f) * 4f;
        text("PLAY", 0f, TITLE_Y + bob, 2.4f, Color.WHITE, Align.center, MENU_WIDTH);

        Rectangle nameRow = itemBounds(NAME);
        text("YOUR NAME", nameRow.x + 12f, nameRow.y + NAME_H / 2f + 8f, 1.1f, Color.LIGHT_GRAY, Align.left, 0f);
        boolean blink = ((int) (time * 2.5f)) % 2 == 0;
        String shownName = typing == NAME ? name + (blink ? "_" : " ") : (name.isEmpty() ? "TAP TO SET" : name);
        text(shownName, nameRow.x + ROW_W - 12f, nameRow.y + NAME_H / 2f + 10f, 1.5f,
            typing == NAME ? Color.GOLD : name.isEmpty() ? Color.GRAY : Color.WHITE, Align.right, 0f);

        Rectangle hostRow = itemBounds(HOST);
        text("HOST A GAME", hostRow.x, hostRow.y + HOST_H / 2f + 13f, 2f, Color.WHITE, Align.center, ROW_W);

        Rectangle addressRow = itemBounds(ADDRESS);
        text("JOIN BY ADDRESS", addressRow.x + 12f, addressRow.y + ADDRESS_H / 2f + 8f, 1.1f, Color.LIGHT_GRAY, Align.left, 0f);
        String shownAddress = typing == ADDRESS ? address + (blink ? "_" : " ") : (address.isEmpty() ? "TAP TO TYPE" : address);
        text(shownAddress, addressRow.x + ROW_W - 12f, addressRow.y + ADDRESS_H / 2f + 9f, 1.3f,
            typing == ADDRESS ? Color.GOLD : address.isEmpty() ? Color.GRAY : Color.WHITE, Align.right, 0f);

        text("ROOMS ON THIS WI-FI", 0f, ROOMS_LABEL_Y, 1.1f, Color.LIGHT_GRAY, Align.center, MENU_WIDTH);
        if (rooms.isEmpty()) {
            String dots = "...".substring(0, 1 + (int) (time * 2f) % 3);
            text("LOOKING FOR ROOMS" + dots, 0f, ROOMS_TOP - 16f, 1.3f, new Color(1f, 1f, 1f, 0.6f), Align.center, MENU_WIDTH);
        }
        for (int i = 0; i < Math.min(MAX_ROOMS_SHOWN, rooms.size()); i++) {
            LanDiscovery.Room room = rooms.get(i);
            Rectangle r = itemBounds(i + FIRST_ROOM);
            float mid = r.y + ROOM_H / 2f;
            text("HOST: " + room.name, r.x + 12f, mid + 10f, 1.4f, Color.WHITE, Align.left, 0f);
            text(room.players + "/" + room.roomSize + (room.isFull() ? "  FULL" : "  JOIN"), r.x + ROW_W - 12f, mid + 8f,
                1.1f, room.isFull() ? Color.SALMON : Color.GOLD, Align.right, 0f);
        }
        if (messageTime > 0f) text(message, 0f, HINT_Y + 36f, 1.1f, Color.SALMON, Align.center, MENU_WIDTH);
        batch.end();

        if (typing >= 0) {
            batch.begin();
            String help = typing == NAME ? "TYPE YOUR NAME, ENTER TO SAVE" : "TYPE THE HOST ADDRESS, ENTER TO JOIN";
            text(help, 0f, HINT_Y + 6f, 1f, Color.LIGHT_GRAY, Align.center, MENU_WIDTH);
            batch.end();
        }
        backButton.draw(viewport, batch, shapes, font, delta);
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
        if (typing >= 0) game.input().setTextEntry(false);
        if (Gdx.input.getInputProcessor() == keyboard) Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        if (searcher != null) { searcher.stop(); searcher = null; }
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapes != null) { shapes.dispose(); shapes = null; }
        if (font != null) { font.dispose(); font = null; }
        backdrop = null; // shared, owned by TumbalataGame
    }
}
