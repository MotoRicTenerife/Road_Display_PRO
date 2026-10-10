# ADR-001: OsmAnd engine integration for ROAD DISPLAY PRO

Date: 2026-10-10
Status: Accepted for investigation; engine integration is NOT yet implemented.
Branch: `next-navi-pro/osmand-engine-integration`

## Decision

Do not replace the existing Ride HUD or pretend that copying a few location algorithms is equivalent to OsmAnd. Evaluate and integrate the smallest end-to-end OsmAnd navigation core that can provide real route calculation, road/route geometry, and maneuver data to ROAD DISPLAY PRO. Preserve the existing app as the host shell and keep the current branch/build usable while the integration is developed separately.

No production APK is to be labelled "OsmAnd integrated" until it contains the real engine, required map data can be loaded, and a route can be calculated and followed in an automated integration test.

## Why this is not a one-line dependency

OsmAnd is a multi-module Android application with Java routing/search components, shared resources and Android-specific integration, plus native/core components used for high-performance map handling. The usable boundary must be determined from the exact upstream revision, Gradle configuration, JNI/native ABI requirements, Android SDK levels, and map-file format. Pulling only an API module would not supply the navigation engine.

## Scope boundary

Keep in ROAD DISPLAY PRO:
- motorcycle Ride HUD and its portrait/landscape layouts;
- large speed display, known-only speed limit, curve warnings and sound cues;
- trip recording, settings, brightness behavior and safety/status presentation.

Replace incrementally where justified by the upstream engine:
- route calculation and route geometry;
- route-progress / off-route detection and recalculation;
- road/turn data derived from the active route;
- location-quality and navigation-location policy where the engine provides a suitable component.

Keep display interpolation strictly render-only. It must never feed predicted coordinates back into routing, speed-limit selection, route matching or trip recording.

## Required investigation before importing upstream source

1. Pin a specific OsmAnd commit and record the upstream license and file-level provenance.
2. Inventory the exact modules/classes required for route calculation, map access, route following and maneuver data; exclude UI/assets not needed by the motorcycle HUD.
3. Compare minSdk/compileSdk, Gradle/AGP, Java/Kotlin, native ABIs, dependency licenses and map-data compatibility with the current project.
4. Decide whether the required code can be distributed in this project under GPLv3 obligations. Do not copy CC-BY-NC-ND artwork or separately licensed assets without permission. Publish corresponding source and notices as required if distribution proceeds.
5. Prototype the engine in an isolated module/branch first. Avoid destabilizing the existing build or silently changing the app's package/signing.

## Offline data decision

A working offline route engine requires compatible downloaded routing/map data and a lifecycle for installation, versioning, storage, updates and missing-region errors. A map display or cached tiles alone are not offline navigation. The app must report unavailable route data honestly and must not fabricate roads, speed limits, turns or ETAs.

## Online/offline behavior contract (added 2026-10-10)

The app must distinguish three independent capabilities instead of a single ambiguous "map available" flag:

1. **Live Ride HUD without downloaded offline maps**: GPS speed and sensor values can still be shown. Online road attributes may be available only when the current network-backed road-data service returns verified data. If no trustworthy road data is returned, speed limit / road name / turn data must remain unknown; do not infer them from cached tiles or a nearby parallel road.
2. **Online route navigation without offline maps**: technically possible only when a real online routing backend is configured and reachable, and the returned route geometry plus maneuvers are parsed and followed. This is not yet implemented/verified as an end-to-end OsmAnd integration in ROAD DISPLAY PRO. It requires internet connectivity and must report loss of service clearly.
3. **Offline route navigation**: requires a compatible downloaded routing dataset that the selected engine can actually read. Raster tiles, a basemap, a raw OSM PBF, or a catalog entry alone do not satisfy this requirement. Missing/invalid/old data must block offline route calculation with an explicit status.

### Required routing mode policy

- Prefer an explicitly selected mode: **Online**, **Offline**, or **Automatic**.
- In **Online**, do not silently claim offline fallback. If network routing fails, retain the current route only while its data remains valid and clearly report that recalculation is unavailable.
- In **Offline**, never make network calls for route calculation; fail clearly if compatible local routing data are missing.
- In **Automatic**, use offline routing only when a compatible local region is installed and validated; otherwise use online routing only if a real backend is configured and reachable. If neither is available, do not start turn-by-turn navigation.
- GPS position and the speed display may continue in all modes; road-dependent guidance must not be fabricated.
- Keep road snapping / display interpolation separate from the authoritative route-following coordinate.

### Current no-offline-map answer

Without offline maps, the current app can still launch and show GPS/sensor information, and some existing online road-data features may work when their services return valid data. **It cannot yet be represented as a fully verified turn-by-turn navigator without offline maps**, because a complete online route calculation + maneuver-following path has not been validated in this branch. A future online mode can work without downloaded maps, but only with a working online routing backend and connectivity. Offline navigation will still require compatible downloaded route data.

## Automated gate before asking for a road test

- Clean Android build and installable debug APK.
- Existing regression suite passes.
- Deterministic route replay covers: jitter, stale and inaccurate fixes, GNSS loss/recovery, tunnel, parallel carriageways, roundabouts, sharp bends, off-route/re-route, arrival and unknown speed limits.
- Tests assert routing location and render marker are separate, route progress does not move backwards on isolated bad fixes, and unknown road attributes remain unknown.
- App startup, permission denial, no network, no map data, interrupted route calculation and process recreation do not crash.
- Artifact integrity and APK metadata are checked in CI.
- Field test is requested only after these gates pass, as one planned session with a fixed checklist. Device/road behavior remains unverified until tested on the S22 Ultra.

## Current status

ROAD DISPLAY PRO 3.46.0 compiles in CI and existing Python regression checks pass. That is a build/static-test result only. The complete OsmAnd engine has not been integrated; no S22 Ultra execution or road validation has been performed. This ADR is a plan and must not be presented as evidence of implementation.
