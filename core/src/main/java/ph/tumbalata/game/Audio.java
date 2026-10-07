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
        SCORE("score", 0.6f),
        // Street events
        TRASH_LAND("trash_land", 0.7f),       // trash lands on the court
        SLIP("slip", 0.8f),                   // a player slips on trash
        DOG_BARK("dog_bark", 0.8f),           // a stray dog trots onto the court
        POOP_SQUISH("poop_squish", 0.9f),     // a player steps in the dog's poop
        // Pop-up signs (each sign plays its own; GAME START! uses GAME_START and GOOD JOB! uses GAME_END)
        SIGN_RUN("sign_run", 0.8f),
        SIGN_HAHA("sign_haha", 0.8f),
        SIGN_MY_TURN("sign_my_turn", 0.8f),
        SIGN_GOTCHA("sign_gotcha", 0.8f),
        SIGN_THROW_TURN("sign_throw_turn", 0.7f),
        SIGN_TAYA_PICKED("sign_taya_picked", 0.9f),
        SIGN_CHOOSE_SPOT("sign_choose_spot", 0.8f),
        SIGN_TAGGED("sign_tagged", 0.8f),
        SIGN_STREAK("sign_streak", 0.8f);

        final String file;
        final float volume;

        Sfx(String file, float volume) {
            this.file = file;
            this.volume = volume;
        }
    }

    /** Looping music. The volume (0..1) balances the tracks against each other, e.g. when a song is mastered louder. */
    public enum Track {
        MENU("menu", 1f),
        GAME("game", 1f),
        VICTORY("victory", 1f);

        final String file;
        final float volume;

        Track(String file, float volume) {
            this.file = file;
            this.volume = volume;
        }
    }

    // Default volumes (0..1). Saved values (Preferences) win once a settings screen changes them.
    private static final float DEFAULT_MASTER = 1f;
    private static final float DEFAULT_MUSIC = 0.15f;
    private static final float DEFAULT_SFX = 0.9f;
    /** The background layer plays this much quieter than the main music. */
    private static final float BACKGROUND_LEVEL = 0.2f;

    // Preference keys. The volume keys were renamed so that volumes saved by older builds (which wrote them whenever
    // M was pressed) no longer override the defaults above.
    private static final String KEY_MASTER = "volume.master";
    private static final String KEY_MUSIC = "volume.music";
    private static final String KEY_SFX = "volume.sfx";
    private static final String KEY_MUTED = "muted";

    private final EnumMap<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private final Preferences prefs;

    private float masterVolume;
    private float musicVolume;
    private float sfxVolume;
    private boolean muted;

    // Two music layers: the main track, and an optional quieter one underneath (e.g. the menu song during a match)
    private final MusicChannel main = new MusicChannel(1f);
    private final MusicChannel background = new MusicChannel(BACKGROUND_LEVEL);

    public Audio() {
        prefs = Gdx.app.getPreferences(PREFS_NAME);
        masterVolume = prefs.getFloat(KEY_MASTER, DEFAULT_MASTER);
        musicVolume = prefs.getFloat(KEY_MUSIC, DEFAULT_MUSIC);
        sfxVolume = prefs.getFloat(KEY_SFX, DEFAULT_SFX);
        muted = prefs.getBoolean(KEY_MUTED, false);

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

    /** Switches the main music to this track (fading), or keeps playing if it is already the current one. */
    public void playMusic(Track track) {
        main.wanted = track;
    }

    /** Plays a track quietly underneath the main music (null = none). */
    public void playBackgroundMusic(Track track) {
        background.wanted = track;
    }

    /** Fades all music out and stays silent. */
    public void stopMusic() {
        main.wanted = null;
        background.wanted = null;
    }

    /** Call once per frame (handles the music fades). */
    public void update(float delta) {
        main.update(delta);
        background.update(delta);
    }

    /** One music layer: the playing track fades out, then the wanted one is loaded and fades in. */
    private final class MusicChannel {
        final float level;
        Track current, wanted;
        Music music;
        float fade = 1f; // 0..1 volume multiplier of the current track

        MusicChannel(float level) {
            this.level = level;
        }

        void update(float delta) {
            if (wanted != current) {
                if (music != null && fade > 0f) {
                    fade = Math.max(0f, fade - delta / MUSIC_FADE_SECONDS); // fade the old track out first
                } else {
                    switchTo(wanted);
                }
            } else if (music != null && fade < 1f) {
                fade = Math.min(1f, fade + delta / MUSIC_FADE_SECONDS);
            }
            if (music != null) music.setVolume(effectiveMusicVolume() * current.volume * level * fade);
        }

        private void switchTo(Track track) {
            release();
            current = track;
            fade = 0f;
            if (track == null) return;

            FileHandle file = find(MUSIC_DIR, track.file);
            if (file == null) return;
            try {
                music = Gdx.audio.newMusic(file);
                music.setLooping(true);
                music.setVolume(0f);
                music.play();
            } catch (Exception e) {
                Gdx.app.error("Audio", "Could not load " + file.path() + ": " + e.getMessage());
                music = null;
            }
        }

        void release() {
            if (music == null) return;
            music.stop();
            music.dispose();
            music = null;
        }
    }

    // ------------------------------------------------------------------
    // Volume (0..1), saved between sessions
    // ------------------------------------------------------------------

    public float getMasterVolume() { return masterVolume; }
    public float getMusicVolume() { return musicVolume; }
    public float getSfxVolume() { return sfxVolume; }
    public boolean isMuted() { return muted; }

    public void setMasterVolume(float v) { masterVolume = MathUtils.clamp(v, 0f, 1f); saveVolumes(); }
    public void setMusicVolume(float v) { musicVolume = MathUtils.clamp(v, 0f, 1f); saveVolumes(); }
    public void setSfxVolume(float v) { sfxVolume = MathUtils.clamp(v, 0f, 1f); saveVolumes(); }

    /** Mute on/off is saved on its own, so muting never freezes the current volumes into the saved settings. */
    public void setMuted(boolean m) {
        muted = m;
        prefs.putBoolean(KEY_MUTED, muted);
        prefs.flush();
    }

    public void toggleMute() { setMuted(!muted); }

    private float effectiveSfxVolume() {
        return muted ? 0f : masterVolume * sfxVolume;
    }

    private float effectiveMusicVolume() {
        return muted ? 0f : masterVolume * musicVolume;
    }

    private void saveVolumes() {
        prefs.putFloat(KEY_MASTER, masterVolume);
        prefs.putFloat(KEY_MUSIC, musicVolume);
        prefs.putFloat(KEY_SFX, sfxVolume);
        prefs.flush();
    }

    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
        sounds.clear();
        main.release();
        background.release();
    }
}
