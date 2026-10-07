package ph.tumbalata.game;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * The court layout the rules need from the map: the line between the Throwers and Taya (it may lean, the court is
 * drawn in perspective) and the area the Throwers spawn in. No drawing.
 *
 * <p>From Tiled ({@link #fromMap}): the "courtline" layer's shape (its centre line, top to bottom) and the
 * "throw area" layer's polygon. Without a map ({@link #straight}) the line is vertical and there is no area, which is
 * what the unit tests use.
 */
public final class CourtLine {
    public static final String LINE_LAYER = "courtline";
    public static final String THROW_AREA_LAYER = "throw area";
    public static final String CAN_ZONE_LAYER = "can zone";

    // The line runs from (topX, topY) to (bottomX, bottomY), world units, y up
    private final float topX, topY, bottomX, bottomY;
    /** Throwers' spawn area (world vertices), or null. */
    private final Polygon throwArea;
    /** Where Taya may choose to stand the can before the match (world vertices), or null. */
    private Polygon canZone;

    private CourtLine(float topX, float topY, float bottomX, float bottomY, Polygon throwArea) {
        this.topX = topX;
        this.topY = topY;
        this.bottomX = bottomX;
        this.bottomY = bottomY;
        this.throwArea = throwArea;
    }

    /** A line from (topX, topY) to (bottomX, bottomY) with an optional throw area (world vertices). For tests. */
    static CourtLine of(float topX, float topY, float bottomX, float bottomY, float[] throwArea) {
        return new CourtLine(topX, topY, bottomX, bottomY, throwArea == null ? null : new Polygon(throwArea));
    }

    /** A vertical line at x, no throw area. */
    public static CourtLine straight(float x) {
        return new CourtLine(x, 1f, x, 0f, null);
    }

    /**
     * Reads the court line and throw area from the map. Falls back to a vertical line at {@code fallbackX} (and no
     * area) for whatever is missing.
     */
    public static CourtLine fromMap(TiledMap map, float fallbackX) {
        float[] line = null;
        MapLayer lineLayer = map.getLayers().get(LINE_LAYER);
        if (lineLayer != null && lineLayer.getObjects().getCount() > 0) {
            line = centreLine(worldVertices(lineLayer.getObjects().get(0)));
        }
        Polygon area = null;
        MapLayer areaLayer = map.getLayers().get(THROW_AREA_LAYER);
        if (areaLayer != null && areaLayer.getObjects().getCount() > 0) {
            float[] v = worldVertices(areaLayer.getObjects().get(0));
            if (v != null && v.length >= 6) area = new Polygon(v);
        }
        CourtLine court = line == null ? new CourtLine(fallbackX, 1f, fallbackX, 0f, area)
            : new CourtLine(line[0], line[1], line[2], line[3], area);
        MapLayer zoneLayer = map.getLayers().get(CAN_ZONE_LAYER);
        if (zoneLayer != null && zoneLayer.getObjects().getCount() > 0) {
            float[] v = worldVertices(zoneLayer.getObjects().get(0));
            if (v != null && v.length >= 6) court.canZone = new Polygon(v);
        }
        return court;
    }

    public static final String CAN_BASE_LAYER = "can base";

    /**
     * Where the can stands, from the "can base" object layer: the centre of its first object (a point, a small
     * rectangle or an ellipse), or null if the map has no such layer.
     */
    public static Vector2 canBaseFromMap(TiledMap map) {
        MapLayer layer = map.getLayers().get(CAN_BASE_LAYER);
        if (layer == null || layer.getObjects().getCount() == 0) return null;
        MapObject o = layer.getObjects().get(0);
        if (o instanceof PointMapObject) return new Vector2(((PointMapObject) o).getPoint());
        if (o instanceof EllipseMapObject) {
            Ellipse e = ((EllipseMapObject) o).getEllipse();
            return new Vector2(e.x + e.width / 2f, e.y + e.height / 2f);
        }
        if (o instanceof RectangleMapObject) return ((RectangleMapObject) o).getRectangle().getCenter(new Vector2());
        if (o instanceof PolygonMapObject) return ((PolygonMapObject) o).getPolygon().getBoundingRectangle().getCenter(new Vector2());
        return null;
    }

    /** The object's outline in world coordinates (the map loader already flips y). */
    private static float[] worldVertices(MapObject o) {
        if (o instanceof PolygonMapObject) return ((PolygonMapObject) o).getPolygon().getTransformedVertices();
        if (o instanceof PolylineMapObject) return ((PolylineMapObject) o).getPolyline().getTransformedVertices();
        if (o instanceof RectangleMapObject) {
            Rectangle r = ((RectangleMapObject) o).getRectangle();
            return new float[] { r.x, r.y, r.x + r.width, r.y, r.x + r.width, r.y + r.height, r.x, r.y + r.height };
        }
        return null;
    }

    /**
     * Centre line of a long thin shape: the middle of its two highest points to the middle of its two lowest.
     * For a two-point polyline this is just the line itself. Returns {topX, topY, bottomX, bottomY}, or null.
     */
    static float[] centreLine(float[] v) {
        if (v == null || v.length < 4) return null;
        int n = v.length / 2;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(v[b * 2 + 1], v[a * 2 + 1])); // highest y first
        int topCount = Math.max(1, n / 2), bottomCount = Math.max(1, n - topCount);
        float tx = 0, ty = 0, bx = 0, by = 0;
        for (int i = 0; i < topCount; i++) { tx += v[order[i] * 2]; ty += v[order[i] * 2 + 1]; }
        for (int i = n - bottomCount; i < n; i++) { bx += v[order[i] * 2]; by += v[order[i] * 2 + 1]; }
        return new float[] { tx / topCount, ty / topCount, bx / bottomCount, by / bottomCount };
    }

    /** x of the line at height y (extended past its ends). */
    public float xAt(float y) {
        if (topY == bottomY) return topX;
        float t = (y - bottomY) / (topY - bottomY);
        return bottomX + (topX - bottomX) * t;
    }

    /** True when (x, y) is on Taya's side of the line. */
    public boolean isPast(float x, float y) {
        return x > xAt(y);
    }

    public boolean isPast(Vector2 p) {
        return isPast(p.x, p.y);
    }

    public boolean hasThrowArea() {
        return throwArea != null;
    }

    public boolean inThrowArea(float x, float y) {
        return throwArea != null && Intersector.isPointInPolygon(throwArea.getTransformedVertices(), 0,
            throwArea.getTransformedVertices().length, x, y);
    }

    /** Centre of the throw area (average of its corners), or null. */
    public Vector2 throwAreaCentre() {
        if (throwArea == null) return null;
        float[] v = throwArea.getTransformedVertices();
        float x = 0, y = 0;
        for (int i = 0; i < v.length; i += 2) { x += v[i]; y += v[i + 1]; }
        return new Vector2(x / (v.length / 2f), y / (v.length / 2f));
    }

    /** For debug drawing: {topX, topY, bottomX, bottomY}. */
    public float[] endpoints() {
        return new float[] { topX, topY, bottomX, bottomY };
    }

    /** Sets the zone where Taya may stand the can (world vertices), e.g. for tests. */
    CourtLine withCanZone(float[] vertices) {
        canZone = vertices == null ? null : new Polygon(vertices);
        return this;
    }

    public boolean hasCanZone() {
        return canZone != null;
    }

    /** True when (x, y) is inside the can zone (false if the map has none). */
    public boolean inCanZone(float x, float y) {
        return canZone != null && Intersector.isPointInPolygon(canZone.getTransformedVertices(), 0,
            canZone.getTransformedVertices().length, x, y);
    }

    /** The can zone's world vertices (for drawing), or null. */
    public float[] canZoneVertices() {
        return canZone == null ? null : canZone.getTransformedVertices();
    }

    /** Centre of the can zone (average of its corners), or null. */
    public Vector2 canZoneCentre() {
        if (canZone == null) return null;
        float[] v = canZone.getTransformedVertices();
        float x = 0, y = 0;
        for (int i = 0; i < v.length; i += 2) { x += v[i]; y += v[i + 1]; }
        return new Vector2(x / (v.length / 2f), y / (v.length / 2f));
    }

    /** For debug drawing: the throw area's world vertices, or null. */
    public float[] throwAreaVertices() {
        return throwArea == null ? null : throwArea.getTransformedVertices();
    }
}
