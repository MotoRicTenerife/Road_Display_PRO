#!/usr/bin/env python3
"""Contract tests for the OsmAnd map-data manager wired into the archived app."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/OsmAndMapManager.java"
PATCH = ROOT / "NEXT_NAVIPRO/apply_online_navigation_patch.py"
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"

template = TEMPLATE.read_text(encoding="utf-8")
patch = PATCH.read_text(encoding="utf-8")
workflow = WORKFLOW.read_text(encoding="utf-8")

required_template = (
    "https://download.osmand.net/list.php?d=1",
    "https://download.osmand.net/download?file=",
    ".obf.zip",
    "ZipInputStream",
    "setConnectTimeout",
    "setReadTimeout",
    "OsmAnd route engine is wired",
    "routing offline non attivo",
)
template_lower = template.lower()
for token in required_template:
    if token.lower() not in template_lower:
        raise SystemExit(f"FAIL: OsmAnd map manager missing required contract token: {token}")

if "shutil.copyfile(MAP_MANAGER_TEMPLATE, MAP_MANAGER)" not in patch:
    raise SystemExit("FAIL: CI overlay does not install the map manager into the Android source tree")
if "OsmAndMapManager.show(MainActivity.this)" not in patch:
    raise SystemExit("FAIL: existing display settings do not expose the map manager")
if "python3 NEXT_NAVIPRO/test_osmand_map_manager_contract.py" not in workflow:
    raise SystemExit("FAIL: CI does not run the map manager contract test")

# The app must be honest: downloading .obf files is not itself proof of offline routing.
if "does not claim offline navigation" not in template and "routing offline non attivo" not in template:
    raise SystemExit("FAIL: missing explicit guard against claiming unimplemented offline routing")

print("PASS: official OsmAnd catalog/download contract present")
print("PASS: manager is wired into the existing app through the deterministic source overlay")
print("PASS: CI verifies the integration hook and the offline-routing disclaimer")
print("LIMITATION: this test does not prove OsmAnd runtime route calculation")
