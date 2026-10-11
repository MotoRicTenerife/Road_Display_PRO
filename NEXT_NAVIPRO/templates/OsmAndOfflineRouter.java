package com.riccardo.roaddisplay;

import android.content.Context;
import android.location.Location;

import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.LatLon;
import net.osmand.router.RoutePlannerFrontEnd;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RoutingConfiguration;
import net.osmand.router.RoutingContext;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal offline route adapter around OsmAnd's Java routing engine.
 * Only returns a route when a real local .obf file contains routable data.
 * Raster tiles and downloaded-but-unreadable files are never treated as routing data.
 */
final class OsmAndOfflineRouter {
    private static final double TURN_THRESHOLD_DEGREES = 35.0;

    private OsmAndOfflineRouter() { }

    static boolean hasInstalledMaps(Context context) {
        File[] files = installedMaps(context);
        return files.length > 0;
    }

    /** Diagnostic used in user-visible errors; do not confuse maps inside the official OsmAnd app sandbox with ours. */
    static String installedMapStatus(Context context) {
        File dir = mapDirectory(context);
        File[] files = installedMaps(context);
        if (files.length == 0) {
            return "Cartella controllata: " + dir.getAbsolutePath()
                    + ". Nessun file .obf leggibile trovato. Se la mappa è stata scaricata nell'app OsmAnd ufficiale, va scaricata/importata anche in NEXT NAVI PRO.";
        }
        StringBuilder status = new StringBuilder("Mappe .obf trovate: ");
        for (int i = 0; i < files.length; i++) {
            if (i > 0) status.append(", ");
            status.append(files[i].getName()).append(" (")
                    .append(files[i].length() / (1024 * 1024)).append(" MB)");
        }
        return status.toString();
    }

    private static File[] installedMaps(Context context) {
        File dir = mapDirectory(context);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".obf")
                && new File(d, name).isFile() && new File(d, name).length() > 1024);
        return files == null ? new File[0] : files;
    }

    static MainActivity.NavigationRouteResult calculate(Context context, Location start,
                                                         double destLat, double destLon) throws Exception {
        if (start == null) throw new IllegalArgumentException("Posizione iniziale mancante");
        File[] maps = installedMaps(context);
        if (maps.length == 0) throw new IllegalStateException(installedMapStatus(context));

        ArrayList<BinaryMapIndexReader> readers = new ArrayList<>();
        ArrayList<RandomAccessFile> handles = new ArrayList<>();
        try {
            for (File map : maps) {
                RandomAccessFile raf = new RandomAccessFile(map, "r");
                handles.add(raf);
                readers.add(new BinaryMapIndexReader(raf, map));
            }
            BinaryMapIndexReader[] readerArray = readers.toArray(new BinaryMapIndexReader[0]);
            Exception last = null;
            String[] profiles = {"motorcycle", "car"};
            for (String profile : profiles) {
                try {
                    RoutingConfiguration.Builder builder = RoutingConfiguration.getDefault();
                    RoutingConfiguration config = builder.build(profile,
                            new RoutingConfiguration.RoutingMemoryLimits(256, 64));
                    RoutePlannerFrontEnd planner = new RoutePlannerFrontEnd();
                    RoutingContext routingContext = planner.buildRoutingContext(config, null, readerArray);
                    if (planner.findRouteSegment(start.getLatitude(), start.getLongitude(), routingContext, null) == null)
                        throw new IllegalStateException("La posizione GPS non ricade su una strada della mappa OsmAnd");
                    if (planner.findRouteSegment(destLat, destLon, routingContext, null) == null)
                        throw new IllegalStateException("La destinazione non ricade su una strada della mappa OsmAnd");
                    // Recreate the context after endpoint matching, as recommended by OsmAnd's own routing tests.
                    routingContext = planner.buildRoutingContext(config, null, readerArray);
                    List<RouteSegmentResult> segments = planner.searchRoute(routingContext,
                            new LatLon(start.getLatitude(), start.getLongitude()),
                            new LatLon(destLat, destLon), null).getList();
                    MainActivity.NavigationRouteResult result = convert(segments, profile);
                    if (result.route.size() >= 2 && result.distanceM > 0) return result;
                    throw new IllegalStateException("OsmAnd non ha restituito una geometria di percorso valida");
                } catch (Exception e) {
                    last = e;
                }
            }
            throw new IllegalStateException("Calcolo offline OsmAnd non riuscito: "
                    + (last == null ? "errore sconosciuto" : safeMessage(last)), last);
        } finally {
            for (BinaryMapIndexReader reader : readers) try { reader.close(); } catch (Exception ignored) { }
            for (RandomAccessFile raf : handles) try { raf.close(); } catch (Exception ignored) { }
        }
    }

    private static MainActivity.NavigationRouteResult convert(List<RouteSegmentResult> segments, String profile) {
        MainActivity.NavigationRouteResult out = new MainActivity.NavigationRouteResult();
        out.provider = "OSMAND_OFFLINE";
        if (segments == null || segments.isEmpty()) return out;

        final class SegmentSpan {
            int start, end;
            double distance;
            double heading;
            String name;
            SegmentSpan(int s, int e, double d, double h, String n) {
                start=s; end=e; distance=d; heading=h; name=n;
            }
        }
        ArrayList<SegmentSpan> spans = new ArrayList<>();
        double duration = 0.0;
        for (RouteSegmentResult segment : segments) {
            int from = segment.getStartPointIndex();
            int to = segment.getEndPointIndex();
            int dir = from <= to ? 1 : -1;
            int startIndex = out.route.size();
            MainActivity.RoutePoint previous = out.route.isEmpty() ? null : out.route.get(out.route.size()-1);
            for (int i = from; dir > 0 ? i <= to : i >= to; i += dir) {
                LatLon p = segment.getPoint(i);
                MainActivity.RoutePoint point = new MainActivity.RoutePoint(p.getLatitude(), p.getLongitude());
                if (previous == null || distance(previous.lat, previous.lon, point.lat, point.lon) > 0.5) {
                    out.route.add(point);
                    previous = point;
                }
            }
            int endIndex = out.route.size()-1;
            if (endIndex < startIndex) continue;
            double heading = startIndex < endIndex
                    ? bearing(out.route.get(startIndex), out.route.get(Math.min(endIndex, startIndex+1)))
                    : 0.0;
            String name = "";
            try { name = segment.getObject().getName("it", false); } catch (Exception ignored) { }
            if (name == null) name = "";
            spans.add(new SegmentSpan(startIndex, endIndex, Math.max(0, segment.getDistance()), heading, name));
            duration += Math.max(0, segment.getSegmentTime());
        }
        if (out.route.size() < 2) return out;

        double[] progress = new double[out.route.size()];
        for (int i=1; i<out.route.size(); i++)
            progress[i] = progress[i-1] + distance(out.route.get(i-1).lat, out.route.get(i-1).lon,
                    out.route.get(i).lat, out.route.get(i).lon);
        for (int i=1; i<spans.size(); i++) {
            SegmentSpan prev = spans.get(i-1), next = spans.get(i);
            if (next.start < 0 || next.start >= out.route.size()) continue;
            double angle = signedAngle(next.heading - prev.heading);
            if (Math.abs(angle) < TURN_THRESHOLD_DEGREES) continue;
            MainActivity.RoutePoint point = out.route.get(next.start);
            String type = angle > 0 ? "right" : "left";
            String instruction = angle > 0 ? "SVOLTA A DESTRA" : "SVOLTA A SINISTRA";
            if (!next.name.isEmpty()) instruction += " · " + next.name;
            MainActivity.RouteStep step = new MainActivity.RouteStep(point.lat, point.lon,
                    next.distance, instruction, next.name, type, -1);
            step.beginShapeIndex = next.start;
            step.endShapeIndex = next.end;
            step.routeProgressM = progress[next.start];
            step.endRouteProgressM = progress[Math.max(next.start, next.end)];
            step.guidanceRelevant = true;
            out.steps.add(step);
        }
        out.distanceM = progress[progress.length-1];
        out.durationSec = duration;
        if (out.steps.isEmpty()) {
            MainActivity.RoutePoint end = out.route.get(out.route.size()-1);
            MainActivity.RouteStep finish = new MainActivity.RouteStep(end.lat, end.lon, 0,
                    "PROSEGUIRE SUL PERCORSO", "", "continue", -1);
            finish.beginShapeIndex = out.route.size()-1;
            finish.endShapeIndex = out.route.size()-1;
            finish.routeProgressM = out.distanceM;
            finish.endRouteProgressM = out.distanceM;
            finish.guidanceRelevant = false;
            out.steps.add(finish);
        }
        return out;
    }

    private static File mapDirectory(Context context) {
        File external = context.getExternalFilesDir(null);
        File base = external != null ? external : context.getFilesDir();
        return new File(base, "osmand/maps");
    }
    private static double signedAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    private static double bearing(MainActivity.RoutePoint a, MainActivity.RoutePoint b) {
        double p1=Math.toRadians(a.lat), p2=Math.toRadians(b.lat), dl=Math.toRadians(b.lon-a.lon);
        return (Math.toDegrees(Math.atan2(Math.sin(dl)*Math.cos(p2),
                Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl)))+360)%360;
    }
    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double p1=Math.toRadians(lat1), p2=Math.toRadians(lat2);
        double dp=p2-p1, dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 6371000.0*2.0*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0.0,1.0-a)));
    }
    static String safeMessage(Exception e) {
        String s=e.getMessage();
        return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }
}
