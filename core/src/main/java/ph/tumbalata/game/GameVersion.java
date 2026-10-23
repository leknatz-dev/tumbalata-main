package ph.tumbalata.game;

/**
 * Which build of the game this is. Raise both for every build shared with friends: {@link #BUILD} by one (Android only
 * installs an update with a higher number) and {@link #NAME} as you like. The Android app reads them from this file
 * (android/build.gradle), the main menu shows the name, and phones only play together on the same build.
 */
public final class GameVersion {
    /** Goes up by one with every shared build. */
    public static final int BUILD = 1;
    /** What players see, like 0.1.0. */
    public static final String NAME = "0.1.0";

    private GameVersion() {}
}
