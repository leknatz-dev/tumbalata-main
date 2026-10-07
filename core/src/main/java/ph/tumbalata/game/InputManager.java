package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;

/**
 * ONE place for all input. Created once by TumbalataGame, used by every screen.
 *
 *  - 4 player slots (MAX_PLAYERS). Slot 0 = Player 1 ... slot 3 = Player 4.
 *  - Controllers are assigned to the first free slot as they connect (or are already connected at startup),
 *    and the slot is freed when one is unplugged. Identical pads work fine: slots follow plug-in order.
 *  - Every slot also has a keyboard scheme (P1 WASD, P2 arrows, P3 IJKL, P4 numpad), so it is testable without pads.
 *  - update() runs once per frame BEFORE the screen, so every screen sees the same state.
 *  - menu() gives merged input for the menus: any controller or the keyboard can navigate.
 *
 * Nothing here allocates per frame, and there is a single controller listener for the whole game.
 */
public final class InputManager {
    public static final int MAX_PLAYERS = 4;

    /** Prints each controller button number to the console. Handy while finding the numbers for controller.properties. */
    private static final boolean DEBUG_PRINT_BUTTONS = false;

    // ---- keyboard schemes (null = no keyboard for that slot) ----
    private static final class KeyScheme {
        final int[] up, down, left, right, a, b, select, start;
        KeyScheme(int[] up, int[] down, int[] left, int[] right, int[] a, int[] b, int[] select, int[] start) {
            this.up = up; this.down = down; this.left = left; this.right = right;
            this.a = a; this.b = b; this.select = select; this.start = start;
        }
    }

    // Player 1: WASD. A = Space, B = E, Select = R.
    private static final KeyScheme KEYS_P1 = new KeyScheme(
        new int[]{Input.Keys.W}, new int[]{Input.Keys.S}, new int[]{Input.Keys.A}, new int[]{Input.Keys.D},
        new int[]{Input.Keys.SPACE}, new int[]{Input.Keys.E}, new int[]{Input.Keys.R}, new int[]{});

    // Player 2: arrows. Space and E work here too (so one person can test both roles, like before), plus Enter / Right Shift.
    private static final KeyScheme KEYS_P2 = new KeyScheme(
        new int[]{Input.Keys.UP}, new int[]{Input.Keys.DOWN}, new int[]{Input.Keys.LEFT}, new int[]{Input.Keys.RIGHT},
        new int[]{Input.Keys.SPACE, Input.Keys.ENTER}, new int[]{Input.Keys.E, Input.Keys.SHIFT_RIGHT},
        new int[]{Input.Keys.R}, new int[]{});

    // Player 3: I J K L. A = U, B = O. (So 3-4 player matches can be tested on one keyboard.)
    private static final KeyScheme KEYS_P3 = new KeyScheme(
        new int[]{Input.Keys.I}, new int[]{Input.Keys.K}, new int[]{Input.Keys.J}, new int[]{Input.Keys.L},
        new int[]{Input.Keys.U}, new int[]{Input.Keys.O}, new int[]{}, new int[]{});

    // Player 4: numpad 8 4 5 6. A = Numpad 0, B = Numpad . (Num Lock on)
    private static final KeyScheme KEYS_P4 = new KeyScheme(
        new int[]{Input.Keys.NUMPAD_8}, new int[]{Input.Keys.NUMPAD_5}, new int[]{Input.Keys.NUMPAD_4},
        new int[]{Input.Keys.NUMPAD_6}, new int[]{Input.Keys.NUMPAD_0}, new int[]{Input.Keys.NUMPAD_DOT},
        new int[]{}, new int[]{});

    private static final KeyScheme[] KEY_SCHEMES = { KEYS_P1, KEYS_P2, KEYS_P3, KEYS_P4 };

    // ---- one controller slot ----
    private static final class Pad {
        Controller controller;          // null = empty slot
        ControllerProfile profile;

        // raw state this frame (controller only)
        boolean up, down, left, right, a, b, select, start;
        // previous frame, for "just pressed"
        boolean pUp, pDown, pLeft, pRight, pA, pB, pSelect, pStart;
        // pressed-this-frame (controller only; used by the menus)
        boolean upPressed, downPressed, leftPressed, rightPressed, aPressed, bPressed, selectPressed, startPressed;
    }

    private final Pad[] pads = new Pad[MAX_PLAYERS];
    private final PlayerInput[] players = new PlayerInput[MAX_PLAYERS];
    private final boolean[] prevKb = new boolean[MAX_PLAYERS * 8]; // previous keyboard state per slot, for edges

    private final MenuInput menu = new MenuInput();
    private boolean menuLocked = false;
    private boolean textEntry = false; // typing a name: keyboard keys are text, not player controls
    private final ControllerAdapter listener;

    public InputManager() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            pads[i] = new Pad();
            players[i] = new PlayerInput();
        }

        // Controllers that were already plugged in, in the order the system lists them
        Array<Controller> existing = Controllers.getControllers();
        for (int i = 0; i < existing.size; i++) assign(existing.get(i));

        listener = new ControllerAdapter() {
            @Override
            public void connected(Controller controller) {
                assign(controller);
            }

            @Override
            public void disconnected(Controller controller) {
                release(controller);
            }

            @Override
            public boolean buttonDown(Controller controller, int buttonCode) {
                if (DEBUG_PRINT_BUTTONS) {
                    Gdx.app.log("Input", controller.getName() + " pressed button " + buttonCode);
                }
                return false;
            }
        };
        Controllers.addListener(listener);
    }

    // ------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------

    private void assign(Controller controller) {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (pads[i].controller == controller) return; // already known
        }
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (pads[i].controller == null) {
                pads[i].controller = controller;
                pads[i].profile = ControllerProfile.forController(controller);
                ControllerProfile pr = pads[i].profile;
                Gdx.app.log("Input", "Player " + (i + 1) + " controller: " + controller.getName() + " (A=" + pr.a + " B=" + pr.b
                    + " Select=" + pr.select + " Start=" + pr.start + ", buttons " + controller.getMinButtonIndex() + ".."
                    + controller.getMaxButtonIndex() + ")");
                return;
            }
        }
        Gdx.app.log("Input", "All " + MAX_PLAYERS + " controller slots are taken, ignoring: " + controller.getName());
    }

    private void release(Controller controller) {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (pads[i].controller == controller) {
                Gdx.app.log("Input", "Player " + (i + 1) + " controller disconnected");
                Pad p = pads[i];
                p.controller = null;
                p.profile = null;
                p.up = p.down = p.left = p.right = p.a = p.b = p.select = p.start = false;
                return;
            }
        }
    }

    /** Input for a player slot (0 = Player 1 ... 3 = Player 4). Never null. */
    public PlayerInput player(int slot) {
        return players[slot];
    }

    /** Merged input for the menus (any controller, or the keyboard). */
    public MenuInput menu() {
        return menu;
    }

    public boolean isControllerConnected(int slot) {
        return pads[slot].controller != null;
    }

    public String getControllerName(int slot) {
        return pads[slot].controller == null ? null : pads[slot].controller.getName();
    }

    public int connectedControllerCount() {
        int n = 0;
        for (int i = 0; i < MAX_PLAYERS; i++) if (pads[i].controller != null) n++;
        return n;
    }

    // ------------------------------------------------------------------
    // Per-frame update
    // ------------------------------------------------------------------

    /** Call once per frame, before the screen renders. */
    public void update() {
        for (int slot = 0; slot < MAX_PLAYERS; slot++) {
            Pad pad = pads[slot];
            readPad(pad);
            mergeIntoPlayer(slot, pad);
        }
        menu.update(pads);
        if (menuLocked) menu.clearNavigation();
    }

    /**
     * While true the keyboard is for typing (names): the per-player keyboard schemes and the M mute key are off, so
     * typing "SAM" doesn't move Player 1. Controllers keep working.
     */
    public void setTextEntry(boolean on) {
        textEntry = on;
    }

    public boolean isTextEntry() {
        return textEntry;
    }

    /** While true the menus ignore input (used during the screen transition, so a button mash can't hit the new screen). */
    public void setMenuLocked(boolean locked) {
        menuLocked = locked;
    }

    /** True during a screen transition. Menus that read per-player input (not menu()) should ignore it then. */
    public boolean isMenuLocked() {
        return menuLocked;
    }

    private static boolean button(Controller c, int code) {
        return code >= 0 && code >= c.getMinButtonIndex() && code <= c.getMaxButtonIndex() && c.getButton(code);
    }

    private static float axis(Controller c, int code) {
        return (code >= 0 && code < c.getAxisCount()) ? c.getAxis(code) : 0f;
    }

    private void readPad(Pad p) {
        p.pUp = p.up; p.pDown = p.down; p.pLeft = p.left; p.pRight = p.right;
        p.pA = p.a; p.pB = p.b; p.pSelect = p.select; p.pStart = p.start;

        Controller c = p.controller;
        if (c == null) {
            p.up = p.down = p.left = p.right = p.a = p.b = p.select = p.start = false;
        } else {
            ControllerProfile prof = p.profile;

            p.a = button(c, prof.a);
            p.b = button(c, prof.b);
            p.select = button(c, prof.select);
            p.start = button(c, prof.start);

            // D-pad buttons first, then the axes (USB retro pads usually report the D-pad as axes)
            boolean up = button(c, prof.dpadUp), down = button(c, prof.dpadDown);
            boolean left = button(c, prof.dpadLeft), right = button(c, prof.dpadRight);

            float ax = axis(c, prof.axisX);
            float ay = axis(c, prof.axisY);
            if (prof.invertY) ay = -ay;
            if (ax < -prof.deadZone) left = true;
            else if (ax > prof.deadZone) right = true;
            if (ay < -prof.deadZone) up = true;
            else if (ay > prof.deadZone) down = true;

            p.up = up; p.down = down; p.left = left; p.right = right;
        }

        p.upPressed = p.up && !p.pUp;
        p.downPressed = p.down && !p.pDown;
        p.leftPressed = p.left && !p.pLeft;
        p.rightPressed = p.right && !p.pRight;
        p.aPressed = p.a && !p.pA;
        p.bPressed = p.b && !p.pB;
        p.selectPressed = p.select && !p.pSelect;
        p.startPressed = p.start && !p.pStart;
    }

    private static boolean anyKey(int[] keys) {
        for (int i = 0; i < keys.length; i++) {
            if (Gdx.input.isKeyPressed(keys[i])) return true;
        }
        return false;
    }

    private void mergeIntoPlayer(int slot, Pad pad) {
        PlayerInput in = players[slot];
        KeyScheme ks = KEY_SCHEMES[slot];

        boolean up = pad.up, down = pad.down, left = pad.left, right = pad.right;
        boolean a = pad.a, b = pad.b, select = pad.select, start = pad.start;

        if (ks != null && !textEntry) {
            up |= anyKey(ks.up);
            down |= anyKey(ks.down);
            left |= anyKey(ks.left);
            right |= anyKey(ks.right);
            a |= anyKey(ks.a);
            b |= anyKey(ks.b);
            select |= anyKey(ks.select);
            start |= anyKey(ks.start);
        }

        // edges against the previous merged state (keyboard + pad together)
        int o = slot * 8;
        in.upPressed = up && !prevKb[o];
        in.downPressed = down && !prevKb[o + 1];
        in.leftPressed = left && !prevKb[o + 2];
        in.rightPressed = right && !prevKb[o + 3];
        in.aPressed = a && !prevKb[o + 4];
        in.bPressed = b && !prevKb[o + 5];
        in.selectPressed = select && !prevKb[o + 6];
        in.startPressed = start && !prevKb[o + 7];
        prevKb[o] = up; prevKb[o + 1] = down; prevKb[o + 2] = left; prevKb[o + 3] = right;
        prevKb[o + 4] = a; prevKb[o + 5] = b; prevKb[o + 6] = select; prevKb[o + 7] = start;

        in.up = up; in.down = down; in.left = left; in.right = right;
        in.a = a; in.b = b; in.select = select; in.start = start;
        in.controllerConnected = pad.controller != null;

        float mx = (right ? 1f : 0f) - (left ? 1f : 0f);
        float my = (up ? 1f : 0f) - (down ? 1f : 0f);
        if (mx != 0f && my != 0f) { // diagonal: normalize without allocating
            mx *= 0.70710678f;
            my *= 0.70710678f;
        }
        in.moveX = mx;
        in.moveY = my;
    }

    public void dispose() {
        Controllers.removeListener(listener);
    }

    // ------------------------------------------------------------------
    // Menu input
    // ------------------------------------------------------------------

    /**
     * Merged input for the menus. Any controller (any slot) or the keyboard can drive any menu.
     * All values are "pressed this frame".
     */
    public static final class MenuInput {
        public boolean up, down, left, right;
        public boolean confirm;      // A / Start on any pad, or Enter / Space / E
        public boolean back;         // B / Select on any pad, or Esc
        public boolean fullscreen;   // F11

        void clearNavigation() {
            up = down = left = right = confirm = back = false;
        }

        void update(Pad[] pads) {
            boolean kUp = Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W);
            boolean kDown = Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S);
            boolean kLeft = Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A);
            boolean kRight = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D);
            boolean kConfirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.E);
            boolean kBack = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE);

            up = kUp; down = kDown; left = kLeft; right = kRight; confirm = kConfirm; back = kBack;
            fullscreen = Gdx.input.isKeyJustPressed(Input.Keys.F11);

            for (int i = 0; i < pads.length; i++) {
                Pad p = pads[i];
                if (p.controller == null) continue;
                up |= p.upPressed;
                down |= p.downPressed;
                left |= p.leftPressed;
                right |= p.rightPressed;
                confirm |= p.aPressed || p.startPressed;
                back |= p.bPressed || p.selectPressed;
            }
        }
    }
}
