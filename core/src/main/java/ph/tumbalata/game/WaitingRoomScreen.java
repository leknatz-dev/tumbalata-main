package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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

/**
 * The waiting room (the game's {@link Lobby}): the players in the room on the left, the host's match settings on the
 * right. The host (Player 1) sets the room size, match time, street events and character traits, can open NAMES, and
 * presses START once the room is full; then everyone picks a character. After a match everyone comes back here.
 *
 * <p>The host's device keeps the real room; phones that join over the Wi-Fi (from the {@link ServerScreen}) appear in
 * it and get a copy, sent again whenever it changes ({@link Network} does that, whatever screen is up). On a phone in someone else's room the settings only show, and B
 * leaves. On the host's device other players can also join by pressing A on their own keys or pad (B to leave), for
 * testing on one computer. The host uses Player 1's keys / pad or the mouse; B or Esc closes the room.
 */
public class WaitingRoomScreen implements Screen {
    private static final float MENU_WIDTH = MenuViewport.WIDTH;
    private static final float BACKDROP_DIM = 0.55f;

    // --- LAYOUT (700 x 500, y up) ---
    private static final float TITLE_Y = 470f;
    private static final float COUNT_Y = 428f;
    private static final float ROWS_TOP = 400f;   // top of the first player row and the first setting
    private static final float PLAYER_X = 36f, PLAYER_W = 300f, PLAYER_H = 56f, PLAYER_GAP = 10f;
    private static final float SETTING_X = 364f, SETTING_W = 300f, SETTING_H = 42f, SETTING_GAP = 8f;
    private static final float START_H = 54f;
    private static final float HINT_Y = 26f;

    private enum Item { ROOM_SIZE, TIME, STREET_EVENTS, TRAITS, NAMES, START }
    private static final Item[] ITEMS = Item.values();

    private final TumbalataGame game;
    private final Lobby lobby;
    private final Network network;
    private final String address; // host: this device's Wi-Fi address, for phones that join by typing it (or null)

    private OrthographicCamera camera;
    private MenuViewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private ControlHints hints;
    private MenuBackdrop backdrop;
    private final GlyphLayout layout = new GlyphLayout();

    private final Rectangle[] itemBounds = new Rectangle[ITEMS.length];
    private int selected = Item.START.ordinal();
    private float time = 0f;
    private boolean leaving = false;
    private String message = "";
    private float messageTime = 0f;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;

    public WaitingRoomScreen(TumbalataGame game) {
        this.game = game;
        this.lobby = game.lobby();
        this.network = game.network();
        this.address = network.isHosting() ? ph.tumbalata.game.net.LanDiscovery.localAddress() : null;
        if (!network.isClient()) {
            if (lobby.size() == 0) lobby.join(0); // a new room: the host is in it
            lobby.unlockAll();
        } else {
            selected = -1; // a phone in someone else's room: the settings only show
        }
        float y = ROWS_TOP;
        for (int i = 0; i < ITEMS.length; i++) {
            float h = ITEMS[i] == Item.START ? START_H : SETTING_H;
            if (ITEMS[i] == Item.START) y -= 12f; // a little gap before START
            itemBounds[i] = new Rectangle(SETTING_X, y - h, SETTING_W, h);
            y -= h + SETTING_GAP;
        }
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
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void handleInput(float delta) {
        InputManager input = game.input();
        if (input.menu().fullscreen) game.toggleFullscreen();
        messageTime = Math.max(0f, messageTime - delta);
        if (network.isClient()) {
            if (!leaving && !input.isMenuLocked()) clientInput(input);
            return;
        }
        if (leaving || input.isMenuLocked()) return;

        // Players on this device join with A and leave with B. A key Player 1 shares (Space, E) doesn't count, so
        // the host pressing a button never adds or removes someone.
        PlayerInput host = input.player(0);
        for (int s = 1; s < InputManager.MAX_PLAYERS; s++) {
            PlayerInput in = input.player(s);
            Lobby.Slot slot = lobby.slotFor(s);
            if (in.aPressed && !host.aPressed && slot == null) {
                if (lobby.join(s) != null) game.audio().play(Audio.Sfx.UI_CONFIRM);
                else say("THE ROOM IS FULL", Audio.Sfx.UI_DENY);
            } else if (in.bPressed && !host.bPressed && slot != null && !slot.isHost()) {
                lobby.leave(slot.id());
                game.audio().play(Audio.Sfx.UI_BACK);
            }
        }

        // The host: Player 1's keys / pad, or the mouse
        if (host.bPressed || host.selectPressed || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            closeRoom();
            return;
        }
        int before = selected;
        if (host.upPressed) selected = (selected + ITEMS.length - 1) % ITEMS.length;
        if (host.downPressed) selected = (selected + 1) % ITEMS.length;
        if (host.leftPressed) change(ITEMS[selected], -1);
        if (host.rightPressed) change(ITEMS[selected], 1);
        if (host.aPressed || host.startPressed) activate(ITEMS[selected]);

        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        boolean mouseMoved = Gdx.input.getX() != lastMouseX || Gdx.input.getY() != lastMouseY;
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();
        int hovered = -1;
        for (int i = 0; i < itemBounds.length; i++) if (itemBounds[i].contains(mouse.x, mouse.y)) hovered = i;
        if (hovered >= 0 && mouseMoved) selected = hovered;
        if (hovered >= 0 && Gdx.input.justTouched()) {
            selected = hovered;
            activate(ITEMS[hovered]);
        }
        if (selected != before) game.audio().play(Audio.Sfx.UI_MOVE);
    }

    // ------------------------------------------------------------------
    // The network
    // ------------------------------------------------------------------

    /** A phone in someone else's room: B or Esc leaves. */
    private void clientInput(InputManager input) {
        PlayerInput in = input.player(0);
        if (in.bPressed || in.selectPressed || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.audio().play(Audio.Sfx.UI_BACK);
            backToServerScreen(null);
        }
    }

    private void backToServerScreen(String why) {
        if (leaving) return;
        leaving = true;
        network.close();
        lobby.clear();
        game.changeScreen(new ServerScreen(game, why));
    }

    /** Left / right on a setting. */
    private void change(Item item, int step) {
        Lobby.Settings s = lobby.settings;
        switch (item) {
            case ROOM_SIZE: {
                int was = s.roomSize;
                if (lobby.setRoomSize(s.roomSize + step) == was) {
                    if (step < 0 && was > Lobby.MIN_PLAYERS) say("EVERYONE HERE STAYS: " + lobby.size() + " PLAYERS", Audio.Sfx.UI_DENY);
                    return;
                }
                break;
            }
            case TIME:
                float was = s.matchSeconds;
                s.stepMatchTime(step);
                if (s.matchSeconds == was) return;
                break;
            case STREET_EVENTS:
                s.streetEvents = !s.streetEvents;
                game.settings().setStreetEvents(s.streetEvents); // remembered for next time
                break;
            case TRAITS:
                s.characterTraits = !s.characterTraits;
                break;
            default:
                return;
        }
        game.audio().play(Audio.Sfx.UI_MOVE);
    }

    /** A (or a click) on an item. */
    private void activate(Item item) {
        switch (item) {
            case ROOM_SIZE: { // A goes round 2 -> 3 -> 4 -> 2 (never below the players in the room)
                int was = lobby.settings.roomSize;
                if (lobby.setRoomSize(was + 1) == was) lobby.setRoomSize(Lobby.MIN_PLAYERS);
                game.audio().play(Audio.Sfx.UI_MOVE);
                break;
            }
            case TIME: {
                float was = lobby.settings.matchSeconds;
                lobby.settings.stepMatchTime(1);
                if (lobby.settings.matchSeconds == was) lobby.settings.matchSeconds = Lobby.Settings.MATCH_TIMES[0];
                game.audio().play(Audio.Sfx.UI_MOVE);
                break;
            }
            case STREET_EVENTS:
            case TRAITS:
                change(item, 1);
                break;
            case NAMES:
                if (lobby.hasRemotePlayers()) {
                    say("EACH PHONE SETS ITS OWN NAME", Audio.Sfx.UI_DENY);
                    return;
                }
                leaving = true;
                game.audio().play(Audio.Sfx.UI_CONFIRM);
                game.changeScreen(new NameEntryScreen(game));
                break;
            case START:
                if (!lobby.isFull()) {
                    say("WAITING FOR " + (lobby.settings.roomSize - lobby.size()) + " MORE", Audio.Sfx.UI_DENY);
                    return;
                }
                leaving = true;
                game.audio().play(Audio.Sfx.UI_CONFIRM);
                network.hostGo(Network.Go.CHARACTER_SELECT, new byte[0]); // the phones come along
                game.changeScreen(new CharacterSelectScreen(game));
                break;
        }
    }

    /** Host: closes the room (the phones in it are told) and goes back to the server screen. */
    private void closeRoom() {
        game.audio().play(Audio.Sfx.UI_BACK);
        backToServerScreen(null);
    }

    private void say(String text, Audio.Sfx sound) {
        message = text;
        messageTime = 1.6f;
        game.audio().play(sound);
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

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (backdrop == null) {
            shapes.setColor(0.18f, 0.35f, 0.25f, 1f);
            shapes.rect(viewport.left(), viewport.bottom(), viewport.visibleWidth(), viewport.visibleHeight());
        }
        drawPlayerRowShapes();
        drawSettingShapes();
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        float bob = MathUtils.sin(time * 2.5f) * 4f;
        text("WAITING ROOM", 0f, TITLE_Y + bob, 2.4f, Color.WHITE, Align.center, MENU_WIDTH);
        boolean full = lobby.isFull();
        String count;
        if (network.isClient() && network.myId() < 0) count = "JOINING...";
        else if (network.isClient()) count = "HOST: " + lobby.slot(0).name() + "   " + lobby.size() + " / " + lobby.settings.roomSize;
        else count = full ? "ROOM FULL! THE HOST CAN START" : lobby.size() + " / " + lobby.settings.roomSize + " PLAYERS";
        if (address != null && !full) count += "     JOIN AT " + address; // for phones the room search doesn't reach
        text(count, 0f, COUNT_Y, 1.2f, full ? Color.GOLD : Color.LIGHT_GRAY, Align.center, MENU_WIDTH);
        drawPlayerRowText();
        drawSettingText();
        if (messageTime > 0f) {
            text(message, 0f, HINT_Y + 36f, 1.1f, Color.SALMON, Align.center, MENU_WIDTH);
        }
        batch.end();

        if (network.isClient()) {
            hints.draw(batch, shapes, font, MENU_WIDTH / 2f, HINT_Y, 0.9f, ControlHints.Icon.B, "LEAVE THE ROOM");
        } else {
            hints.draw(batch, shapes, font, MENU_WIDTH / 2f, HINT_Y, 0.9f,
                ControlHints.Icon.DPAD_UP_DOWN, "CHOOSE", ControlHints.Icon.DPAD_LEFT_RIGHT, "CHANGE",
                ControlHints.Icon.A, "OK", ControlHints.Icon.B, "CLOSE ROOM");
        }
    }

    private float playerRowY(int i) {
        return ROWS_TOP - PLAYER_H - i * (PLAYER_H + PLAYER_GAP);
    }

    private void drawPlayerRowShapes() {
        for (int i = 0; i < lobby.settings.roomSize; i++) {
            float y = playerRowY(i);
            boolean in = i < lobby.size();
            shapes.setColor(0f, 0f, 0f, in ? 0.7f : 0.35f);
            shapes.rect(PLAYER_X, y, PLAYER_W, PLAYER_H);
            Color c = Player.SLOT_COLORS[i];
            shapes.setColor(c.r, c.g, c.b, in ? 1f : 0.25f);
            shapes.rect(PLAYER_X, y, 52f, PLAYER_H);
        }
    }

    private void drawPlayerRowText() {
        for (int i = 0; i < lobby.settings.roomSize; i++) {
            float y = playerRowY(i), mid = y + PLAYER_H / 2f;
            boolean in = i < lobby.size();
            text("P" + (i + 1), PLAYER_X, mid + 9f, 1.3f, in ? Color.WHITE : Color.GRAY, Align.center, 52f);
            if (in) {
                Lobby.Slot s = lobby.slot(i);
                text(s.name(), PLAYER_X + 66f, mid + 11f, 1.6f, Color.WHITE, Align.left, 0f);
                String tag = network.isClient() && i == network.myId() ? "YOU"
                    : s.isHost() ? "HOST" : network.isHosting() && s.isRemote() ? "PHONE" : null;
                if (tag != null) {
                    text(tag, PLAYER_X + PLAYER_W - 10f, mid + 7f, 1f, tag.equals("PHONE") ? Color.SKY : Color.GOLD, Align.right, 0f);
                }
            } else {
                float pulse = 0.5f + 0.5f * MathUtils.sin(time * 4f + i);
                text("WAITING...", PLAYER_X + 66f, mid + 11f, 1.4f, new Color(0.75f, 0.75f, 0.75f, 0.5f + 0.4f * pulse),
                    Align.left, 0f);
                if (!network.isClient()) text("PRESS A TO JOIN", PLAYER_X + PLAYER_W - 10f, mid + 6f, 0.85f, Color.GRAY, Align.right, 0f);
            }
        }
    }

    private void drawSettingShapes() {
        float pulse = (MathUtils.sin(time * 6f) + 1f) / 2f;
        for (int i = 0; i < ITEMS.length; i++) {
            Rectangle r = itemBounds[i];
            if (i == selected) {
                shapes.setColor(1f, 0.85f, 0.2f, 0.75f + 0.25f * pulse); // gold frame on the chosen one
                shapes.rect(r.x - 3f, r.y - 3f, r.width + 6f, r.height + 6f);
            }
            if (ITEMS[i] == Item.START) {
                boolean ready = lobby.isFull() && !network.isClient();
                shapes.setColor(ready ? 0.2f : 0.25f, ready ? 0.65f : 0.25f, ready ? 0.3f : 0.27f, 0.95f);
            } else {
                shapes.setColor(0f, 0f, 0f, 0.7f);
            }
            shapes.rect(r.x, r.y, r.width, r.height);
        }
    }

    private void drawSettingText() {
        Lobby.Settings s = lobby.settings;
        for (int i = 0; i < ITEMS.length; i++) {
            Rectangle r = itemBounds[i];
            float mid = r.y + r.height / 2f;
            Item item = ITEMS[i];
            if (item == Item.START) {
                String label = network.isClient() ? "THE HOST STARTS"
                    : lobby.isFull() ? "START" : "WAITING " + lobby.size() + "/" + s.roomSize;
                boolean bright = lobby.isFull() && !network.isClient();
                text(label, r.x, mid + 13f, network.isClient() ? 1.6f : 2f, bright ? Color.WHITE : Color.LIGHT_GRAY, Align.center, r.width);
                continue;
            }
            if (item == Item.NAMES) {
                text("NAMES", r.x, mid + 10f, 1.4f, Color.WHITE, Align.center, r.width);
                continue;
            }
            String label, value;
            switch (item) {
                case ROOM_SIZE: label = "PLAYERS"; value = String.valueOf(s.roomSize); break;
                case TIME: label = "TIME"; value = clock(s.matchSeconds); break;
                case STREET_EVENTS: label = "STREET EVENTS"; value = s.streetEvents ? "ON" : "OFF"; break;
                default: label = "TRAITS"; value = s.characterTraits ? "ON" : "OFF"; break;
            }
            text(label, r.x + 12f, mid + 9f, 1.2f, Color.LIGHT_GRAY, Align.left, 0f);
            Color valueColor = "OFF".equals(value) ? Color.SALMON : Color.WHITE;
            String shown = network.isClient() ? value : "<  " + value + "  >"; // only the host can change them
            text(shown, r.x + r.width - 12f, mid + 10f, 1.4f, valueColor, Align.right, 0f);
        }
    }

    private static String clock(float seconds) {
        int s = Math.round(seconds);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
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
        dispose();
    }

    @Override
    public void dispose() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapes != null) { shapes.dispose(); shapes = null; }
        if (font != null) { font.dispose(); font = null; }
        if (hints != null) { hints.dispose(); hints = null; }
        backdrop = null; // shared, owned by TumbalataGame
    }
}
