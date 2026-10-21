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
