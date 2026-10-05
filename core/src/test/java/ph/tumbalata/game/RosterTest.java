package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RosterTest {

    @Test
    void twoPlayersMatchTheOriginalGame() {
        Roster r = new Roster(2);
        assertEquals(0, r.activeThrower(), "P1 throws");
        assertEquals(1, r.taya(), "P2 is Taya");

        r.nextTurn();
        assertEquals(0, r.activeThrower(), "a single Thrower keeps the turn");

        r.swapWithTaya(0);
        assertEquals(1, r.activeThrower());
        assertEquals(0, r.taya());
    }

    @Test
    void lastPlayerStartsAsTayaAndOthersThrowInOrder() {
        Roster r = new Roster(4);
        assertEquals(3, r.taya());
        assertArrayEquals(new int[] { 0, 1, 2 }, r.throwers().toArray());
    }

    @Test
    void turnRotatesThroughAllThrowers() {
        Roster r = new Roster(4);
        int[] seen = new int[4];
        for (int i = 0; i < 4; i++) {
            seen[i] = r.activeThrower();
            r.nextTurn();
        }
        assertArrayEquals(new int[] { 0, 1, 2, 0 }, seen);
    }

    @Test
    void swappedTayaTakesTheThrowersPlaceAndThrowsNext() {
        Roster r = new Roster(4);
        r.nextTurn();                        // P2's turn: order 1, 2, 0
        r.swapWithTaya(r.activeThrower());   // P2 tagged
        assertEquals(1, r.taya());
        assertArrayEquals(new int[] { 3, 2, 0 }, r.throwers().toArray(), "old Taya (P4) is up next");
    }

    @Test
    void everyPlayerIsExactlyOneRole() {
        Roster r = new Roster(3);
        r.swapWithTaya(1);
        r.nextTurn();
        r.swapWithTaya(r.activeThrower());
        boolean[] seen = new boolean[3];
        seen[r.taya()] = true;
        for (int i = 0; i < r.throwers().size; i++) {
            int id = r.throwers().get(i);
            assertEquals(false, seen[id], "player " + id + " listed twice");
            seen[id] = true;
        }
        assertArrayEquals(new boolean[] { true, true, true }, seen);
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new Roster(1));
        Roster r = new Roster(3);
        assertThrows(IllegalArgumentException.class, () -> r.swapWithTaya(r.taya()));
    }
}
