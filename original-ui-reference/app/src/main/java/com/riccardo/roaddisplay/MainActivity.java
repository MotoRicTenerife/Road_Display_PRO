package com.riccardo.roaddisplay;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.location.GnssStatus;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.PropertyFactory;

public class MainActivity extends Activity {
    static final int REQ_LOC = 42;
    RideView view;
    MapView vectorMapView;
    MapLibreMap vectorMap;
    volatile boolean vectorMapReady = false;
    volatile boolean vectorMapFailed = false;
    static final String VECTOR_MAP_STYLE = "https://tiles.openfreemap.org/styles/positron";
    LocationManager lm;
    LocationListener listener;
    FusedLocationProviderClient fusedLocationClient;
    LocationCallback fusedLocationCallback;
    boolean fusedLocationActive = false;
    RoadDb db;
    RoadEngine engine;
    TripDb tripDb;
    TripRecorder tripRecorder;
    TelemetryDb telemetryDb;
    SensorFusion sensorFusion;
    TelemetryRecorder telemetryRecorder;
    private final Object tripDbLock = new Object();
    private volatile boolean activityDestroyed = false;
    volatile boolean tripRecording = false;
    volatile long selectedTripId = -1L;
    volatile java.util.ArrayList<TripDb.Point> selectedTripPoints = new java.util.ArrayList<>();
    volatile TripDb.TripSummary selectedTripSummary;
    ToneGenerator tone;
    TextToSpeech navigationTts;
    volatile boolean navigationTtsReady = false;
    volatile int lastSpokenNavigationStep = -1;
    volatile int lastSpokenNavigationStage = -1; // 0=preparation, 1=confirmation, 2=action
    volatile long lastSpokenRoundaboutExitAt = -1L;
    volatile int lastSpokenSpeedLimitKmh = 0;
    volatile boolean awaitingNewSpeedLimitAfterUnknown = false;
    volatile long lastSpeedLimitVoiceAt = 0L;
    boolean resumePromptShown = false;
    boolean audio = true;                 // Audio is enabled from launch; no activation overlay.
    boolean tripsScreen = false;
    boolean tripDayScreen = false;
    String selectedTripDayKey = "";
    final RectF gpxButtonBounds = new RectF();
    final RectF memoryButtonBounds = new RectF();
    static final int REQ_IMPORT_GPX = 5101;
    static final int REQ_EXPORT_GPX = 5102;
    private File exportGpxSource;
    long exportTripId = -1L;
    boolean navigationActive = false;
    volatile boolean navigationLoading = false;
    volatile String navigationStatus = "";
    volatile String navigationInstruction = "";
    volatile String navigationDistance = "";
    volatile String navigationDestinationDistance = "";
    volatile double navigationDestLat = Double.NaN, navigationDestLon = Double.NaN;
    volatile String navigationDestinationLabel = "";
    volatile String navigationDestinationLabelOverride = "";
    volatile long navigationLastRouteAt = 0L;
    volatile boolean navigationRerouteInProgress = false;
    volatile long navigationLastRerouteAt = 0L;
    volatile long navigationOffRouteSince = 0L;
    volatile float navigationRouteDeviation = 0f;
    // Route-following state: progress is measured along the routed polyline, not
    // by straight-line distance to a maneuver point. This prevents stale steps
    // when the GPS fix misses the exact maneuver coordinate.
    volatile double navigationRouteProgressM = 0.0;
    int navigationRouteSegmentIndex = 0;
    volatile double navigationMatchedLat = Double.NaN, navigationMatchedLon = Double.NaN;
    volatile float navigationMatchedBearing = Float.NaN;
    volatile long navigationLastFixAt = 0L;
    final ArrayList<Double> navigationStepProgressM = new ArrayList<>();
    volatile boolean lastCurveWarningValid = false;
    volatile float lastCurveWarningDistance = Float.MAX_VALUE;
    volatile long lastCurveRouteFetchAt = 0L;
    volatile boolean curveRouteFetchInProgress = false;
    final ArrayList<RoutePoint> curvePreviewRoute = new ArrayList<>();
    static final class RoutePoint {
        final double lat, lon;
        RoutePoint(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
    static final class RouteStep {
        double lat, lon, distance;
        String instruction, roadName, maneuverType, nextRoadName, voicePre, voicePost;
        int roundaboutExit;
        double routeProgressM = Double.NaN, endRouteProgressM = Double.NaN;
        int beginShapeIndex = -1, endShapeIndex = -1;
        boolean guidanceRelevant = true;
        RouteStep(double lat, double lon, double distance, String instruction, String roadName, String maneuverType, int roundaboutExit) {
            this.lat = lat; this.lon = lon; this.distance = distance;
            this.instruction = instruction;
            this.roadName = roadName == null ? "" : roadName;
            this.maneuverType = maneuverType == null ? "" : maneuverType;
            this.roundaboutExit = roundaboutExit;
            this.nextRoadName = "";
            this.voicePre = "";
            this.voicePost = "";
        }
    }

    static final class NavigationRouteResult {
        final ArrayList<RoutePoint> route = new ArrayList<>();
        final ArrayList<RouteStep> steps = new ArrayList<>();
        double distanceM = 0.0;
        double durationSec = 0.0;
        String provider = "";
    }
    static final class DestinationCandidate {
        final String title, subtitle, source;
        final double lat, lon, distanceM;
        DestinationCandidate(String title, String subtitle, double lat, double lon, double distanceM, String source) {
            this.title=title; this.subtitle=subtitle; this.lat=lat; this.lon=lon; this.distanceM=distanceM; this.source=source;
        }
    }
    final Object navigationLock = new Object();
    final RouteMapMatcher routeMapMatcher = new RouteMapMatcher();
    final ArrayList<RoutePoint> navigationRoute = new ArrayList<>();
    final ArrayList<RouteStep> navigationSteps = new ArrayList<>();
    int navigationStepIndex = 0;

    // ETA engine: route geometry + OSM speed limits (Valhalla locate) with a safe
    // router-duration fallback when a segment has no reliable limit.
    static final class EtaSample {
        double distanceM;
        int speedLimitKmh;
        EtaSample(double distanceM, int speedLimitKmh) { this.distanceM = distanceM; this.speedLimitKmh = speedLimitKmh; }
    }
    final Object etaLock = new Object();
    final ArrayList<Double> navigationRouteCumM = new ArrayList<>();
    final ArrayList<EtaSample> navigationEtaSamples = new ArrayList<>();
    volatile double navigationRouteDistanceM = 0.0;
    volatile double navigationRouteBaseDurationSec = 0.0;
    volatile boolean navigationEtaLoading = false;
    volatile boolean navigationEtaReady = false;
    volatile long navigationEtaEpochMs = 0L;
    volatile long navigationEtaRemainingSec = 0L;
    volatile double navigationEtaProgressM = 0.0;
    volatile float navigationObservedFactor = 1.0f;
    volatile long navigationStartedAt = 0L;
    volatile double navigationStartedProgressM = 0.0;
    String installDate = "";
    volatile boolean downloadingRoads = false;
    volatile boolean roadDownloadAttempted = false;
    int satellitesVisible = 0;
    int satellitesUsed = 0;
    GnssStatus.Callback gnssCallback;
    long lastOnline = 0;
    Location previousMapLocation;
    long lastUiLocationRefreshAt = 0L;
    Location lastUiRefreshLocation;
    double lastRoadDownloadLat = Double.NaN, lastRoadDownloadLon = Double.NaN;
    boolean lastRoadDownloadGpsAccurate = false;
    float mapBearing = 0f;
    boolean hasMapBearing = false;
    volatile long lastWeatherAt = 0L;
    // Public Nominatim geocoding is used only for explicit user requests; enforce its 1 req/s limit.
    volatile long lastNominatimGeocodeAt = 0L;
    volatile double lastWeatherLat = Double.NaN, lastWeatherLon = Double.NaN;
    volatile boolean weatherLoading = false;
    volatile String weatherSummary = "METEO IN ATTESA";
    volatile String weatherPlaceName = "";
    volatile String weatherDetail = "";
    volatile boolean weatherAdverse = false;
    volatile String weatherHourlySummary = "";
    volatile JSONObject latestWeatherHourlyObject = null;
    volatile String destinationWeatherSummary = "";
    volatile String destinationWeatherHourlySummary = "";
    volatile boolean destinationWeatherLoading = false;
    volatile boolean showWeatherDetails = false;
    volatile int weatherExpandedBand = -1;
    volatile boolean approximateLocation = false;
    volatile boolean gpsSignalLost = true;
    volatile long lastAccurateGpsAt = 0L;
    volatile long lastUsableGpsAt = 0L;
    volatile long lastGnssFixAt = 0L;
    volatile Location lastGnssFix = null;
    // GNSS quality tiers: a strong fix remains the safety gate, while a still-real
    // but weaker GNSS fix keeps the UI/navigation state alive instead of falsely
    // declaring GPS loss. Android's GPS_PROVIDER represents the device GNSS receiver,
    // which may use multiple constellations supported by the handset.
    // Location quality is deliberately split into three levels. A fresh fused
    // location with moderate accuracy is still a real usable position; it must
    // never be presented as GPS loss merely because it did not come directly
    // from LocationManager.GPS_PROVIDER. Road/curve processing remains stricter.
    static final float GNSS_STRONG_ACCURACY_M = 50f;
    static final float GNSS_USABLE_ACCURACY_M = 100f;
    static final long GNSS_STRONG_MAX_AGE_MS = 3500L;
    static final long GNSS_USABLE_MAX_AGE_MS = 10000L;
    static final long GNSS_LOSS_TIMEOUT_MS = 15000L;
    final android.os.Handler gpsHealthHandler = new android.os.Handler(Looper.getMainLooper());
    final Runnable gpsHealthCheck = new Runnable() { @Override public void run() {
        long now = System.currentTimeMillis();
        if (lastUsableGpsAt == 0L || now - lastUsableGpsAt > GNSS_LOSS_TIMEOUT_MS) {
            if (!gpsSignalLost) {
                gpsSignalLost = true;
                currentSpeedLimitKmh = 0;
                if (engine != null) { engine.current = new Curve(); engine.noRoad = true; }
                if (!activityDestroyed && view != null) view.invalidate();
            }
        }
        gpsHealthHandler.postDelayed(this, 1000L);
    }};
        volatile String roadDataStatus = "";
    volatile String airTemperature = "--°";
    volatile float deviceAmbientTemperature = Float.NaN;
    volatile boolean hasDeviceAmbientTemperature = false;
    SensorManager temperatureSensorManager;
    Sensor ambientTemperatureSensor;
    SensorEventListener ambientTemperatureListener;
    double dailyKm = 0.0;
    volatile int currentSpeedLimitKmh = 0;
    boolean speedLimitAlerted = false;
    boolean radarAlerted = false;
    volatile String radarAlertMessage = "RADAR";
    String activeRadarId = "";
    int radarAlertStage = 0; // 0=not armed, 1=early alert sent, 2=100m sent, 3=50m sent, 4=10m sent
    long lastRadarStageAt = 0L;
    double activeRadarStartDistance = 0.0;
    long lastRadarSyncAttemptAt = 0L;
    volatile long radarAlertUntil = 0L;
    volatile int radarFlashToggles = 0;
    Location lastDistanceLocation;
    String dailyDistanceDate = "";
    SensorManager lightSensorManager;
    Sensor lightSensor;
    SensorEventListener lightSensorListener;
    volatile float currentAmbientLux = Float.NaN;
    volatile int displayThemeMode = 0; // 0=auto, 1=dark, 2=negative
    volatile boolean negativeTheme = false;
    static final float SUNLIGHT_ENTER_LUX = 12500f;
    static final float SUNLIGHT_EXIT_LUX = 6000f;
    float originalWindowBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
    boolean brightnessCaptured = false;
    static final String PREF_HOME_ADDRESS = "home_address";
    static final String PREF_NAV_RESUME = "nav_resume";
    static final String PREF_NAV_RESUME_LAT = "nav_resume_lat";
    static final String PREF_NAV_RESUME_LON = "nav_resume_lon";
    static final String PREF_NAV_RESUME_LABEL = "nav_resume_label";
    static final String PREF_HOME_LAT = "home_lat";
    static final String PREF_HOME_LON = "home_lon";
    static final String PREF_RECENT_DESTINATIONS = "recent_destinations";
    static final String PREF_SENSOR_CALIBRATION = "sensor_calibration";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Adjust only this Activity's window brightness using the ambient-light sensor;
        // restore the previous window setting when the app leaves the foreground.
        originalWindowBrightness = getWindow().getAttributes().screenBrightness;
        brightnessCaptured = true;
        android.content.SharedPreferences displayPrefs = getSharedPreferences("road_display", MODE_PRIVATE);
        displayThemeMode = Math.max(0, Math.min(2, displayPrefs.getInt("display_theme_mode", 0)));
        negativeTheme = displayThemeMode == 2;
        lightSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (lightSensorManager != null) lightSensor = lightSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        temperatureSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (temperatureSensorManager != null) {
            ambientTemperatureSensor = temperatureSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            if (ambientTemperatureSensor != null) {
                ambientTemperatureListener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent event) {
                        if (event == null || event.values == null || event.values.length == 0) return;
                        float t = event.values[0];
                        if (t > -40f && t < 80f) {
                            deviceAmbientTemperature = t;
                            hasDeviceAmbientTemperature = true;
                            if (!activityDestroyed && view != null) view.postInvalidate();
                        }
                    }
                    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
                };
                temperatureSensorManager.registerListener(ambientTemperatureListener, ambientTemperatureSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        lightSensorListener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                if (event == null || event.values == null || event.values.length == 0) return;
                float lux = Math.max(0f, event.values[0]);
                currentAmbientLux = lux;
                final float target = lux < 3f ? 0.25f : lux < 20f ? 0.40f : lux < 80f ? 0.65f : lux < 400f ? 0.88f : 1.0f;
                runOnUiThread(() -> {
                    setWindowBrightness(target);
                    if (displayThemeMode == 0) {
                        boolean next = negativeTheme ? lux > SUNLIGHT_EXIT_LUX : lux >= SUNLIGHT_ENTER_LUX;
                        if (next != negativeTheme) { negativeTheme = next; if (!activityDestroyed && view != null) view.invalidate(); }
                    }
                });
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        immersive();
        android.content.SharedPreferences prefs = getSharedPreferences("road_display", MODE_PRIVATE);
        installDate = prefs.getString("install_date", "");
        if (installDate.isEmpty()) {
            long firstInstall = System.currentTimeMillis();
            try { firstInstall = getPackageManager().getPackageInfo(getPackageName(), 0).firstInstallTime; } catch (Exception ignored) { }
            installDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(firstInstall));
            prefs.edit().putString("install_date", installDate).apply();
        }
        db = new RoadDb(this);
        engine = new RoadEngine(db);
        tripDb = new TripDb(this);
        tripDb.recoverInterruptedTrips(System.currentTimeMillis());
        telemetryDb = new TelemetryDb(this);
        sensorFusion = new SensorFusion(this, sample -> {
            if (telemetryRecorder != null) telemetryRecorder.onFusion(sample);
        });
        telemetryRecorder = new TelemetryRecorder(telemetryDb, sensorFusion);
        sensorFusion.start();
        tripRecorder = new TripRecorder(tripDb, new TripRecorder.Listener() {
            @Override public void onTripStateChanged(boolean recording, long tripId) {
                tripRecording = recording;
                if (telemetryRecorder != null) { if (recording) telemetryRecorder.start(); else telemetryRecorder.stop(); }
                if (!activityDestroyed && view != null) view.postInvalidate();
            }
        });
        tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        navigationTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = navigationTts.setLanguage(new Locale("it", "IT"));
                navigationTtsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                navigationTts.setSpeechRate(0.95f);
                navigationTts.setPitch(1.0f);
            }
        });
        view = new RideView(this);
        initializeVectorMap(b);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        if (vectorMapView != null) {
            root.addView(vectorMapView, new android.widget.FrameLayout.LayoutParams(-1, -1));
            vectorMapView.setVisibility(View.GONE);
        }
        root.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        requestLocation();
        gpsHealthHandler.post(gpsHealthCheck);
        new Handler(Looper.getMainLooper()).postDelayed(this::maybeOfferNavigationResume, 1800L);
    }

    void initializeVectorMap(Bundle state) {
        try {
            // MapLibre is isolated behind a hard fallback: if the native renderer,
            // style, or device GPU cannot initialize, the proven Canvas/raster map
            // remains available and the Activity must not crash.
            MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre);
            vectorMapView = new MapView(this);
            vectorMapView.onCreate(state);
            vectorMapView.getMapAsync(new OnMapReadyCallback() {
                @Override public void onMapReady(MapLibreMap map) {
                    vectorMap = map;
                    map.setStyle(VECTOR_MAP_STYLE, style -> {
                        // Keep the provider's light, fully styled cartography. Recoloring
                        // every road layer black erased road hierarchy and made the map
                        // visually collapse into a black road mesh. The GPS marker and
                        // radar symbols remain overlaid by RideView.
                        vectorMapReady = true;
                        vectorMapFailed = false;
                        syncVectorMapCamera(false);
                        if (view != null) view.postInvalidate();
                    });
                    vectorMapView.addOnCameraIsChangingListener(() -> syncVectorMapStateFromCamera());
                    vectorMapView.addOnCameraDidChangeListener(animated -> syncVectorMapStateFromCamera());
                }
            });
        } catch (Throwable t) {
            vectorMapFailed = true;
            vectorMapReady = false;
            vectorMap = null;
            if (vectorMapView != null) {
                try { vectorMapView.onDestroy(); } catch (Throwable ignored) {}
                vectorMapView = null;
            }
        }
    }

    void applyHighContrastMapStyle(org.maplibre.android.maps.Style style) {
        if (style == null) return;
        for (Layer layer : style.getLayers()) {
            if (layer == null) continue;
            String id = layer.getId() == null ? "" : layer.getId().toLowerCase(Locale.ROOT);
            if (layer instanceof BackgroundLayer) {
                ((BackgroundLayer) layer).setProperties(PropertyFactory.backgroundColor(Color.WHITE));
            } else if (layer instanceof FillLayer) {
                // A white base removes the dark land/water fills of the source style.
                ((FillLayer) layer).setProperties(
                        PropertyFactory.fillColor(Color.WHITE),
                        PropertyFactory.fillOutlineColor(Color.BLACK));
            } else if (layer instanceof LineLayer) {
                // Road/transport layers are black. Non-road linework is kept softer
                // so borders and hydrography do not overpower the road network.
                boolean road = id.contains("transportation") || id.contains("road")
                        || id.contains("street") || id.contains("highway")
                        || id.contains("motorway") || id.contains("bridge")
                        || id.contains("tunnel") || id.contains("path")
                        || id.contains("ferry");
                ((LineLayer) layer).setProperties(PropertyFactory.lineColor(road ? Color.BLACK : 0xFF777777));
            } else if (layer instanceof SymbolLayer) {
                // Only recolor text; preserve icon-image and icon-color styling.
                ((SymbolLayer) layer).setProperties(
                        PropertyFactory.textColor(Color.BLACK),
                        PropertyFactory.textHaloColor(Color.WHITE),
                        PropertyFactory.textHaloWidth(1.2f));
            }
        }
    }

    void syncVectorMapStateFromCamera() {
        if (!vectorMapReady || vectorMap == null || view == null || !view.mapExpanded) return;
        try {
            CameraPosition cp = vectorMap.getCameraPosition();
            if (cp.target != null) {
                view.mapViewLat = cp.target.getLatitude();
                view.mapViewLon = cp.target.getLongitude();
            }
            view.mapZoom = (float)Math.max(0.5, Math.min(1.7, (cp.zoom - 15.0) / 2.2 + 0.8));
            view.mapFrozenBearing = view.normalizeBearing((float)cp.bearing);
            view.mapUserPanned = true;
            view.postInvalidateOnAnimation();
        } catch (Throwable ignored) {}
    }

    void syncVectorMapCamera(boolean animate) {
        if (!vectorMapReady || vectorMap == null || view == null) return;
        double lat = Double.isNaN(view.mapViewLat) ? (engine.loc == null ? 0.0 : engine.loc.getLatitude()) : view.mapViewLat;
        double lon = Double.isNaN(view.mapViewLon) ? (engine.loc == null ? 0.0 : engine.loc.getLongitude()) : view.mapViewLon;
        double bearing = view.mapFrozenBearing;
        double zoom = 15.0 + (view.mapZoom - 0.8) * 2.2;
        zoom = Math.max(12.0, Math.min(19.0, zoom));
        CameraPosition cp = new CameraPosition.Builder()
                .target(new LatLng(lat, lon))
                .zoom(zoom)
                .bearing(bearing)
                .tilt(0.0)
                .build();
        try {
            if (animate) vectorMap.setCameraPosition(cp); else vectorMap.setCameraPosition(cp);
        } catch (Throwable ignored) {}
    }

    void setVectorMapExpanded(boolean expanded) {
        if (vectorMapView == null) return;
        try {
            vectorMapView.setVisibility(expanded && vectorMapReady ? View.VISIBLE : View.GONE);
            if (expanded && vectorMapReady) syncVectorMapCamera(false);
        } catch (Throwable ignored) {}
    }

    @Override protected void onStart() {
        super.onStart();
        if (vectorMapView != null) { try { vectorMapView.onStart(); } catch (Throwable ignored) {} }
    }
    @Override protected void onStop() {
        if (vectorMapView != null) { try { vectorMapView.onStop(); } catch (Throwable ignored) {} }
        super.onStop();
    }
    @Override public void onLowMemory() {
        super.onLowMemory();
        if (vectorMapView != null) { try { vectorMapView.onLowMemory(); } catch (Throwable ignored) {} }
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (vectorMapView != null) { try { vectorMapView.onLowMemory(); } catch (Throwable ignored) {} }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && view != null) {
            view.clearTileMemoryCache();
        }
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (vectorMapView != null) { try { vectorMapView.onSaveInstanceState(outState); } catch (Throwable ignored) {} }
    }

    void immersive() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    void requestLocation() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        startGps();
    }

    void startGps() {
        if (fusedLocationActive || lm != null) return;

        // Primary source: Google's Fused Location Provider. Android's own
        // documentation recommends FLP for continuous driving/navigation updates;
        // it combines the available location sources and avoids making the app
        // depend on the provider string being exactly "gps".
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationCallback = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    for (Location l : result.getLocations()) processLocation(l);
                }
            };
            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                    .setMinUpdateIntervalMillis(500L)
                    .setMinUpdateDistanceMeters(1.0f)
                    .setWaitForAccurateLocation(false)
                    .setMaxUpdateDelayMillis(2000L)
                    .build();
            fusedLocationClient.requestLocationUpdates(request, fusedLocationCallback, Looper.getMainLooper());
            fusedLocationActive = true;

            // Seed the UI quickly from the fused cache. This is only a seed: all
            // safety/navigation decisions still require a fresh live fix.
            fusedLocationClient.getLastLocation().addOnSuccessListener(l -> {
                if (l != null) processLocation(l);
            });
        } catch (SecurityException ignored) {
            fusedLocationActive = false;
        } catch (Exception ignored) {
            fusedLocationActive = false;
        }

        // Keep the platform LocationManager as a fallback. It is also used for
        // GNSS satellite status, which is useful for diagnostics on Samsung devices.
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location l) { processLocation(l); }
            @Override public void onProviderEnabled(String p) { if (!activityDestroyed && view != null) view.invalidate(); }
            @Override public void onProviderDisabled(String p) {
                if (LocationManager.GPS_PROVIDER.equals(p) && !fusedLocationActive) gpsSignalLost = true;
                if (!activityDestroyed && view != null) view.invalidate();
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) { if (!activityDestroyed && view != null) view.invalidate(); }
        };
        if (Build.VERSION.SDK_INT >= 24) {
            gnssCallback = new GnssStatus.Callback() {
                @Override public void onSatelliteStatusChanged(GnssStatus status) {
                    int visible = 0, used = 0;
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        if (status.usedInFix(i)) used++;
                        visible++;
                    }
                    satellitesVisible = visible;
                    satellitesUsed = used;
                    if (!activityDestroyed && view != null) view.invalidate();
                }
            };
            try { lm.registerGnssStatusCallback(gnssCallback, new android.os.Handler(Looper.getMainLooper())); }
            catch (Exception ignored) { }
        }
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, listener, Looper.getMainLooper()); }
        catch (SecurityException ignored) { }
        catch (Exception ignored) { }

        // Network/passive are retained only as fallback/seed sources. They are
        // never allowed to override a better fresh fused/GNSS position.
        try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 250, listener, Looper.getMainLooper()); }
        catch (SecurityException ignored) { }
        catch (Exception ignored) { }
        try { lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 15000, 500, listener, Looper.getMainLooper()); }
        catch (SecurityException ignored) { }
        catch (Exception ignored) { }

        // Platform cache remains a final fallback if Google Play services has no cache.
        Location best = null;
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
        for (String provider : providers) {
            try {
                Location candidate = lm.getLastKnownLocation(provider);
                if (candidate != null && (best == null ||
                        (candidate.hasAccuracy() && (!best.hasAccuracy() || candidate.getAccuracy() < best.getAccuracy())) ||
                        candidate.getTime() > best.getTime() + 120000L)) best = candidate;
            } catch (SecurityException ignored) { }
            catch (Exception ignored) { }
        }
        if (best != null) processLocation(best);
        else {
            Location saved = loadSavedLocation();
            if (saved != null) processLocation(saved);
        }
    }

    void processLocation(Location l) {
        if (l == null) return;
        long nowMs = System.currentTimeMillis();
        long locationAgeMs = l.hasAccuracy() ? Math.max(0L, nowMs - l.getTime()) : Long.MAX_VALUE;
        String provider = l.getProvider() == null ? "" : l.getProvider().toLowerCase(Locale.ROOT);
        boolean isPlatformGnss = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        boolean isFused = "fused".equals(provider);
        boolean hasReasonableAccuracy = l.hasAccuracy() && Float.isFinite(l.getAccuracy())
                && l.getAccuracy() >= 0f && l.getAccuracy() <= GNSS_USABLE_ACCURACY_M;
        boolean freshLive = (isPlatformGnss || isFused) && locationAgeMs <= GNSS_LOSS_TIMEOUT_MS;
        if (freshLive) {
            lastGnssFixAt = nowMs;
            // Prefer a fresh fused/GNSS fix over a cached/network position.
            if (hasReasonableAccuracy || lastGnssFix == null) lastGnssFix = new Location(l);
        }
        boolean strongGps = (isPlatformGnss || isFused) && l.hasAccuracy()
                && l.getAccuracy() <= GNSS_STRONG_ACCURACY_M
                && locationAgeMs <= GNSS_STRONG_MAX_AGE_MS;
        boolean usableGps = (isPlatformGnss || isFused) && hasReasonableAccuracy
                && locationAgeMs <= GNSS_USABLE_MAX_AGE_MS;
        if (usableGps) {
            lastUsableGpsAt = nowMs;
            gpsSignalLost = false;
        }
        if (strongGps) lastAccurateGpsAt = nowMs;
        // Keep the existing safety semantics: only a strong GNSS fix unlocks
        // road/speed-limit processing. A weaker but real GNSS fix remains visible
        // as GPS DEBOLE rather than being mislabeled as GPS INSUFFICIENTE.
        boolean accurateGps = strongGps;
        approximateLocation = !strongGps && !usableGps;
        updateMapBearing(l);
        if (accurateGps) {
            updateDailyDistance(l);
            if (sensorFusion != null) sensorFusion.updateGps(l);
            if (tripRecorder != null) tripRecorder.accept(l);
            // Navigation is deliberately turn-by-turn only for now. Curve prediction
            // is disabled: it was not reliable enough to be safety-relevant. The router
            // remains authoritative for turns and roundabouts.
            engine.update(l);
            currentSpeedLimitKmh = engine.speedLimitKmh(l);
            // Do not carry a limit sampled from a previous route segment into the
            // current road. The route profile is useful for ETA, not authoritative
            // enough to announce the legal limit at the rider's current position.
            float actualKmh = l.hasSpeed() && Float.isFinite(l.getSpeed()) ? l.getSpeed() * 3.6f : 0f;
            RadarApproach radarApproach = engine.nearestRadar(l);
            double radarDistance = radarApproach == null ? Double.POSITIVE_INFINITY : radarApproach.distanceMeters;
            updateRadarAlerts(radarApproach, actualKmh, currentSpeedLimitKmh, nowMs);
            if (currentSpeedLimitKmh > 0) {
                if (currentSpeedLimitKmh != lastSpokenSpeedLimitKmh) {
                    final int lim = currentSpeedLimitKmh;
                    final boolean enteringKnownZone = lastSpokenSpeedLimitKmh > 0 || awaitingNewSpeedLimitAfterUnknown;
                    awaitingNewSpeedLimitAfterUnknown = false;
                    lastSpokenSpeedLimitKmh = lim;
                    lastSpeedLimitVoiceAt = nowMs;
                    if (audio && navigationTtsReady && navigationTts != null) {
                        final String announcement = enteringKnownZone
                                ? "Inizio del limite di velocità, " + lim + " chilometri orari."
                                : "Limite di velocità, " + lim + " chilometri orari.";
                        runOnUiThread(() -> { try { navigationTts.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "speed-limit-zone-" + lim); } catch (Exception ignored) { } });
                    }
                }
                if (actualKmh > currentSpeedLimitKmh + 3f) {
                    if (!speedLimitAlerted) {
                        beepSpeedLimit(); speedLimitAlerted = true;
                        if (audio && navigationTtsReady && navigationTts != null && nowMs - lastSpeedLimitVoiceAt > 8000L) {
                            final int lim = currentSpeedLimitKmh; lastSpeedLimitVoiceAt = nowMs;
                            runOnUiThread(() -> { try { navigationTts.speak("Attenzione, limite " + lim + " chilometri orari.", TextToSpeech.QUEUE_FLUSH, null, "speed-limit-over"); } catch (Exception ignored) { } });
                        }
                    }
                } else if (actualKmh <= currentSpeedLimitKmh - 3f) speedLimitAlerted = false;
            } else {
                speedLimitAlerted = false;
                if (lastSpokenSpeedLimitKmh > 0) {
                    awaitingNewSpeedLimitAfterUnknown = true;
                    if (audio && navigationTtsReady && navigationTts != null) {
                        lastSpeedLimitVoiceAt = nowMs;
                        runOnUiThread(() -> { try { navigationTts.speak("Fine del limite di velocità noto. Nuovo limite non disponibile.", TextToSpeech.QUEUE_FLUSH, null, "speed-limit-unknown"); } catch (Exception ignored) { } });
                    }
                }
                lastSpokenSpeedLimitKmh = 0;
            }
            if (telemetryRecorder != null && tripRecording) telemetryRecorder.onLocation(l, null, currentSpeedLimitKmh, engine.matchedWayId);
        } else {
            // Approximate/network locations are for map loading only, never curve or speed advice.
            // Never keep an old speed limit alive after GPS quality is lost.
            currentSpeedLimitKmh = 0;
            speedLimitAlerted = false;
            engine.loc = new Location(l);
            engine.current = new Curve();
            engine.noRoad = true;
            engine.reloadRoads(l);
        }
        if (!"ip-approx".equals(l.getProvider())) saveLocation(l);
        // Coalesce GPS callbacks: avoid redrawing the entire dashboard/map for every tiny fix.
        long uiNow = System.currentTimeMillis();
        float uiMoved = lastUiRefreshLocation == null ? Float.MAX_VALUE : lastUiRefreshLocation.distanceTo(l);
        if (view != null && (lastUiLocationRefreshAt == 0L ||
                (uiNow - lastUiLocationRefreshAt >= 750L && uiMoved >= 2f) ||
                uiNow - lastUiLocationRefreshAt >= 1500L)) {
            lastUiLocationRefreshAt = uiNow;
            lastUiRefreshLocation = new Location(l);
            view.invalidate();
        }
        long now = System.currentTimeMillis();
        boolean firstRoadLoad = !roadDownloadAttempted;
        boolean movedFar = !Double.isNaN(lastRoadDownloadLat) &&
                distanceMeters(lastRoadDownloadLat, lastRoadDownloadLon, l.getLatitude(), l.getLongitude()) >= 8000.0;
        boolean gpsQualityChanged = accurateGps && !lastRoadDownloadGpsAccurate;
        boolean retryFailed = roadDownloadAttempted && (engine.mapWays == null || engine.mapWays.isEmpty()) && now - lastOnline >= 120000L;
        boolean cacheExpired = now - lastOnline >= 6L * 60L * 60L * 1000L;
        boolean radarRefreshDue = engine.db != null && engine.db.radarSyncDue(l.getLatitude(), l.getLongitude(), now) && now - lastRadarSyncAttemptAt >= 2L * 60L * 60L * 1000L;
        // Road data refreshes more frequently; radar coverage also has an explicit persisted 24-hour freshness check.
        if (!downloadingRoads && (firstRoadLoad || movedFar || gpsQualityChanged || retryFailed || cacheExpired || radarRefreshDue)) {
            lastOnline = now;
            if (radarRefreshDue) lastRadarSyncAttemptAt = now;
            lastRoadDownloadLat = l.getLatitude(); lastRoadDownloadLon = l.getLongitude(); lastRoadDownloadGpsAccurate = accurateGps;
            downloadingRoads = true;
            roadDownloadAttempted = true;
            roadDataStatus = tr("SCARICO STRADE…");
            Location requestLocation = new Location(l);
            new Thread(() -> {
                boolean ok = false;
                try {
                    ok = engine.fetchOnline(requestLocation);
                    engine.reloadRoads(requestLocation);
                    roadDataStatus = ok || (engine.mapWays != null && !engine.mapWays.isEmpty())
                            ? "" : tr("DATI STRADALI NON DISPONIBILI");
                } finally {
                    downloadingRoads = false;
                    if (!activityDestroyed && view != null) view.postInvalidate();
                }
            }, "road-data-download").start();
        }
        // Weather is strictly GPS-based: never use IP/network approximations for weather data.
        if (accurateGps) maybeUpdateWeather(l, false);
        if (accurateGps && view != null) view.updateNavigation(l);
    }

    void requestCurvePreview(Location l) {
        long now=System.currentTimeMillis();
        if(curveRouteFetchInProgress||now-lastCurveRouteFetchAt<20000L)return;
        curveRouteFetchInProgress=true; lastCurveRouteFetchAt=now;
        final Location start=new Location(l);
        double distanceAhead=1200.0;
        double lat2=start.getLatitude() + (distanceAhead/110540.0)*Math.cos(Math.toRadians(start.getBearing()));
        double lon2=start.getLongitude() + (distanceAhead/(111320.0*Math.max(0.2,Math.cos(Math.toRadians(start.getLatitude())))))*Math.sin(Math.toRadians(start.getBearing()));
        new Thread(() -> {
            HttpURLConnection c=null;
            try{
                String u="https://router.project-osrm.org/route/v1/driving/"+start.getLongitude()+","+start.getLatitude()+";"+lon2+","+lat2+
                        "?overview=full&geometries=geojson&steps=false&alternatives=false&continue_straight=true";
                c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(12000);c.setRequestMethod("GET");
                c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","RoadDisplayPRO/3.22.17 (Android motorcycle dashboard)");
                if(c.getResponseCode()!=HttpURLConnection.HTTP_OK)throw new Exception("HTTP");
                JSONObject root=new JSONObject(readConnection(c));JSONArray rs=root.optJSONArray("routes");
                if(rs==null||rs.length()==0)throw new Exception("no route");
                JSONObject g=rs.getJSONObject(0).optJSONObject("geometry");JSONArray pts=g==null?null:g.optJSONArray("coordinates");
                if(pts==null||pts.length()<4)throw new Exception("no geometry");
                ArrayList<RoutePoint> preview=new ArrayList<>();
                for(int i=0;i<pts.length();i++){JSONArray p=pts.getJSONArray(i);if(p.length()>=2)preview.add(new RoutePoint(p.getDouble(1),p.getDouble(0)));}
                synchronized(curvePreviewRoute){curvePreviewRoute.clear();curvePreviewRoute.addAll(preview);}
                Curve q=engine.findCurveOnRoute(start,curvePreviewRoute);
                if(q.valid){engine.current=q;lastCurveWarningValid=true;lastCurveWarningDistance=q.distance;}
                if(!activityDestroyed && view!=null)if (!activityDestroyed && view != null) view.postInvalidate();
            }catch(Exception ignored){}finally{if(c!=null)try{c.disconnect();}catch(Exception ignored){}curveRouteFetchInProgress=false;}
        },"curve-preview-route").start();
    }

    void updateDailyDistance(Location l) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        android.content.SharedPreferences prefs = getSharedPreferences("road_display", MODE_PRIVATE);
        if (!today.equals(dailyDistanceDate)) {
            if (!dailyDistanceDate.isEmpty()) prefs.edit().putLong("trip_" + dailyDistanceDate, Double.doubleToLongBits(dailyKm)).apply();
            dailyDistanceDate = today;
            dailyKm = Double.longBitsToDouble(prefs.getLong("trip_" + today, Double.doubleToLongBits(0.0)));
            lastDistanceLocation = null;
        }
        if (lastDistanceLocation != null) {
            float delta = lastDistanceLocation.distanceTo(l);
            long age = Math.abs(l.getTime() - lastDistanceLocation.getTime());
            // Ignore GPS jumps and stale fixes; count only plausible movement between fresh fixes.
            if (delta >= 2f && delta <= 2000f && age <= 120000L) {
                dailyKm += delta / 1000.0;
                prefs.edit().putString("daily_date", today).putLong("daily_km_bits", Double.doubleToLongBits(dailyKm)).putLong("trip_" + today, Double.doubleToLongBits(dailyKm)).apply();
            }
        }
        lastDistanceLocation = new Location(l);
    }

    void saveLocation(Location l) {
        try {
            getSharedPreferences("road_display", MODE_PRIVATE).edit()
                    .putString("last_lat", Double.toString(l.getLatitude()))
                    .putString("last_lon", Double.toString(l.getLongitude()))
                    .putLong("last_time", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    Location loadSavedLocation() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("road_display", MODE_PRIVATE);
            String lat = prefs.getString("last_lat", null), lon = prefs.getString("last_lon", null);
            if (lat == null || lon == null) return null;
            Location saved = new Location("saved-network");
            saved.setLatitude(Double.parseDouble(lat)); saved.setLongitude(Double.parseDouble(lon));
            saved.setAccuracy(5000f); saved.setTime(prefs.getLong("last_time", System.currentTimeMillis()));
            return saved;
        } catch (Exception ignored) { return null; }
    }

    String tr(String it) {
        String lang = Locale.getDefault().getLanguage();
        if ("it".equals(lang) || lang == null) return it;
        String es = "es".equals(lang) ? "es" : "en";
        String[][] rows = {
            {"GPS IN RICERCA","GPS BUSCANDO","GPS SEARCHING"}, {"GPS DEBOLE","GPS DÉBIL","WEAK GPS"},
            {"GPS OK","GPS OK","GPS OK"}, {"GPS OTTIMO","GPS EXCELENTE","EXCELLENT GPS"}, {"GPS BUONO","GPS BUENO","GOOD GPS"}, {"GPS DISCRETO","GPS ACEPTABLE","FAIR GPS"}, {"GPS MOLTO DEBOLE","GPS MUY DÉBIL","VERY WEAK GPS"}, {"DATI STRADALI IN ATTESA","ESPERANDO DATOS DE CARRETERA","WAITING FOR ROAD DATA"},
            {"DATI STRADALI NON DISPONIBILI","DATOS DE CARRETERA NO DISPONIBLES","ROAD DATA UNAVAILABLE"},
            {"SCARICO STRADE…","DESCARGANDO CARRETERAS…","DOWNLOADING ROADS…"},
            {"DOWNLOAD DATI STRADALI…","DESCARGANDO DATOS…","DOWNLOADING ROAD DATA…"},
            {"STRADE IN ATTESA","ESPERANDO CARRETERAS","WAITING FOR ROADS"}, {"DIREZIONE IN ATTESA","ESPERANDO DIRECCIÓN","WAITING FOR HEADING"},
            {"NESSUNA CURVA RILEVATA","NO SE DETECTA CURVA","NO CURVE DETECTED"}, {"LEGGERA","SUAVE","MILD"},
            {"MEDIA","MEDIA","MEDIUM"}, {"STRETTA","CERRADA","SHARP"}, {"TORNANTE","HORQUILLA","HAIRPIN"},
            {"SINISTRA","IZQUIERDA","LEFT"}, {"DESTRA","DERECHA","RIGHT"}, {"CONSIGLIATA","RECOMENDADA","RECOMMENDED"},
            {"VELOCITÀ","VELOCIDAD","SPEED"}, {"DISTANZA","DISTANCIA","DISTANCE"}, {"PENDENZA","PENDIENTE","GRADE"},
            {"SIMULAZIONE: NON USARE COME INDICAZIONE DI SICUREZZA","SIMULACIÓN: NO USAR COMO INDICACIÓN DE SEGURIDAD","SIMULATION: NOT A SAFETY RECOMMENDATION"},
            {"AUDIO ON","AUDIO ACTIVADO","AUDIO ON"}, {"METEO IN ATTESA","METEO PENDIENTE","WEATHER PENDING"},
            {"METEO NON DISPONIBILE","METEO NO DISPONIBLE","WEATHER UNAVAILABLE"}, {"SERENO","DESPEJADO","CLEAR"},
            {"NUVOLOSO","NUBLADO","CLOUDY"}, {"NEBBIA","NIEBLA","FOG"}, {"PIOVIGGINE","LLOVIZNA","DRIZZLE"},
            {"PIOVIGGINE GELATA","LLUVIA ENGELANTE","FREEZING DRIZZLE"}, {"PIOGGIA","LLUVIA","RAIN"},
            {"PIOGGIA GELATA","LLUVIA ENGELANTE","FREEZING RAIN"}, {"NEVE","NIEVE","SNOW"}, {"ROVESCI","CHUBASCOS","SHOWERS"},
            {"ROVESCI DI NEVE","CHUBASCOS DE NIEVE","SNOW SHOWERS"}, {"TEMPORALE","TORMENTA","THUNDERSTORM"},
            {"ATTENZIONE METEO","ALERTA METEOROLÓGICA","WEATHER ALERT"}, {"MAPPA · RAGGIO 1000 m","MAPA · RADIO 1000 m","MAP · 1000 m RADIUS"},
            {"MAPPA · RAGGIO 1000 m · TRASCINA PER SCORRERE","MAPA · RADIO 1000 m · ARRASTRA PARA MOVER","MAP · 1000 m RADIUS · DRAG TO PAN"},
            {"CHIUDI ✕","CERRAR ✕","CLOSE ✕"}, {"← RIDE","← CONDUCCIÓN","← RIDE"}, {"METEO ↻","METEO ↻","WEATHER ↻"},
            {"METEO…","METEO…","WEATHER…"}, {"ATTIVA AUDIO","ACTIVAR AUDIO","ENABLE AUDIO"},
            {"MAPPA IN ATTESA","MAPA PENDIENTE","MAP WAITING"},
            {"POSIZIONE APPROSSIMATIVA","POSICIÓN APROXIMADA","APPROXIMATE LOCATION"},
            {"POSIZIONE RETE","POSICIÓN DE RED","NETWORK LOCATION"},
            {"CARICAMENTO MAPPA…","CARGANDO MAPA…","LOADING MAP…"},
            {"MAPPA NON DISPONIBILE","MAPA NO DISPONIBLE","MAP UNAVAILABLE"},
            {"TEMPERATURA","TEMPERATURA","TEMPERATURE"}, {"TEMP","TEMP","TEMP"}
        };
        for (String[] row : rows) if (row[0].equals(it)) return row["es".equals(es) ? 1 : 2];
        return it;
    }

    void maybeUpdateWeather(Location location, boolean manual) {
        if (location == null || weatherLoading) return;
        // Weather must always correspond to a real GPS fix from this device.
        String weatherProvider = location.getProvider() == null ? "" : location.getProvider().toLowerCase(Locale.ROOT);
        boolean weatherLiveProvider = LocationManager.GPS_PROVIDER.equals(location.getProvider()) || "fused".equals(weatherProvider);
        long weatherAge = Math.max(0L, System.currentTimeMillis() - location.getTime());
        if (!weatherLiveProvider || !location.hasAccuracy() || location.getAccuracy() > 50f || weatherAge > 5000L) return;
        long now = System.currentTimeMillis();
        boolean timeDue = lastWeatherAt == 0L || now - lastWeatherAt >= 15L * 60L * 1000L;
        boolean distanceDue = !Double.isNaN(lastWeatherLat) &&
                distanceMeters(lastWeatherLat, lastWeatherLon, location.getLatitude(), location.getLongitude()) >= 10000.0;
        if (!manual && !timeDue && !distanceDue) return;
        weatherLoading = true;
        Location at = new Location(location);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String urlText = "https://api.open-meteo.com/v1/forecast?latitude=" + at.getLatitude()
                        + "&longitude=" + at.getLongitude()
                        + "&current=temperature_2m,apparent_temperature,precipitation,rain,showers,weather_code,wind_speed_10m,wind_gusts_10m"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m"
                        + "&forecast_days=2&timezone=auto&models=best_match";
                connection = (HttpURLConnection) new URL(urlText).openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + connection.getResponseCode());
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line; while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject response = new JSONObject(body.toString());
                JSONObject root = response.getJSONObject("current");
                int code = root.optInt("weather_code", -1);
                double temp = root.optDouble("temperature_2m", Double.NaN);
                double rain = root.optDouble("precipitation", 0.0);
                double wind = root.optDouble("wind_speed_10m", 0.0);
                double gust = root.optDouble("wind_gusts_10m", wind);
                String condition = weatherCodeLabel(code);
                weatherSummary = condition + (Double.isNaN(temp) ? "" : " · " + Math.round(temp) + "°C");
                // Keep the meteorological 2 m air temperature as the displayed value.
                // If the handset exposes a real ambient-temperature sensor, its reading is
                // collected separately for future calibrated fusion, but is not mixed in with
                // an arbitrary weighting: a phone on a handlebar can be warmed by sunlight,
                // its own electronics and the vehicle.
                airTemperature = Double.isNaN(temp) ? "--°" : Math.round(temp) + "°";
                String windLabel = "es".equals(Locale.getDefault().getLanguage()) ? "viento" : "en".equals(Locale.getDefault().getLanguage()) ? "wind" : "vento";
                String gustLabel = "es".equals(Locale.getDefault().getLanguage()) ? "rachas" : "en".equals(Locale.getDefault().getLanguage()) ? "gusts" : "raffiche";
                String rainLabel = "es".equals(Locale.getDefault().getLanguage()) ? "lluvia" : "en".equals(Locale.getDefault().getLanguage()) ? "rain" : "pioggia";
                weatherDetail = windLabel + " " + Math.round(wind) + " km/h · " + gustLabel + " " + Math.round(gust) + " km/h · " + rainLabel + " " + String.format(Locale.getDefault(), "%.1f", rain) + " mm";
                latestWeatherHourlyObject = response.optJSONObject("hourly");
                weatherHourlySummary = formatHourlyWeather(latestWeatherHourlyObject);
                weatherAdverse = code >= 95 || code == 65 || code == 67 || code == 82 || code == 86 || rain >= 2.0 || gust >= 60.0;
                lastWeatherAt = System.currentTimeMillis();
                lastWeatherLat = at.getLatitude(); lastWeatherLon = at.getLongitude();
                String place = reverseWeatherPlace(at.getLatitude(), at.getLongitude());
                if (place != null && !place.isEmpty()) weatherPlaceName = place;
            } catch (Exception ex) {
                if (lastWeatherAt == 0L) weatherSummary = tr("METEO NON DISPONIBILE");
                weatherDetail = tr("METEO NON DISPONIBILE") + " · " + ("es".equals(Locale.getDefault().getLanguage()) ? "comprueba la conexión" : "en".equals(Locale.getDefault().getLanguage()) ? "check connection" : "verifica connessione");
                lastWeatherAt = System.currentTimeMillis();
            } finally {
                if (connection != null) connection.disconnect();
                weatherLoading = false;
                if (!activityDestroyed && view != null) view.postInvalidate();
            }
        }, "weather-update").start();
    }

    String reverseWeatherPlace(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            String urlText = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=" + lat + "&lon=" + lon + "&zoom=18&addressdetails=1&accept-language=it";
            c = (HttpURLConnection) new URL(urlText).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.22.6 (Android motorcycle display)");
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return "";
            JSONObject root = new JSONObject(readConnection(c));
            JSONObject a = root.optJSONObject("address");
            if (a == null) return root.optString("display_name", "");
            String locality = firstNonEmpty(a.optString("village", ""), a.optString("town", ""), a.optString("city", ""), a.optString("municipality", ""), a.optString("suburb", ""), a.optString("hamlet", ""));
            String municipality = firstNonEmpty(a.optString("municipality", ""), a.optString("city", ""), a.optString("town", ""));
            if (!locality.isEmpty() && !municipality.isEmpty() && !locality.equalsIgnoreCase(municipality)) return locality + ", " + municipality;
            if (!locality.isEmpty()) return locality;
            return root.optString("display_name", "");
        } catch (Exception ignored) {
            return "";
        } finally { if (c != null) c.disconnect(); }
    }

    String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }


    String formatWeatherPeriod(JSONObject hourly, int band, boolean expanded) {
        if (band < 0 || band > 3) return "Dati meteo non disponibili";
        if (hourly == null) return new String[]{"🌙  NOTTE · 00:00–07:00", "🌤️  MATTINA · 08:00–15:00", "🌤️  POMERIGGIO · 16:00–19:00", "🌙  SERA · 20:00–23:00"}[band] + "\nPrevisioni non disponibili";
        JSONArray times = hourly.optJSONArray("time");
        JSONArray temps = hourly.optJSONArray("temperature_2m");
        JSONArray codes = hourly.optJSONArray("weather_code");
        JSONArray probs = hourly.optJSONArray("precipitation_probability");
        JSONArray winds = hourly.optJSONArray("wind_speed_10m");
        if (times == null || temps == null || times.length() == 0) return "Previsioni non disponibili";
        final int[] froms = {0, 8, 16, 20};
        final int[] tos = {8, 16, 20, 24};
        final String[] labels = {"NOTTE · 00:00–07:00", "MATTINA · 08:00–15:00", "POMERIGGIO · 16:00–19:00", "SERA · 20:00–23:00"};
        String firstDate = times.optString(0, "");
        if (firstDate.length() >= 10) firstDate = firstDate.substring(0, 10);
        double tempSum=0, windSum=0, probSum=0; int tempN=0, windN=0, probN=0, dominantCode=-1, maxSeverity=-1, rainyHours=0;
        StringBuilder detail = new StringBuilder();
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (int i=0; i<times.length(); i++) {
            String ts=times.optString(i, "");
            if (ts.length()<13 || !ts.startsWith(firstDate)) continue;
            int hour;
            try { hour=Integer.parseInt(ts.substring(11,13)); } catch(Exception ex) { continue; }
            if (hour < froms[band] || hour >= tos[band]) continue;
            double temp=temps.optDouble(i, Double.NaN);
            double wind=winds==null?Double.NaN:winds.optDouble(i, Double.NaN);
            int prob=probs==null?-1:probs.optInt(i,-1);
            int code=codes==null?-1:codes.optInt(i,-1);
            if (!Double.isNaN(temp)) { tempSum+=temp; tempN++; }
            if (!Double.isNaN(wind)) { windSum+=wind; windN++; }
            if (prob>=0) { probSum+=prob; probN++; if(prob>=40) rainyHours++; }
            int severity = weatherSeverity(code);
            if (severity>maxSeverity) { maxSeverity=severity; dominantCode=code; }
            if (expanded) {
                if(detail.length()>0) detail.append("\n");
                Date dt=null; try { dt=in.parse(ts); } catch(Exception ignored) {}
                detail.append(dt==null?ts.substring(11):hm.format(dt)).append("  ").append(weatherCodeLabel(code));
                if(!Double.isNaN(temp)) detail.append("  ").append(Math.round(temp)).append("°C");
                if(prob>=0) detail.append("  ").append(prob).append("% pioggia");
                if(!Double.isNaN(wind)) detail.append("  vento ").append(Math.round(wind)).append(" km/h");
            }
        }
        if(tempN==0 && windN==0 && probN==0) return labels[band]+"\nDati non disponibili";
        String icon = weatherCodeIcon(dominantCode);
        if(windN>0 && windSum/windN>=35) icon += " ≋";
        StringBuilder summary = new StringBuilder(icon).append("  ").append(labels[band]);
        if(tempN>0) summary.append("\nTemperatura media ").append(Math.round(tempSum/tempN)).append("°C");
        if(windN>0) summary.append(" · vento medio ").append(Math.round(windSum/windN)).append(" km/h");
        if(probN>0) summary.append(" · probabilità media pioggia ").append(Math.round(probSum/probN)).append("%");
        if(rainyHours>0) summary.append("\nPossibile pioggia in ").append(rainyHours).append(rainyHours==1?" ora":" ore");
        if(expanded) summary.append("\n\nDettaglio orario\n").append(detail.length()==0?"Dati orari non disponibili":detail.toString());
        else summary.append("\nTocca per vedere tutte le ore");
        return summary.toString();
    }

    int weatherSeverity(int code) {
        if(code>=95) return 6;
        if(code==65||code==67||code==82||code==86) return 5;
        if(code==61||code==63||code==80||code==81||code==71||code==73||code==75) return 4;
        if(code==51||code==53||code==55||code==56||code==57||code==66) return 3;
        if(code==45||code==48) return 2;
        if(code==2||code==3) return 1;
        return code==0||code==1?0:-1;
    }

    String weatherCodeIcon(int code) {
        if(code>=95) return "⛈️";
        if(code==65||code==67||code==82||code==86||code==61||code==63||code==80||code==81||code==51||code==53||code==55) return "🌧️";
        if(code==71||code==73||code==75||code==77||code==85) return "❄️";
        if(code==45||code==48) return "🌫️";
        if(code==2||code==3) return "☁️";
        if(code==1) return "🌤️";
        if(code==0) return "☀️";
        return "🌡️";
    }

    String formatHourlyWeather(JSONObject hourly) {
        if (hourly == null) return "";
        StringBuilder out = new StringBuilder();
        for(int band=0; band<4; band++) {
            if(out.length()>0) out.append("\n\n");
            out.append(formatWeatherPeriod(hourly, band, true));
        }
        return out.toString();
    }

    void loadDestinationWeather() {
        if (!navigationActive || Double.isNaN(navigationDestLat) || Double.isNaN(navigationDestLon)) {
            destinationWeatherSummary=""; destinationWeatherHourlySummary=""; return;
        }
        destinationWeatherLoading=true;
        final double lat=navigationDestLat, lon=navigationDestLon;
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                String urlText="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
                        +"&current=temperature_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation"
                        +"&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m"
                        +"&forecast_days=2&timezone=auto&models=best_match";
                connection=(HttpURLConnection)new URL(urlText).openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(15000); connection.setRequestMethod("GET");
                if(connection.getResponseCode()!=HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP "+connection.getResponseCode());
                String body=readConnection(connection);
                JSONObject root=new JSONObject(body);
                JSONObject cur=root.getJSONObject("current");
                int code=cur.optInt("weather_code",-1);
                double temp=cur.optDouble("temperature_2m",Double.NaN);
                double wind=cur.optDouble("wind_speed_10m",0);
                double gust=cur.optDouble("wind_gusts_10m",wind);
                destinationWeatherSummary=weatherCodeLabel(code)+(Double.isNaN(temp)?"":" · "+Math.round(temp)+"°C")+" · vento "+Math.round(wind)+" km/h · raffiche "+Math.round(gust)+" km/h";
                destinationWeatherHourlySummary=formatHourlyWeather(root.optJSONObject("hourly"));
            } catch(Exception ex) {
                destinationWeatherSummary="METEO DESTINAZIONE NON DISPONIBILE";
                destinationWeatherHourlySummary="";
            } finally {
                if(connection!=null) connection.disconnect();
                destinationWeatherLoading=false;
                if(!activityDestroyed && view!=null) if (!activityDestroyed && view != null) view.postInvalidate();
            }
        },"destination-weather").start();
    }

    String readConnection(HttpURLConnection c) throws Exception {
        StringBuilder b=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String line;while((line=r.readLine())!=null)b.append(line);}
        return b.toString();
    }

    String weatherIcon() {
        String s = weatherSummary.toLowerCase(Locale.ROOT);
        if (s.contains("tempor") || s.contains("tempest")) return "⚡";
        if (s.contains("piogg") || s.contains("rain") || s.contains("lluv")) return "☂";
        if (s.contains("nuvol") || s.contains("cloud") || s.contains("nube")) return "☁";
        if (s.contains("vento") || s.contains("wind")) return "≋";
        if (s.contains("neve") || s.contains("snow")) return "❄";
        return "☀";
    }

    String weatherCodeLabel(int code) {
        switch (code) {
            case 0: return tr("SERENO");
            case 1: case 2: case 3: return tr("NUVOLOSO");
            case 45: case 48: return tr("NEBBIA");
            case 51: case 53: case 55: return tr("PIOVIGGINE");
            case 56: case 57: return tr("PIOVIGGINE GELATA");
            case 61: case 63: case 65: return tr("PIOGGIA");
            case 66: case 67: return tr("PIOGGIA GELATA");
            case 71: case 73: case 75: case 77: return tr("NEVE");
            case 80: case 81: case 82: return tr("ROVESCI");
            case 85: case 86: return tr("ROVESCI DI NEVE");
            case 95: case 96: case 99: return tr("TEMPORALE");
            default: return "METEO";
        }
    }

    void updateMapBearing(Location location) {
        if (location == null) return;
        if (location.hasBearing() && (!location.hasSpeed() || location.getSpeed() > 0.8f)) {
            mapBearing = location.getBearing();
            hasMapBearing = true;
        } else if (previousMapLocation != null) {
            float moved = previousMapLocation.distanceTo(location);
            if (moved >= 3.0f) {
                mapBearing = previousMapLocation.bearingTo(location);
                hasMapBearing = true;
            }
        }
        previousMapLocation = new Location(location);
    }

    void beep(int n, boolean longFirst) {
        if (!audio || tone == null) return;
        new Thread(() -> {
            try {
                if (longFirst) { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 520); Thread.sleep(180); }
                for (int i = 0; i < n; i++) { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150); Thread.sleep(160); }
            } catch (Exception ignored) { }
        }, "curve-audio").start();
    }

    void triggerRadarAlert(String spokenText, String displayText) {
        radarAlertMessage = displayText == null || displayText.trim().isEmpty() ? "RADAR" : displayText;
        radarAlertUntil = System.currentTimeMillis() + 5000L;
        radarFlashToggles = 0;
        if (audio && navigationTtsReady && navigationTts != null) {
            final String phrase = spokenText == null || spokenText.trim().isEmpty() ? "Attenzione Radar" : spokenText;
            runOnUiThread(() -> { try { if (navigationTts != null) navigationTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "radar-alert-" + System.currentTimeMillis()); } catch (Exception ignored) {} });
        } else if (audio && tone != null) {
            new Thread(() -> { try { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 450); } catch (Exception ignored) {} }, "radar-audio").start();
        }
        final long until = radarAlertUntil;
        new Thread(() -> {
            while (System.currentTimeMillis() < until) {
                if (!activityDestroyed && view != null) view.postInvalidate();
                try { Thread.sleep(250); } catch (InterruptedException ignored) { break; }
            }
            if (!activityDestroyed && view != null) view.postInvalidate();
        }, "radar-flash").start();
    }

    void updateRadarAlerts(RadarApproach approach, float actualKmh, int limitKmh, long nowMs) {
        if (approach == null || approach.point == null || !Double.isFinite(approach.distanceMeters)) {
            if (radarAlertStage > 0 && nowMs - lastRadarStageAt > 2500L) resetRadarAlertSequence();
            return;
        }
        String id = approach.point.id == null ? "" : approach.point.id;
        double distance = approach.distanceMeters;
        if (!id.equals(activeRadarId)) resetRadarAlertSequence();
        if (radarAlertStage == 0) {
            // Never infer a legal limit. Without a known road limit, proximity icons remain visible but no spoken overspeed alarm is triggered.
            if (limitKmh <= 0 || actualKmh < limitKmh + 3.0f) return;
            double start = Math.max(400.0, Math.min(1000.0, actualKmh * 6.0));
            if (distance > start || distance < 5.0) return;
            activeRadarId = id; activeRadarStartDistance = start; radarAlertStage = 1; lastRadarStageAt = nowMs; radarAlerted = true;
            triggerRadarAlert("Radar a " + Math.max(10, (int)(Math.round(distance / 10.0) * 10)) + " metri", Math.max(10, (int)(Math.round(distance / 10.0) * 10)) + " m");
            return;
        }
        // Once an overspeed approach has triggered, keep the distance sequence even if the rider slows down.
        if (distance > activeRadarStartDistance + 100.0 || distance < 4.0) { resetRadarAlertSequence(); return; }
        if (radarAlertStage == 1 && distance <= 100.0) {
            radarAlertStage = 2; lastRadarStageAt = nowMs; triggerRadarAlert("Radar a 100 metri", "100 m");
        } else if (radarAlertStage == 2 && distance <= 50.0) {
            radarAlertStage = 3; lastRadarStageAt = nowMs; triggerRadarAlert("Radar a 50 metri", "50 m");
        } else if (radarAlertStage == 3 && distance <= 10.0) {
            radarAlertStage = 4; lastRadarStageAt = nowMs; triggerRadarAlert("Attenzione Radar", "RADAR");
        } else if (radarAlertStage >= 4 && distance > 100.0) resetRadarAlertSequence();
    }

    void resetRadarAlertSequence() {
        activeRadarId = ""; radarAlertStage = 0; activeRadarStartDistance = 0.0; radarAlerted = false;
    }

    void beepSpeedLimit() {
        if (!audio || tone == null) return;
        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 85);
                    Thread.sleep(115);
                }
            } catch (Exception ignored) { }
        }, "speed-limit-audio").start();
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == REQ_LOC && g != null) {
            boolean granted = false;
            for (int grant : g) if (grant == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) startGps(); else { if (!activityDestroyed && view != null) view.invalidate(); }
        } else if (!activityDestroyed && view != null) view.invalidate();
    }

    @Override public void onBackPressed() {
        if (view != null && view.mapExpanded) { view.mapExpanded = false; view.mapUserPanned = false; view.selectedMapLat = Double.NaN; view.selectedMapLon = Double.NaN; view.selectedMapPlaceName = ""; view.invalidate(); }
        else if (selectedTripId > 0) { selectedTripId = -1L; selectedTripSummary = null; selectedTripPoints.clear(); if (!activityDestroyed && view != null) view.invalidate(); }
        else if (tripDayScreen) { tripDayScreen = false; selectedTripDayKey = ""; if (!activityDestroyed && view != null) { view.tripsScroll = 0f; view.invalidate(); } }
        else if (tripsScreen) { tripsScreen = false; if (!activityDestroyed && view != null) view.invalidate(); }
        else super.onBackPressed();
    }

    void setWindowBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.15f, Math.min(1f, brightness));
        getWindow().setAttributes(lp);
    }

    void restoreWindowBrightness() {
        if (!brightnessCaptured) return;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = originalWindowBrightness;
        getWindow().setAttributes(lp);
    }

    @Override protected void onResume() {
        super.onResume();
        if (vectorMapView != null) { try { vectorMapView.onResume(); } catch (Throwable ignored) {} }
        if (lightSensorManager != null && lightSensor != null && lightSensorListener != null) {
            try { lightSensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL); }
            catch (Exception ignored) { }
        }
    }

    @Override protected void onPause() {
        if (vectorMapView != null) { try { vectorMapView.onPause(); } catch (Throwable ignored) {} }
        if (lightSensorManager != null && lightSensorListener != null) lightSensorManager.unregisterListener(lightSensorListener);
        restoreWindowBrightness();
        saveNavigationResumeState();
        super.onPause();
    }

    void saveNavigationResumeState() {
        if (activityDestroyed) return;
        android.content.SharedPreferences p = getSharedPreferences("road_display", MODE_PRIVATE);
        if (navigationActive && Double.isFinite(navigationDestLat) && Double.isFinite(navigationDestLon)) {
            p.edit().putBoolean(PREF_NAV_RESUME, true).putString(PREF_NAV_RESUME_LAT, Double.toString(navigationDestLat)).putString(PREF_NAV_RESUME_LON, Double.toString(navigationDestLon)).putString(PREF_NAV_RESUME_LABEL, navigationDestinationLabel == null ? "" : navigationDestinationLabel).apply();
        } else p.edit().putBoolean(PREF_NAV_RESUME, false).apply();
    }
    void maybeOfferNavigationResume() {
        if (activityDestroyed || resumePromptShown || navigationActive) return;
        android.content.SharedPreferences p = getSharedPreferences("road_display", MODE_PRIVATE);
        if (!p.getBoolean(PREF_NAV_RESUME, false)) return;
        double lat,lon; try { lat=Double.parseDouble(p.getString(PREF_NAV_RESUME_LAT,"NaN")); lon=Double.parseDouble(p.getString(PREF_NAV_RESUME_LON,"NaN")); } catch(Exception e){return;}
        if(!Double.isFinite(lat)||!Double.isFinite(lon))return; resumePromptShown=true; String label=p.getString(PREF_NAV_RESUME_LABEL,"");
        new android.app.AlertDialog.Builder(this).setTitle("NAVIGAZIONE INTERROTTA").setMessage("Vuoi continuare la navigazione verso "+(label==null||label.trim().isEmpty()?"la destinazione precedente":label)+"?").setNegativeButton("NUOVA NAVIGAZIONE",(d,w)->p.edit().putBoolean(PREF_NAV_RESUME,false).apply()).setPositiveButton("CONTINUA",(d,w)->{navigationDestinationLabelOverride=label==null?"":label;if(view!=null)view.startNavigation("",String.format(Locale.US,"%.6f,%.6f",lat,lon));}).show();
    }

    @Override protected void onDestroy() {
        activityDestroyed = true;
        if (fusedLocationClient != null && fusedLocationCallback != null) {
            try { fusedLocationClient.removeLocationUpdates(fusedLocationCallback); } catch (Exception ignored) { }
            fusedLocationActive = false;
        }
        if (lm != null) {
            if (listener != null) lm.removeUpdates(listener);
            if (Build.VERSION.SDK_INT >= 24 && gnssCallback != null) {
                try { lm.unregisterGnssStatusCallback(gnssCallback); } catch (Exception ignored) { }
            }
        }
        if (view != null) view.releaseTiles();
        if (tone != null) tone.release();
        if (navigationTts != null) { try { navigationTts.stop(); navigationTts.shutdown(); } catch (Exception ignored) {} }
        if (tripRecorder != null) tripRecorder.shutdown();
        if (sensorFusion != null) sensorFusion.stop();
        if (telemetryRecorder != null) telemetryRecorder.shutdown();
        if (vectorMapView != null) { try { vectorMapView.onDestroy(); } catch (Throwable ignored) {} vectorMapView = null; vectorMap = null; }
        synchronized (tripDbLock) {
            if (tripDb != null) tripDb.close();
            tripDb = null;
        }
        if (telemetryDb != null) telemetryDb.close();
        if (db != null) db.close();
        super.onDestroy();
    }


    File gpxDirectory() {
        File dir = new File(getFilesDir(), "gpx");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static String safeFileName(String name) {
        if (name == null || name.trim().isEmpty()) name = "traccia";
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".gpx")) name += ".gpx";
        return name;
    }

    long directorySize(File f) {
        if (f == null || !f.exists()) return 0L;
        if (f.isFile()) return f.length();
        long total = 0L;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double n = bytes / 1024.0;
        if (n < 1024.0) return String.format(Locale.getDefault(), "%.1f KB", n);
        n /= 1024.0;
        if (n < 1024.0) return String.format(Locale.getDefault(), "%.1f MB", n);
        return String.format(Locale.getDefault(), "%.2f GB", n / 1024.0);
    }

    void showMemoryManager() {
        new Thread(() -> {
            File mapDir = new File(getFilesDir(), "maptiles");
            File gpxDir = gpxDirectory();
            long map = directorySize(mapDir), gpx = directorySize(gpxDir);
            long appFiles = Math.max(0L, directorySize(getFilesDir()) - map - gpx);
            long cache = directorySize(getCacheDir());
            android.os.StatFs stat = new android.os.StatFs(getFilesDir().getAbsolutePath());
            long free = stat.getAvailableBytes();
            String message = "MAPPE IN CACHE\n" + formatBytes(map) +
                    "\n\nFILE GPX\n" + formatBytes(gpx) +
                    "\n\nDATI APP (esclusi mappe/GPX)\n" + formatBytes(appFiles) +
                    "\n\nCACHE TEMPORANEA\n" + formatBytes(cache) +
                    "\n\nSPAZIO LIBERO\n" + formatBytes(free);
            runOnUiThread(() -> {
                if (activityDestroyed) return;
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("MEMORIA E ARCHIVIAZIONE")
                        .setMessage(message)
                        .setNegativeButton("CHIUDI", null)
                        .setNeutralButton("SVUOTA CACHE MAPPE", (d,w) -> clearMapCache())
                        .setPositiveButton("GESTISCI GPX", (d,w) -> showGpxManager())
                        .create();
                dialog.show();
            });
        }, "storage-stats").start();
    }

    void clearMapCache() {
        new AlertDialog.Builder(this).setTitle("SVUOTARE LA CACHE MAPPE?")
                .setMessage("Verranno eliminate solo le tile salvate localmente. GPS, viaggi, GPX e impostazioni non verranno toccati.")
                .setNegativeButton("ANNULLA", null)
                .setPositiveButton("SVUOTA", (d,w) -> new Thread(() -> {
                    File dir = new File(getFilesDir(), "maptiles");
                    deleteRecursively(dir);
                    if (view != null) view.clearTileMemoryCache();
                    runOnUiThread(() -> { if (!activityDestroyed) { if (view != null) view.invalidate(); showMemoryManager(); } });
                }, "clear-map-cache").start()).show();
    }

    void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        try { f.delete(); } catch (Exception ignored) { }
    }

    void showGpxManager() {
        File dir = gpxDirectory();
        File[] files = dir.listFiles((d,n) -> n.toLowerCase(Locale.ROOT).endsWith(".gpx"));
        if (files == null) files = new File[0];
        java.util.Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        final File[] list = files;
        String[] labels = new String[list.length];
        for (int i=0;i<list.length;i++) labels[i] = list[i].getName() + "  ·  " + formatBytes(list[i].length());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("I MIEI FILE GPX")
                .setItems(labels, (d,which) -> showGpxFileActions(list[which]))
                .setNegativeButton("CHIUDI", null)
                .setNeutralButton("IMPORTA GPX", (d,w) -> importGpx())
                .create();
        if (list.length == 0) dialog.setMessage("Nessun file GPX memorizzato.\n\nPuoi importare una traccia GPX dal dispositivo.");
        dialog.show();
    }

    void showGpxFileActions(File file) {
        if (file == null || !file.isFile()) return;
        new AlertDialog.Builder(this).setTitle(file.getName())
                .setMessage(formatBytes(file.length()))
                .setNegativeButton("CHIUDI", null)
                .setNeutralButton("ELIMINA", (d,w) -> new AlertDialog.Builder(this)
                        .setTitle("ELIMINARE IL GPX?")
                        .setMessage("Il file verrà eliminato dalla memoria di ROAD DISPLAY PRO.")
                        .setNegativeButton("ANNULLA", null)
                        .setPositiveButton("ELIMINA", (dd,ww) -> { try { file.delete(); } catch (Exception ignored) {} showGpxManager(); })
                        .show())
                .setPositiveButton("ESPORTA COPIA", (d,w) -> exportGpxFile(file))
                .show();
    }

    void exportGpxFile(File source) {
        if (source == null || !source.isFile()) return;
        exportGpxSource = source;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/gpx+xml");
        i.putExtra(Intent.EXTRA_TITLE, source.getName());
        try { startActivityForResult(i, REQ_EXPORT_GPX); } catch (Exception e) { exportGpxSource=null; }
    }

    void copyGpxFileToUri(File source, Uri uri) {
        try (InputStream in=new FileInputStream(source); java.io.OutputStream out=getContentResolver().openOutputStream(uri)) {
            if(out==null) throw new Exception("Destinazione non disponibile");
            byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) out.write(buf,0,n);
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("GPX ESPORTATO").setMessage("Copia esportata correttamente.").setPositiveButton("OK",null).show());
        } catch(Exception e) {
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("ESPORTAZIONE FALLITA").setMessage(e.getMessage()==null?"errore":e.getMessage()).setPositiveButton("OK",null).show());
        }
    }

    void importGpx() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/gpx+xml");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/gpx+xml", "application/xml", "text/xml", "text/plain"});
        try { startActivityForResult(intent, REQ_IMPORT_GPX); } catch (Exception e) {
            intent.setType("*/*"); startActivityForResult(intent, REQ_IMPORT_GPX);
        }
    }

    boolean isGpxName(String name) { return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gpx"); }

    int countGpxTrackPoints(File file) {
        int count = 0;
        try (InputStream raw = new FileInputStream(file); InputStream in = isGpxName(file.getName()) ? raw : raw) {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line=br.readLine()) != null) {
                if (line.toLowerCase(Locale.ROOT).contains("<trkpt")) count++;
                if (count > 2000000) throw new Exception("GPX troppo grande");
            }
        } catch (Exception e) { return -1; }
        return count;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT_GPX) {
            new Thread(() -> {
                File dest = null;
                try {
                    String name = null;
                    android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
                    if (c != null) { try { int idx=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if(idx>=0&&c.moveToFirst()) name=c.getString(idx); } finally { c.close(); } }
                    if (name == null) name = "importato_" + System.currentTimeMillis() + ".gpx";
                    if (!isGpxName(name)) name += ".gpx";
                    dest = new File(gpxDirectory(), safeFileName(name));
                    int suffix=2; while(dest.exists()){ String base=dest.getName().substring(0,dest.getName().length()-4); dest=new File(gpxDirectory(),base+"_"+suffix+++".gpx"); }
                    try (InputStream in=getContentResolver().openInputStream(uri); FileOutputStream out=new FileOutputStream(dest)) {
                        if(in==null) throw new Exception("File non leggibile");
                        byte[] buf=new byte[8192]; int n; long total=0; while((n=in.read(buf))>0){ total+=n; if(total>50L*1024L*1024L) throw new Exception("GPX superiore a 50 MB"); out.write(buf,0,n); }
                    }
                    int points=countGpxTrackPoints(dest);
                    if(points<1) throw new Exception("Nessun punto <trkpt> trovato nel GPX");
                    final File saved=dest; final int pc=points;
                    runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("GPX IMPORTATO")
                            .setMessage(saved.getName()+"\n"+formatBytes(saved.length())+"\n"+pc+" punti traccia")
                            .setPositiveButton("OK",null).show());
                } catch(Exception e) {
                    if(dest!=null) try{dest.delete();}catch(Exception ignored){}
                    final String msg=e.getMessage()==null?"file non valido":e.getMessage();
                    runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("IMPORTAZIONE GPX FALLITA").setMessage(msg).setPositiveButton("OK",null).show());
                }
            }, "gpx-import").start();
        } else if (requestCode == REQ_EXPORT_GPX) {
            if (exportGpxSource != null) {
                final File source=exportGpxSource; exportGpxSource=null;
                new Thread(() -> copyGpxFileToUri(source, uri), "gpx-copy-export").start();
            } else if (exportTripId > 0) {
                final long tripId = exportTripId; exportTripId = -1L;
                new Thread(() -> exportTripToUri(tripId, uri), "gpx-export").start();
            }
        }
    }

    void exportTripToUri(long tripId, Uri uri) {
        try {
            TripDb.TripSummary summary; java.util.ArrayList<TripDb.Point> points;
            synchronized (tripDbLock) { summary=tripDb.getTrip(tripId); points=tripDb.getPoints(tripId); }
            if(summary==null||points.size()<2) throw new Exception("Traccia GPS insufficiente");
            try (java.io.OutputStream os=getContentResolver().openOutputStream(uri); BufferedWriter bw=new BufferedWriter(new OutputStreamWriter(os,"UTF-8"))) {
                if(os==null) throw new Exception("Destinazione non disponibile");
                bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"ROAD DISPLAY PRO\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
                bw.write("<trk><name>ROAD DISPLAY PRO "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(summary.startTime))+"</name><trkseg>\n");
                for(TripDb.Point p:points) {
                    bw.write(String.format(Locale.US,"<trkpt lat=\"%.8f\" lon=\"%.8f\"><ele>%.2f</ele><time>%s</time></trkpt>\n",p.lat,p.lon,p.alt,isoUtc(p.time)));
                }
                bw.write("</trkseg></trk></gpx>\n"); bw.flush();
            }
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("GPX ESPORTATO").setMessage("Traccia esportata correttamente.").setPositiveButton("OK",null).show());
        } catch(Exception e) {
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("ESPORTAZIONE GPX FALLITA").setMessage(e.getMessage()==null?"errore":e.getMessage()).setPositiveButton("OK",null).show());
        }
    }

    String isoUtc(long millis) { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US) {{ setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }}.format(new Date(millis)); }

    void exportSelectedTripGpx() {
        if(selectedTripId<=0) return;
        TripDb.TripSummary t=selectedTripSummary;
        String name="road_display_"+(t==null?System.currentTimeMillis():new SimpleDateFormat("yyyyMMdd_HHmm",Locale.getDefault()).format(new Date(t.startTime)))+".gpx";
        exportTripId=selectedTripId;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/gpx+xml"); i.putExtra(Intent.EXTRA_TITLE,name);
        try { startActivityForResult(i,REQ_EXPORT_GPX); } catch(Exception e) { exportTripId=-1L; }
    }

    // Navigation helpers used by the outer GPS/location processing code.
    // These deliberately live in MainActivity (not RideView), because processLocation()
    // runs in the Activity scope.
    RouteStep upcomingRoundaboutLocked(Location l) {
        if (l == null || navigationSteps.isEmpty()) return null;
        int start = Math.max(0, Math.min(navigationStepIndex, navigationSteps.size() - 1));
        RouteStep best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = start; i < Math.min(navigationSteps.size(), start + 2); i++) {
            RouteStep st = navigationSteps.get(i);
            String type = st.maneuverType == null ? "" : st.maneuverType.toLowerCase(Locale.ROOT);
            if (!("roundabout".equals(type) || "rotary".equals(type))) continue;
            double d = distanceMeters(l.getLatitude(), l.getLongitude(), st.lat, st.lon);
            if (d <= 350.0 && d < bestD) { best = st; bestD = d; }
        }
        return best;
    }

    int directionFromModifier(String type) {
        if (type == null) return 1;
        String x = type.toLowerCase(Locale.ROOT);
        return x.contains("left") ? -1 : 1;
    }

    class RideView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint negativeLayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Curve emptyCurve = new Curve();
        final RectF mapBounds = new RectF(), tripMapBounds = new RectF(), tripsButtonBounds = new RectF(), navButtonBounds = new RectF(), exitButtonBounds = new RectF(), settingsButtonBounds = new RectF(), weatherButtonBounds = new RectF(), mapCloseBounds = new RectF(), mapSearchBounds = new RectF(), mapRecenterBounds = new RectF(), mapSetDestBounds = new RectF(), mapSetHomeBounds = new RectF(), mapClearPointBounds = new RectF(), tripsCloseBounds = new RectF(), tripDetailHomeBounds = new RectF(), tripDetailListBounds = new RectF(), tripDetailDeleteBounds = new RectF(), tripMapZoomInBounds = new RectF(), tripMapZoomOutBounds = new RectF();
        boolean mapExpanded = false, draggingMap = false, movedMap = false;
        boolean mapUserPanned = false;
        float mapFrozenBearing = 0f;
        long lastRouteTilePriorityAt = 0L;
        float mapZoom = 1.5f, pinchStartDistance = 0f, pinchStartZoom = 1.5f;
        float mapZoomCompact = 1.5f;
        final RectF speedBounds = new RectF();
        double mapAnchorLat = Double.NaN, mapAnchorLon = Double.NaN;
        Location mapAnchorLocation;
        // Full-screen map owns a geographic viewport center. No pixel-pan accumulator or fling.
        double mapViewLat = Double.NaN, mapViewLon = Double.NaN;
        double selectedMapLat = Double.NaN, selectedMapLon = Double.NaN;
        String selectedMapPlaceName = "";
        volatile int selectedMapGeocodeToken = 0;
        long lastTileAnchorUpdate = 0L;
        double smoothMarkerLat=Double.NaN,smoothMarkerLon=Double.NaN; float smoothMarkerBearing=Float.NaN;
        boolean pinching = false, rotatingMap = false;
        float rotateStartAngle = 0f, rotateStartBearing = 0f;
        float tripsScroll = 0f;
        float downX, downY;
        double downMapLat = Double.NaN, downMapLon = Double.NaN;
        final int yellow = 0xFFFFD400, green = 0xFF00F080, blue = 0xFF42A5FF;
        final float density;
        final LruCache<String, Bitmap> tileBitmaps = new LruCache<String, Bitmap>(96 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) { return value == null ? 0 : value.getByteCount(); }
        };
        // Stable last-known tile images prevent a blank/placeholder flash while a fresh tile arrives.
        final LruCache<String, Bitmap> stableTileBitmaps = new LruCache<String, Bitmap>(96 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) { return value == null ? 0 : value.getByteCount(); }
        };
        final Set<String> tilesLoading = ConcurrentHashMap.newKeySet();
        final ConcurrentHashMap<String, Long> tileRetryAfter = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Long> tileExpiries = new ConcurrentHashMap<>();
        void clearTileMemoryCache() {
            tileBitmaps.evictAll();
            stableTileBitmaps.evictAll();
        }
        final ExecutorService tileExecutor = Executors.newFixedThreadPool(3);
        volatile int tileFailures = 0;
        boolean tripMapRendering = false;
        boolean tripMapUserPanned = false;
        double tripMapCenterLat = Double.NaN, tripMapCenterLon = Double.NaN;
        boolean draggingTripMap = false, tripRotating = false;
        float tripRotateStartAngle = 0f, tripRotateStartBearing = 0f;
        float tripDownX, tripDownY; double tripDownLat=Double.NaN, tripDownLon=Double.NaN;
        int tripMapZoom = 15;
        int tripMapZoomOffset = 0;
        float tripMapBearing = 0f;

        RideView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            p.setTypeface(TypefaceCompat.bold());
            p.setTextAlign(Paint.Align.LEFT);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            setBackgroundColor(Color.BLACK);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ColorMatrix invert = new ColorMatrix(new float[]{
                -1,0,0,0,255, 0,-1,0,0,255, 0,0,-1,0,255, 0,0,0,1,0
            });
            negativeLayerPaint.setColorFilter(new ColorMatrixColorFilter(invert));
        }

        float dp(float v) { return v * density; }
        float normalizeBearing(float b) { float x=b%360f; return x<0f?x+360f:x; }
        float minDp() { return Math.min(getWidth(), getHeight()) / density; }
        void txt(Canvas c, String s, float x, float y, float sizeDp, int color, Paint.Align align) {
            p.setTextSize(Math.max(10f, sizeDp) * density);
            p.setColor(color); p.setTextAlign(align); p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
            c.drawText(s, x, y, p);
        }
        void panel(Canvas c, RectF r, int color, float radius) {
            p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(r, radius, radius, p);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            boolean vectorExpanded = mapExpanded && vectorMapReady && vectorMapView != null && vectorMapView.getVisibility() == View.VISIBLE;
            boolean applyNegative = negativeTheme && !vectorExpanded;
            int layer = applyNegative ? c.saveLayer(0, 0, getWidth(), getHeight(), negativeLayerPaint) : -1;
            if (vectorExpanded) c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            else c.drawColor(Color.BLACK);
            if (mapExpanded) drawExpandedMap(c);
            else if (selectedTripId > 0) drawTripDetail(c); else if (tripDayScreen) drawTripDay(c); else if (tripsScreen) drawTrips(c); else drawRide(c);
            if (applyNegative) c.restoreToCount(layer);
            postInvalidateDelayed((navigationActive||mapExpanded)?50:1000); // Smooth navigation marker while active.
        }

        String now() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); }
        String gpsSummary() {
            Location l = lastGnssFix != null ? lastGnssFix : engine.loc;
            if (l == null) return tr("GPS IN RICERCA");
            if ("ip-approx".equals(l.getProvider()) || "saved-network".equals(l.getProvider())) {
                String acc = l.hasAccuracy() ? " ±" + Math.round(l.getAccuracy()) + "m" : "";
                return tr("POSIZIONE APPROSSIMATIVA") + acc;
            }
            String provider = l.getProvider() == null ? "" : l.getProvider().toLowerCase(Locale.ROOT);
            boolean fused = "fused".equals(provider);
            if (!LocationManager.GPS_PROVIDER.equals(l.getProvider()) && !fused) {
                String acc = l.hasAccuracy() ? " ±" + Math.round(l.getAccuracy()) + "m" : "";
                return tr("POSIZIONE RETE") + acc;
            }
            long age = Math.max(0L, System.currentTimeMillis() - l.getTime());
            if (gpsSignalLost || age > GNSS_LOSS_TIMEOUT_MS) return tr("GPS INSUFFICIENTE");
            float a = l.hasAccuracy() ? l.getAccuracy() : Float.POSITIVE_INFINITY;
            String state;
            if (a <= 10f && satellitesUsed >= 8) state = tr("GPS OTTIMO");
            else if (a <= 25f) state = tr("GPS BUONO");
            else if (a <= 50f) state = tr("GPS DISCRETO");
            else if (a <= 100f) state = tr("GPS DEBOLE");
            else state = tr("GPS MOLTO DEBOLE");
            String sat = satellitesVisible > 0 ? " · " + satellitesUsed + "/" + satellitesVisible + " sat" : "";
            String acc = l.hasAccuracy() ? " ±" + Math.round(l.getAccuracy()) + "m" : "";
            return state + sat + acc;
        }
        String roadSummary() {
            if (downloadingRoads) return tr("SCARICO STRADE…");
            if (engine.ways != null && !engine.ways.isEmpty()) return "STRADE " + engine.ways.size();
            if (!roadDownloadAttempted) return tr("DATI STRADALI IN ATTESA");
            return tr("DATI STRADALI NON DISPONIBILI");
        }
        int batteryPercent() {
            try {
                android.content.Intent i = registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                if (i == null) return -1;
                int level=i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1);
                int scale=i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,-1);
                return level>=0&&scale>0 ? Math.round(level*100f/scale) : -1;
            } catch(Exception e) { return -1; }
        }

        void drawTopStatus(Canvas c) {
            int w = getWidth(); boolean land = w > getHeight();
            float barH = dp(land ? 34 : 50);
            panel(c, new RectF(0, 0, w, barH), 0xFF111820, 0);
            float fs = Math.max(10f, Math.min(15f, minDp() * .034f));
            int gpsColor = (!gpsSignalLost && engine.gpsGood()) ? green : 0xFFFFB84D;
            int roadColor = (downloadingRoads || (engine.mapWays != null && !engine.mapWays.isEmpty())) ? green : 0xFFFFB84D;
            if (land) {
                txt(c, gpsSummary(), w * .46f, dp(23), fs, gpsColor, Paint.Align.CENTER);
                String road = downloadingRoads ? tr("SCARICO STRADE…") : (engine.mapWays != null && !engine.mapWays.isEmpty() ? "STRADE " + engine.mapWays.size() : tr("STRADE IN ATTESA"));
                txt(c, road, w - dp(10), dp(23), fs, roadColor, Paint.Align.RIGHT);
            } else {
                txt(c, gpsSummary(), w / 2f, dp(21), fs, gpsColor, Paint.Align.CENTER);
                String road = downloadingRoads ? tr("DOWNLOAD DATI STRADALI…") : (engine.mapWays != null && !engine.mapWays.isEmpty() ? "DATI STRADALI OK · " + engine.mapWays.size() + " STRADE" : tr("DATI STRADALI IN ATTESA"));
                txt(c, road, w / 2f, dp(42), Math.max(9f, fs - 1), roadColor, Paint.Align.CENTER);
            }
        }

        void drawDemoInfo(Canvas c, boolean land) {
            float y = dp(48), h = dp(29), w = getWidth();
            panel(c, new RectF(0, y, w, y + h), 0xFF111820, 0);
            float fs = Math.max(9f, Math.min(12f, minDp() * .030f));
            txt(c, now(), dp(9), y + dp(19), fs, Color.WHITE, Paint.Align.LEFT);
            txt(c, gpsSummary(), w * .48f, y + dp(19), fs, engine.gpsGood() ? green : 0xFFFFB84D, Paint.Align.CENTER);
            String roads = downloadingRoads ? "DOWNLOAD…" : (engine.ways != null && !engine.ways.isEmpty() ? "STRADE " + engine.ways.size() : tr("STRADE IN ATTESA"));
            txt(c, roads, w - dp(9), y + dp(19), fs, (downloadingRoads || (engine.ways != null && !engine.ways.isEmpty())) ? green : 0xFFFFB84D, Paint.Align.RIGHT);
        }

        void drawRide(Canvas c) {
            int w = getWidth(), h = getHeight();
            boolean land = w > h;
            Location l = engine.loc;
            Curve q = emptyCurve;
            drawTopStatus(c);
            float b = dp(10);
            float buttonTop = dp(land ? 39 : 54);

            if (land) {
                // Landscape: the left 55% is divided into NAV/header/speed zones;
                // the map owns the right side. No element is positioned relative to another
                // element's text bounds, which was the source of the previous collisions.
                float speedCx = w * .53f;
                float speedBase = h * .62f;
                drawSpeed(c, speedCx, speedBase, l == null ? 0 : speed(l), 0f, currentSpeedLimitKmh, true);
                drawRadarAlert(c, w * .15f, h * .18f, true);
                txt(c, String.format(Locale.US, "%.1f km", dailyKm), speedCx, h * .93f, 18, Color.WHITE, Paint.Align.CENTER);

                float mapW = Math.min(w * .31f, h * .70f);
                float mapH = Math.min(h * .62f, w * .29f);
                float mapCx = w * .82f, mapCy = h * .55f;
                mapBounds.set(mapCx - mapW / 2f, mapCy - mapH / 2f, mapCx + mapW / 2f, mapCy + mapH / 2f);
                drawRoadMap(c, mapBounds, l, false);

                // Clock and destination have their own two-line header above the map.
                drawAltitude(c, mapBounds.centerX(), Math.min(h - dp(8), mapBounds.bottom + dp(22)), l, true);

            } else {
                // Portrait: header -> navigation -> speed -> trip/map. Everything has a
                // dedicated vertical zone and the header never shares the map coordinates.
                float speedCx = w * .50f;
                float speedBase = h * .73f;
                drawRadarAlert(c, w * .82f, h * .125f, false);

                float mapW = w * .88f;
                float mapH = h * .105f;
                float mapCy = h * .91f;
                mapBounds.set((w - mapW) / 2f, mapCy - mapH / 2f, (w + mapW) / 2f, mapCy + mapH / 2f);

                drawSpeed(c, speedCx, speedBase, l == null ? 0 : speed(l), 0f, currentSpeedLimitKmh, false);
                txt(c, String.format(Locale.US, "%.1f km", dailyKm), speedCx, h * .84f, 17, Color.WHITE, Paint.Align.CENTER);

                drawRoadMap(c, mapBounds, l, false);
                drawAltitude(c, mapBounds.centerX(), Math.min(h - dp(6), mapBounds.bottom + dp(18)), l, false);
            }

            // Explicit app-exit control: keeps the rider from accidentally using Android's
            // system Back gesture/button, which can be interpreted as a map interaction.
            // It is deliberately small and isolated from the navigation controls.
            exitButtonBounds.set(w - dp(54), buttonTop, w - b, buttonTop + dp(26));
            tripsButtonBounds.set(w - dp(132), buttonTop, w - dp(58), buttonTop + dp(26));
            navButtonBounds.set(w - dp(208), buttonTop, w - dp(136), buttonTop + dp(26));
            settingsButtonBounds.set(w - dp(262), buttonTop, w - dp(216), buttonTop + dp(26));
            panel(c, settingsButtonBounds, 0xFF252A30, dp(7));
            stroke.setColor(0xFF7E8994); stroke.setStrokeWidth(dp(1)); stroke.setStyle(Paint.Style.STROKE); c.drawRoundRect(settingsButtonBounds, dp(7), dp(7), stroke); stroke.setStyle(Paint.Style.FILL);
            txt(c, "⚙", settingsButtonBounds.centerX(), settingsButtonBounds.centerY() + dp(5), 16, yellow, Paint.Align.CENTER);
            panel(c, navButtonBounds, navigationActive ? 0xFF6B5200 : 0xFF252A30, dp(7));
            stroke.setColor(navigationActive ? yellow : 0xFF7E8994); stroke.setStrokeWidth(dp(1)); stroke.setStyle(Paint.Style.STROKE); c.drawRoundRect(navButtonBounds, dp(7), dp(7), stroke); stroke.setStyle(Paint.Style.FILL);
            txt(c, navigationActive ? tr("NAV ON") : tr("NAV"), navButtonBounds.centerX(), navButtonBounds.centerY() + dp(4), 10, navigationActive ? yellow : Color.WHITE, Paint.Align.CENTER);
            panel(c, tripsButtonBounds, 0xFF252A30, dp(7));
            stroke.setColor(0xFF7E8994); stroke.setStrokeWidth(dp(1)); stroke.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(tripsButtonBounds, dp(7), dp(7), stroke); stroke.setStyle(Paint.Style.FILL);
            txt(c, "VIAGGI", tripsButtonBounds.centerX(), tripsButtonBounds.centerY() + dp(4), 10, yellow, Paint.Align.CENTER);
            panel(c, exitButtonBounds, 0xFF35191D, dp(7));
            stroke.setColor(0xFFB94A55); stroke.setStrokeWidth(dp(1)); stroke.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(exitButtonBounds, dp(7), dp(7), stroke); stroke.setStyle(Paint.Style.FILL);
            txt(c, "ESCI", exitButtonBounds.centerX(), exitButtonBounds.centerY() + dp(4), 9, 0xFFFFA0A8, Paint.Align.CENTER);
            weatherButtonBounds.set(dp(10), buttonTop, dp(78), buttonTop + dp(26));
            panel(c, weatherButtonBounds, 0xFF252A30, dp(7));
            stroke.setColor(0xFF7E8994); stroke.setStrokeWidth(dp(1)); c.drawRoundRect(weatherButtonBounds, dp(7), dp(7), stroke); stroke.setStyle(Paint.Style.FILL);
            txt(c, weatherLoading ? tr("METEO…") : tr("METEO ↻"), weatherButtonBounds.centerX(), weatherButtonBounds.centerY() + dp(4), 10, 0xFF8ED7FF, Paint.Align.CENTER);
            txt(c, airTemperature, weatherButtonBounds.centerX(), weatherButtonBounds.bottom + dp(34), 38.4f, 0xFFFFD66B, Paint.Align.CENTER);
            txt(c, weatherIcon(), weatherButtonBounds.right + dp(38), weatherButtonBounds.bottom + dp(34), 36f, 0xFFFFD66B, Paint.Align.CENTER);
            if (weatherAdverse || (showWeatherDetails && !weatherDetail.isEmpty())) {
                float stripBottom = buttonTop + dp(land ? 26 : 38);
                float weatherInfoRight = weatherButtonBounds.right + dp(48);
                RectF weatherStrip = new RectF(weatherInfoRight, buttonTop, Math.max(weatherInfoRight + dp(12), tripsButtonBounds.left - dp(5)), stripBottom);
                panel(c, weatherStrip, weatherAdverse ? 0xFFE00020 : 0xFF17212B, dp(4));
                String line1 = weatherAdverse ? "ATTENZIONE METEO" : weatherSummary;
                String line2 = weatherAdverse ? weatherSummary + " · " + weatherDetail : weatherDetail;
                float bannerSize = Math.max(7f, Math.min(9f, minDp() * .021f));
                p.setTextSize(bannerSize * density);
                float maxText = Math.max(dp(20), weatherStrip.width() - dp(8));
                while (p.measureText(line1) > maxText && bannerSize > 7f) { bannerSize -= .5f; p.setTextSize(bannerSize * density); }
                if (p.measureText(line1) > maxText) line1 = "METEO";
                if (p.measureText(line2) > maxText) { while (line2.length() > 8 && p.measureText(line2) > maxText) line2 = line2.substring(0, line2.length()-2) + "…"; }
                if (land) txt(c, line1 + " · " + line2, weatherStrip.centerX(), weatherStrip.centerY() + dp(3), bannerSize, Color.WHITE, Paint.Align.CENTER);
                else {
                    float mid = weatherStrip.centerY();
                    txt(c, line1, weatherStrip.centerX(), mid - dp(3), bannerSize, Color.WHITE, Paint.Align.CENTER);
                    txt(c, line2, weatherStrip.centerX(), mid + dp(10), bannerSize, Color.WHITE, Paint.Align.CENTER);
                }
            }

            // FINAL LAYOUT PASS: each information block is drawn exactly once.
            // Navigation has one dedicated rectangle; clock/destination have a separate
            // header above the speed display; battery is always bottom-right.
            if (navigationActive || navigationLoading) {
                float navLeft, navRight, navTop, navBottom;
                if (land) {
                    // Landscape: NAVIGATION -> SPEED LIMIT -> TACHOMETER.
                    // Keep the navigation block strictly on the left, with a dedicated
                    // centre lane for the legal-limit badge before the speed instrument.
                    navLeft = dp(12);
                    navRight = Math.min(speedBounds.left - dp(190), w * .34f);
                    navTop = dp(150);
                    navBottom = h * .84f;
                } else {
                    // Portrait: NAVIGATION occupies the left side of the upper band;
                    // the legal-limit badge sits immediately to its right. The tachometer
                    // remains below, centred, and is never used as the badge's anchor.
                    navLeft = dp(12);
                    navRight = Math.min(w * .61f, w - dp(150));
                    navTop = dp(131);
                    navBottom = Math.min(h * .41f, speedBounds.top - dp(105));
                }
                float navW = navRight - navLeft;
                float navH = navBottom - navTop;
                // Never let a transient tachometer resize or density calculation make the
                // guidance block disappear. If the preferred lane is too narrow, fall back
                // to a compact but always-visible guidance area.
                if (navW < dp(150)) { navRight = Math.min(speedBounds.left - dp(70), w * .43f); navW = Math.max(dp(120), navRight - navLeft); }
                if (navH < dp(105)) { navBottom = Math.max(navTop + dp(105), speedBounds.top - dp(20)); navH = navBottom - navTop; }
                drawNavigationGuidance(c, (navLeft + navRight) / 2f,
                        (navTop + navBottom) / 2f, land, navW, navH);
            }

            // Dedicated legal-speed lane: always between the navigation block and the
            // tachometer. It is deliberately independent from speedBounds so it cannot
            // drift underneath the map or collide with navigation text.
            // The legal speed limit is a primary riding datum, not a navigation-only datum.
            // It is therefore rendered whenever a real road limit is available, both in NAV
            // and during normal riding. Keep it in the dedicated lane between navigation and
            // the tachometer, with enough physical room for the full number inside the white field.
            if (currentSpeedLimitKmh > 0) {
                float limitCx;
                float limitCy;
                float badgeR;
                if (land) {
                    float navRightForBadge=Math.min(speedBounds.left-dp(70),w*.34f); limitCx=(navRightForBadge+speedBounds.left)*.5f; limitCy=h*.49f; badgeR=Math.min(dp(52),h*.105f);
                } else {
                    badgeR = Math.min(dp(42), w * .105f);
                    // Anchor the badge to the navigation band, not to a fixed pixel row:
                    // this keeps it visible on short displays and avoids the map/header.
                    limitCx = w * .76f;
                    float preferredY = dp(225);
                    float upperSafe = dp(150) + badgeR;
                    float lowerSafe = Math.max(upperSafe + dp(12), speedBounds.top - badgeR - dp(12));
                    limitCy = Math.max(upperSafe, Math.min(preferredY, lowerSafe));
                    limitCx = Math.max(badgeR + dp(8), Math.min(w - badgeR - dp(8), limitCx));
                }
                drawSpeedLimitBadge(c, limitCx, limitCy, badgeR, currentSpeedLimitKmh);
            }

            // Single prominent clock + destination header, always above the speed display.
            drawRideHeader(c, land, speedBounds);

            int bat = batteryPercent();
            if (bat >= 0) {
                int bc = bat <= 15 ? Color.RED : (bat <= 30 ? yellow : 0xFFB8C2CC);
                txt(c, "BAT " + bat + "%", w - dp(10), h - dp(10), 11, bc, Paint.Align.RIGHT);
            }
            txt(c, tr("AUDIO ON"), dp(12), h - dp(10), 11, green, Paint.Align.LEFT);
        }

        void drawRideHeader(Canvas c, boolean land, RectF speedRect) {
            int w = getWidth();
            float cx = land ? speedRect.centerX() : w * .50f;
            // Landscape header is part of the same centered instrument cluster as speed/trip.
            // Keep it below the GPS status row and just above the large speed digits.
            float timeY = land ? speedRect.top - dp(48) : speedRect.top - dp(70);
            float destY = land ? speedRect.top - dp(15) : speedRect.top - dp(30);
            float timeSize = land ? 27f : 27f;
            txt(c, now(), cx, timeY, timeSize, Color.WHITE, Paint.Align.CENTER);
            if (navigationActive && navigationDestinationLabel != null && !navigationDestinationLabel.isEmpty()) {
                String dest = navigationDestinationLabel.toUpperCase(Locale.ROOT);
                float maxW = land ? w * .40f : w * .72f;
                float fs = land ? 14f : 14f;
                p.setTypeface(TypefaceCompat.bold());
                p.setTextSize(fs * density);
                while (p.measureText(dest) > maxW && fs > 8f) { fs -= .5f; p.setTextSize(fs * density); }
                if (p.measureText(dest) > maxW) {
                    while (dest.length() > 8 && p.measureText(dest + "…") > maxW) dest = dest.substring(0, dest.length()-2);
                    dest += "…";
                }
                txt(c, "→ " + dest, cx, destY, fs, yellow, Paint.Align.CENTER);
            }
        }

        void drawMapHeader(Canvas c, RectF map, boolean land) {
            float center = map.centerX();
            float gap = land ? dp(7) : dp(6);
            float destY = map.top - gap;
            float timeY = destY - (land ? dp(20) : dp(22));
            String time = now();
            txt(c, time, center, timeY, land ? 22 : 20, Color.WHITE, Paint.Align.CENTER);
            if (navigationActive && navigationDestinationLabel != null && !navigationDestinationLabel.isEmpty()) {
                String dest = navigationDestinationLabel.toUpperCase(Locale.ROOT);
                float maxW = map.width() - dp(8);
                float fs = land ? 12f : 11f;
                p.setTypeface(TypefaceCompat.bold()); p.setTextSize(fs * density);
                while (p.measureText(dest) > maxW && fs > 8f) { fs -= .5f; p.setTextSize(fs * density); }
                if (p.measureText(dest) > maxW) {
                    while (dest.length() > 8 && p.measureText(dest + "…") > maxW) dest = dest.substring(0, dest.length()-2);
                    dest += "…";
                }
                txt(c, "→ " + dest, center, destY, fs, yellow, Paint.Align.CENTER);
            }
        }

        void drawRoadMap(Canvas c, RectF bounds, Location location, boolean expanded) {
            float radius = dp(7);
            panel(c, bounds, 0xFF080C10, radius);
            c.save();
            Path clip = new Path(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            c.clipPath(clip);
            boolean vector = expanded && vectorMapReady && vectorMapView != null && vectorMapView.getVisibility() == View.VISIBLE;
            boolean raster = vector ? true : drawRasterTiles(c, bounds, location, expanded);
            if (!vector && navigationActive && location != null) drawNavigationRoute(c, bounds, location, expanded);
            if (!raster) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(0.6f)); p.setColor(0xFF202A33);
                for (int i = 1; i < 4; i++) {
                    float x = bounds.left + bounds.width() * i / 4f;
                    float y = bounds.top + bounds.height() * i / 4f;
                    c.drawLine(x, bounds.top, x, bounds.bottom, p);
                    c.drawLine(bounds.left, y, bounds.right, y, p);
                }
                if (location != null && engine.mapWays != null && !engine.mapWays.isEmpty()) {
                    double lat0 = location.getLatitude(), lon0 = location.getLongitude();
                    double cosLat = Math.cos(Math.toRadians(lat0));
                    float bearing = hasMapBearing ? mapBearing : 0f;
                    double theta = Math.toRadians(bearing);
                    float scaleX = bounds.width() / 1000f, scaleY = bounds.height() / 1000f;
                    for (RoadWay way : engine.mapWays) {
                        if (way == null || way.pts == null || way.pts.size() < 2) continue;
                        Path road = new Path(); boolean started = false;
                        for (Geo point : way.pts) {
                            double east = (point.lon - lon0) * 111320.0 * cosLat;
                            double north = (point.lat - lat0) * 110540.0;
                            double eastRot = east * Math.cos(theta) - north * Math.sin(theta);
                            double northRot = east * Math.sin(theta) + north * Math.cos(theta);
                            float x = bounds.centerX() + (float)eastRot * scaleX;
                            float y = bounds.centerY() - (float)northRot * scaleY;
                            if (x < bounds.left - dp(10) || x > bounds.right + dp(10) || y < bounds.top - dp(10) || y > bounds.bottom + dp(10)) { started = false; continue; }
                            if (!started) { road.moveTo(x, y); started = true; } else road.lineTo(x, y);
                        }
                        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                        p.setStrokeWidth(dp(3)); p.setColor(0xFF59636D); c.drawPath(road, p);
                        p.setStrokeWidth(dp(1.1f)); p.setColor(0xFFD5DDE4); c.drawPath(road, p);
                    }
                }
                String status = downloadingRoads ? tr("SCARICO STRADE…") :
                        (location == null ? tr("GPS IN RICERCA") : (!tilesLoading.isEmpty() ? tr("CARICAMENTO MAPPA…") :
                        (tileFailures > 0 ? tr("MAPPA NON DISPONIBILE") : (roadDataStatus.isEmpty() ? tr("MAPPA IN ATTESA") : roadDataStatus))));
                float statusSize = Math.max(8, Math.min(expanded ? 14 : 10, minDp() * .026f));
                txt(c, status, bounds.centerX(), bounds.bottom - dp(expanded ? 30 : 5), statusSize, 0xFFB8C2CC, Paint.Align.CENTER);
            }
            drawRadarMarkers(c, bounds, location, expanded);
            // Attribution is kept visible in both compact and expanded map views.
            String attribution = "© OpenStreetMap contributors";
            float attribSize = expanded ? 9f : 6.5f;
            p.setTextSize(attribSize * density);
            float attribWidth = p.measureText(attribution) + dp(6);
            RectF attrib = new RectF(bounds.right - attribWidth - dp(2), bounds.bottom - dp(expanded ? 21 : 13), bounds.right - dp(2), bounds.bottom - dp(2));
            panel(c, attrib, 0xD9000000, dp(2));
            txt(c, attribution, attrib.right - dp(3), attrib.bottom - dp(2), attribSize, Color.WHITE, Paint.Align.RIGHT);
            c.restore();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f)); p.setColor(Color.WHITE);
            c.drawRoundRect(bounds, radius, radius, p); p.setStyle(Paint.Style.FILL);
            // GPS marker is projected from its real coordinate; it is not artificially pinned to the viewport.
            float markerSize=Math.min(bounds.width(),bounds.height())*(expanded?.045f:.10f);float markerCx=bounds.centerX(),markerCy=bounds.centerY();
            if(location!=null){double centerLat=expanded&&!Double.isNaN(mapViewLat)?mapViewLat:mapAnchorLat,centerLon=expanded&&!Double.isNaN(mapViewLon)?mapViewLon:mapAnchorLon;if(Double.isNaN(centerLat)||Double.isNaN(centerLon)){centerLat=location.getLatitude();centerLon=location.getLongitude();}
                double targetMarkerLat=location.getLatitude(),targetMarkerLon=location.getLongitude(); float targetMarkerBearing=!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f);
                if(navigationActive&&!Double.isNaN(navigationMatchedLat)&&!Double.isNaN(navigationMatchedLon)){targetMarkerLat=navigationMatchedLat;targetMarkerLon=navigationMatchedLon;}
                else if(location.hasAccuracy() && location.getAccuracy() <= 20f){Location roadSnap=engine.mapMatchedLocation(location);if(roadSnap!=null){targetMarkerLat=roadSnap.getLatitude();targetMarkerLon=roadSnap.getLongitude();}}
                if(Double.isNaN(smoothMarkerLat)||Double.isNaN(smoothMarkerLon)){smoothMarkerLat=targetMarkerLat;smoothMarkerLon=targetMarkerLon;smoothMarkerBearing=targetMarkerBearing;} else {
                    Location sm=new Location("marker-smooth");sm.setLatitude(smoothMarkerLat);sm.setLongitude(smoothMarkerLon);Location tg=new Location("marker-target");tg.setLatitude(targetMarkerLat);tg.setLongitude(targetMarkerLon);
                    float targetGap=sm.distanceTo(tg);
                    // Avoid multi-second lag at motorway speed and snap immediately after a large, credible GPS jump.
                    double alpha=targetGap>45f?1.0:(location.hasSpeed()&&location.getSpeed()>18f?0.72:0.55);
                    smoothMarkerLat+=(targetMarkerLat-smoothMarkerLat)*alpha;smoothMarkerLon+=(targetMarkerLon-smoothMarkerLon)*alpha;
                    if(Float.isNaN(smoothMarkerBearing))smoothMarkerBearing=targetMarkerBearing;else{float bd=targetMarkerBearing-smoothMarkerBearing;while(bd>180)bd-=360;while(bd<-180)bd+=360;smoothMarkerBearing=normalizeBearing(smoothMarkerBearing+bd*(location.hasSpeed()&&location.getSpeed()>18f?0.55f:0.32f));}}
                double markerLat=smoothMarkerLat,markerLon=smoothMarkerLon;
                int z=expanded?Math.max(15,Math.min(19,15+Math.round((mapZoom-.5f)*4f))):Math.max(16,Math.min(19,16+Math.round((mapZoomCompact-.8f)*4.3f)));double world=256.0*(1<<z);
                double sc=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,centerLat)))),ccx=(centerLon+180.0)/360.0*world,ccy=(.5-Math.log((1+sc)/(1-sc))/(4*Math.PI))*world;
                double sl=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,markerLat)))),lcx=(markerLon+180.0)/360.0*world,lcy=(.5-Math.log((1+sl)/(1-sl))/(4*Math.PI))*world;double br=Math.toRadians(expanded?mapFrozenBearing:(!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f)));
                double dx=lcx-ccx,dy=lcy-ccy,sx=dx*Math.cos(br)+dy*Math.sin(br),sy=-dx*Math.sin(br)+dy*Math.cos(br);markerCx+=(float)sx;markerCy+=(float)sy;}
            c.save();c.clipRect(bounds);Path bike=new Path();bike.moveTo(markerCx,markerCy-markerSize*.72f);bike.lineTo(markerCx-markerSize*.46f,markerCy+markerSize*.48f);bike.lineTo(markerCx,markerCy+markerSize*.20f);bike.lineTo(markerCx+markerSize*.46f,markerCy+markerSize*.48f);bike.close();stroke.setStyle(Paint.Style.STROKE);stroke.setColor(Color.BLACK);stroke.setStrokeWidth(dp(expanded?3:2));c.drawPath(bike,stroke);p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFD400);c.drawPath(bike,p);
            if(expanded&&!Double.isNaN(selectedMapLat)&&!Double.isNaN(selectedMapLon)){float[] pt=mapScreenPoint(selectedMapLat,selectedMapLon,bounds);if(pt!=null){float fs=Math.max(dp(18),Math.min(dp(30),Math.min(bounds.width(),bounds.height())*.035f));stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2));stroke.setColor(Color.BLACK);c.drawLine(pt[0],pt[1]+fs*.55f,pt[0],pt[1]-fs*.35f,stroke);Path flag=new Path();flag.moveTo(pt[0],pt[1]-fs*.35f);flag.lineTo(pt[0]+fs*.65f,pt[1]-fs*.12f);flag.lineTo(pt[0],pt[1]+fs*.05f);flag.close();p.setStyle(Paint.Style.FILL);p.setColor(Color.RED);c.drawPath(flag,p);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(1.5f));stroke.setColor(Color.WHITE);c.drawPath(flag,stroke);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawCircle(pt[0],pt[1],dp(3),p);}}
            c.restore();
            txt(c, expanded ? tr("MAPPA · TRASCINA PER SCORRERE") : tr("MAPPA"), bounds.centerX(), bounds.top + dp(expanded ? 23 : 13),
                    Math.max(8, Math.min(expanded ? 14 : 10, minDp() * .026f)), Color.WHITE, Paint.Align.CENTER);
            if (expanded && !Double.isNaN(selectedMapLat) && !Double.isNaN(selectedMapLon)) {
                String place = selectedMapPlaceName.isEmpty() ? tr("RICERCA LUOGO…") : selectedMapPlaceName;
                float fs = Math.max(10f, Math.min(17f, minDp() * .032f));
                float maxW = bounds.width() * .72f; p.setTextSize(fs * density);
                while (p.measureText(place) > maxW && fs > 8f) { fs -= .5f; p.setTextSize(fs * density); }
                panel(c, new RectF(bounds.centerX()-maxW/2f, bounds.bottom-dp(62), bounds.centerX()+maxW/2f, bounds.bottom-dp(34)), 0xD9000000, dp(7));
                txt(c, place, bounds.centerX(), bounds.bottom-dp(43), fs, Color.WHITE, Paint.Align.CENTER);
            }
            // Map zoom controls are deliberately small and kept inside the map frame.
            float zbtn = dp(expanded ? 48 : 36);
            RectF zin = new RectF(bounds.right-zbtn-dp(6), bounds.bottom-zbtn*2-dp(10), bounds.right-dp(6), bounds.bottom-zbtn-dp(10));
            RectF zout = new RectF(bounds.right-zbtn-dp(6), bounds.bottom-zbtn-dp(6), bounds.right-dp(6), bounds.bottom-dp(6));
            panel(c, zin, 0xCC18212A, dp(6)); panel(c, zout, 0xCC18212A, dp(6));
            txt(c, "+", zin.centerX(), zin.centerY()+dp(expanded?8:6), expanded?24:17, Color.WHITE, Paint.Align.CENTER);
            txt(c, "−", zout.centerX(), zout.centerY()+dp(expanded?6:5), expanded?24:17, Color.WHITE, Paint.Align.CENTER);
            if (location != null && !hasMapBearing) txt(c, tr("DIREZIONE IN ATTESA"), bounds.centerX(), bounds.bottom - dp(expanded ? 28 : 15),
                    Math.max(8, Math.min(10, minDp() * .024f)), 0xFFFFD400, Paint.Align.CENTER);
        }

        void drawRadarMarkers(Canvas c, RectF bounds, Location location, boolean expanded) {
            ArrayList<RadarPoint> snapshot = engine == null ? null : engine.radars;
            if (snapshot == null || snapshot.isEmpty() || location == null) return;
            double centerLat = expanded && !Double.isNaN(mapViewLat) ? mapViewLat : mapAnchorLat;
            double centerLon = expanded && !Double.isNaN(mapViewLon) ? mapViewLon : mapAnchorLon;
            if (Double.isNaN(centerLat) || Double.isNaN(centerLon)) { centerLat = location.getLatitude(); centerLon = location.getLongitude(); }
            int z = expanded ? Math.max(15, Math.min(19, 15 + Math.round((mapZoom - .5f) * 4f))) : Math.max(16, Math.min(19, 16 + Math.round((mapZoomCompact - .8f) * 4.3f)));
            double world = 256.0 * (1 << z);
            double s0 = Math.sin(Math.toRadians(Math.max(-85, Math.min(85, centerLat))));
            double cx = (centerLon + 180.0) / 360.0 * world, cy = (.5 - Math.log((1 + s0) / (1 - s0)) / (4 * Math.PI)) * world;
            double bearing = Math.toRadians(expanded ? mapFrozenBearing : (!Float.isNaN(navigationMatchedBearing) ? navigationMatchedBearing : (hasMapBearing ? mapBearing : 0f)));
            double mapScale = expanded ? 1.0 : 1.15;
            float radius = dp(expanded ? 10 : 7);
            c.save(); c.clipRect(bounds);
            for (RadarPoint radar : snapshot) {
                if (radar == null || !Double.isFinite(radar.lat) || !Double.isFinite(radar.lon)) continue;
                float x, y;
                // In the full-screen MapLibre view, use the renderer's own projection.
                // This keeps each radar pinned to its geographic coordinate while the
                // user pans, zooms, or rotates the native map; compact/raster maps retain
                // the existing Canvas projection below.
                boolean nativeFullScreenProjection = expanded && vectorMapReady && vectorMap != null
                        && vectorMapView != null && vectorMapView.getVisibility() == View.VISIBLE;
                if (nativeFullScreenProjection) {
                    try {
                        android.graphics.PointF screen = vectorMap.getProjection().toScreenLocation(new LatLng(radar.lat, radar.lon));
                        x = screen.x;
                        y = screen.y;
                    } catch (Throwable projectionFailure) {
                        double ss = Math.sin(Math.toRadians(Math.max(-85, Math.min(85, radar.lat))));
                        double px = (radar.lon + 180.0) / 360.0 * world, py = (.5 - Math.log((1 + ss) / (1 - ss)) / (4 * Math.PI)) * world;
                        double dx = px - cx, dy = py - cy;
                        x = bounds.centerX() + (float)((dx * Math.cos(bearing) + dy * Math.sin(bearing)) * mapScale);
                        y = bounds.centerY() + (float)((-dx * Math.sin(bearing) + dy * Math.cos(bearing)) * mapScale);
                    }
                } else {
                    double ss = Math.sin(Math.toRadians(Math.max(-85, Math.min(85, radar.lat))));
                    double px = (radar.lon + 180.0) / 360.0 * world, py = (.5 - Math.log((1 + ss) / (1 - ss)) / (4 * Math.PI)) * world;
                    double dx = px - cx, dy = py - cy;
                    x = bounds.centerX() + (float)((dx * Math.cos(bearing) + dy * Math.sin(bearing)) * mapScale);
                    y = bounds.centerY() + (float)((-dx * Math.sin(bearing) + dy * Math.cos(bearing)) * mapScale);
                }
                if (x < bounds.left - radius || x > bounds.right + radius || y < bounds.top - radius || y > bounds.bottom + radius) continue;
                p.setStyle(Paint.Style.FILL); p.setColor(0xFFEA3323); c.drawCircle(x, y, radius, p);
                stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1.5f)); stroke.setColor(Color.WHITE); c.drawCircle(x, y, radius, stroke);
                // Simple radar dish glyph centred exactly on the stored coordinate.
                stroke.setStrokeWidth(dp(expanded ? 1.8f : 1.2f)); stroke.setColor(Color.WHITE);
                c.drawLine(x - radius * .45f, y + radius * .28f, x + radius * .45f, y + radius * .28f, stroke);
                c.drawLine(x - radius * .12f, y + radius * .18f, x + radius * .28f, y - radius * .35f, stroke);
                p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); c.drawCircle(x - radius * .25f, y - radius * .25f, radius * .10f, p);
            }
            c.restore(); p.setStyle(Paint.Style.FILL); stroke.setStyle(Paint.Style.STROKE);
        }

        void drawNavigationRoute(Canvas c, RectF bounds, Location location, boolean expanded) {
            ArrayList<RoutePoint> snapshot;
            synchronized(navigationLock){ if(navigationRoute.isEmpty()) return; snapshot=new ArrayList<>(navigationRoute); }
            final int zoom=expanded?Math.max(15,Math.min(19,15+Math.round((mapZoom-.5f)*4f))):Math.max(16,Math.min(19,16+Math.round((mapZoomCompact-.8f)*4.3f)));
            double lat0,lon0;
            if(expanded&&!Double.isNaN(mapViewLat)&&!Double.isNaN(mapViewLon)){lat0=mapViewLat;lon0=mapViewLon;}
            else{lat0=Math.max(-85,Math.min(85,Double.isNaN(mapAnchorLat)?location.getLatitude():mapAnchorLat));lon0=Double.isNaN(mapAnchorLon)?location.getLongitude():mapAnchorLon;}
            double world=256.0*(1<<zoom);double cx=(lon0+180.0)/360.0*world;double ss=Math.sin(Math.toRadians(lat0));double cy=(.5-Math.log((1+ss)/(1-ss))/(4*Math.PI))*world;
            float scale=expanded?1f:1.15f;Path route=new Path();boolean started=false;
            float renderBearing=expanded?mapFrozenBearing:(!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f));
            c.save();c.clipRect(bounds);c.translate(bounds.centerX(),bounds.centerY());c.rotate(-renderBearing);
            for(RoutePoint rp:snapshot){double yy=Math.max(-85,Math.min(85,rp.lat));double s=Math.sin(Math.toRadians(yy));double px=(rp.lon+180.0)/360.0*world;double py=(.5-Math.log((1+s)/(1-s))/(4*Math.PI))*world;float x=(float)((px-cx)*scale),y=(float)((py-cy)*scale);if(!started){route.moveTo(x,y);started=true;}else route.lineTo(x,y);}
            stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);stroke.setColor(Color.BLACK);stroke.setStrokeWidth(dp(expanded?10:5));c.drawPath(route,stroke);stroke.setColor(yellow);stroke.setStrokeWidth(dp(expanded?5:2.8f));c.drawPath(route,stroke);c.restore();
        }

        void prioritizeVisibleRouteTiles(int zoom, int minX, int maxX, int minY, int maxY, double world) {
            if (!navigationActive) return;
            long now = System.currentTimeMillis();
            if (now - lastRouteTilePriorityAt < 700L) return;
            lastRouteTilePriorityAt = now;
            ArrayList<RoutePoint> snapshot;
            synchronized(navigationLock) {
                if (navigationRoute.isEmpty()) return;
                snapshot = new ArrayList<>(navigationRoute);
            }
            int tileCount = 1 << zoom;
            HashSet<String> priority = new HashSet<>();
            // Sample the route rather than walking/requesting it on every frame.
            // A route can contain hundreds or thousands of geometry points.
            int stride = Math.max(1, snapshot.size() / 180);
            for (int i = 0; i < snapshot.size(); i += stride) {
                RoutePoint rp = snapshot.get(i);
                if (rp == null) continue;
                double yy = Math.max(-85.0, Math.min(85.0, rp.lat));
                double ss = Math.sin(Math.toRadians(yy));
                double px = (rp.lon + 180.0) / 360.0 * world;
                double py = (0.5 - Math.log((1.0 + ss) / (1.0 - ss)) / (4.0 * Math.PI)) * world;
                int tx = (int)Math.floor(px / 256.0);
                int ty = (int)Math.floor(py / 256.0);
                if (tx < minX - 1 || tx > maxX + 1 || ty < minY - 1 || ty > maxY + 1) continue;
                for (int ox=-1; ox<=1; ox++) for (int oy=-1; oy<=1; oy++) {
                    int qx=tx+ox, qy=ty+oy;
                    if(qx<minX||qx>maxX||qy<minY||qy>maxY||qy<0||qy>=tileCount) continue;
                    int wx=((qx%tileCount)+tileCount)%tileCount;
                    priority.add(zoom+"/"+wx+"/"+qy);
                }
            }
            // Route tiles first, adjacent tiles second. Keep this deliberately small:
            // fullscreen rendering must not flood the worker queue with dozens of
            // simultaneous tile downloads, otherwise panning becomes visibly jerky.
            int requested=0;
            for(String key: priority) {
                if(requested>=6) break;
                String[] a=key.split("/");
                if(a.length!=3) continue;
                int x,y; try { x=Integer.parseInt(a[1]); y=Integer.parseInt(a[2]); } catch(Exception ignored){continue;}
                Bitmap b=tileBitmaps.get(key);
                if(b==null||b.isRecycled()){
                    Long retryAt=tileRetryAfter.get(key);
                    if(retryAt==null||now>=retryAt) { requestTile(zoom,x,y,key); requested++; }
                }
            }
        }

        boolean drawRasterTiles(Canvas c, RectF bounds, Location location, boolean expanded) {
            if(location==null)return false;
            final int zoom=tripMapRendering?Math.max(10,Math.min(17,tripMapZoom)):(expanded?Math.max(15,Math.min(19,15+Math.round((mapZoom-.5f)*4f))):Math.max(16,Math.min(19,16+Math.round((mapZoomCompact-.8f)*4.3f))));
            double lat=Math.max(-85.0,Math.min(85.0,location.getLatitude())),lon=location.getLongitude();
            if(expanded){if(Double.isNaN(mapViewLat)||Double.isNaN(mapViewLon)){mapViewLat=lat;mapViewLon=lon;}lat=Math.max(-85,Math.min(85,mapViewLat));lon=mapViewLon;}
            else{if(Double.isNaN(mapAnchorLat)||mapAnchorLocation==null||mapAnchorLocation.distanceTo(location)>=40f||System.currentTimeMillis()-lastTileAnchorUpdate>30000L){mapAnchorLat=lat;mapAnchorLon=lon;mapAnchorLocation=new Location(location);lastTileAnchorUpdate=System.currentTimeMillis();}lat=mapAnchorLat;lon=mapAnchorLon;}
            double world=256.0*(1<<zoom);double centerX=(lon+180.0)/360.0*world;double sin=Math.sin(Math.toRadians(lat));double centerY=(.5-Math.log((1.0+sin)/(1.0-sin))/(4.0*Math.PI))*world;
            float scale=expanded?1f:1.15f;if(scale<=0f)return false;
            float renderBearing=expanded?mapFrozenBearing:(!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f));double angle=Math.toRadians(renderBearing);double ca=Math.abs(Math.cos(angle)),sa=Math.abs(Math.sin(angle));
            double halfX=(ca*bounds.width()+sa*bounds.height())/(2.0*scale)+256.0;double halfY=(sa*bounds.width()+ca*bounds.height())/(2.0*scale)+256.0;
            int tileCount=1<<zoom;int minX=(int)Math.floor((centerX-halfX)/256.0)-1,maxX=(int)Math.floor((centerX+halfX)/256.0)+1;int minY=Math.max(0,(int)Math.floor((centerY-halfY)/256.0)-1),maxY=Math.min(tileCount-1,(int)Math.floor((centerY+halfY)/256.0)+1);
            prioritizeVisibleRouteTiles(zoom,minX,maxX,minY,maxY,world);
            boolean any=false;int requestsThisFrame=0;final int maxRequestsThisFrame=expanded?12:8;
            c.save();c.translate(bounds.centerX(),bounds.centerY());c.rotate(-renderBearing);p.setStyle(Paint.Style.FILL);
            for(int tx=minX;tx<=maxX;tx++){int wrappedX=((tx%tileCount)+tileCount)%tileCount;for(int ty=minY;ty<=maxY;ty++){String key=zoom+"/"+wrappedX+"/"+ty;Bitmap bitmap=tileBitmaps.get(key);
                float left=(float)(tx*256.0-centerX)*scale,top=(float)(ty*256.0-centerY)*scale;
                if(bitmap==null||bitmap.isRecycled()){Bitmap stable=stableTileBitmaps.get(key);if(stable!=null&&!stable.isRecycled()){c.drawBitmap(stable,null,new RectF(left,top,left+256f*scale,top+256f*scale),p);any=true;}else{if(requestsThisFrame<maxRequestsThisFrame){requestTile(zoom,wrappedX,ty,key);requestsThisFrame++;}Bitmap parent=drawParentTile(c,zoom,wrappedX,ty,tx,centerX,centerY,scale);if(parent!=null)any=true;else{p.setColor(0xFF202830);c.drawRect(new RectF(left,top,left+256f*scale,top+256f*scale),p);}}continue;}
                if(isTileStale(zoom,wrappedX,ty)){Long retryAt=tileRetryAfter.get(key);if(requestsThisFrame<maxRequestsThisFrame&&(retryAt==null||System.currentTimeMillis()>=retryAt)){requestTile(zoom,wrappedX,ty,key);requestsThisFrame++;}}
                c.drawBitmap(bitmap,null,new RectF(left,top,left+256f*scale,top+256f*scale),p);any=true;
            }}
            c.restore();return any;
        }

        File tileDirectory() {
            File dir = new File(getContext().getFilesDir(), "maptiles");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }

        File tileImageFile(int z, int x, int y) { return new File(tileDirectory(), z + "_" + x + "_" + y + ".png"); }
        File tileExpiryFile(int z, int x, int y) { return new File(tileDirectory(), z + "_" + x + "_" + y + ".exp"); }

        boolean isTileStale(int z, int x, int y) {
            Long expiry = tileExpiries.get(z + "/" + x + "/" + y);
            return expiry != null && expiry <= System.currentTimeMillis();
        }

        // Draw a parent tile quadrant while the sharper child tile downloads, avoiding black squares.
        Bitmap drawParentTile(Canvas c, int z, int x, int y, int wrappedX, double centerX, double centerY, float scale) {
            for (int level = z - 1; level >= Math.max(1, z - 4); level--) {
                int shift = z - level;
                int px = x >> shift, py = y >> shift;
                int parentCount = 1 << level;
                px = ((px % parentCount) + parentCount) % parentCount;
                if (py < 0 || py >= parentCount) continue;
                String parentKey = level + "/" + px + "/" + py;
                Bitmap parent = tileBitmaps.get(parentKey);
                if (parent == null || parent.isRecycled()) { Long retryAt = tileRetryAfter.get(parentKey); if (retryAt == null || System.currentTimeMillis() >= retryAt) requestTile(level, px, py, parentKey); continue; }
                int quadrant = 1 << shift;
                int srcSize = 256 / quadrant;
                int sx = (x & (quadrant - 1)) * srcSize, sy = (y & (quadrant - 1)) * srcSize;
                android.graphics.Rect src = new android.graphics.Rect(sx, sy, sx + srcSize, sy + srcSize);
                float left = (float)(wrappedX * 256.0 - centerX) * scale;
                float top = (float)(y * 256.0 - centerY) * scale;
                RectF dest = new RectF(left, top, left + 256f * scale, top + 256f * scale);
                c.drawBitmap(parent, src, dest, p);
                return parent;
            }
            return null;
        }

        void requestTile(int z, int x, int y, String key) {
            if (!tilesLoading.add(key)) return;
            Long retry = tileRetryAfter.get(key);
            if (retry != null && System.currentTimeMillis() < retry) { tilesLoading.remove(key); return; }
            tileExecutor.execute(() -> {
                try {
                    File dir = tileDirectory();
                    String safe = "osm_v2_" + z + "_" + x + "_" + y;
                    File imageFile = new File(dir, safe + ".png"), expiryFile = new File(dir, safe + ".exp");
                    // Migrate a previously cached tile lazily on the worker thread, never during drawing.
                    File legacyImage = new File(new File(getContext().getCacheDir(), "maptiles"), safe + ".png");
                    File legacyExpiry = new File(new File(getContext().getCacheDir(), "maptiles"), safe + ".exp");
                    if (!imageFile.exists() && legacyImage.isFile()) {
                        try (InputStream in = new java.io.FileInputStream(legacyImage); FileOutputStream out = new FileOutputStream(imageFile)) {
                            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                            if (legacyExpiry.isFile()) try (InputStream ei = new java.io.FileInputStream(legacyExpiry); FileOutputStream eo = new FileOutputStream(expiryFile)) { while ((n = ei.read(buffer)) > 0) eo.write(buffer, 0, n); }
                        } catch (Exception ignored) { }
                    }
                    long expiry = 0L;
                    if (expiryFile.exists()) try { expiry = Long.parseLong(new String(java.nio.file.Files.readAllBytes(expiryFile.toPath()), "UTF-8").trim()); } catch (Exception ignored) { }
                    if (imageFile.exists() && expiry == 0L) expiry = imageFile.lastModified() + 30L * 86400000L;
                    if (imageFile.exists()) tileExpiries.put(key, expiry);
                    boolean fresh = imageFile.exists() && expiry > System.currentTimeMillis();
                    if (imageFile.exists()) {
                        Bitmap cached = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                        if (cached != null) { tileBitmaps.put(key, cached); stableTileBitmaps.put(key, cached); }
                    }
                    if (fresh) return; // Keep the on-disk tile and avoid needless requests.
                    // CARTO raster endpoints now return an "API key required" watermark
                    // without a project-owned key. Use the public OSM raster endpoint directly.
                    // Keep the provider configurable in code; do not silently accept HTTP-200
                    // watermark images as valid map tiles.
                    // Providers are used sequentially, never in parallel. This keeps the
                    // primary OSM service protected while still giving the map a resilient
                    // fallback path when a provider is temporarily unavailable.
                    String subdomain = new String[]{"a","b","c"}[Math.floorMod(x + y, 3)];
                    String[] urls = {
                        "https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png",
                        "https://" + subdomain + ".tile.openstreetmap.fr/osmfr/" + z + "/" + x + "/" + y + ".png",
                        "https://" + subdomain + ".tile.openstreetmap.fr/hot/" + z + "/" + x + "/" + y + ".png",
                        "https://" + subdomain + ".tile-cyclosm.openstreetmap.fr/cyclosm-lite/" + z + "/" + x + "/" + y + ".png"
                    };
                    for (int provider = 0; provider < urls.length; provider++) {
                        HttpURLConnection conn = null;
                        try {
                            conn = (HttpURLConnection)new URL(urls[provider]).openConnection();
                            conn.setConnectTimeout(3000); conn.setReadTimeout(7000);
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("User-Agent", "RoadDisplayPRO/3.22.17 (Android motorcycle dashboard; map attribution visible)");
                            conn.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");
                            int code = conn.getResponseCode();
                            if (code != HttpURLConnection.HTTP_OK) continue;
                            byte[] bytes;
                            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                                byte[] buffer = new byte[8192]; int n;
                                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                                bytes = out.toByteArray();
                            }
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bitmap == null) continue;
                            try (FileOutputStream out = new FileOutputStream(imageFile)) { out.write(bytes); }
                            long maxAge = 30L * 86400L;
                            String cacheControl = conn.getHeaderField("Cache-Control");
                            if (cacheControl != null) {
                                Matcher matcher = Pattern.compile("max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(cacheControl);
                                if (matcher.find()) try { maxAge = Math.max(60L, Long.parseLong(matcher.group(1))); } catch (Exception ignored) { }
                            }
                            long tileExpiry = System.currentTimeMillis() + maxAge * 1000L;
                            try (FileOutputStream out = new FileOutputStream(expiryFile)) { out.write(Long.toString(tileExpiry).getBytes("UTF-8")); }
                            tileExpiries.put(key, tileExpiry);
                            tileBitmaps.put(key, bitmap);
                            stableTileBitmaps.put(key, bitmap);
                            tileFailures = Math.max(0, tileFailures - 1);
                            tileRetryAfter.remove(key);
                            return;
                        } catch (Exception ignored) { }
                        finally { if (conn != null) conn.disconnect(); }
                    }
                    tileFailures++;
                    tileRetryAfter.put(key, System.currentTimeMillis() + 60000L);
                } finally {
                    tilesLoading.remove(key);
                    postInvalidate();
                }
            });
        }

        void releaseTiles() {
            tileExecutor.shutdownNow();
            tileBitmaps.evictAll();
            stableTileBitmaps.evictAll();
        }


        String fmtTripDuration(long ms) {
            long sec=Math.max(0,ms/1000), h=sec/3600, m=(sec%3600)/60;
            return h>0 ? String.format(Locale.getDefault(),"%dh %02dm",h,m) : String.format(Locale.getDefault(),"%dm",m);
        }
        String fmtTripDate(long t) { return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(t)); }

        String tripDayKey(long t) { return new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date(t)); }
        String tripDayLabel(String key) {
            try {
                Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).parse(key);
                return new SimpleDateFormat("EEEE dd MMMM yyyy",Locale.getDefault()).format(d);
            } catch(Exception e) { return key; }
        }
        java.util.LinkedHashMap<String,java.util.ArrayList<TripDb.TripSummary>> groupTripsByDay(java.util.List<TripDb.TripSummary> list) {
            java.util.LinkedHashMap<String,java.util.ArrayList<TripDb.TripSummary>> groups=new java.util.LinkedHashMap<>();
            for(TripDb.TripSummary t:list){String k=tripDayKey(t.startTime); java.util.ArrayList<TripDb.TripSummary> g=groups.get(k); if(g==null){g=new java.util.ArrayList<>();groups.put(k,g);} g.add(t);}
            return groups;
        }
        void drawTrips(Canvas c) {
            int w=getWidth(), h=getHeight(); c.drawColor(0xFF070A0D);
            panel(c,new RectF(0,0,w,dp(58)),0xFF141B22,0);
            txt(c,"DIARIO VIAGGI",dp(16),dp(37),22,yellow,Paint.Align.LEFT);
            // The working RIDE action is kept in the tool row below. Do not draw a
            // second RIDE button in the title bar: the old upper button had no reliable
            // touch target and duplicated the same action.
            java.util.List<TripDb.TripSummary> list=tripDb==null?new java.util.ArrayList<>():tripDb.listTrips(500);
            java.util.LinkedHashMap<String,java.util.ArrayList<TripDb.TripSummary>> groups=groupTripsByDay(list);
            double total=0; for(TripDb.TripSummary t:list) total+=t.distanceM/1000.0;
            txt(c,String.format(Locale.getDefault(),"GIORNI  %d",groups.size()),dp(16),dp(84),15,Color.WHITE,Paint.Align.LEFT);
            txt(c,String.format(Locale.getDefault(),"TOTALE  %.1f km",total),w-dp(16),dp(84),15,0xFF65D7FF,Paint.Align.RIGHT);
            float toolsY=dp(92), toolGap=dp(8), toolW=(w-dp(32)-toolGap*2)/3f;
            gpxButtonBounds.set(dp(10),toolsY,dp(10)+toolW,toolsY+dp(38)); panel(c,gpxButtonBounds,0xFF1D2A34,dp(7)); txt(c,"GPX",gpxButtonBounds.centerX(),toolsY+dp(24),12,yellow,Paint.Align.CENTER);
            memoryButtonBounds.set(gpxButtonBounds.right+toolGap,toolsY,gpxButtonBounds.right+toolGap+toolW,toolsY+dp(38)); panel(c,memoryButtonBounds,0xFF1D2A34,dp(7)); txt(c,"MEMORIA",memoryButtonBounds.centerX(),toolsY+dp(24),12,0xFF65D7FF,Paint.Align.CENTER);
            tripsCloseBounds.set(w-dp(104),toolsY,w-dp(8),toolsY+dp(38)); panel(c,tripsCloseBounds,0xFF26313A,dp(7)); txt(c,"← RIDE",tripsCloseBounds.centerX(),toolsY+dp(24),12,Color.WHITE,Paint.Align.CENTER);
            if(tripRecording){panel(c,new RectF(dp(12),dp(98),w-dp(12),dp(132)),0xFF153323,dp(7));txt(c,"● REGISTRAZIONE VIAGGIO IN CORSO",w/2f,dp(121),13,green,Paint.Align.CENTER);}
            float top=tripRecording?dp(176):dp(140), rowH=Math.max(dp(82),Math.min(dp(98),h*.11f)), bottom=h-dp(10);
            c.save(); c.clipRect(0,top,w,bottom);
            java.util.ArrayList<String> keys=new java.util.ArrayList<>(groups.keySet());
            for(int i=0;i<keys.size();i++){
                String key=keys.get(i); java.util.ArrayList<TripDb.TripSummary> g=groups.get(key); float yy=top+i*rowH-tripsScroll; if(yy>bottom||yy+rowH<top)continue;
                double km=0; for(TripDb.TripSummary t:g) km+=t.distanceM/1000.0;
                panel(c,new RectF(dp(10),yy,w-dp(10),yy+rowH-dp(5)),0xFF10171E,dp(8));
                txt(c,tripDayLabel(key),dp(20),yy+dp(27),16,Color.WHITE,Paint.Align.LEFT);
                txt(c,String.format(Locale.getDefault(),"%d %s",g.size(),g.size()==1?"VIAGGIO":"VIAGGI"),w-dp(20),yy+dp(27),15,yellow,Paint.Align.RIGHT);
                txt(c,String.format(Locale.getDefault(),"%.1f km totali",km),dp(20),yy+dp(53),12,0xFF65D7FF,Paint.Align.LEFT);
                txt(c,"TOCCA PER APRIRE LA GIORNATA  ›",w-dp(20),yy+dp(53),10,0xFFB7C0C8,Paint.Align.RIGHT);
            }
            c.restore();
            if(groups.isEmpty()) txt(c,"Nessun viaggio registrato. I viaggi vengono registrati automaticamente con GPS preciso e movimento reale.",w/2f,h*.55f,14,0xFF9AA3AD,Paint.Align.CENTER);
            txt(c,"Ogni giornata raggruppa tutti i viaggi effettuati quel giorno",w/2f,h-dp(7),10,0xFF7E8994,Paint.Align.CENTER);
        }

        void openTripDay(String key){ selectedTripDayKey=key; tripDayScreen=true; tripsScroll=0f; invalidate(); }

        void drawTripDay(Canvas c){
            int w=getWidth(), h=getHeight(); c.drawColor(0xFF070A0D);
            panel(c,new RectF(0,0,w,dp(58)),0xFF141B22,0);
            txt(c,"GIORNATA",dp(16),dp(37),20,yellow,Paint.Align.LEFT);
            tripsCloseBounds.set(w-dp(104),dp(8),w-dp(8),dp(50)); panel(c,tripsCloseBounds,0xFF26313A,dp(8));
            txt(c,"← VIAGGI",tripsCloseBounds.centerX(),tripsCloseBounds.centerY()+dp(5),13,Color.WHITE,Paint.Align.CENTER);
            java.util.List<TripDb.TripSummary> all=tripDb==null?new java.util.ArrayList<>():tripDb.listTrips(500);
            java.util.ArrayList<TripDb.TripSummary> day=new java.util.ArrayList<>();
            for(TripDb.TripSummary t:all) if(tripDayKey(t.startTime).equals(selectedTripDayKey)) day.add(t);
            double km=0; for(TripDb.TripSummary t:day) km+=t.distanceM/1000.0;
            txt(c,tripDayLabel(selectedTripDayKey),dp(16),dp(82),17,Color.WHITE,Paint.Align.LEFT);
            txt(c,String.format(Locale.getDefault(),"%d %s  •  %.1f km",day.size(),day.size()==1?"viaggio":"viaggi",km),w-dp(16),dp(82),14,0xFF65D7FF,Paint.Align.RIGHT);
            if(tripRecording){panel(c,new RectF(dp(12),dp(94),w-dp(12),dp(128)),0xFF153323,dp(7));txt(c,"● REGISTRAZIONE VIAGGIO IN CORSO",w/2f,dp(117),13,green,Paint.Align.CENTER);}
            float top=tripRecording?dp(142):dp(96), rowH=Math.max(dp(76),Math.min(dp(92),h*.10f)), bottom=h-dp(58);
            c.save(); c.clipRect(0,top,w,bottom);
            for(int i=0;i<day.size();i++){
                TripDb.TripSummary t=day.get(i); float yy=top+i*rowH-tripsScroll; if(yy>bottom||yy+rowH<top)continue;
                panel(c,new RectF(dp(10),yy,w-dp(10),yy+rowH-dp(5)),0xFF10171E,dp(7));
                String times=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(t.startTime))+" → "+(t.endTime>0?new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(t.endTime)):"IN CORSO");
                txt(c,times,dp(20),yy+dp(22),15,Color.WHITE,Paint.Align.LEFT);
                txt(c,String.format(Locale.getDefault(),"%.1f km",t.distanceM/1000.0),w-dp(20),yy+dp(22),16,yellow,Paint.Align.RIGHT);
                txt(c,String.format(Locale.getDefault(),"%s   •   media %.1f   •   max %.1f km/h",fmtTripDuration(t.durationMs),t.avgKmh,t.maxKmh),dp(20),yy+dp(46),11,0xFFB7C0C8,Paint.Align.LEFT);
                txt(c,String.format(Locale.getDefault(),"%d punti GPS",t.pointCount),w-dp(20),yy+dp(46),11,0xFF65D7FF,Paint.Align.RIGHT);
                txt(c,"TOCCA PER DETTAGLI  ›",w-dp(20),yy+dp(67),9,0xFF7E8994,Paint.Align.RIGHT);
            }
            c.restore();
            if(day.isEmpty()) txt(c,"Nessun viaggio per questa giornata.",w/2f,h*.5f,14,0xFF9AA3AD,Paint.Align.CENTER);
            txt(c,"← VIAGGI",dp(18),h-dp(18),10,0xFF7E8994,Paint.Align.LEFT);
        }

        void openTripDetail(long id){
            selectedTripId=id;
            new Thread(() -> {
                if (activityDestroyed) return;
                TripDb.TripSummary summary;
                java.util.ArrayList<TripDb.Point> pts;
                synchronized (tripDbLock) {
                    if (activityDestroyed || tripDb == null) return;
                    try {
                        summary=tripDb.getTrip(id);
                        pts=tripDb.getPoints(id);
                    } catch (Exception ignored) { return; }
                }
                if (activityDestroyed) return;
                runOnUiThread(() -> {
                    if (activityDestroyed || view == null) return;
                    selectedTripSummary=summary; selectedTripPoints=pts; tripMapZoomOffset=0; tripMapUserPanned=false; tripMapCenterLat=Double.NaN; tripMapCenterLon=Double.NaN; tripsScreen=false; tripsScroll=0f; invalidate();
                });
            },"trip-detail-load").start();
        }

        void drawTripDetail(Canvas c){
            int w=getWidth(), h=getHeight(); boolean land=w>h;
            c.drawColor(0xFF070A0D);
            panel(c,new RectF(0,0,w,dp(58)),0xFF141B22,0);
            txt(c,"DETTAGLIO VIAGGIO",dp(16),dp(37),20,yellow,Paint.Align.LEFT);
            RectF back=new RectF(w-dp(116),dp(8),w-dp(8),dp(50)); panel(c,back,0xFF26313A,dp(8));
            txt(c,"← VIAGGI",back.centerX(),back.centerY()+dp(5),13,Color.WHITE,Paint.Align.CENTER);
            TripDb.TripSummary t=selectedTripSummary;
            if(t==null){txt(c,"CARICAMENTO VIAGGIO…",w/2f,h*.45f,16,Color.WHITE,Paint.Align.CENTER);return;}

            if(land){
                float sideW=Math.max(dp(285),Math.min(dp(390),w*.34f));
                RectF info=new RectF(dp(10),dp(72),dp(10)+sideW,h-dp(68));
                RectF map=new RectF(info.right+dp(12),dp(72),w-dp(10),h-dp(68));
                drawTripInfo(c,info,t,true);
                drawTripMap(c,map,selectedTripPoints);
                RectF home=new RectF(dp(10),h-dp(56),dp(150),h-dp(12)); panel(c,home,0xFF26313A,dp(7));
                txt(c,"⌂ PRINCIPALE",home.centerX(),home.centerY()+dp(5),12,Color.WHITE,Paint.Align.CENTER);
                RectF list=new RectF(dp(160),h-dp(56),dp(300),h-dp(12)); panel(c,list,0xFF26313A,dp(7));
                txt(c,"← VIAGGI",list.centerX(),list.centerY()+dp(5),12,Color.WHITE,Paint.Align.CENTER);
                RectF del=new RectF(w-dp(150),h-dp(56),w-dp(10),h-dp(12)); panel(c,del,0xFF342020,dp(7));
                txt(c,"ELIMINA",del.centerX(),del.centerY()+dp(5),12,0xFFFF7777,Paint.Align.CENTER);
                tripDetailHomeBounds.set(home); tripDetailListBounds.set(list); tripDetailDeleteBounds.set(del);
            } else {
                float mapTop=dp(68), mapBottom=Math.min(h*.48f,h-dp(270));
                if(mapBottom<mapTop+dp(170)) mapBottom=Math.min(h-dp(190),mapTop+dp(190));
                RectF map=new RectF(dp(10),mapTop,w-dp(10),mapBottom);
                drawTripMap(c,map,selectedTripPoints);
                RectF info=new RectF(dp(10),mapBottom+dp(10),w-dp(10),h-dp(72));
                drawTripInfo(c,info,t,false);
                float gap=dp(8), bw=(w-dp(20)-2*gap)/3f, by=h-dp(56);
                RectF list=new RectF(dp(10),by,dp(10)+bw, h-dp(12)); panel(c,list,0xFF26313A,dp(7));
                txt(c,"← VIAGGI",list.centerX(),list.centerY()+dp(5),12,Color.WHITE,Paint.Align.CENTER);
                RectF home=new RectF(list.right+gap,by,list.right+gap+bw,h-dp(12)); panel(c,home,0xFF26313A,dp(7));
                txt(c,"ESPORTA GPX",home.centerX(),home.centerY()+dp(5),12,0xFF65D7FF,Paint.Align.CENTER);
                RectF del=new RectF(home.right+gap,by,w-dp(10),h-dp(12)); panel(c,del,0xFF342020,dp(7));
                txt(c,"ELIMINA",del.centerX(),del.centerY()+dp(5),12,0xFFFF7777,Paint.Align.CENTER);
                tripDetailListBounds.set(list); tripDetailHomeBounds.set(home); tripDetailDeleteBounds.set(del);
            }
        }

        void drawTripInfo(Canvas c,RectF r,TripDb.TripSummary t,boolean land){
            panel(c,r,0xFF10171E,dp(9));
            txt(c,"DATI DEL VIAGGIO",r.left+dp(14),r.top+dp(25),14,yellow,Paint.Align.LEFT);
            String endLabel=t.endTime>0?fmtTripDate(t.endTime):"IN CORSO";
            txt(c,fmtTripDate(t.startTime)+"  →  "+endLabel,r.left+dp(14),r.top+dp(49),11,Color.WHITE,Paint.Align.LEFT);
            txt(c,String.format(Locale.getDefault(),"%.1f km",t.distanceM/1000.0),r.right-dp(14),r.top+dp(49),18,yellow,Paint.Align.RIGHT);
            float y=r.top+dp(78);
            if(land){
                float col=r.width()/2f;
                tripInfoCell(c,"DURATA",fmtTripDuration(t.durationMs),r.left+dp(14),y);
                tripInfoCell(c,"MEDIA",String.format(Locale.getDefault(),"%.1f km/h",t.avgKmh),r.left+col,y);
                y+=dp(62);
                tripInfoCell(c,"MASSIMA",String.format(Locale.getDefault(),"%.1f km/h",t.maxKmh),r.left+dp(14),y);
                tripInfoCell(c,"PUNTI GPS",String.valueOf(t.pointCount),r.left+col,y);
                y+=dp(62);
                tripInfoCell(c,"QUOTA MIN",String.format(Locale.getDefault(),"%.0f m",t.minAlt),r.left+dp(14),y);
                tripInfoCell(c,"QUOTA MAX",String.format(Locale.getDefault(),"%.0f m",t.maxAlt),r.left+col,y);
                y+=dp(62);
                tripInfoCell(c,"DISLIVELLO +",String.format(Locale.getDefault(),"%.0f m",t.elevationGainM),r.left+dp(14),y);
            } else {
                float col=r.width()/2f;
                tripInfoCell(c,"DURATA",fmtTripDuration(t.durationMs),r.left+dp(14),y);
                tripInfoCell(c,"MEDIA",String.format(Locale.getDefault(),"%.1f km/h",t.avgKmh),r.left+col,y);
                y+=dp(54);
                tripInfoCell(c,"MASSIMA",String.format(Locale.getDefault(),"%.1f km/h",t.maxKmh),r.left+dp(14),y);
                tripInfoCell(c,"PUNTI GPS",String.valueOf(t.pointCount),r.left+col,y);
                y+=dp(54);
                txt(c,String.format(Locale.getDefault(),"QUOTA %.0f → %.0f m   •   DISLIVELLO +%.0f m",t.minAlt,t.maxAlt,t.elevationGainM),r.left+dp(14),y,11,0xFF65D7FF,Paint.Align.LEFT);
            }
        }

        void tripInfoCell(Canvas c,String label,String value,float x,float y){
            txt(c,label,x,y,9,0xFF7E8994,Paint.Align.LEFT);
            txt(c,value,x,y+dp(22),16,Color.WHITE,Paint.Align.LEFT);
        }

        void drawTripMap(Canvas c,RectF r,java.util.ArrayList<TripDb.Point> pts){
            panel(c,r,0xFF0C1218,dp(9));
            if(pts==null||pts.size()<2){txt(c,"TRACCIA GPS NON DISPONIBILE",r.centerX(),r.centerY(),13,0xFF7E8994,Paint.Align.CENTER);return;}
            double minLa=Double.MAX_VALUE,minLo=Double.MAX_VALUE,maxLa=-Double.MAX_VALUE,maxLo=-Double.MAX_VALUE;
            for(TripDb.Point q:pts){if(q==null)continue;minLa=Math.min(minLa,q.lat);maxLa=Math.max(maxLa,q.lat);minLo=Math.min(minLo,q.lon);maxLo=Math.max(maxLo,q.lon);}
            double centerLat=(minLa+maxLa)/2.0,centerLon=(minLo+maxLo)/2.0;
            if(!tripMapUserPanned || Double.isNaN(tripMapCenterLat) || Double.isNaN(tripMapCenterLon)){ tripMapCenterLat=centerLat; tripMapCenterLon=centerLon; }
            centerLat=tripMapCenterLat; centerLon=tripMapCenterLon;
            Location center=new Location("trip-map"); center.setLatitude(centerLat); center.setLongitude(centerLon);
            double oldLat=mapViewLat,oldLon=mapViewLon; float oldBearing=mapFrozenBearing,oldZoom=mapZoom; boolean oldUser=mapUserPanned;
            mapViewLat=centerLat; mapViewLon=centerLon; mapFrozenBearing=tripMapBearing; mapUserPanned=true;
            tripMapBounds.set(r);
            double spanLat=Math.max(1e-7,maxLa-minLa), spanLon=Math.max(1e-7,maxLo-minLo);
            double cos=Math.max(.15,Math.cos(Math.toRadians(centerLat)));
            double usableW=Math.max(120.0,r.width()/density-56.0), usableH=Math.max(120.0,r.height()/density-56.0);
            double zoomLon=Math.log((usableW*360.0)/(256.0*spanLon))/Math.log(2.0);
            double zoomLat=Math.log((usableH*360.0*cos)/(256.0*spanLat))/Math.log(2.0);
            int baseTripZoom=(int)Math.floor(Math.min(zoomLon,zoomLat));
            tripMapZoom=(int)Math.max(10,Math.min(18,baseTripZoom+tripMapZoomOffset));
            tripMapRendering=true;
            boolean raster=drawRasterTiles(c,r,center,true);
            if(!raster){p.setStyle(Paint.Style.FILL);p.setColor(0xFF111A22);c.drawRect(r,p);}
            drawTripTrace(c,r,pts);
            txt(c,"TRACCIA GPS",r.left+dp(12),r.top+dp(22),11,Color.WHITE,Paint.Align.LEFT);
            txt(c,String.format(Locale.getDefault(),"%d punti",pts.size()),r.right-dp(12),r.top+dp(22),11,0xFF65D7FF,Paint.Align.RIGHT);
            // Dedicated trip-map zoom controls. The trip starts in a fit-to-route view,
            // but the rider can now zoom in several steps to inspect bends and GPS drift.
            float zb=dp(38), zg=dp(6);
            tripMapZoomInBounds.set(r.right-zb-dp(10), r.top+dp(10), r.right-dp(10), r.top+dp(10)+zb);
            tripMapZoomOutBounds.set(r.right-zb-dp(10), r.top+dp(10)+zb+zg, r.right-dp(10), r.top+dp(10)+2*zb+zg);
            panel(c,tripMapZoomInBounds,0xCC18212A,dp(7));
            panel(c,tripMapZoomOutBounds,0xCC18212A,dp(7));
            txt(c,"+",tripMapZoomInBounds.centerX(),tripMapZoomInBounds.centerY()+dp(8),23,Color.WHITE,Paint.Align.CENTER);
            txt(c,"−",tripMapZoomOutBounds.centerX(),tripMapZoomOutBounds.centerY()+dp(7),23,Color.WHITE,Paint.Align.CENTER);
            tripMapRendering=false;
            mapViewLat=oldLat;mapViewLon=oldLon;mapFrozenBearing=oldBearing;mapZoom=oldZoom;mapUserPanned=oldUser;
        }

        void drawTripTrace(Canvas c,RectF r,java.util.ArrayList<TripDb.Point> pts){
            if(pts==null||pts.size()<2){txt(c,"TRACCIA NON DISPONIBILE",r.centerX(),r.centerY(),13,0xFF7E8994,Paint.Align.CENTER);return;}
            // The raster map uses Web Mercator. Project the GPS trace with the EXACT
            // same centre/zoom/world transform instead of fitting latitude/longitude
            // linearly into the card. This keeps the yellow trace locked to the roads.
            int zoom=Math.max(10,Math.min(17,tripMapZoom));
            double world=256.0*(1L<<zoom);
            double centerLat=mapViewLat, centerLon=mapViewLon;
            if(Double.isNaN(centerLat)||Double.isNaN(centerLon)){
                centerLat=pts.get(pts.size()/2).lat; centerLon=pts.get(pts.size()/2).lon;
            }
            double cxWorld=(centerLon+180.0)/360.0*world;
            double sin=Math.sin(Math.toRadians(Math.max(-85.0,Math.min(85.0,centerLat))));
            double cyWorld=(.5-Math.log((1.0+sin)/(1.0-sin))/(4.0*Math.PI))*world;
            Path path=new Path(); boolean first=true;
            for(TripDb.Point q:pts){
                if(q==null||Double.isNaN(q.lat)||Double.isNaN(q.lon))continue;
                double lat=Math.max(-85.0,Math.min(85.0,q.lat));
                double ss=Math.sin(Math.toRadians(lat));
                double px=(q.lon+180.0)/360.0*world;
                double py=(.5-Math.log((1.0+ss)/(1.0-ss))/(4.0*Math.PI))*world;
                double dx=px-cxWorld, dy=py-cyWorld;
                double ang=Math.toRadians(tripMapBearing);
                double rx=dx*Math.cos(ang)+dy*Math.sin(ang);
                double ry=-dx*Math.sin(ang)+dy*Math.cos(ang);
                float x=(float)(r.centerX()+rx);
                float y=(float)(r.centerY()+ry);
                if(first){path.moveTo(x,y);first=false;}else path.lineTo(x,y);
            }
            stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(3));stroke.setColor(yellow);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,stroke);stroke.setStyle(Paint.Style.FILL);
            TripDb.Point a=pts.get(0),b=pts.get(pts.size()-1);
            float[] ap=tripMapPoint(r,a,world,cxWorld,cyWorld),bp=tripMapPoint(r,b,world,cxWorld,cyWorld);
            p.setColor(green);c.drawCircle(ap[0],ap[1],dp(6),p);p.setColor(Color.RED);c.drawCircle(bp[0],bp[1],dp(6),p);
            txt(c,"PARTENZA",ap[0],ap[1]+dp(18),9,green,Paint.Align.CENTER);txt(c,"ARRIVO",bp[0],bp[1]+dp(18),9,0xFFFF7777,Paint.Align.CENTER);
        }

        float[] tripMapPoint(RectF r,TripDb.Point q,double world,double cxWorld,double cyWorld){
            double lat=Math.max(-85.0,Math.min(85.0,q.lat));
            double ss=Math.sin(Math.toRadians(lat));
            double px=(q.lon+180.0)/360.0*world;
            double py=(.5-Math.log((1.0+ss)/(1.0-ss))/(4.0*Math.PI))*world;
            double dx=px-cxWorld, dy=py-cyWorld;
            double ang=Math.toRadians(tripMapBearing);
            double rx=dx*Math.cos(ang)+dy*Math.sin(ang);
            double ry=-dx*Math.sin(ang)+dy*Math.cos(ang);
            return new float[]{(float)(r.centerX()+rx),(float)(r.centerY()+ry)};
        }

        void deleteSelectedTrip(){
            final long id=selectedTripId;
            new AlertDialog.Builder(MainActivity.this).setTitle("ELIMINARE IL VIAGGIO?").setMessage("La traccia GPS e tutte le statistiche di questo viaggio verranno eliminate.").setNegativeButton("ANNULLA",null).setPositiveButton("ELIMINA",(d,w)->new Thread(()->{
                if (activityDestroyed) return;
                synchronized (tripDbLock) {
                    if (activityDestroyed || tripDb == null) return;
                    try { tripDb.deleteTrip(id); } catch (Exception ignored) { return; }
                }
                if (activityDestroyed) return;
                runOnUiThread(()->{
                    if (activityDestroyed || view == null) return;
                    selectedTripId=-1;selectedTripSummary=null;selectedTripPoints.clear();tripsScreen=true;invalidate();
                });
            },"trip-delete").start()).show();
        }

        void showExitConfirmation() {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("CHIUDERE ROAD DISPLAY PRO?")
                    .setMessage("La navigazione verrà chiusa e l'app verrà terminata.")
                    .setNegativeButton("ANNULLA", null)
                    .setPositiveButton("CHIUDI APP", (d, w) -> {
                        try { if (navigationActive) stopNavigation(); } catch (Exception ignored) { }
                        finishAndRemoveTask();
                    })
                    .show();
        }

        void showWeatherPopup() {
            LinearLayout root=new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)dp(18),(int)dp(8),(int)dp(18),(int)dp(8));
            ScrollView scroll=new ScrollView(MainActivity.this);
            LinearLayout content=new LinearLayout(MainActivity.this); content.setOrientation(LinearLayout.VERTICAL);
            TextView title=new TextView(MainActivity.this); title.setText("METEO ATTUALE"); title.setTextSize(18); title.setTextColor(yellow); content.addView(title);
            TextView place=new TextView(MainActivity.this); place.setText(weatherPlaceName.isEmpty()?"Luogo in rilevamento…":weatherPlaceName); place.setTextSize(14); place.setTextColor(0xFF8ED7FF); content.addView(place);
            TextView current=new TextView(MainActivity.this); current.setText(weatherSummary+"\n"+weatherDetail); current.setTextSize(15); current.setTextColor(Color.WHITE); current.setPadding(0,(int)dp(6),0,(int)dp(12)); content.addView(current);
            TextView hoursTitle=new TextView(MainActivity.this); hoursTitle.setText("PREVISIONI PER FASCIA · TOCCA PER ESPANDERE"); hoursTitle.setTextSize(16); hoursTitle.setTextColor(yellow); content.addView(hoursTitle);
            TextView[] periodViews = new TextView[4];
            for (int band=0; band<4; band++) {
                final int selectedBand=band;
                TextView period=new TextView(MainActivity.this);
                period.setText(formatWeatherPeriod(null, band, false));
                period.setTextSize(14); period.setTextColor(Color.WHITE);
                period.setPadding((int)dp(10),(int)dp(10),(int)dp(10),(int)dp(10));
                period.setBackgroundColor(0xFF202832);
                LinearLayout.LayoutParams periodLp=new LinearLayout.LayoutParams(-1,-2); periodLp.setMargins(0,(int)dp(5),0,(int)dp(3));
                content.addView(period,periodLp); periodViews[band]=period;
                period.setOnClickListener(v -> { weatherExpandedBand = (weatherExpandedBand==selectedBand ? -1 : selectedBand); for(int j=0;j<4;j++) { periodViews[j].setText(formatWeatherPeriod(latestWeatherHourlyObject, j, weatherExpandedBand==j)); } });
            }
            TextView dt=null, dc=null, dh=null;
            if(navigationActive && !Double.isNaN(navigationDestLat)) {
                dt=new TextView(MainActivity.this); dt.setText("METEO DESTINAZIONE"); dt.setTextSize(18); dt.setTextColor(yellow); content.addView(dt);
                dc=new TextView(MainActivity.this); dc.setText(destinationWeatherLoading?"Caricamento…":(destinationWeatherSummary.isEmpty()?"Meteo destinazione non disponibile":destinationWeatherSummary)); dc.setTextSize(15); dc.setTextColor(Color.WHITE); dc.setPadding(0,(int)dp(6),0,(int)dp(10)); content.addView(dc);
                dh=new TextView(MainActivity.this); dh.setText(destinationWeatherHourlySummary.isEmpty()?(destinationWeatherLoading?"Caricamento…":"Previsioni non disponibili"):destinationWeatherHourlySummary); dh.setTextSize(14); dh.setTextColor(Color.WHITE); content.addView(dh);
            }
            scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,(int)dp(430)));
            final TextView fPlace=place, fCurrent=current, fDest=dc, fDestHours=dh;
            final android.os.Handler handler=new android.os.Handler(Looper.getMainLooper());
            final AlertDialog dialog=new AlertDialog.Builder(MainActivity.this).setTitle("METEO").setView(root).setPositiveButton("CHIUDI",null).create();
            final Runnable updater=new Runnable(){ public void run(){
                fPlace.setText(weatherPlaceName.isEmpty()?"Luogo in rilevamento…":weatherPlaceName);
                fCurrent.setText(weatherSummary+"\n"+weatherDetail);
                for(int j=0;j<4;j++) periodViews[j].setText(weatherLoading && latestWeatherHourlyObject==null ? "Caricamento previsioni…" : formatWeatherPeriod(latestWeatherHourlyObject, j, weatherExpandedBand==j));
                if(fDest!=null) fDest.setText(destinationWeatherLoading?"Caricamento…":(destinationWeatherSummary.isEmpty()?"Meteo destinazione non disponibile":destinationWeatherSummary));
                if(fDestHours!=null) fDestHours.setText(destinationWeatherHourlySummary.isEmpty()?(destinationWeatherLoading?"Caricamento…":"Previsioni non disponibili"):destinationWeatherHourlySummary);
                if(dialog.isShowing() && (weatherLoading || destinationWeatherLoading || weatherPlaceName.isEmpty())) handler.postDelayed(this,700);
            }};
            dialog.setOnDismissListener(d -> handler.removeCallbacks(updater));
            dialog.show();
            handler.post(updater);
        }

        void showNavigationDialog() { showNavigationDialog(Double.NaN, Double.NaN); }

        void showNavigationDialog(double pickedLat, double pickedLon) {
            final android.widget.LinearLayout box = new android.widget.LinearLayout(MainActivity.this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.setPadding((int)dp(18), (int)dp(4), (int)dp(18), 0);

            final android.widget.EditText departure = new android.widget.EditText(MainActivity.this);
            departure.setSingleLine(true);
            departure.setHint("Partenza (vuoto = posizione GPS)");
            departure.setTextSize(16f);

            final android.widget.AutoCompleteTextView destination = new android.widget.AutoCompleteTextView(MainActivity.this);
            destination.setSingleLine(true);
            destination.setHint("Destinazione");
            destination.setTextSize(16f);
            destination.setThreshold(0);
            final ArrayList<String> recentDestinations=getRecentDestinations();
            final ArrayAdapter<String> recentAdapter=new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_dropdown_item_1line,recentDestinations);
            destination.setAdapter(recentAdapter);
            destination.setOnFocusChangeListener((v,has)->{if(has && !recentDestinations.isEmpty()) destination.postDelayed(destination::showDropDown,120);});
            destination.setOnClickListener(v->{if(!recentDestinations.isEmpty()) destination.showDropDown();});
            destination.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            destination.setOnEditorActionListener((v, actionId, event) -> {
                boolean done = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN);
                if(!done) return false;
                android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(imm!=null) imm.hideSoftInputFromWindow(destination.getWindowToken(),0);
                destination.clearFocus();
                String q=destination.getText().toString().trim();
                if(q.isEmpty()) android.widget.Toast.makeText(MainActivity.this,"Inserisci una destinazione",android.widget.Toast.LENGTH_SHORT).show();
                else searchDestinationCandidatesDialog(departure,destination,q);
                return true;
            });
            if(!Double.isNaN(pickedLat)&&!Double.isNaN(pickedLon)) destination.setText(String.format(Locale.US,"%.6f,%.6f",pickedLat,pickedLon));

            android.widget.LinearLayout depRow = new android.widget.LinearLayout(MainActivity.this);
            depRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            depRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            depRow.addView(departure, new android.widget.LinearLayout.LayoutParams(0, (int)dp(54), 1f));
            android.widget.Button depHomeBtn = new android.widget.Button(MainActivity.this);
            depHomeBtn.setText("CASA"); depHomeBtn.setTextSize(11f); depHomeBtn.setAllCaps(false);
            depRow.addView(depHomeBtn, new android.widget.LinearLayout.LayoutParams((int)dp(66), (int)dp(48)));
            box.addView(depRow);

            android.widget.LinearLayout destRow = new android.widget.LinearLayout(MainActivity.this);
            destRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            destRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            destRow.addView(destination, new android.widget.LinearLayout.LayoutParams(0, (int)dp(54), 1f));
            android.widget.Button destHomeBtn = new android.widget.Button(MainActivity.this);
            destHomeBtn.setText("CASA"); destHomeBtn.setTextSize(11f); destHomeBtn.setAllCaps(false);
            destRow.addView(destHomeBtn, new android.widget.LinearLayout.LayoutParams((int)dp(66), (int)dp(48)));
            box.addView(destRow);

            android.widget.LinearLayout saveRow = new android.widget.LinearLayout(MainActivity.this);
            saveRow.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL);
            android.widget.Button saveHomeBtn = new android.widget.Button(MainActivity.this);
            saveHomeBtn.setText("SALVA CASA"); saveHomeBtn.setTextSize(10f); saveHomeBtn.setAllCaps(false);
            saveRow.addView(saveHomeBtn, new android.widget.LinearLayout.LayoutParams((int)dp(110), (int)dp(38)));
            box.addView(saveRow);

            final android.content.SharedPreferences prefs = getSharedPreferences("road_display", MODE_PRIVATE);
            final String savedHome = prefs.getString(PREF_HOME_ADDRESS, "");
            if (!savedHome.isEmpty()) { depHomeBtn.setText("CASA ✓"); destHomeBtn.setText("CASA ✓"); }

            depHomeBtn.setOnClickListener(v -> fillHome(departure));
            destHomeBtn.setOnClickListener(v -> fillHome(destination));
            saveHomeBtn.setOnClickListener(v -> {
                String home = destination.getText().toString().trim();
                if (home.isEmpty()) home = departure.getText().toString().trim();
                if (home.isEmpty()) {
                    android.widget.Toast.makeText(MainActivity.this, "Inserisci prima l'indirizzo di casa", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                android.content.SharedPreferences.Editor e = prefs.edit().putString(PREF_HOME_ADDRESS, home);
                Location currentHome = engine.loc;
                if (isUsableNavigationStart(currentHome)) {
                    e.putString(PREF_HOME_LAT, Double.toString(currentHome.getLatitude()));
                    e.putString(PREF_HOME_LON, Double.toString(currentHome.getLongitude()));
                    e.apply();
                    depHomeBtn.setText("CASA ✓"); destHomeBtn.setText("CASA ✓");
                    android.widget.Toast.makeText(MainActivity.this, "CASA memorizzata con posizione GPS", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    e.remove(PREF_HOME_LAT).remove(PREF_HOME_LON).apply();
                    depHomeBtn.setText("CASA ✓"); destHomeBtn.setText("CASA ✓");
                    android.widget.Toast.makeText(MainActivity.this, "Indirizzo di casa memorizzato", android.widget.Toast.LENGTH_SHORT).show();
                }
            });

            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle(navigationActive ? "NAVIGAZIONE ATTIVA" : "NAVIGAZIONE")
                    .setView(box)
                    .setNegativeButton("ANNULLA", null);
            if (navigationActive) b.setNeutralButton("STOP NAV", (d,w)-> stopNavigation());
            b.setPositiveButton("VAI", null);
            final android.app.AlertDialog dialog=b.create();
            dialog.setOnShowListener(d -> {
                android.widget.Button go=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                go.setOnClickListener(v -> {
                    String from=departure.getText().toString().trim();
                    String to=destination.getText().toString().trim();
                    if(to.isEmpty()) {
                        android.widget.Toast.makeText(MainActivity.this,"Inserisci una destinazione",android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    if(imm!=null) imm.hideSoftInputFromWindow(destination.getWindowToken(),0);
                    destination.clearFocus();
                    if(parseCoordinateDestination(to)!=null) {
                        dialog.dismiss();
                        startNavigation(from,to);
                    } else {
                        dialog.dismiss();
                        searchDestinationCandidatesDialog(departure,destination,to);
                    }
                });
            });
            dialog.show();
        }


        void showMapSearchDialog() {
            final android.widget.EditText queryField = new android.widget.EditText(MainActivity.this);
            queryField.setSingleLine(true);
            queryField.setTextSize(17f);
            queryField.setHint("Indirizzo, luogo o punto di interesse");
            queryField.setPadding((int)dp(10),(int)dp(8),(int)dp(10),(int)dp(8));
            android.widget.LinearLayout box = new android.widget.LinearLayout(MainActivity.this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.setPadding((int)dp(8),(int)dp(4),(int)dp(8),0);
            box.addView(queryField, new android.widget.LinearLayout.LayoutParams(-1,(int)dp(54)));
            final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("CERCA SULLA MAPPA")
                    .setView(box)
                    .setNegativeButton("ANNULLA", null)
                    .setPositiveButton("CERCA", null)
                    .create();
            dialog.setOnShowListener(x -> {
                queryField.requestFocus();
                android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(imm!=null) imm.showSoftInput(queryField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String q=queryField.getText().toString().trim();
                    if(q.isEmpty()){ android.widget.Toast.makeText(MainActivity.this,"Inserisci cosa cercare",android.widget.Toast.LENGTH_SHORT).show(); return; }
                    if(imm!=null) imm.hideSoftInputFromWindow(queryField.getWindowToken(),0);
                    dialog.dismiss();
                    searchMapDestinationCandidatesDialog(q);
                });
                queryField.setOnEditorActionListener((v,actionId,event)->{
                    boolean done=actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                            || (event!=null && event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER && event.getAction()==android.view.KeyEvent.ACTION_DOWN);
                    if(!done)return false;
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                });
            });
            dialog.show();
        }

        void searchMapDestinationCandidatesDialog(final String query) {
            final Location bias=engine==null || engine.loc==null ? null : new Location(engine.loc);
            final android.app.AlertDialog loading=new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("RICERCA SULLA MAPPA")
                    .setMessage("Cerco luoghi e indirizzi…")
                    .setNegativeButton("ANNULLA",null).create();
            loading.show();
            new Thread(() -> {
                ArrayList<DestinationCandidate> candidates=new ArrayList<>();
                for(String qv:addressSearchVariants(query)){
                    try { candidates.addAll(searchDestinationCandidatesPhoton(qv,bias)); } catch(Exception ignored) {}
                    try { candidates.addAll(searchDestinationCandidatesCartoCiudad(qv,bias)); } catch(Exception ignored) {}
                    try { candidates.addAll(searchDestinationCandidatesNominatim(qv,bias)); } catch(Exception ignored) {}
                }
                mergeAndSortDestinationCandidates(candidates);
                if(activityDestroyed)return;
                view.post(() -> {
                    if(loading.isShowing())loading.dismiss();
                    if(candidates.isEmpty()){android.widget.Toast.makeText(MainActivity.this,"Nessun risultato trovato",android.widget.Toast.LENGTH_LONG).show();return;}
                    showMapSearchCandidates(candidates);
                });
            },"map-search-destination").start();
        }

        void showMapSearchCandidates(final ArrayList<DestinationCandidate> candidates) {
            final android.widget.LinearLayout box=new android.widget.LinearLayout(MainActivity.this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.setPadding((int)dp(8),(int)dp(4),(int)dp(8),0);
            final android.widget.RadioGroup group=new android.widget.RadioGroup(MainActivity.this);
            group.setOrientation(android.widget.RadioGroup.VERTICAL);
            final ArrayList<android.widget.RadioButton> buttons=new ArrayList<>();
            for(DestinationCandidate c:candidates){
                android.widget.RadioButton rb=new android.widget.RadioButton(MainActivity.this);
                String sub=c.subtitle==null?"":c.subtitle;
                rb.setText(c.title+(sub.isEmpty()?"":"\n"+sub)+"\n"+formatSearchDistance(c.distanceM));
                rb.setTextSize(15f);
                rb.setPadding((int)dp(4),(int)dp(7),(int)dp(4),(int)dp(7));
                rb.setId(View.generateViewId());
                group.addView(rb,new android.widget.RadioGroup.LayoutParams(-1,(int)dp(82)));
                buttons.add(rb);
            }
            if(!buttons.isEmpty())buttons.get(0).setChecked(true);
            box.addView(group);
            android.widget.TextView note=new android.widget.TextView(MainActivity.this);
            note.setText("La mappa verrà centrata sul punto scelto; la navigazione non viene avviata.");
            note.setTextSize(12f); note.setTextColor(Color.LTGRAY);
            note.setPadding((int)dp(8),(int)dp(8),(int)dp(8),(int)dp(2));
            box.addView(note);
            final android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("RISULTATI SULLA MAPPA")
                    .setView(box).setNegativeButton("ANNULLA",null).setPositiveButton("MOSTRA SULLA MAPPA",null).create();
            dialog.setOnShowListener(x->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                int checked=group.getCheckedRadioButtonId(),idx=-1;
                for(int i=0;i<buttons.size();i++)if(buttons.get(i).getId()==checked){idx=i;break;}
                if(idx<0)idx=0;
                DestinationCandidate c=candidates.get(idx);
                mapViewLat=c.lat; mapViewLon=c.lon;
                selectedMapLat=c.lat; selectedMapLon=c.lon; selectedMapPlaceName=c.title==null?"":c.title;
                mapUserPanned=true;
                if(vectorMapReady && vectorMap!=null)syncVectorMapCamera(true);
                dialog.dismiss(); invalidate();
            }));
            dialog.show();
        }

        void searchDestinationCandidatesDialog(final android.widget.EditText departure, final android.widget.AutoCompleteTextView destination, final String query) {
            final Location bias=engine==null || engine.loc==null ? null : new Location(engine.loc);
            final android.app.AlertDialog loading=new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("RICERCA DESTINAZIONE")
                    .setMessage("Cerco luoghi e indirizzi nelle vicinanze…")
                    .setNegativeButton("ANNULLA",null).create();
            loading.show();
            new Thread(() -> {
                ArrayList<DestinationCandidate> candidates=new ArrayList<>();
                for(String qv:addressSearchVariants(query)){
                    try { candidates.addAll(searchDestinationCandidatesPhoton(qv,bias)); } catch(Exception ignored) {}
                    try { candidates.addAll(searchDestinationCandidatesCartoCiudad(qv,bias)); } catch(Exception ignored) {}
                    try { candidates.addAll(searchDestinationCandidatesNominatim(qv,bias)); } catch(Exception ignored) {}
                }
                mergeAndSortDestinationCandidates(candidates);
                if(activityDestroyed) return;
                view.post(() -> {
                    if(loading.isShowing()) loading.dismiss();
                    if(candidates.isEmpty()) { android.widget.Toast.makeText(MainActivity.this,"Nessun risultato trovato",android.widget.Toast.LENGTH_LONG).show(); return; }
                    showDestinationCandidates(candidates,departure,destination);
                });
            },"destination-search").start();
        }

        ArrayList<String> addressSearchVariants(String query){
            ArrayList<String> out=new ArrayList<>(); String q=query==null?"":query.trim(); if(q.isEmpty())return out; out.add(q);
            String low=q.toLowerCase(Locale.ROOT);
            if(low.contains("morro afonso")){ String noAlto=q.replaceAll("(?i)\\balto\\b"," ").replaceAll("\\s+"," ").trim(); String withAlto=low.contains(" alto")?q:q+" Alto"; if(!noAlto.equalsIgnoreCase(q))out.add(noAlto); if(!withAlto.equalsIgnoreCase(q))out.add(withAlto); }
            return out;
        }

        ArrayList<DestinationCandidate> searchDestinationCandidatesPhoton(String query, Location bias) throws Exception {
            ArrayList<DestinationCandidate> out=new ArrayList<>();
            String clean=query==null?"":query.trim(); if(clean.isEmpty()) return out;
            String enc=java.net.URLEncoder.encode(clean,"UTF-8");
            StringBuilder url=new StringBuilder("https://photon.komoot.io/api/?q=").append(enc).append("&limit=12&lang=it");
            if(bias!=null && Double.isFinite(bias.getLatitude()) && Double.isFinite(bias.getLongitude())) url.append("&lat=").append(bias.getLatitude()).append("&lon=").append(bias.getLongitude()).append("&zoom=12&location_bias_scale=0.35");
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(url.toString()).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET"); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","RoadDisplayPRO/3.28.5 (Android motorcycle dashboard; map attribution visible)");
                if(c.getResponseCode()!=HttpURLConnection.HTTP_OK) return out;
                JSONObject root=new JSONObject(readHttp(c)); JSONArray features=root.optJSONArray("features"); if(features==null) return out;
                for(int i=0;i<features.length();i++) {
                    JSONObject f=features.optJSONObject(i); if(f==null) continue; JSONObject g=f.optJSONObject("geometry"), p=f.optJSONObject("properties"); JSONArray co=g==null?null:g.optJSONArray("coordinates"); if(co==null||co.length()<2) continue;
                    double lon=co.optDouble(0,Double.NaN), lat=co.optDouble(1,Double.NaN); if(!Double.isFinite(lat)||!Double.isFinite(lon)) continue;
                    String title=firstNonEmpty(p==null?"":p.optString("name",""),p==null?"":p.optString("street",""),"Risultato");
                    StringBuilder sub=new StringBuilder();
                    if(p!=null) { String hn=p.optString("housenumber",""), street=p.optString("street",""); String city=firstNonEmpty(p.optString("city",""),p.optString("district",""),p.optString("state","")); if(!hn.isEmpty()&&!street.isEmpty()) sub.append(street).append(" ").append(hn); else if(!street.isEmpty()) sub.append(street); if(!city.isEmpty()){if(sub.length()>0)sub.append(", ");sub.append(city);} }
                    double d=bias==null?Double.POSITIVE_INFINITY:distanceMeters(bias.getLatitude(),bias.getLongitude(),lat,lon); out.add(new DestinationCandidate(title,sub.toString(),lat,lon,d,"Photon"));
                }
            } finally { if(c!=null)c.disconnect(); }
            return out;
        }

        ArrayList<DestinationCandidate> searchDestinationCandidatesCartoCiudad(String query, Location bias) throws Exception {
            ArrayList<DestinationCandidate> out=new ArrayList<>();
            String clean=query==null?"":query.trim(); if(clean.isEmpty())return out;
            String[] qs=new String[]{clean, clean+", Adeje, Santa Cruz de Tenerife", clean+", Tenerife, Canarias"};
            for(String q:qs){
                String enc=java.net.URLEncoder.encode(q,"UTF-8");
                String url="https://www.cartociudad.es/geocoder/api/geocoder/candidates?q="+enc+"&limit=12&countrycode=es";
                HttpURLConnection c=null;
                try{
                    c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(7000); c.setReadTimeout(10000); c.setRequestMethod("GET");
                    c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","RoadDisplayPRO/3.37.1 (Android motorcycle dashboard)");
                    if(c.getResponseCode()!=HttpURLConnection.HTTP_OK)continue;
                    JSONArray arr=new JSONArray(readHttp(c));
                    for(int i=0;i<arr.length();i++){
                        JSONObject it=arr.optJSONObject(i); if(it==null||!it.has("lat")||!it.has("lng"))continue;
                        double lat=it.optDouble("lat",Double.NaN),lon=it.optDouble("lng",Double.NaN); if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
                        String title=firstNonEmpty(it.optString("address",""),it.optString("poblacion",""),"Risultato");
                        String sub=firstNonEmpty(it.optString("address",""),it.optString("muni",""),it.optString("poblacion",""));
                        double d=bias==null?Double.POSITIVE_INFINITY:distanceMeters(bias.getLatitude(),bias.getLongitude(),lat,lon);
                        out.add(new DestinationCandidate(title,sub,lat,lon,d,"CartoCiudad"));
                    }
                    if(!out.isEmpty())break;
                }finally{if(c!=null)c.disconnect();}
            }
            return out;
        }

        ArrayList<DestinationCandidate> searchDestinationCandidatesNominatim(String query, Location bias) throws Exception {
            ArrayList<DestinationCandidate> out=new ArrayList<>(); long now=System.currentTimeMillis(); long wait=1000L-(now-lastNominatimGeocodeAt); if(wait>0) Thread.sleep(wait); lastNominatimGeocodeAt=System.currentTimeMillis();
            String clean=query==null?"":query.trim(); if(clean.isEmpty()) return out; String enc=java.net.URLEncoder.encode(clean,"UTF-8");
            String url="https://nominatim.openstreetmap.org/search?format=jsonv2&limit=10&addressdetails=1&namedetails=1&accept-language=it,en&q="+enc;
            HttpURLConnection c=null;
            try { c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET"); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","RoadDisplayPRO/3.28.5 (Android motorcycle dashboard; map attribution visible)"); if(c.getResponseCode()!=HttpURLConnection.HTTP_OK)return out; JSONArray arr=new JSONArray(readHttp(c));
                for(int i=0;i<arr.length();i++){ JSONObject it=arr.optJSONObject(i); if(it==null)continue; double lat=it.optDouble("lat",Double.NaN),lon=it.optDouble("lon",Double.NaN); if(!Double.isFinite(lat)||!Double.isFinite(lon))continue; JSONObject addr=it.optJSONObject("address"); String title=it.optString("name",""); if(title.isEmpty()&&addr!=null)title=firstNonEmpty(addr.optString("road",""),addr.optString("amenity",""),addr.optString("shop",""),it.optString("type","Risultato")); if(title.isEmpty())title="Risultato"; String sub=it.optString("display_name",""); double d=bias==null?Double.POSITIVE_INFINITY:distanceMeters(bias.getLatitude(),bias.getLongitude(),lat,lon); out.add(new DestinationCandidate(title,sub,lat,lon,d,"Nominatim")); }
            } finally { if(c!=null)c.disconnect(); }
            return out;
        }

        String firstNonEmpty(String... values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v.trim();return "";}
        void mergeAndSortDestinationCandidates(ArrayList<DestinationCandidate> list){
            java.util.Collections.sort(list,(a,b)->{if(Double.isFinite(a.distanceM)&&Double.isFinite(b.distanceM))return Double.compare(a.distanceM,b.distanceM);if(Double.isFinite(a.distanceM))return -1;if(Double.isFinite(b.distanceM))return 1;return a.title.compareToIgnoreCase(b.title);});
            ArrayList<DestinationCandidate> unique=new ArrayList<>(); for(DestinationCandidate c:list){boolean dup=false;for(DestinationCandidate u:unique)if(distanceMeters(c.lat,c.lon,u.lat,u.lon)<80.0){dup=true;break;}if(!dup)unique.add(c);if(unique.size()>=12)break;} list.clear();list.addAll(unique);
        }
        String formatSearchDistance(double meters){if(!Double.isFinite(meters))return "DISTANZA NON DISPONIBILE";if(meters<1000)return Math.max(10,Math.round(meters/10.0)*10)+" m";return String.format(Locale.ITALY,"%.1f km",meters/1000.0);}
        void showDestinationCandidates(final ArrayList<DestinationCandidate> candidates, final android.widget.EditText departure, final android.widget.AutoCompleteTextView destination){
            final android.widget.LinearLayout box=new android.widget.LinearLayout(MainActivity.this); box.setOrientation(android.widget.LinearLayout.VERTICAL); box.setPadding((int)dp(8),(int)dp(4),(int)dp(8),0); final android.widget.RadioGroup group=new android.widget.RadioGroup(MainActivity.this); group.setOrientation(android.widget.RadioGroup.VERTICAL); final ArrayList<android.widget.RadioButton> buttons=new ArrayList<>();
            for(DestinationCandidate c:candidates){android.widget.RadioButton rb=new android.widget.RadioButton(MainActivity.this);String sub=c.subtitle==null?"":c.subtitle;rb.setText(c.title+(sub.isEmpty()?"":"\n"+sub)+"\n"+formatSearchDistance(c.distanceM));rb.setTextSize(15f);rb.setPadding((int)dp(4),(int)dp(7),(int)dp(4),(int)dp(7));rb.setId(View.generateViewId());group.addView(rb,new android.widget.RadioGroup.LayoutParams(-1,(int)dp(82)));buttons.add(rb);} if(!buttons.isEmpty())buttons.get(0).setChecked(true);box.addView(group);android.widget.TextView note=new android.widget.TextView(MainActivity.this);note.setText("Distanza dalla posizione GPS; il percorso stradale viene calcolato dopo la scelta.");note.setTextSize(12f);note.setTextColor(Color.LTGRAY);note.setPadding((int)dp(8),(int)dp(8),(int)dp(8),(int)dp(2));box.addView(note);
            final android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(MainActivity.this).setTitle("SCEGLI DESTINAZIONE").setView(box).setNegativeButton("ANNULLA",null).setPositiveButton("VAI",null).create(); dialog.setOnShowListener(x->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int checked=group.getCheckedRadioButtonId(),idx=-1;for(int i=0;i<buttons.size();i++)if(buttons.get(i).getId()==checked){idx=i;break;}if(idx<0)idx=0;DestinationCandidate c=candidates.get(idx);destination.setText(c.title);navigationDestinationLabelOverride=c.title;String from=departure.getText().toString().trim();dialog.dismiss();startNavigation(from,String.format(Locale.US,"%.6f,%.6f",c.lat,c.lon));})); dialog.show();
        }

        ArrayList<String> getRecentDestinations() {
            ArrayList<String> list=new ArrayList<>();
            String raw=getSharedPreferences("road_display",MODE_PRIVATE).getString(PREF_RECENT_DESTINATIONS,"");
            if(raw.isEmpty()) return list;
            try { JSONArray a=new JSONArray(raw); for(int i=0;i<a.length() && list.size()<10;i++){String x=a.optString(i,"").trim(); if(!x.isEmpty()&&!list.contains(x))list.add(x);} } catch(Exception ignored) {}
            return list;
        }
        void saveRecentDestination(String value) {
            String x=value==null?"":value.trim(); if(x.isEmpty()) return;
            ArrayList<String> list=getRecentDestinations(); list.remove(x); list.add(0,x); while(list.size()>10)list.remove(list.size()-1);
            JSONArray a=new JSONArray(); for(String item:list)a.put(item);
            getSharedPreferences("road_display",MODE_PRIVATE).edit().putString(PREF_RECENT_DESTINATIONS,a.toString()).apply();
        }

        void fillHome(android.widget.EditText field) {
            android.content.SharedPreferences p = getSharedPreferences("road_display", MODE_PRIVATE);
            // CASA is an exact coordinate bookmark. Always put the coordinates captured
            // by the map flag into the destination field. Never substitute a reverse-
            // geocoded street address: the saved map point is the authoritative target.
            String coordinates = formatSavedHomeCoordinates(p);
            if (!coordinates.isEmpty()) {
                field.setText(coordinates);
                field.setSelection(field.length());
                return;
            }
            android.widget.Toast.makeText(MainActivity.this, "Prima salva CASA sulla mappa", android.widget.Toast.LENGTH_SHORT).show();
        }

        String formatSavedHomeCoordinates(android.content.SharedPreferences p) {
            try {
                double lat=Double.parseDouble(p.getString(PREF_HOME_LAT, "NaN"));
                double lon=Double.parseDouble(p.getString(PREF_HOME_LON, "NaN"));
                if(Double.isFinite(lat) && Double.isFinite(lon) && lat>=-90.0 && lat<=90.0 && lon>=-180.0 && lon<=180.0) {
                    return String.format(Locale.US, "%.8f, %.8f", lat, lon);
                }
            } catch(Exception ignored) {}
            return "";
        }

        boolean hasValidSavedHome(android.content.SharedPreferences p) {
            if (p == null) return false;
            try {
                double lat=Double.parseDouble(p.getString(PREF_HOME_LAT, "NaN"));
                double lon=Double.parseDouble(p.getString(PREF_HOME_LON, "NaN"));
                return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
            } catch (Exception ignored) { return false; }
        }

        void stopNavigation() {
            navigationActive=false; navigationLoading=false; navigationStatus=""; navigationInstruction=""; navigationDistance=""; navigationDestinationDistance="";
            synchronized (navigationLock) { navigationRoute.clear(); navigationSteps.clear(); }
            synchronized (etaLock) { navigationRouteCumM.clear(); navigationEtaSamples.clear(); }
            navigationStepIndex=0; lastSpokenNavigationStep=-1; lastSpokenNavigationStage=-1; lastSpokenRoundaboutExitAt=-1L;
            navigationRouteProgressM=0.0; navigationRouteSegmentIndex=0; navigationOffRouteSince=0L; navigationRouteDeviation=0f;
            navigationMatchedLat=Double.NaN; navigationMatchedLon=Double.NaN; navigationMatchedBearing=Float.NaN; navigationLastFixAt=0L;
            routeMapMatcher.reset();
            synchronized(etaLock){ navigationStepProgressM.clear(); }
            navigationDestLat=Double.NaN; navigationDestLon=Double.NaN; navigationDestinationLabel="";
            navigationRouteDistanceM=0.0; navigationRouteBaseDurationSec=0.0; navigationEtaLoading=false; navigationEtaReady=false;
            navigationEtaEpochMs=0L; navigationEtaRemainingSec=0L; navigationEtaProgressM=0.0; navigationObservedFactor=1.0f; navigationStartedAt=0L;
            view.invalidate();
        }

        boolean isUsableNavigationStart(Location l) {
            if (l == null) return false;
            if (LocationManager.GPS_PROVIDER.equals(l.getProvider()) && l.hasAccuracy() && l.getAccuracy() <= 80f) return true;
            long age = Math.abs(System.currentTimeMillis() - l.getTime());
            return age <= 15L * 60L * 1000L && l.hasAccuracy() && l.getAccuracy() <= 250f;
        }

        Location getBestLastKnownNavigationStart() {
            Location best = null;
            if (lm != null) {
                String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
                for (String provider : providers) {
                    try {
                        Location candidate = lm.getLastKnownLocation(provider);
                        if (candidate == null) continue;
                        long age = Math.abs(System.currentTimeMillis() - candidate.getTime());
                        if (age > 2L * 60L * 60L * 1000L) continue;
                        if (best == null || (candidate.hasAccuracy() && (!best.hasAccuracy() || candidate.getAccuracy() < best.getAccuracy())) || candidate.getTime() > best.getTime()) best = new Location(candidate);
                    } catch (Exception ignored) { }
                }
            }
            if (best == null) {
                Location saved = loadSavedLocation();
                if (saved != null) {
                    long age = Math.abs(System.currentTimeMillis() - saved.getTime());
                    if (age <= 24L * 60L * 60L * 1000L) best = saved;
                }
            }
            return best;
        }

        ArrayList<RoutePoint> decodePolyline6(String encoded) throws Exception {
            ArrayList<RoutePoint> out = new ArrayList<>();
            if (encoded == null || encoded.isEmpty()) return out;
            int index = 0, lat = 0, lon = 0;
            while (index < encoded.length()) {
                int result = 0, shift = 0, b;
                do { if (index >= encoded.length()) throw new Exception("Polyline lat incompleta"); b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
                lat += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                result = 0; shift = 0;
                do { if (index >= encoded.length()) throw new Exception("Polyline lon incompleta"); b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
                lon += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                out.add(new RoutePoint(lat / 1_000_000.0, lon / 1_000_000.0));
            }
            return out;
        }

        String valhallaManeuverType(int type) {
            switch (type) {
                case 1: return "start"; case 2: return "start_right"; case 3: return "start_left";
                case 4: return "arrive"; case 5: return "arrive_right"; case 6: return "arrive_left";
                case 7: return "becomes"; case 8: return "continue";
                case 9: return "slight_left"; case 10: return "right"; case 11: return "sharp_right";
                case 12: return "uturn_right"; case 13: return "uturn_left"; case 14: return "sharp_left";
                case 15: return "left"; case 16: return "slight_right";
                case 17: return "ramp_straight"; case 18: return "ramp_right"; case 19: return "ramp_left";
                case 20: return "exit_right"; case 21: return "exit_left";
                case 22: return "stay_straight"; case 23: return "stay_right"; case 24: return "stay_left";
                case 25: return "merge"; case 26: return "roundabout"; case 27: return "roundabout_exit";
                default: return "other";
            }
        }

        String firstStreetName(JSONObject m) {
            JSONArray names = m == null ? null : m.optJSONArray("street_names");
            if (names != null && names.length() > 0) {
                String n = names.optString(0, "").trim();
                if (!n.isEmpty()) return n;
            }
            JSONArray begin = m == null ? null : m.optJSONArray("begin_street_names");
            if (begin != null && begin.length() > 0) return begin.optString(0, "").trim();
            return "";
        }

        String routeManeuverInstruction(String type, int roundaboutExit, String roadName, String nextRoadName) {
            if ("roundabout".equals(type)) return roundaboutExit > 0 ? "ROTONDA · " + ordinalItalian(roundaboutExit).toUpperCase(Locale.ROOT) + " USCITA" : "ROTONDA";
            if ("roundabout_exit".equals(type)) return nextRoadName == null || nextRoadName.isEmpty() ? "USCITA DALLA ROTONDA" : "USCITA ROTONDA · " + nextRoadName;
            if ("arrive".equals(type) || "arrive_right".equals(type) || "arrive_left".equals(type)) return "ARRIVO";
            if ("uturn_right".equals(type) || "uturn_left".equals(type)) return "INVERSIONE";
            if ("right".equals(type) || "ramp_right".equals(type) || "exit_right".equals(type)) return "SVOLTA A DESTRA";
            if ("left".equals(type) || "ramp_left".equals(type) || "exit_left".equals(type)) return "SVOLTA A SINISTRA";
            if ("sharp_right".equals(type)) return "SVOLTA STRETTA A DESTRA";
            if ("sharp_left".equals(type)) return "SVOLTA STRETTA A SINISTRA";
            if ("stay_right".equals(type)) return "AL BIVIO, PRENDI DESTRA";
            if ("stay_left".equals(type)) return "AL BIVIO, PRENDI SINISTRA";
            if ("stay_straight".equals(type)) return "AL BIVIO, PROSEGUI DRITTO";
            if ("merge".equals(type)) return "IMMETTITI";
            if ("ramp_straight".equals(type)) return "PRENDI LA RAMPA";
            return "PROSEGUI";
        }

        NavigationRouteResult fetchValhallaRoute(Location start, double destLat, double destLon) throws Exception {
            JSONObject req = new JSONObject();
            JSONArray locs = new JSONArray();
            JSONObject a = new JSONObject(); a.put("lat", start.getLatitude()); a.put("lon", start.getLongitude());
            if (start.hasBearing() && start.getSpeed() >= 2.0f) { a.put("heading", Math.round(start.getBearing())); a.put("heading_tolerance", 25); }
            if (start.hasAccuracy()) a.put("search_cutoff", Math.max(20.0, Math.min(60.0, start.getAccuracy()*1.5)));
            JSONObject b = new JSONObject(); b.put("lat", destLat); b.put("lon", destLon);
            locs.put(a); locs.put(b);
            req.put("locations", locs); req.put("costing", "motorcycle"); req.put("units", "kilometers");
            req.put("language", "it-IT"); req.put("directions_type", "instructions"); req.put("roundabout_exits", true);
            String url = "https://valhalla1.openstreetmap.de/route?json=" + java.net.URLEncoder.encode(req.toString(), "UTF-8");
            HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
            try {
                c.setConnectTimeout(3500); c.setReadTimeout(6500); c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.30");
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new Exception("Valhalla HTTP " + c.getResponseCode());
                JSONObject root = new JSONObject(readHttp(c));
                JSONObject trip = root.optJSONObject("trip");
                if (trip == null) trip = root;
                JSONObject summary = trip.optJSONObject("summary");
                JSONArray legs = trip.optJSONArray("legs");
                if (legs == null || legs.length() == 0) throw new Exception("Valhalla: legs assenti");
                NavigationRouteResult rr = new NavigationRouteResult(); rr.provider = "VALHALLA";
                double total = 0.0;
                for (int li=0; li<legs.length(); li++) {
                    JSONObject leg = legs.getJSONObject(li);
                    ArrayList<RoutePoint> shape = decodePolyline6(leg.optString("shape", ""));
                    if (shape.size() < 2) continue;
                    int baseIndex = rr.route.size();
                    for (int i=0; i<shape.size(); i++) {
                        if (li > 0 && i == 0 && !rr.route.isEmpty()) continue;
                        rr.route.add(shape.get(i));
                    }
                    JSONArray mans = leg.optJSONArray("maneuvers");
                    if (mans != null) for (int mi=0; mi<mans.length(); mi++) {
                        JSONObject m = mans.getJSONObject(mi);
                        int typeNum = m.optInt("type", 0);
                        String type = valhallaManeuverType(typeNum);
                        int begin = m.optInt("begin_shape_index", -1) + baseIndex;
                        int end = m.optInt("end_shape_index", -1) + baseIndex;
                        if (begin < 0 || begin >= rr.route.size()) continue;
                        begin = Math.max(0, Math.min(rr.route.size()-1, begin));
                        end = Math.max(begin, Math.min(rr.route.size()-1, end));
                        RoutePoint rp = rr.route.get(begin);
                        double lengthM = Math.max(0.0, m.optDouble("length", 0.0) * 1000.0);
                        int exit = m.optInt("roundabout_exit_count", -1);
                        String road = firstStreetName(m);
                        String nextRoad = "";
                        if (mi + 1 < mans.length()) nextRoad = firstStreetName(mans.optJSONObject(mi + 1));
                        String ins = routeManeuverInstruction(type, exit, road, nextRoad);
                        RouteStep st = new RouteStep(rp.lat, rp.lon, lengthM, ins, road, type, exit);
                        st.nextRoadName = nextRoad;
                        st.voicePre = m.optString("verbal_pre_transition_instruction", "");
                        st.voicePost = m.optString("verbal_post_transition_instruction", "");
                        st.beginShapeIndex = begin; st.endShapeIndex = end;
                        st.guidanceRelevant = !("slight_left".equals(type) || "slight_right".equals(type) || "continue".equals(type) || "becomes".equals(type) || "start".equals(type));
                        if ("roundabout".equals(type) && exit <= 0) st.guidanceRelevant = false;
                        rr.steps.add(st);
                    }
                }
                if (rr.route.size() < 2) throw new Exception("Valhalla: geometria assente");
                rr.distanceM = summary != null ? summary.optDouble("length", 0.0) * 1000.0 : pathDistance(rr.route);
                rr.durationSec = summary != null ? summary.optDouble("time", 0.0) : 0.0;
                if (rr.distanceM <= 0.0) rr.distanceM = pathDistance(rr.route);
                double[] cum = new double[rr.route.size()];
                for (int i=1;i<rr.route.size();i++) cum[i]=cum[i-1]+distanceMeters(rr.route.get(i-1).lat,rr.route.get(i-1).lon,rr.route.get(i).lat,rr.route.get(i).lon);
                for (RouteStep st: rr.steps) {
                    if (st.beginShapeIndex >= 0 && st.beginShapeIndex < cum.length) st.routeProgressM=cum[st.beginShapeIndex];
                    if (st.endShapeIndex >= 0 && st.endShapeIndex < cum.length) st.endRouteProgressM=cum[st.endShapeIndex];
                }
                return rr;
            } finally { try { c.disconnect(); } catch (Exception ignored) {} }
        }

        NavigationRouteResult fetchOsrmRoute(Location start, double destLat, double destLon) throws Exception {
            String routeUrl = buildNavigationRouteUrl(start, destLat, destLon);
            HttpURLConnection c = (HttpURLConnection)new URL(routeUrl).openConnection();
            try {
                c.setConnectTimeout(3000); c.setReadTimeout(6000); c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.30");
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new Exception("OSRM HTTP " + c.getResponseCode());
                JSONObject root = new JSONObject(readHttp(c));
                if (!"Ok".equalsIgnoreCase(root.optString("code"))) throw new Exception("OSRM route non disponibile");
                JSONArray routes = root.optJSONArray("routes"); if (routes == null || routes.length() == 0) throw new Exception("OSRM: nessun percorso");
                JSONObject route = routes.getJSONObject(0), geometry = route.optJSONObject("geometry"); JSONArray coords = geometry == null ? null : geometry.optJSONArray("coordinates");
                if (coords == null || coords.length() < 2) throw new Exception("OSRM: geometria assente");
                NavigationRouteResult rr = new NavigationRouteResult(); rr.provider="OSRM";
                for (int i=0;i<coords.length();i++){JSONArray pt=coords.getJSONArray(i);rr.route.add(new RoutePoint(pt.getDouble(1),pt.getDouble(0)));}
                JSONArray legs=route.optJSONArray("legs");
                if(legs!=null) for(int li=0;li<legs.length();li++){
                    JSONArray steps=legs.getJSONObject(li).optJSONArray("steps"); if(steps==null)continue;
                    for(int si=0;si<steps.length();si++){
                        JSONObject st=steps.getJSONObject(si),man=st.optJSONObject("maneuver"); if(man==null)continue;
                        JSONArray loc=man.optJSONArray("location"); if(loc==null||loc.length()<2)continue;
                        String type=man.optString("type",""); String mod=man.optString("modifier",""); String name=st.optString("name",""); if(name.isEmpty())name=st.optString("ref","");
                        int exit=man.optInt("exit",-1); String ins=navInstruction(type,mod,name);
                        if(("roundabout".equals(type)||"rotary".equals(type))&&exit>0)ins="ROTONDA · "+ordinalItalian(exit).toUpperCase(Locale.ROOT)+" USCITA";
                        RouteStep rs=new RouteStep(loc.getDouble(1),loc.getDouble(0),st.optDouble("distance",0),ins,name,type,exit);
                        rs.guidanceRelevant=!("new name".equals(type)||"continue".equals(type)||"notification".equals(type)||"slight left".equals(mod)||"slight right".equals(mod));
                        // A roundabout without a reliable exit number is not a useful rider
                        // maneuver. Do not draw a question-mark roundabout in live guidance.
                        if (("roundabout".equals(type)||"rotary".equals(type)) && exit<=0) rs.guidanceRelevant=false;
                        if (("roundabout".equals(type)||"rotary".equals(type)) && exit>0) rs.nextRoadName="";
                        rr.steps.add(rs);
                    }
                }
                rr.distanceM=route.optDouble("distance",pathDistance(rr.route)); rr.durationSec=route.optDouble("duration",0.0);
                synchronized(navigationLock){ /* no-op: result is immutable after construction */ }
                return rr;
            } finally { try { c.disconnect(); } catch (Exception ignored) {} }
        }

        NavigationRouteResult fetchNavigationRoute(Location start, double destLat, double destLon) throws Exception {
            // Ask both routing engines concurrently. The first complete, valid result wins;
            // this removes the old sequential "wait for server A, then wait for server B" delay.
            java.util.concurrent.ExecutorService ex=java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.Future<NavigationRouteResult> va=ex.submit(() -> { try { return fetchValhallaRoute(start,destLat,destLon); } catch(Exception e){ return null; } });
            java.util.concurrent.Future<NavigationRouteResult> os=ex.submit(() -> { try { return fetchOsrmRoute(start,destLat,destLon); } catch(Exception e){ return null; } });
            long deadline=System.currentTimeMillis()+8000L; NavigationRouteResult osrmFallback=null; boolean valhallaChecked=false, osrmChecked=false;
            try {
                while(System.currentTimeMillis()<deadline){
                    // Valhalla is primary. OSRM is only a fallback after Valhalla has
                    // actually failed, never simply because OSRM answered sooner.
                    if(!valhallaChecked && va.isDone()){
                        valhallaChecked=true;
                        NavigationRouteResult r=va.get();
                        if(r!=null && r.route.size()>=2 && !r.steps.isEmpty()) return r;
                    }
                    if(!osrmChecked && os.isDone()){
                        osrmChecked=true;
                        NavigationRouteResult r=os.get();
                        if(r!=null && r.route.size()>=2 && !r.steps.isEmpty()) osrmFallback=r;
                    }
                    if(valhallaChecked && osrmFallback!=null) return osrmFallback;
                    try { Thread.sleep(25L); } catch(InterruptedException ie){ Thread.currentThread().interrupt(); break; }
                }
                if(osrmFallback!=null) return osrmFallback;
            } finally { ex.shutdownNow(); }
            throw new Exception("Percorso non disponibile");
        }

        void startNavigation(final String departureQuery, final String destinationQuery) {
            final String fromQuery = departureQuery == null ? "" : departureQuery.trim();
            final String toQuery = destinationQuery == null ? "" : destinationQuery.trim();
            Location gpsStart = engine.loc;
            if (fromQuery.isEmpty() && !isUsableNavigationStart(gpsStart)) {
                Location fallback = getBestLastKnownNavigationStart();
                if (fallback != null) {
                    gpsStart = fallback;
                    engine.loc = new Location(fallback);
                }
            }

            if (toQuery.isEmpty()) {
                navigationStatus="DESTINAZIONE MANCANTE";
                navigationActive=false; navigationLoading=false; view.invalidate();
                return;
            }
            if (fromQuery.isEmpty() && gpsStart == null) {
                navigationStatus="POSIZIONE DI PARTENZA NON DISPONIBILE";
                navigationActive=false; navigationLoading=false; view.invalidate();
                return;
            }

            // Snapshot the selected start position before entering the worker thread.
            // Java lambdas may capture only final/effectively-final local variables.
            final Location navigationStart = gpsStart == null ? null : new Location(gpsStart);

            navigationLoading=true;
            navigationActive=false;
            navigationStatus="RICERCA DESTINAZIONE…";
            view.invalidate();

            new Thread(() -> {
                try {
                    Location start = navigationStart == null ? null : new Location(navigationStart);
                    android.content.SharedPreferences hp=getSharedPreferences("road_display", MODE_PRIVATE);
                    String savedHome=hp.getString(PREF_HOME_ADDRESS, "");

                    if(!fromQuery.isEmpty()) {
                        double[] home=homeCoordsIfMatch(fromQuery,savedHome,hp);
                        if(home!=null) {
                            start=new Location("saved-home");
                            start.setLatitude(home[0]); start.setLongitude(home[1]);
                        } else {
                            start=geocodeAddressPhoton(fromQuery);
                            if(start==null) start=geocodeAddressNominatim(fromQuery);
                            if(start==null) throw new Exception("Partenza non trovata");
                        }
                    }

                    if(start==null) throw new Exception("Posizione di partenza non disponibile");

                    navigationStatus="RICERCA DESTINAZIONE…";
                    if (!activityDestroyed && view != null) view.postInvalidate();
                    // CASA is authoritative: resolve the exact stored coordinates BEFORE
                    // the generic coordinate parser, so the route uses the original double
                    // values saved by the map flag rather than the rounded text shown in UI.
                    double[] homeDestination=homeCoordsIfMatch(toQuery,savedHome,hp);
                    Location destinationLocation=null;
                    if(homeDestination!=null) {
                        destinationLocation=new Location("saved-home");
                        destinationLocation.setLatitude(homeDestination[0]); destinationLocation.setLongitude(homeDestination[1]);
                    } else {
                        destinationLocation=parseCoordinateDestination(toQuery);
                        if(destinationLocation==null) {
                            destinationLocation=geocodeAddressPhoton(toQuery);
                            if(destinationLocation==null) destinationLocation=geocodeAddressNominatim(toQuery);
                        }
                    }
                    if(destinationLocation==null) throw new Exception("Destinazione non trovata");

                    final double lat=destinationLocation.getLatitude();
                    final double lon=destinationLocation.getLongitude();
                    navigationDestLat=lat; navigationDestLon=lon;
                    String resolvedLabel=navigationDestinationLabelOverride;
                    navigationDestinationLabel=(resolvedLabel==null || resolvedLabel.trim().isEmpty()) ? toQuery : resolvedLabel.trim();
                    navigationDestinationLabelOverride="";
                    saveRecentDestination(navigationDestinationLabel);
                    if (!activityDestroyed && view != null) view.postInvalidate();
                    navigationStatus="CALCOLO PERCORSO…";
                    if (!activityDestroyed && view != null) view.postInvalidate();

                    NavigationRouteResult routeResult = fetchNavigationRoute(start, lat, lon);
                    if (routeResult == null || routeResult.route.size() < 2) throw new Exception("Percorso non valido");
                    final ArrayList<RoutePoint> newRoute = routeResult.route;
                    final ArrayList<RouteStep> newSteps = routeResult.steps;
                    final double routeDistanceM = routeResult.distanceM > 0.0 ? routeResult.distanceM : pathDistance(newRoute);
                    final double routeDurationSec = routeResult.durationSec;
                    // Suppress purely geometric road bends. Only genuine navigation maneuvers
                    // (turns, forks, ramps, merges, roundabouts and arrival) are shown/spoken.
                    newSteps.removeIf(st -> !st.guidanceRelevant);
                    for (int i=0;i<newSteps.size()-1;i++) {
                        if (newSteps.get(i).nextRoadName == null || newSteps.get(i).nextRoadName.isEmpty())
                            newSteps.get(i).nextRoadName = newSteps.get(i+1).roadName;
                    }
                    suppressFalseSameRoadTurns(newSteps);
                    newSteps.removeIf(st -> !st.guidanceRelevant);

                    synchronized(navigationLock) {
                        navigationRoute.clear(); navigationRoute.addAll(newRoute);
                        navigationSteps.clear(); navigationSteps.addAll(newSteps);
                        navigationStepIndex=navigationSteps.isEmpty()?0:firstUsefulStep(start);
                        lastSpokenNavigationStep=-1;
                        lastSpokenNavigationStage=-1;
                        lastSpokenRoundaboutExitAt=-1L;
                        navigationInstruction=navigationSteps.isEmpty()?"PROSEGUI":navigationSteps.get(navigationStepIndex).instruction;
                        navigationDistance="";
                        navigationStatus="";
                        navigationActive=true;
                        navigationLoading=false;
                        navigationLastRouteAt=System.currentTimeMillis();
                        navigationRouteDistanceM=routeDistanceM;
                        navigationRouteBaseDurationSec=routeDurationSec;
                        navigationEtaProgressM=0.0;
                        navigationObservedFactor=1.0f;
                        navigationStartedAt=System.currentTimeMillis();
                        navigationStartedProgressM=0.0;
                        navigationEtaEpochMs=0L;
                        navigationEtaRemainingSec=0L;
                        navigationEtaReady=false;
                        navigationEtaLoading=false;
                    }
                    routeMapMatcher.reset();
                    rebuildNavigationRouteCumulative(newRoute);
                    synchronized(navigationLock){ if(!navigationSteps.isEmpty()) navigationDistance=formatNavDistance(start); }
                    requestRouteSpeedProfile(newRoute);
                    if(!newSteps.isEmpty()) { speakNavigationMessage("Navigazione avviata."); }
                    if (!activityDestroyed && view != null) view.postInvalidate();
                } catch(Exception ex) {
                    navigationLoading=false; navigationActive=false;
                    String reason=ex==null?"":ex.getMessage();
                    if(reason==null || reason.trim().isEmpty()) reason="errore di rete o percorso non disponibile";
                    final String userReason=reason;
                    navigationDestinationLabelOverride="";
                    navigationStatus="NAVIGAZIONE NON DISPONIBILE";
                    navigationInstruction=""; navigationDistance=""; navigationDestinationDistance="";
                    if (!activityDestroyed && view != null) view.post(() -> {
                        android.widget.Toast.makeText(MainActivity.this, "Navigazione: "+userReason, android.widget.Toast.LENGTH_LONG).show();
                        view.postInvalidate();
                    });
                }
            },"navigation-route").start();
        }

        Location parseCoordinateDestination(String query) {
            if (query == null) return null;
            String q = query.trim().replace(';', ',');
            // Accept the exact format produced by the full-screen map picker:
            // latitude,longitude. Also tolerate whitespace around the comma.
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*$")
                    .matcher(q);
            if (!m.matches()) return null;
            try {
                double lat = Double.parseDouble(m.group(1));
                double lon = Double.parseDouble(m.group(2));
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
                Location l = new Location("map-coordinate");
                l.setLatitude(lat);
                l.setLongitude(lon);
                return l;
            } catch (Exception ignored) { return null; }
        }

        // Global geocoder: Photon/OpenStreetMap is tried first so the app is not Spain-specific.
        // CartoCiudad remains a Spain-specific high-quality fallback, then global Nominatim.
        Location geocodeAddressPhoton(String query) throws Exception {
            String clean = query == null ? "" : query.trim();
            if (clean.isEmpty()) return null;
            String enc = java.net.URLEncoder.encode(clean, "UTF-8");
            String url = "https://photon.komoot.io/api/?q=" + enc + "&limit=8&lang=it";
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("User-Agent", "RoadDisplayPRO/3.22.17 (Android motorcycle dashboard)");
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                JSONObject root = new JSONObject(readHttp(c));
                JSONArray features = root.optJSONArray("features");
                if (features == null || features.length() == 0) return null;
                String ql = clean.toLowerCase(Locale.ROOT);
                JSONObject best = null; int bestScore = Integer.MIN_VALUE;
                for (int i=0; i<features.length(); i++) {
                    JSONObject f = features.optJSONObject(i); if (f == null) continue;
                    JSONObject prop = f.optJSONObject("properties");
                    JSONObject geom = f.optJSONObject("geometry");
                    JSONArray coord = geom == null ? null : geom.optJSONArray("coordinates");
                    if (coord == null || coord.length() < 2) continue;
                    String hay = prop == null ? "" : prop.toString().toLowerCase(Locale.ROOT);
                    int score = 0;
                    String[] toks = ql.split("\\s+");
                    for (String t : toks) if (t.length() >= 3 && hay.contains(t)) score += 5;
                    if (prop != null) {
                        String hnum = prop.optString("housenumber", "");
                        String street = prop.optString("street", "");
                        if (!hnum.isEmpty() && ql.contains(hnum.toLowerCase(Locale.ROOT))) score += 25;
                        if (!street.isEmpty() && ql.contains(street.toLowerCase(Locale.ROOT))) score += 25;
                    }
                    if (score > bestScore) { bestScore = score; best = f; }
                }
                if (best == null) return null;
                JSONArray coord = best.getJSONObject("geometry").getJSONArray("coordinates");
                Location r = new Location("photon");
                r.setLongitude(coord.getDouble(0)); r.setLatitude(coord.getDouble(1));
                return r;
            } finally { if (c != null) c.disconnect(); }
        }

        Location geocodeAddressCartoCiudad(String query) throws Exception {
            String clean = query == null ? "" : query.trim();
            if (clean.isEmpty()) return null;
            String[] queries = new String[]{
                    clean + ", Adeje, Santa Cruz de Tenerife",
                    clean + ", Tenerife, Canarias"
            };
            for (String q : queries) {
                String enc = java.net.URLEncoder.encode(q, "UTF-8");
                String url = "https://www.cartociudad.es/geocoder/api/geocoder/candidates?q=" + enc
                        + "&limit=12&countrycode=es";
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection)new URL(url).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET");
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "RoadDisplayPRO/3.22.17 (Android motorcycle dashboard)");
                    if (c.getResponseCode() != HttpURLConnection.HTTP_OK) continue;
                    JSONArray arr = new JSONArray(readHttp(c));
                    JSONObject best = null; int bestScore = Integer.MIN_VALUE;
                    for (int i=0;i<arr.length();i++) {
                        JSONObject it = arr.getJSONObject(i);
                        String type = it.optString("type", "");
                        String address = it.optString("address", "");
                        String muni = it.optString("muni", "");
                        String poblacion = it.optString("poblacion", "");
                        String hay = (address + " " + muni + " " + poblacion).toLowerCase(Locale.ROOT);
                        int score = 0;
                        if ("portal".equalsIgnoreCase(type)) score += 100;
                        if (hay.contains("adeje")) score += 50;
                        if (hay.contains("tijoco")) score += 40;
                        String[] toks = clean.toLowerCase(Locale.ROOT).split("\\s+");
                        for (String t : toks) if (t.length() >= 3 && hay.contains(t)) score += 5;
                        if (it.has("lat") && it.has("lng")) score += 10;
                        if (score > bestScore) { bestScore = score; best = it; }
                    }
                    if (best != null && best.has("lat") && best.has("lng")) {
                        Location r = new Location("cartociudad");
                        r.setLatitude(best.getDouble("lat")); r.setLongitude(best.getDouble("lng"));
                        return r;
                    }
                } finally { if (c != null) c.disconnect(); }
            }
            return null;
        }

        Location geocodeAddressNominatim(String query) throws Exception {
            long now=System.currentTimeMillis();
            long wait=1000L-(now-lastNominatimGeocodeAt);
            if(wait>0) Thread.sleep(wait);
            lastNominatimGeocodeAt=System.currentTimeMillis();
            String clean=query.trim();
            Matcher coord=Pattern.compile("@?\\s*(-?\\d+(?:\\.\\d+)?)[,;]\\s*(-?\\d+(?:\\.\\d+)?)").matcher(clean);
            if(coord.matches()) {
                double lat=Double.parseDouble(coord.group(1)), lon=Double.parseDouble(coord.group(2));
                if(Math.abs(lat)<=90 && Math.abs(lon)<=180) { Location r=new Location("saved-coordinate"); r.setLatitude(lat); r.setLongitude(lon); return r; }
            }
            String[] queries = new String[]{ clean };
            JSONArray bestArr=null;
            for(int qi=0;qi<queries.length;qi++) {
                if(qi>0) { Thread.sleep(Math.max(0L,1000L-(System.currentTimeMillis()-lastNominatimGeocodeAt))); lastNominatimGeocodeAt=System.currentTimeMillis(); }
                String enc=java.net.URLEncoder.encode(queries[qi],"UTF-8");
                String url="https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&addressdetails=1&layer=address&accept-language=it,en&q="+enc;
                HttpURLConnection c=null;
                try {
                    c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod("GET"); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","RoadDisplayPRO/3.22.17 (Android motorcycle dashboard; map attribution visible)");
                    if(c.getResponseCode()!=HttpURLConnection.HTTP_OK) continue;
                    JSONArray arr=new JSONArray(readHttp(c)); if(arr.length()>0){bestArr=arr; break;}
                } finally { if(c!=null)c.disconnect(); }
            }
            if(bestArr==null||bestArr.length()==0)return null;
            String ql=clean.toLowerCase(Locale.ROOT);
            JSONObject best=bestArr.getJSONObject(0); int bestScore=Integer.MIN_VALUE;
            for(int i=0;i<bestArr.length();i++){ JSONObject it=bestArr.getJSONObject(i); String disp=it.optString("display_name","").toLowerCase(Locale.ROOT); String name=it.optString("name","").toLowerCase(Locale.ROOT); int score=0;
                String[] toks=ql.split("\\s+"); for(String t:toks) if(t.length()>=3&&disp.contains(t))score+=5;
                if(!name.isEmpty()&&ql.contains(name))score+=10;
                JSONObject addr=it.optJSONObject("address");
                if(addr!=null){
                    String hn=addr.optString("house_number","");
                    String st=addr.optString("road","");
                    if(!hn.isEmpty()&&ql.matches(".*\\b"+java.util.regex.Pattern.quote(hn.toLowerCase(Locale.ROOT))+"\\b.*"))score+=25;
                    if(!st.isEmpty()&&ql.contains(st.toLowerCase(Locale.ROOT)))score+=25;
                }
                if(score>bestScore){bestScore=score;best=it;}
            }
            Location result=new Location("nominatim"); result.setLatitude(best.getDouble("lat")); result.setLongitude(best.getDouble("lon")); return result;
        }

        double[] homeCoordsIfMatch(String query,String savedHome,android.content.SharedPreferences hp) {
            if (query == null || hp == null) return null;
            String q=query.trim();
            if(q.isEmpty()) return null;
            // CASA is an explicit local destination token. Never send it to Photon/Nominatim:
            // a geocoder cannot reliably resolve a private, user-defined label like CASA.
            boolean isHomeToken="CASA".equalsIgnoreCase(q);
            boolean isSavedAddress=!savedHome.isEmpty() && q.equalsIgnoreCase(savedHome.trim());
            if(!isHomeToken && !isSavedAddress) return null;
            try {
                double lat=Double.parseDouble(hp.getString(PREF_HOME_LAT,"NaN"));
                double lon=Double.parseDouble(hp.getString(PREF_HOME_LON,"NaN"));
                if(Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90.0&&lat<=90.0&&lon>=-180.0&&lon<=180.0) return new double[]{lat,lon};
            } catch(Exception ignored) {}
            return null;
        }

        String readHttp(HttpURLConnection c) throws Exception { try(InputStream in=c.getInputStream(); BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"))){StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);return b.toString();} }
        double pathDistance(ArrayList<RoutePoint> route) {
            if (route == null || route.size() < 2) return 0.0;
            double total = 0.0;
            for (int i = 1; i < route.size(); i++) total += distanceMeters(route.get(i-1).lat, route.get(i-1).lon, route.get(i).lat, route.get(i).lon);
            return total;
        }

        void rebuildNavigationRouteCumulative(ArrayList<RoutePoint> route) {
            // Lock order is navigationLock -> etaLock everywhere in live navigation.
            // Keep the same order here to avoid a rare reroute/ETA deadlock.
            synchronized (navigationLock) {
                synchronized (etaLock) {
                    navigationRouteCumM.clear();
                    if (route == null || route.isEmpty()) { navigationStepProgressM.clear(); return; }
                    double total = 0.0;
                    navigationRouteCumM.add(0.0);
                    for (int i = 1; i < route.size(); i++) {
                        total += distanceMeters(route.get(i-1).lat, route.get(i-1).lon, route.get(i).lat, route.get(i).lon);
                        navigationRouteCumM.add(total);
                    }
                    if (navigationRouteDistanceM <= 0.0) navigationRouteDistanceM = total;
                }
                rebuildNavigationStepProgressLocked();
            }
        }

        void requestRouteSpeedProfile(final ArrayList<RoutePoint> route) {
            if (route == null || route.size() < 2 || navigationRouteDistanceM < 300.0 || navigationEtaLoading) return;
            final long routeToken = navigationLastRouteAt;
            navigationEtaLoading = true;
            final ArrayList<RoutePoint> samplePoints = new ArrayList<>();
            final ArrayList<Double> sampleDistances = new ArrayList<>();
            synchronized (navigationLock) {
                if (navigationRouteCumM.size() != route.size()) rebuildNavigationRouteCumulative(route);
            }
            synchronized (etaLock) {
                double total = navigationRouteDistanceM;
                double step = total <= 12000.0 ? 1000.0 : (total <= 50000.0 ? 1500.0 : 2000.0);
                int max = 60;
                for (double target = 0.0; target < total && samplePoints.size() < max - 1; target += step) {
                    int idx = nearestRouteIndexForDistanceLocked(target);
                    if (idx >= 0 && idx < route.size()) { samplePoints.add(route.get(idx)); sampleDistances.add(Math.min(target, total)); }
                }
                samplePoints.add(route.get(route.size()-1)); sampleDistances.add(total);
            }
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    JSONArray locs = new JSONArray();
                    for (RoutePoint rp : samplePoints) {
                        JSONObject o = new JSONObject(); o.put("lat", rp.lat); o.put("lon", rp.lon); locs.put(o);
                    }
                    JSONObject req = new JSONObject(); req.put("locations", locs); req.put("costing", "auto"); req.put("verbose", true);
                    String u = "https://valhalla1.openstreetmap.de/locate?json=" + java.net.URLEncoder.encode(req.toString(), "UTF-8");
                    c = (HttpURLConnection)new URL(u).openConnection();
                    c.setConnectTimeout(7000); c.setReadTimeout(12000); c.setRequestMethod("GET");
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.27 (Android motorcycle display)");
                    if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new Exception("Valhalla HTTP " + c.getResponseCode());
                    JSONArray result = new JSONArray(readHttp(c));
                    ArrayList<EtaSample> parsed = new ArrayList<>();
                    for (int i = 0; i < result.length() && i < sampleDistances.size(); i++) {
                        JSONObject loc = result.optJSONObject(i);
                        int limit = 0;
                        if (loc != null) {
                            JSONArray edges = loc.optJSONArray("edges");
                            if (edges != null) {
                                double bestDist = Double.MAX_VALUE;
                                for (int j=0; j<edges.length(); j++) {
                                    JSONObject e = edges.optJSONObject(j); if (e == null) continue;
                                    double ed = e.optDouble("distance", e.optDouble("side_of_street", 0));
                                    int lim = e.optInt("speed_limit", 0);
                                    JSONObject ei = e.optJSONObject("edge_info");
                                    if (lim <= 0 && ei != null) lim = ei.optInt("speed_limit", 0);
                                    if (lim >= 10 && lim <= 160 && (ed <= 0 || ed < bestDist)) { limit = lim; bestDist = ed; }
                                }
                            }
                        }
                        parsed.add(new EtaSample(sampleDistances.get(i), limit));
                    }
                    int known = 0;
                    for (EtaSample s : parsed) if (s.speedLimitKmh > 0) known++;
                    if (!navigationActive || activityDestroyed || routeToken != navigationLastRouteAt) return;
                    synchronized (etaLock) {
                        navigationEtaSamples.clear(); navigationEtaSamples.addAll(parsed);
                        navigationEtaReady = parsed.size() >= 2 && known >= Math.max(1, parsed.size()/3);
                    }
                } catch (Exception ignored) {
                    synchronized (etaLock) { navigationEtaSamples.clear(); navigationEtaReady = false; }
                } finally {
                    if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
                    navigationEtaLoading = false;
                    if (!activityDestroyed && view != null) view.postInvalidate();
                }
            }, "navigation-eta-profile").start();
        }

        int nearestRouteIndexForDistanceLocked(double targetM) {
            if (navigationRouteCumM.isEmpty()) return -1;
            int lo=0, hi=navigationRouteCumM.size()-1;
            while (lo < hi) { int mid=(lo+hi)>>>1; if (navigationRouteCumM.get(mid) < targetM) lo=mid+1; else hi=mid; }
            return lo;
        }

        double routeProgressMeters(Location l) {
            // During active navigation the authoritative progress is the road-locked
            // matcher state. Never re-project the raw GPS point here: callers such as
            // ETA and speed-limit display may run more than once per location callback.
            return navigationRouteProgressM;
        }

        int etaLimitAt(double distanceM) {
            synchronized (etaLock) {
                if (navigationEtaSamples.isEmpty()) return 0;
                EtaSample best = navigationEtaSamples.get(0);
                for (EtaSample s : navigationEtaSamples) { if (s.distanceM <= distanceM) best=s; else break; }
                return best.speedLimitKmh;
            }
        }

        double modelSecondsForRemaining(double progressM) {
            double total=navigationRouteDistanceM;
            if (total<=0.0) return 0.0;
            double remaining=Math.max(0.0,total-progressM);
            if (remaining<1.0) return 0.0;
            double baseDuration=navigationRouteBaseDurationSec;
            double fallbackKmh=(baseDuration>1.0)?(total/baseDuration)*3.6:70.0;
            fallbackKmh=Math.max(20.0,Math.min(110.0,fallbackKmh));
            synchronized (etaLock) {
                if (!navigationEtaReady || navigationEtaSamples.size()<2) return baseDuration>1.0 ? baseDuration*(remaining/total) : remaining/(fallbackKmh/3.6);
                double sec=0.0;
                double from=progressM;
                double to=total;
                double d=from;
                while (d < to) {
                    double end=to;
                    for (EtaSample sample : navigationEtaSamples) {
                        if (sample.distanceM > d + 0.5) { end=Math.min(end, sample.distanceM); break; }
                    }
                    double mid=(d+end)*0.5;
                    int lim=etaLimitAt(mid);
                    double cruise=lim>0 ? Math.max(20.0, lim*0.88) : fallbackKmh;
                    sec += (end-d)/(cruise/3.6);
                    if (end <= d + 0.01) break;
                    d=end;
                }
                return sec>0.0?sec:(baseDuration>1.0?baseDuration*(remaining/total):remaining/(fallbackKmh/3.6));
            }
        }

        void updateNavigationEta(Location l) {
            if (!navigationActive || l == null || navigationRouteDistanceM <= 0.0) return;
            double progress=routeProgressMeters(l);
            navigationEtaProgressM=progress;
            double remainingModel=modelSecondsForRemaining(progress);
            float actualKmh=l.hasSpeed()?l.getSpeed()*3.6f:0f;
            if (actualKmh >= 25f) {
                double localModelSpeed=0.0;
                if (navigationRouteDistanceM-progress >= 250.0) {
                    double aheadTime=remainingModel-modelSecondsForRemaining(progress+250.0);
                    if (aheadTime > 1.0) localModelSpeed=250.0/aheadTime*3.6;
                } else if (progress >= 250.0) {
                    double behindTime=modelSecondsForRemaining(progress-250.0)-remainingModel;
                    if (behindTime > 1.0) localModelSpeed=250.0/behindTime*3.6;
                }
                // Use a deliberately slow EMA so a single braking/acceleration event cannot move ETA wildly.
                if (localModelSpeed > 20.0 && localModelSpeed < 140.0) {
                    float target=(float)Math.max(0.78,Math.min(1.18,actualKmh/localModelSpeed));
                    navigationObservedFactor = navigationObservedFactor*0.94f + target*0.06f;
                }
            }
            double adjusted=remainingModel/Math.max(0.80,Math.min(1.20,navigationObservedFactor));
            navigationEtaRemainingSec=Math.max(0L,Math.round(adjusted));
            navigationEtaEpochMs=System.currentTimeMillis()+navigationEtaRemainingSec*1000L;
        }

        String formatEtaTime(long epochMs) {
            if (epochMs<=0L) return "--:--";
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(epochMs));
        }

        String formatEtaRemaining(long seconds) {
            if (seconds<=0L) return "0 min";
            long min=Math.max(1L,Math.round(seconds/60.0));
            if (min<60) return min+" min";
            return (min/60)+" h " + String.format(Locale.US,"%02d",min%60)+" min";
        }

        int firstUsefulStep(Location l){
            synchronized(navigationLock){
                if(navigationSteps.isEmpty())return 0;
                if(l==null||navigationRoute.size()<2)return 0;
                int currentRouteIndex=0; double currentBest=Double.MAX_VALUE;
                for(int i=0;i<navigationRoute.size();i++){RoutePoint p=navigationRoute.get(i);double d=distanceMeters(l.getLatitude(),l.getLongitude(),p.lat,p.lon);if(d<currentBest){currentBest=d;currentRouteIndex=i;}}
                for(int i=0;i<navigationSteps.size();i++){
                    RouteStep st=navigationSteps.get(i); int stepRouteIndex=0; double best=Double.MAX_VALUE;
                    for(int j=0;j<navigationRoute.size();j++){RoutePoint p=navigationRoute.get(j);double d=distanceMeters(st.lat,st.lon,p.lat,p.lon);if(d<best){best=d;stepRouteIndex=j;}}
                    if(stepRouteIndex>=currentRouteIndex-3)return i;
                }
                return Math.max(0,navigationSteps.size()-1);
            }
        }
        RouteStep upcomingRoundaboutLocked(Location l) {
            if (l == null || navigationSteps.isEmpty()) return null;
            int start = Math.max(0, Math.min(navigationStepIndex, navigationSteps.size() - 1));
            RouteStep best = null; double bestD = Double.MAX_VALUE;
            for (int i = start; i < Math.min(navigationSteps.size(), start + 2); i++) {
                RouteStep st = navigationSteps.get(i);
                String type = st.maneuverType == null ? "" : st.maneuverType.toLowerCase(Locale.ROOT);
                if (!("roundabout".equals(type) || "rotary".equals(type))) continue;
                double d = Double.isNaN(st.routeProgressM) ? distanceMeters(l.getLatitude(), l.getLongitude(), st.lat, st.lon) : Math.max(0.0, st.routeProgressM - navigationRouteProgressM);
                if (d <= 350.0 && d < bestD) { best = st; bestD = d; }
            }
            return best;
        }

        int directionFromModifier(String type) {
            if (type == null) return 1;
            String x = type.toLowerCase(Locale.ROOT);
            return (x.contains("left")) ? -1 : 1;
        }

        String navInstruction(String type,String modifier,String name){String m=modifier==null?"":modifier; if("roundabout".equals(type)||"rotary".equals(type))return "ROTONDA"; if("exit roundabout".equals(type)||"exit rotary".equals(type))return "USCITA DALLA ROTONDA"; if("arrive".equals(type))return "ARRIVO"; if("merge".equals(type))return "IMMETTITI"; if("fork".equals(type)||"end of road".equals(type)){if("left".equals(m))return "AL BIVIO, PRENDI SINISTRA";if("right".equals(m))return "AL BIVIO, PRENDI DESTRA";return "AL BIVIO, PROSEGUI";} if("uturn".equals(type))return "INVERSIONE"; if("new name".equals(type)||"notification".equals(type)||"continue".equals(type))return "PROSEGUI"; return directionLabel(m);}
        String directionLabel(String m){if("left".equals(m))return "SVOLTA A SINISTRA";if("right".equals(m))return "SVOLTA A DESTRA";if("slight left".equals(m))return "SINISTRA LEGGERA";if("slight right".equals(m))return "DESTRA LEGGERA";if("sharp left".equals(m))return "SINISTRA STRETTA";if("sharp right".equals(m))return "DESTRA STRETTA";return "PROSEGUI";}
        String formatNavDistance(Location l){
            synchronized(navigationLock){
                if(navigationSteps.isEmpty() || l==null) return "";
                updateRouteProgressLocked(l);
                advanceNavigationStepLocked();
                int idx=Math.max(0,Math.min(navigationStepIndex,navigationSteps.size()-1));
                RouteStep st=navigationSteps.get(idx);
                double target=st.routeProgressM;
                double d=Double.isNaN(target) ? distanceMeters(l.getLatitude(),l.getLongitude(),st.lat,st.lon) : Math.max(0.0,target-navigationRouteProgressM);
                if(d<0.0)d=0.0;
                return d<1000?Math.max(0,Math.round(d))+" m":String.format(Locale.US,"%.1f km",d/1000.0);
            }
        }

        // Project the GPS fix onto the route polyline. Once a route is being followed,
        // search locally around the previous segment to avoid snapping to a parallel
        // road or a later loop of the same route. A full search is used after a reroute
        // or when the local window has no valid candidate.
        double angleDifference(double a, double b) {
            double d = Math.abs((a - b) % 360.0);
            return d > 180.0 ? 360.0 - d : d;
        }

        double segmentBearing(RoutePoint a, RoutePoint b) {
            return bearingBetween(a.lat, a.lon, b.lat, b.lon);
        }

        double bearingBetween(double lat1, double lon1, double lat2, double lon2) {
            double p1=Math.toRadians(lat1), p2=Math.toRadians(lat2), dl=Math.toRadians(lon2-lon1);
            double y=Math.sin(dl)*Math.cos(p2);
            double x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);
            return (Math.toDegrees(Math.atan2(y,x))+360.0)%360.0;
        }

        double[] projectToSegmentWithBearing(Location l, RoutePoint a, RoutePoint b) {
            double[] pr=projectToSegment(l.getLatitude(),l.getLongitude(),a.lat,a.lon,b.lat,b.lon);
            double score=pr[0];
            if(l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 3.0f){
                double diff=angleDifference(l.getBearing(),segmentBearing(a,b));
                double acc=l.hasAccuracy()?Math.max(5.0,Math.min(60.0,l.getAccuracy())):25.0;
                double headingWeight=Math.max(0.25,Math.min(1.0,(35.0/acc)));
                score += Math.min(90.0,diff*0.85*headingWeight);
            }
            return new double[]{score,pr[0],pr[1]};
        }

        void updateRouteProgressLocked(Location l){
            if(l==null || navigationRoute.size()<2 || navigationRouteCumM.size()!=navigationRoute.size()) return;

            // Primary local map matcher: candidate road segments + Viterbi sequence
            // matching. The raw GNSS point is never allowed to choose a parallel
            // carriageway simply because it is a few metres closer.
            RouteMapMatcher.Result r = routeMapMatcher.update(l, navigationRoute, navigationRouteCumM, navigationRouteDistanceM);
            if(r != null && r.valid){
                double accepted = Math.max(navigationRouteProgressM, r.progressM);
                // Permit only a tiny correction backwards for GNSS jitter. Route
                // progress used for maneuvers/ETA must otherwise be monotonic.
                if(r.progressM + 12.0 >= navigationRouteProgressM) accepted = r.progressM;
                navigationRouteProgressM = Math.max(0.0, Math.min(navigationRouteDistanceM, accepted));
                navigationRouteSegmentIndex = Math.max(0, Math.min(navigationRoute.size()-2, r.segmentIndex));
                navigationRouteDeviation = (float)Math.max(0.0, r.lateralM);
                navigationMatchedLat = r.lat;
                navigationMatchedLon = r.lon;
                navigationMatchedBearing = (float)r.bearing;
                navigationLastFixAt = System.currentTimeMillis();
                return;
            }

            // No candidate: retain the last road-locked position. This is deliberate;
            // a transient GNSS failure must not put the marker on a nearby road.
            navigationRouteDeviation = Math.max(navigationRouteDeviation,
                    l.hasAccuracy() ? l.getAccuracy() : 50f);
        }

        double segmentDistance(RoutePoint a, RoutePoint b){ return distanceMeters(a.lat,a.lon,b.lat,b.lon); }
        double[] projectToSegment(double lat,double lon,double alat,double alon,double blat,double blon){
            double cos=Math.cos(Math.toRadians(lat));
            double ax=(alon-lon)*111320.0*cos, ay=(alat-lat)*110540.0;
            double bx=(blon-lon)*111320.0*cos, by=(blat-lat)*110540.0;
            double dx=bx-ax, dy=by-ay, len2=dx*dx+dy*dy;
            double t=len2<0.001?0.0:Math.max(0.0,Math.min(1.0,-(ax*dx+ay*dy)/len2));
            return new double[]{Math.hypot(ax+t*dx,ay+t*dy),t};
        }

        void rebuildNavigationStepProgressLocked(){
            navigationStepProgressM.clear();
            if(navigationSteps.isEmpty() || navigationRoute.size()<2 || navigationRouteCumM.size()!=navigationRoute.size()) return;
            for(RouteStep st:navigationSteps){
                double bestP=st.routeProgressM;
                if(Double.isNaN(bestP) || bestP < 0.0 || bestP > navigationRouteDistanceM + 100.0){
                    double best=Double.MAX_VALUE; bestP=0.0;
                    for(int i=0;i<navigationRoute.size()-1;i++){
                        RoutePoint a=navigationRoute.get(i),b=navigationRoute.get(i+1);
                        double[] pr=projectToSegment(st.lat,st.lon,a.lat,a.lon,b.lat,b.lon);
                        if(pr[0]<best){best=pr[0];bestP=navigationRouteCumM.get(i)+pr[1]*segmentDistance(a,b);}
                    }
                }
                st.routeProgressM=Math.max(0.0,Math.min(navigationRouteDistanceM,bestP));
                navigationStepProgressM.add(st.routeProgressM);
            }
        }

        void advanceNavigationStepLocked(){
            if(navigationSteps.isEmpty() || navigationStepProgressM.size()!=navigationSteps.size()) return;
            double p=navigationRouteProgressM;
            int idx=Math.max(0,Math.min(navigationStepIndex,navigationSteps.size()-1));
            // Skip already-passed maneuver anchors. A 12 m hysteresis prevents a
            // noisy fix from oscillating around a maneuver.
            while(idx<navigationSteps.size()-1){
                RouteStep cur=navigationSteps.get(idx);
                double advanceAt = cur.routeProgressM;
                if("roundabout".equals(cur.maneuverType) && Double.isFinite(cur.endRouteProgressM)) advanceAt=cur.endRouteProgressM;
                if(advanceAt > p+12.0) break;
                idx++;
            }
            if(idx!=navigationStepIndex){
                navigationStepIndex=idx; navigationInstruction=navigationSteps.get(idx).instruction;
                lastSpokenNavigationStage=-1;
            }
        }
        void speakNavigationMessage(String message) {
            if (!audio || !navigationTtsReady || navigationTts == null || message == null || message.trim().isEmpty()) return;
            final String spoken = message.trim();
            if (activityDestroyed) return;
            runOnUiThread(() -> {
                if (activityDestroyed || navigationTts == null) return;
                try { navigationTts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "nav-message-" + System.currentTimeMillis()); } catch (Exception ignored) {}
            });
        }

        String maneuverSpeech(String instruction) {
            if (instruction == null || instruction.trim().isEmpty()) return "prosegui";
            String x = instruction.toLowerCase(Locale.ITALIAN);
            if (x.contains("sinistra")) return "svolta a sinistra";
            if (x.contains("destra")) return "svolta a destra";
            if (x.contains("inversione")) return "fai inversione";
            if (x.contains("uscita dalla rotonda")) return "esci dalla rotonda";
            if (x.contains("rotonda")) return "entra nella rotonda";
            if (x.contains("arrivo")) return "arrivo";
            if (x.contains("bivio")) return "al bivio";
            if (x.contains("immettiti")) return "immettiti";
            return x;
        }

        String ordinalItalian(int n) {
            switch(n) {
                case 1: return "prima"; case 2: return "seconda"; case 3: return "terza";
                case 4: return "quarta"; case 5: return "quinta"; case 6: return "sesta";
                default: return n + "ª";
            }
        }

        String maneuverSpeech(RouteStep st) {
            if(st == null) return "prosegui";
            String type = st.maneuverType == null ? "" : st.maneuverType.toLowerCase(Locale.ITALIAN);
            if("roundabout".equals(type) && st.roundaboutExit > 0) {
                String s = "alla rotonda, prendi la " + ordinalItalian(st.roundaboutExit) + " uscita";
                if(st.nextRoadName != null && !st.nextRoadName.trim().isEmpty()) s += " verso " + st.nextRoadName.trim();
                return s;
            }
            if("roundabout_exit".equals(type)) {
                if(st.roadName != null && !st.roadName.trim().isEmpty()) return "esci dalla rotonda verso " + st.roadName.trim();
                if(st.nextRoadName != null && !st.nextRoadName.trim().isEmpty()) return "esci dalla rotonda verso " + st.nextRoadName.trim();
                return "esci dalla rotonda";
            }
            if("stay_right".equals(type)) return st.roadName.isEmpty() ? "al bivio, vai a destra" : "al bivio, vai a destra su " + st.roadName;
            if("stay_left".equals(type)) return st.roadName.isEmpty() ? "al bivio, vai a sinistra" : "al bivio, vai a sinistra su " + st.roadName;
            if("stay_straight".equals(type)) return st.roadName.isEmpty() ? "al bivio, prosegui dritto" : "al bivio, prosegui dritto su " + st.roadName;
            String base = maneuverSpeech(st.instruction);
            if(st.roadName != null && !st.roadName.trim().isEmpty() && !"arrivo".equalsIgnoreCase(st.instruction) && !"PROSEGUI".equalsIgnoreCase(st.instruction))
                base += " su " + st.roadName.trim();
            return base;
        }

        void speakNavigationStep(int index, RouteStep step, int stage, double distanceMetersValue) {
            if (!audio || !navigationTtsReady || navigationTts == null || step == null || step.instruction == null || step.instruction.isEmpty()) return;
            if (index == lastSpokenNavigationStep && stage == lastSpokenNavigationStage) return;

            // The threshold decides WHEN to speak; the actual GPS-derived distance decides WHAT to speak.
            // This avoids saying "500 m" when the rider is actually 380 m from the maneuver.
            int meters = Math.max(1, (int)Math.round(distanceMetersValue));
            StringBuilder msg = new StringBuilder();
            String speech = maneuverSpeech(step);
            if (stage >= 4) {
                msg.append("Ora, ").append(speech);
            } else {
                msg.append("Tra ").append(meters).append(" metri, ").append(speech);
            }

            lastSpokenNavigationStep = index;
            lastSpokenNavigationStage = stage;
            final String spoken = msg.toString();
            if (activityDestroyed) return;
            runOnUiThread(() -> {
                if (activityDestroyed || navigationTts == null) return;
                try {
                    navigationTts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "nav-step-" + index + "-" + stage);
                } catch (Exception ignored) {}
            });
        }

        double distanceToRouteMeters(Location l) {
            synchronized (navigationLock) {
                if (l == null || navigationRoute == null || navigationRoute.size() < 2) return Float.MAX_VALUE;
                double best = Double.MAX_VALUE;
                for (int i=0;i<navigationRoute.size()-1;i++) {
                    RoutePoint a=navigationRoute.get(i), b=navigationRoute.get(i+1);
                    double d=distanceToSegmentMeters(l.getLatitude(),l.getLongitude(),a.lat,a.lon,b.lat,b.lon);
                    if (d<best) best=d;
                }
                return best;
            }
        }

        double distanceToSegmentMeters(double lat,double lon,double alat,double alon,double blat,double blon) {
            double cos=Math.cos(Math.toRadians(lat));
            double ax=(alon-lon)*111320.0*cos, ay=(alat-lat)*110540.0;
            double bx=(blon-lon)*111320.0*cos, by=(blat-lat)*110540.0;
            double dx=bx-ax, dy=by-ay, len2=dx*dx+dy*dy;
            double t=len2<0.001?0.0:Math.max(0.0,Math.min(1.0,-(ax*dx+ay*dy)/len2));
            return Math.hypot(ax+t*dx,ay+t*dy);
        }

        String buildNavigationRouteUrl(Location start, double destLat, double destLon) {
            String base = "https://router.project-osrm.org/route/v1/driving/"
                    + start.getLongitude() + "," + start.getLatitude() + ";" + destLon + "," + destLat
                    + "?overview=full&geometries=geojson&steps=true&annotations=true&alternatives=false"
                    + "&continue_straight=true";
            // On a reroute, constrain the first snap to the rider's actual travel
            // direction and measured GPS accuracy. This prevents snapping to the
            // parallel/opposite carriageway when the two are close together.
            if (start.hasBearing() && start.getSpeed() >= 2.0f) {
                float accuracy = start.hasAccuracy() ? start.getAccuracy() : 25f;
                accuracy = Math.max(8f, Math.min(35f, accuracy));
                int bearing = Math.round(start.getBearing());
                if (bearing >= 360) bearing = bearing % 360;
                base += "&bearings=" + bearing + ",35;"
                        + "&radiuses=" + String.format(Locale.US, "%.0f", accuracy) + ";unlimited";
            }
            return base;
        }

        void suppressFalseSameRoadTurns(ArrayList<RouteStep> steps) {
            if(steps==null)return; for(int i=0;i<steps.size()-1;i++){RouteStep st=steps.get(i),n=steps.get(i+1);String a=st.roadName==null?"":st.roadName.trim(),b=n.roadName==null?"":n.roadName.trim(),t=st.maneuverType==null?"":st.maneuverType.toLowerCase(Locale.ROOT);boolean same=!a.isEmpty()&&!b.isEmpty()&&a.equalsIgnoreCase(b);boolean real="roundabout".equals(t)||"roundabout_exit".equals(t)||"fork".equals(t)||"merge".equals(t)||"end of road".equals(t)||"uturn".equals(t);if(same&&!real&&(t.contains("left")||t.contains("right")||"turn".equals(t)))st.guidanceRelevant=false;}
        }
        boolean isMotorwayStep(RouteStep st){if(st==null)return false;String n=((st.roadName==null?"":st.roadName)+" "+(st.nextRoadName==null?"":st.nextRoadName)).toLowerCase(Locale.ROOT);return n.contains("autopista")||n.contains("motorway")||n.contains("autovía")||n.contains("autovia")||n.matches(".*\\b(?:a|ap|tf)-[0-9]+\\b.*");}

        void requestNavigationReroute(Location l) {
            if (!navigationActive || l == null || Double.isNaN(navigationDestLat) || navigationRerouteInProgress) return;
            long now=System.currentTimeMillis();
            if (now-navigationLastRerouteAt < 2000L) return;
            navigationRerouteInProgress=true;
            navigationLastRerouteAt=now;
            final Location start=new Location(l);
            final double destLat=navigationDestLat, destLon=navigationDestLon;
            navigationStatus="RICALCOLO PERCORSO…";
            if (!activityDestroyed && view != null) view.postInvalidate();
            new Thread(() -> {
                try {
                    NavigationRouteResult routeResult = fetchNavigationRoute(start, destLat, destLon);
                    if (routeResult == null || routeResult.route.size() < 2) throw new Exception("Percorso non disponibile");
                    final ArrayList<RoutePoint> newRoute = routeResult.route;
                    final ArrayList<RouteStep> newSteps = routeResult.steps;
                    newSteps.removeIf(st -> !st.guidanceRelevant);
                    for (int i=0;i<newSteps.size()-1;i++) {
                        if (newSteps.get(i).nextRoadName == null || newSteps.get(i).nextRoadName.isEmpty())
                            newSteps.get(i).nextRoadName = newSteps.get(i+1).roadName;
                    }
                    suppressFalseSameRoadTurns(newSteps);
                    newSteps.removeIf(st -> !st.guidanceRelevant);
                    if (!navigationActive || activityDestroyed) return;
                    synchronized(navigationLock){
                        if (!navigationActive || activityDestroyed) return;
                        navigationRoute.clear(); navigationRoute.addAll(newRoute);
                        navigationSteps.clear(); navigationSteps.addAll(newSteps);
                        navigationStepIndex=navigationSteps.isEmpty()?0:firstUsefulStep(start);
                        lastSpokenNavigationStep=-1; lastSpokenNavigationStage=-1; lastSpokenRoundaboutExitAt=-1L;
                        navigationInstruction=navigationSteps.isEmpty()?"PROSEGUI":navigationSteps.get(navigationStepIndex).instruction;
                        navigationDistance="";
                        navigationStatus="";
                        navigationOffRouteSince=0L;
                        navigationRouteDeviation=0f;
                        navigationRouteProgressM=0.0;
                        navigationRouteSegmentIndex=0;
                        navigationMatchedLat=Double.NaN; navigationMatchedLon=Double.NaN;
                        navigationRouteDistanceM=routeResult.distanceM>0.0?routeResult.distanceM:pathDistance(newRoute);
                        navigationRouteBaseDurationSec=routeResult.durationSec;
                        navigationEtaProgressM=0.0;
                        navigationObservedFactor=1.0f;
                        navigationEtaEpochMs=0L;
                        navigationEtaRemainingSec=0L;
                        navigationEtaReady=false;
                        navigationEtaLoading=false;
                        navigationLastRouteAt=System.currentTimeMillis();
                    }
                    routeMapMatcher.reset();
                    rebuildNavigationRouteCumulative(newRoute);
                    synchronized(navigationLock){ if(!navigationSteps.isEmpty()) navigationDistance=formatNavDistance(start); }
                    requestRouteSpeedProfile(newRoute);
                    // Rerouting is deliberately silent: the next valid maneuver speaks on its own.
                }catch(Exception ignored){
                    if (navigationActive && !activityDestroyed) navigationStatus="";
                }finally{
                    navigationRerouteInProgress=false;
                    if(!activityDestroyed && view!=null)if (!activityDestroyed && view != null) view.postInvalidate();
                }
            },"navigation-reroute").start();
        }

        void updateNavigation(Location l){
            synchronized (navigationLock) {
            if(!navigationActive||l==null||navigationSteps.isEmpty())return;
            updateRouteProgressLocked(l);
            float speedKmh=l.hasSpeed()?l.getSpeed()*3.6f:0f;
            long now=System.currentTimeMillis();
            // Off-route is based on the matched route geometry, with the threshold
            // scaled to the actual reported GPS accuracy. Accurate fixes trigger quickly;
            // poor fixes do not cause false reroutes.
            float gpsAcc = l.hasAccuracy() ? l.getAccuracy() : 25f;
            double offRouteThreshold = Math.max(32.0, Math.min(75.0, gpsAcc * 1.8));
            boolean matcherOffRoute = routeMapMatcher.offRouteSamples() >= 3;
            if(navigationRouteDeviation>offRouteThreshold && speedKmh>=5f){
                if(navigationOffRouteSince==0L)navigationOffRouteSince=now;
                if(matcherOffRoute && now-navigationOffRouteSince>=1800L)requestNavigationReroute(l);
            }else if(navigationRouteDeviation<Math.max(22.0,offRouteThreshold*0.62)){
                navigationOffRouteSince=0L;
            }

            int before=navigationStepIndex;
            navigationDistance=formatNavDistance(l);
            if(Double.isNaN(navigationDestLat))return;
            double dest=distanceMeters(l.getLatitude(),l.getLongitude(),navigationDestLat,navigationDestLon);
            navigationDestinationDistance=dest<1000?Math.max(0,Math.round(dest))+" m":String.format(Locale.US,"%.1f km",dest/1000.0);
            // Arrival must be tied to the end of the routed path, not just straight-line
            // distance. This avoids declaring arrival on a parallel road or a nearby
            // junction that happens to be within a few metres of the destination.
            double endRouteDistance=Double.MAX_VALUE;
            if(!navigationRoute.isEmpty()){
                RoutePoint end=navigationRoute.get(navigationRoute.size()-1);
                endRouteDistance=distanceMeters(l.getLatitude(),l.getLongitude(),end.lat,end.lon);
            }
            double routeRemaining = navigationRouteDistanceM > 0.0 ? Math.max(0.0, navigationRouteDistanceM-navigationRouteProgressM) : endRouteDistance;
            boolean arrived = (dest <= 12.0 && endRouteDistance <= 30.0) || (routeRemaining <= 18.0 && dest <= 45.0 && endRouteDistance <= 35.0 && navigationRouteDeviation <= 50.0);
            if(arrived){
                navigationInstruction="ARRIVO";navigationDistance="0 m";
                if(lastSpokenNavigationStep!=-2){lastSpokenNavigationStep=-2;lastSpokenNavigationStage=3;speakNavigationMessage("Sei arrivato a destinazione.");}
                // Arrival is a terminal navigation state: stop guidance, rerouting and
                // further maneuver announcements while preserving the trip itself.
                navigationActive=false;
                navigationLoading=false;
                navigationStatus="DESTINAZIONE RAGGIUNTA";
                synchronized(navigationLock){ navigationRoute.clear(); navigationSteps.clear(); }
                navigationStepIndex=0;
                navigationDestLat=Double.NaN; navigationDestLon=Double.NaN;
                navigationDestinationLabel="";
                view.invalidate();return;
            }
            int idx=navigationStepIndex;
            if(idx>=0&&idx<navigationSteps.size()){
                RouteStep st=navigationSteps.get(idx);
                double d=Double.isNaN(st.routeProgressM)?distanceMeters(l.getLatitude(),l.getLongitude(),st.lat,st.lon):Math.max(0.0,st.routeProgressM-navigationRouteProgressM);
                if(idx!=before)lastSpokenNavigationStage=-1;
                int stage=-1;
                // Only two cues per genuine maneuver: one useful advance notice and
                // one immediate cue. Avoid a cascade of 250/100/50/8 m announcements.
                boolean motorway=isMotorwayStep(st);
                double preThreshold=motorway
                        ? Math.max(500.0, Math.min(1000.0, Math.max(500.0, speedKmh*5.5)))
                        : Math.max(180.0, Math.min(400.0, Math.max(180.0, speedKmh*4.5)));
                if(d<=25) stage=4;
                else if(d<=preThreshold) stage=0;
                if(stage>=0&&(idx!=lastSpokenNavigationStep||stage>lastSpokenNavigationStage))speakNavigationStep(idx,st,stage,d);
                // While inside a roundabout, announce the actual exit shortly before it.
                if("roundabout".equals(st.maneuverType) && Double.isFinite(st.endRouteProgressM) && idx+1<navigationSteps.size()) {
                    RouteStep exitStep=navigationSteps.get(idx+1);
                    if("roundabout_exit".equals(exitStep.maneuverType)) {
                        double exitDistance=Math.max(0.0,exitStep.routeProgressM-navigationRouteProgressM);
                        if(exitDistance<=25.0 && lastSpokenRoundaboutExitAt!=idx) {
                            lastSpokenRoundaboutExitAt=idx;
                            String target=exitStep.roadName==null?"":exitStep.roadName.trim();
                            speakNavigationMessage(target.isEmpty()?"Tra pochi metri, esci dalla rotonda":"Tra pochi metri, esci dalla rotonda verso "+target);
                        }
                    }
                }
            }
            if(navigationDistance.isEmpty())navigationDistance=formatNavDistance(l);
            updateNavigationEta(l);
            view.invalidate();
            }
        }
        /**
         * Curve warning is a self-contained card.  The card owns its background,
         * icon and text, so nothing can bleed into the navigation instruction below
         * (or beside it).  The icon is deliberately centred on the card's geometric
         * centre, not on the text or on an approximate triangle vertex.
         */
        void drawNavigationCurveAlert(Canvas c, float left, float right, float top, float bottom, boolean land) {
            if (!navigationActive) return;
            Curve q = engine.current;
            if (q == null || !q.valid || q.distance < 0f || q.distance > 100f) return;

            boolean blink = q.distance <= 50f;
            if (blink && ((System.currentTimeMillis() / 500L) & 1L) != 0L) {
                postInvalidateDelayed(120L);
                return;
            }

            drawCurveAlertCard(c, new RectF(left, top, right, bottom), q, land);
            postInvalidateDelayed(blink ? 120L : 1000L);
        }

        void drawCurveAlertCard(Canvas c, RectF area, Curve q, boolean land) {
            if (q == null || !q.valid) return;
            float pad = dp(6);
            RectF card = new RectF(area.left + pad, area.top + pad, area.right - pad, area.bottom - pad);
            if (card.width() <= dp(80) || card.height() <= dp(70)) return;

            // Opaque background: this is intentional.  The warning must remain readable
            // over every possible navigation label and map/dashboard element.
            int bg = q.type == 5 ? 0xFF071A24 : (q.type >= 3 ? 0xFF211010 : 0xFF10161C);
            panel(c, card, bg, dp(10));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(q.type == 5 ? 0xFF55D6FF : (q.type >= 3 ? 0xFFFFD400 : 0xFF6F7C88));
            c.drawRoundRect(card, dp(10), dp(10), stroke);
            stroke.setStyle(Paint.Style.FILL);

            float cx = card.centerX();
            // Reserve the upper ~58% exclusively for the sign.  Its centre is exact,
            // and its maximum size is constrained by BOTH dimensions of the card.
            float iconCy = card.top + card.height() * .34f;
            float iconSize = Math.min(dp(70), Math.min(card.width() * .62f, card.height() * .43f));
            drawCurveSymbol(c, cx, iconCy, q, iconSize);

            float labelSize = land ? 11f : 12f;
            float distSize = land ? 16f : 18f;
            float dirSize = land ? 8.5f : 9.5f;
            txt(c, tr(q.label == null ? "CURVA" : q.label), cx,
                    card.top + card.height() * .70f, labelSize,
                    q.type == 5 ? 0xFF55D6FF : (q.type >= 3 ? 0xFFFFD400 : (q.type == 1 ? 0xFF35D66F : 0xFFFFD400)), Paint.Align.CENTER);
            txt(c, fmtDist(q.distance), cx,
                    card.top + card.height() * .83f, distSize, yellow, Paint.Align.CENTER);
            txt(c, tr(q.dir < 0 ? "SINISTRA" : "DESTRA"), cx,
                    card.top + card.height() * .95f, dirSize, 0xFFE0E5EA, Paint.Align.CENTER);
        }

        void drawNavigationManeuverGlyph(Canvas c, float cx, float cy, float width, float height, Location l) {
            if (!navigationActive || navigationSteps.isEmpty()) return;
            RouteStep st;
            synchronized(navigationLock) {
                if (navigationStepIndex < 0 || navigationStepIndex >= navigationSteps.size()) return;
                st = navigationSteps.get(navigationStepIndex);
            }
            String type = st.maneuverType == null ? "" : st.maneuverType.toLowerCase(Locale.ROOT);

            // Professional rider-glance pictograms.  These are FILLED vector-like paths,
            // not text glyphs and not compressed route polylines.  Left/right are exact
            // geometric mirrors so the arrowhead can never point in the wrong direction.
            float s = Math.min(width, height) * .90f;
            float outline = Math.max(dp(3), s * .035f);
            int fillColor = 0xFFFFD400; // high-visibility yellow; black outline for contrast

            boolean roundabout = "roundabout".equals(type) || "roundabout_exit".equals(type) || "rotary".equals(type);
            if (roundabout) {
                drawRoundaboutPictogram(c, cx, cy, s, st.roundaboutExit, fillColor, outline);
                return;
            }

            boolean left = type.contains("left");
            boolean right = type.contains("right");
            boolean straight = !left && !right;
            Path shape = new Path();

            if (straight) {
                // Large, centered upward arrow: broad shaft + symmetrical triangular head.
                float shaftW = s * .22f;
                float headW = s * .48f;
                float bottom = cy + s * .34f;
                float shoulder = cy - s * .03f;
                float tip = cy - s * .40f;
                shape.moveTo(cx - shaftW / 2f, bottom);
                shape.lineTo(cx + shaftW / 2f, bottom);
                shape.lineTo(cx + shaftW / 2f, shoulder);
                shape.lineTo(cx + headW / 2f, shoulder);
                shape.lineTo(cx, tip);
                shape.lineTo(cx - headW / 2f, shoulder);
                shape.lineTo(cx - shaftW / 2f, shoulder);
                shape.close();
            } else {
                // Reference geometry: vertical stem rising from below, a clean 90-degree
                // elbow, then a horizontal shaft ending in a large directional head.
                // Build RIGHT once; LEFT is its exact mirror around cx.
                float stemHalf = s * .105f;
                float elbowY = cy + s * .01f;
                float shaftTop = cy - s * .105f;
                float shaftBottom = cy + s * .035f;
                float headBaseX = cx + s * .20f;
                float tipX = cx + s * .46f;
                float headTopY = cy - s * .25f;
                float headBottomY = cy + s * .18f;

                shape.moveTo(cx - stemHalf, cy + s * .34f);
                shape.lineTo(cx + stemHalf, cy + s * .34f);
                shape.lineTo(cx + stemHalf, elbowY);
                shape.lineTo(headBaseX, elbowY);
                shape.lineTo(headBaseX, headTopY);
                shape.lineTo(tipX, cy - s * .035f);
                shape.lineTo(headBaseX, headBottomY);
                shape.lineTo(headBaseX, shaftBottom);
                shape.lineTo(cx - stemHalf, shaftBottom);
                shape.close();

                if (left) {
                    Matrix mirror = new Matrix();
                    mirror.setScale(-1f, 1f, cx, cy);
                    shape.transform(mirror);
                }
            }

            // Thick black keyline first, then the high-visibility fill.
            p.setStyle(Paint.Style.FILL);
            p.setColor(fillColor);
            stroke.setStyle(Paint.Style.FILL_AND_STROKE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeWidth(outline);
            stroke.setColor(fillColor);
            c.drawPath(shape, stroke);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(outline);
            stroke.setColor(Color.BLACK);
            c.drawPath(shape, stroke);
            stroke.setStyle(Paint.Style.FILL);
        }

        void drawRoundaboutPictogram(Canvas c, float cx, float cy, float s, int exit, int fillColor, float outline) {
            // True circular roundabout symbol with three directional arrowheads.
            float r = s * .30f;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.SQUARE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(s * .115f + outline * 1.8f);
            stroke.setColor(Color.BLACK);
            c.drawCircle(cx, cy, r, stroke);
            stroke.setStrokeWidth(s * .115f);
            stroke.setColor(fillColor);
            c.drawCircle(cx, cy, r, stroke);

            // Three arrowheads following the circular flow.
            float ah = s * .105f;
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(-55 + i * 120);
                float ax = cx + (float)Math.cos(a) * r;
                float ay = cy + (float)Math.sin(a) * r;
                double tangent = a + Math.PI / 2.0;
                float tx = (float)Math.cos(tangent);
                float ty = (float)Math.sin(tangent);
                float nx = -ty, ny = tx;
                Path head = new Path();
                head.moveTo(ax + tx * ah * .85f, ay + ty * ah * .85f);
                head.lineTo(ax - tx * ah * .55f + nx * ah * .62f, ay - ty * ah * .55f + ny * ah * .62f);
                head.lineTo(ax - tx * ah * .55f - nx * ah * .62f, ay - ty * ah * .55f - ny * ah * .62f);
                head.close();
                stroke.setStyle(Paint.Style.FILL_AND_STROKE);
                stroke.setStrokeWidth(outline);
                stroke.setColor(Color.BLACK);
                c.drawPath(head, stroke);
                stroke.setStyle(Paint.Style.FILL);
                stroke.setColor(fillColor);
                c.drawPath(head, stroke);
            }

            // Never invent an exit number.  Unknown remains visibly unknown rather than
            // pretending that the route engine supplied a value.
            String n = exit > 0 ? Integer.toString(exit) : "";
            if (!n.isEmpty()) {
                txt(c, n, cx, cy + s * .12f, Math.max(24f, Math.min(42f, s / density * .30f)), Color.WHITE, Paint.Align.CENTER);
            }
        }

        void drawNavigationGuidance(Canvas c,float cx,float cy,boolean land,float availableW,float availableH){
            Location l=engine.loc;
            if(navigationLoading){txt(c,"NAVIGAZIONE…",cx,cy+dp(8),land?22:25,yellow,Paint.Align.CENTER);return;}
            if(!navigationActive){txt(c,"NAV",cx,cy+dp(8),land?22:25,0xFF6B737B,Paint.Align.CENTER);return;}
            if(l!=null) navigationDistance=formatNavDistance(l);
            String ins=navigationInstruction==null?"PROSEGUI":navigationInstruction, d=navigationDistance==null?"":navigationDistance, road="";
            synchronized(navigationLock){if(!navigationSteps.isEmpty()){int idx=Math.min(navigationStepIndex,navigationSteps.size()-1);road=navigationSteps.get(idx).roadName;}}
            float aw=Math.max(dp(120),availableW),ah=Math.max(dp(90),availableH),top=cy-ah/2f,arrowCy=top+ah*.20f;
            synchronized(navigationLock){drawNavigationManeuverGlyph(c,cx,arrowCy,Math.min(aw,dp(150)),Math.min(ah,dp(90)),l);}
            float maxW=aw-dp(12);String line1=ins.toUpperCase(Locale.ROOT),line2=road==null?"":road.trim();p.setTypeface(TypefaceCompat.bold());float size=land?16f:18f;
            while(size>8f){p.setTextSize(size*density);if(p.measureText(line1)<=maxW)break;size-=.5f;}
            if(p.measureText(line1)>maxW){int cut=line1.lastIndexOf(' ',Math.min(line1.length()-1,Math.max(1,line1.length()/2+3)));if(cut>0){String second=line1.substring(cut+1);line1=line1.substring(0,cut);if(line2.isEmpty())line2=second;}}
            txt(c,line1,cx,top+ah*.43f,size,Color.WHITE,Paint.Align.CENTER);
            if(!line2.isEmpty()){float roadSize=Math.max(8f,Math.min(size-1f,land?14f:15f));while(roadSize>8f){p.setTextSize(roadSize*density);if(p.measureText(line2)<=maxW)break;roadSize-=.5f;}txt(c,line2,cx,top+ah*.58f,roadSize,0xFFDCE3E8,Paint.Align.CENTER);}
            txt(c,d,cx,top+ah*.74f,land?22:25,yellow,Paint.Align.CENTER);
            if(!navigationDestinationDistance.isEmpty())txt(c,"DESTINAZIONE  "+navigationDestinationDistance,cx,top+ah*.91f,land?14:15,0xFF65D7FF,Paint.Align.CENTER);
            if(navigationEtaEpochMs>0L&&navigationEtaRemainingSec>0L){float etaY=top+ah*.99f,etaFs=land?11f:12f;String eta="ARRIVO "+formatEtaTime(navigationEtaEpochMs)+"  ·  "+formatEtaRemaining(navigationEtaRemainingSec);txt(c,eta,cx,Math.min(top+ah-dp(2),etaY),etaFs,0xFFFFD66B,Paint.Align.CENTER);}
        }

        void drawExpandedMap(Canvas c) {
            int w = getWidth(), h = getHeight();
            boolean land = w > h;
            boolean vector = vectorMapReady && vectorMapView != null && vectorMapView.getVisibility() == View.VISIBLE;
            if (!vector) c.drawColor(0xFF05080B);
            RectF full = new RectF(0, 0, w, h);
            mapBounds.set(full);
            if (!vector) drawRoadMap(c, full, engine.loc, true);

            // Map toolbar: landscape keeps one compact row. Portrait uses two rows
            // (3 + 2 buttons), with generous button widths so labels remain readable.
            // The toolbar stays at the upper-left, leaving the map centre unobstructed.
            float gap = Math.max(dp(5), Math.min(dp(9), w * .012f));
            float side = Math.max(dp(8), Math.min(dp(14), w * .018f));
            float top = Math.max(dp(8), Math.min(dp(12), h * .012f));
            float barH = land ? Math.max(dp(42), Math.min(dp(54), h * .055f))
                              : Math.max(dp(42), Math.min(dp(50), h * .050f));
            if (!land) {
                // Portrait: keep SEARCH on its own full-width row. On narrow phones the
                // previous layout placed SEARCH on top of the third action button, which
                // made the search control partially or completely invisible.
                float searchW = Math.min(dp(360), w - 2f * side);
                float searchLeft = (w - searchW) * .5f;
                mapSearchBounds.set(searchLeft, top, searchLeft + searchW, top + barH);

                float row1Top = top + barH + gap;
                float buttonW = Math.min(dp(190), (w - 2f * side - 2f * gap) / 3f);
                float groupW = 3f * buttonW + 2f * gap;
                float left = (w - groupW) * .5f;
                // All three first-row actions intentionally use the exact same bounds
                // dimensions, including DESTINAZIONE QUI.
                mapRecenterBounds.set(left, row1Top, left + buttonW, row1Top + barH);
                mapSetDestBounds.set(left + buttonW + gap, row1Top, left + 2f * buttonW + gap, row1Top + barH);
                mapSetHomeBounds.set(left + 2f * (buttonW + gap), row1Top, left + 3f * buttonW + 2f * gap, row1Top + barH);
                float row2Top = row1Top + barH + gap;
                float row2W = Math.min(dp(190), (groupW - gap) / 2f);
                float row2Left = (w - (2f * row2W + gap)) * .5f;
                mapClearPointBounds.set(row2Left, row2Top, row2Left + row2W, row2Top + barH);
                mapCloseBounds.set(row2Left + row2W + gap, row2Top, row2Left + 2f * row2W + gap, row2Top + barH);
            } else {
                // Landscape: reserve a dedicated lane for SEARCH first. The five map
                // actions must fit entirely in the remaining width; previously their
                // centred group extended underneath the search button, hiding the last
                // action on wide landscape displays.
                float searchW=Math.min(dp(210),w*.28f);
                float searchLeft=w-searchW-side;
                float available=w-2f*side-gap-searchW;
                float buttonW=Math.max(dp(52),Math.min(dp(230),(available-4f*gap)/5f));
                float groupW=5f*buttonW+4f*gap;
                float left=side;
                if(groupW>available){ buttonW=Math.max(dp(42),(available-4f*gap)/5f); groupW=5f*buttonW+4f*gap; }
                mapRecenterBounds.set(left,top,left+buttonW,top+barH);
                mapSetDestBounds.set(left+buttonW+gap,top,left+2f*buttonW+gap,top+barH);
                mapSetHomeBounds.set(left+2f*(buttonW+gap),top,left+3f*buttonW+2f*gap,top+barH);
                mapClearPointBounds.set(left+3f*(buttonW+gap),top,left+4f*buttonW+3f*gap,top+barH);
                mapCloseBounds.set(left+4f*(buttonW+gap),top,left+5f*buttonW+4f*gap,top+barH);
                mapSearchBounds.set(searchLeft,top,w-side,top+barH);
            }
            drawMapToolbarButton(c,mapSearchBounds,"CERCA DESTINAZIONE",yellow);
            drawMapToolbarButton(c, mapRecenterBounds, tr("RICENTRA GPS"), yellow);
            drawMapToolbarButton(c, mapSetDestBounds, tr("DESTINAZIONE QUI"), yellow);
            drawMapToolbarButton(c, mapSetHomeBounds, tr("SALVA CASA QUI"), yellow);
            drawMapToolbarButton(c, mapClearPointBounds, tr("ELIMINA PUNTO"), Color.WHITE);
            drawMapToolbarButton(c, mapCloseBounds, tr("CHIUDI ✕"), Color.WHITE);
        }

        void drawMapToolbarButton(Canvas c, RectF r, String label, int color) {
            panel(c, r, 0xFF202A33, dp(7));
            stroke.setColor(0xFFE4E8EC); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1));
            c.drawRoundRect(r, dp(7), dp(7), stroke); stroke.setStyle(Paint.Style.FILL);
            float fs = Math.min(11f, Math.max(8.5f, (r.width() / density) * .052f));
            p.setTypeface(TypefaceCompat.bold()); p.setTextSize(fs * density);
            while (p.measureText(label) > r.width() - dp(10) && fs > 7.5f) { fs -= .5f; p.setTextSize(fs * density); }
            txt(c, label, r.centerX(), r.centerY() + dp(4), fs, color, Paint.Align.CENTER);
        }

        void drawSpeedLimitBadge(Canvas c, float cx, float cy, float radius, int speedLimit) {
            float r = Math.max(dp(20), radius);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, r, p);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(dp(3.5f), r * .105f));
            stroke.setColor(Color.RED);
            c.drawCircle(cx, cy, r, stroke);
            String lim = Integer.toString(speedLimit);
            // fs is kept in px while measuring; txt() expects dp. The previous code
            // multiplied an already-pixel-sized value by density a second time, which
            // made the number spill outside the white disc on high-density displays.
            float fsPx = Math.min(dp(34), r * .72f);
            p.setTypeface(TypefaceCompat.bold());
            p.setTextSize(fsPx);
            while (fsPx > dp(17) && p.measureText(lim) > r * 1.42f) {
                fsPx -= dp(1);
                p.setTextSize(fsPx);
            }
            float fsDp = fsPx / density;
            txt(c, lim, cx, cy + fsPx * .32f, fsDp, Color.BLACK, Paint.Align.CENTER);
            float unitFs = Math.max(7.5f, Math.min(9.5f, r / density * .20f));
            txt(c, "km/h", cx, cy + r * .68f, unitFs, Color.BLACK, Paint.Align.CENTER);
            stroke.setStyle(Paint.Style.FILL);
        }

        float speed(Location l) { return l.hasSpeed() ? l.getSpeed() * 3.6f : 0f; }
        String fmtDist(float d) { return d < 1000 ? Math.round(d) + " m" : String.format(Locale.US, "%.1f km", d / 1000f); }

        void drawSpeed(Canvas c, float cx, float baseY, float s, float rec, int speedLimit, boolean land) {
            int value=Math.max(0,Math.min(999,Math.round(s)));
            String digits=String.format(Locale.US,"%03d",value);
            float w=getWidth(),h=getHeight();
            // Size the tachometer from the actual screen dimensions, with separate portrait/landscape caps.
            // Taller, more readable race-style tachometer. Keep the three digits
            // separated while using the available display area efficiently.
            // On very compact portrait displays keep a little more vertical reserve for
            // navigation/ETA.  The S22/Note-class displays remain unchanged.
            float portraitDp = h / Math.max(1f, density);
            float portraitTarget = portraitDp < 700f ? .19f : (portraitDp < 850f ? .30f : .37f);
            float targetHeight=land ? Math.min(520f, h*.58f) : Math.min(760f, h*portraitTarget);
            float digitWidthFactor=3f*.42f+2f*.10f;
            // Leave a dedicated lane for the legal-limit road sign in portrait.
            // The rider must be able to read the limit without deciphering a tiny text row.
            float widthLimit=(w*(land?.50f:.78f)-dp(24))/digitWidthFactor;
            float topLimit=land?h*.10f:h*.29f, bottomLimit=land?h*.82f:h*.77f;
            float heightLimit=Math.max(90f,Math.min((baseY-topLimit)/.78f,(bottomLimit-baseY)/.12f));
            float numberHeight=Math.max(dp(90),Math.min(targetHeight,Math.min(widthLimit,heightLimit)));
            android.content.SharedPreferences prefs=getSharedPreferences("road_display",MODE_PRIVATE);
            int yellowAt=prefs.getInt("speed_yellow",90),redAt=prefs.getInt("speed_red",120);
            boolean overLimit=speedLimit>0&&s>speedLimit;
            int normalFill=overLimit?Color.RED:(s>=redAt?Color.RED:(s>=yellowAt?0xFFFFD400:Color.WHITE));
            int border=(s<=50f&&!overLimit)?0xFF00FF55:(overLimit?Color.WHITE:(s>=redAt?Color.WHITE:(s>=yellowAt?0xFFFF8A00:0xFF101010)));
            float digitWidth=numberHeight*.42f,gap=numberHeight*.10f,total=3f*digitWidth+2f*gap,left=cx-total/2f;
            boolean leading=true;
            for(int i=0;i<3;i++){
                char ch=digits.charAt(i);
                boolean gray=leading&&ch=='0';
                if(ch!='0') leading=false;
                int fill=gray?0xFF454A50:normalFill;
                drawSevenDigit(c,left+i*(digitWidth+gap)+digitWidth/2f,baseY,numberHeight,ch,fill,border);
            }
            // Dedicated lower information row: RECOMMENDED on the left, the REAL
            // speed limit on the right.  Never hide the legal limit inside the large
            // tachometer: if it exists, it must be immediately visible at a glance.
            float dailyY=land?h*.93f:h*.84f;
            float infoY=dailyY-(land?dp(52):dp(22));
            float leftCx=left;
            float rightCx=left+total;
            float smallFs=Math.min(12f,Math.max(8f,numberHeight*.06f));
            float valueFs=Math.min(18f,Math.max(11f,numberHeight*.085f));
            if(rec>0){
                txt(c,tr("CONSIGLIATA"),leftCx,infoY-dp(1),smallFs,yellow,Paint.Align.LEFT);
                txt(c,Math.round(rec)+" km/h",leftCx,infoY+dp(19),valueFs,yellow,Paint.Align.LEFT);
            }
            // Speed-limit badge is rendered by drawRide in a dedicated lane between
            // navigation and the tachometer. This keeps the legal limit independent from
            // the large speed instrument and prevents overlap with navigation text.
            speedBounds.set(cx-total/2f-dp(14),baseY-numberHeight*.88f,cx+total/2f+dp(14),baseY+numberHeight*.72f);
        }

        void drawSevenDigit(Canvas c,float cx,float baseline,float h,char digit,int fill,int border){
            int mask; switch(digit){case '0':mask=0x3F;break;case '1':mask=0x06;break;case '2':mask=0x5B;break;case '3':mask=0x4F;break;case '4':mask=0x66;break;case '5':mask=0x6D;break;case '6':mask=0x7D;break;case '7':mask=0x07;break;case '8':mask=0x7F;break;case '9':mask=0x6F;break;default:mask=0;break;}
            float w=h*.50f, t=Math.max(dp(4.5f),h*.095f), x=cx-w/2f, top=baseline-h*.78f, bottom=baseline+h*.12f, mid=(top+bottom)/2f, inset=t*.8f;
            // Keep a small, uniform physical gap between horizontal and vertical segments.
            // The gap is measured from the outer edge of the stroke, so segments never touch
            // even when the display is rendered at large sizes.
            float gap=Math.max(dp(3f),t*.32f);
            float endGap=t*.50f+gap;
            float[][] seg={
                    {x+inset+endGap,top,x+w-inset-endGap,top},
                    {x+w-inset,top+endGap,x+w-inset,mid-endGap},
                    {x+w-inset,mid+endGap,x+w-inset,bottom-endGap},
                    {x+inset+endGap,bottom,x+w-inset-endGap,bottom},
                    {x+inset,mid+endGap,x+inset,bottom-endGap},
                    {x+inset,top+endGap,x+inset,mid-endGap},
                    {x+inset+endGap,mid,x+w-inset-endGap,mid}
            };
            for(int i=0;i<7;i++)if((mask&(1<<i))!=0){
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeCap(Paint.Cap.ROUND);
                stroke.setStrokeWidth(t+dp(2));
                stroke.setColor(border);
                c.drawLine(seg[i][0],seg[i][1],seg[i][2],seg[i][3],stroke);
                stroke.setStrokeWidth(t);
                stroke.setColor(fill);
                c.drawLine(seg[i][0],seg[i][1],seg[i][2],seg[i][3],stroke);
            }
        }

        void txtOutlined(Canvas c,String text,float x,float y,float size,int fill,int outline,float ow){p.setTypeface(TypefaceCompat.bold());p.setTextSize(size*density);p.setTextAlign(Paint.Align.CENTER);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(ow);p.setColor(outline);c.drawText(text,x,y,p);p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawText(text,x,y,p);}

        void drawAltitude(Canvas c, float cx, float y, Location location, boolean land) {
            String altitude = (location != null && location.hasAltitude() &&
                    (location.getProvider() == null || (!"ip-approx".equals(location.getProvider()) && !"saved-network".equals(location.getProvider()))))
                    ? "ALT " + Math.round(location.getAltitude()) + " m s.l.m."
                    : "ALT -- m s.l.m.";
            float size = land ? 14f : 13f;
            // Yellow on black, bold typeface: readable in strong outdoor light.
            txt(c, altitude, cx, y, size, 0xFFFFD400, Paint.Align.CENTER);
        }

        void drawCenteredCurveGlyph(Canvas c, float cx, float cy, float sz, float sign, int color, int type) {
            // Each severity has its own geometry. Mirroring only the X coordinate keeps
            // left/right perfectly symmetric while making LIGHT/MEDIUM/TIGHT/HAIRPIN
            // visually distinct at a glance.
            Path inner = new Path();
            float ex, ey;
            if (type == 4) {
                float x1=sz*.30f, x2=sz*.34f, y0=sz*.28f, y1=sz*.02f, y2=-sz*.24f;
                inner.moveTo(cx-sign*x1, cy+y0);
                inner.cubicTo(cx-sign*sz*.05f, cy+y0, cx+sign*x2, cy+y0, cx+sign*x2, cy+y1);
                inner.cubicTo(cx+sign*x2, cy+y2, cx-sign*sz*.08f, cy-y2*.25f, cx-sign*sz*.16f, cy-y2);
                ex=cx-sign*sz*.16f; ey=cy-y2;
            } else if (type == 3) {
                float x0=sz*.30f;
                inner.moveTo(cx-sign*x0, cy+sz*.28f);
                inner.cubicTo(cx-sign*sz*.02f, cy+sz*.28f, cx+sign*sz*.34f, cy+sz*.14f, cx+sign*sz*.30f, cy-sz*.02f);
                inner.cubicTo(cx+sign*sz*.26f, cy-sz*.20f, cx+sign*sz*.08f, cy-sz*.30f, cx-sign*sz*.04f, cy-sz*.36f);
                ex=cx-sign*sz*.04f; ey=cy-sz*.36f;
            } else if (type == 2) {
                float x0=sz*.30f;
                inner.moveTo(cx-sign*x0, cy+sz*.28f);
                inner.cubicTo(cx-sign*sz*.06f, cy+sz*.28f, cx+sign*sz*.30f, cy+sz*.12f, cx+sign*sz*.30f, cy-sz*.05f);
                inner.cubicTo(cx+sign*sz*.30f, cy-sz*.20f, cx+sign*sz*.14f, cy-sz*.30f, cx+sign*sz*.02f, cy-sz*.36f);
                ex=cx+sign*sz*.02f; ey=cy-sz*.36f;
            } else {
                float x0=sz*.24f;
                inner.moveTo(cx-sign*x0, cy+sz*.25f);
                inner.cubicTo(cx-sign*sz*.08f, cy+sz*.25f, cx+sign*sz*.27f, cy+sz*.13f, cx+sign*sz*.27f, cy-sz*.04f);
                inner.cubicTo(cx+sign*sz*.27f, cy-sz*.20f, cx+sign*sz*.10f, cy-sz*.30f, cx+sign*sz*.02f, cy-sz*.35f);
                ex=cx+sign*sz*.02f; ey=cy-sz*.35f;
            }
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(Color.BLACK); stroke.setStrokeWidth(Math.max(dp(4),sz*.105f)); c.drawPath(inner,stroke);
            stroke.setColor(color); stroke.setStrokeWidth(Math.max(dp(2.5f),sz*.060f)); c.drawPath(inner,stroke);
            Path arrow=new Path();
            arrow.moveTo(ex-sign*sz*.10f,ey+sz*.015f); arrow.lineTo(ex,ey-sz*.075f); arrow.lineTo(ex+sign*sz*.10f,ey+sz*.015f);
            stroke.setColor(Color.BLACK); stroke.setStrokeWidth(Math.max(dp(3.5f),sz*.075f)); c.drawPath(arrow,stroke);
            stroke.setColor(color); stroke.setStrokeWidth(Math.max(dp(2),sz*.045f)); c.drawPath(arrow,stroke);
            stroke.setStyle(Paint.Style.FILL);
        }

        void drawCurveSymbol(Canvas c, float cx, float cy, Curve q, float sz) {
            if (q == null || !q.valid) return;
            boolean left = q.dir < 0;
            int curveColor = q.type == 5 ? 0xFF55D6FF : (q.type == 1 ? 0xFF35D66F : q.type == 2 ? 0xFFFFD400 : 0xFFF04438);
            float sign = left ? 1f : -1f;

            if (q.type == 5) {
                float half = sz * .72f;
                RectF square = new RectF(cx-half, cy-half, cx+half, cy+half);
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF07131A); c.drawRoundRect(square, dp(7), dp(7), p);
                stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeJoin(Paint.Join.ROUND);
                stroke.setStrokeWidth(Math.max(dp(3), sz*.075f)); stroke.setColor(curveColor);
                c.drawRoundRect(square, dp(7), dp(7), stroke);
                float ringR = sz*.31f;
                stroke.setStrokeWidth(Math.max(dp(3), sz*.09f)); stroke.setStrokeCap(Paint.Cap.ROUND);
                c.drawCircle(cx,cy,ringR,stroke);
                // Gap the ring on the exit side and draw a centred directional arrow.
                float ax = cx + sign*ringR*.98f, ay = cy;
                Path arrow = new Path();
                arrow.moveTo(ax-sign*sz*.14f, ay-sz*.12f);
                arrow.lineTo(ax, ay);
                arrow.lineTo(ax-sign*sz*.14f, ay+sz*.12f);
                stroke.setStrokeWidth(Math.max(dp(3), sz*.075f)); c.drawPath(arrow,stroke);
                stroke.setStyle(Paint.Style.FILL);
            } else if (q.type == 1) {
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF07130B);
                c.drawCircle(cx, cy, sz * .70f, p);
                stroke.setStyle(Paint.Style.STROKE); stroke.setColor(curveColor);
                stroke.setStrokeWidth(Math.max(dp(3), sz * .075f));
                c.drawCircle(cx, cy, sz * .70f, stroke);
                drawCenteredCurveGlyph(c, cx, cy, sz, sign, curveColor, q.type);
            } else if (q.type == 2) {
                float half = sz * .70f;
                RectF square = new RectF(cx - half, cy - half, cx + half, cy + half);
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF171300); c.drawRoundRect(square, dp(5), dp(5), p);
                stroke.setStyle(Paint.Style.STROKE); stroke.setColor(curveColor);
                stroke.setStrokeWidth(Math.max(dp(3), sz * .075f));
                c.drawRoundRect(square, dp(5), dp(5), stroke);
                drawCenteredCurveGlyph(c, cx, cy, sz, sign, curveColor, q.type);
            } else {
                // Equilateral warning triangle: all three vertices are generated from
                // the same centre and radius, so the warning symbol can never drift sideways.
                float radius = sz * .78f;
                Path triangle = new Path();
                triangle.moveTo(cx, cy - radius);
                triangle.lineTo(cx - radius * .8660254f, cy + radius * .5f);
                triangle.lineTo(cx + radius * .8660254f, cy + radius * .5f);
                triangle.close();
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF050608); c.drawPath(triangle, p);
                stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeJoin(Paint.Join.ROUND);
                stroke.setColor(yellow); stroke.setStrokeWidth(Math.max(dp(3), sz * .085f));
                c.drawPath(triangle, stroke);

                // The directional glyph is severity-specific and remains centred
                // inside the warning triangle.
                drawCenteredCurveGlyph(c, cx, cy, sz * .72f, sign, curveColor, q.type);

                // The exclamation marks are mirrored around the same centre line.
                float exclamationY = cy + sz * .28f;
                float exclamationSize = Math.max(26, sz / density * .40f);
                txt(c, "!", cx - sz * .43f, exclamationY, exclamationSize, Color.WHITE, Paint.Align.CENTER);
                txt(c, "!", cx + sz * .43f, exclamationY, exclamationSize, Color.WHITE, Paint.Align.CENTER);
            }
        }

        void drawCurve(Canvas c, float cx, float cy, Curve q, float sz) {
            if (q == null || !q.valid) {
                txt(c, "—", cx, cy + dp(12), Math.max(32, sz / density * .62f), 0xFF6B737B, Paint.Align.CENTER);
                txt(c, tr("NESSUNA CURVA RILEVATA"), cx, cy + sz * .82f,
                        Math.max(11, Math.min(15, sz / density * .13f)), 0xFF9AA3AD, Paint.Align.CENTER);
                return;
            }
            drawCurveSymbol(c, cx, cy, q, sz);
            int curveColor = q.type == 5 ? 0xFF55D6FF : q.type == 1 ? 0xFF35D66F : q.type == 2 ? 0xFFFFD400 : 0xFFF04438;
            txt(c, tr(q.label == null ? "CURVA" : q.label), cx, cy + sz * 1.02f,
                    Math.max(13, Math.min(21, sz / density * .18f)), curveColor, Paint.Align.CENTER);
            txt(c, fmtDist(q.distance), cx, cy + sz * 1.24f,
                    Math.max(18, Math.min(29, sz / density * .23f)), yellow, Paint.Align.CENTER);
            txt(c, tr(q.dir < 0 ? "SINISTRA" : "DESTRA"), cx, cy + sz * 1.43f,
                    Math.max(10, Math.min(15, sz / density * .12f)), 0xFFE0E5EA, Paint.Align.CENTER);
        }

        RectF boundsForZoom(boolean expanded, boolean plus) {
            float zbtn = dp(expanded ? 48 : 36);
            if (plus) return new RectF(mapBounds.right-zbtn-dp(6), mapBounds.bottom-zbtn*2-dp(10), mapBounds.right-dp(6), mapBounds.bottom-zbtn-dp(10));
            return new RectF(mapBounds.right-zbtn-dp(6), mapBounds.bottom-zbtn-dp(6), mapBounds.right-dp(6), mapBounds.bottom-dp(6));
        }
        boolean mapZoomButton(RectF r,float x,float y){return r.contains(x,y);}

        void showSensorCalibration() {
            final LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding((int)dp(20),(int)dp(8),(int)dp(20),0);
            TextView info = new TextView(MainActivity.this);
            info.setText("Fissa il telefono definitivamente al supporto. Metti la moto dritta, ferma e senza muovere il manubrio. La calibrazione memorizza l'orientamento reale del telefono rispetto alla moto.\n\nDurante la marcia i sensori vengono fusi con il GPS per registrare i movimenti. Questi dati non modificano le decisioni di sicurezza del navigatore.");
            info.setTextSize(15f); info.setTextColor(Color.WHITE); box.addView(info);
            TextView status = new TextView(MainActivity.this); status.setTextSize(16f); status.setTextColor(yellow);
            status.setPadding(0,(int)dp(14),(int)dp(0),(int)dp(8));
            status.setText(sensorFusion != null && sensorFusion.hasCalibration() ? "CALIBRAZIONE PRESENTE" : "NESSUNA CALIBRAZIONE"); box.addView(status);
            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).setTitle("CALIBRAZIONE SENSORI").setView(box).setNegativeButton("CHIUDI",null).setPositiveButton("CALIBRA ORA",null).create();
            dialog.setOnShowListener(d -> {
                android.widget.Button b=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(v -> {
                    if(sensorFusion==null || !sensorFusion.isStationaryEnough()) { status.setText("FERMA E TIENI LA MOTO DRITTA"); return; }
                    b.setEnabled(false); status.setText("CALIBRAZIONE TRA 3…");
                    new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if(sensorFusion != null && sensorFusion.calibrateNow()) status.setText("CALIBRAZIONE COMPLETATA ✓");
                        else status.setText("CALIBRAZIONE NON RIUSCITA — RIPROVA DA FERMO");
                        b.setEnabled(true);
                    },3000L);
                });
            });
            dialog.show();
        }

        void showDisplaySettings() {
            android.content.SharedPreferences prefs = getSharedPreferences("road_display", MODE_PRIVATE);
            final String[] modes = {"AUTO — sensore luminosità", "SCURO — sempre", "NEGATIVO — sempre"};
            android.widget.RadioGroup group = new android.widget.RadioGroup(MainActivity.this);
            group.setOrientation(android.widget.RadioGroup.VERTICAL);
            group.setPadding((int)dp(18), (int)dp(8), (int)dp(18), (int)dp(8));
            final android.widget.RadioButton[] radios = new android.widget.RadioButton[modes.length];
            for (int i = 0; i < modes.length; i++) {
                radios[i] = new android.widget.RadioButton(MainActivity.this);
                radios[i].setText(modes[i]);
                radios[i].setId(100 + i);
                group.addView(radios[i], new LinearLayout.LayoutParams(-1, (int)dp(48)));
            }
            group.check(100 + Math.max(0, Math.min(2, displayThemeMode)));
            String luxText = Float.isNaN(currentAmbientLux) ? "Lettura sensore: non disponibile" : "Lettura sensore: " + Math.round(currentAmbientLux) + " lux";
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("IMPOSTAZIONI DISPLAY")
                .setMessage("Tema di visualizzazione\nAUTO: negativo sopra 12.500 lux, ritorno al tema scuro sotto 6.000 lux. Soglie dimezzate su richiesta. La luminosità automatica dello schermo resta separata.\n\n" + luxText)
                .setView(group)
                .setNegativeButton("ANNULLA", null)
                .setPositiveButton("SALVA", (d, which) -> {
                    int checked = group.getCheckedRadioButtonId() - 100;
                    if (checked < 0 || checked > 2) checked = 0;
                    displayThemeMode = checked;
                    prefs.edit().putInt("display_theme_mode", checked).apply();
                    if (checked == 2) negativeTheme = true;
                    else if (checked == 1) negativeTheme = false;
                    else if (!Float.isNaN(currentAmbientLux)) negativeTheme = currentAmbientLux >= SUNLIGHT_ENTER_LUX;
                    if (!activityDestroyed && view != null) view.invalidate();
                }).show();
        }

        void showSpeedSettings() {
            android.content.SharedPreferences prefs=getSharedPreferences("road_display",MODE_PRIVATE);
            final android.widget.LinearLayout box=new android.widget.LinearLayout(MainActivity.this); box.setOrientation(android.widget.LinearLayout.VERTICAL); box.setPadding((int)dp(20),(int)dp(8),(int)dp(20),0);
            final android.widget.EditText e1=new android.widget.EditText(MainActivity.this); e1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e1.setHint("Giallo da km/h"); e1.setText(Integer.toString(prefs.getInt("speed_yellow",90)));
            final android.widget.EditText e2=new android.widget.EditText(MainActivity.this); e2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e2.setHint("Rosso da km/h"); e2.setText(Integer.toString(prefs.getInt("speed_red",120)));
            box.addView(e1); box.addView(e2);
            android.widget.Button calib = new android.widget.Button(MainActivity.this); calib.setText("CALIBRAZIONE SENSORI"); calib.setAllCaps(false); calib.setOnClickListener(v -> showSensorCalibration()); box.addView(calib, new LinearLayout.LayoutParams(-1,(int)dp(48)));
            new android.app.AlertDialog.Builder(MainActivity.this).setTitle("COLORI VELOCITÀ").setMessage("Imposta le soglie del display. Il limite stradale, quando disponibile, ha sempre priorità.").setView(box).setNegativeButton("ANNULLA",null).setPositiveButton("SALVA",(d,w)->{try{int y=Integer.parseInt(e1.getText().toString());int r=Integer.parseInt(e2.getText().toString());if(y<1)y=90;if(r<=y)r=y+1;if(r>250)r=250;prefs.edit().putInt("speed_yellow",y).putInt("speed_red",r).apply();invalidate();}catch(Exception ignored){}}).show();
        }

        void drawRadarAlert(Canvas c,float cx,float cy,boolean land){
            long left=radarAlertUntil-System.currentTimeMillis(); if(left<=0) return;
            boolean flash=((System.currentTimeMillis()/500L)&1L)==0L;
            if (flash) {
                float sz=land?dp(54):dp(50); Path t=new Path();t.moveTo(cx,cy-sz*.5f);t.lineTo(cx-sz*.5f,cy+sz*.4f);t.lineTo(cx+sz*.5f,cy+sz*.4f);t.close();p.setStyle(Paint.Style.FILL);p.setColor(Color.RED);c.drawPath(t,p);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2));stroke.setColor(Color.WHITE);c.drawPath(t,stroke);txt(c,radarAlertMessage,cx,cy+dp(6),land?10:9,Color.WHITE,Paint.Align.CENTER);
            }
            postInvalidateDelayed(250);
        }

        double pendingMapLat=Double.NaN, pendingMapLon=Double.NaN;

        int expandedMapZoom(){return Math.max(15,Math.min(19,15+Math.round((mapZoom-.5f)*4f)));}

        float[] mapScreenPoint(double lat,double lon,RectF bounds){if(Double.isNaN(mapViewLat)||Double.isNaN(mapViewLon))return null;double world=256.0*(1<<expandedMapZoom());double s1=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,mapViewLat)))),cx=(mapViewLon+180.0)/360.0*world,cy=(.5-Math.log((1+s1)/(1-s1))/(4*Math.PI))*world;double s2=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,lat)))),px=(lon+180.0)/360.0*world,py=(.5-Math.log((1+s2)/(1-s2))/(4*Math.PI))*world,br=Math.toRadians(mapFrozenBearing),dx=px-cx,dy=py-cy,sx=dx*Math.cos(br)+dy*Math.sin(br),sy=-dx*Math.sin(br)+dy*Math.cos(br);return new float[]{bounds.centerX()+(float)sx,bounds.centerY()+(float)sy};}

        Location mapLocationFromScreen(float x,float y){if(Double.isNaN(mapViewLat)||Double.isNaN(mapViewLon))return engine.loc==null?null:new Location(engine.loc);double world=256.0*(1<<expandedMapZoom());double s=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,mapViewLat)))),cx=(mapViewLon+180.0)/360.0*world,cy=(.5-Math.log((1+s)/(1-s))/(4*Math.PI))*world,sx=x-mapBounds.centerX(),sy=y-mapBounds.centerY(),br=Math.toRadians(mapFrozenBearing),dx=sx*Math.cos(br)-sy*Math.sin(br),dy=sx*Math.sin(br)+sy*Math.cos(br),px=cx+dx,py=cy+dy,lon=px/world*360.0-180.0,n=Math.PI-2.0*Math.PI*py/world;Location l=new Location("map-pick");l.setLatitude(Math.max(-85,Math.min(85,Math.toDegrees(Math.atan(Math.sinh(n))))));l.setLongitude(lon);return l;}

        Location mapCenterLocation(){if(!Double.isNaN(selectedMapLat)&&!Double.isNaN(selectedMapLon)){Location l=new Location("map-pick");l.setLatitude(selectedMapLat);l.setLongitude(selectedMapLon);return l;}if(!Double.isNaN(mapViewLat)&&!Double.isNaN(mapViewLon)){Location l=new Location("map-center");l.setLatitude(mapViewLat);l.setLongitude(mapViewLon);return l;}return engine.loc==null?null:new Location(engine.loc);}

        void reverseGeocodeSelectedMapPoint(final double lat, final double lon) {
            final int token = ++selectedMapGeocodeToken;
            selectedMapPlaceName = "";
            postInvalidate();
            new Thread(() -> {
                HttpURLConnection c = null;
                String name = "";
                try {
                    long now = System.currentTimeMillis();
                    long wait = 1000L - (now - lastNominatimGeocodeAt);
                    if (wait > 0) Thread.sleep(wait);
                    lastNominatimGeocodeAt = System.currentTimeMillis();
                    String urlText = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=" + lat + "&lon=" + lon + "&zoom=18&addressdetails=1&accept-language=it";
                    c = (HttpURLConnection)new URL(urlText).openConnection();
                    c.setConnectTimeout(6000); c.setReadTimeout(9000); c.setRequestMethod("GET");
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.22.20 (Android motorcycle display; map attribution visible)");
                    if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        JSONObject root = new JSONObject(readConnection(c));
                        JSONObject a = root.optJSONObject("address");
                        if (a != null) {
                            String road = a.optString("road", "");
                            String hn = a.optString("house_number", "");
                            String locality = firstNonEmpty(a.optString("village", ""), a.optString("town", ""), a.optString("city", ""), a.optString("municipality", ""), a.optString("suburb", ""), a.optString("hamlet", ""));
                            String first = road.isEmpty() ? root.optString("name", "") : road + (hn.isEmpty() ? "" : " " + hn);
                            if (!first.isEmpty() && !locality.isEmpty()) name = first + ", " + locality; else if (!first.isEmpty()) name = first; else name = locality;
                        }
                        if (name.isEmpty()) name = root.optString("display_name", "");
                    }
                } catch (Exception ignored) {} finally { if (c != null) c.disconnect(); }
                final String result = name;
                post(() -> { if (token == selectedMapGeocodeToken && !Double.isNaN(selectedMapLat) && Math.abs(selectedMapLat-lat)<1e-8 && Math.abs(selectedMapLon-lon)<1e-8) { selectedMapPlaceName = result; invalidate(); } });
            }, "map-reverse-geocode").start();
        }

        void clearSelectedMapPoint() {
            selectedMapLat = Double.NaN; selectedMapLon = Double.NaN; selectedMapPlaceName = ""; ++selectedMapGeocodeToken; invalidate();
        }

        void saveHomeLocation(Location l) {
            if(l==null)return;
            final double lat=l.getLatitude(), lon=l.getLongitude();
            if(!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return;

            // CASA is an exact map-coordinate bookmark. Keep the coordinate pair as the
            // canonical value and mirror it in PREF_HOME_ADDRESS for legacy UI paths.
            // Do not reverse-geocode CASA: a geocoder may return a nearby road/address
            // that is not the exact point selected by the user.
            String coordinates=String.format(Locale.US,"%.8f, %.8f",lat,lon);
            getSharedPreferences("road_display",MODE_PRIVATE).edit()
                    .putString(PREF_HOME_ADDRESS,coordinates)
                    .putString(PREF_HOME_LAT,Double.toString(lat))
                    .putString(PREF_HOME_LON,Double.toString(lon))
                    .apply();
            android.widget.Toast.makeText(MainActivity.this,"Posizione CASA memorizzata: "+coordinates,android.widget.Toast.LENGTH_SHORT).show();
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            final int action=e.getActionMasked(); final float x=e.getX(), y=e.getY();
            if(mapExpanded){
                if(vectorMapReady && vectorMapView != null && vectorMapView.getVisibility() == View.VISIBLE) {
                    if(action==MotionEvent.ACTION_DOWN){ downX=x; downY=y; movedMap=false; }
                    if(action==MotionEvent.ACTION_MOVE && Math.hypot(x-downX,y-downY)>dp(8)) movedMap=true;
                    try { vectorMapView.dispatchTouchEvent(e); } catch (Throwable ignored) {}
                    if(action==MotionEvent.ACTION_UP && !movedMap){
                        if(mapSearchBounds.contains(x,y)){ showMapSearchDialog(); return true; }
                        if(mapCloseBounds.contains(x,y)){ mapExpanded=false; mapUserPanned=false; clearSelectedMapPoint(); setVectorMapExpanded(false); invalidate(); return true; }
                        if(mapRecenterBounds.contains(x,y)){
                            if(engine.loc!=null){mapViewLat=engine.loc.getLatitude();mapViewLon=engine.loc.getLongitude();}
                            mapFrozenBearing=navigationActive && !Float.isNaN(navigationMatchedBearing) ? navigationMatchedBearing : (hasMapBearing ? mapBearing : 0f);
                            mapUserPanned=false; clearSelectedMapPoint(); syncVectorMapCamera(false); invalidate(); return true;
                        }
                        if(mapZoomButton(boundsForZoom(true,true),x,y)){ mapZoom=Math.min(1.7f,mapZoom+.2f); syncVectorMapCamera(false); invalidate(); return true; }
                        if(mapZoomButton(boundsForZoom(true,false),x,y)){ mapZoom=Math.max(.5f,mapZoom-.2f); syncVectorMapCamera(false); invalidate(); return true; }
                    }
                    if(action==MotionEvent.ACTION_CANCEL){ try { vectorMapView.dispatchTouchEvent(e); } catch (Throwable ignored) {} }
                    return true;
                }
                if(action==MotionEvent.ACTION_DOWN){
                    downX=x; downY=y; movedMap=false; draggingMap=true; pinching=false;
                    downMapLat=mapViewLat; downMapLon=mapViewLon; return true;
                }
                if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){
                    float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
                    pinchStartDistance=(float)Math.hypot(dx,dy); pinchStartZoom=mapZoom; pinching=pinchStartDistance>2f;
                    rotateStartAngle=(float)Math.toDegrees(Math.atan2(dy,dx)); rotateStartBearing=mapFrozenBearing;
                    rotatingMap=true; draggingMap=false; return true;
                }
                if(action==MotionEvent.ACTION_MOVE){
                    if(e.getPointerCount()>=2){
                        if(pinching && pinchStartDistance>2f){
                            float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1), dist=(float)Math.hypot(dx,dy);
                            mapZoom=Math.max(.5f,Math.min(1.7f,pinchStartZoom*dist/pinchStartDistance));
                        }
                        if(rotatingMap){
                            float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
                            float angle=(float)Math.toDegrees(Math.atan2(dy,dx));
                            float delta=angle-rotateStartAngle;
                            while(delta>180f)delta-=360f; while(delta<-180f)delta+=360f;
                            mapFrozenBearing=normalizeBearing(rotateStartBearing+delta); // Match the finger gesture direction; minus delta inverted rotation.
                            mapUserPanned=true;
                        }
                        invalidate(); return true;
                    }
                    if(draggingMap && e.getPointerCount()==1 && !Double.isNaN(downMapLat) && !Double.isNaN(downMapLon)){
                        float dx=x-downX, dy=y-downY;
                        if(Math.hypot(dx,dy)>dp(8)) movedMap=true;
                        if(movedMap){
                            int z=expandedMapZoom(); double world=256.0*(1<<z);
                            double s=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,downMapLat))));
                            double cx=(downMapLon+180.0)/360.0*world, cy=(.5-Math.log((1+s)/(1-s))/(4*Math.PI))*world;
                            double br=Math.toRadians(mapFrozenBearing), mdx=dx*Math.cos(br)-dy*Math.sin(br), mdy=dx*Math.sin(br)+dy*Math.cos(br);
                            double px=cx-mdx, py=cy-mdy;
                            mapViewLon=px/world*360.0-180.0; double n=Math.PI-2.0*Math.PI*py/world;
                            mapViewLat=Math.max(-85,Math.min(85,Math.toDegrees(Math.atan(Math.sinh(n))))); mapUserPanned=true; postInvalidateOnAnimation();
                        }
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_POINTER_UP){ pinching=false; rotatingMap=false; pinchStartDistance=0f; draggingMap=false; return true; }
                if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP){
                    if(action==MotionEvent.ACTION_UP && !movedMap && !pinching){
                        if(mapSearchBounds.contains(x,y)){ showMapSearchDialog(); }
                        else if(mapCloseBounds.contains(x,y)){ mapExpanded=false; mapUserPanned=false; clearSelectedMapPoint(); setVectorMapExpanded(false); }
                        else if(mapRecenterBounds.contains(x,y)){
                            if(engine.loc!=null){mapViewLat=engine.loc.getLatitude(); mapViewLon=engine.loc.getLongitude();}
                            mapFrozenBearing=navigationActive && !Float.isNaN(navigationMatchedBearing) ? navigationMatchedBearing : (hasMapBearing ? mapBearing : 0f); mapUserPanned=false; clearSelectedMapPoint(); syncVectorMapCamera(false);
                        } else if(mapSetDestBounds.contains(x,y)){
                            Location picked=mapCenterLocation();
                            if(picked!=null){
                                pendingMapLat=picked.getLatitude();
                                pendingMapLon=picked.getLongitude();
                                // Preserve the proven destination workflow: the full-screen
                                // map hands the exact coordinate to the navigation dialog, and
                                // the existing AVVIA button starts the route calculation.
                                // The coordinate remains machine-readable and is parsed directly
                                // by startNavigation(), so Photon/Nominatim are never needed for
                                // a point selected on the map.
                                showNavigationDialog(pendingMapLat,pendingMapLon);
                            }
                        } else if(mapSetHomeBounds.contains(x,y)){ Location picked=mapCenterLocation(); if(picked!=null)saveHomeLocation(picked);
                        } else if(mapClearPointBounds.contains(x,y)){ clearSelectedMapPoint();
                        } else if(mapZoomButton(boundsForZoom(true,true),x,y)){ mapZoom=Math.min(1.7f,mapZoom+.2f);
                        } else if(mapZoomButton(boundsForZoom(true,false),x,y)){ mapZoom=Math.max(.5f,mapZoom-.2f);
                        } else {
                            Location picked=mapLocationFromScreen(x,y);
                            if(picked!=null){ selectedMapLat=picked.getLatitude(); selectedMapLon=picked.getLongitude(); selectedMapPlaceName=""; reverseGeocodeSelectedMapPoint(selectedMapLat,selectedMapLon); }
                        }
                        invalidate();
                    }
                    draggingMap=false; pinching=false; pinchStartDistance=0f; return true;
                }
                return true;
            }
            if(selectedTripId>0){
                if(action==MotionEvent.ACTION_DOWN){
                    downX=x; downY=y;
                    draggingTripMap=tripMapBounds.contains(x,y); movedMap=false;
                    tripDownX=x; tripDownY=y; tripDownLat=tripMapCenterLat; tripDownLon=tripMapCenterLon;
                    return true;
                }
                if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2 && tripMapBounds.contains(x,y)){
                    float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
                    tripRotateStartAngle=(float)Math.toDegrees(Math.atan2(dy,dx)); tripRotateStartBearing=tripMapBearing; tripRotating=true; draggingTripMap=false; return true;
                }
                if(action==MotionEvent.ACTION_MOVE){
                    if(tripRotating && e.getPointerCount()>=2){
                        float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
                        float angle=(float)Math.toDegrees(Math.atan2(dy,dx)); float delta=angle-tripRotateStartAngle;
                        while(delta>180f)delta-=360f; while(delta<-180f)delta+=360f;
                        tripMapBearing=normalizeBearing(tripRotateStartBearing+delta); // Keep trip-map rotation consistent with the main map. invalidate(); return true;
                    }
                    if(draggingTripMap && e.getPointerCount()==1 && tripMapBounds.contains(x,y) && !Double.isNaN(tripDownLat) && !Double.isNaN(tripDownLon)){
                        float dx=x-tripDownX, dy=y-tripDownY; if(Math.hypot(dx,dy)>dp(6))movedMap=true;
                        if(movedMap){
                            int z=Math.max(10,Math.min(18,tripMapZoom)); double world=256.0*(1L<<z);
                            double s0=Math.sin(Math.toRadians(Math.max(-85,Math.min(85,tripDownLat))));
                            double cxw=(tripDownLon+180.0)/360.0*world, cyw=(.5-Math.log((1+s0)/(1-s0))/(4*Math.PI));
                            cyw*=world;
                            double ang=Math.toRadians(tripMapBearing); double mdx=dx*Math.cos(ang)-dy*Math.sin(ang), mdy=dx*Math.sin(ang)+dy*Math.cos(ang);
                            double px=cxw-mdx, py=cyw-mdy; tripMapCenterLon=px/world*360.0-180.0; double nn=Math.PI-2*Math.PI*py/world;
                            tripMapCenterLat=Math.max(-85,Math.min(85,Math.toDegrees(Math.atan(Math.sinh(nn))))); tripMapUserPanned=true; invalidate();
                        }
                        return true;
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_POINTER_UP){tripRotating=false;draggingTripMap=false;return true;}
                if(action==MotionEvent.ACTION_UP){
                    if(tripMapZoomInBounds.contains(x,y)){tripMapZoomOffset=Math.min(4,tripMapZoomOffset+1);invalidate();}
                    else if(tripMapZoomOutBounds.contains(x,y)){tripMapZoomOffset=Math.max(-2,tripMapZoomOffset-1);invalidate();}
                    else if(tripDetailListBounds.contains(x,y)){selectedTripId=-1;selectedTripSummary=null;selectedTripPoints.clear();tripDayScreen=false;selectedTripDayKey="";tripsScreen=true;tripsScroll=0f;invalidate();}
                    else if(tripDetailHomeBounds.contains(x,y)){exportSelectedTripGpx();}
                    else if(tripDetailDeleteBounds.contains(x,y)) deleteSelectedTrip();
                    draggingTripMap=false;tripRotating=false;return true;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_DOWN){downX=x;downY=y;return true;} if(action!=MotionEvent.ACTION_UP)return true;
            if(tripDayScreen){
                if(tripsCloseBounds.contains(x,y)){tripDayScreen=false;selectedTripDayKey="";tripsScroll=0f;invalidate();}
                else if(Math.abs(y-downY)>dp(8)){tripsScroll=Math.max(0f,tripsScroll+(downY-y));invalidate();}
                else if(action==MotionEvent.ACTION_UP){
                    java.util.List<TripDb.TripSummary> all=tripDb.listTrips(500); java.util.ArrayList<TripDb.TripSummary> day=new java.util.ArrayList<>();
                    for(TripDb.TripSummary t:all) if(tripDayKey(t.startTime).equals(selectedTripDayKey)) day.add(t);
                    float top=tripRecording?dp(142):dp(96), rowH=Math.max(dp(76),Math.min(dp(92),getHeight()*.10f));
                    int idx=(int)((y-top+tripsScroll)/rowH); if(idx>=0&&idx<day.size()&&y>=top){openTripDetail(day.get(idx).id);}
                }
                return true;
            }
            if(tripsScreen){
                if(gpxButtonBounds.contains(x,y)){showGpxManager();return true;}
                if(memoryButtonBounds.contains(x,y)){showMemoryManager();return true;}
                if(tripsCloseBounds.contains(x,y)){tripsScreen=false;invalidate();}
                else if(Math.abs(y-downY)>dp(8)){tripsScroll=Math.max(0f,tripsScroll+(downY-y));invalidate();}
                else if(action==MotionEvent.ACTION_UP){
                    java.util.List<TripDb.TripSummary> list=tripDb.listTrips(500); java.util.LinkedHashMap<String,java.util.ArrayList<TripDb.TripSummary>> groups=groupTripsByDay(list); java.util.ArrayList<String> keys=new java.util.ArrayList<>(groups.keySet());
                    float top=tripRecording?dp(176):dp(140), rowH=Math.max(dp(82),Math.min(dp(98),getHeight()*.11f));
                    int idx=(int)((y-top+tripsScroll)/rowH); if(idx>=0&&idx<keys.size()&&y>=top){openTripDay(keys.get(idx));}
                }
                return true;
            }
            if(mapBounds.contains(x,y)){
                if(mapZoomButton(boundsForZoom(false,true),x,y)){mapZoomCompact=Math.min(1.5f,mapZoomCompact+.2f);invalidate();return true;}
                if(mapZoomButton(boundsForZoom(false,false),x,y)){mapZoomCompact=Math.max(.8f,mapZoomCompact-.2f);invalidate();return true;}
                mapExpanded=true; mapUserPanned=false; mapFrozenBearing=navigationActive && !Float.isNaN(navigationMatchedBearing) ? navigationMatchedBearing : (hasMapBearing ? mapBearing : 0f);
                if(engine.loc!=null){mapViewLat=engine.loc.getLatitude();mapViewLon=engine.loc.getLongitude();}
                clearSelectedMapPoint(); setVectorMapExpanded(true); syncVectorMapCamera(false); invalidate(); return true;
            }
            if(speedBounds.contains(x,y)){showSpeedSettings();return true;} if(settingsButtonBounds.contains(x,y)){showDisplaySettings();return true;} if(navButtonBounds.contains(x,y)){showNavigationDialog();return true;} if(exitButtonBounds.contains(x,y)){showExitConfirmation();return true;}
            if(weatherButtonBounds.contains(x,y)){if(engine.loc!=null&&LocationManager.GPS_PROVIDER.equals(engine.loc.getProvider())&&engine.loc.hasAccuracy()&&engine.loc.getAccuracy()<=50f)maybeUpdateWeather(engine.loc,true);loadDestinationWeather();showWeatherPopup();return true;}
            if(tripsButtonBounds.contains(x,y)){tripsScreen=true;tripsScroll=0f;invalidate();return true;} return true;
        }


    }

    // Keeps typography consistent without depending on downloaded fonts.
    static class TypefaceCompat {
        static android.graphics.Typeface bold() { return android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD); }
    }
}
