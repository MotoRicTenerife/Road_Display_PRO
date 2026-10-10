# ROAD DISPLAY NEXT — Architecture and Migration Plan

Status: planning baseline, created on the isolated branch `next/architecture-baseline`.
The `main` branch is intentionally left untouched.

## Product goal

Build a dependable Android motorcycle navigation/display application for a handlebar-mounted phone, prioritizing smooth positioning, trustworthy road data, offline operation, sunlight readability, and non-repeating road alerts.

Application identity carried forward:
- Existing package: `com.riccardo.roaddisplay`
- Existing project: `Road_Display_PRO`
- Target test device: Samsung Galaxy S22 Ultra
- Primary road-data test area: Tenerife, Canary Islands

## Baseline facts

The existing README describes the 3.0.0 baseline as a native Android app with GPS, RIDE/DEMO screens, audio warnings, OSM/Overpass road geometry, and an SQLite cache of previously acquired geometry. It explicitly does **not** contain a complete offline road graph for the Canary Islands. This limitation must remain visible until a real offline dataset is installed and tested.

The current root Gradle build declares Android Gradle Plugin 8.5.2. Earlier development attempts encountered Android SDK/Gradle compatibility and CI setup failures; a successful build must be demonstrated by an actual CI result or local build log, not assumed.

## Required architecture

Keep responsibilities separated so each subsystem can be tested independently:

1. **app-ui** — RIDE/DEMO, portrait/landscape, large speed readout, curve symbol and limit badge layout.
2. **location** — Android location provider, accuracy and freshness checks, bearing/speed validation, lifecycle-safe updates.
3. **positioning** — filtering and map-matching to plausible road segments; smooth marker animation without hiding uncertainty or fabricating a position.
4. **map-renderer** — offline-capable vector map rendering and stable camera behavior.
5. **road-data** — indexed local road graph, address/name search, road classes, speed limits, intersections, roundabouts and exits.
6. **routing** — route calculation, route progress, deviation detection, recalculation and arrival termination.
7. **curve-engine** — route-aware geometry ahead, curvature classification, turn/exit distinction and confidence.
8. **alerts** — deduplicated audio/visual announcements with cooldown and route-event identity.
9. **offline-data** — licensed regional dataset import/download, versioning, integrity checks and storage management.
10. **trip-recorder** — persistent trip data, storage estimates, deletion and GPX export.
11. **settings** — brightness handling, display orientation/layout, audio and map contrast.
12. **test-harness** — unit, integration, regression, stress and crash tests.

## Open-source integration policy

OsmAnd should be evaluated as a source of proven navigation/rendering approaches and potentially reusable components. Do not copy code indiscriminately. Before integrating any component, record its exact upstream repository/revision, file paths, license, copyright notices, modifications, dependency compatibility and distribution obligations.

OsmAnd core is commonly distributed under GPL-family licensing; the exact license of each candidate component and the obligations for the combined application must be verified before copying or linking it. If obligations are incompatible with the intended distribution, use a compatible library or implement the behavior independently. Keep attribution and required license texts in the repository.

OpenStreetMap data licensing (ODbL) is separate from application-code licensing. Do not bulk-prefetch the public OSM raster tile server. For offline Tenerife maps, use a legally distributable OSM-derived dataset/provider and document attribution, update date, coverage and import process.

## Non-negotiable behavior

- Never fabricate GPS, road geometry, road names, speed limits, turns or offline availability.
- Distinguish poor GPS accuracy from missing road data.
- Smooth marker movement visually while retaining raw fixes and confidence for diagnostics.
- Curves along the same road are not turns.
- Deduplicate route alerts; warn ahead of exits and actual turns, not repeatedly for the same event.
- Announce the end of a speed limit and a newly applicable limit when supported by reliable road data.
- A completed route must stop navigation after arrival.
- Map contrast option: white background and black roads.
- Auto-brightness thresholds requested by the user should be reduced to half the previous values; preserve safe manual override/restore behavior.
- Never cover the speed-limit badge or navigation/curve information in either portrait or landscape.
- Keep RIDE glanceable: no keyboard or complex interactions while moving.

## Implementation order

### Phase 0 — Baseline and reproducibility
- Preserve `main`; capture the current source tree, Gradle configuration, permissions, dependencies and CI status.
- Establish a reproducible build and a list of known failing tests/build errors.
- Confirm which version is the true latest implementation before migrating; do not treat the old README version as proof that it matches later 3.3x work.

### Phase 1 — Smooth and trustworthy positioning
- Audit location update frequency, stale-fix handling, bearing/speed noise, map camera updates and marker rendering.
- Separate position filtering from visual interpolation.
- Add tests for stationary drift, low accuracy, sparse fixes, abrupt jumps, heading changes and parallel roads.
- Compare candidate approaches against OsmAnd source/behavior without importing code until licensing is reviewed.

### Phase 2 — Real offline road graph and map
- Choose a legally compatible vector renderer and regional dataset pipeline.
- Import Tenerife road geometry into an indexed, versioned offline database.
- Test road snapping and address search on parallel roads, junctions, roundabouts, tunnels and rural roads.

### Phase 3 — Route-aware navigation and alerts
- Implement route calculation, progress, off-route detection, recalculation and arrival.
- Introduce stable event IDs and stateful alert lifecycle to prevent repeated announcements.
- Separate curve-on-road geometry from turn/exit instructions.
- Handle speed-limit end/new-limit events only when supported by reliable data.

### Phase 4 — Motorcycle UI and reliability
- Fix portrait/landscape layout and overlap regression tests.
- Implement the requested white/black map mode and brightness behavior.
- Run GPS, route, map, audio, layout, stress and crash tests.

### Phase 5 — Release gate
- Verify clean CI build, APK artifact, install/launch, permissions and real-device smoke tests.
- Publish only with documented known limitations and a reproducible build.
- Do not claim field-tested behavior until tested on the Tricity route.

## Acceptance criteria

- Marker motion appears smooth without jumping across parallel roads in the defined test traces.
- Uncertain or unavailable data is visibly reported instead of guessed.
- A full Tenerife offline dataset is confirmed present before claiming full offline navigation.
- The same route event is not announced repeatedly.
- Turns/exits, roundabouts and curves on the same road are classified separately.
- Portrait and landscape have no overlapping critical labels.
- CI build and regression tests have recorded results.
