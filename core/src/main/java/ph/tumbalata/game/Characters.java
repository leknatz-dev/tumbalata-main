package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Color;

/** The 4 playable characters (placeholders for now). Shared by CharacterSelectScreen and GameScreen. */
public final class Characters {
    private Characters() {}

    public static final int COUNT = 4;

    public static final String[] NAMES = { "CHAR 1", "CHAR 2", "CHAR 3", "CHAR 4" };

    // Optional portraits for the select screen. If a file is missing, a colored placeholder card is drawn.
    public static final String[] PORTRAIT_FILES = { "char1.png", "char2.png", "char3.png", "char4.png" };
    // Or one white character for all four cards (tinted per card). Character art is drawn white and tinted.
    public static final String SHARED_PORTRAIT_FILE = "character.png";

    // Placeholder card colors on the select screen
    public static final Color[] CARD_COLORS = {
        new Color(0.80f, 0.25f, 0.25f, 1f),
        new Color(0.25f, 0.40f, 0.85f, 1f),
        new Color(0.25f, 0.70f, 0.35f, 1f),
        new Color(0.90f, 0.75f, 0.20f, 1f)
    };

    // --- TRAITS (placeholder balance: small multipliers, 1 = normal). Index = character. ---
    // They apply on top of the role: Taya and Throwers have different base speeds (GameConstants), and a FAST
    // character is faster than normal in either role.
    public static final String[] TRAITS = { "FAST", "STRONG", "SNIPER", "SNEAKY" };
    public static final String[] TRAIT_HINTS = {
        "Runs faster, harder to aim",
        "Throws harder, a bit slower, harder to aim",
        "Long aim arrow and easy aim, weaker throw",
        "Grabs slippers from further away, a bit faster"
    };
    /** Running speed. */
    public static final float[] SPEED = { 1.12f, 0.92f, 1.00f, 1.05f };
    /** Slipper throw speed, and how far Taya tosses the can. */
    public static final float[] THROW = { 1.00f, 1.15f, 0.90f, 1.00f };
    /** Aim meter speed (angle swing and power bar): lower is slower, so easier to time. */
    public static final float[] AIM = { 1.20f, 1.15f, 0.75f, 1.00f };
    /** Reach for picking up their slipper. */
    public static final float[] REACH = { 1.00f, 1.00f, 1.00f, 1.40f };
    /** Gets the long aim arrow (assets/arrow_sniper.png) instead of the default one. */
    public static final boolean[] LONG_ARROW = { false, false, true, false };

    // Each character's colour in a match (slipper, aim arrow, name tag, HUD): bright versions of the card colours
    public static final Color[] COLORS = {
        new Color(0.95f, 0.30f, 0.30f, 1f),
        new Color(0.35f, 0.55f, 1.00f, 1f),
        new Color(0.35f, 0.85f, 0.40f, 1f),
        new Color(1.00f, 0.85f, 0.25f, 1f)
    };

    // In-game sprite tints (placeholder until each character has its own sprite sheet)
    public static final Color[] TINTS = {
        new Color(1.00f, 0.60f, 0.60f, 1f),
        new Color(0.60f, 0.70f, 1.00f, 1f),
        new Color(0.60f, 1.00f, 0.65f, 1f),
        new Color(1.00f, 0.95f, 0.55f, 1f)
    };
}