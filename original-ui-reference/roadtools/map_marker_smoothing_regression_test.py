from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/riccardo/roaddisplay/MainActivity.java').read_text()
engine = (root / 'app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java').read_text()
checks = {
    'non-navigation marker uses local road snap': 'engine.mapMatchedLocation(location)' in main,
    'snap restricted to accurate fixes': 'location.getAccuracy() <= 20f' in main,
    'high-speed marker smoothing is more responsive': 'location.getSpeed()>18f?0.72:0.55' in main,
    'large credible jump does not leave marker lagging': 'targetGap>45f?1.0' in main,
    'road snap has distance guard': 'bestDistance > 22.0' in engine,
    'oneway heading filter exists': '"yes".equalsIgnoreCase(way.oneway)' in engine,
    'speed limit remains based on real mapped data': 'return best == null || bestScore > 55.0 ? 0 : bestLimit;' in engine,
}
for name, ok in checks.items():
    print(f'{name}: {"PASS" if ok else "FAIL"}')
print(f'CHECKS={len(checks)}')
print(f'FAILURES={sum(not x for x in checks.values())}')
raise SystemExit(0 if all(checks.values()) else 1)
