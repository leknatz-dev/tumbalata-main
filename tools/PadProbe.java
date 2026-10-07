/**
 * Lists every game controller Windows exposes, with the GUID needed for assets/gamecontrollerdb.txt and whether the
 * window library's own list knows it ("gamepad=true"; a close guide to what the game's controller library knows).
 * If the game does not see a pad listed here, add a mapping line for its GUID to that file.
 *
 * Usage (from the repo root, after a build):
 *     java -cp lwjgl3/build/libs/TUMBALATAEXPO-1.0.0.jar tools/PadProbe.java
 */
import static org.lwjgl.glfw.GLFW.*;
import java.nio.*;
public class PadProbe { public static void main(String[] a) {
  if (!glfwInit()) { System.out.println("glfw init failed"); return; }
  for (int j = GLFW_JOYSTICK_1; j <= GLFW_JOYSTICK_LAST; j++) {
    if (!glfwJoystickPresent(j)) continue;
    FloatBuffer ax = glfwGetJoystickAxes(j); ByteBuffer bt = glfwGetJoystickButtons(j); ByteBuffer ht = glfwGetJoystickHats(j);
    System.out.printf("joystick %d: name=\"%s\" guid=%s gamepad=%s buttons=%d axes=%d hats=%d%n", j, glfwGetJoystickName(j), glfwGetJoystickGUID(j),
      glfwJoystickIsGamepad(j), bt == null ? -1 : bt.limit(), ax == null ? -1 : ax.limit(), ht == null ? -1 : ht.limit());
  }
  glfwTerminate(); } }
