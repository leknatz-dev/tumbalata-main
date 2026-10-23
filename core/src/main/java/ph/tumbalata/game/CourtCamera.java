package ph.tumbalata.game;

/**
 * Where the match screen looks: the whole court ({@link #wide()}), or close up on this phone's player and the can
 * ({@link #follow}). Close up it zooms in as far as {@link #CLOSEST} while the two are near each other, and out (up to
 * {@link #WIDEST_FOLLOW}) to keep both in view; Taya always sees more ({@link #TAYA_CLOSEST}). Past that the can goes
 * off-screen (the screen points to it) but the player never does. It eases between views, and never shows past the
 * edges of the map. No drawing: the screen sets its camera from {@link #x}, {@link #y} and {@link #zoom} (1 = the whole
 * court, 0.5 = twice as close).
 */
public final class CourtCamera {
    /** As close as it gets (1.8x the whole-court view). */
    static final float CLOSEST = 0.55f;
    /** As wide as it gets while following (still closer than the whole court). */
    static final float WIDEST_FOLLOW = 0.85f;
    /** Taya sees more of the court than the Throwers (their advantage for guarding the can): zoomed 3/5 as close. */
    static final float TAYA_CLOSEST = CLOSEST * 5f / 3f;
    /** Taya, as wide as it gets: the whole court. */
    static final float TAYA_WIDEST = 1f;
    /** Room kept around the player and the can (world units). */
    static final float PADDING = 170f;
    /** The middle of the view leans this much towards the player (0 = halfway to the can, 1 = on the player). */
    static final float PLAYER_WEIGHT = 0.6f;
    /** How quickly it catches up (higher = snappier). */
    static final float EASE = 4f;

    private final float viewX, viewY, viewW, viewH;
    /** Where it looks now (the middle of the view, world units) and how far zoomed (1 = whole court). */
    public float x, y, zoom = 1f;
    private float targetX, targetY, targetZoom = 1f;

    /** The whole court is {@code viewW} x {@code viewH} from ({@code viewX}, {@code viewY}). */
    public CourtCamera(float viewX, float viewY, float viewW, float viewH) {
        this.viewX = viewX;
        this.viewY = viewY;
        this.viewW = viewW;
        this.viewH = viewH;
        wide();
        snap();
    }

    /** Aim for the whole court. */
    public void wide() {
        targetX = viewX + viewW / 2f;
        targetY = viewY + viewH / 2f;
        targetZoom = 1f;
    }

    /** Aim for the player at (px, py) and the can at (cx, cy). {@code taya}: that player is Taya (a wider view). */
    public void follow(float px, float py, float cx, float cy, boolean taya) {
        float needed = Math.max((Math.abs(px - cx) + 2f * PADDING) / viewW, (Math.abs(py - cy) + 2f * PADDING) / viewH);
        targetZoom = taya ? Math.max(TAYA_CLOSEST, Math.min(TAYA_WIDEST, needed)) : Math.max(CLOSEST, Math.min(WIDEST_FOLLOW, needed));
        float halfW = targetZoom * viewW / 2f, halfH = targetZoom * viewH / 2f;
        float mx = cx + (px - cx) * PLAYER_WEIGHT, my = cy + (py - cy) * PLAYER_WEIGHT;
        // The player always stays in view, PADDING from the edge (if the can doesn't fit, it's the can that goes)
        targetX = clamp(mx, px - halfW + PADDING, px + halfW - PADDING);
        targetY = clamp(my, py - halfH + PADDING, py + halfH - PADDING);
    }

    /** Eases towards the aim. */
    public void update(float delta) {
        float k = 1f - (float) Math.exp(-EASE * delta);
        zoom += (targetZoom - zoom) * k;
        x += (targetX - x) * k;
        y += (targetY - y) * k;
        keepOnMap();
    }

    /** Jumps straight to the aim. */
    public void snap() {
        zoom = targetZoom;
        x = targetX;
        y = targetY;
        keepOnMap();
    }

    private void keepOnMap() {
        float halfW = zoom * viewW / 2f, halfH = zoom * viewH / 2f;
        x = clamp(x, viewX + halfW, viewX + viewW - halfW);
        y = clamp(y, viewY + halfH, viewY + viewH - halfH);
    }

    /** True if (wx, wy) is on screen, at least {@code margin} world units inside the edge. */
    public boolean sees(float wx, float wy, float margin) {
        float halfW = zoom * viewW / 2f, halfH = zoom * viewH / 2f;
        return Math.abs(wx - x) <= halfW - margin && Math.abs(wy - y) <= halfH - margin;
    }

    /** Where world point (wx, wy) is on the fixed whole-court layer the HUD is drawn in. */
    public float toScreenX(float wx) {
        return viewX + viewW / 2f + (wx - x) / zoom;
    }

    public float toScreenY(float wy) {
        return viewY + viewH / 2f + (wy - y) / zoom;
    }

    private static float clamp(float v, float lo, float hi) {
        return lo > hi ? (lo + hi) / 2f : Math.max(lo, Math.min(hi, v));
    }
}
