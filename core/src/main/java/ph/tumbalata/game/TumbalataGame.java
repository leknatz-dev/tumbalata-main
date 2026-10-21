package ph.tumbalata.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;

/**
 * Owns the window and screen changes. One window (or a phone screen) for the whole game, any size or shape: every
 * screen fits itself to it (menus with {@link MenuViewport}, the match letterboxed). Screens switch with
 * {@link #changeScreen(Screen)} and call {@link #toggleFullscreen()} on F11.
 */
public class TumbalataGame extends Game {
    // Platform step run before the controllers are read (the desktop launcher loads extra pad mappings here)
    private final Runnable controllerSetup;

    public TumbalataGame() {
        this(null);
    }

    /** @param controllerSetup run once at startup, before any controller is read (may be null) */
    public TumbalataGame(Runnable controllerSetup) {
        this.controllerSetup = controllerSetup;
    }

    // The window size before going fullscreen, to come back to
    private int windowedW, windowedH;

    // One live background shared by the menu screens, so the characters keep walking across screen changes
    private MenuBackdrop backdrop;
    private boolean backdropTried = false;

    /** The shared live menu background, or null if it couldn't be built (screens then use their image background). */
    public MenuBackdrop getBackdrop() {
        if (backdrop == null && !backdropTried) {
            backdropTried = true;
            backdrop = MenuBackdrop.tryCreate();
        }
        return backdrop;
    }

    private void releaseBackdrop() {
        if (backdrop != null) {
            backdrop.dispose();
            backdrop = null;
        }
        backdropTried = false;
    }

    private static boolean isMenuScreen(Screen screen) {
        return screen instanceof MainMenuScreen
            || screen instanceof PlayerSelectScreen
            || screen instanceof NameEntryScreen
            || screen instanceof CharacterSelectScreen
            || screen instanceof VictoryScreen;
    }

    // Sweep animation played over every screen change
    private ScreenTransition transition;

    // All keyboard + controller input (4 player slots, menu navigation). One instance for the whole game.
    private InputManager input;

    public InputManager input() {
        return input;
    }

    // All sound effects and music. One instance for the whole game.
    private Audio audio;

    public Audio audio() {
        return audio;
    }

    // Saved game options (street events on/off)
    private GameSettings settings;

    public GameSettings settings() {
        return settings;
    }

    // The players getting ready for the next match (names, characters, match settings). One for the whole game.
    private final Lobby lobby = new Lobby();

    public Lobby lobby() {
        return lobby;
    }

    @Override
    public void create() {
        transition = new ScreenTransition();
        if (controllerSetup != null) controllerSetup.run();
        input = new InputManager();
        audio = new Audio();
        settings = new GameSettings();
        lobby.settings.streetEvents = settings.streetEvents();
        windowedW = Gdx.graphics.getWidth();
        windowedH = Gdx.graphics.getHeight();
        setScreen(new MainMenuScreen(this));
    }

    // A screen change that was requested during this frame. It is carried out at the end of the frame, after the
    // current screen has drawn its last frame (which becomes the transition's snapshot).
    private Screen pendingScreen;

    /** True while a screen change is pending or its can sweep is still playing. */
    public boolean isTransitioning() {
        return transition.isActive() || pendingScreen != null;
    }

    /** Switches screens with the can sweep. Requests made while a transition is already playing are ignored. */
    public void changeScreen(Screen newScreen) {
        if (transition.isActive() || pendingScreen != null) return;
        pendingScreen = newScreen;
    }

    private void switchNow(Screen newScreen) {
        if (!isMenuScreen(newScreen)) {
            releaseBackdrop(); // not needed during the game or the victory screen
        }
        if (getScreen() != null) {
            getScreen().dispose();
        }
        setScreen(newScreen);
    }

    @Override
    public void render() {
        input.setMenuLocked(transition.isActive());
        input.update();  // once per frame, before the screen reads it
        if (!input.isTextEntry() && Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.M)) audio.toggleMute(); // M = mute (not while typing a name)
        audio.update(Gdx.graphics.getDeltaTime()); // music fades
        super.render(); // draws the current screen (and lets it read input, which may request a screen change)

        if (pendingScreen != null) {
            Screen next = pendingScreen;
            pendingScreen = null;

            transition.captureSnapshot();               // the old screen's last frame
            transition.begin(null);
            audio.play(Audio.Sfx.TRANSITION_ROLL);
            switchNow(next);                            // the new screen is underneath; the snapshot covers it
            return;
        }

        transition.update(Gdx.graphics.getDeltaTime());
        transition.draw();
    }

    public void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(windowedW, windowedH);
        } else {
            windowedW = Gdx.graphics.getWidth();
            windowedH = Gdx.graphics.getHeight();
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

    @Override
    public void dispose() {
        if (transition != null) transition.dispose();
        if (input != null) input.dispose();
        if (audio != null) audio.dispose();
        releaseBackdrop();
        super.dispose();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }
}