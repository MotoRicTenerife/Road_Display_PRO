# Architecture decision: OsmAnd as the navigation base

## Decision

Use the official OsmAnd Android source as the base application. Do not attempt to recreate its map rendering, offline data management, route engine, navigation state machine, search, or rerouting with isolated dependencies.

## Integration order

### Phase 0 — reproducible upstream baseline
- Pin a specific upstream OsmAnd commit/tag.
- Record the exact JDK, Android SDK, Gradle and native toolchain requirements.
- Build the baseline in CI before changing UI.
- Preserve the upstream license and notices.

### Phase 1 — map/navigation proof
- Install on a clean Android device.
- Download the official regional offline map from within the app.
- Confirm map rendering after a cold restart and with all network connections disabled.
- Route Adeje → Los Cristianos; verify geometry and turn instructions.
- Simulate/perform a safe route deviation and confirm rerouting.
- Save logs and test results as CI artifacts where possible.

### Phase 2 — NEXT NAVI PRO shell and visual language
- Create a custom launcher/home screen and app branding.
- Use original NEXT NAVI PRO graphics and original icons/layouts; do not copy restricted OsmAnd artwork.
- Keep the underlying map canvas, navigation engine, routing state and offline data manager owned by OsmAnd.
- Provide a custom navigation HUD as a controlled overlay: next maneuver, distance, current speed, speed limit when sourced, ETA, GPS quality.
- Preserve an easy way to enter native map/search/settings screens until custom equivalents are proven.

### Phase 3 — riding display
- Add RIDE display in portrait and landscape.
- Keep speed position stable, high contrast and suitable for sunlight.
- Add curve warnings only when backed by real road geometry and adequate location confidence.
- Never fabricate speed limits, road geometry, curve radius, grade or recommended speed.
- Navigation and map rendering must continue if the curve module fails.

### Phase 4 — release quality
- Build/crash/regression tests.
- Offline route smoke test on every relevant change.
- Device validation on Samsung S22 Ultra / Android 16.
- No “ready for road test” claim until the acceptance gates are documented as passing.

## Engineering rule

One source of truth for navigation state. The custom UI must observe/extend real OsmAnd state rather than run a second competing route engine. A failed route or unavailable map must be visible as an error, never replaced by a blank map that appears successful.
