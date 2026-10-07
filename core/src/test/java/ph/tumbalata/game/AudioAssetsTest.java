package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

/** Every sound and music track the code asks for has a file in assets/audio (any supported extension). */
class AudioAssetsTest {
    private static final File ASSETS = new File("../assets");
    private static final String[] EXTENSIONS = { ".ogg", ".mp3", ".wav" };

    private static boolean exists(String dir, String name) {
        for (String ext : EXTENSIONS) {
            if (new File(ASSETS, dir + name + ext).isFile()) return true;
        }
        return false;
    }

    @Test
    void everySoundEffectHasAFile() {
        for (Audio.Sfx sfx : Audio.Sfx.values()) {
            assertTrue(exists("audio/sfx/", sfx.file), "missing assets/audio/sfx/" + sfx.file + " (.ogg/.mp3/.wav)");
        }
    }

    @Test
    void everyMusicTrackHasAFile() {
        for (Audio.Track track : Audio.Track.values()) {
            assertTrue(exists("audio/music/", track.file), "missing assets/audio/music/" + track.file + " (.ogg/.mp3/.wav)");
        }
    }
}
