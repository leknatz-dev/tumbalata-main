package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;

import java.util.Properties;

/**
 * Which physical button/axis numbers mean what, for one kind of controller.
 *
 * Numbers come from the pad's own mapping when the controller library recognises it (usually A = 0, B = 1,
 * Select = 4, Start = 6). Otherwise the raw defaults below are used (A = 0, B = 1, Select = 8, Start = 9, D-pad on axes 0
 * and 1). The console shows the numbers in use for each pad when it connects.
 * To change them WITHOUT recompiling, create assets/controller.properties, for example:
 *
 *     a=0
 *     b=1
 *     select=8
 *     start=9
 *     axisX=0
 *     axisY=1
 *     invertY=false
 *
 * Run the game and look at the console (DEBUG_PRINT_BUTTONS in InputManager) to see the numbers your pad sends.
 */
public final class ControllerProfile {
    private static final String FILE = "controller.properties";

    // Physical codes for the logical buttons
    public int a = 0;
    public int b = 1;
    public int select = 8;
    public int start = 9;

    // D-pad: real D-pad buttons if the pad's mapping has them, otherwise these axes
    public int axisX = 0;
    public int axisY = 1;
    public boolean invertY = false;   // false: axis value < 0 means UP (what your pads do)
    public float deadZone = 0.5f;

    // D-pad button codes taken from the controller's own mapping (-1 = not available)
    public int dpadUp = -1, dpadDown = -1, dpadLeft = -1, dpadRight = -1;

    /** Builds the profile for a controller: defaults, then the controller's own D-pad mapping, then the .properties file. */
    public static ControllerProfile forController(Controller controller) {
        ControllerProfile p = new ControllerProfile();

        // A pad the controller library recognises reports its buttons in a standard layout (e.g. Select = 4,
        // Start = 6), not the raw numbers above. Use those whenever it gives them.
        ControllerMapping m = controller.getMapping();
        if (m != null) {
            if (m.buttonA >= 0) p.a = m.buttonA;
            if (m.buttonB >= 0) p.b = m.buttonB;
            if (m.buttonBack >= 0) p.select = m.buttonBack;
            if (m.buttonStart >= 0) p.start = m.buttonStart;
            p.dpadUp = m.buttonDpadUp;
            p.dpadDown = m.buttonDpadDown;
            p.dpadLeft = m.buttonDpadLeft;
            p.dpadRight = m.buttonDpadRight;
        }

        try {
            if (Gdx.files.internal(FILE).exists()) {
                Properties props = new Properties();
                props.load(Gdx.files.internal(FILE).read());
                p.a = readInt(props, "a", p.a);
                p.b = readInt(props, "b", p.b);
                p.select = readInt(props, "select", p.select);
                p.start = readInt(props, "start", p.start);
                p.axisX = readInt(props, "axisX", p.axisX);
                p.axisY = readInt(props, "axisY", p.axisY);
                p.invertY = Boolean.parseBoolean(props.getProperty("invertY", String.valueOf(p.invertY)));
                p.deadZone = Float.parseFloat(props.getProperty("deadZone", String.valueOf(p.deadZone)));
            }
        } catch (Exception e) {
            Gdx.app.error("Input", "Could not read " + FILE + ": " + e);
        }
        return p;
    }

    private static int readInt(Properties props, String key, int fallback) {
        String v = props.getProperty(key);
        return (v == null) ? fallback : Integer.parseInt(v.trim());
    }
}