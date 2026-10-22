package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A player on another phone, as the host's match sees them. */
class RemoteInputSourceTest {
    private static int frame(float moveX, int buttons) {
        return new InputFrame().set(moveX, 0f, buttons).pack();
    }

    @Test
    void eachStepTakesTheNextFrame() {
        RemoteInputSource source = new RemoteInputSource();
        source.add(frame(0.5f, 0));
        source.add(frame(1f, InputFrame.A));
        InputFrame out = new InputFrame();
        source.sample(1, out);
        assertEquals(0.5f, out.moveX(), 0.01f);
        source.sample(1, out);
        assertTrue(out.held(InputFrame.A));
    }

    @Test
    void whenNothingArrivedThePlayerKeepsHolding() {
        RemoteInputSource source = new RemoteInputSource();
        source.add(frame(1f, InputFrame.B));
        InputFrame out = new InputFrame();
        source.sample(1, out);
        source.sample(1, out); // the next frame is late
        assertEquals(1f, out.moveX(), 0.01f);
        assertTrue(out.held(InputFrame.B), "still held: no new press, since it never let go");
    }

    @Test
    void framesThatPileUpAreFoldedInWithoutLosingATap() {
        RemoteInputSource source = new RemoteInputSource();
        source.add(frame(0f, InputFrame.A)); // a quick tap...
        for (int i = 0; i < RemoteInputSource.MAX_WAITING + 2; i++) source.add(frame(0.25f * i, 0)); // ...then a burst
        InputFrame out = new InputFrame();
        source.sample(1, out);
        assertTrue(out.held(InputFrame.A), "the tap still counts");
        int left = 0;
        InputFrame last = new InputFrame();
        for (int i = 0; i < 10; i++) {
            source.sample(1, out);
            if (!out.equals(last)) left++;
            last.set(out);
        }
        assertTrue(left <= RemoteInputSource.MAX_WAITING, "never more than " + RemoteInputSource.MAX_WAITING + " steps behind");
    }

    @Test
    void aPlayerWhosePhoneDroppedStandsStill() {
        RemoteInputSource source = new RemoteInputSource();
        source.add(frame(1f, InputFrame.A));
        InputFrame out = new InputFrame();
        source.sample(1, out);
        source.disconnect();
        source.add(frame(1f, InputFrame.A)); // a late frame after the drop
        source.sample(1, out);
        assertEquals(0f, out.moveX(), 0f);
        assertFalse(out.held(InputFrame.A));
    }
}
