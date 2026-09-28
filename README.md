# ROAD DISPLAY PRO 3.0.0

Package: `com.riccardo.roaddisplay`

A native Android motorcycle display designed for a handlebar-mounted phone. The current 3.0.0 project contains a real native GPS layer, a vector ride display, DEMO controls, audio warnings, online OSM road geometry acquisition and a local SQLite road-geometry cache.

## Important status / honesty statement

This release does **not** claim to contain a complete offline map of the Canary Islands. No fabricated road data is bundled. Offline curve detection works for road geometry that has actually been acquired and stored in the local database. If no local road geometry is available, the app reports `DATI STRADALI NON DISPONIBILI` rather than inventing a curve.

The online provider currently uses an Overpass API endpoint to obtain OSM road ways around the current GPS position. The local cache stores their geometry in SQLite. This is deliberately separate from the UI so a future dedicated offline provider can replace it.

OpenStreetMap data is used under the ODbL. Do not bulk-download the standard OSM raster tile server for offline use; OSMF's tile policy explicitly prohibits prefetch/bulk offline use of `tile.openstreetmap.org`. For a full regional offline package, use an OSM-derived dataset/provider that permits offline distribution or self-host the required data.

## Current features

- RIDE is the default mode.
- DEMO can be opened from the RIDE screen.
- Portrait and landscape layouts.
- Native Android GPS using `LocationManager`.
- Speed, GPS accuracy/state and road-data state.
- Road geometry from OSM/Overpass when online.
- Local SQLite cache for previously acquired road geometry.
- Curve classification: LEGGERA / MEDIA / STRETTA / TORNANTE.
- Left/right mirrored vector graphics.
- Warning triangle for tight curves and hairpins.
- Professional arrow: black outline + yellow fill.
- Audio: 1 / 2 / 3 short beeps; hairpin = long + 2 short.
- 150 m crossing threshold with re-arm when the curve moves back beyond 150 m.
- Keep-screen-on and immersive fullscreen.
- No fake recommended speed when no usable radius is available.
- Demo sliders for speed, distance, grade and recommended speed.

## What is intentionally not claimed yet

- No validated safety-certified recommended-speed algorithm.
- No complete Canary Islands offline road database bundled in the APK.
- No turn-by-turn navigation UI.
- No elevation database bundled. OSM road geometry normally does not provide reliable elevation for this purpose, so RIDE does not fabricate grade data.
- No claim that an OSM way is always the exact continuation through an intersection; a future graph/stitching layer should improve this.

## Build without a PC

### Option A: AndroidIDE on the phone

AndroidIDE can open and build Gradle-based Android projects directly on Android devices. The original AndroidIDE project is archived, so use it only as a practical build tool and obtain it from a trusted source. The project is deliberately dependency-light and uses classic Android APIs to maximize compatibility.

1. Install AndroidIDE from a trusted source.
2. Install its JDK/SDK/build tools.
3. Copy/extract this project on the phone.
4. Open the project folder.
5. Let Gradle sync.
6. Run/build `assembleDebug`.
7. Install the generated APK.

### Option B: GitHub Actions (online build)

This project includes `.github/workflows/build-apk.yml`.

You can create a GitHub repository from your phone, upload the project files, and push them to `main`. GitHub Actions will build the debug APK in the cloud and publish it as a workflow artifact. This is the easiest PC-free route if AndroidIDE gives trouble.

The APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions artifact is named:

`road-display-pro-3.0.0-debug`

## Android permissions

The app requests location permission because curve anticipation depends on the current GPS position. Internet permission is required for online OSM road geometry retrieval.

## DEMO controls

Tap the DEMO area from RIDE. In DEMO:

- tap the upper area to cycle curve type;
- tap the direction area to switch left/right;
- use the four lower horizontal controls for speed, distance, grade and recommended speed.

All values are marked/sourced as simulated. DEMO must never be interpreted as a real safety recommendation.

## Online / offline behaviour

Online:

`GPS -> current position -> Overpass -> OSM road geometry -> curve engine -> local SQLite cache -> display/audio`

Offline:

`GPS -> current position -> local SQLite cache -> curve engine -> display/audio`

If the local database has no usable road geometry, the app displays `DATI STRADALI NON DISPONIBILI`.

## OSM / offline data strategy

Geofabrik publishes a current Canary Islands OSM extract in PBF format. A PBF is an excellent source for a future full offline database, but it is not copied blindly into the APK. A production offline package should preprocess the PBF into a compact road graph/SQLite format containing only the geometry and tags required by ROAD DISPLAY PRO.

For the first usable release, automatic online acquisition + local caching avoids shipping fabricated or stale road data. The architecture leaves the database/provider boundary ready for a prebuilt Canary Islands road package.

## Recommended next engineering phase

1. Replace individual OSM ways with a connected road graph.
2. Stitch ways at intersections and preserve directionality.
3. Add a proper offline PBF/SQLite preprocessing pipeline.
4. Add an elevation source/database for grade.
5. Improve curve segmentation and radius fitting with smoothing.
6. Add route-aware look-ahead and S-curve handling.
7. Validate the recommended-speed algorithm against real road geometry before presenting it as anything more than informational.
8. Add a dedicated offline package for Tenerife/Canary Islands generated from a dated OSM extract.

## Testing checklist

### DEMO
- 4 curve classes
- left/right
- speed 0–180
- distance 0–500 m
- grade negative/zero/positive
- recommended speed
- audio threshold
- portrait/landscape

### GPS
- permission
- GPS disabled
- weak accuracy
- recovery
- speed
- road data available/unavailable

### Online
- road retrieval
- cache creation
- connection loss without crash

### Offline
- disable Wi-Fi and mobile data
- previously cached road geometry remains usable
- uncached road shows `DATI STRADALI NON DISPONIBILI`

## Release signing

For a personal test APK, the debug build is sufficient. For a distributable release APK, create and protect your own keystore and configure Gradle signing. Never put a private production keystore or its passwords into a public repository.
