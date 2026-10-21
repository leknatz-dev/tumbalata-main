package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LobbyTest {
    private static Lobby lobby(int players) {
        Lobby lobby = new Lobby();
        lobby.reset(players);
        return lobby;
    }

    @Test
    void namesAreTidiedAndBlankOnesGetTheDefault() {
        Lobby lobby = lobby(3);
        lobby.setName(0, "  maya ");
        lobby.setName(1, "averyveryverylongname");
        assertArrayEquals(new String[] { "MAYA", "AVERYVERYV", "P3" }, lobby.names());
        assertEquals("", lobby.slot(2).typedName());
    }

    @Test
    void everyoneStartsOnADifferentCharacter() {
        assertArrayEquals(new int[] { 0, 1, 2, 3 }, lobby(4).characters());
    }

    @Test
    void aCharacterCanOnlyBeLockedInOnce() {
        Lobby lobby = lobby(2);
        lobby.choose(0, 2);
        lobby.choose(1, 2);
        assertEquals(-1, lobby.lock(0));
        assertEquals(0, lobby.lock(1), "taken by P1");
        assertFalse(lobby.slot(1).isLocked());
        lobby.choose(1, 3);
        assertEquals(-1, lobby.lock(1));
        assertTrue(lobby.allLocked());
    }

    @Test
    void aLockedPlayerCantMoveUntilTheyUnlock() {
        Lobby lobby = lobby(2);
        lobby.lock(0);
        lobby.choose(0, 3);
        assertEquals(0, lobby.slot(0).character());
        lobby.unlock(0);
        lobby.choose(0, -1); // wraps around the cards
        assertEquals(Characters.COUNT - 1, lobby.slot(0).character());
    }

    @Test
    void backFromAMatchEveryonePicksAgainButKeepsTheirNames() {
        Lobby lobby = lobby(2);
        lobby.setName(0, "Maya");
        lobby.lock(0);
        lobby.lock(1);
        lobby.unlockAll();
        assertFalse(lobby.allLocked());
        assertEquals("MAYA", lobby.slot(0).name());
        assertEquals(0, lobby.slot(0).character(), "starts on the character they had");
    }

    @Test
    void playersJoinUntilTheRoomIsFull() {
        Lobby lobby = new Lobby();
        lobby.settings.roomSize = 3;
        assertTrue(lobby.join(0).isHost(), "the first one in is the host");
        assertEquals(null, lobby.join(0), "that input already plays");
        lobby.join(2);
        assertFalse(lobby.isFull());
        lobby.join(1);
        assertTrue(lobby.isFull());
        assertEquals(null, lobby.join(3), "full");
        assertEquals(2, lobby.slotFor(1).id(), "numbered in join order");
    }

    @Test
    void whenSomeoneLeavesTheOthersMoveUp() {
        Lobby lobby = new Lobby();
        lobby.join(0);
        lobby.join(1);
        lobby.join(2);
        lobby.setName(2, "kim");
        lobby.leave(1);
        assertEquals(2, lobby.size());
        assertEquals(1, lobby.slotFor(2).id());
        assertArrayEquals(new String[] { "P1", "KIM" }, lobby.names());
    }

    @Test
    void theRoomSizeStaysBetweenTwoAndFourAndFitsEveryoneIn() {
        Lobby lobby = new Lobby();
        lobby.join(0);
        lobby.join(1);
        lobby.join(2);
        assertEquals(3, lobby.setRoomSize(2), "can't drop below the 3 in the room");
        assertEquals(4, lobby.setRoomSize(9));
        lobby.leave(2);
        lobby.leave(1);
        assertEquals(2, lobby.setRoomSize(1));
    }

    @Test
    void matchTimeStepsThroughTheChoicesAndStopsAtTheEnds() {
        Lobby.Settings s = new Lobby.Settings();
        assertEquals(360f, s.matchSeconds);
        s.stepMatchTime(1);
        assertEquals(480f, s.matchSeconds);
        s.matchSeconds = 60f;
        s.stepMatchTime(-1);
        assertEquals(60f, s.matchSeconds, "already the shortest");
    }

    @Test
    void theRoomCanBeSentWhileGoingThroughThePlayers() throws Exception {
        // The host sends each phone the room inside a loop over the players: that must not break the loop
        Lobby lobby = lobby(3);
        int sent = 0;
        for (Lobby.Slot s : lobby.slots()) {
            lobby.writeTo(new java.io.DataOutputStream(new java.io.ByteArrayOutputStream()));
            lobby.lockedBy(s.character());
            sent++;
        }
        assertEquals(3, sent);
    }

    @Test
    void resetKeepsTheSettingsAndChecksTheCount() {
        Lobby lobby = lobby(2);
        lobby.settings.matchSeconds = 120f;
        lobby.reset(4);
        assertEquals(4, lobby.size());
        assertEquals(120f, lobby.settings.matchSeconds);
        assertThrows(IllegalArgumentException.class, () -> lobby.reset(1));
        assertThrows(IllegalArgumentException.class, () -> lobby.reset(5));
    }
}
