#!/usr/bin/env python3
"""Contract tests for NEXT NAVI PRO's universal offline-region catalog.

These tests validate catalog metadata and safe lifecycle rules. They do not claim
that a raw OSM PBF is directly renderable or routable by the Android app.
"""
import json
import pathlib
import unittest
from urllib.parse import urlparse

ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "NEXT_NAVIPRO" / "offline-region-catalog.schema.json"

class OfflineCatalogContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))

    def test_catalog_is_global_and_not_tenerife_hardcoded(self):
        self.assertEqual(self.schema["catalog_scope"], "global")
        self.assertIn("catalog_hierarchy", self.schema)
        self.assertTrue(self.schema["region_selection"]["multi_select"])

    def test_required_lifecycle_operations_are_declared(self):
        operations = self.schema["package_lifecycle"]["operations"]
        for op in ("download", "resume", "pause", "retry", "verify_checksum",
                   "check_update", "atomic_update", "rollback", "delete"):
            self.assertIn(op, operations)

    def test_map_capabilities_are_separate(self):
        capabilities = self.schema["package_capabilities"]
        for name in ("rendering", "offline_search", "offline_routing",
                     "speed_limits", "radar_poi"):
            self.assertIn(name, capabilities)
        self.assertTrue(self.schema["package_capabilities"]["explicit_coverage_required"])

    def test_sources_require_licensing_and_integrity_metadata(self):
        for field in ("source_url", "license", "attribution", "updated_at",
                      "sha256", "format", "capabilities"):
            self.assertIn(field, self.schema["region_package_required_fields"])
        self.assertTrue(self.schema["reject_unknown_or_missing_checksums"])

    def test_example_url_is_https(self):
        url = urlparse(self.schema["example_source_policy"]["required_scheme"] + "://example.invalid/data")
        self.assertEqual(url.scheme, "https")

if __name__ == "__main__":
    unittest.main(verbosity=2)
