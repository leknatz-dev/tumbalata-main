package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Static walls from a Tiled object layer (rectangles, rotated rectangles and convex polygons),
 * plus the box-vs-wall queries the game uses: blocked checks, sliding along walls and swept movement.
 * Shared by GameScreen and MenuBackdrop. Has no rendering or GL code, so it can be unit tested.
 */
public class MapCollision {
    /** Swept movement is checked in steps of this many pixels, so fast objects can't skip thin walls. */
    public static final float SWEEP_STEP = 4f;
    /** How far (px) an object stuck inside a wall is searched outward for a free spot before sweeping. */
    public static final float UNSTICK_RADIUS = 24f;

    private final Array<Shape2D> shapes;
    // Bounding box per polygon (null for rectangles), computed once: walls never move, and a cheap box test
    // skips the polygon check for walls that are nowhere near
    private final Array<Rectangle> bounds;

    private final Rectangle hitboxRect = new Rectangle();
    private final float[] hitboxVerts = new float[8];
    private final Polygon hitboxPoly = new Polygon(new float[8]);

    public MapCollision(Array<Shape2D> shapes) {
        this.shapes = new Array<>(shapes);
        this.bounds = new Array<>(shapes.size);
        for (Shape2D shape : this.shapes) {
            bounds.add(shape instanceof Polygon ? new Rectangle(((Polygon) shape).getBoundingRectangle()) : null);
        }
    }

    /** Loads every object in the layers named {@code layerName} (case-insensitive, also inside group layers). */
    public static MapCollision fromLayer(TiledMap map, String layerName) {
        return fromLayers(map.getLayers(), layerName);
    }

    public static MapCollision fromLayers(MapLayers layers, String layerName) {
        Array<Shape2D> out = new Array<>();
        collect(layers, layerName, false, out);
        return new MapCollision(out);
    }

    /** One collision set made of several others, e.g. the can's own walls plus the player walls. */
    public static MapCollision combine(MapCollision... parts) {
        Array<Shape2D> all = new Array<>();
        for (MapCollision part : parts) all.addAll(part.shapes);
        return new MapCollision(all);
    }

    /** The wall shapes, for debug drawing. Do not modify. */
    public Array<Shape2D> shapes() {
        return shapes;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** True if a w x h box centred on (cx, cy) overlaps any wall. */
    public boolean isBlocked(float cx, float cy, float w, float h) {
        float left = cx - w / 2f;
        float bottom = cy - h / 2f;
        float right = left + w;
        float top = bottom + h;

        hitboxRect.set(left, bottom, w, h);
        boolean polyBuilt = false;

        for (int i = 0; i < shapes.size; i++) {
            Shape2D wall = shapes.get(i);
            if (wall instanceof Rectangle) {
                if (((Rectangle) wall).overlaps(hitboxRect)) return true;
                continue;
            }
            if (!(wall instanceof Polygon)) continue;
            Rectangle b = bounds.get(i);
            if (b.x > right || b.x + b.width < left || b.y > top || b.y + b.height < bottom) continue;
            if (!polyBuilt) {
                hitboxVerts[0] = left;  hitboxVerts[1] = bottom;
                hitboxVerts[2] = right; hitboxVerts[3] = bottom;
                hitboxVerts[4] = right; hitboxVerts[5] = top;
                hitboxVerts[6] = left;  hitboxVerts[7] = top;
                hitboxPoly.setVertices(hitboxVerts);
                polyBuilt = true;
            }
            if (Intersector.overlapConvexPolygons(hitboxPoly, (Polygon) wall)) return true;
        }
        return false;
    }

    /**
     * Moves a box that went from (prevX, prevY) to {@code pos} back out of walls, keeping whichever axis is free,
     * so it slides along walls. A box that was already inside a wall is left alone so it can walk out.
     */
    public void slide(Vector2 pos, float prevX, float prevY, float w, float h, float offY) {
        float nx = pos.x;
        float ny = pos.y;

        if (!isBlocked(nx, ny + offY, w, h)) return;
        if (isBlocked(prevX, prevY + offY, w, h)) return;

        if (!isBlocked(nx, prevY + offY, w, h)) {
            pos.set(nx, prevY);
        } else if (!isBlocked(prevX, ny + offY, w, h)) {
            pos.set(prevX, ny);
        } else {
            pos.set(prevX, prevY);
        }
    }

    /**
     * Moves a box from (oldX, oldY) towards {@code pos} in {@link #SWEEP_STEP} steps. On hitting a wall it stops at the
     * last free spot and reflects {@code vel} on the blocked axis, scaled by {@code bounce}. A box that starts inside a
     * wall is first nudged to the nearest free spot.
     */
    public void sweep(Vector2 pos, Vector2 vel, float oldX, float oldY,
                      float w, float h, float offX, float offY, float bounce) {
        float nx = pos.x, ny = pos.y;
        if (nx == oldX && ny == oldY) return;

        float startX = oldX, startY = oldY;
        if (isBlocked(startX + offX, startY + offY, w, h)) {
            boolean found = false;
            for (float r = 1f; r <= UNSTICK_RADIUS && !found; r += 1f) {
                for (int i = 0; i < 16; i++) {
                    float a = i * MathUtils.PI2 / 16f;
                    float cx = oldX + MathUtils.cos(a) * r;
                    float cy = oldY + MathUtils.sin(a) * r;
                    if (!isBlocked(cx + offX, cy + offY, w, h)) {
                        startX = cx;
                        startY = cy;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return;
        }

        float dx = nx - startX, dy = ny - startY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist == 0f) {
            pos.set(startX, startY);
            return;
        }

        int steps = Math.max(1, MathUtils.ceil(dist / SWEEP_STEP));
        float lastX = startX, lastY = startY;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float cx = startX + dx * t;
            float cy = startY + dy * t;
            if (isBlocked(cx + offX, cy + offY, w, h)) {
                boolean xBlocked = isBlocked(cx + offX, lastY + offY, w, h);
                boolean yBlocked = isBlocked(lastX + offX, cy + offY, w, h);
                if (!xBlocked && !yBlocked) {
                    xBlocked = true;
                    yBlocked = true;
                }
                pos.set(lastX, lastY);
                if (xBlocked) vel.x = -vel.x * bounce;
                if (yBlocked) vel.y = -vel.y * bounce;
                return;
            }
            lastX = cx;
            lastY = cy;
        }
        pos.set(nx, ny);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private static void collect(MapLayers layers, String layerName, boolean insideMatch, Array<Shape2D> out) {
        for (MapLayer layer : layers) {
            boolean matches = insideMatch || layerName.equalsIgnoreCase(layer.getName());
            if (layer instanceof MapGroupLayer) {
                collect(((MapGroupLayer) layer).getLayers(), layerName, matches, out);
            } else if (matches) {
                for (MapObject obj : layer.getObjects()) {
                    addObject(layerName, obj, out);
                }
            }
        }
    }

    private static void addObject(String layerName, MapObject obj, Array<Shape2D> out) {
        if (obj instanceof RectangleMapObject) {
            Rectangle r = ((RectangleMapObject) obj).getRectangle();
            float rotation = obj.getProperties().get("rotation", 0f, Float.class);
            if (Math.abs(rotation) < 0.01f) {
                out.add(r);
            } else {
                // Tiled rotates clockwise around the object's top-left corner
                float px = r.x, py = r.y + r.height;
                float rad = -rotation * MathUtils.degreesToRadians;
                float cos = MathUtils.cos(rad), sin = MathUtils.sin(rad);
                float[] local = { 0, -r.height, r.width, -r.height, r.width, 0, 0, 0 };
                float[] v = new float[8];
                for (int i = 0; i < 4; i++) {
                    float lx = local[i * 2], ly = local[i * 2 + 1];
                    v[i * 2] = px + lx * cos - ly * sin;
                    v[i * 2 + 1] = py + lx * sin + ly * cos;
                }
                out.add(new Polygon(v));
            }
        } else if (obj instanceof PolygonMapObject) {
            out.add(((PolygonMapObject) obj).getPolygon());
        } else if (Gdx.app != null) {
            Gdx.app.log("MapCollision", "Skipped unsupported object '" + obj.getName() + "' ("
                + obj.getClass().getSimpleName() + ") in layer " + layerName);
        }
    }
}
