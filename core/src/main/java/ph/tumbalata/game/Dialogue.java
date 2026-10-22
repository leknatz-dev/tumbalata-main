package ph.tumbalata.game;

import com.badlogic.gdx.utils.Array;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The intro dialogue as it plays: which line is up, how much of it has typed out, and holding B to skip. No drawing
 * ({@link DialogueBox} draws it), so the host can run it with the rest of the match.
 *
 * <p>A new speaker comes in before their text types: when they talk from the other side the box first slides over
 * ({@link #SLIDE_SECONDS}), then they rise up from behind it ({@link #RISE_SECONDS}). The same speaker again just
 * types. Pressing next while a line types finishes it; pressing again goes to the next line.
 */
public final class Dialogue {
    public static final float CHARS_PER_SECOND = 40f;
    public static final float SKIP_HOLD_SECONDS = 0.8f;
    public static final float SLIDE_SECONDS = 0.28f;
    public static final float RISE_SECONDS = 0.22f;
    /** Share of the full width the box takes when one kid talks (everyone at once: the full width). */
    public static final float HALF = 0.5f;

    private final Array<DialogueScript.Line> lines;
    private int index = 0;
    private float shown = 0f;    // characters of the current line typed so far
    private float skipHeld = 0f;
    private float lineTime = 0f; // seconds since the current line came up
    private float time = 0f;
    private boolean slides, rises; // how the current line's speaker comes in
    private boolean finished;

    public Dialogue(Array<DialogueScript.Line> lines) {
        this.lines = lines;
        this.finished = lines.size == 0;
        this.slides = false; // the box starts on the first speaker's side
        this.rises = true;
    }

    public boolean isFinished() { return finished; }
    public int index() { return index; }
    public float time() { return time; }
    public float lineTime() { return lineTime; }

    /** The line up now (null once finished or when there are no lines). */
    public DialogueScript.Line line() {
        return finished ? null : lines.get(index);
    }

    /** The line before this one (where the box slides from), or null for the first. */
    public DialogueScript.Line previousLine() {
        return index > 0 && !finished ? lines.get(index - 1) : null;
    }

    /** How many characters of the current line show. */
    public int shownChars() {
        return (int) shown;
    }

    public boolean isLineDone() {
        return !finished && shown >= lines.get(index).text.length();
    }

    /** 0..1: how far the skip button has been held. */
    public float skipProgress() {
        return Math.min(1f, skipHeld / SKIP_HOLD_SECONDS);
    }

    /** 0..1: the box sliding over to this speaker's side (1 when it doesn't move). */
    public float slideProgress() {
        return slides ? Math.min(1f, lineTime / SLIDE_SECONDS) : 1f;
    }

    /** 0..1: the speaker rising from behind the box, after the slide (1 when it's the same speaker). */
    public float riseProgress() {
        if (!rises) return 1f;
        return Math.max(0f, Math.min(1f, (lineTime - slideSeconds()) / RISE_SECONDS));
    }

    private float slideSeconds() {
        return slides ? SLIDE_SECONDS : 0f;
    }

    /** Seconds after a line comes up before it starts typing. */
    private float entranceSeconds() {
        return slideSeconds() + (rises ? RISE_SECONDS : 0f);
    }

    /**
     * One step.
     * @param next     next pressed (anyone's A)
     * @param skipDown skip held (anyone's B); held {@link #SKIP_HOLD_SECONDS} skips the whole dialogue
     */
    public void update(float delta, boolean next, boolean skipDown) {
        if (finished) return;
        time += delta;
        lineTime += delta;
        skipHeld = skipDown ? skipHeld + delta : 0f;
        if (skipHeld >= SKIP_HOLD_SECONDS) {
            finished = true;
            return;
        }
        DialogueScript.Line line = lines.get(index);
        int length = line.text.length();
        if (shown < length && lineTime >= entranceSeconds()) shown = Math.min(length, shown + delta * CHARS_PER_SECOND);
        if (next) {
            if (shown < length) {
                shown = length; // first press finishes the line
            } else if (++index >= lines.size) {
                finished = true;
            } else {
                DialogueScript.Line now = lines.get(index);
                shown = 0f;
                lineTime = 0f;
                rises = now.speaker != line.speaker;
                slides = boxLeft(now) != boxLeft(line) || boxRight(now) != boxRight(line);
            }
        }
    }

    /** P1 / P3 talk from the left, P2 / P4 from the right. */
    public static boolean isLeft(int speaker) {
        return speaker % 2 == 0;
    }

    /** The box's left edge for this line, as a share of the full width (0 = far left). */
    public static float boxLeft(DialogueScript.Line line) {
        return line.speaker < 0 || isLeft(line.speaker) ? 0f : 1f - HALF;
    }

    /** The box's right edge for this line (1 = far right). */
    public static float boxRight(DialogueScript.Line line) {
        return line.speaker < 0 || !isLeft(line.speaker) ? 1f : HALF;
    }

    // Network copy (the host sends this state; a phone draws its copy). The lines themselves travel once, at the start.
    void writeState(DataOutputStream out) throws IOException {
        out.writeByte(index);
        out.writeFloat(shown);
        out.writeFloat(skipHeld);
        out.writeFloat(lineTime);
        out.writeFloat(time);
        out.writeBoolean(slides);
        out.writeBoolean(rises);
        out.writeBoolean(finished);
    }

    void readState(DataInputStream in) throws IOException {
        index = in.readByte();
        shown = in.readFloat();
        skipHeld = in.readFloat();
        lineTime = in.readFloat();
        time = in.readFloat();
        slides = in.readBoolean();
        rises = in.readBoolean();
        finished = in.readBoolean();
    }
}
