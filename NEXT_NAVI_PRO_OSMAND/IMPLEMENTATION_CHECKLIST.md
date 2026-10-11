# Implementation checklist

## Bootstrap
- [ ] Pin official OsmAnd source revision and record it in `UPSTREAM_REVISION.md`.
- [ ] Build upstream baseline in CI without project modifications.
- [ ] Archive build logs and exact toolchain versions.
- [ ] Confirm debug APK installs and launches on Android 16.

## Functional navigation
- [ ] Map visible after cold start.
- [ ] Offline map download for Canary Islands/Tenerife succeeds.
- [ ] Map visible after force-stop/restart with network disabled.
- [ ] GPS marker follows position without lane/road jumps beyond normal GNSS uncertainty.
- [ ] Adeje → Los Cristianos route calculates.
- [ ] Maneuver list and voice guidance agree with route geometry.
- [ ] Deliberate route deviation triggers rerouting.
- [ ] No network calls required for the offline test.
- [ ] Clear diagnostics for missing map, invalid route, and weak GPS.

## NEXT NAVI PRO UI
- [ ] Original branded splash/home screen.
- [ ] Custom navigation HUD overlay.
- [ ] RIDE mode and DEMO mode remain distinct.
- [ ] Portrait and landscape layouts.
- [ ] Speed-limit badge does not overlap map/HUD.
- [ ] Curve alerts use real data and can be disabled independently.
- [ ] Accessibility, sunlight contrast, and large touch targets.

## Release gate
- [ ] CI build passes.
- [ ] Unit/instrumentation/regression tests pass.
- [ ] Cold start, offline route, and reroute tests pass.
- [ ] Crash log review completed.
- [ ] License/attribution audit completed.
- [ ] Only then create a test APK for the user.
