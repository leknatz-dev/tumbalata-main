package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VictoryRankingTest {

    @Test
    void highestScoreFirstAndTiesKeepPlayerOrder() {
        assertArrayEquals(new int[] { 2, 0, 3, 1 }, VictoryScreen.orderByScore(new int[] { 5, 1, 9, 5 }));
    }

    @Test
    void tiedPlayersSharePlaces() {
        int[] scores = { 5, 1, 9, 5 };
        int[] order = VictoryScreen.orderByScore(scores);
        assertArrayEquals(new int[] { 0, 1, 1, 3 }, VictoryScreen.places(scores, order), "1st, 2nd, 2nd, 4th");
    }

    @Test
    void singleWinnerBanner() {
        int[] scores = { 3, 7 };
        int[] order = VictoryScreen.orderByScore(scores);
        assertEquals("PLAYER 2 WINS!", VictoryScreen.winnerText(order, VictoryScreen.places(scores, order)));
    }

    @Test
    void sharedWinnerBanner() {
        int[] scores = { 6, 2, 6 };
        int[] order = VictoryScreen.orderByScore(scores);
        assertEquals("P1 & P3 WIN!", VictoryScreen.winnerText(order, VictoryScreen.places(scores, order)));
    }

    @Test
    void winnerTextUsesPlayerNames() {
        int[] scores = { 5, 9, 9 };
        int[] order = VictoryScreen.orderByScore(scores);
        int[] places = VictoryScreen.places(scores, order);
        assertEquals("JOJO & KIM WIN!", VictoryScreen.winnerText(order, places, new String[] { "MAYA", "JOJO", "KIM" }));
        int[] solo = { 1, 0 };
        int[] o2 = VictoryScreen.orderByScore(solo);
        assertEquals("MAYA WINS!", VictoryScreen.winnerText(o2, VictoryScreen.places(solo, o2), new String[] { "MAYA", "JOJO" }));
    }
}
