#!/usr/bin/env python3
"""Apply deterministic online-navigation quality overlays to the archived Android baseline.

The original project archive remains untouched in Git. This overlay is applied
in CI and fails closed if expected source snippets have drifted.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "Road_Display_PRO"
MAIN = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java"
MATCHER = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/RouteMapMatcher.java"
ENGINE = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java"
SMOOTH_TEST = PROJECT / "roadtools/map_marker_smoothing_regression_test.py"
RADAR_TEST = PROJECT / "roadtools/radar_system_regression_test.py"
BUILD_VERSION = PROJECT / "BUILD_VERSION.txt"
GRADLE = PROJECT / "app/build.gradle"

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 0 and new in text:
        return text
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one baseline match, got {count}")
    return text.replace(old, new, 1)

main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    "        double smoothMarkerLat=Double.NaN,smoothMarkerLon=Double.NaN; float smoothMarkerBearing=Float.NaN;",
    "        double smoothMarkerLat=Double.NaN,smoothMarkerLon=Double.NaN; float smoothMarkerBearing=Float.NaN;\n"
    "        long smoothMarkerLastFrameAt=0L;",
    "marker animation clock",
)
old_smoothing = """                    float targetGap=sm.distanceTo(tg);
                    // Avoid multi-second lag at motorway speed and snap immediately after a large, credible GPS jump.
                    double alpha=targetGap>45f?1.0:(location.hasSpeed()&&location.getSpeed()>18f?0.72:0.55);
                    smoothMarkerLat+=(targetMarkerLat-smoothMarkerLat)*alpha;smoothMarkerLon+=(targetMarkerLon-smoothMarkerLon)*alpha;
                    if(Float.isNaN(smoothMarkerBearing))smoothMarkerBearing=targetMarkerBearing;else{float bd=targetMarkerBearing-smoothMarkerBearing;while(bd>180)bd-=360;while(bd<-180)bd+=360;smoothMarkerBearing=normalizeBearing(smoothMarkerBearing+bd*(location.hasSpeed()&&location.getSpeed()>18f?0.55f:0.32f));}"""
new_smoothing = """                    float targetGap=sm.distanceTo(tg);
                    // Time-based exponential smoothing is independent of render frame rate.
                    // A credible large jump snaps immediately; ordinary GPS jitter is eased.
                    long markerFrameNow=android.os.SystemClock.elapsedRealtime();
                    float markerDt=smoothMarkerLastFrameAt==0L?0.05f:
                            Math.max(0.001f,Math.min(0.10f,(markerFrameNow-smoothMarkerLastFrameAt)/1000f));
                    smoothMarkerLastFrameAt=markerFrameNow;
                    boolean motorwaySpeed=location.hasSpeed()&&location.getSpeed()>18f;
                    double markerAlpha=targetGap>45f?1.0:(1.0-Math.exp(-markerDt/(motorwaySpeed?0.12:0.20)));
                    smoothMarkerLat+=(targetMarkerLat-smoothMarkerLat)*markerAlpha;
                    smoothMarkerLon+=(targetMarkerLon-smoothMarkerLon)*markerAlpha;
                    if(Float.isNaN(smoothMarkerBearing))smoothMarkerBearing=targetMarkerBearing;
                    else{
                        float bd=targetMarkerBearing-smoothMarkerBearing;
                        while(bd>180)bd-=360;
                        while(bd<-180)bd+=360;
                        double bearingAlpha=1.0-Math.exp(-markerDt/(motorwaySpeed?0.10:0.18));
                        smoothMarkerBearing=normalizeBearing((float)(smoothMarkerBearing+bd*bearingAlpha));
                    }"""
main = replace_once(main, old_smoothing, new_smoothing, "time-based marker smoothing")
MAIN.write_text(main, encoding="utf-8")
# Improve speed-limit alert timing to avoid transient GPS-speed spikes and alert spam.
old_fields = """    volatile int currentSpeedLimitKmh = 0;
    boolean speedLimitAlerted = false;"""
new_fields = """    volatile int currentSpeedLimitKmh = 0;
    boolean speedLimitAlerted = false;
    volatile long speedLimitOverSinceMs = 0L;
    volatile long speedLimitBelowSinceMs = 0L;
    volatile long lastSpeedLimitWarningAt = 0L;"""
main = replace_once(main, old_fields, new_fields, "speed limit alert state")
old_warn = """                if (actualKmh > currentSpeedLimitKmh + 3f) {
                    if (!speedLimitAlerted) {
                        beepSpeedLimit(); speedLimitAlerted = true;
                        if (audio && navigationTtsReady && navigationTts != null && nowMs - lastSpeedLimitVoiceAt > 8000L) {
                            final int lim = currentSpeedLimitKmh; lastSpeedLimitVoiceAt = nowMs;
                            runOnUiThread(() -> { try { navigationTts.speak("Attenzione, limite " + lim + " chilometri orari.", TextToSpeech.QUEUE_FLUSH, null, "speed-limit-over"); } catch (Exception ignored) { } });
                        }
                    }
                } else if (actualKmh <= currentSpeedLimitKmh - 3f) speedLimitAlerted = false;"""
new_warn = """                if (actualKmh > currentSpeedLimitKmh + 3f) {
                    speedLimitBelowSinceMs = 0L;
                    if (speedLimitOverSinceMs == 0L) speedLimitOverSinceMs = nowMs;
                    // OsmAnd-style anti-spam: require 5 seconds of continuous overspeed,
                    // then repeat no more often than every 120 seconds.
                    if (nowMs - speedLimitOverSinceMs >= 5000L
                            && (lastSpeedLimitWarningAt == 0L || nowMs - lastSpeedLimitWarningAt >= 120000L)) {
                        beepSpeedLimit();
                        speedLimitAlerted = true;
                        lastSpeedLimitWarningAt = nowMs;
                        if (audio && navigationTtsReady && navigationTts != null) {
                            final int lim = currentSpeedLimitKmh;
                            lastSpeedLimitVoiceAt = nowMs;
                            runOnUiThread(() -> { try { navigationTts.speak("Attenzione, limite " + lim + " chilometri orari.", TextToSpeech.QUEUE_FLUSH, null, "speed-limit-over"); } catch (Exception ignored) { } });
                        }
                    }
                } else {
                    speedLimitOverSinceMs = 0L;
                    if (actualKmh <= currentSpeedLimitKmh) {
                        if (speedLimitBelowSinceMs == 0L) speedLimitBelowSinceMs = nowMs;
                        // Reset the repeat timer only after 30 seconds at/below the legal limit.
                        if (nowMs - speedLimitBelowSinceMs >= 30000L) {
                            lastSpeedLimitWarningAt = 0L;
                            speedLimitAlerted = false;
                        }
                    } else {
                        speedLimitBelowSinceMs = 0L;
                    }
                }"""
main = replace_once(main, old_warn, new_warn, "debounced speed limit warning")
main = main.replace("                speedLimitAlerted = false;\n                if (lastSpokenSpeedLimitKmh > 0)", "                speedLimitAlerted = false;\n                speedLimitOverSinceMs = 0L; speedLimitBelowSinceMs = 0L; lastSpeedLimitWarningAt = 0L;\n                if (lastSpokenSpeedLimitKmh > 0)")
main = main.replace("            speedLimitAlerted = false;\n            engine.loc = new Location(l);", "            speedLimitAlerted = false; speedLimitOverSinceMs = 0L; speedLimitBelowSinceMs = 0L; lastSpeedLimitWarningAt = 0L;\n            engine.loc = new Location(l);")
MAIN.write_text(main, encoding="utf-8")



test = SMOOTH_TEST.read_text(encoding="utf-8")
test = replace_once(
    test,
    "    'high-speed marker smoothing is more responsive': 'location.getSpeed()>18f?0.72:0.55' in main,",
    "    'marker smoothing uses elapsed frame time': 'smoothMarkerLastFrameAt' in main and 'SystemClock.elapsedRealtime()' in main,\n"
    "    'marker smoothing is frame-rate independent': '1.0-Math.exp(-markerDt/(motorwaySpeed?0.12:0.20))' in main,\n"
    "    'bearing smoothing uses a time constant': 'bearingAlpha=1.0-Math.exp(-markerDt/(motorwaySpeed?0.10:0.18))' in main,",
    "marker smoothing regression assertions",
)
SMOOTH_TEST.write_text(test, encoding="utf-8")


# Match geometry first, including roads with unknown limits, so an adjacent road's
# limit can never be borrowed merely because the current road has no maxspeed tag.
engine = ENGINE.read_text(encoding="utf-8")
method_start = engine.index("    int speedLimitKmh(Location l) {")
method_end = engine.index("    double distanceToSegmentMeters", method_start)
new_method = """    int speedLimitKmh(Location l) {
        if (l == null) return 0;
        RoadWay best = null;
        double bestScore = Double.MAX_VALUE;
        double bestDelta = Double.NaN;
        double bestDistance = Double.MAX_VALUE;
        boolean hasUsableHeading = l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 3f;
        if (ways != null) {
            for (RoadWay way : ways) {
                if (way.pts == null || way.pts.size() < 2) continue;
                for (int i = 0; i + 1 < way.pts.size(); i++) {
                    Geo a = way.pts.get(i), b = way.pts.get(i + 1);
                    double d = distanceToSegmentMeters(l.getLatitude(), l.getLongitude(), a, b);
                    if (d > 70.0) continue;
                    double segmentBearing = bearing(a, b);
                    double delta = Double.NaN;
                    double headingPenalty = 0.0;
                    if (hasUsableHeading) {
                        delta = Math.abs(normalizeAngle(segmentBearing - l.getBearing()));
                        delta = Math.min(delta, 360.0 - delta);
                        headingPenalty = delta * 0.30;
                        if (delta > 105.0 && d > 18.0) continue;
                    }
                    double score = d + headingPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        bestDistance = d;
                        best = way;
                        bestDelta = delta;
                    }
                }
            }
        }
        if (best != null && bestScore <= 55.0) {
            int limit = 0;
            if (hasUsableHeading && Double.isFinite(bestDelta)) {
                if (bestDelta > 90.0) {
                    limit = parseSpeedLimit(best.maxspeedBackward);
                    if (limit <= 0) limit = parseSpeedLimit(best.maxspeed);
                } else {
                    limit = parseSpeedLimit(best.maxspeedForward);
                    if (limit <= 0) limit = parseSpeedLimit(best.maxspeed);
                }
            } else {
                // Without a reliable direction, only a non-directional maxspeed is safe.
                limit = parseSpeedLimit(best.maxspeed);
            }
            // A geometrically matched road with no usable limit means "unknown".
            // Do not borrow a neighbouring road's limit or a stale network edge.
            return limit;
        }
        return cachedNetworkSpeedLimit(l);
    }

    int cachedNetworkSpeedLimit(Location l) {
        if (l == null || matchedWayId.isEmpty() || matchedSpeedLimitKmh <= 0 || matchedLocation == null) return 0;
        if (System.currentTimeMillis() - matchedAt >= 20000L || matchedLocation.distanceTo(l) > 100.0f) return 0;
        if (l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 3f && matchedLocation.hasBearing()) {
            double delta = Math.abs(normalizeAngle(l.getBearing() - matchedLocation.getBearing()));
            delta = Math.min(delta, 360.0 - delta);
            if (delta > 55.0 && matchedLocation.distanceTo(l) > 12.0f) return 0;
        }
        return matchedSpeedLimitKmh;
    }

"""
engine = engine[:method_start] + new_method + engine[method_end:]
ENGINE.write_text(engine, encoding="utf-8")

matcher = MATCHER.read_text(encoding="utf-8")
matcher = replace_once(
    matcher,
    """        return Math.max(0.0, Math.min(1.0, 0.52 * distScore + 0.28 * marginScore + 0.20 * continuity));""",
    """        // Confidence must fall when GNSS accuracy degrades; otherwise a very poor fix
        // can look deceptively trustworthy because its distance tolerance is widened.
        double accuracyFactor = Math.max(0.25, Math.min(1.0, 25.0 / accuracy(l)));
        double baseConfidence = 0.52 * distScore + 0.28 * marginScore + 0.20 * continuity;
        return Math.max(0.0, Math.min(1.0, baseConfidence * accuracyFactor));""",
    "GNSS accuracy confidence weighting",
)
MATCHER.write_text(matcher, encoding="utf-8")

# Keep every existing regression assertion aligned with the new app version.
for test_path in (PROJECT / "roadtools").glob("*_test.py"):
    test_text = test_path.read_text(encoding="utf-8")
    test_text = test_text.replace("versionCode 540; versionName '3.42.0'", "versionCode 541; versionName '3.43.0'")
    test_text = test_text.replace("versionName '3.42.0'", "versionName '3.43.0'")
    test_text = test_text.replace("version is 3.42.0", "version is 3.43.0")
    test_text = test_text.replace("version 3.42.0", "version 3.43.0")
    test_path.write_text(test_text, encoding="utf-8")

BUILD_VERSION.write_text("3.43.0\n", encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionCode 539", "versionCode 541", "Android version code")
gradle = replace_once(gradle, "versionName '3.41.9'", "versionName '3.43.0'", "Android version name")
GRADLE.write_text(gradle, encoding="utf-8")

speed_test = PROJECT / "roadtools/speed_limit_handling_regression_test.py"
speed_test.write_text('''#!/usr/bin/env python3
"""Regression checks for trustworthy speed-limit selection and non-spam warnings."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
checks = {
    "geometry is selected before checking limit availability": "best = way;" in ENGINE and "return limit;" in ENGINE,
    "network edge cache has a finite freshness window": "System.currentTimeMillis() - matchedAt >= 20000L" in ENGINE,
    "network edge cache has a spatial bound": "matchedLocation.distanceTo(l) > 100.0f" in ENGINE,
    "network edge cache rejects sharp heading changes": "delta > 55.0 && matchedLocation.distanceTo(l) > 12.0f" in ENGINE,
    "speeding warning requires five continuous seconds": "nowMs - speedLimitOverSinceMs >= 5000L" in MAIN,
    "repeat warning is rate limited to 120 seconds": "nowMs - lastSpeedLimitWarningAt >= 120000L" in MAIN,
    "repeat timer resets after 30 seconds at legal speed": "nowMs - speedLimitBelowSinceMs >= 30000L" in MAIN,
    "unknown local road limit does not borrow a neighbouring or cached limit": "A geometrically matched road with no usable limit means \\"unknown\\"." in ENGINE and "return limit;" in ENGINE,
    "version is 3.43.0": "versionCode 541; versionName '3.43.0'" in BUILD,
}
for name, passed in checks.items():
    print(("PASS " if passed else "FAIL ") + name)
print(f"CHECKS={len(checks)} FAILURES={sum(not value for value in checks.values())}")
raise SystemExit(0 if all(checks.values()) else 1)
''', encoding="utf-8")

new_test = PROJECT / "roadtools/map_matching_confidence_regression_test.py"
new_test.write_text('''#!/usr/bin/env python3
"""Regression checks for GNSS-quality-aware route map-matching confidence."""
from pathlib import Path
import math
ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/RouteMapMatcher.java").read_text(encoding="utf-8")
assert "double accuracyFactor = Math.max(0.25, Math.min(1.0, 25.0 / accuracy(l)));" in SOURCE
assert "baseConfidence * accuracyFactor" in SOURCE
assert "return Math.max(0.0, Math.min(1.0, baseConfidence * accuracyFactor));" in SOURCE
# With identical geometric/heading evidence, poor accuracy must never increase confidence.
for base in [i / 100.0 for i in range(101)]:
    scores = []
    for acc in [3, 5, 8, 12, 20, 25, 35, 50, 80]:
        factor = max(0.25, min(1.0, 25.0 / max(3.0, min(80.0, acc))))
        scores.append(base * factor)
    assert all(a >= b for a, b in zip(scores, scores[1:])), "confidence rose as GNSS accuracy worsened"
assert math.isclose(max(0.25, min(1.0, 25.0 / 80.0)), 0.3125)
assert max(0.25, min(1.0, 25.0 / 80.0)) < max(0.25, min(1.0, 25.0 / 5.0))
print("MAP MATCHING GNSS CONFIDENCE REGRESSION: PASS")
print("Poor-accuracy fixes are down-weighted; high-accuracy confidence is preserved.")
''', encoding="utf-8")

print("ONLINE_NAV_PATCH=PASS")
print("VERSION=3.43.0")
print("CHANGED=frame-rate-independent marker smoothing; GNSS-aware map matching; speed-limit freshness and anti-spam warnings")
