# Upstream revision tracking

**Status: first CI build configured; reproducible revision pin is pending a successful baseline build.**

Official source: https://github.com/osmandapp/OsmAnd

The workflow at `.github/workflows/build-osmand-baseline.yml` checks out the official OsmAnd application plus its required companion repositories (resources, core, core-legacy, build, tools, misc), records every checked-out Git SHA in `upstream-revisions.txt`, and attempts the official `assembleNightlyFreeLegacyFatDebug` build.

The initial workflow intentionally follows the current upstream default branch to establish a baseline. This is not yet a release pin. After the first successful build, use the recorded SHAs to pin all inputs to known commits, rerun CI, and verify the artifact before proceeding to UI customization.

Do not label the unmodified upstream APK as a finished NEXT NAVI PRO release. It is only a technical baseline for validating the map/routing foundation.
