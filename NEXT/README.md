# NEXT NAVI — Alpha 2 (GPS test build)

This branch now produces an installable Android debug APK for real-device GPS testing. It is an early engineering build, not yet a complete navigation app.

## Included in Alpha 2
- App name and launcher label: **NEXT NAVI**
- Full-screen, keep-screen-on ride display
- Live Android location updates with permission handling
- Current speed only when a recent speed-bearing fix exists
- GPS state: waiting, stale, inaccurate, or good
- Display of horizontal accuracy, fix age, latitude/longitude, and bearing when Android provides it
- Clearly marked DEMO toggle, with simulated values separated from live GPS
- No invented road geometry, speed limits, turns, or curve warnings
- Automated unit tests for GPS freshness and accuracy classification

## Not included yet
The APK is not yet a full navigator: no map renderer, offline Tenerife road graph, destination search, route calculation, map matching, rerouting, turn-by-turn instructions, production curve engine, GPX trip recorder/export, or automatic brightness controller.

## Build
Requires JDK 17, Android SDK platform 35, Build Tools 35.0.0, and Gradle 8.7.

```sh
gradle testDebugUnitTest assembleDebug
```

Expected APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Get the APK
Open the [NEXT NAVI GitHub Actions workflow](https://github.com/MotoRicTenerife/Road_Display_PRO/actions/workflows/build-next.yml), open the latest successful run on `next/architecture-baseline`, then download artifact `next-navi-alpha2-debug`. The ZIP contains `app-debug.apk`.

## Device test checklist
1. Install the APK on the Samsung S22 Ultra.
2. Grant precise location permission and test outdoors.
3. Check whether the displayed coordinates follow the actual location and whether the reported accuracy matches Android's fix.
4. Compare displayed speed with the bike display/GPS app; do not treat it as certified speed.
5. Toggle DEMO and verify the UI explicitly marks simulated values.
6. Turn location off/on, revoke permission, background/resume the app, and check for stale data or crashes.
7. Repeat in portrait and landscape.

## Next development phase
1. Recover and inspect the latest available 3.3x project source rather than discard proven modules.
2. Migrate tested existing features into this build.
3. Implement smooth positioning/map matching, route graph and route recalculation.
4. Integrate a licensed map renderer and real offline Tenerife data.
5. Add route-aware curve/turn alerts and persistent trips/GPX.
6. Run regression, stress and crash tests before field installation.

No safety recommendation is made by this Alpha build. Do not use it as a substitute for a proper navigation system while riding.
