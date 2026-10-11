package com.riccardo.roaddisplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;

import gnu.trove.list.array.TIntArrayList;
import net.osmand.binary.BinaryMapDataObject;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.util.MapUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight offline OBF cartography for every Canvas map surface.
 * It reads actual OsmAnd vector objects and never downloads raster tiles.
 * Rendering is deliberately separated from the UI thread: the last complete
 * viewport remains visible while the next viewport is queried.
 */
final class OsmAndOfflineMapRenderer {
    private static final int MAX_FEATURES = 9000;
    private static final int MAX_POINTS = 260000;
    private static final long RETRY_EMPTY_MS = 8000L;

    private static final int WATER = 1;
    private static final int GREEN = 2;
    private static final int BUILDING = 3;
    private static final int RAIL = 4;
    private static final int MINOR_ROAD = 5;
    private static final int ROAD = 6;
    private static final int MAJOR_ROAD = 7;
    private static final int MOTORWAY = 8;
    private static final int COASTLINE = 9;

    private final Context app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "osmand-offline-map-renderer");
        t.setDaemon(true);
        return t;
    });
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private volatile List<Feature> features = Collections.emptyList();
    private volatile boolean loading;
    private volatile String status = "MAPPA OSMAND IN ATTESA";
    private volatile long loadedSignature = Long.MIN_VALUE;
    private volatile long lastRequestAt;
    private volatile double requestedLat = Double.NaN;
    private volatile double requestedLon = Double.NaN;
    private volatile int requestedZoom = -1;
    private volatile float requestedWidth;
    private volatile float requestedHeight;
    private volatile float requestedScale = 1f;
    private volatile float requestedBearing;
    private volatile long wantedSignature = Long.MIN_VALUE;
    private List<ReaderHandle> openReaders = new ArrayList<>();

    private static final class ReaderHandle {
        final File file;
        final RandomAccessFile raf;
        final BinaryMapIndexReader reader;
        ReaderHandle(File f, RandomAccessFile r, BinaryMapIndexReader b) {
            file = f; raf = r; reader = b;
        }
        void close() {
            try { reader.close(); } catch (Exception ignored) { }
            try { raf.close(); } catch (Exception ignored) { }
        }
    }

    private static final class Feature {
        final int[] xy;
        final int kind;
        final boolean area;
        Feature(int[] points, int k, boolean a) { xy = points; kind = k; area = a; }
    }

    OsmAndOfflineMapRenderer(Context context) {
        app = context.getApplicationContext();
    }

    boolean draw(Canvas canvas, RectF bounds, double centerLat, double centerLon,
                 int zoom, float bearing, float scale, float density) {
        File[] maps = installedMaps();
        if (maps.length == 0) return false;

        long signature = signature(maps);
        double safeLat = Math.max(-85.0, Math.min(85.0, centerLat));
        double safeLon = Math.max(-180.0, Math.min(180.0, centerLon));
        if (shouldRefresh(signature, safeLat, safeLon, zoom, bounds, scale)) {
            requestViewport(maps, signature, safeLat, safeLon, zoom, bounds, scale, bearing);
        }

        canvas.save();
        canvas.clipRect(bounds);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF2F0E8);
        canvas.drawRect(bounds, paint);
        canvas.translate(bounds.centerX(), bounds.centerY());
        canvas.rotate(-bearing);

        int centerX = MapUtils.get31TileNumberX(safeLon);
        int centerY = MapUtils.get31TileNumberY(safeLat);
        double pixelScale = 256.0 / (double)(1L << (31 - Math.max(10, Math.min(19, zoom))));
        List<Feature> snapshot = features;
        for (Feature feature : snapshot) {
            if (feature.xy.length < 4) continue;
            Path path = new Path();
            boolean started = false;
            for (int i = 0; i + 1 < feature.xy.length; i += 2) {
                float x = (float)((feature.xy[i] - (double)centerX) * pixelScale * scale);
                float y = (float)((feature.xy[i + 1] - (double)centerY) * pixelScale * scale);
                if (!Float.isFinite(x) || !Float.isFinite(y)) { started = false; continue; }
                if (Math.abs(x) > bounds.width() * 4 || Math.abs(y) > bounds.height() * 4) {
                    // Keep distant vertices only when a line crosses the visible viewport.
                    // Canvas clipping handles the final segment; this avoids huge path bounds.
                    if (!started) continue;
                }
                if (!started) { path.moveTo(x, y); started = true; }
                else path.lineTo(x, y);
            }
            if (feature.area) path.close();
            drawFeature(canvas, path, feature, density);
        }
        canvas.restore();

        if (snapshot.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            paint.setTextSize(Math.max(10f, Math.min(14f, bounds.width() * 0.035f)));
            paint.setColor(0xFF4A5660);
            String message = loading ? "CARICAMENTO MAPPA OSMAND…" : status;
            canvas.drawText(message, bounds.centerX(), bounds.bottom - Math.max(12f, density * 24f), paint);
        }
        return true;
    }

    private void drawFeature(Canvas c, Path path, Feature f, float density) {
        if (f.area && (f.kind == WATER || f.kind == GREEN || f.kind == BUILDING)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(f.kind == WATER ? 0xFFB9D9E9 : f.kind == GREEN ? 0xFFD8E7C9 : 0xFFE2DED5);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(0.35f, density * 0.45f));
            paint.setColor(f.kind == WATER ? 0xFF9DC8DF : f.kind == GREEN ? 0xFFC1D7AF : 0xFFD0C9BD);
            c.drawPath(path, paint);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        float width;
        int casing, fill;
        switch (f.kind) {
            case WATER:
            case COASTLINE:
                width = f.kind == COASTLINE ? 1.7f : 1.2f;
                casing = fill = 0xFF72B6D6;
                break;
            case RAIL:
                width = 1.0f; casing = 0xFF9A9290; fill = 0xFFF9F7F2; break;
            case MOTORWAY:
                width = 4.2f; casing = 0xFFC78C55; fill = 0xFFF4C18A; break;
            case MAJOR_ROAD:
                width = 3.1f; casing = 0xFFC3A47C; fill = 0xFFFFE2A9; break;
            case ROAD:
                width = 2.1f; casing = 0xFFB9B6AE; fill = 0xFFFFFFFF; break;
            case MINOR_ROAD:
            default:
                width = 1.35f; casing = 0xFFC7C3BA; fill = 0xFFFCFBF7; break;
        }
        paint.setStrokeWidth(Math.max(0.8f, width * density + density * 0.7f));
        paint.setColor(casing);
        c.drawPath(path, paint);
        paint.setStrokeWidth(Math.max(0.65f, width * density));
        paint.setColor(fill);
        c.drawPath(path, paint);
    }

    private boolean shouldRefresh(long signature, double lat, double lon, int zoom,
                                  RectF bounds, float scale) {
        if (signature != wantedSignature || zoom != requestedZoom || features.isEmpty() && !loading
                && System.currentTimeMillis() - lastRequestAt >= RETRY_EMPTY_MS) return true;
        if (Double.isNaN(requestedLat) || Double.isNaN(requestedLon)) return true;
        double cos = Math.max(0.15, Math.cos(Math.toRadians(lat)));
        double movedX = Math.abs(lon - requestedLon) * 111320.0 * cos;
        double movedY = Math.abs(lat - requestedLat) * 110540.0;
        return movedX > Math.max(90.0, bounds.width() * 0.35 / Math.max(0.5f, scale) * 156543.0 / (1 << zoom))
                || movedY > Math.max(90.0, bounds.height() * 0.35 / Math.max(0.5f, scale) * 156543.0 / (1 << zoom))
                || Math.abs(bounds.width() - requestedWidth) > 80f
                || Math.abs(bounds.height() - requestedHeight) > 80f
                || Math.abs(bearingDelta(requestedBearing, 0f)) > 999f;
    }

    private void requestViewport(File[] maps, long signature, double lat, double lon, int zoom,
                                 RectF bounds, float scale, float bearing) {
        if (loading) return;
        loading = true;
        wantedSignature = signature;
        requestedLat = lat;
        requestedLon = lon;
        requestedZoom = zoom;
        requestedWidth = bounds.width();
        requestedHeight = bounds.height();
        requestedScale = scale;
        requestedBearing = bearing;
        lastRequestAt = System.currentTimeMillis();
        final File[] selected = maps.clone();
        final double qLat = lat, qLon = lon;
        final int qZoom = zoom;
        final float qWidth = bounds.width(), qHeight = bounds.height(), qScale = scale, qBearing = bearing;
        worker.execute(() -> {
            try {
                ensureReaders(selected, signature);
                int cx = MapUtils.get31TileNumberX(qLon);
                int cy = MapUtils.get31TileNumberY(qLat);
                double a = Math.toRadians(qBearing);
                double halfW = (Math.abs(Math.cos(a)) * qWidth + Math.abs(Math.sin(a)) * qHeight)
                        / (2.0 * Math.max(0.5f, qScale)) + 120.0;
                double halfH = (Math.abs(Math.sin(a)) * qWidth + Math.abs(Math.cos(a)) * qHeight)
                        / (2.0 * Math.max(0.5f, qScale)) + 120.0;
                double coordinatePerPixel = (double)(1L << (31 - Math.max(10, Math.min(19, qZoom)))) / 256.0;
                int left = clamp31((long)Math.floor(cx - halfW * coordinatePerPixel));
                int right = clamp31((long)Math.ceil(cx + halfW * coordinatePerPixel));
                int top = clamp31((long)Math.floor(cy - halfH * coordinatePerPixel));
                int bottom = clamp31((long)Math.ceil(cy + halfH * coordinatePerPixel));
                ArrayList<Feature> found = new ArrayList<>();
                int pointCount = 0;
                BinaryMapIndexReader.SearchFilter filter = (types, index) -> {
                    for (int i = 0; i < types.size(); i++) {
                        BinaryMapIndexReader.TagValuePair pair = index.decodeType(types.get(i));
                        if (pair == null || pair.tag == null) continue;
                        String tag = pair.tag;
                        if ("highway".equals(tag) || "natural".equals(tag) || "water".equals(tag)
                                || "waterway".equals(tag) || "landuse".equals(tag) || "leisure".equals(tag)
                                || "building".equals(tag) || "railway".equals(tag) || "aeroway".equals(tag)
                                || "amenity".equals(tag)) return true;
                    }
                    return false;
                };
                for (ReaderHandle handle : openReaders) {
                    List<BinaryMapDataObject> objects = handle.reader.searchMapIndex(
                            BinaryMapIndexReader.buildSearchRequest(left, right, top, bottom,
                                    Math.max(10, Math.min(19, qZoom)), filter));
                    if (objects == null) continue;
                    for (BinaryMapDataObject object : objects) {
                        if (found.size() >= MAX_FEATURES || pointCount >= MAX_POINTS) break;
                        Feature feature = convert(object);
                        if (feature == null) continue;
                        int pts = feature.xy.length / 2;
                        if (pts < 2 || pointCount + pts > MAX_POINTS) continue;
                        found.add(feature);
                        pointCount += pts;
                    }
                    if (found.size() >= MAX_FEATURES || pointCount >= MAX_POINTS) break;
                }
                found.sort(Comparator.comparingInt(f -> drawOrder(f.kind, f.area)));
                features = Collections.unmodifiableList(found);
                status = found.isEmpty() ? "NESSUN DATO OSMAND IN QUEST'AREA" : "MAPPA OSMAND OFFLINE";
                loadedSignature = signature;
            } catch (Throwable error) {
                status = "ERRORE LETTURA MAPPA OSMAND";
                android.util.Log.w("RoadDisplay", "Offline OBF map render failed", error);
                features = Collections.emptyList();
            } finally {
                loading = false;
            }
        });
    }

    private Feature convert(BinaryMapDataObject object) {
        if (object == null || object.getPointsLength() < 2 || object.getMapIndex() == null) return null;
        String highway = "", natural = "", water = "", waterway = "", landuse = "",
                leisure = "", building = "", railway = "", aeroway = "", amenity = "";
        int[] types = object.getTypes();
        if (types != null) {
            for (int type : types) {
                BinaryMapIndexReader.TagValuePair pair = object.getMapIndex().decodeType(type);
                if (pair == null || pair.tag == null) continue;
                switch (pair.tag) {
                    case "highway": highway = pair.value; break;
                    case "natural": natural = pair.value; break;
                    case "water": water = pair.value; break;
                    case "waterway": waterway = pair.value; break;
                    case "landuse": landuse = pair.value; break;
                    case "leisure": leisure = pair.value; break;
                    case "building": building = pair.value; break;
                    case "railway": railway = pair.value; break;
                    case "aeroway": aeroway = pair.value; break;
                    case "amenity": amenity = pair.value; break;
                }
            }
        }
        int kind;
        if (!highway.isEmpty()) {
            String h = highway.toLowerCase(Locale.ROOT);
            if (h.equals("motorway") || h.equals("motorway_link") || h.equals("trunk") || h.equals("trunk_link")) kind = MOTORWAY;
            else if (h.equals("primary") || h.equals("primary_link") || h.equals("secondary") || h.equals("secondary_link")) kind = MAJOR_ROAD;
            else if (h.equals("tertiary") || h.equals("tertiary_link") || h.equals("unclassified") || h.equals("residential")) kind = ROAD;
            else kind = MINOR_ROAD;
        } else if ("coastline".equals(natural) || "coastline_broken".equals(natural)) kind = COASTLINE;
        else if (!water.isEmpty() || "water".equals(natural) || !waterway.isEmpty()) kind = WATER;
        else if (!landuse.isEmpty() || "wood".equals(natural) || "forest".equals(natural)
                || "grassland".equals(natural) || "scrub".equals(natural) || "park".equals(leisure)) kind = GREEN;
        else if (!building.isEmpty() || !amenity.isEmpty() || !aeroway.isEmpty()) kind = BUILDING;
        else if (!railway.isEmpty()) kind = RAIL;
        else return null;

        int[] coords = object.getCoordinates();
        if (coords == null || coords.length < 4) return null;
        return new Feature(coords.clone(), kind, object.isArea());
    }

    private static int drawOrder(int kind, boolean area) {
        if (area) return kind == WATER ? 0 : kind == GREEN ? 1 : kind == BUILDING ? 2 : 3;
        switch (kind) {
            case WATER: case COASTLINE: return 4;
            case RAIL: return 5;
            case MINOR_ROAD: return 6;
            case ROAD: return 7;
            case MAJOR_ROAD: return 8;
            case MOTORWAY: return 9;
            default: return 10;
        }
    }

    private void ensureReaders(File[] maps, long signature) throws Exception {
        if (loadedSignature == signature && !openReaders.isEmpty()) return;
        closeReaders();
        ArrayList<ReaderHandle> opened = new ArrayList<>();
        for (File map : maps) {
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(map, "r");
                opened.add(new ReaderHandle(map, raf, new BinaryMapIndexReader(raf, map)));
            } catch (Exception e) {
                if (raf != null) try { raf.close(); } catch (Exception ignored) { }
            }
        }
        openReaders = opened;
        if (opened.isEmpty()) throw new IllegalStateException("Nessun file OBF apribile");
    }

    private synchronized void closeReaders() {
        for (ReaderHandle handle : openReaders) handle.close();
        openReaders.clear();
    }

    void close() {
        worker.execute(this::closeReaders);
        worker.shutdown();
    }

    private File[] installedMaps() {
        File external = app.getExternalFilesDir(null);
        File base = external != null ? external : app.getFilesDir();
        File dir = new File(base, "osmand/maps");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".obf")
                && new File(d, name).isFile() && new File(d, name).length() > 1024);
        return files == null ? new File[0] : files;
    }

    private static long signature(File[] files) {
        long s = 1125899906842597L;
        for (File f : files) {
            s = 31L * s + f.getName().hashCode();
            s = 31L * s + f.length();
            s = 31L * s + f.lastModified();
        }
        return s;
    }

    private static int clamp31(long v) {
        return (int)Math.max(0L, Math.min(2147483647L, v));
    }

    private static double bearingDelta(float a, float b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
