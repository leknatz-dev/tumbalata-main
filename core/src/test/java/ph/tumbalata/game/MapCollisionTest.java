package ph.tumbalata.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

class MapCollisionTest {

    private static MapCollision walls(Shape2D... shapes) {
        return new MapCollision(new Array<>(shapes));
    }

    private static Polygon square(float x, float y, float size) {
        return new Polygon(new float[] { x, y, x + size, y, x + size, y + size, x, y + size });
    }

    // ------------------------------------------------------------------
    // isBlocked
    // ------------------------------------------------------------------

    @Test
    void rectangleWallBlocksOverlappingBoxOnly() {
        MapCollision c = walls(new Rectangle(100, 100, 50, 50));
        assertTrue(c.isBlocked(125, 125, 14, 10));
        assertTrue(c.isBlocked(95, 125, 14, 10), "box edge reaches into the wall");
        assertFalse(c.isBlocked(90, 125, 14, 10), "box edge stops short of the wall");
        assertFalse(c.isBlocked(300, 300, 14, 10));
    }

    @Test
    void polygonWallBlocksOverlappingBoxOnly() {
        MapCollision c = walls(square(100, 100, 20));
        assertTrue(c.isBlocked(110, 110, 14, 10));
        assertFalse(c.isBlocked(140, 110, 14, 10));
    }

    @Test
    void polygonWallDoesNotBlockFarAwayBox() {
        // Regression: GameScreen's old hitbox polygon never set its 4th corner, leaving it at (0, 0).
        // The box then stretched into a sliver towards the map origin and hit polygon walls far away from it.
        MapCollision c = walls(square(100, 100, 20));
        assertFalse(c.isBlocked(300, 300, 14, 10));
    }

    @Test
    void emptyCollisionNeverBlocks() {
        assertFalse(walls().isBlocked(0, 0, 100, 100));
    }

    // ------------------------------------------------------------------
    // Loading from Tiled layers
    // ------------------------------------------------------------------

    @Test
    void loadsRectanglesAndPolygonsFromNamedLayerOnly() {
        MapLayers layers = new MapLayers();
        layers.add(layer("Collision1", new RectangleMapObject(0, 0, 10, 10),
            new PolygonMapObject(new float[] { 50, 50, 60, 50, 60, 60 })));
        layers.add(layer("collision2", new RectangleMapObject(500, 500, 10, 10)));

        MapCollision c = MapCollision.fromLayers(layers, "collision1");
        assertEquals(2, c.shapes().size, "layer name is case-insensitive, other layers are ignored");
        assertTrue(c.isBlocked(5, 5, 2, 2));
        assertTrue(c.isBlocked(58, 52, 2, 2));
        assertFalse(c.isBlocked(505, 505, 2, 2));
    }

    @Test
    void loadsEveryLayerInsideMatchingGroup() {
        MapGroupLayer group = new MapGroupLayer();
        group.setName("collision1");
        group.getLayers().add(layer("walls", new RectangleMapObject(0, 0, 10, 10)));
        group.getLayers().add(layer("more walls", new RectangleMapObject(20, 0, 10, 10)));

        MapLayers layers = new MapLayers();
        layers.add(group);

        assertEquals(2, MapCollision.fromLayers(layers, "collision1").shapes().size);
    }

    @Test
    void rotatedRectangleIsRotatedClockwiseAroundTopLeft() {
        // 50 x 10 bar at (100, 100); Tiled's 90 degree rotation turns it to hang down from its top-left (100, 110)
        RectangleMapObject bar = new RectangleMapObject(100, 100, 50, 10);
        bar.getProperties().put("rotation", 90f);
        MapLayers layers = new MapLayers();
        layers.add(layer("collision1", bar));

        MapCollision c = MapCollision.fromLayers(layers, "collision1");
        assertTrue(c.isBlocked(95, 80, 2, 2), "inside the rotated bar (x 90..100, y 60..110)");
        assertFalse(c.isBlocked(130, 105, 2, 2), "where the unrotated bar would have been");
    }

    @Test
    void combineKeepsWallsFromAllParts() {
        MapCollision c = MapCollision.combine(walls(new Rectangle(0, 0, 10, 10)), walls(square(100, 100, 10)));
        assertEquals(2, c.shapes().size);
        assertTrue(c.isBlocked(5, 5, 2, 2));
        assertTrue(c.isBlocked(105, 105, 2, 2));
    }

    // ------------------------------------------------------------------
    // slide (player movement)
    // ------------------------------------------------------------------

    @Test
    void slideKeepsTheFreeAxisWhenMovingIntoAWall() {
        MapCollision c = walls(new Rectangle(100, 0, 10, 200));
        Vector2 pos = new Vector2(96, 55); // moved right into the wall and up
        c.slide(pos, 90, 50, 14, 10, 0);
        assertEquals(90, pos.x, 0.001f, "blocked x is undone");
        assertEquals(55, pos.y, 0.001f, "free y is kept");
    }

    @Test
    void slideLeavesFreeMovementAlone() {
        MapCollision c = walls(new Rectangle(100, 0, 10, 200));
        Vector2 pos = new Vector2(60, 70);
        c.slide(pos, 50, 50, 14, 10, 0);
        assertEquals(new Vector2(60, 70), pos);
    }

    @Test
    void slideLetsAPlayerAlreadyInsideAWallMoveOut() {
        MapCollision c = walls(new Rectangle(100, 0, 10, 200));
        Vector2 pos = new Vector2(106, 50);
        c.slide(pos, 104, 50, 14, 10, 0);
        assertEquals(106, pos.x, 0.001f);
    }

    // ------------------------------------------------------------------
    // sweep (can and slipper)
    // ------------------------------------------------------------------

    @Test
    void fastObjectDoesNotTunnelThroughThinWall() {
        MapCollision c = walls(new Rectangle(200, -1000, 4, 2000));
        Vector2 pos = new Vector2(400, 0);         // one frame moved it from x=100 to x=400, past the wall
        Vector2 vel = new Vector2(300 * 60, 0);
        c.sweep(pos, vel, 100, 0, 12, 12, 0, 0, 0.5f);

        assertTrue(pos.x + 6 <= 200, "stopped on the near side of the wall, x=" + pos.x);
        assertEquals(-300 * 60 * 0.5f, vel.x, 0.001f, "bounced back with damping");
        assertEquals(0, vel.y, 0.001f);
    }

    @Test
    void sweepReflectsOnlyTheBlockedAxis() {
        MapCollision c = walls(new Rectangle(200, -1000, 4, 2000));
        Vector2 pos = new Vector2(250, 50);
        Vector2 vel = new Vector2(100, 100);
        c.sweep(pos, vel, 150, 0, 12, 12, 0, 0, 0.5f);

        assertEquals(-50, vel.x, 0.001f);
        assertEquals(100, vel.y, 0.001f);
    }

    @Test
    void objectStartingInsideWallIsNudgedOutThenMoves() {
        // Starts in the right half of the wall, so the nearest free spot is on the right; then flies right freely
        MapCollision c = walls(new Rectangle(100, 0, 10, 200));
        Vector2 pos = new Vector2(160, 100);
        Vector2 vel = new Vector2(100, 0);
        c.sweep(pos, vel, 108, 100, 12, 12, 0, 0, 0.5f);

        assertFalse(c.isBlocked(pos.x, pos.y, 12, 12));
        assertEquals(160, pos.x, 0.001f);
        assertEquals(100, vel.x, 0.001f, "no bounce");
    }

    @Test
    void sweepWithoutMovementDoesNothing() {
        MapCollision c = walls(new Rectangle(100, 0, 10, 200));
        Vector2 pos = new Vector2(50, 50);
        Vector2 vel = new Vector2(0, 0);
        c.sweep(pos, vel, 50, 50, 12, 12, 0, 0, 0.5f);
        assertEquals(new Vector2(50, 50), pos);
    }

    private static MapLayer layer(String name, com.badlogic.gdx.maps.MapObject... objects) {
        MapLayer layer = new MapLayer();
        layer.setName(name);
        for (com.badlogic.gdx.maps.MapObject o : objects) layer.getObjects().add(o);
        return layer;
    }
}
