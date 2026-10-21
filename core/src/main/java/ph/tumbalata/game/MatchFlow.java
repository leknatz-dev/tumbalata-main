package ph.tumbalata.game;

import com.badlogic.gdx.math.Vector2;

/**
 * A whole match from the first line of dialogue to the end, in fixed steps: the intro dialogue, the mano that picks
 * the first Taya, Taya choosing the can spot, GAME START, the play itself, and GOOD JOB. No drawing or sound, so the
 * host can run it; the screen draws it and reacts through {@link Listener}.
 *
 * <p>Waits that match a pop-up sign ("NAME IS TAYA!", "GAME START!", "GOOD JOB!") last that sign's
 * {@link Signs.Sign#duration}, so the sign and the flow end together. A hit-stop ({@link #isFrozen()}) holds the
 * action still for a moment when the can is knocked or someone is caught.
 */
public final class MatchFlow {
    public enum Stage { DIALOGUE, MANO, MANO_RESULT, CAN_SPOT, INTRO, PLAYING, OUTRO, DONE }

    /** What happened, for the screen (signs, sounds). All optional. */
    public interface Listener {
        /** The flow moved on to this stage. */
        default void stageStarted(Stage stage) {}
        /** The mano: hands flipped (REVEAL), a new round after "again" (CHOOSING), or the Taya is final (DONE). */
        default void manoChanged(Mano.Phase phase) {}
        /** INTRO, once the screen is ready: "GAME START!" goes up now. */
        default void gameStartShown() {}
    }

    /** Seconds the action freezes when the can is knocked down. */
    public static final float KNOCK_FREEZE = 0.07f;
    /** A tag, or Taya's can landing on a slipper. */
    public static final float CATCH_FREEZE = 0.05f;

    private final Match match;
    private final Mano mano;
    private final Dialogue dialogue;
    private Listener listener = new Listener() {};
    private Match.Events matchEvents = new Match.Events() {};

    private Stage stage = Stage.DIALOGUE;
    private float stageTime = 0f;
    private boolean gameStartShown = false;
    private float freezeLeft = 0f;

    public MatchFlow(Match match, Mano mano, Dialogue dialogue) {
        this.match = match;
        this.mano = mano;
        this.dialogue = dialogue;
        match.setEvents(new FlowEvents());
        if (dialogue.isFinished()) stage = Stage.MANO; // no script for this many players: straight to the mano
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? new Listener() {} : listener;
    }

    /** The match's own events (score, throws, tags...), passed on after the flow has seen them. */
    public void setMatchEvents(Match.Events events) {
        this.matchEvents = events == null ? new Match.Events() {} : events;
    }

    public Stage stage() { return stage; }
    public Match match() { return match; }
    public Mano mano() { return mano; }
    public Dialogue dialogue() { return dialogue; }

    /** Seconds since the current stage started. */
    public float stageTime() { return stageTime; }

    /** True during a hit-stop: nothing moves (presses wait for it to end). */
    public boolean isFrozen() {
        return freezeLeft > 0f;
    }

    /** Before the mano has picked the Taya: nobody is Taya yet. */
    public boolean beforeTaya() {
        return stage == Stage.DIALOGUE || stage == Stage.MANO;
    }

    /**
     * One fixed step ({@link FixedStep#STEP}). Players' inputs are read from {@link Player#input}.
     * @param ready false while something keeps the players from seeing the court (the screen transition): the
     *              dialogue, the mano and GAME START wait for it
     */
    public void step(float delta, boolean ready) {
        if (freezeLeft > 0f) {
            freezeLeft -= delta;
            return;
        }
        stageTime += delta;
        switch (stage) {
            case DIALOGUE:
                if (!ready) break;
                dialogue.update(delta, anyonePressedNext(), anyoneHoldsSkip());
                if (dialogue.isFinished()) enter(Stage.MANO);
                break;
            case MANO:
                if (ready) stepMano(delta);
                break;
            case MANO_RESULT: // "NAME IS TAYA!"
                if (stageTime < Signs.Sign.TAYA_PICKED.duration) break;
                if (match.court().hasCanZone()) { // Taya chooses where the can stands for this match
                    match.beginCanPlacement();
                    enter(Stage.CAN_SPOT);
                } else {
                    enter(Stage.INTRO);
                }
                break;
            case CAN_SPOT:
                match.update(delta); // only Taya moves; canSpotChosen() moves on to INTRO
                break;
            case INTRO: // "GAME START!" once the players can see the court, then play
                if (!gameStartShown) {
                    if (!ready) break;
                    gameStartShown = true;
                    stageTime = 0f;
                    listener.gameStartShown();
                } else if (stageTime >= Signs.Sign.GAME_START.duration) {
                    enter(Stage.PLAYING);
                }
                break;
            case PLAYING:
                match.update(delta);
                if (match.isOver()) enter(Stage.OUTRO);
                break;
            case OUTRO: // "GOOD JOB!"
                if (stageTime >= Signs.Sign.GOOD_JOB.duration) enter(Stage.DONE);
                break;
            case DONE:
                break;
        }
    }

    private void stepMano(float delta) {
        for (int i = 0; i < match.players().size; i++) {
            PlayerInput in = match.players().get(i).input;
            if (in.aPressed) mano.choose(i, true);   // A = palm up
            if (in.bPressed) mano.choose(i, false);  // B = palm down (also the default)
        }
        Mano.Phase happened = mano.update(delta);
        if (happened == null) return;
        if (happened == Mano.Phase.DONE) match.chooseFirstTaya(match.players().get(mano.taya()).id);
        listener.manoChanged(happened);
        if (happened == Mano.Phase.DONE) enter(Stage.MANO_RESULT);
    }

    private void enter(Stage next) {
        stage = next;
        stageTime = 0f;
        listener.stageStarted(next);
    }

    private boolean anyonePressedNext() {
        for (int i = 0; i < match.players().size; i++) {
            PlayerInput in = match.players().get(i).input;
            if (in.aPressed || in.startPressed) return true;
        }
        return false;
    }

    private boolean anyoneHoldsSkip() {
        for (int i = 0; i < match.players().size; i++) if (match.players().get(i).input.b) return true;
        return false;
    }

    /** Sees the match's events first (hit-stops, the can spot), then passes every one on to the screen. */
    private final class FlowEvents implements Match.Events {
        @Override public void scored(Player player, int points) { matchEvents.scored(player, points); }
        @Override public void slipperThrown(Player thrower) { matchEvents.slipperThrown(thrower); }
        @Override public void canTossed(Player taya) { matchEvents.canTossed(taya); }
        @Override public void tayaTossTurn(Player taya) { matchEvents.tayaTossTurn(taya); }
        @Override public void tossMissed(Player taya) { matchEvents.tossMissed(taya); }
        @Override public void roundEnded() { matchEvents.roundEnded(); }
        @Override public void trashIncoming(Trash trash) { matchEvents.trashIncoming(trash); }
        @Override public void trashLanded(Trash trash) { matchEvents.trashLanded(trash); }
        @Override public void slipped(Player player, Trash trash) { matchEvents.slipped(player, trash); }
        @Override public void dogArrived(StrayDog dog) { matchEvents.dogArrived(dog); }
        @Override public void dogPooped(StrayDog dog) { matchEvents.dogPooped(dog); }
        @Override public void steppedInPoop(Player player) { matchEvents.steppedInPoop(player); }
        @Override public void throwerTurn(Player thrower) { matchEvents.throwerTurn(thrower); }

        @Override
        public void canKnocked(Player thrower) {
            freeze(KNOCK_FREEZE);
            matchEvents.canKnocked(thrower);
        }

        @Override
        public void tagged(Player taya, Player victim) {
            freeze(CATCH_FREEZE);
            matchEvents.tagged(taya, victim);
        }

        @Override
        public void tossHitSlipper(Player taya, Player victim) {
            freeze(CATCH_FREEZE);
            matchEvents.tossHitSlipper(taya, victim);
        }

        @Override
        public void canSpotChosen(Vector2 spot) {
            matchEvents.canSpotChosen(spot);
            enter(Stage.INTRO); // GAME START
        }
    }

    private void freeze(float seconds) {
        freezeLeft = Math.max(freezeLeft, seconds);
    }
}
