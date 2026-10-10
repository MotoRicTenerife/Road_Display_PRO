# OsmAnd engine dependency audit

Date: 2026-10-10
Upstream revision audited: `osmandapp/Osmand@cacaa5fe1499af8806210de6f81293f925847043`
Status: source/build-file audit only; no engine code imported and no runtime route calculation demonstrated.

## Findings confirmed from upstream source

1. `OsmAnd-java` is a Java 17 module, not a standalone drop-in Android library. Its Gradle build depends on `:OsmAnd-shared` and external libraries including commons-logging, Gson, JSON, commons-compress, junidecode, JTS, Open Location Code, ScribeJava, kxml2, plus bundled Trove and patched ICU jars.
2. The `androidJar` task is not simply a prebuilt dependency: it builds the Java classes and includes resources, excluding the desktop `PlatformUtil`. Its normal build requires collected external resources from routing, abbreviations, rendering styles, POI/OSM resource trees and a downloaded `regions.ocbf`. Those relative paths assume the complete OsmAnd source tree.
3. `:OsmAnd-shared` is a Kotlin Multiplatform module. The audited JVM build declares Kotlin 2.0.0 plugins and dependencies including serialization, coroutines, datetime, Okio, Stately, Ktor, SQLite JDBC, kxml2 and commons-logging. It is a separate compatibility surface, not just a Java helper jar.
4. The audited root settings include `:OsmAnd`, `:OsmAnd-java`, `:OsmAnd-api`, `:OsmAnd-telegram`, `:OsmAnd-shared` and plugins. The API module is an AIDL/library module and is not the routing engine.
5. The routing entry point `RoutePlannerFrontEnd` constructs a `RoutingContext` from `RoutingConfiguration`, `NativeLibrary` and `BinaryMapIndexReader[]`, and returns `RouteCalcResult` from route search. Therefore a functional route calculation needs the compatible map reader, routing configuration/resources, map files and lifecycle—not merely a UI/API module.
6. `NativeLibrary` exposes native-backed methods as well as Java routing types. We must identify which call paths are mandatory for the chosen routing mode and ensure native libraries exist for each shipped ABI; compiling Java sources alone does not prove those calls work.
7. Upstream `OsmAnd-java/build.gradle` targets Java 17. The audited OsmAnd version catalog sets compileSdk/targetSdk 36 and minSdk 24. ROAD DISPLAY PRO's current CI uses Gradle 8.7 and Android SDK 35, so directly importing the current full OsmAnd build risks an incompatible toolchain jump.
8. Offline map data uses OsmAnd's `.obf` format. The app must validate that the selected file includes the routing section required by the route planner; ordinary map tiles or a display-only vector map are not sufficient evidence of routability.
9. The upstream repository's main code is GPLv3, while some artwork/resources have separate licenses. Any distributed integration needs a license/provenance review and the applicable source/notices; do not copy the UI or artwork wholesale.

## Integration decision

Do **not** add `OsmAnd-api` as a substitute for the engine and do not add a speculative Maven/Ivy coordinate until the exact artifact, version, contents, transitive dependencies and license are verified.

The safest next implementation unit is an isolated engine adapter with a narrow contract:
- open/close a validated `.obf` routing dataset;
- calculate a route from explicit start/end coordinates using an explicit profile/configuration;
- return immutable route geometry and maneuver/segment metadata;
- report missing map, missing routing index, cancellation, invalid endpoints and calculation failure as typed errors;
- run route calculation off the UI thread;
- keep GPS filtering, route progress and display-marker interpolation as separate layers.

## Required gates before merging

- Reproducible build of the isolated adapter against the pinned upstream revision.
- Test fixture containing a known-good routable `.obf` region, with license/source documented.
- Deterministic tests: successful route, no routing data, endpoints outside coverage, cancellation, malformed/unsupported map, route geometry continuity and maneuvers.
- APK ABI inspection and a runtime smoke test for every native entry point actually used.
- Existing ROAD DISPLAY PRO regression suite remains green.
- No claim of offline navigation until route calculation succeeds from installed local data without network.

## Important limitation

This audit confirms architectural/build constraints from source; it does not prove the project can currently compile OsmAnd as a dependency, that a compatible prebuilt artifact is available, or that a route can yet be calculated in ROAD DISPLAY PRO.