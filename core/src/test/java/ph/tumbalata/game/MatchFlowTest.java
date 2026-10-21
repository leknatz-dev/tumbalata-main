package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** A whole match flow with no screen, the way a host would run it. */
class MatchFlowTest {
    private static final float DT = FixedStep.STEP;
    private static final float[] CAN_ZONE = { 767.333f, 370f, 769.333f, -42f, 1000.667f, -42.667f, 999.333f, 372f };

    private Array<Player> players;
    private Match match;
    private MatchFlow flow;
    private final List<MatchFlow.Stage> stages = new ArrayList<>();
    private int gameStarts = 0;

    private MatchFlow newFlow(int playerCount, float matchSeconds, Array<DialogueScript.Line> lines) {
        players = new Array<>();
        for (int id = 0; id < playerCount; id++) {
            Player p = new Player(id, id, 0f, 0f, GameConstants.PLAYER_SPEED, new PlayerInput(),
                Match.PLAY_MIN_X, Match.PLAY_MAX_X, Match.PLAY_MIN_Y, Match.PLAY_MAX_Y);
            p.slipper = new Slipper(0f, 0f);
            players.add(p);
        }
        MapCollision noWalls = new MapCollision(new Array<>());
        match = new Match(players, new Can(0f, 0f), noWalls, noWalls, matchSeconds);
        flow = new MatchFlow(match, new Mano(playerCount, new Random(1)), new Dialogue(lines));
        flow.setListener(new MatchFlow.Listener() {
            @Override public void stageStarted(MatchFlow.Stage stage) { stages.add(stage); }
            @Override public void gameStartShown() { gameStarts++; }
        });
        return flow;
    }

    /** Steps like the screen does: presses are used up by each step. */
    private void step(boolean ready) {
        flow.step(DT, ready);
        for (Player p : players) p.input.clearPresses();
    }

    private void stepFor(float seconds, boolean ready) {
        for (int i = 0; i < Math.round(seconds / DT); i++) step(ready);
    }

    private void stepUntil(MatchFlow.Stage stage, float maxSeconds) {
        for (float t = 0; t < maxSeconds && flow.stage() != stage; t += DT) step(true);
        assertSame(stage, flow.stage(), "reached " + stage);
    }

    private static Array<DialogueScript.Line> lines(String script, int players) {
        String[] names = { "A", "B", "C", "D" };
        return DialogueScript.parse(script).pick(players, java.util.Arrays.copyOf(names, players), new Random(1));
    }

    @Test
    void runsAWholeMatchFromTheManoToTheEnd() {
        newFlow(2, 5f, new Array<>());
        assertSame(MatchFlow.Stage.MANO, flow.stage(), "no dialogue: straight to the mano");
        assertTrue(flow.beforeTaya());

        players.get(1).input.aPressed = true; // P2 palm up, P1 palm down: P2 is Taya
        step(true);
        stepUntil(MatchFlow.Stage.MANO_RESULT, Mano.CHOOSE_SECONDS + Mano.REVEAL_SECONDS + 0.5f);
        assertSame(players.get(1), match.taya());
        assertFalse(flow.beforeTaya());

        stepUntil(MatchFlow.Stage.INTRO, Signs.Sign.TAYA_PICKED.duration + 0.1f);
        stepUntil(MatchFlow.Stage.PLAYING, Signs.Sign.GAME_START.duration + 0.1f);
        assertEquals(1, gameStarts);

        stepUntil(MatchFlow.Stage.OUTRO, 5.1f);
        assertTrue(match.isOver());
        stepUntil(MatchFlow.Stage.DONE, Signs.Sign.GOOD_JOB.duration + 0.1f);

        assertEquals(java.util.Arrays.asList(MatchFlow.Stage.MANO_RESULT, MatchFlow.Stage.INTRO, MatchFlow.Stage.PLAYING,
            MatchFlow.Stage.OUTRO, MatchFlow.Stage.DONE), stages);
    }

    @Test
    void theNameIsTayaSignGetsItsFullTime() {
        newFlow(2, 60f, new Array<>());
        players.get(0).input.aPressed = true;
        step(true);
        stepUntil(MatchFlow.Stage.MANO_RESULT, 6f);
        stepFor(Signs.Sign.TAYA_PICKED.duration - 0.1f, true);
        assertSame(MatchFlow.Stage.MANO_RESULT, flow.stage(), "still showing NAME IS TAYA!");
    }

    @Test
    void theDialogueComesFirstAndCanBeSkipped() {
        newFlow(2, 60f, lines("[2]\nP1: Uy!\nP2: Sige!\nALL: Maiba taya!", 2));
        assertSame(MatchFlow.Stage.DIALOGUE, flow.stage());
        players.get(0).input.b = true; // hold B
        stepFor(Dialogue.SKIP_HOLD_SECONDS + 0.05f, true);
        assertSame(MatchFlow.Stage.MANO, flow.stage());
    }

    @Test
    void anyonesAGoesThroughTheDialogue() {
        newFlow(3, 60f, lines("[3]\nP1: Uy!\nP3: Sige!", 3));
        for (int press = 0; press < 4 && flow.stage() == MatchFlow.Stage.DIALOGUE; press++) {
            stepFor(1f, true);
            players.get(2).input.aPressed = true; // P3 presses: finishes the line, then the next
            step(true);
        }
        assertSame(MatchFlow.Stage.MANO, flow.stage());
    }

    @Test
    void nothingStartsWhileTheScreenIsNotReady() {
        newFlow(2, 60f, lines("[2]\nP1: Uy!", 2));
        players.get(0).input.b = true;
        stepFor(3f, false); // the screen transition is still playing
        assertSame(MatchFlow.Stage.DIALOGUE, flow.stage(), "the skip waits too");
        players.get(0).input.b = false;

        newFlow(2, 60f, new Array<>());
        stepFor(10f, false);
        assertSame(MatchFlow.Stage.MANO, flow.stage(), "the mano waits");
        assertEquals(Mano.CHOOSE_SECONDS, flow.mano().secondsLeft(), 0.001f);
    }

    @Test
    void gameStartWaitsForTheScreen() {
        newFlow(2, 60f, new Array<>());
        players.get(0).input.aPressed = true;
        step(true);
        stepUntil(MatchFlow.Stage.INTRO, 10f);
        stepFor(5f, false);
        assertEquals(0, gameStarts, "no GAME START behind the transition");
        assertSame(MatchFlow.Stage.INTRO, flow.stage());
        stepUntil(MatchFlow.Stage.PLAYING, Signs.Sign.GAME_START.duration + 0.1f);
        assertEquals(1, gameStarts);
    }

    @Test
    void withACanZoneTayaChoosesTheSpotBeforeGameStart() {
        newFlow(2, 60f, new Array<>());
        match.setCourt(CourtLine.straight(Match.THROW_LINE_X).withCanZone(CAN_ZONE));
        players.get(0).input.aPressed = true; // P1 is Taya
        step(true);
        stepUntil(MatchFlow.Stage.CAN_SPOT, 10f);

        Player taya = match.taya();
        taya.position.set(900f, 100f - Match.FEET_OFFSET_Y); // feet inside the zone
        taya.input.bPressed = true;
        step(true);
        assertSame(MatchFlow.Stage.INTRO, flow.stage());
        assertEquals(new Vector2(900f, 100f), match.canBase());
    }

    /** A scripted player: runs about, presses A and B now and then (different per player). */
    private static InputFrame scripted(int player, int step, InputFrame out) {
        float t = step * DT;
        int buttons = 0;
        if ((step + player * 17) % 90 < 6) buttons |= InputFrame.A;
        if ((step + player * 29) % 140 < 5) buttons |= InputFrame.B;
        return out.set((float) Math.cos(t * (1.3 + player)), (float) Math.sin(t * (0.9 + player * 0.4)), buttons);
    }

    /** Plays a whole flow from the same input frames; returns where everything ended up. */
    private String replay(int steps) {
        newFlow(3, 20f, lines("[3]\nP1: Uy!\nP2: Sige!\nP3: Tara!", 3));
        match.setStreetEvents(true, 42L);
        InputFrame f = new InputFrame();
        for (int s = 0; s < steps; s++) {
            if (!flow.isFrozen()) for (int p = 0; p < players.size; p++) players.get(p).input.apply(scripted(p, s, f));
            flow.step(DT, true);
        }
        StringBuilder state = new StringBuilder(flow.stage() + " taya=" + match.taya().id + " t=" + match.timeLeft());
        for (Player p : players) state.append(" | ").append(p.position).append(' ').append(p.slipper.position).append(' ').append(p.score);
        return state.append(" can=").append(match.can().position).toString();
    }

    @Test
    void theSameInputFramesPlayTheSameMatch() {
        // What lets a host and the phones agree: the match only depends on the frames it is given
        String first = replay(60 * 25), second = replay(60 * 25);
        assertEquals(first, second);
        assertFalse(first.startsWith("DIALOGUE") || first.startsWith("MANO"), "got into the match: " + first);
    }

    @Test
    void passesOnEveryMatchEvent() throws Exception {
        // MatchFlow sees the match's events first; it must pass every one of them on to the screen
        Class<?> flowEvents = Class.forName(MatchFlow.class.getName() + "$FlowEvents");
        for (Method m : Match.Events.class.getMethods()) {
            Method own = flowEvents.getMethod(m.getName(), m.getParameterTypes());
            assertSame(flowEvents, own.getDeclaringClass(), "MatchFlow passes on " + m.getName());
        }
    }
}
