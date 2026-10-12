#!/usr/bin/env python3
"""Static regression checks for hourly weather period summaries and drill-down."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/riccardo/roaddisplay/MainActivity.java').read_text()
build=(root/'app/build.gradle').read_text()
checks={
    'four defined time bands': 'final int[] froms = {0, 8, 16, 20}' in main and 'final int[] tos = {8, 16, 20, 24}' in main,
    'hourly forecast based on actual Open-Meteo response': 'latestWeatherHourlyObject = response.optJSONObject("hourly")' in main,
    'average temperature computed from valid hourly samples': 'tempSum/tempN' in main,
    'average wind computed from valid hourly samples': 'windSum/windN' in main,
    'average precipitation probability computed from available samples': 'probSum/probN' in main,
    'weather condition icons mapped from forecast codes': 'String weatherCodeIcon(int code)' in main,
    'hourly details expandable on tap': 'period.setOnClickListener' in main and 'weatherExpandedBand==selectedBand' in main,
    'unknown forecast remains unavailable rather than fabricated': 'Previsioni non disponibili' in main and 'Dati non disponibili' in main,
    'version incremented': "versionCode 539; versionName '3.41.9'" in build,
}
for label,ok in checks.items(): print(('PASS' if ok else 'FAIL')+' '+label)
print(f'CHECKS={len(checks)} FAILURES={sum(not x for x in checks.values())}')
raise SystemExit(0 if all(checks.values()) else 1)
