"""Static regression checks for ROAD DISPLAY PRO map gestures and speed-limit UI."""
from pathlib import Path

MAIN = Path("app/src/main/java/com/riccardo/roaddisplay/MainActivity.java")
BUILD = Path("app/build.gradle")

def main():
    s = MAIN.read_text(encoding="utf-8")
    b = BUILD.read_text(encoding="utf-8")

    # Landscape map toolbar reserves a separate search lane; the five function
    # buttons must not be centred underneath the search button.
    assert "float searchLeft=w-searchW-side;" in s
    assert "float available=w-2f*side-gap-searchW;" in s
    assert "float left=side;" in s
    assert "mapCloseBounds.set(left+4f*(buttonW+gap)" in s
    # Portrait keeps search on its own row so it cannot overlap the three action buttons.
    assert "Portrait: keep SEARCH on its own full-width row" in s
    assert "mapSearchBounds.set(searchLeft, top, searchLeft + searchW, top + barH)" in s
    # Map search is a map-centering operation, not an implicit navigation start.
    assert "void showMapSearchDialog()" in s
    assert "void searchMapDestinationCandidatesDialog(final String query)" in s
    assert "void showMapSearchCandidates(final ArrayList<DestinationCandidate> candidates)" in s
    assert "mapViewLat=c.lat; mapViewLon=c.lon;" in s
    assert "selectedMapLat=c.lat; selectedMapLon=c.lon;" in s
    assert "showMapSearchDialog();" in s

    required = [
        "boolean pinching = false, rotatingMap = false",
        "rotateStartAngle",
        "mapFrozenBearing=normalizeBearing(rotateStartBearing+delta)",
        "boolean draggingTripMap = false, tripRotating = false",
        "tripMapBearing=normalizeBearing(tripRotateStartBearing+delta)",
        "drawSpeedLimitBadge(c, limitCx, limitCy, badgeR, currentSpeedLimitKmh)",
        "currentSpeedLimitKmh > 0",
        "if (navW < dp(150))",
        "implementation 'org.maplibre.gl:android-sdk-opengl:13.6.1'",
        "VECTOR_MAP_STYLE",
        "searchDestinationCandidatesCartoCiudad",
        "setVectorMapExpanded(true)",
        "setVectorMapExpanded(false)",
    ]
    bad = [x for x in required if x not in (b if x.startswith("implementation ") else s)]
    if "p.setTextSize(fs * density);" in s[s.find("void drawSpeedLimitBadge"):s.find("void drawSpeedLimitBadge")+1800]:
        bad.append("double-density speed-limit text sizing")
    print(f"CHECKS={len(required)+1}")
    print(f"FAILURES={len(bad)}")
    if bad:
        for x in bad: print("FAIL", x)
        raise SystemExit(1)
    print("RESULT=PASS")

if __name__ == "__main__": main()
