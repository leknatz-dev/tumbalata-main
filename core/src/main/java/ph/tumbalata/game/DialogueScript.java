package ph.tumbalata.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import java.util.Random;

/**
 * The intro dialogue script (assets/dialogue/intro.txt): one or more scripts per player count, one line per speech.
 * No drawing, so it can be tested.
 *
 * <pre>
 * [4]                      a script for 4 players (several [4] sections = random variants)
 * P1: Uy! Laro tayo!       speaker P1..P4 or ALL, then the text; {P1}..{P4} become the players' names
 * # a note                 ignored
 * </pre>
 */
public final class DialogueScript {
    /** One line of dialogue. {@link #speaker} is a player id, or {@link #ALL} for everyone at once. */
    public static final class Line {
        public static final int ALL = -1;
        public final int speaker;
        public final String text;

        Line(int speaker, String text) {
            this.speaker = speaker;
            this.text = text;
        }
    }

    private final IntMap<Array<Array<Line>>> scripts = new IntMap<>(); // player count -> variants -> lines

    /** Parses the script text. Bad lines are skipped (and counted in {@link #problems()}). */
    public static DialogueScript parse(String text) {
        DialogueScript script = new DialogueScript();
        Array<Line> current = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                try {
                    int count = Integer.parseInt(line.substring(1, line.length() - 1).trim());
                    current = new Array<>();
                    if (!script.scripts.containsKey(count)) script.scripts.put(count, new Array<>());
                    script.scripts.get(count).add(current);
                } catch (NumberFormatException e) {
                    current = null;
                    script.problems++;
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (current == null || colon < 0) {
                script.problems++;
                continue;
            }
            String who = line.substring(0, colon).trim().toUpperCase();
            String say = line.substring(colon + 1).trim();
            int speaker;
            if (who.equals("ALL")) speaker = Line.ALL;
            else if (who.length() == 2 && who.charAt(0) == 'P' && Character.isDigit(who.charAt(1))) speaker = who.charAt(1) - '1';
            else {
                script.problems++;
                continue;
            }
            current.add(new Line(speaker, say));
        }
        return script;
    }

    private int problems = 0;

    /** Lines that could not be read (for a log message). */
    public int problems() {
        return problems;
    }

    /**
     * A random script for this many players, with names filled in, or an empty array if there is none. Lines whose
     * speaker isn't in the game are dropped.
     */
    public Array<Line> pick(int players, String[] names, Random rng) {
        Array<Line> out = new Array<>();
        Array<Array<Line>> variants = scripts.get(players);
        if (variants == null || variants.size == 0) return out;
        Array<Line> chosen = variants.get(rng.nextInt(variants.size));
        for (Line l : chosen) {
            if (l.speaker >= players) continue;
            String text = l.text;
            for (int p = 0; p < names.length; p++) text = text.replace("{P" + (p + 1) + "}", names[p]);
            out.add(new Line(l.speaker, text));
        }
        return out;
    }
}
