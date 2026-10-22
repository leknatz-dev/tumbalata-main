package ph.tumbalata.game.android;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import ph.tumbalata.game.TumbalataGame;

/** Starts the game on Android: full screen (the bars hidden, the camera cutout used too), kept awake while open. */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // draw under the camera cutout instead of a black strip
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true; // the game fills the screen; swipe from an edge to see the bars
        config.useWakelock = true;      // the screen doesn't dim during a match
        config.numSamples = 2;          // a little smoothing on edges (the desktop uses 4)
        initialize(new TumbalataGame(), config);
    }
}
