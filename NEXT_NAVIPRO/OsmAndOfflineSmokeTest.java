package net.osmand.router;

import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.LatLon;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Real .obf routing smoke test run in CI against OsmAnd's pinned Java engine. */
public class OsmAndOfflineSmokeTest {
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
