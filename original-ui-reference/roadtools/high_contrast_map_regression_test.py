from pathlib import Path
MAIN=Path('app/src/main/java/com/riccardo/roaddisplay/MainActivity.java')
BUILD=Path('app/build.gradle')
def main():
 s=MAIN.read_text(encoding='utf-8'); b=BUILD.read_text(encoding='utf-8')
 checks={
  'MapLibre style hook retained':'map.setStyle(VECTOR_MAP_STYLE, style -> {' in s,
  'light Positron vector style':'VECTOR_MAP_STYLE = "https://tiles.openfreemap.org/styles/positron"' in s,
  'no indiscriminate black road recoloring':'try { applyHighContrastMapStyle(style); }' not in s,
  'GPS/radar overlays preserved':'drawRadarMarkers(c, bounds, location, expanded)' in s and 'bike.moveTo(markerCx,markerCy-markerSize*.72f)' in s,
  'version 3.41.9':"versionCode 539; versionName '3.41.9'" in b,
 }
 for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+' '+name)
 print(f'CHECKS={len(checks)} FAILURES={sum(not x for x in checks.values())}')
 if not all(checks.values()): raise SystemExit(1)
if __name__=='__main__': main()
