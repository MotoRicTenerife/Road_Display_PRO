#!/usr/bin/env python3
"""Contract tests for the OsmAnd map-data manager wired into the archived app."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/OsmAndMapManager.java"
ROUTER_TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/OsmAndOfflineRouter.java"
PLATFORM_TEMPLATE = ROOT / "NEXT_NAVIPRO/templates/PlatformUtil.java"
PATCH = ROOT / "NEXT_NAVIPRO/apply_online_navigation_patch.py"
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"

template = TEMPLATE.read_text(encoding="utf-8")
router = ROUTER_TEMPLATE.read_text(encoding="utf-8")
platform = PLATFORM_TEMPLATE.read_text(encoding="utf-8")
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
if "shutil.copyfile(OSMAND_ROUTER_TEMPLATE, OSMAND_ROUTER)" not in patch:
    raise SystemExit("FAIL: CI overlay does not install the offline router into the Android source tree")
if "OsmAndMapManager.show(MainActivity.this)" not in patch:
    raise SystemExit("FAIL: existing display settings do not expose the map manager")
if 'OsmAndOfflineRouter.calculate(MainActivity.this, start, destLat, destLon)' not in patch:
    raise SystemExit("FAIL: existing route selection does not prefer OsmAnd offline routing")
for token in ('AUTOMATICO · OsmAnd offline prioritario', 'ONLINE · Valhalla / OSRM', 'OFFLINE · solo OsmAnd', 'routing_engine_mode'):
    if token not in patch:
        raise SystemExit(f"FAIL: missing routing mode control/policy: {token}")
if "OSMAND_OFFLINE" not in router or "new BinaryMapIndexReader" not in router:
    raise SystemExit("FAIL: offline router does not open real OsmAnd .obf data")
if "class PlatformUtil" not in platform:
    raise SystemExit("FAIL: Android PlatformUtil shim is missing")
if "python3 NEXT_NAVIPRO/test_osmand_map_manager_contract.py" not in workflow:
    raise SystemExit("FAIL: CI does not run the map manager contract test")
if "osmand-routing-libraries" not in workflow or "OsmAnd-java:androidJar" not in workflow:
    raise SystemExit("FAIL: CI does not build/package the pinned OsmAnd routing libraries")

# The app must be honest: downloading .obf files is not itself proof of offline routing.
if "does not claim offline navigation" not in template_lower and "routing offline non attivo" not in template_lower:
    raise SystemExit("FAIL: missing explicit guard against claiming unimplemented offline routing")

print("PASS: official OsmAnd catalog/download contract present")
print("PASS: manager is wired into the existing app through the deterministic source overlay")
print("PASS: CI verifies the integration hooks, offline routing priority, and explicit disclaimer")
print("LIMITATION: this test does not prove OsmAnd runtime route calculation")
