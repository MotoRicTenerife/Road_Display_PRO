#!/usr/bin/env python3
"""Apply the reviewed online-navigation patch to the archived Android baseline.

The repository keeps the original, user-provided source archive intact. This
script applies a small, deterministic source overlay during CI before tests and
APK compilation. It fails closed if the expected baseline has drifted.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "Road_Display_PRO"
MAIN = PROJECT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java"
SMOOTH_TEST = PROJECT / "roadtools/map_marker_smoothing_regression_test.py"

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
print("ONLINE_NAV_PATCH=PASS")
print("CHANGED=RideView marker smoothing; roadtools marker smoothing regression")
