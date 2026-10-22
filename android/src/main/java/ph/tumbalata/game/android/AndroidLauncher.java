package ph.tumbalata.game.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import ph.tumbalata.game.TumbalataGame;

/** Starts the game on Android: full screen (the bars hidden), kept awake while it's open. */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true; // the game fills the screen; swipe from an edge to see the bars
        config.useWakelock = true;      // the screen doesn't dim during a match
        config.numSamples = 2;          // a little smoothing on edges (the desktop uses 4)
        initialize(new TumbalataGame(), config);
    }
}
