package ph.tumbalata.game;

import java.util.function.IntFunction;

/**
 * Input from this device (keyboard and pads, through {@link InputManager}) as {@link InputFrame}s. The screen draws
 * at its own pace and the match steps at 60 a second, so a quick tap could start and end between two steps: every
 * press is remembered until the next {@link #sample}, which then reports the button as held, so the match still sees
 * it go down.
 */
public final class LocalInputSource implements InputSource {
    private final IntFunction<PlayerInput> live;
    private final int[] tapped = new int[InputManager.MAX_PLAYERS]; // buttons pressed since the last sample

    public LocalInputSource(InputManager inputs) {
        this(inputs::player);
    }

    /** {@code live}: this frame's input per player slot. */
    LocalInputSource(IntFunction<PlayerInput> live) {
        this.live = live;
    }

    /** Once a frame, after the input is read: remembers this frame's presses for the next step. */
    public void update() {
        for (int p = 0; p < tapped.length; p++) {
            PlayerInput in = live.apply(p);
            if (in == null) continue;
            if (in.aPressed) tapped[p] |= InputFrame.A;
            if (in.bPressed) tapped[p] |= InputFrame.B;
            if (in.startPressed) tapped[p] |= InputFrame.START;
        }
    }

    @Override
    public void sample(int player, InputFrame out) {
        PlayerInput in = live.apply(player);
        int held = (in.a ? InputFrame.A : 0) | (in.b ? InputFrame.B : 0) | (in.start ? InputFrame.START : 0);
        out.set(in.moveX, in.moveY, held | tapped[player]);
        tapped[player] = 0;
    }
}
