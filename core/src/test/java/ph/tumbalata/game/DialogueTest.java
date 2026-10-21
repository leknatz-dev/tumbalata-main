package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class DialogueTest {
    private static final float DT = FixedStep.STEP;

    private static Dialogue dialogue(String script) {
        return new Dialogue(DialogueScript.parse("[4]\n" + script).pick(4, new String[] { "A", "B", "C", "D" }, new Random(1)));
    }

    private static void run(Dialogue d, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) d.update(DT, false, false);
    }

    @Test
    void theFirstSpeakerRisesThenTheTextTypes() {
        Dialogue d = dialogue("P1: Uy! Laro tayo!");
        run(d, Dialogue.RISE_SECONDS - 0.05f);
        assertEquals(0, d.shownChars(), "still rising");
        assertEquals(1f, d.slideProgress(), "the box starts on their side");
        run(d, 0.05f + 0.25f);
        assertEquals(10, d.shownChars(), 1, "40 letters a second");
    }

    @Test
    void theOtherSideSlidesOverFirst() {
        Dialogue d = dialogue("P1: Uy!\nP2: Sige!");
        d.update(DT, true, false); // finish
        d.update(DT, true, false); // next line: P2, the other side
        assertEquals(0f, d.slideProgress(), 0.001f);
        run(d, Dialogue.SLIDE_SECONDS);
        assertEquals(1f, d.slideProgress(), 0.001f);
        assertEquals(0f, d.riseProgress(), 0.06f, "rising only after the slide");
        assertEquals(0, d.shownChars());
        run(d, Dialogue.RISE_SECONDS + 0.1f);
        assertTrue(d.shownChars() > 0, "then it types");
    }

    @Test
    void theSameSpeakerAgainJustTypes() {
        Dialogue d = dialogue("P1: Uy!\nP1: Tara!");
        d.update(DT, true, false);
        d.update(DT, true, false);
        assertEquals(1f, d.riseProgress());
        run(d, 0.1f);
        assertTrue(d.shownChars() > 0);
    }

    @Test
    void nextFinishesTheLineThenGoesOn() {
        Dialogue d = dialogue("P1: Uy! Laro tayo!\nP3: Sige!");
        d.update(DT, true, false);
        assertTrue(d.isLineDone(), "first press: the whole line");
        assertEquals(0, d.index());
        d.update(DT, true, false);
        assertEquals(1, d.index(), "second press: the next line");
        d.update(DT, true, false);
        d.update(DT, true, false);
        assertTrue(d.isFinished());
    }

    @Test
    void holdingSkipEndsItAndLettingGoResets() {
        Dialogue d = dialogue("P1: Uy!\nP2: Sige!");
        for (int i = 0; i < 30; i++) d.update(DT, false, true); // half a second
        for (int i = 0; i < 2; i++) d.update(DT, false, false); // let go
        assertEquals(0f, d.skipProgress());
        for (int i = 0; i < Math.round(Dialogue.SKIP_HOLD_SECONDS / DT) + 1; i++) d.update(DT, false, true);
        assertTrue(d.isFinished());
    }
}
