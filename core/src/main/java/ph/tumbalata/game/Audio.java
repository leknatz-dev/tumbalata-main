package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;

import java.util.EnumMap;

/**
 * ONE place for all sound effects and music. Created once by TumbalataGame; screens call
 * {@code game.audio().play(Audio.Sfx.UI_CONFIRM)} or {@code game.audio().playMusic(Audio.Track.MENU)}.
 *
 * <ul>
 *   <li>Files live in {@code assets/audio/sfx/} and {@code assets/audio/music/}, named after the enum entries below.
 *       Any of .ogg, .mp3 or .wav works: to replace a placeholder, drop in a file with the same name (and delete the
 *       old one if the extension differs). See docs/PRD.md "Audio" for the full list.</li>
 *   <li>A missing file is logged once and then silently skipped, so the game never crashes on audio.</li>
 *   <li>Music fades out and the next track fades in when it changes. Asking for the track that is already playing
 *       does nothing, so the menu music keeps going across the menu screens.</li>
 *   <li>Master, music and effects volume plus mute are saved in Preferences ("tumbalata").</li>
 * </ul>
 */
public final class Audio {
    private static final String SFX_DIR = "audio/sfx/";
    private static final String MUSIC_DIR = "audio/music/";
    private static final String[] EXTENSIONS = { ".ogg", ".mp3", ".wav" };
    private static final String PREFS_NAME = "tumbalata";

    private static final float MUSIC_FADE_SECONDS = 0.6f;

    /** Short sound effects. The volume is relative to the effects volume. */
    public enum Sfx {
        // UI
        UI_MOVE("ui_move", 0.6f),             // menu selection moves
        UI_CONFIRM("ui_confirm", 0.8f),       // button pressed / character locked in
        UI_BACK("ui_back", 0.7f),             // back / unlock
        UI_DENY("ui_deny", 0.7f),             // not allowed (character taken)
        TRANSITION_ROLL("transition_roll", 0.8f), // the rolling can sweeping across on a screen change
        // Match flow
        GAME_START("game_start", 0.9f),
        GAME_END("game_end", 0.9f),           // time up whistle
        VICTORY("victory_fanfare", 0.9f),     // when the podium appears
        // Gameplay
        THROW("throw", 0.7f),                 // slipper thrown / can tossed
        CAN_HIT("can_hit", 0.9f),             // slipper knocks the can, or Taya's can lands on a slipper
        TAG("tag", 0.9f),
        SCORE("score", 0.6f);

        final String file;
        final float volume;

        Sfx(String file, float volume) {
            this.file = file;
            this.volume = volume;
        }
    }

    /** Looping background music. */
    public enum Track {
        MENU("menu"),
        GAME("game"),
        VICTORY("victory");

        final String file;

        Track(String file) {
            this.file = file;
        }
    }

    private final EnumMap<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private final Preferences prefs;

    private float masterVolume;
    private float musicVolume;
    private float sfxVolume;
    private boolean muted;

    // Music: the playing track fades out, then the wanted one is loaded and fades in
    private Track currentTrack;
    private Music currentMusic;
    private Track wantedTrack;
    private float fade = 1f; // 0..1 volume multiplier of the current track

    public Audio() {
        prefs = Gdx.app.getPreferences(PREFS_NAME);
        masterVolume = prefs.getFloat("masterVolume", 1f);
        musicVolume = prefs.getFloat("musicVolume", 0.6f);
        sfxVolume = prefs.getFloat("sfxVolume", 0.9f);
        muted = prefs.getBoolean("muted", false);

        for (Sfx sfx : Sfx.values()) {
            FileHandle file = find(SFX_DIR, sfx.file);
            if (file == null) continue;
            try {
                sounds.put(sfx, Gdx.audio.newSound(file));
            } catch (Exception e) {
                Gdx.app.error("Audio", "Could not load " + file.path() + ": " + e.getMessage());
            }
        }
    }

    /** The first existing file for this name with any supported extension, or null (logged). */
    private static FileHandle find(String dir, String name) {
        for (String ext : EXTENSIONS) {
            FileHandle f = Gdx.files.internal(dir + name + ext);
            if (f.exists()) return f;
        }
        Gdx.app.error("Audio", "Missing " + dir + name + " (.ogg/.mp3/.wav), it will be silent");
        return null;
    }

    // ------------------------------------------------------------------
    // Sound effects
    // ------------------------------------------------------------------

    public void play(Sfx sfx) {
        play(sfx, 1f);
    }

    /** Plays with a pitch multiplier (1 = normal), e.g. a little random pitch so repeated sounds feel less robotic. */
    public void play(Sfx sfx, float pitch) {
        Sound sound = sounds.get(sfx);
        if (sound == null) return;
        float volume = effectiveSfxVolume() * sfx.volume;
        if (volume <= 0f) return;
        sound.play(volume, pitch, 0f);
    }

    /** Plays with a random pitch between 0.92 and 1.08. */
    public void playVaried(Sfx sfx) {
        play(sfx, MathUtils.random(0.92f, 1.08f));
    }

    // ------------------------------------------------------------------
    // Music
    // ------------------------------------------------------------------

    /** Switches to this track (fading), or keeps playing if it is already the current one. */
    public void playMusic(Track track) {
        wantedTrack = track;
    }

    /** Fades the music out and stays silent. */
    public void stopMusic() {
        wantedTrack = null;
    }

    /** Call once per frame (handles the music fades). */
    public void update(float delta) {
        if (wantedTrack != currentTrack) {
            if (currentMusic != null && fade > 0f) {
                fade = Math.max(0f, fade - delta / MUSIC_FADE_SECONDS); // fade the old track out first
            } else {
                switchTo(wantedTrack);
            }
        } else if (currentMusic != null && fade < 1f) {
            fade = Math.min(1f, fade + delta / MUSIC_FADE_SECONDS);
        }
        if (currentMusic != null) currentMusic.setVolume(effectiveMusicVolume() * fade);
    }

    private void switchTo(Track track) {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }
        currentTrack = track;
        fade = 0f;
        if (track == null) return;

        FileHandle file = find(MUSIC_DIR, track.file);
        if (file == null) return;
        try {
            currentMusic = Gdx.audio.newMusic(file);
            currentMusic.setLooping(true);
            currentMusic.setVolume(0f);
            currentMusic.play();
        } catch (Exception e) {
            Gdx.app.error("Audio", "Could not load " + file.path() + ": " + e.getMessage());
            currentMusic = null;
        }
    }

    // ------------------------------------------------------------------
    // Volume (0..1), saved between sessions
    // ------------------------------------------------------------------

    public float getMasterVolume() { return masterVolume; }
    public float getMusicVolume() { return musicVolume; }
    public float getSfxVolume() { return sfxVolume; }
    public boolean isMuted() { return muted; }

    public void setMasterVolume(float v) { masterVolume = MathUtils.clamp(v, 0f, 1f); save(); }
    public void setMusicVolume(float v) { musicVolume = MathUtils.clamp(v, 0f, 1f); save(); }
    public void setSfxVolume(float v) { sfxVolume = MathUtils.clamp(v, 0f, 1f); save(); }
    public void setMuted(boolean m) { muted = m; save(); }
    public void toggleMute() { setMuted(!muted); }

    private float effectiveSfxVolume() {
        return muted ? 0f : masterVolume * sfxVolume;
    }

    private float effectiveMusicVolume() {
        return muted ? 0f : masterVolume * musicVolume;
    }

    private void save() {
        prefs.putFloat("masterVolume", masterVolume);
        prefs.putFloat("musicVolume", musicVolume);
        prefs.putFloat("sfxVolume", sfxVolume);
        prefs.putBoolean("muted", muted);
        prefs.flush();
    }

    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
        sounds.clear();
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }
    }
}
