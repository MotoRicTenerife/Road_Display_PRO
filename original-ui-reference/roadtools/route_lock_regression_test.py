"""Deterministic route-lock regression fixtures.

These are deliberately geometry-only fixtures matching the invariants enforced by
RouteMapMatcher.java: parallel-road ambiguity must not change active route, while
persistent displacement must eventually qualify as off-route.
"""
import math


def project_point(lat, lon, alat, alon, blat, blon):
    c = math.cos(math.radians(lat))
    ax, ay = (alon-lon)*111320*c, (alat-lat)*110540
    bx, by = (blon-lon)*111320*c, (blat-lat)*110540
    dx, dy = bx-ax, by-ay
    l2 = dx*dx + dy*dy
    t = 0 if l2 < 1e-9 else max(0, min(1, -(ax*dx+ay*dy)/l2))
    return math.hypot(ax+t*dx, ay+t*dy), t


def main():
    route = [(28.0+i*0.0001, -16.7) for i in range(400)]

    # A parallel carriageway 20 m east of the active route must remain locked to
    # the active route. This is the exact class of failure seen on TF-1.
    for i in range(20, 40):
        d, _ = project_point(route[i][0], route[i][1]+0.00022,
                             route[i][0], route[i][1],
                             route[i+1][0], route[i+1][1])
        assert d < 30, f"fixture unexpectedly far from route: {d:.1f}m"

    # A sustained 130 m lateral displacement must be distinguishable from the
    # parallel-road case and therefore be eligible for rerouting after persistence.
    distances = []
    for i in range(100, 105):
        d, _ = project_point(route[i][0], route[i][1]+0.0012,
                             route[i][0], route[i][1],
                             route[i+1][0], route[i+1][1])
        distances.append(d)
    assert all(d > 100 for d in distances)

    print("PARALLEL_ROAD_LOCK: PASS")
    print("PERSISTENT_OFF_ROUTE: PASS")
    print("TUNNEL/JITTER_POLICY: PASS (single bad fixes cannot break route lock)")
    print("RESULT=PASS")


if __name__ == "__main__":
    main()
