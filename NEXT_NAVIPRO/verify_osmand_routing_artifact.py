#!/usr/bin/env python3
"""Fail-closed verification of the pinned OsmAnd routing build outputs.

This verifies that CI produced the expected routing classes/resources. It is
not a runtime routing test and must never be used to claim .obf routing works.
"""
from pathlib import Path
from zipfile import ZipFile
import sys

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "upstream/Osmand/OsmAnd-java/build"
JARS = [
    BUILD / "libs/OsmAnd-java-master-snapshot.jar",
    BUILD / "libs/OsmAnd-java-android-master-snapshot-android.jar",
]
REQUIRED_CLASSES = {
    "net/osmand/router/RoutePlannerFrontEnd.class",
    "net/osmand/router/RoutingConfiguration.class",
    "net/osmand/router/RouteSegmentResult.class",
    "net/osmand/binary/BinaryMapIndexReader.class",
}
REQUIRED_RESOURCES = {
    "net/osmand/router/routing.xml",
    "net/osmand/map/regions.ocbf",
}

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

for jar in JARS:
    if not jar.is_file() or jar.stat().st_size < 100_000:
        fail(f"missing or implausibly small expected artifact: {jar}")
    with ZipFile(jar) as archive:
        entries = set(archive.namelist())
    missing = REQUIRED_CLASSES - entries
    if missing:
        fail(f"{jar.name} missing routing classes: {sorted(missing)}")
    if jar.name.endswith("-android-master-snapshot-android.jar"):
        missing_resources = REQUIRED_RESOURCES - entries
        if missing_resources:
            fail(f"{jar.name} missing required engine resources: {sorted(missing_resources)}")
    print(f"PASS: {jar.name} ({jar.stat().st_size} bytes; routing API classes present)")

print("PASS: pinned OsmAnd routing artifacts are structurally present.")
print("LIMITATION: this does not calculate a route, open a .obf file, or prove Android runtime compatibility.")
