#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
src = (ROOT / 'app/src/main/java/com/riccardo/roaddisplay/MainActivity.java').read_text()
build = (ROOT / 'app/build.gradle').read_text()
checks = {
    'ambient light sensor is used': 'Sensor.TYPE_LIGHT' in src,
    'auto/manual theme preference persists': 'display_theme_mode' in src and 'prefs.edit().putInt("display_theme_mode", checked).apply()' in src,
    'half-reduced high lux threshold exists': 'SUNLIGHT_ENTER_LUX = 12500f' in src,
    'half-reduced lower lux threshold provides hysteresis': 'SUNLIGHT_EXIT_LUX = 6000f' in src,
    'gear button has a hit target': 'settingsButtonBounds.contains(x,y)' in src,
    'settings expose auto dark negative': all(x in src for x in ['AUTO — sensore luminosità', 'SCURO — sempre', 'NEGATIVO — sempre']),
    'negative rendering mode remains available': 'negativeTheme && !vectorExpanded' in src,
    'version incremented': "versionCode 539; versionName '3.41.9'" in build,
}
for label, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ' ' + label)
print(f'CHECKS={len(checks)} FAILURES={sum(not x for x in checks.values())}')
raise SystemExit(0 if all(checks.values()) else 1)
