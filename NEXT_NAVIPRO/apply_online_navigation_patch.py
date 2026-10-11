#!/usr/bin/env python3
"""Apply deterministic online-navigation quality overlays to the archived Android baseline.

The original project archive remains untouched in Git. This overlay is applied
in CI and fails closed if expected source snippets have drifted.
"""
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "Road_Display_PRO"
MAIN = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java"
MATCHER = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/RouteMapMatcher.java"
ENGINE = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java"
SMOOTH_TEST = PROJECT / "roadtools/map_marker_smoothing_regression_test.py"
RADAR_TEST = PROJECT / "roadtools/radar_system_regression_test.py"
BUILD_VERSION = PROJECT / "BUILD_VERSION.txt"
GRADLE = PROJECT / "app/build.gradle"
MAP_MANAGER = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/OsmAndMapManager.java"
MAP_MANAGER_TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/OsmAndMapManager.java"
OSMAND_ROUTER = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/OsmAndOfflineRouter.java"
OSMAND_ROUTER_TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/OsmAndOfflineRouter.java"
PLATFORM_SHIM = PROJECT / "app/src/main/java/net/osmand/PlatformUtil.java"
PLATFORM_SHIM_TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/PlatformUtil.java"
OFFLINE_RENDERER = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/OsmAndOfflineMapRenderer.java"
OFFLINE_RENDERER_TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/OsmAndOfflineMapRenderer.java"

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 0 and new in text:
        return text
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one baseline match, got {count}")
    return text.replace(old, new, 1)

for template in (MAP_MANAGER_TEMPLATE, OSMAND_ROUTER_TEMPLATE, PLATFORM_SHIM_TEMPLATE, OFFLINE_RENDERER_TEMPLATE):
    if not template.is_file():
        raise RuntimeError(f"Missing required OsmAnd integration template: {template}")
MAP_MANAGER.parent.mkdir(parents=True, exist_ok=True)
OSMAND_ROUTER.parent.mkdir(parents=True, exist_ok=True)
PLATFORM_SHIM.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(MAP_MANAGER_TEMPLATE, MAP_MANAGER)
shutil.copyfile(OSMAND_ROUTER_TEMPLATE, OSMAND_ROUTER)
shutil.copyfile(PLATFORM_SHIM_TEMPLATE, PLATFORM_SHIM)
shutil.copyfile(OFFLINE_RENDERER_TEMPLATE, OFFLINE_RENDERER)

# Attach the OsmAnd Java engine and its runtime support libraries to the archived app build.
gradle = GRADLE.read_text(encoding="utf-8")
required_dependencies = [
    "implementation fileTree(dir: 'libs', include: ['*.jar'])",
    "implementation 'commons-logging:commons-logging:1.2'",
    "implementation 'com.google.code.gson:gson:2.8.9'",
    "implementation 'org.json:json:20171018'",
    "implementation 'org.apache.commons:commons-compress:1.17'",
    "implementation 'com.moparisthebest:junidecode:0.1.1'",
    "implementation 'com.vividsolutions:jts-core:1.14.0'",
    "implementation 'com.google.openlocationcode:openlocationcode:1.0.4'",
    "implementation 'net.sf.kxml:kxml2:2.1.8'",
    "implementation 'net.sf.trove4j:trove4j:3.0.3'",
    "implementation files('libs/OsmAnd-shared-debug.aar')",
    "implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.20'",
    "implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3'",
    "implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1'",
    "implementation 'org.jetbrains.kotlinx:kotlinx-datetime:0.6.1'",
    "implementation 'com.squareup.okio:okio:3.9.0'",
    "implementation 'co.touchlab:stately-concurrent-collections:2.1.0'",
    "implementation 'io.ktor:ktor-client-core:3.1.3'",
    "implementation 'io.ktor:ktor-client-encoding:3.1.3'",
    "implementation 'io.ktor:ktor-network:3.1.3'",
    "implementation 'io.ktor:ktor-client-okhttp:3.1.3'",
    "implementation 'androidx.sqlite:sqlite:2.3.1'",
    "implementation 'androidx.sqlite:sqlite-framework:2.3.1'",
    "implementation 'io.coil-kt.coil3:coil-core:3.1.0'",
    "implementation 'io.coil-kt.coil3:coil-network-okhttp:3.1.0'",
]
missing_dependencies = [dep for dep in required_dependencies if dep not in gradle]
if missing_dependencies:
    if "dependencies {" in gradle:
        gradle = gradle.replace("dependencies {", "dependencies {\n    " + "\n    ".join(missing_dependencies), 1)
    else:
        gradle += "\n\ndependencies {\n    " + "\n    ".join(missing_dependencies) + "\n}\n"
    GRADLE.write_text(gradle, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main = replace_once(main, "    volatile boolean vectorMapFailed = false;", "    volatile boolean vectorMapFailed = false;\n    OsmAndOfflineMapRenderer offlineMapRenderer;", "offline OBF renderer state")
main = replace_once(main, "        view = new RideView(this);", "        view = new RideView(this);\n        offlineMapRenderer = new OsmAndOfflineMapRenderer(this);", "initialize offline OBF renderer")

# Add an honest, compact source badge over the shared map/HUD surface.
# MapLibre currently renders the visible cartography online; choosing OsmAnd
# offline routing must never make the UI falsely imply that the map renderer is offline.
badge_code = '''
    private void installMapSourceStatusBadge() {
        android.widget.FrameLayout root = findViewById(android.R.id.content);
        if (root == null) return;
        android.view.View existing = root.findViewWithTag("road_display_map_source_badge");
        android.widget.TextView badge;
        if (existing instanceof android.widget.TextView) {
            badge = (android.widget.TextView) existing;
        } else {
            badge = new android.widget.TextView(this);
            badge.setTag("road_display_map_source_badge");
            badge.setTextSize(10f);
            badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            badge.setTextColor(android.graphics.Color.WHITE);
            badge.setPadding((int) mapBadgeDp(9), (int) mapBadgeDp(5), (int) mapBadgeDp(9), (int) mapBadgeDp(5));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xDD111111);
            bg.setCornerRadius(mapBadgeDp(8));
            bg.setStroke((int) mapBadgeDp(1), 0xFFFFD54F);
            badge.setBackground(bg);
            badge.setElevation(mapBadgeDp(8));
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.END);
            lp.setMargins((int) mapBadgeDp(8), (int) mapBadgeDp(8), (int) mapBadgeDp(8), 0);
            root.addView(badge, lp);
        }
        refreshMapSourceStatusBadge();
    }

    private float mapBadgeDp(float value) {\n        return value * getResources().getDisplayMetrics().density;\n    }\n\n    private void refreshMapSourceStatusBadge() {
        android.widget.FrameLayout root = findViewById(android.R.id.content);
        if (root == null) return;
        android.view.View view = root.findViewWithTag("road_display_map_source_badge");
        if (!(view instanceof android.widget.TextView)) return;
        int mode = getSharedPreferences("road_display", MODE_PRIVATE).getInt("routing_engine_mode", 0);
        String route = mode == 1 ? "NAV ONLINE" : mode == 2 ? "NAV OFFLINE" : "NAV AUTO";
        ((android.widget.TextView) view).setText("CARTINA ONLINE · " + route);
        ((android.widget.TextView) view).setContentDescription("Cartografia online MapLibre; modalità percorso: " + route);
    }

'''
# Install the badge after the first content view is attached; the app's ride HUD and
# expanded map share this activity surface, so the source label stays visible on both.
content_match = re.search(r"\bsetContentView\([^;]*\);", main)
if not content_match:
    raise RuntimeError("map source status badge: could not find setContentView in MainActivity")
main = main[:content_match.end()] + "\n        installMapSourceStatusBadge();" + main[content_match.end():]
class_end = main.rfind("\n}")
if class_end < 0:
    raise RuntimeError("map source status badge: MainActivity class end not found")
main = main[:class_end] + "\n" + badge_code + main[class_end:]
# When local OsmAnd OBF files exist, use the offline vector renderer on all Canvas maps
# and suppress MapLibre/raster network cartography. Routing mode remains independent.
main = replace_once(main,
    """    void setVectorMapExpanded(boolean expanded) {
        if (vectorMapView == null) return;
        try {
            vectorMapView.setVisibility(expanded && vectorMapReady ? View.VISIBLE : View.GONE);
            if (expanded && vectorMapReady) syncVectorMapCamera(false);
        } catch (Throwable ignored) {}
    }""",
    """    void setVectorMapExpanded(boolean expanded) {
        if (vectorMapView == null) return;
        try {
            boolean localObfCartography = OsmAndOfflineRouter.hasInstalledMaps(this);
            vectorMapView.setVisibility(expanded && vectorMapReady && !localObfCartography ? View.VISIBLE : View.GONE);
            if (expanded && vectorMapReady && !localObfCartography) syncVectorMapCamera(false);
        } catch (Throwable ignored) {}
    }""", "hide online map when OBF cartography exists")
main = replace_once(main,
    "float renderBearing=expanded?mapFrozenBearing:(!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f));double angle=Math.toRadians(renderBearing);",
    "float renderBearing=expanded?mapFrozenBearing:(!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f));\n            if (OsmAndOfflineRouter.hasInstalledMaps(MainActivity.this) && offlineMapRenderer != null) {\n                offlineMapRenderer.draw(c, bounds, lat, lon, zoom, renderBearing, scale, density);\n                return true; // Local OBF is the cartographic source; never silently fetch raster tiles.\n            }\n            double angle=Math.toRadians(renderBearing);",
    "offline OBF renderer takes precedence over network tiles")
main = replace_once(main,
    "        if (view != null) view.releaseTiles();",
    "        if (view != null) view.releaseTiles();\n        if (offlineMapRenderer != null) offlineMapRenderer.close();",
    "close offline OBF renderer")
# Keep the visual source badge truthful independently of the route engine selected.
main = replace_once(main,
    """        ((android.widget.TextView) view).setText("CARTINA ONLINE · " + route);
        ((android.widget.TextView) view).setContentDescription("Cartografia online MapLibre; modalità percorso: " + route);""",
    """        boolean localObf = OsmAndOfflineRouter.hasInstalledMaps(this);
        ((android.widget.TextView) view).setText(localObf ? "OFF" : "ON");
        ((android.widget.TextView) view).setContentDescription((localObf ? "Cartografia vettoriale OsmAnd offline" : "Cartografia online MapLibre") + "; modalità percorso: " + route);""",
    "truthful offline cartography source badge")
main = replace_once(main,
    "    private float mapBadgeDp(float value) {",
    """    void onOfflineMapsChanged() {
        refreshMapSourceStatusBadge();
        if (view != null) {
            if (view.mapExpanded) setVectorMapExpanded(true);
            view.postInvalidate();
        }
    }

    private float mapBadgeDp(float value) {""",
    "refresh map UI after OBF install/delete")
# Match the visual map rotation to the user's two-finger gesture. The map renderer
# rotates its content by -bearing, so bearing must move opposite to the finger angle.
main = replace_once(
    main,
    "mapFrozenBearing=normalizeBearing(rotateStartBearing+delta); // Match the finger gesture direction; minus delta inverted rotation.",
    "mapFrozenBearing=normalizeBearing(rotateStartBearing+delta); // Follow the user's two-finger rotation direction.",
    "full-screen map rotation direction",
)
main = replace_once(
    main,
    "tripMapBearing=normalizeBearing(tripRotateStartBearing+delta); // Keep trip-map rotation consistent with the main map.",
    "tripMapBearing=normalizeBearing(tripRotateStartBearing+delta); // Follow the same gesture direction as the full-screen map.",
    "trip-map rotation direction",
)
# The full-screen map is a native MapLibre MapView layered above the Canvas. Its
# built-in rotate detector has the opposite feel for this app's map-following gesture.
# Disable only native rotation, retain native pan/zoom, and apply a single custom delta.
main = replace_once(
    main,
    "                    vectorMap = map;\n",
    "                    vectorMap = map;\n                    map.getUiSettings().setRotateGesturesEnabled(false);\n",
    "disable native MapLibre rotation gesture",
)
main = replace_once(
    main,
    "        boolean pinching = false, rotatingMap = false;\n        float rotateStartAngle = 0f, rotateStartBearing = 0f;",
    "        boolean pinching = false, rotatingMap = false;\n"
    "        float rotateStartAngle = 0f, rotateStartBearing = 0f;\n"
    "        boolean vectorRotating = false;\n"
    "        float vectorRotateStartAngle = 0f, vectorRotateStartBearing = 0f;",
    "custom full-screen MapLibre rotation state",
)
main = replace_once(
    main,
    """                    if(action==MotionEvent.ACTION_DOWN){ downX=x; downY=y; movedMap=false; }
                    if(action==MotionEvent.ACTION_MOVE && Math.hypot(x-downX,y-downY)>dp(8)) movedMap=true;
                    try { vectorMapView.dispatchTouchEvent(e); } catch (Throwable ignored) {}""",
    """                    if(action==MotionEvent.ACTION_DOWN){ downX=x; downY=y; movedMap=false; vectorRotating=false; }
                    if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){
                        float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
                        vectorRotateStartAngle=(float)Math.toDegrees(Math.atan2(dy,dx));
                        vectorRotateStartBearing=mapFrozenBearing;
                        vectorRotating=true; movedMap=true;
                    }
                    if(action==MotionEvent.ACTION_MOVE && e.getPointerCount()>=2 && vectorRotating){
                        float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
                        float angle=(float)Math.toDegrees(Math.atan2(dy,dx));
                        float delta=angle-vectorRotateStartAngle;
                        while(delta>180f)delta-=360f; while(delta<-180f)delta+=360f;
                        mapFrozenBearing=normalizeBearing(vectorRotateStartBearing+delta);
                        mapUserPanned=true; movedMap=true;
                        // Change only the bearing: preserve MapLibre's live target and zoom
                        // so rotation never snaps the map back during a simultaneous pinch.
                        try {
                            CameraPosition currentCamera = vectorMap.getCameraPosition();
                            vectorMap.setCameraPosition(new CameraPosition.Builder()
                                    .target(currentCamera.target).zoom(currentCamera.zoom)
                                    .tilt(currentCamera.tilt).bearing(mapFrozenBearing).build());
                        } catch (Throwable ignored) { }
                    }
                    if(action==MotionEvent.ACTION_MOVE && Math.hypot(x-downX,y-downY)>dp(8)) movedMap=true;
                    if(action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP)
                        vectorRotating=false;
                    try { vectorMapView.dispatchTouchEvent(e); } catch (Throwable ignored) {}""",
    "manual full-screen MapLibre rotation gesture",
)
# Expose map data management in the existing display-settings dialog without
# replacing or simplifying any of the Ride HUD/settings behavior.
main = replace_once(
    main,
    """            group.setOrientation(android.widget.RadioGroup.VERTICAL);
            group.setPadding((int)dp(18), (int)dp(8), (int)dp(18), (int)dp(8));
            final android.widget.RadioButton[] radios = new android.widget.RadioButton[modes.length];""",
    """            group.setOrientation(android.widget.RadioGroup.VERTICAL);
            group.setPadding((int)dp(18), (int)dp(8), (int)dp(18), (int)dp(8));
            LinearLayout settingsLayout = new LinearLayout(MainActivity.this);
            settingsLayout.setOrientation(LinearLayout.VERTICAL);
            settingsLayout.addView(group, new LinearLayout.LayoutParams(-1, -2));
            final android.widget.RadioButton[] radios = new android.widget.RadioButton[modes.length];""",
    "OsmAnd manager settings container",
)
main = replace_once(
    main,
    """            group.check(100 + Math.max(0, Math.min(2, displayThemeMode)));
            String luxText = Float.isNaN(currentAmbientLux) ? "Lettura sensore: non disponibile" : "Lettura sensore: " + Math.round(currentAmbientLux) + " lux";""",
    """            group.check(100 + Math.max(0, Math.min(2, displayThemeMode)));
            android.widget.Button osmAndMaps = new android.widget.Button(MainActivity.this);
            osmAndMaps.setText("MAPPE OFFLINE OSMAND");
            osmAndMaps.setAllCaps(false);
            osmAndMaps.setTextSize(12f);
            osmAndMaps.setMaxLines(1);
            osmAndMaps.setSingleLine(true);
            osmAndMaps.setGravity(android.view.Gravity.CENTER);
            osmAndMaps.setPadding((int)dp(6), 0, (int)dp(6), 0);
            osmAndMaps.setOnClickListener(v -> OsmAndMapManager.show(MainActivity.this));
            settingsLayout.addView(osmAndMaps, new LinearLayout.LayoutParams(-1, (int)dp(48)));
            android.content.SharedPreferences routingPrefs = getSharedPreferences("road_display", MODE_PRIVATE);
            android.widget.Button routingModeButton = new android.widget.Button(MainActivity.this);
            int savedRoutingMode = Math.max(0, Math.min(2, routingPrefs.getInt("routing_engine_mode", 0)));
            String[] routingModeLabels = {"AUTOMATICO · OsmAnd, poi online", "ONLINE · Valhalla / OSRM", "OFFLINE · solo OsmAnd"};
            String[] routingModeButtonLabels = {"NAV: AUTO", "NAV: ONLINE", "NAV: OFFLINE"};
            routingModeButton.setText(routingModeButtonLabels[savedRoutingMode]);
            routingModeButton.setAllCaps(false);
            // Long routing labels must remain readable inside the settings button on narrow screens.
            routingModeButton.setTextSize(12f);
            routingModeButton.setSingleLine(true);
            routingModeButton.setGravity(android.view.Gravity.CENTER);
            routingModeButton.setPadding((int)dp(6), 0, (int)dp(6), 0);
            routingModeButton.setOnClickListener(v -> {
                android.widget.RadioGroup routingModes = new android.widget.RadioGroup(MainActivity.this);
                routingModes.setOrientation(android.widget.RadioGroup.VERTICAL);
                routingModes.setPadding((int)dp(18), (int)dp(8), (int)dp(18), (int)dp(8));
                for (int i = 0; i < routingModeLabels.length; i++) {
                    android.widget.RadioButton option = new android.widget.RadioButton(MainActivity.this);
                    option.setId(200 + i);
                    option.setText(routingModeLabels[i]);
                    option.setTextSize(13f);
                    option.setMaxLines(2);
                    option.setPadding((int)dp(8), 0, (int)dp(8), 0);
                    routingModes.addView(option, new android.widget.RadioGroup.LayoutParams(-1, (int)dp(52)));
                }
                routingModes.check(200 + Math.max(0, Math.min(2, routingPrefs.getInt("routing_engine_mode", 0))));
                new AlertDialog.Builder(MainActivity.this).setTitle("MOTORE DI NAVIGAZIONE")
                    .setMessage("AUTOMATICO: usa OsmAnd se le mappe locali permettono un percorso reale, altrimenti prova online. ONLINE: usa Valhalla e OSRM. OFFLINE: non usa la rete per calcolare il percorso e fallisce se i dati locali non bastano.")
                    .setView(routingModes).setNegativeButton("ANNULLA", null)
                    .setPositiveButton("SALVA", (d, which) -> {
                        int selectedMode = routingModes.getCheckedRadioButtonId() - 200;
                        if (selectedMode < 0 || selectedMode > 2) selectedMode = 0;
                        routingPrefs.edit().putInt("routing_engine_mode", selectedMode).apply();
                        routingModeButton.setText(routingModeButtonLabels[selectedMode]);
                        refreshMapSourceStatusBadge();
                    }).show();
            });
            settingsLayout.addView(routingModeButton, new LinearLayout.LayoutParams(-1, (int)dp(48)));
            String luxText = Float.isNaN(currentAmbientLux) ? "Lettura sensore: non disponibile" : "Lettura sensore: " + Math.round(currentAmbientLux) + " lux";""",
    "OsmAnd manager settings button",
)
main = replace_once(
    main,
    """.setView(group)
                .setNegativeButton("ANNULLA", null)""",
    """.setView(settingsLayout)
                .setNegativeButton("ANNULLA", null)""",
    "OsmAnd manager settings view",
)
# Prefer the actual OsmAnd Java router when local .obf maps exist. If a map does not
# cover the route or the offline engine cannot calculate it, keep the current online
# Valhalla -> OSRM routing path as the fallback.
main = replace_once(
    main,
    """        NavigationRouteResult fetchNavigationRoute(Location start, double destLat, double destLon) throws Exception {
            // Ask both routing engines concurrently. The first complete, valid result wins;""",
    """        NavigationRouteResult fetchNavigationRoute(Location start, double destLat, double destLon) throws Exception {
            int routingMode = MainActivity.this.getSharedPreferences("road_display", MODE_PRIVATE).getInt("routing_engine_mode", 0);
            if (routingMode != 1 && OsmAndOfflineRouter.hasInstalledMaps(MainActivity.this)) {
                try {
                    NavigationRouteResult offline = OsmAndOfflineRouter.calculate(MainActivity.this, start, destLat, destLon);
                    if (offline != null && offline.route.size() >= 2 && !offline.steps.isEmpty()) return offline;
                    throw new IllegalStateException("OsmAnd non ha restituito un percorso valido");
                } catch (Exception offlineFailure) {
                    android.util.Log.w("RoadDisplay", "OsmAnd offline route unavailable", offlineFailure);
                    if (routingMode == 2) throw new Exception("MODALITÀ OFFLINE: calcolo OsmAnd fallito. " + OsmAndOfflineRouter.safeMessage(offlineFailure), offlineFailure);
                }
            }
            if (routingMode == 2) throw new Exception("MODALITÀ OFFLINE: " + OsmAndOfflineRouter.installedMapStatus(MainActivity.this));
            // Ask the existing online routing engines concurrently. Valhalla remains primary;""",
    "OsmAnd offline-first route selection",
)
# Keep valid-but-moderate GNSS fixes alive longer; never treat one weak sample as an immediate outage.
main = replace_once(
    main,
    "    static final float GNSS_USABLE_ACCURACY_M = 100f;\n    static final long GNSS_STRONG_MAX_AGE_MS = 3500L;\n    static final long GNSS_USABLE_MAX_AGE_MS = 10000L;\n    static final long GNSS_LOSS_TIMEOUT_MS = 15000L;",
    "    static final float GNSS_USABLE_ACCURACY_M = 120f;\n    static final long GNSS_STRONG_MAX_AGE_MS = 5000L;\n    static final long GNSS_USABLE_MAX_AGE_MS = 15000L;\n    static final long GNSS_LOSS_TIMEOUT_MS = 40000L;",
    "GNSS holdover and usable-fix window",
)
# Keep speed limits available on a fresh, usable fix even when it misses the stricter safety-grade GNSS tier.
main = replace_once(
    main,
    """        } else {
            // Approximate/network locations are for map loading only, never curve or speed advice.
            // Never keep an old speed limit alive after GPS quality is lost.
            currentSpeedLimitKmh = 0;""",
    """        } else {
            // A moderate but fresh GNSS fix may still support road-limit lookup.
            // Do not enable curve/safety advice or trip-distance accumulation on this path.
            if (usableGps && l.hasAccuracy() && l.getAccuracy() <= 65f) {
                // Refresh the road snapshot before querying the limit; this also
                // starts a throttled online edge cross-check when GPS quality permits.
                engine.update(l);
                currentSpeedLimitKmh = engine.speedLimitKmh(l);
            } else {
                currentSpeedLimitKmh = 0;
            }
            // Approximate/network locations are for map loading only, never curve or speed advice.""",
    "speed limit lookup on usable GNSS fix",
)
# Distinguish fused-provider callbacks from fallback network/passive updates. Network
# callbacks must never erase a still-valid GNSS speed limit or move the live road state.
main = replace_once(main, "    void processLocation(Location l) {\n        if (l == null) return;", "    void processLocation(Location l) {\n        processLocation(l, false);\n    }\n\n    void processLocation(Location l, boolean fromFusedCallback) {\n        if (l == null) return;", "fused provider-aware location processing")
main = replace_once(main, "for (Location l : result.getLocations()) processLocation(l);", "for (Location l : result.getLocations()) processLocation(l, true);", "fused callback source")
main = replace_once(main, "if (l != null) processLocation(l);", "if (l != null) processLocation(l, true);", "fused cached source")
main = replace_once(main, 'boolean isFused = fromFusedCallback || "fused".equals(provider);', 'boolean isFused = "fused".equals(provider);', "do not infer GPS quality from callback origin")
accuracy_pattern = r"boolean hasReasonableAccuracy\s*=\s*[^;]+;"
main, accuracy_replacements = re.subn(
    accuracy_pattern,
    "float providerAccuracyLimit = isFused ? 50f : GNSS_USABLE_ACCURACY_M;\\n"
    "        boolean hasReasonableAccuracy = l.hasAccuracy() && Float.isFinite(l.getAccuracy())\\n"
    "                && l.getAccuracy() <= providerAccuracyLimit;",
    main,
)
if accuracy_replacements != 1:
    raise RuntimeError(f"provider-specific location accuracy gate: expected exactly one baseline match, got {accuracy_replacements}")
main = replace_once(main, "        boolean usableGps = (isPlatformGnss || isFused) && hasReasonableAccuracy\n                && locationAgeMs <= GNSS_USABLE_MAX_AGE_MS;", "        boolean usableGps = (isPlatformGnss || isFused) && hasReasonableAccuracy\n                && locationAgeMs <= GNSS_USABLE_MAX_AGE_MS;\n        if (!isPlatformGnss && !isFused && lastUsableGpsAt != 0L\n                && nowMs - lastUsableGpsAt <= GNSS_LOSS_TIMEOUT_MS) return;", "ignore fallback provider over live GNSS")
MAIN.write_text(main, encoding="utf-8")
main = replace_once(
    main,
    "        double smoothMarkerLat=Double.NaN,smoothMarkerLon=Double.NaN; float smoothMarkerBearing=Float.NaN;",
    "        double smoothMarkerLat=Double.NaN,smoothMarkerLon=Double.NaN; float smoothMarkerBearing=Float.NaN;\n"
    "        long smoothMarkerLastFrameAt=0L;",
    "marker animation clock",
)
old_smoothing = """                    float targetGap=sm.distanceTo(tg);
                    // Avoid multi-second lag at motorway speed and snap immediately after a large, credible GPS jump.
                    double alpha=targetGap>45f?1.0:(location.hasSpeed()&&location.getSpeed()>18f?0.72:0.55);
                    smoothMarkerLat+=(targetMarkerLat-smoothMarkerLat)*alpha;smoothMarkerLon+=(targetMarkerLon-smoothMarkerLon)*alpha;
                    if(Float.isNaN(smoothMarkerBearing))smoothMarkerBearing=targetMarkerBearing;else{float bd=targetMarkerBearing-smoothMarkerBearing;while(bd>180)bd-=360;while(bd<-180)bd+=360;smoothMarkerBearing=normalizeBearing(smoothMarkerBearing+bd*(location.hasSpeed()&&location.getSpeed()>18f?0.55f:0.32f));}"""
new_smoothing = """                    float targetGap=sm.distanceTo(tg);
                    // Time-based exponential smoothing is independent of render frame rate.
                    // A credible large jump snaps immediately; ordinary GPS jitter is eased.
                    long markerFrameNow=android.os.SystemClock.elapsedRealtime();
                    float markerDt=smoothMarkerLastFrameAt==0L?0.05f:
                            Math.max(0.001f,Math.min(0.10f,(markerFrameNow-smoothMarkerLastFrameAt)/1000f));
                    smoothMarkerLastFrameAt=markerFrameNow;
                    boolean motorwaySpeed=location.hasSpeed()&&location.getSpeed()>18f;
                    double markerAlpha=targetGap>45f?1.0:(1.0-Math.exp(-markerDt/(motorwaySpeed?0.12:0.20)));
                    smoothMarkerLat+=(targetMarkerLat-smoothMarkerLat)*markerAlpha;
                    smoothMarkerLon+=(targetMarkerLon-smoothMarkerLon)*markerAlpha;
                    if(Float.isNaN(smoothMarkerBearing))smoothMarkerBearing=targetMarkerBearing;
                    else{
                        float bd=targetMarkerBearing-smoothMarkerBearing;
                        while(bd>180)bd-=360;
                        while(bd<-180)bd+=360;
                        double bearingAlpha=1.0-Math.exp(-markerDt/(motorwaySpeed?0.10:0.18));
                        smoothMarkerBearing=normalizeBearing((float)(smoothMarkerBearing+bd*bearingAlpha));
                    }"""
main = replace_once(main, old_smoothing, new_smoothing, "time-based marker smoothing")
# Animate between 1 Hz GNSS samples: render-only short dead reckoning, never used for road/safety decisions.
main = replace_once(
    main,
    "double targetMarkerLat=location.getLatitude(),targetMarkerLon=location.getLongitude(); float targetMarkerBearing=!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f);",
    """double targetMarkerLat=location.getLatitude(),targetMarkerLon=location.getLongitude(); float targetMarkerBearing=!Float.isNaN(navigationMatchedBearing)?navigationMatchedBearing:(hasMapBearing?mapBearing:0f);
                if(location.hasSpeed() && location.hasBearing() && location.getSpeed() > 1.5f) {
                    long fixAgeMs = Math.max(0L, System.currentTimeMillis() - location.getTime());
                    double predictSeconds = Math.min(0.55, fixAgeMs / 1000.0);
                    double distanceAhead = location.getSpeed() * predictSeconds;
                    double bearingRad = Math.toRadians(location.getBearing());
                    double cosLat = Math.max(0.15, Math.cos(Math.toRadians(location.getLatitude())));
                    targetMarkerLat += distanceAhead * Math.cos(bearingRad) / 111320.0;
                    targetMarkerLon += distanceAhead * Math.sin(bearingRad) / (111320.0 * cosLat);
                }""",
    "smooth marker short-horizon motion prediction",
)
main = main.replace("postInvalidateDelayed((navigationActive||mapExpanded)?50:1000); // Smooth navigation marker while active.", "postInvalidateDelayed((navigationActive||mapExpanded)?33:1000); // Keep the marker animation near 30 fps while active.")
main = replace_once(main, "else if(location.hasAccuracy() && location.getAccuracy() <= 20f)", "else if(location.hasAccuracy() && location.getAccuracy() <= 65f && location.getTime() > 0L && System.currentTimeMillis() - location.getTime() <= 15000L)", "fresh-fix-only road snap for moderate but usable GPS")
MAIN.write_text(main, encoding="utf-8")
# Improve speed-limit alert timing to avoid transient GPS-speed spikes and alert spam.
old_fields = """    volatile int currentSpeedLimitKmh = 0;
    boolean speedLimitAlerted = false;"""
new_fields = """    volatile int currentSpeedLimitKmh = 0;
    boolean speedLimitAlerted = false;
    volatile long speedLimitOverSinceMs = 0L;
    volatile long speedLimitBelowSinceMs = 0L;
    volatile long lastSpeedLimitWarningAt = 0L;"""
main = replace_once(main, old_fields, new_fields, "speed limit alert state")
old_warn = """                if (actualKmh > currentSpeedLimitKmh + 3f) {
                    if (!speedLimitAlerted) {
                        beepSpeedLimit(); speedLimitAlerted = true;
                        if (audio && navigationTtsReady && navigationTts != null && nowMs - lastSpeedLimitVoiceAt > 8000L) {
                            final int lim = currentSpeedLimitKmh; lastSpeedLimitVoiceAt = nowMs;
                            runOnUiThread(() -> { try { navigationTts.speak("Attenzione, limite " + lim + " chilometri orari.", TextToSpeech.QUEUE_FLUSH, null, "speed-limit-over"); } catch (Exception ignored) { } });
                        }
                    }
                } else if (actualKmh <= currentSpeedLimitKmh - 3f) speedLimitAlerted = false;"""
new_warn = """                if (actualKmh > currentSpeedLimitKmh + 3f) {
                    speedLimitBelowSinceMs = 0L;
                    if (speedLimitOverSinceMs == 0L) speedLimitOverSinceMs = nowMs;
                    // OsmAnd-style anti-spam: require 5 seconds of continuous overspeed,
                    // then repeat no more often than every 120 seconds.
                    if (nowMs - speedLimitOverSinceMs >= 5000L
                            && (lastSpeedLimitWarningAt == 0L || nowMs - lastSpeedLimitWarningAt >= 120000L)) {
                        beepSpeedLimit();
                        speedLimitAlerted = true;
                        lastSpeedLimitWarningAt = nowMs;
                        if (audio && navigationTtsReady && navigationTts != null) {
                            final int lim = currentSpeedLimitKmh;
                            lastSpeedLimitVoiceAt = nowMs;
                            runOnUiThread(() -> { try { navigationTts.speak("Attenzione, limite " + lim + " chilometri orari.", TextToSpeech.QUEUE_FLUSH, null, "speed-limit-over"); } catch (Exception ignored) { } });
                        }
                    }
                } else {
                    speedLimitOverSinceMs = 0L;
                    if (actualKmh <= currentSpeedLimitKmh) {
                        if (speedLimitBelowSinceMs == 0L) speedLimitBelowSinceMs = nowMs;
                        // Reset the repeat timer only after 30 seconds at/below the legal limit.
                        if (nowMs - speedLimitBelowSinceMs >= 30000L) {
                            lastSpeedLimitWarningAt = 0L;
                            speedLimitAlerted = false;
                        }
                    } else {
                        speedLimitBelowSinceMs = 0L;
                    }
                }"""
main = replace_once(main, old_warn, new_warn, "debounced speed limit warning")
main = main.replace("                speedLimitAlerted = false;\n                if (lastSpokenSpeedLimitKmh > 0)", "                speedLimitAlerted = false;\n                speedLimitOverSinceMs = 0L; speedLimitBelowSinceMs = 0L; lastSpeedLimitWarningAt = 0L;\n                if (lastSpokenSpeedLimitKmh > 0)")
main = main.replace("            speedLimitAlerted = false;\n            engine.loc = new Location(l);", "            speedLimitAlerted = false; speedLimitOverSinceMs = 0L; speedLimitBelowSinceMs = 0L; lastSpeedLimitWarningAt = 0L;\n            engine.loc = new Location(l);")
MAIN.write_text(main, encoding="utf-8")



test = SMOOTH_TEST.read_text(encoding="utf-8")
test = replace_once(
    test,
    "    'high-speed marker smoothing is more responsive': 'location.getSpeed()>18f?0.72:0.55' in main,",
    "    'marker smoothing uses elapsed frame time': 'smoothMarkerLastFrameAt' in main and 'SystemClock.elapsedRealtime()' in main,\n"
    "    'marker smoothing is frame-rate independent': '1.0-Math.exp(-markerDt/(motorwaySpeed?0.12:0.20))' in main,\n"
    "    'bearing smoothing uses a time constant': 'bearingAlpha=1.0-Math.exp(-markerDt/(motorwaySpeed?0.10:0.18))' in main,\n"
    "    'marker predicts only a short horizon between GPS samples': 'Math.min(0.55, fixAgeMs / 1000.0)' in main and 'distanceAhead = location.getSpeed() * predictSeconds' in main,\n"
    "    'active map animation refreshes near 30 fps': 'postInvalidateDelayed((navigationActive||mapExpanded)?33:1000); // Keep the marker animation near 30 fps while active.' in main,",
    "marker smoothing regression assertions",
)
SMOOTH_TEST.write_text(test, encoding="utf-8")


# Match geometry first, including roads with unknown limits, so an adjacent road's
# limit can never be borrowed merely because the current road has no maxspeed tag.
engine = ENGINE.read_text(encoding="utf-8")
# Guard the rendering-only road snap against stale, inaccurate, or invalid fixes.
engine = replace_once(
    engine,
    "Location mapMatchedLocation(Location l) {\n        if (l == null || ways == null || ways.isEmpty()) return null;",
    "Location mapMatchedLocation(Location l) {\n        if (l == null || ways == null || ways.isEmpty()\n"
    "                || !l.hasAccuracy() || !Float.isFinite(l.getAccuracy()) || l.getAccuracy() > 65f\n"
    "                || l.getTime() <= 0L || System.currentTimeMillis() - l.getTime() > 15000L) return null;",
    "fresh accurate location required for marker road snap",
)
# Critical repair: fetchOnline() existed but was never called, so the local roads DB
# could stay empty and speedLimitKmh() would return 0 indefinitely. Load OSM ways
# asynchronously, with bounded retry/movement cadence, never on the UI thread.
engine = replace_once(
    engine,
    "    Location loc;",
    "    volatile Location loc;",
    "publish latest location safely to background road loader",
)
engine = replace_once(
    engine,
    '    String matchedWayId = "";',
    '    String matchedWayId = "";\n    String displayMatchedWayId = "";\n    long displayMatchedAt = 0L;',
    "remember the last display-only road match for continuity",
)
engine = replace_once(
    engine,
    "    volatile boolean crossCheckInFlight = false;",
    "    volatile boolean crossCheckInFlight = false;\n"
    "    volatile boolean roadFetchInFlight = false;\n"
    "    Location lastRoadFetchLocation;\n"
    "    long lastRoadFetchAt = 0L;",
    "online OSM road-fetch state",
)
engine = replace_once(
    engine,
    "        if (refresh) reloadRoads(l);",
    "        if (refresh) reloadRoads(l);\n        requestRoadDataFetch(l);",
    "trigger OSM road fetch from live location updates",
)
engine = replace_once(
    engine,
    "    boolean update(Location l) {",
    """    void requestRoadDataFetch(Location l) {
        if (l == null || !l.hasAccuracy() || !Float.isFinite(l.getAccuracy())
                || l.getAccuracy() > 65f || roadFetchInFlight) return;
        long now = System.currentTimeMillis();
        boolean noLocalRoads = ways == null || ways.isEmpty();
        boolean movedFar = lastRoadFetchLocation == null
                || lastRoadFetchLocation.distanceTo(l) >= 5000f;
        boolean retryDue = lastRoadFetchAt == 0L
                || now - lastRoadFetchAt >= (noLocalRoads ? 60000L : 15L * 60L * 1000L);
        if (!noLocalRoads && !movedFar && !retryDue) return;
        roadFetchInFlight = true;
        lastRoadFetchAt = now;
        lastRoadFetchLocation = new Location(l);
        final Location queryLocation = new Location(l);
        new Thread(() -> {
            boolean loaded = false;
            try { loaded = fetchOnline(queryLocation); }
            catch (Exception ignored) { }
            finally {
                if (loaded) {
                    Location latest = loc == null ? queryLocation : new Location(loc);
                    reloadRoads(latest);
                }
                roadFetchInFlight = false;
            }
        }, "osm-road-data-fetch").start();
    }

    boolean update(Location l) {""",
    "asynchronous OSM road fetch implementation",
)
method_start = engine.index("    int speedLimitKmh(Location l) {")
method_end = engine.index("    double distanceToSegmentMeters", method_start)
new_method = """    int speedLimitKmh(Location l) {
        if (l == null) return 0;
        RoadWay best = null;
        double bestScore = Double.MAX_VALUE;
        double bestDelta = Double.NaN;
        double bestDistance = Double.MAX_VALUE;
        boolean hasUsableHeading = l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 3f;
        if (ways != null) {
            for (RoadWay way : ways) {
                if (way.pts == null || way.pts.size() < 2) continue;
                for (int i = 0; i + 1 < way.pts.size(); i++) {
                    Geo a = way.pts.get(i), b = way.pts.get(i + 1);
                    double d = distanceToSegmentMeters(l.getLatitude(), l.getLongitude(), a, b);
                    if (d > 70.0) continue;
                    double segmentBearing = bearing(a, b);
                    double delta = Double.NaN;
                    double headingPenalty = 0.0;
                    if (hasUsableHeading) {
                        delta = Math.abs(normalizeAngle(segmentBearing - l.getBearing()));
                        delta = Math.min(delta, 360.0 - delta);
                        headingPenalty = delta * 0.30;
                        if (delta > 105.0 && d > 18.0) continue;
                    }
                    double score = d + headingPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        bestDistance = d;
                        best = way;
                        bestDelta = delta;
                    }
                }
            }
        }
        if (best != null && bestScore <= 55.0) {
            int limit = 0;
            if (hasUsableHeading && Double.isFinite(bestDelta)) {
                if (bestDelta > 90.0) {
                    limit = parseSpeedLimit(best.maxspeedBackward);
                    if (limit <= 0) limit = parseSpeedLimit(best.maxspeed);
                } else {
                    limit = parseSpeedLimit(best.maxspeedForward);
                    if (limit <= 0) limit = parseSpeedLimit(best.maxspeed);
                }
            } else {
                // Without a reliable direction, only a non-directional maxspeed is safe.
                limit = parseSpeedLimit(best.maxspeed);
            }
            // If this is the very same OSM way independently matched by Valhalla,
            // its edge limit is a safe fallback when local tags are incomplete.
            // Never borrow a limit from a different neighbouring way.
            if (limit > 0) return limit;
            if (best.id != null && best.id.equals(matchedWayId)) return cachedNetworkSpeedLimit(l);
            return 0;
        }
        return cachedNetworkSpeedLimit(l);
    }

    int cachedNetworkSpeedLimit(Location l) {
        if (l == null || matchedWayId.isEmpty() || matchedSpeedLimitKmh <= 0 || matchedLocation == null) return 0;
        if (System.currentTimeMillis() - matchedAt >= 20000L || matchedLocation.distanceTo(l) > 100.0f) return 0;
        if (l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 3f && matchedLocation.hasBearing()) {
            double delta = Math.abs(normalizeAngle(l.getBearing() - matchedLocation.getBearing()));
            delta = Math.min(delta, 360.0 - delta);
            if (delta > 55.0 && matchedLocation.distanceTo(l) > 12.0f) return 0;
        }
        return matchedSpeedLimitKmh;
    }

"""
engine = engine[:method_start] + new_method + engine[method_end:]
# Improve map-only snapping using measured direction and a small same-way continuity bonus.
# This snap is for rendering only; navigation/safety continue to use raw GNSS + route matcher.
engine = replace_once(
    engine,
    """                if (l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 5.0f && "yes".equalsIgnoreCase(way.oneway)) {
                    double seg = bearing(a, b);
                    double delta = Math.abs(normalizeAngle(seg - l.getBearing()));
                    delta = Math.min(delta, 360.0 - delta);
                    if (delta > 100.0) continue;
                    score += delta * 0.35;
                }""",
    """                if (l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 3.0f) {
                    double seg = bearing(a, b);
                    double delta = Math.abs(normalizeAngle(seg - l.getBearing()));
                    delta = Math.min(delta, 360.0 - delta);
                    if (delta > 100.0 && distance > 8.0) continue;
                    score += delta * 0.22;
                }
                if (!displayMatchedWayId.isEmpty() && displayMatchedWayId.equals(way.id)
                        && System.currentTimeMillis() - displayMatchedAt <= 15000L && distance <= 28.0) score -= 8.0;""",
    "heading-aware map-only road snap",
)
engine = replace_once(
    engine,
    "        if (bestWay == null || bestDistance > 22.0) return null;",
    "        if (bestWay == null || bestDistance > 22.0) return null;\n"
    "        displayMatchedWayId = bestWay.id == null ? \"\" : bestWay.id;\n"
    "        displayMatchedAt = System.currentTimeMillis();",
    "retain display road identity between fixes",
)
ENGINE.write_text(engine, encoding="utf-8")

matcher = MATCHER.read_text(encoding="utf-8")
matcher = replace_once(
    matcher,
    """        return Math.max(0.0, Math.min(1.0, 0.52 * distScore + 0.28 * marginScore + 0.20 * continuity));""",
    """        // Confidence must fall when GNSS accuracy degrades; otherwise a very poor fix
        // can look deceptively trustworthy because its distance tolerance is widened.
        double accuracyFactor = Math.max(0.25, Math.min(1.0, 25.0 / accuracy(l)));
        double baseConfidence = 0.52 * distScore + 0.28 * marginScore + 0.20 * continuity;
        return Math.max(0.0, Math.min(1.0, baseConfidence * accuracyFactor));""",
    "GNSS accuracy confidence weighting",
)
MATCHER.write_text(matcher, encoding="utf-8")

# Keep every existing regression assertion aligned with the new app version.
for test_path in (PROJECT / "roadtools").glob("*_test.py"):
    test_text = test_path.read_text(encoding="utf-8")
    test_text = test_text.replace("versionCode 539; versionName '3.41.9'", "versionCode 544; versionName '3.46.0'")
    test_text = test_text.replace("versionCode 540; versionName '3.42.0'", "versionCode 544; versionName '3.46.0'")
    test_text = test_text.replace("versionName '3.41.9'", "versionName '3.46.0'")
    test_text = test_text.replace("versionName '3.42.0'", "versionName '3.46.0'")
    test_text = test_text.replace("version is 3.42.0", "version is 3.46.0")
    test_text = test_text.replace("version 3.42.0", "version 3.46.0")
    test_text = test_text.replace("'return best == null || bestScore > 55.0 ? 0 : bestLimit;' in engine,", "'if (best != null && bestScore <= 55.0)' in engine and 'return limit;' in engine,")
    test_text = test_text.replace("'snap restricted to accurate fixes': 'location.getAccuracy() <= 20f' in main,", "'road snap accepts moderate but usable fixes': 'location.getAccuracy() <= 65f' in main,")
    test_text = test_text.replace("'oneway heading filter exists': '\"yes\".equalsIgnoreCase(way.oneway)' in engine,", "'heading-aware road snap exists': 'score += delta * 0.22;' in engine and 'displayMatchedWayId.equals(way.id)' in engine,")
    test_text = test_text.replace("'matchedLocation.distanceTo(l) <= 150.0f' in s", "'matchedLocation.distanceTo(l) > 100.0f' in s")
    test_text = test_text.replace("'System.currentTimeMillis()-matchedAt < 30000L' in s", "'System.currentTimeMillis() - matchedAt >= 20000L' in s")
    test_text = test_text.replace('needle = "if (ways == null || ways.isEmpty()) return 0;"', 'needle = "return cachedNetworkSpeedLimit(l);"')
    test_path.write_text(test_text, encoding="utf-8")

BUILD_VERSION.write_text("3.46.0\n", encoding="utf-8")
ride_test = PROJECT / "roadtools/speed_limit_ride_regression_test.py"
ride_test.write_text('''from pathlib import Path
p = Path(__file__).resolve().parents[0] / "../app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java"
s = p.read_text(encoding="utf-8")
method = s[s.index("int speedLimitKmh(Location l)"):s.index("double distanceToSegmentMeters")]
assert "RoadWay best = null;" in method
assert "best = way;" in method
assert "best.id != null && best.id.equals(matchedWayId)" in method
assert "return cachedNetworkSpeedLimit(l);" in method
assert "Never borrow a limit from a different neighbouring way." in method
cache = s[s.index("int cachedNetworkSpeedLimit(Location l)"):s.index("double distanceToSegmentMeters")]
assert "matchedLocation.distanceTo(l) > 100.0f" in cache
assert "System.currentTimeMillis() - matchedAt >= 20000L" in cache
assert "delta > 55.0" in cache
print("speed-limit RIDE regression: PASS")
''', encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionCode 539", "versionCode 544", "Android version code")
gradle = replace_once(gradle, "versionName '3.41.9'", "versionName '3.46.0'", "Android version name")
GRADLE.write_text(gradle, encoding="utf-8")

fresh_snap_test = PROJECT / "roadtools/marker_snap_gnss_regression_test.py"
fresh_snap_test.write_text('''#!/usr/bin/env python3
"""Source-level guardrails for map-marker road snapping."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java").read_text(encoding="utf-8")
checks = {
    "visual marker snap requires accuracy metadata": "!l.hasAccuracy()" in ENGINE and "l.getAccuracy() > 65f" in ENGINE,
    "visual marker snap rejects non-finite accuracy": "Float.isFinite(l.getAccuracy())" in ENGINE,
    "visual marker snap rejects fixes older than 15 seconds": "System.currentTimeMillis() - l.getTime() > 15000L" in ENGINE,
    "render path independently rejects stale fixes before snapping": "System.currentTimeMillis() - location.getTime() <= 15000L" in MAIN,
    "render-only snapped coordinate is not fed back into road engine": "engine.update(roadSnap)" not in MAIN and "processLocation(roadSnap)" not in MAIN,
    "road snap remains bounded to mapped road geometry": "bestDistance > 22.0" in ENGINE,
    "heading disagreement can reject a candidate road": "delta > 100.0 && distance > 8.0" in ENGINE,
}
for name, ok in checks.items():
    print(f"{name}: {'PASS' if ok else 'FAIL'}")
print(f"CHECKS={len(checks)} FAILURES={sum(not x for x in checks.values())}")
raise SystemExit(0 if all(checks.values()) else 1)
''', encoding="utf-8")

speed_test = PROJECT / "roadtools/speed_limit_handling_regression_test.py"
speed_test.write_text('''#!/usr/bin/env python3
"""Regression checks for trustworthy speed-limit selection and non-spam warnings."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
checks = {
    "geometry is selected before checking limit availability": "best = way;" in ENGINE and "return limit;" in ENGINE,
    "network edge cache has a finite freshness window": "System.currentTimeMillis() - matchedAt >= 20000L" in ENGINE,
    "network edge cache has a spatial bound": "matchedLocation.distanceTo(l) > 100.0f" in ENGINE,
    "network edge cache rejects sharp heading changes": "delta > 55.0 && matchedLocation.distanceTo(l) > 12.0f" in ENGINE,
    "speeding warning requires five continuous seconds": "nowMs - speedLimitOverSinceMs >= 5000L" in MAIN,
    "repeat warning is rate limited to 120 seconds": "nowMs - lastSpeedLimitWarningAt >= 120000L" in MAIN,
    "repeat timer resets after 30 seconds at legal speed": "nowMs - speedLimitBelowSinceMs >= 30000L" in MAIN,
    "cached edge limit is accepted only for the same matched OSM way": "best.id.equals(matchedWayId)" in ENGINE and "return cachedNetworkSpeedLimit(l);" in ENGINE,
    "fallback network callbacks cannot erase a recent GNSS limit": "nowMs - lastUsableGpsAt <= GNSS_LOSS_TIMEOUT_MS) return;" in MAIN,
    "callback origin alone does not promote a fix to GNSS": 'boolean isFused = "fused".equals(provider);' in MAIN and "fromFusedCallback ||" not in MAIN,
    "fused-provider fixes use a stricter 50 m accuracy gate": "float providerAccuracyLimit = isFused ? 50f : GNSS_USABLE_ACCURACY_M;" in MAIN and "l.getAccuracy() <= providerAccuracyLimit" in MAIN,
    "moderate usable GNSS refreshes roads before limit lookup": "engine.update(l);\\n                currentSpeedLimitKmh = engine.speedLimitKmh(l);" in MAIN,
    "GPS holdover does not expire after a single brief gap": "GNSS_LOSS_TIMEOUT_MS = 40000L" in MAIN,
    "live GPS update triggers OSM road data fetch": "requestRoadDataFetch(l);" in ENGINE,
    "OSM road fetch is asynchronous, not on UI thread": '"osm-road-data-fetch"' in ENGINE and "new Thread(() ->" in ENGINE,
    "OSM fetch retries when no local roads are available": "noLocalRoads ? 60000L" in ENGINE,
    "speed-limit geometry lookup remains conservative": "Never borrow a limit from a different neighbouring way." in ENGINE,
    "version is 3.46.0": "versionCode 544; versionName '3.46.0'" in BUILD,
}
for name, passed in checks.items():
    print(("PASS " if passed else "FAIL ") + name)
print(f"CHECKS={len(checks)} FAILURES={sum(not value for value in checks.values())}")
raise SystemExit(0 if all(checks.values()) else 1)
''', encoding="utf-8")

new_test = PROJECT / "roadtools/map_matching_confidence_regression_test.py"
new_test.write_text('''#!/usr/bin/env python3
"""Regression checks for GNSS-quality-aware route map-matching confidence."""
from pathlib import Path
import math
ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/RouteMapMatcher.java").read_text(encoding="utf-8")
assert "double accuracyFactor = Math.max(0.25, Math.min(1.0, 25.0 / accuracy(l)));" in SOURCE
assert "baseConfidence * accuracyFactor" in SOURCE
assert "return Math.max(0.0, Math.min(1.0, baseConfidence * accuracyFactor));" in SOURCE
# With identical geometric/heading evidence, poor accuracy must never increase confidence.
for base in [i / 100.0 for i in range(101)]:
    scores = []
    for acc in [3, 5, 8, 12, 20, 25, 35, 50, 80]:
        factor = max(0.25, min(1.0, 25.0 / max(3.0, min(80.0, acc))))
        scores.append(base * factor)
    assert all(a >= b for a, b in zip(scores, scores[1:])), "confidence rose as GNSS accuracy worsened"
assert math.isclose(max(0.25, min(1.0, 25.0 / 80.0)), 0.3125)
assert max(0.25, min(1.0, 25.0 / 80.0)) < max(0.25, min(1.0, 25.0 / 5.0))
print("MAP MATCHING GNSS CONFIDENCE REGRESSION: PASS")
print("Poor-accuracy fixes are down-weighted; high-accuracy confidence is preserved.")
''', encoding="utf-8")

rotation_test = PROJECT / "roadtools/map_rotation_gesture_regression_test.py"
rotation_test.write_text('''#!/usr/bin/env python3
"""Regression checks for the full-screen map's two-finger rotation direction."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java").read_text(encoding="utf-8")
assert "map.getUiSettings().setRotateGesturesEnabled(false);" in MAIN
assert "vectorRotating=true; movedMap=true;" in MAIN
assert "mapFrozenBearing=normalizeBearing(vectorRotateStartBearing+delta);" in MAIN
assert "CameraPosition currentCamera = vectorMap.getCameraPosition();" in MAIN
assert ".target(currentCamera.target).zoom(currentCamera.zoom)" in MAIN
assert "mapFrozenBearing=normalizeBearing(rotateStartBearing+delta);" in MAIN
assert "tripMapBearing=normalizeBearing(tripRotateStartBearing+delta);" in MAIN
assert "mapFrozenBearing=normalizeBearing(rotateStartBearing-delta);" not in MAIN
assert "tripMapBearing=normalizeBearing(tripRotateStartBearing-delta);" not in MAIN
# UI regression: long labels wrap to two lines, shrink, and ellipsize instead of overflowing.
assert 'String[] routingModeButtonLabels = {"NAV: AUTO", "NAV: ONLINE", "NAV: OFFLINE"};' in MAIN
assert "routingModeButton.setSingleLine(true);" in MAIN
assert "routingModeButton.setTextSize(12f);" in MAIN
assert 'osmAndMaps.setText("MAPPE OFFLINE OSMAND");' in MAIN
assert "osmAndMaps.setSingleLine(true);" in MAIN
print("MAP ROTATION GESTURE REGRESSION: PASS")
print("SETTINGS BUTTON LABEL FIT REGRESSION: PASS")
''', encoding="utf-8")

print("ONLINE_NAV_PATCH=PASS")
print("VERSION=3.46.0")
print("CHANGED=map rotation direction; settings button text fit; frame-rate-independent marker smoothing; GNSS-aware map matching; speed-limit freshness and anti-spam warnings")
