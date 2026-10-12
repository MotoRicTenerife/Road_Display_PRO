"""Static geometry stress test for ROAD DISPLAY PRO 3.24.0 curve/NAV/weather layout."""
from __future__ import annotations

import math

SIZES = [
    (2280, 1080), (2560, 1136), (2400, 1080), (1920, 1080),
    (1080, 2280), (1080, 2340), (1440, 3200), (800, 1280), (1280, 800),
]
DENSITIES = [1.0, 2.0, 2.75, 3.0, 3.5, 4.0]


def rect_overlap(a, b):
    return not (a[2] <= b[0] or a[0] >= b[2] or a[3] <= b[1] or a[1] >= b[3])


def dp(v, density):
    return v * density


def layout(w, h, density):
    land = w > h
    speed_cx = w * .52 if land else w * .50
    speed_base = h * .62 if land else h * .73
    target = min(520, h * .58) if land else min(760, h * .37)
    width_limit = (w * (.50 if land else .99) - dp(24, density)) / (.3 + .2)
    top_limit = h * .10 if land else h * .29
    bottom_limit = h * .82 if land else h * .77
    height_limit = max(90, min((speed_base - top_limit) / .78,
                               (bottom_limit - speed_base) / .12))
    number_height = max(dp(90, density), min(target, width_limit, height_limit))
    total = 3 * number_height * .42 + 2 * number_height * .10
    speed_left = speed_cx - total / 2 - dp(14, density)
    speed_top = speed_base - number_height * .88

    if land:
        nav_left = dp(12, density)
        nav_right = min(speed_left - dp(24, density), w * .35)
        nav_top = dp(150, density)
        nav_bottom = h * .84
    else:
        nav_left = dp(18, density)
        nav_right = w - dp(18, density)
        nav_top = dp(132, density)
        nav_bottom = min(h * .41, speed_top - dp(105, density))

    nav_w, nav_h = nav_right - nav_left, nav_bottom - nav_top
    if nav_w <= dp(150, density) or nav_h <= dp(120, density):
        return None

    if not land:
        # Weather temperature/icon occupy roughly through 114dp; require a
        # minimum 12dp breathing room before the curve/NAV band.
        weather_bottom = dp(114, density)
        if nav_top < weather_bottom + dp(12, density):
            return ("weather-clearance-fail", (nav_top, weather_bottom))

    gap = dp(8, density)
    curve_w = max(dp(112, density), min(nav_w * .40,
                                        nav_w - dp(150, density) - gap))
    curve_right = nav_left + curve_w
    guidance_left = curve_right + gap
    guidance_w = nav_right - guidance_left

    if guidance_w >= dp(140, density):
        curve = (nav_left + dp(6, density), nav_top + dp(6, density),
                 curve_right - dp(6, density), nav_bottom - dp(6, density))
        guidance = (guidance_left, nav_top, nav_right, nav_bottom)
        mode = "side-by-side"
    else:
        half = (nav_h - gap) * .5
        curve = (nav_left + dp(6, density), nav_top + dp(6, density),
                 nav_right - dp(6, density), nav_top + half - dp(6, density))
        guidance_top = nav_top + half + gap
        guidance = (nav_left, guidance_top, nav_right, nav_bottom)
        mode = "vertical-fallback"

    return mode, (nav_left, nav_top, nav_right, nav_bottom), curve, guidance


def triangle_centroid():
    r = 1.0
    pts = [(0, -r), (-r * .8660254, r * .5), (r * .8660254, r * .5)]
    return (sum(x for x, _ in pts) / 3, sum(y for _, y in pts) / 3)


def main():
    failures = []
    tested = 0
    for w, h in SIZES:
        for density in DENSITIES:
            tested += 1
            result = layout(w, h, density)
            if result is None:
                continue
            if result[0] == "weather-clearance-fail":
                failures.append((w, h, density, "portrait", "weather clearance", result))
                continue
            mode, nav, curve, guidance = result
            if rect_overlap(curve, guidance):
                failures.append((w, h, density, mode, "overlap", curve, guidance))
            for name, r in (("curve", curve), ("guidance", guidance)):
                if r[0] < nav[0] or r[1] < nav[1] or r[2] > nav[2] or r[3] > nav[3]:
                    failures.append((w, h, density, mode, name + " outside nav", r, nav))
            if curve[2] <= curve[0] or curve[3] <= curve[1]:
                failures.append((w, h, density, mode, "invalid curve rect"))
            if guidance[2] <= guidance[0] or guidance[3] <= guidance[1]:
                failures.append((w, h, density, mode, "invalid guidance rect"))

    cx, cy = triangle_centroid()
    if abs(cx) > 1e-6 or abs(cy) > 1e-6:
        failures.append(("triangle", cx, cy))

    print(f"TESTED_CASES={tested}")
    print(f"FAILURES={len(failures)}")
    print(f"TRIANGLE_CENTROID=({cx:.6f},{cy:.6f})")
    if failures:
        for failure in failures[:20]:
            print("FAIL", failure)
        raise SystemExit(1)
    print("RESULT=PASS")


if __name__ == "__main__":
    main()
