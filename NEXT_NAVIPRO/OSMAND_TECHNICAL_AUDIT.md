# OsmAnd technical audit for ROAD DISPLAY PRO

Date: 2026-10-10  
Target branch: `next-navi-pro/integration-baseline`

This is a source-level engineering review, not a claim that OsmAnd has been integrated or that ROAD DISPLAY PRO has been device-tested. The goal is to port the relevant *behaviour* into our existing architecture while keeping the motorcycle HUD.

## Primary sources inspected

- `OsmAnd/src/net/osmand/plus/OsmAndLocationProvider.java` — current source SHA `8c71e09caae769e7ada19afdb20d9fae6eeeee80`.
- `OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/SpeedometerWidget.java` — current source SHA `6c0723b4e285846312b5b8a47c0395d3f8eef51e`.
- `OsmAnd/src/net/osmand/plus/routing/RouteCalculationResult.java` — current source SHA `8b574d13b9e65aef481c287518ec483763f2b602`.
- OsmAnd licence: https://github.com/osmandapp/Osmand/blob/master/LICENSE

## Findings and what they mean for our app

### 1. GPS is a lifecycle, not a single accuracy threshold

In `OsmAndLocationProvider`, location acquisition, last-GPS-fix time, last-general-location time, routing position, loss notification and recovery are tracked separately. Notable current constants include an 18-second lost-location check, a 12-second interval during which a recent GPS fix prevents switching to network location while following, and a 50 m accuracy policy for GPX/routing. These are OsmAnd policy choices, not universal truths to copy verbatim.

Relevant behaviours:
- The fused provider can emit network-derived positions. In GPS-only/following mode, OsmAnd rejects some inaccurate fused results rather than letting them overwrite the current navigation state.
- Network location is managed separately from the GPS/fused path.
- Loss notification is delayed; recovery cancels the lost state and schedules another check.
- Routing receives a separately prepared position through `setLocationForRouting`, rather than every incoming sample being blindly treated as equally trustworthy.
- Tunnel simulation is a deliberate special case tied to route/tunnel information. It is not general permission to extrapolate the marker whenever GPS disappears.

**ROAD DISPLAY PRO action:** maintain separate timestamps/state for raw GNSS, fused/network fallback, usable routing fix, and GPS-loss/recovery. A stale or weak sample must not reset the good-fix clock. Network/passive updates must not pull the marker to a parallel road while a recent credible GNSS fix is available. A GPS-loss grace period may keep the UI calm, but must not fabricate a new position or keep presenting stale road data as current.

### 2. Smooth rendering must not become fake navigation data

OsmAnd separates provider updates and routing state from map/UI updates; its sensor and UI work is also not all performed in the main thread. The source does not justify copying an arbitrary smoothing coefficient and assuming the problem is solved.

**ROAD DISPLAY PRO action:** retain two explicit values:
- **measurement/navigation fix** — timestamped Android location with accuracy, used for map matching, route deviation, speed-limit lookup, trip recording and safety warnings;
- **render-only marker** — interpolated short-term display point, never written back into the fix and never used to generate a road decision.

Use elapsed-time-based interpolation (not a fixed alpha per frame), shortest-angle bearing interpolation, a strict maximum prediction horizon, and stop prediction when the fix ages out or accuracy deteriorates. Do not smooth across a credible large discontinuity by slowly dragging the marker across buildings/roads. Map matching should be stable because of candidate confidence and temporal continuity, not because the icon is visually lagged.

### 3. “GPS lost” and “GPS weak” are different states

OsmAnd keeps loss/recovery timing and uses an accuracy policy for routing. Its 18 s loss notification is not evidence that a 100–120 m fix is good enough for every use. For a motorcycle HUD, we need different quality gates for:
- display continuity,
- map/road candidate search,
- route-following and off-route decisions,
- curve/radar/turn warnings,
- trip telemetry.

**ROAD DISPLAY PRO action:** use a small explicit quality state machine (GOOD / DEGRADED / LOST / RECOVERING) based on fix age, accuracy, provider, speed plausibility and temporal continuity. Give each consumer its own permitted quality. For example, a degraded fix may trigger a road-data refresh but must not by itself cause a lane switch, a turn announcement or a confident legal-speed-limit display. Recover only after a credible fix; avoid flickering the UI on one bad sample.

### 4. Speed limit is a road-data result, not a speedometer decoration

In `SpeedometerWidget`, the limit is obtained through `getSpeedLimitInfo()`, which checks waypoint/routing alarm data and then the current route segment/road object. Display/alert behaviour is tied to the availability of that data and user warning mode. That is an important architectural difference: OsmAnd has a routing graph and road objects to query, while our current path combines locally fetched OSM ways and online matched edges.

**ROAD DISPLAY PRO action:** trace and test the entire data chain:
1. did the road data fetch actually run and succeed?
2. does the local road set cover the current point?
3. which candidate road was matched, at what distance and heading score?
4. is its `maxspeed` directional or non-directional?
5. is the online edge match the same road/way and still fresh?
6. was the result discarded by location quality, timeout, or UI state?

Never borrow a limit from a nearby but different road just because the matched road has no limit tag. If direction is uncertain, do not guess between `maxspeed:forward` and `maxspeed:backward`. Keep “unknown” distinct from “no limit.” Add logs/telemetry for each rejection reason, without showing fabricated limits to the rider.

### 5. Map matching and routing should not be conflated

OsmAnd's routing code works with route segments/route data objects and a following-mode lifecycle. Its location provider prepares the position that routing consumes. A display marker snapped to the nearest visible road is not, by itself, a route matcher.

**ROAD DISPLAY PRO action:** for each GNSS sample, score candidate road segments using lateral distance relative to reported accuracy, heading difference only when heading is meaningful, route progress/continuity, road direction/access constraints and time continuity. Apply hysteresis: a single noisy sample must not switch to an adjacent carriageway. If no candidate has adequate confidence, keep the raw fix, mark the road match uncertain, and avoid turn/reroute decisions until enough evidence arrives. Route deviation needs a threshold relative to GNSS accuracy and persistence over multiple samples.

### 6. What cannot be copied as a single snippet

- `OsmAndLocationProvider` depends on OsmAnd's application, settings, routing helper, service helpers, simulation provider and custom `net.osmand.Location` type.
- `SpeedometerWidget` depends on OsmAnd's `AlarmInfo`, `WaypointHelper`, routing segments, settings and region-specific rendering.
- `RouteCalculationResult` depends on OsmAnd's route graph, route segments and routing engine.

Copying one method without its dependency model will not deliver OsmAnd's behaviour and is likely to produce fragile glue code. The useful unit to port is the behaviour with explicit inputs, outputs and tests, or a properly integrated library/component with its dependencies and licence obligations understood.

## Proposed implementation sequence in ROAD DISPLAY PRO

1. **Location quality state machine** — unit-test stale timestamps, weak/strong fixes, fused/network contamination, loss grace, recovery and provider changes.
2. **Render-only marker interpolator** — deterministic elapsed-time math tests; cap dead reckoning; prove its output never reaches trip DB, matcher or safety engine.
3. **Road candidate matcher** — synthetic tests for parallel carriageways, junctions, roundabouts, U-turns, inaccurate fixes and GPS jumps; expose match confidence and rejection reason.
4. **Speed-limit pipeline diagnostics** — trace OSM fetch, parse, candidate way ID, direction, source, age and distance; tests for directional tags, absent tags, stale online matches and adjacent roads.
5. **Route-following state** — persistent off-route evidence, controlled recalculation, clear arrival terminal state, no repeated instructions after arrival.
6. **HUD integration** — preserve existing layout, auto-brightness behaviour, curve warnings, radar and audio; only display speed limits when the source result is valid.
7. **Quality gates** — run all existing `roadtools/*_test.py` checks, add the cases above, build the APK in CI, then test on the S22 Ultra. Static tests are not a substitute for the actual ride test.

## Licensing and reuse decision

OsmAnd's repository states that its code is GPLv3, while UI/artwork and some resources have separate licences. “Open source” does not mean “unrestricted” or “safe to conceal.” I will not help disguise copied code or evade attribution/licence obligations. We can either:
- implement the described behaviours independently in ROAD DISPLAY PRO and cite OsmAnd as the technical reference; or
- deliberately reuse a component under its applicable licence, preserve copyright/licence notices and meet the obligations for the way the app is distributed. The exact compatibility and distribution path should be checked before shipping.

This audit intentionally does not copy OsmAnd UI artwork or claim that code from OsmAnd has already been inserted into the APK.
