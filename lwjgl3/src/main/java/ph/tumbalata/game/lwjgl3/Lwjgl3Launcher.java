package ph.tumbalata.game.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.controllers.desktop.JamepadControllerManager;
import ph.tumbalata.game.TumbalataGame;

public class Lwjgl3Launcher {
    /** Extra pad mappings (assets/gamecontrollerdb.txt) for pads the controller library doesn't know. */
    private static final String EXTRA_MAPPINGS = "/gamecontrollerdb.txt";

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Tumbalata");
        config.setWindowedMode(700, 500);
        config.setResizable(true); // Ensures seamless viewport stretching in fullscreen/maximized
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);

        new Lwjgl3Application(new TumbalataGame(Lwjgl3Launcher::loadExtraPadMappings), config);
    }

    /** Starts the controller library, then teaches it the extra pads, before the game reads any controller. */
    private static void loadExtraPadMappings() {
        Controllers.getControllers(); // starts the controller library
        try {
            JamepadControllerManager.addMappingsFromFile(EXTRA_MAPPINGS);
        } catch (Exception e) {
            Gdx.app.error("Input", "Could not load " + EXTRA_MAPPINGS + ": " + e.getMessage());
        }
    }
}
