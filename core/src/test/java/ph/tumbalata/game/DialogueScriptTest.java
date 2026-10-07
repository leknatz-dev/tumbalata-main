package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DialogueScriptTest {
    private static final String TEXT = String.join("\n",
        "# notes are ignored",
        "[2]",
        "P1: Uy {P2}! Laro tayo!",
        "P2: Sige!",
        "ALL: Maiba taya!",
        "[3]",
        "P3: {P1} at {P2}, tara!",
        "nonsense line",
        "P9: nobody",
        "[3]",
        "P1: Second variant");

    @Test
    void readsScriptsAndFillsInNames() {
        DialogueScript script = DialogueScript.parse(TEXT);
        Array<DialogueScript.Line> lines = script.pick(2, new String[] { "MAYA", "JOJO" }, new Random(1));
        assertEquals(3, lines.size);
        assertEquals(0, lines.get(0).speaker);
        assertEquals("Uy JOJO! Laro tayo!", lines.get(0).text);
        assertEquals(DialogueScript.Line.ALL, lines.get(2).speaker);
    }

    @Test
    void picksAmongVariantsAndDropsSpeakersNotInTheGame() {
        DialogueScript script = DialogueScript.parse(TEXT);
        assertEquals(1, script.problems(), "the line without a speaker");
        boolean sawFirst = false, sawSecond = false;
        Random rng = new Random(42);
        for (int i = 0; i < 40; i++) {
            Array<DialogueScript.Line> lines = script.pick(3, new String[] { "A", "B", "C" }, rng);
            if (lines.first().text.equals("A at B, tara!")) {
                sawFirst = true;
                assertEquals(1, lines.size, "P9 isn't playing: dropped");
            } else {
                sawSecond = true;
            }
        }
        assertTrue(sawFirst && sawSecond, "both variants come up");
    }

    @Test
    void noScriptForThisCountMeansNoDialogue() {
        assertEquals(0, DialogueScript.parse(TEXT).pick(4, new String[] { "A", "B", "C", "D" }, new Random(1)).size);
    }

    @Test
    void theRealScriptHasEveryPlayerCount() throws Exception {
        String text = new String(Files.readAllBytes(new File("../assets/dialogue/intro.txt").toPath()), "UTF-8");
        DialogueScript script = DialogueScript.parse(text);
        assertEquals(0, script.problems(), "every line in intro.txt reads correctly");
        for (int players = 2; players <= 4; players++) {
            String[] names = { "A", "B", "C", "D" };
            Array<DialogueScript.Line> lines = script.pick(players, java.util.Arrays.copyOf(names, players), new Random(3));
            assertTrue(lines.size > 0, "a script for " + players + " players");
            for (DialogueScript.Line l : lines) assertTrue(!l.text.contains("{P"), "names filled in: " + l.text);
        }
    }
}
