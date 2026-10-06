package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

class AwardsTest {
    private static Awards.Award find(Array<Awards.Award> awards, String title) {
        for (Awards.Award a : awards) if (a.title.equals(title)) return a;
        return null;
    }

    @Test
    void nothingHappenedMeansNoAwards() {
        assertEquals(0, Awards.compute(new MatchStats(3)).size);
    }

    @Test
    void mostKnocksWinsAsintado() {
        MatchStats s = new MatchStats(3);
        s.knocks[0] = 1;
        s.knocks[2] = 3;
        Awards.Award a = find(Awards.compute(s), "ASINTADO");
        assertArrayEquals(new int[] { 2 }, a.winners);
        assertEquals("3 KNOCKS", a.detail);
        assertEquals("P3", a.winnerNames());
    }

    @Test
    void tiedPlayersShareAnAward() {
        MatchStats s = new MatchStats(4);
        s.safeRuns[1] = 2;
        s.safeRuns[3] = 2;
        Awards.Award a = find(Awards.compute(s), "TSINELAS CHAMPION");
        assertArrayEquals(new int[] { 1, 3 }, a.winners);
        assertEquals("P2 & P4", a.winnerNames());
    }

    @Test
    void bestTayaCountsTagsAndTossHits() {
        MatchStats s = new MatchStats(2);
        s.tags[0] = 1;
        s.tossHits[1] = 1;
        s.tags[1] = 1;
        Awards.Award a = find(Awards.compute(s), "BEST TAYA");
        assertArrayEquals(new int[] { 1 }, a.winners);
        assertEquals("2 CATCHES", a.detail);
    }

    @Test
    void singularUnit() {
        MatchStats s = new MatchStats(2);
        s.slips[0] = 1;
        assertEquals("1 SLIP", find(Awards.compute(s), "MADULAS").detail);
    }
}
