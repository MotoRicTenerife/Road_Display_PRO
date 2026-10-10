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

radar_test = RADAR_TEST.read_text(encoding="utf-8")
radar_test = replace_once(radar_test, "versionName '3.41.9'", "versionName '3.42.0'", "radar test version")
RADAR_TEST.write_text(radar_test, encoding="utf-8")

BUILD_VERSION.write_text("3.42.0\n", encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionName '3.41.9'", "versionName '3.42.0'", "Android version name")
GRADLE.write_text(gradle, encoding="utf-8")

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
print("VERSION=3.42.0")
print("CHANGED=frame-rate-independent marker smoothing; GNSS-quality-weighted map-matching confidence; targeted regression test")
