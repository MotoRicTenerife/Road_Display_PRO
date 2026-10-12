from pathlib import Path

p = Path(__file__).resolve().parents[0] / "../app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java"
s = p.read_text(encoding="utf-8")
assert "Location matchedLocation;" in s
assert "matchedLocation=new Location(q)" in s
assert "matchedLocation.distanceTo(l) <= 150.0f" in s
assert "System.currentTimeMillis()-matchedAt < 30000L" in s
needle = "if (ways == null || ways.isEmpty()) return 0;"
pos = s.index(needle)
match_pos = s.index("return matchedSpeedLimitKmh;", s.index("int speedLimitKmh(Location l)"))
assert match_pos < pos, "network speed-limit match must be checked before local-cache-empty return"
print("speed-limit RIDE regression: PASS")
