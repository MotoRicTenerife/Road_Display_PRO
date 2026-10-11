# Third-party notices — OsmAnd routing integration

## OsmAnd Java routing engine

- Upstream project: https://github.com/osmandapp/Osmand
- Pinned source revision: cacaa5fe1499af8806210de6f81293f925847043
- License: GNU General Public License v3.0 (GPL-3.0), as stated by the upstream project.
- Used components: OsmAnd-java routing classes/resources and the corresponding OsmAnd-shared Android runtime.
- ROAD DISPLAY PRO integration code: NEXT_NAVIPRO/templates/OsmAndOfflineRouter.java, NEXT_NAVIPRO/templates/PlatformUtil.java, and the integration patch in NEXT_NAVIPRO/apply_online_navigation_patch.py.

The exact upstream source revision and its license must accompany any distributed build. Because GPL-3.0 applies to the integrated OsmAnd code, anyone distributing a combined ROAD DISPLAY PRO binary must review and satisfy the applicable GPL source, license, and notice requirements for the complete combined work. Do not distribute an APK as a closed/proprietary binary while relying on this integration without resolving those obligations.

## OpenStreetMap data

OsmAnd map packages contain OpenStreetMap-derived data. Attribution: © OpenStreetMap contributors, available under the Open Database License (ODbL). See https://www.openstreetmap.org/copyright.

## Status

This notice records provenance and does not itself establish that offline route calculation works. ROAD DISPLAY PRO must successfully calculate and follow a route using a downloaded .obf map before offline navigation can be described as verified. A successful APK build is not a substitute for that runtime test.
