#!/usr/bin/env python3
"""Integrate NEXT NAVI PRO's original rider-glance layout into OsmAnd."""
from pathlib import Path
import shutil

root = Path("android")
patch_root = Path("next-navi-pro/NEXT_NAVI_PRO_OSMAND")
activity = root / "OsmAnd/src/net/osmand/plus/activities/MapActivity.java"
overlay = root / "OsmAnd/src/net/osmand/plus/activities/NextNaviProOverlay.java"
shutil.copyfile(patch_root / "NextNaviProOverlay.java", overlay)

source = activity.read_text(encoding="utf-8")
field_marker = "\tprivate KeyEventHelper keyEventHelper;"
field_patch = field_marker + "\n\tprivate NextNaviProOverlay nextNaviProOverlay;"
if "private NextNaviProOverlay nextNaviProOverlay;" not in source:
    if source.count(field_marker) != 1:
        raise SystemExit("Cannot safely locate MapActivity field insertion point")
    source = source.replace(field_marker, field_patch, 1)

create_marker = "\t\tsetContentView(R.layout.main);"
create_patch = create_marker + "\n\t\tnextNaviProOverlay = new NextNaviProOverlay(this, app);\n\t\tnextNaviProOverlay.install();"
if "nextNaviProOverlay.install();" not in source:
    if source.count(create_marker) != 1:
        raise SystemExit("Cannot safely locate MapActivity content initialization")
    source = source.replace(create_marker, create_patch, 1)

destroy_marker = "\tprotected void onDestroy() {\n\t\tsuper.onDestroy();"
destroy_patch = "\tprotected void onDestroy() {\n\t\tif (nextNaviProOverlay != null) {\n\t\t\tnextNaviProOverlay.dispose();\n\t\t\tnextNaviProOverlay = null;\n\t\t}\n\t\tsuper.onDestroy();"
if "nextNaviProOverlay.dispose();" not in source:
    if source.count(destroy_marker) != 1:
        raise SystemExit("Cannot safely locate MapActivity destruction point")
    source = source.replace(destroy_marker, destroy_patch, 1)

activity.write_text(source, encoding="utf-8")
print("Installed NEXT NAVI PRO Canvas HUD using OsmAnd-owned map, GPS, routing and speed-limit state.")
