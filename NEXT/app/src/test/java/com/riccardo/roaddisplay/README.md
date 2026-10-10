# Test plan placeholder

The alpha scaffold intentionally has no external test dependency yet. Add deterministic JVM tests for:
- stale fix threshold (10 seconds)
- accuracy threshold (50 metres)
- speed conversion (m/s to km/h)
- DEMO values never being treated as GPS
- no curve/speed-limit claim when road geometry is unavailable

Device/instrumentation tests are required for permission flows, rotation, lifecycle and GPS provider behavior.
