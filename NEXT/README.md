# ROAD DISPLAY NEXT — Alpha 1

This is an isolated first Android scaffold. It does not overwrite the legacy project or claim feature parity with the latest Road Display PRO 3.3x work.

## Alpha 1 scope
- Native Android app, package `com.riccardo.roaddisplay`
- Portrait/landscape responsive layout
- Full-screen ride display and keep-screen-on
- Runtime location permission and native Android location updates
- Speed from a recent location fix only; accuracy and stale-fix states shown
- Explicitly labeled DEMO toggle
- Honest road-data unavailable state: no invented curves or speed limits

## Not included yet
No route planner, connected offline road graph, map renderer, turn-by-turn guidance, curve detection, speed-limit database, GPX recording/export, or production-ready brightness controller.

## Build
Requires JDK 17, Android SDK platform 35, Build Tools 35.0.0, and Gradle 8.7. From this directory run:

```sh
gradle assembleDebug
```

Expected output: `app/build/outputs/apk/debug/app-debug.apk`.

## Next milestones
1. Verify build in CI and correct any compiler/SDK issues.
2. Add unit tests for GPS freshness/accuracy and demo isolation.
3. Inspect the archived PRO ZIP/source tree and migrate proven existing modules.
4. Select and license-review the map renderer and offline dataset.
5. Implement road graph, smooth map matching, route engine and route-aware alerts.
