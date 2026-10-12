#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
src = (ROOT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
checks = {
    "first known speed limit announced": '"Limite di velocità, " + lim' in src,
    "new known zone announced distinctly": '"Inizio del limite di velocità, " + lim' in src,
    "unknown-to-known transition is remembered": "awaitingNewSpeedLimitAfterUnknown = true" in src and "lastSpokenSpeedLimitKmh > 0 || awaitingNewSpeedLimitAfterUnknown" in src,
    "unknown limit does not invent a number": '"Fine del limite di velocità noto. Nuovo limite non disponibile."' in src,
    "badge responds to display dimensions": "float upperSafe = dp(150) + badgeR" in src and "speedBounds.top - badgeR" in src,
    "sunlight negative mode retained": '"NEGATIVO — sempre"' in src,
    "display settings gear retained": "settingsButtonBounds.contains(x,y)" in src,
    "version is 3.41.9": "versionCode 539; versionName '3.41.9'" in build,
    "sunlight thresholds halved": "SUNLIGHT_ENTER_LUX = 12500f" in src and "SUNLIGHT_EXIT_LUX = 6000f" in src,
    "light map style": 'VECTOR_MAP_STYLE = "https://tiles.openfreemap.org/styles/positron"' in src,
    "stale route limit not used as current legal limit": "Do not carry a limit sampled from a previous route segment" in src,
    "two navigation cue stages only": "if(d<=25) stage=4" in src and "else if(d<=preThreshold) stage=0" in src,
}
for label, ok in checks.items(): print(("PASS" if ok else "FAIL") + " " + label)
print(f"CHECKS={len(checks)} FAILURES={sum(not x for x in checks.values())}")
raise SystemExit(0 if all(checks.values()) else 1)
