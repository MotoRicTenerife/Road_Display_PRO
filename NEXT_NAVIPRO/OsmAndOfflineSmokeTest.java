package net.osmand.router;

import net.osmand.binary.BinaryMapDataObject;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.LatLon;
import net.osmand.util.MapUtils;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Real .obf routing smoke test run in CI against OsmAnd's pinned Java engine. */
public class OsmAndOfflineSmokeTest {
    @Test
    public void offlineMapViewportContainsDrawableRoadGeometryNearAdeje() throws Exception {
        String path = System.getenv("OSMAND_SMOKE_OBF");
        assertNotNull("OSMAND_SMOKE_OBF must point to the Canary Islands .obf file", path);
        File map = new File(path);
        assertTrue("Canary .obf fixture must exist and be non-empty", map.isFile() && map.length() > 1024);

        try (RandomAccessFile raf = new RandomAccessFile(map, "r")) {
            BinaryMapIndexReader reader = new BinaryMapIndexReader(raf, map);
            try {
                int centerX = MapUtils.get31TileNumberX(-16.7260);
                int centerY = MapUtils.get31TileNumberY(28.1218);
                int radius31 = 250000;
                BinaryMapIndexReader.SearchFilter highwaysOnly = (types, index) -> {
                    for (int i = 0; i < types.size(); i++) {
                        BinaryMapIndexReader.TagValuePair pair = index.decodeType(types.get(i));
                        if (pair != null && "highway".equals(pair.tag)) return true;
                    }
                    return false;
                };
                List<BinaryMapDataObject> objects = reader.searchMapIndex(
                        BinaryMapIndexReader.buildSearchRequest(centerX - radius31, centerX + radius31,
                                centerY - radius31, centerY + radius31, 15, highwaysOnly));
                assertNotNull("Offline viewport query must return a result list", objects);
                assertTrue("Adeje offline viewport must contain map features for rendering", !objects.isEmpty());

                boolean drawableRoad = false;
                for (BinaryMapDataObject object : objects) {
                    if (object == null || object.getMapIndex() == null || object.getTypes() == null
                            || object.getCoordinates() == null || object.getCoordinates().length < 4) continue;
                    for (int type : object.getTypes()) {
                        BinaryMapIndexReader.TagValuePair pair = object.getMapIndex().decodeType(type);
                        if (pair != null && "highway".equals(pair.tag)) {
                            drawableRoad = true;
                            break;
                        }
                    }
                    if (drawableRoad) break;
                }
                assertTrue("Offline viewport must contain drawable highway geometry, not a blank canvas", drawableRoad);
            } finally {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
    }

    @Test
    public void calculatesRouteInsideCanaryIslandsObf() throws Exception {
        String path = System.getenv("OSMAND_SMOKE_OBF");
        assertNotNull("OSMAND_SMOKE_OBF must point to a downloaded routable .obf file", path);
        File map = new File(path);
        assertTrue("Canary .obf fixture must exist and be non-empty", map.isFile() && map.length() > 1024);

        RandomAccessFile raf = new RandomAccessFile(map, "r");
        BinaryMapIndexReader reader = null;
        try {
            reader = new BinaryMapIndexReader(raf, map);
            BinaryMapIndexReader[] maps = new BinaryMapIndexReader[] { reader };
            RoutingConfiguration config = RoutingConfiguration.getDefault().build(
                    "car", new RoutingConfiguration.RoutingMemoryLimits(256, 64));
            RoutePlannerFrontEnd planner = new RoutePlannerFrontEnd();
            RoutingContext context = planner.buildRoutingContext(config, null, maps);

            LatLon start = new LatLon(28.1218, -16.7260); // Adeje
            LatLon end = new LatLon(28.0525, -16.7159);   // Los Cristianos
            assertNotNull("Start coordinate must match a routable road", planner.findRouteSegment(
                    start.getLatitude(), start.getLongitude(), context, null));
            assertNotNull("Destination coordinate must match a routable road", planner.findRouteSegment(
                    end.getLatitude(), end.getLongitude(), context, null));

            context = planner.buildRoutingContext(config, null, maps);
            List<RouteSegmentResult> route = planner.searchRoute(context, start, end, null).detailed;
            assertNotNull("OsmAnd must return a route list", route);
            assertTrue("OsmAnd must return at least one route segment", !route.isEmpty());
            double distanceMeters = 0.0;
            for (RouteSegmentResult segment : route) distanceMeters += segment.getDistance();
            assertTrue("Route must have a plausible positive distance", distanceMeters > 1000.0);
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) { }
            try { raf.close(); } catch (Exception ignored) { }
        }
    }
}
