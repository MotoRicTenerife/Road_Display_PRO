# Offline road-data preparation

The production offline target is a compact SQLite road graph generated from an OSM PBF extract, not a bulk cache of OSM raster tiles.

Recommended source for the Canary Islands:
https://download.geofabrik.de/africa/canary-islands.html

The Geofabrik extract is current OSM data in PBF format. A future preprocessing tool should:

1. read the PBF;
2. retain routable/ride-relevant highway classes;
3. retain way directionality and connectivity;
4. simplify geometry without destroying curve shape;
5. calculate/store bounding boxes;
6. store nodes/edges in a spatial index;
7. optionally add elevation;
8. export a compact SQLite database that Road Display PRO can import.

Do not package the raw PBF as if it were an Android road database. The Android runtime needs an indexed representation designed for fast nearest-road and forward-geometry queries.
