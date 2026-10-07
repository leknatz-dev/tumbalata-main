package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ManoTest {
    @Test
    void theOddHandIsTaya() {
        assertEquals(2, Mano.oddOneOut(new boolean[] { false, false, true, false }), "3 down, 1 up");
        assertEquals(0, Mano.oddOneOut(new boolean[] { false, true, true, true }), "3 up, 1 down");
        assertEquals(1, Mano.oddOneOut(new boolean[] { true, false, true }), "3 players: 2/1");
    }

    @Test
    void noOddHandMeansAgain() {
        assertEquals(-1, Mano.oddOneOut(new boolean[] { true, true, false, false }), "2/2");
        assertEquals(-1, Mano.oddOneOut(new boolean[] { true, true, true, true }), "all up");
        assertEquals(-1, Mano.oddOneOut(new boolean[] { false, false, false, false }), "all down");
        assertEquals(-1, Mano.oddOneOut(new boolean[] { false, false, false }), "3 the same");
    }

    @Test
    void twoPlayersDifferentHandsMakeThePalmUpOneTaya() {
        assertEquals(1, Mano.oddOneOut(new boolean[] { false, true }));
        assertEquals(-1, Mano.oddOneOut(new boolean[] { true, true }));
    }

    @Test
    void aFullRoundCountsDownRevealsAndPicksTheTaya() {
        Mano mano = new Mano(4, new Random(1));
        mano.choose(3, true);                            // only P4 palm up; the others pressed nothing (down)
        assertEquals(null, mano.update(Mano.CHOOSE_SECONDS - 0.01f));
        mano.choose(0, true);
        mano.choose(0, false);                           // changed their mind before the end
        assertEquals(Mano.Phase.REVEAL, mano.update(0.02f));
        assertEquals(3, mano.taya());
        mano.choose(1, true);                            // too late: ignored
        assertTrue(!mano.isPalmUp(1));
        assertEquals(Mano.Phase.DONE, mano.update(Mano.REVEAL_SECONDS));
        assertEquals(3, mano.taya());
    }

    @Test
    void againStartsANewRoundWithFreshHands() {
        Mano mano = new Mano(4, new Random(1));
        mano.choose(0, true);
        mano.choose(1, true);                            // 2/2
        mano.update(Mano.CHOOSE_SECONDS);
        assertTrue(mano.isAgain());
        assertEquals(Mano.Phase.CHOOSING, mano.update(Mano.REVEAL_SECONDS));
        assertTrue(!mano.isPalmUp(0), "hands reset to palm down");
        assertEquals(Mano.CHOOSE_SECONDS, mano.secondsLeft(), 0.001f);
    }

    @Test
    void neverGetsStuck() {
        Mano mano = new Mano(4, new Random(7));
        for (int round = 0; round < 20 && mano.phase() != Mano.Phase.DONE; round++) {
            mano.update(Mano.CHOOSE_SECONDS);            // nobody presses anything: all palm down, every time
            mano.update(Mano.REVEAL_SECONDS);
        }
        assertEquals(Mano.Phase.DONE, mano.phase());
        assertTrue(mano.taya() >= 0 && mano.taya() < 4);
    }
}
