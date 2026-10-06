package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Game options saved between sessions (same Preferences file as the audio volumes). */
public final class GameSettings {
    private static final String PREFS_NAME = "tumbalata";
    private static final String STREET_EVENTS = "streetEvents";

    private final Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
    private boolean streetEvents = prefs.getBoolean(STREET_EVENTS, true);

    /** Random street events during a match (trash thrown onto the court). */
    public boolean streetEvents() {
        return streetEvents;
    }

    public void setStreetEvents(boolean on) {
        streetEvents = on;
        prefs.putBoolean(STREET_EVENTS, on);
        prefs.flush();
    }
}
