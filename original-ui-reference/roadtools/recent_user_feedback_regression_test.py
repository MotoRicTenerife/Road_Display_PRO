#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/"app/src/main/java/com/riccardo/roaddisplay/MainActivity.java").read_text(encoding="utf-8")
b=(root/"app/build.gradle").read_text(encoding="utf-8")
checks={
 "sunlight thresholds halved with hysteresis": "SUNLIGHT_ENTER_LUX = 12500f" in s and "SUNLIGHT_EXIT_LUX = 6000f" in s,
 "UI explains new thresholds": "sopra 12.500 lux" in s and "sotto 6.000 lux" in s,
 "positron light style enabled": 'VECTOR_MAP_STYLE = "https://tiles.openfreemap.org/styles/positron"' in s,
 "black road recoloring no longer applied": "try { applyHighContrastMapStyle(style); }" not in s,
 "no stale route-derived speed limit fallback": "Do not carry a limit sampled from a previous route segment" in s and "if (routeLimit > 0) currentSpeedLimitKmh = routeLimit" not in s,
 "navigation has only advance and immediate stages": "if(d<=25) stage=4" in s and "else if(d<=preThreshold) stage=0" in s and "else if(d<=100)stage=2" not in s,
 "same-road false-turn suppression remains": "suppressFalseSameRoadTurns(newSteps)" in s,
 "next known limit after unknown announced as new zone": "awaitingNewSpeedLimitAfterUnknown = true" in s and "lastSpokenSpeedLimitKmh > 0 || awaitingNewSpeedLimitAfterUnknown" in s,
 "version bumped": "versionCode 539; versionName '3.41.9'" in b,
}
for k,v in checks.items(): print(("PASS " if v else "FAIL ")+k)
print(f"CHECKS={len(checks)} FAILURES={sum(not v for v in checks.values())}")
raise SystemExit(0 if all(checks.values()) else 1)
