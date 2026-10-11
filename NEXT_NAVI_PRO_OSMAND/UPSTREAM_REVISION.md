# Upstream revision pin

**Status: initial upstream baseline compiled successfully in GitHub Actions.**

The successful baseline used Java 17 and produced the official `assembleNightlyFreeLegacyFatDebug` APK. The exact source revisions recorded by that build are now pinned in `.github/workflows/build-osmand-baseline.yml` for reproducibility.

| Component | Pinned commit |
|---|---|
| OsmAnd Android app | `96cbea0fb2bca3a82c7ff36bfbe2add1f472970f` |
| OsmAnd-resources | `b981dac242961d9de6135b1b0ff82cabd8f55a7d` |
| OsmAnd-core | `69f9df37c09463732b6c0360b01859555566dc28` |
| OsmAnd-core-legacy | `7a1bb2c565ebf714b5a6bd06d1de23792c9a20b6` |
| OsmAnd-build | `54356f2a6d1d07fe67510a25ec7912c0c63b277d` |
| OsmAnd-tools | `84d3b721d70e9e8d7ac84275b4f65275b8b1c1e6` |
| OsmAnd-misc | `3fa7f2334740b3de68cd604725ebd02dc114d205` |

The next build targets ARM64 (the Samsung S22 Ultra architecture) and changes the app label to NEXT NAVI PRO. This is a build configuration change only; it does not yet constitute the custom RIDE HUD or proof of runtime navigation tests.

Build evidence: workflow run #8, commit `4e106f80c919d304abf1c3584543f7f624949f47`. The APK and diagnostic artifacts were generated successfully on 2026-10-11.
