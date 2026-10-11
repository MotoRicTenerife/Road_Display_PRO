# NEXT NAVI PRO — OsmAnd-based rebuild

**Status: upstream build verified; NEXT NAVI PRO customization is in progress.**

The official OsmAnd baseline has now compiled successfully in GitHub Actions. The next build targets ARM64 for the Samsung S22 Ultra and changes the launcher label to NEXT NAVI PRO. Map rendering and real-device navigation still require runtime validation; the baseline build alone is not proof of those tests.

The new direction is to use the official OsmAnd Android application as the navigation foundation and build NEXT NAVI PRO's own riding/driving interface around it. The previous attempt to embed isolated routing pieces in ROAD DISPLAY PRO is not the baseline for this rebuild.

## Goal

Produce one Android application with:
- a reliable, native OsmAnd map renderer;
- online and offline maps;
- real route planning, turn-by-turn guidance, voice prompts, and rerouting;
- a custom NEXT NAVI PRO home screen and ride/navigation HUD;
- portrait and landscape layouts, sunlight-readable high-contrast UI;
- motorcycle-specific curve and road alerts as an optional layer, never replacing navigation;
- honest failure states: no fake position, route, road data, or recommended speed.

## Source strategy

- Upstream source: https://github.com/osmandapp/OsmAnd
- Upstream build guide: https://www.osmand.net/docs/technical/build-osmand/
- Upstream SDK/API guide: https://osmand.net/docs/technical/osmand-api-sdk/
- This repository's prior ROAD DISPLAY PRO implementation remains available on other branches for reference only.
- The initial implementation must be a real source-based fork/derivative of OsmAnd, not another wrapper around a half-integrated routing JAR.

## Non-negotiable acceptance gates

1. Build the unmodified/pinned upstream baseline in CI.
2. Install and launch it on Android 16 / Samsung S22 Ultra.
3. Download and display the Canary Islands/Tenerife offline map.
4. Calculate a real route (initial smoke test: Adeje → Los Cristianos).
5. Follow the route, receive turn guidance, and recalculate after a deliberate deviation.
6. Repeat route and map tests with Wi-Fi and mobile data disabled.
7. Only after all gates pass, layer in the NEXT NAVI PRO interface.
8. Run build, crash, and regression tests before offering an APK for road testing.

## Important

This commit does not claim the app or APK is ready. It records the agreed architecture and creates a clean workstream so the current project is not destabilized. See `ARCHITECTURE.md` and `LICENSE_COMPLIANCE.md`.
