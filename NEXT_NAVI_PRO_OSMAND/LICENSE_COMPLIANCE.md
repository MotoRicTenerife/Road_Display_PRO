# OsmAnd derivative — license and attribution checklist

This is an engineering checklist, not legal advice.

## Before copying or modifying upstream source
- Read the current upstream LICENSE and every relevant third-party license.
- Preserve copyright and license notices.
- Identify GPL-3.0-covered code and prepare to provide the corresponding source code and license to recipients when distributing binaries.
- Keep a source offer / source archive process for each distributed build.
- Track modifications to upstream files.
- Audit native libraries, map data, voices, fonts, icons and other assets separately.

## UI and artwork warning
The upstream OsmAnd license file states that much of the UI/UX artwork is under CC-BY-NC-ND 4.0, with additional restrictions and some proprietary assets. Do **not** assume that the ability to modify GPL source grants permission to reuse all upstream graphics or publish them in a derivative app. Build NEXT NAVI PRO's visual identity from original assets and verify asset-specific permissions before distribution.

## Map data
- Preserve OpenStreetMap attribution and comply with ODbL requirements for the data and any derived database.
- Regional map downloads should use OsmAnd's supported data flow; do not bulk-prefetch the public OSM raster tile server.
- Record the provenance and date of any separately bundled dataset.

## Release gate
No public APK or store release until the source, licenses, notices, map-data attribution, voices and visual assets have been audited for the exact pinned upstream revision.
