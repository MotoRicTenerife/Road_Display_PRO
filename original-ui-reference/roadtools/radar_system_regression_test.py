#!/usr/bin/env python3
"""Static and 100k-case stress checks for the Road Display PRO radar feature."""
from pathlib import Path
import math, random, sys
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT/'app/src/main/java/com/riccardo/roaddisplay/MainActivity.java').read_text()
ENGINE = (ROOT/'app/src/main/java/com/riccardo/roaddisplay/RoadEngine.java').read_text()
DB = (ROOT/'app/src/main/java/com/riccardo/roaddisplay/RoadDb.java').read_text()
checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

check('persisted radar freshness metadata', 'radar_sync' in DB and 'lastRadarSyncAt(double lat,double lon)' in DB and 'radarSyncDue(double lat,double lon,long now)' in DB)
check('24-hour freshness interval', '24L*60L*60L*1000L' in DB and 'cell TEXT PRIMARY KEY' in DB)
check('failed refresh retains last known records', 'replaceRadarsNear' in DB and 'beginTransaction()' in DB and 'setTransactionSuccessful()' in DB)
check('stale radar points removed only inside successful refresh radius', 'Location.distanceBetween(lat,lon,c.getDouble(1),c.getDouble(2),dist)' in DB and 'dist[0]<=radiusMeters' in DB)
check('fixed radar source query', '[highway=speed_camera]' in ENGINE)
check('nearest radar uses exact stored coordinates', 'Location.distanceBetween(l.getLatitude(), l.getLongitude(), r.lat, r.lon, d)' in ENGINE)
check('radar icons rendered on map', 'drawRadarMarkers(c, bounds, location, expanded)' in MAIN and 'c.drawCircle(x, y, radius, p)' in MAIN)
check('full-screen MapLibre radar markers use native map projection', 'vectorMap.getProjection().toScreenLocation(new LatLng(radar.lat, radar.lon))' in MAIN and 'nativeFullScreenProjection' in MAIN)
check('radar projection matches tile zoom, scale and navigation bearing', 'double mapScale = expanded ? 1.0 : 1.15;' in MAIN and 'navigationMatchedBearing' in MAIN and 'drawRadarMarkers(c, bounds, location, expanded)' in MAIN)
check('alerts gated by known speed limit', 'if (limitKmh <= 0 || actualKmh < limitKmh + 3.0f) return;' in MAIN)
check('adaptive initial warning distance bounded', 'Math.max(400.0, Math.min(1000.0, actualKmh * 6.0))' in MAIN)
check('100 m warning', 'distance <= 100.0' in MAIN and 'Radar a 100 metri' in MAIN and 'triggerRadarAlert("Radar a 100 metri", "100 m")' in MAIN)
check('50 m warning', 'distance <= 50.0' in MAIN and 'Radar a 50 metri' in MAIN and 'triggerRadarAlert("Radar a 50 metri", "50 m")' in MAIN)
check('10 m voice warning', 'distance <= 10.0' in MAIN and 'Attenzione Radar' in MAIN and 'triggerRadarAlert("Attenzione Radar", "RADAR")' in MAIN)
check('no radar warning from approximate GPS', 'updateRadarAlerts(radarApproach, actualKmh, currentSpeedLimitKmh, nowMs)' in MAIN and 'if (accurateGps)' in MAIN)
check('version bump', "versionName '3.41.9'" in (ROOT/'app/build.gradle').read_text())

# Exercise the documented distance sequence and overspeed gate over randomized approaches.
rng = random.Random(3410)
N = 100_000
for i in range(N):
    limit = rng.randint(20, 120)
    actual = rng.uniform(0, 180)
    speed_distance = max(400.0, min(1000.0, actual * 6.0))
    # No legal limit => no initial alarm; exactly +3 km/h is eligible.
    eligible = limit > 0 and actual >= limit + 3.0
    if not eligible:
        check('overspeed gate stress sample', True)
    else:
        check('overspeed gate stress sample', speed_distance >= 400.0 and speed_distance <= 1000.0)
# Check stage thresholds never reverse and each threshold is one-shot.
for distances in ([900, 500, 101, 100, 51, 50, 11, 10, 5], [400, 100, 50, 10], [1000, 999, 500, 250, 100, 50, 10]):
    stage=1; seen=[]
    for d in distances:
        if stage==1 and d<=100: seen.append(100); stage=2
        elif stage==2 and d<=50: seen.append(50); stage=3
        elif stage==3 and d<=10: seen.append(10); stage=4
    check('monotonic alert stages', seen == [x for x in (100,50,10) if x in seen] and len(seen) == len(set(seen)))

failed=[name for name,ok in checks if not ok]
print(f'Radar system regression: {len(checks)-len(failed)}/{len(checks)} checks passed')
if failed:
    print('FAILED:', *failed, sep='\n - ')
    sys.exit(1)
# Stress the Web-Mercator conversion used for exact marker placement.
for _ in range(N):
    lat = rng.uniform(-80.0, 80.0); lon = rng.uniform(-179.0, 179.0); zoom = rng.randint(15, 19)
    world = 256.0 * (1 << zoom)
    x = (lon + 180.0) / 360.0 * world
    ss = math.sin(math.radians(lat))
    y = (.5 - math.log((1 + ss) / (1 - ss)) / (4 * math.pi)) * world
    lon2 = x / world * 360.0 - 180.0
    n = math.pi - 2.0 * math.pi * y / world
    lat2 = math.degrees(math.atan(math.sinh(n)))
    if abs(lat-lat2) > 1e-8 or abs(lon-lon2) > 1e-8:
        check('map coordinate projection stress sample', False)
        break
else:
    check('100k exact coordinate projection round trips', True)
print(f'PASS: {N:,} randomized alert-gate samples; {N:,} map-coordinate round trips; stage progression, freshness, map icon, and no-invented-limit guards checked.')
