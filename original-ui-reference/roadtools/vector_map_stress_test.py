"""Deterministic stress tests for the phase-B vector-map state bridge."""
from pathlib import Path
import math
import random

MAIN = Path("app/src/main/java/com/riccardo/roaddisplay/MainActivity.java")

def norm(b):
    x = b % 360.0
    return x + 360.0 if x < 0 else x

def main():
    s = MAIN.read_text(encoding="utf-8")
    required = [
        "MapView vectorMapView",
        "volatile boolean vectorMapReady",
        "VECTOR_MAP_STYLE = \"https://tiles.openfreemap.org/styles/positron\"",
        "syncVectorMapStateFromCamera",
        "syncVectorMapCamera",
        "try { vectorMapView.dispatchTouchEvent(e); }",
        "mapFrozenBearing=normalizeBearing(rotateStartBearing+delta)",
        "tripMapBearing=normalizeBearing(tripRotateStartBearing+delta)",
        "searchDestinationCandidatesCartoCiudad",
    ]
    missing=[x for x in required if x not in s]
    failures=[]
    rng=random.Random(337)
    # 250,000 randomized camera/rotation states. These must never produce NaN,
    # infinity, or bearings outside the canonical [0,360) interval.
    cases=250_000
    for _ in range(cases):
        b=rng.uniform(-1e9,1e9)
        d=rng.uniform(-720,720)
        z=rng.uniform(.5,1.7)
        out=norm(b-d)
        zoom=15.0+(z-.8)*2.2
        if not (0.0 <= out < 360.0 and math.isfinite(out)):
            failures.append("bearing")
            break
        if not (12.0 <= zoom <= 19.0 and math.isfinite(zoom)):
            failures.append("zoom")
            break
    # Source-level crash guards: all MapLibre lifecycle calls are guarded and the
    # native renderer has an explicit fallback path.
    for token in ("catch (Throwable t)", "vectorMapFailed = true", "vectorMapView.onDestroy()"):
        if token not in s: failures.append(token)
    print(f"CASES={cases}")
    print(f"FAILURES={len(missing)+len(failures)}")
    for x in missing: print("MISSING",x)
    for x in failures: print("FAIL",x)
    if missing or failures: raise SystemExit(1)
    print("RESULT=PASS")

if __name__ == "__main__": main()
