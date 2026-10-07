package ph.tumbalata.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * End-of-match awards shown on the victory screen. An award only goes out if someone actually did the thing, and
 * tied players share it. Titles and units are here so they are easy to rename.
 */
public final class Awards {
    private Awards() {}

    public static final class Award {
        public final String title;
        /** e.g. "3 KNOCKS". */
        public final String detail;
        /** Player ids (0 = P1), in player order. */
        public final int[] winners;

        Award(String title, String detail, int[] winners) {
            this.title = title;
            this.detail = detail;
            this.winners = winners;
        }

        /** "MAYA & JOJO", using each player's name (index = player id). */
        public String winnerNames(String[] names) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < winners.length; i++) {
                if (i > 0) s.append(" & ");
                s.append(names[winners[i]]);
            }
            return s.toString();
        }

        /** "P1 & P3". */
        public String winnerNames() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < winners.length; i++) {
                if (i > 0) s.append(" & ");
                s.append('P').append(winners[i] + 1);
            }
            return s.toString();
        }
    }

    public static Array<Award> compute(MatchStats stats) {
        int n = stats.players();
        int[] bestTaya = new int[n];
        for (int i = 0; i < n; i++) bestTaya[i] = stats.tags[i] + stats.tossHits[i];

        Array<Award> out = new Array<>();
        add(out, "ASINTADO", stats.knocks, "KNOCK", "KNOCKS");
        add(out, "BEST TAYA", bestTaya, "CATCH", "CATCHES");
        add(out, "TSINELAS CHAMPION", stats.safeRuns, "SAFE RUN", "SAFE RUNS");
        add(out, "MADULAS", stats.slips, "SLIP", "SLIPS");
        add(out, "MABAHO", stats.poopSteps, "STEP IN POOP", "STEPS IN POOP");
        add(out, "MALAS", stats.caught, "TIME CAUGHT", "TIMES CAUGHT");
        return out;
    }

    private static void add(Array<Award> out, String title, int[] values, String one, String many) {
        int best = 0;
        for (int v : values) best = Math.max(best, v);
        if (best == 0) return; // nobody did it
        IntArray winners = new IntArray();
        for (int i = 0; i < values.length; i++) if (values[i] == best) winners.add(i);
        out.add(new Award(title, best + " " + (best == 1 ? one : many), winners.toArray()));
    }
}
