import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.Random;

/** Generates simple synthesized placeholder sounds (16-bit mono WAV, 22050 Hz). Usage (from the repo root): java tools/GenPlaceholderSounds.java assets
 * Overwrites assets/audio/sfx and assets/audio/music with placeholder beeps. Replace them with real sounds of the same name. */
public class GenPlaceholderSounds {
    static final int RATE = 22050;
    static final Random RNG = new Random(42);

    public static void main(String[] args) throws IOException {
        Path sfx = Paths.get(args[0], "audio", "sfx");
        Path music = Paths.get(args[0], "audio", "music");
        Files.createDirectories(sfx);
        Files.createDirectories(music);

        write(sfx.resolve("ui_move.wav"), tone(880, 0.05, 0.5, Wave.SQUARE));
        write(sfx.resolve("ui_confirm.wav"), concat(tone(660, 0.06, 0.5, Wave.SQUARE), tone(990, 0.09, 0.5, Wave.SQUARE)));
        write(sfx.resolve("ui_back.wav"), concat(tone(660, 0.06, 0.5, Wave.SQUARE), tone(440, 0.09, 0.5, Wave.SQUARE)));
        write(sfx.resolve("ui_deny.wav"), tone(140, 0.18, 0.5, Wave.SQUARE));
        write(sfx.resolve("transition_roll.wav"), roll(1.4));
        write(sfx.resolve("game_start.wav"), arpeggio(new double[] { 523, 659, 784, 1047 }, 0.1, 0.25));
        write(sfx.resolve("game_end.wav"), whistle());
        write(sfx.resolve("victory_fanfare.wav"), concat(arpeggio(new double[] { 523, 659, 784 }, 0.12, 0.12),
            chord(new double[] { 523, 659, 784, 1047 }, 0.9)));
        write(sfx.resolve("throw.wav"), whoosh(0.22));
        write(sfx.resolve("can_hit.wav"), clang(0.5));
        write(sfx.resolve("tag.wav"), concat(thump(0.08), tone(1200, 0.08, 0.4, Wave.SQUARE)));
        write(sfx.resolve("score.wav"), concat(tone(1320, 0.05, 0.4, Wave.SINE), tone(1760, 0.12, 0.4, Wave.SINE)));
        write(sfx.resolve("trash_land.wav"), concat(thump(0.07), whoosh(0.12)));
        write(sfx.resolve("slip.wav"), concat(slide(0.35), tone(220, 0.12, 0.4, Wave.SQUARE)));
        write(sfx.resolve("dog_bark.wav"), concat(concat(woof(0.13), new double[(int) (0.08 * RATE)]), woof(0.16)));
        write(sfx.resolve("poop_squish.wav"), squish(0.3));

        // Music loops (8 bars of simple arpeggios)
        write(music.resolve("menu.wav"), loop(new double[][] { { 262, 330, 392 }, { 220, 262, 330 }, { 175, 220, 262 }, { 196, 247, 294 } }, 100, 0.79)); // about as loud as a real song
        write(music.resolve("game.wav"), loop(new double[][] { { 220, 262, 330 }, { 196, 247, 294 }, { 175, 220, 262 }, { 196, 247, 294 } }, 140, 0.79)); // about as loud as a real song
        write(music.resolve("victory.wav"), loop(new double[][] { { 262, 330, 392 }, { 349, 440, 523 }, { 392, 494, 587 }, { 262, 330, 392 } }, 110, 0.79)); // about as loud as a real song
    }

    enum Wave { SINE, SQUARE }

    static double[] tone(double freq, double seconds, double amp, Wave wave) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double s = Math.sin(2 * Math.PI * freq * t);
            if (wave == Wave.SQUARE) s = Math.signum(s) * 0.5;
            out[i] = s * amp * envelope(i, n);
        }
        return out;
    }

    /** Short attack, smooth release, so there are no clicks. */
    static double envelope(int i, int n) {
        int attack = Math.min(n / 10, (int) (0.005 * RATE));
        if (i < attack) return i / (double) attack;
        return Math.pow(1.0 - (i - attack) / (double) (n - attack), 1.5);
    }

    static double[] arpeggio(double[] freqs, double noteLen, double lastLen) {
        double[] out = new double[0];
        for (int k = 0; k < freqs.length; k++) {
            out = concat(out, tone(freqs[k], k == freqs.length - 1 ? lastLen : noteLen, 0.45, Wave.SQUARE));
        }
        return out;
    }

    static double[] chord(double[] freqs, double seconds) {
        double[] out = new double[(int) (seconds * RATE)];
        for (double f : freqs) {
            double[] t = tone(f, seconds, 0.22, Wave.SQUARE);
            for (int i = 0; i < out.length; i++) out[i] += t[i];
        }
        return out;
    }

    /** Rolling can: low filtered noise with a rhythmic clack, fading in and out. */
    static double[] roll(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += 0.08 * ((RNG.nextDouble() * 2 - 1) - lp);           // low-passed noise rumble
            double clack = Math.pow(Math.max(0, Math.sin(2 * Math.PI * 7 * t)), 20); // 7 bumps per second
            double ring = Math.sin(2 * Math.PI * 520 * t) * clack * 0.3;
            double env = Math.sin(Math.PI * t / seconds);              // in and out
            out[i] = (lp * 1.8 + ring) * env * 0.8;
        }
        return out;
    }

    static double[] whistle() {
        double[] a = vibrato(2100, 0.18);
        double[] gap = new double[(int) (0.06 * RATE)];
        double[] b = vibrato(2100, 0.45);
        return concat(concat(a, gap), b);
    }

    static double[] vibrato(double freq, double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double f = freq + 60 * Math.sin(2 * Math.PI * 30 * t);
            phase += 2 * Math.PI * f / RATE;
            double noise = (RNG.nextDouble() * 2 - 1) * 0.15;
            out[i] = (Math.sin(phase) + noise) * 0.35 * Math.min(1, Math.min(i, n - i) / (0.01 * RATE));
        }
        return out;
    }

    static double[] whoosh(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            double cutoff = 0.05 + 0.4 * p;                            // brightens as it flies off
            lp += cutoff * ((RNG.nextDouble() * 2 - 1) - lp);
            out[i] = lp * Math.sin(Math.PI * p) * 0.9;
        }
        return out;
    }

    /** Metallic can hit: a few inharmonic partials with fast decay plus a click. */
    static double[] clang(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double[] partials = { 410, 1023, 1689, 2540, 3320 };
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double s = 0;
            for (int k = 0; k < partials.length; k++) {
                s += Math.sin(2 * Math.PI * partials[k] * t) * Math.exp(-t * (8 + k * 6)) / (k + 1);
            }
            double click = i < 200 ? (RNG.nextDouble() * 2 - 1) * (1 - i / 200.0) : 0;
            out[i] = s * 0.6 + click * 0.5;
        }
        return out;
    }

    /** A short "woof": a falling growly tone with some noise. */
    static double[] woof(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            phase += 2 * Math.PI * (520 - 300 * p) / RATE;
            double growl = Math.signum(Math.sin(phase)) * 0.35 + Math.sin(phase * 2) * 0.25;
            out[i] = (growl + (RNG.nextDouble() * 2 - 1) * 0.2) * Math.sin(Math.PI * p) * 0.8;
        }
        return out;
    }

    /** Wet low squish: low-passed noise with a quick wobble. */
    static double[] squish(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            lp += 0.06 * ((RNG.nextDouble() * 2 - 1) - lp);
            double wobble = 1 + 0.5 * Math.sin(2 * Math.PI * 18 * p * seconds);
            out[i] = lp * 4.0 * wobble * Math.pow(1 - p, 1.5);
        }
        return out;
    }

    /** Falling "wheee" for slipping on trash. */
    static double[] slide(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            phase += 2 * Math.PI * (900 - 650 * p) / RATE;
            out[i] = Math.sin(phase) * 0.4 * (1 - p * 0.6);
        }
        return out;
    }

    static double[] thump(double seconds) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            phase += 2 * Math.PI * (160 - 100 * p) / RATE;
            out[i] = Math.sin(phase) * (1 - p) * 0.8;
        }
        return out;
    }

    /** Simple looping track: bass note + eighth-note arpeggio per chord, 2 bars per chord, played twice. */
    static double[] loop(double[][] chords, int bpm, double amp) {
        double beat = 60.0 / bpm;
        double[] out = new double[0];
        for (int rep = 0; rep < 1; rep++) {
            for (double[] chord : chords) {
                double[] bar = new double[(int) (beat * 4 * RATE)];
                double[] bass = tone(chord[0] / 2, beat * 4, amp * 0.9, Wave.SINE);
                for (int i = 0; i < bar.length && i < bass.length; i++) bar[i] += bass[i];
                for (int step = 0; step < 8; step++) {
                    double f = chord[step % chord.length] * (step % 8 < 4 ? 1 : 2);
                    double[] note = tone(f, beat / 2, amp * 0.5, Wave.SQUARE);
                    int start = (int) (step * beat / 2 * RATE);
                    for (int i = 0; i < note.length && start + i < bar.length; i++) bar[start + i] += note[i];
                }
                out = concat(out, bar);
            }
        }
        return out;
    }

    static double[] concat(double[] a, double[] b) {
        double[] out = new double[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static void write(Path path, double[] samples) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double s : samples) data.putShort((short) Math.round(Math.max(-1, Math.min(1, s)) * 32000));
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + data.capacity()).put("WAVE".getBytes())
            .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
            .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16)
            .put("data".getBytes()).putInt(data.capacity());
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(header.array());
            os.write(data.array());
        }
        System.out.printf("%-40s %6.2f s%n", path.getFileName(), samples.length / (double) RATE);
    }
}
