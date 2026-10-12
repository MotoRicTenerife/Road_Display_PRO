## 3.34.0 — HOME COORDINATES AUTHORITATIVE

CASA salvata dalla mappa usa esclusivamente le coordinate esatte del flag come destinazione. Il campo destinazione mostra sempre latitudine,longitudine; il reverse geocoding non può sostituire il punto salvato.


## 3.32.0 — CASA COORDINATE FIX + CLEAN NAV GLYPHS

- Fixed saved CASA navigation: a map-saved home coordinate is now addressed by the explicit `CASA` token and bypasses geocoding.
- Saved home latitude/longitude are validated before use.
- Navigation maneuver graphics are now deterministic: right turn, left turn, straight arrow, and roundabout circle with exit number.
- Removed the live routed-polyline maneuver scribble from the rider guidance glyph.
ROAD DISPLAY PRO 3.32.0

## 3.27.0 — ETA integrata nella navigazione

- ETA direttamente nel pannello di navigazione: ora di arrivo e tempo residuo.
- Profilo velocità del percorso ricavato, quando disponibile, dai `speed_limit` degli archi OSM tramite Valhalla Locate.
- Velocità di crociera stimata al di sotto del limite; i segmenti senza limite affidabile usano il tempo del router come fallback, senza inventare limiti.
- ETA ricalcolata con la progressione GPS e corretta lentamente sulla velocità reale, evitando oscillazioni dovute a singoli campioni.
- Il profilo ETA viene aggiornato anche dopo un ricalcolo del percorso e i risultati asincroni obsoleti non possono sovrascrivere il nuovo percorso.
- UI: `CONSIGLIATA` a sinistra; `LIMITE xxx` a destra con il solo `km/h` già presente, evitando un secondo `km/h`.
- Layout portrait adattivo sui display molto compatti per lasciare spazio sufficiente a NAV/ETA.
- Nessun traffico live in questa release.
- Test locali: ETA stress 100.000 casi + layout stress 54 casi, entrambi PASS.


# ROAD DISPLAY PRO 3.24.0

**CI packaging:** this compatibility archive contains the Gradle project inside `Road_Display_PRO/` because the current external CI expects that directory after extraction. The Android application version is `3.24.0`.


## 3.24.0 — validazione stradale
- Velocità consigliata dinamica: geometria reale della curva + severità + contesto della strada.
- Se disponibile, il `maxspeed` reale della strada è un limite superiore della velocità consigliata; non viene inventato quando manca.
- Acquisizione di `highway` e `maxspeed:type` dai dati OpenStreetMap per migliorare il contesto stradale.
- Gestione GPS robusta con Fused Location Provider ad alta accuratezza, fallback al LocationManager, controllo dell'età del fix e stati distinti tra posizione forte, debole e realmente assente.
- Migliorata la coerenza tra velocità consigliata e limite reale.


## 3.24.0 — Curve engine refinement, trip-map zoom e guidance
- Curve classification made deliberately conservative: severity requires agreement between heading change and radius.
- Navigation route geometry is never reversed from noisy phone bearing; OSRM route order is authoritative, preventing left/right mirroring.
- Roundabout priority extended so geometric curves cannot temporarily appear as a hairpin before a known roundabout.
- Curve beep threshold reduced to 100 m and navigation speech stages reduced to 150 / 45 / 12 m, with the final action spoken as “Ora…”.
- Curve glyphs are now distinct for light, medium, tight and hairpin bends and remain centred when mirrored.
- Trip detail map now has dedicated +/− zoom controls, with four zoom-in levels and two zoom-out levels from the fit-to-route view.


## Versione 3.23.8 — Curve/NAV layout
- Segnalazione curva isolata in card opaca dedicata.
- Curve warning e guida NAV affiancate allo stesso livello verticale.
- Eliminata la sovrapposizione tra icona/etichetta curva e testi NAV.
- Simbolo curva/tornante rigenerato con coordinate simmetriche rispetto al centro del triangolo.
- Layout adattivo portrait/landscape con fallback verticale solo per larghezze eccezionalmente ridotte.
# ROAD DISPLAY PRO 3.23.2 — TRIP DIARY / GIORNI

## Aggiornamento 3.23.2 — Diario raggruppato per giornata

La schermata VIAGGI ora raggruppa i viaggi per data: ogni giornata appare come una sola voce con numero di viaggi e chilometri totali. Toccando una data si apre una schermata dedicata con tutti i viaggi di quel giorno; da lì ogni singolo viaggio apre il dettaglio con dati e mappa GPS. Il tasto Indietro segue il percorso Dettaglio → Giornata → Viaggi → RIDE.

## Aggiornamento 3.23.1 — Diario di viaggio
- Aggiunto un vero diario persistente dei viaggi in SQLite (`trip_diary.db`), separato dal contatore giornaliero.
- Registrazione automatica solo con GPS preciso e movimento reale; nessun avvio per semplici oscillazioni GPS da fermo.
- Salvataggio della traccia GPS a punti, con statistiche di distanza, durata, media, velocità massima, quota e dislivello positivo.
- Arresto automatico dopo 5 minuti di inattività continua; i semafori e le brevi soste non interrompono il viaggio.
- Recupero sicuro dei viaggi rimasti aperti dopo un riavvio/crash del processo.
- Schermata DIARIO VIAGGI con elenco, totale km, numero punti GPS e dettaglio del singolo viaggio.
- Dettaglio con traccia GPS, partenza/arrivo, statistiche e cancellazione del viaggio.
- Tutte le scritture del registratore sono serializzate su un worker dedicato per non bloccare il thread UI/GPS.

## Verifiche 3.23.2
- Bilanciamento sorgenti Java verificato: parentesi graffe, tonde e quadre.
- Stress test Java del registratore: 50.000+ fix GPS, letture concorrenti da 8 thread, lifecycle start/stop e shutdown; nessun errore.
- Test di GPS jump: segmenti impossibili vengono ignorati senza interrompere il viaggio.
- ZIP verificato con `unzip -t`: nessun errore.
- APK Android va compilato nel workflow GitHub Actions/Android Studio, perché l'ambiente di verifica locale non contiene Gradle/Android SDK.

# ROAD DISPLAY PRO 3.22.17

## Aggiornamento 3.22.17
- Aggiunto il pulsante esplicito **ESCI** nella barra superiore della schermata RIDE.
- La chiusura richiede una conferma per evitare tocchi accidentali durante l'uso.
- La conferma chiude prima una eventuale navigazione attiva e poi termina l'app con `finishAndRemoveTask()`.



## Aggiornamenti 3.4.0
- Corretto il refresh della cache stradale dopo il download online: la mappa viene ridisegnata con le geometrie appena scaricate.
- Scaricamento iniziale delle strade entro circa 10 km dalla posizione GPS tramite Overpass API, con endpoint alternativi e aggiornamento quando la moto si sposta di circa 8 km.
- Mappa monocromatica cliccabile a schermo intero; trascinamento per esplorare i dati già scaricati; raggio visualizzato di 1000 m nella finestra RIDE.
- Pulsante meteo con aggiornamento automatico ogni 30 minuti o dopo 15 km, oltre all'aggiornamento manuale. L'avviso rosso si attiva per temporali, pioggia intensa o raffiche forti secondo soglie indicative.
- Etichette principali localizzate in base alla lingua di sistema per italiano, spagnolo e inglese.
- Simboli curve aggiornati: cerchio verde, quadrato giallo, triangolo doppio per la curva stretta e punto esclamativo per il tornante.

## Limiti da conoscere
- Il download iniziale richiede una posizione GPS valida e una connessione Internet. Gli endpoint pubblici Overpass possono essere lenti o temporaneamente non disponibili.
- La mappa è una visualizzazione vettoriale essenziale delle geometrie stradali OSM memorizzate localmente, non una cartografia professionale turn-by-turn e non garantisce copertura offline completa.
- Il meteo usa Open-Meteo; le soglie per il banner rosso sono avvisi indicativi, non certificazioni di sicurezza.
- Le velocità consigliate e la classificazione delle curve sono sperimentali e non devono essere usate come unico riferimento durante la guida.

## Build
Aprire il progetto in Android Studio oppure usare il workflow GitHub Actions `Build APK`.
# ROAD DISPLAY PRO 3.2.0

Aggiornamento: mappa vettoriale monocromatica centrale con raggio nominale di 1000 m (copertura circa 1 km x 1 km), centratura sulla posizione GPS e rotazione per mantenere la direzione di marcia verso l'alto. La mappa usa le geometrie stradali OpenStreetMap già recuperate e memorizzate localmente; non è una cartografia offline completa e può risultare vuota se i dati stradali non sono disponibili. Pulsante DEMO ridotto e simboli curve ricolorati: verde leggera, giallo media, rosso con triangolo a doppio bordo stretta e tornante con punto esclamativo.

# ROAD DISPLAY PRO 3.0.0

Package: `com.riccardo.roaddisplay`

A native Android motorcycle display designed for a handlebar-mounted phone. The current 3.0.0 project contains a real native GPS layer, a vector ride display, DEMO controls, audio warnings, online OSM road geometry acquisition and a local SQLite road-geometry cache.

## Important status / honesty statement

This release does **not** claim to contain a complete offline map of the Canary Islands. No fabricated road data is bundled. Offline curve detection works for road geometry that has actually been acquired and stored in the local database. If no local road geometry is available, the app reports `DATI STRADALI NON DISPONIBILI` rather than inventing a curve.

The online provider currently uses an Overpass API endpoint to obtain OSM road ways around the current GPS position. The local cache stores their geometry in SQLite. This is deliberately separate from the UI so a future dedicated offline provider can replace it.

OpenStreetMap data is used under the ODbL. Do not bulk-download the standard OSM raster tile server for offline use; OSMF's tile policy explicitly prohibits prefetch/bulk offline use of `tile.openstreetmap.org`. For a full regional offline package, use an OSM-derived dataset/provider that permits offline distribution or self-host the required data.

## Current features

- RIDE is the default mode.
- DEMO can be opened from the RIDE screen.
- Portrait and landscape layouts.
- Native Android GPS using `LocationManager`.
- Speed, GPS accuracy/state and road-data state.
- Road geometry from OSM/Overpass when online.
- Local SQLite cache for previously acquired road geometry.
- Curve classification: LEGGERA / MEDIA / STRETTA / TORNANTE.
- Left/right mirrored vector graphics.
- Warning triangle for tight curves and hairpins.
- Professional arrow: black outline + yellow fill.
- Audio: 1 / 2 / 3 short beeps; hairpin = long + 2 short.
- 150 m crossing threshold with re-arm when the curve moves back beyond 150 m.
- Keep-screen-on and immersive fullscreen.
- No fake recommended speed when no usable radius is available.
- Demo sliders for speed, distance, grade and recommended speed.

## What is intentionally not claimed yet

- No validated safety-certified recommended-speed algorithm.
- No complete Canary Islands offline road database bundled in the APK.
- No turn-by-turn navigation UI.
- No elevation database bundled. OSM road geometry normally does not provide reliable elevation for this purpose, so RIDE does not fabricate grade data.
- No claim that an OSM way is always the exact continuation through an intersection; a future graph/stitching layer should improve this.

## Build without a PC

### Option A: AndroidIDE on the phone

AndroidIDE can open and build Gradle-based Android projects directly on Android devices. The original AndroidIDE project is archived, so use it only as a practical build tool and obtain it from a trusted source. The project is deliberately dependency-light and uses classic Android APIs to maximize compatibility.

1. Install AndroidIDE from a trusted source.
2. Install its JDK/SDK/build tools.
3. Copy/extract this project on the phone.
4. Open the project folder.
5. Let Gradle sync.
6. Run/build `assembleDebug`.
7. Install the generated APK.

### Option B: GitHub Actions (online build)

This project includes `.github/workflows/build-apk.yml`.

You can create a GitHub repository from your phone, upload the project files, and push them to `main`. GitHub Actions will build the debug APK in the cloud and publish it as a workflow artifact. This is the easiest PC-free route if AndroidIDE gives trouble.

The APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions artifact is named:

`road-display-pro-3.17.0-debug`

## Android permissions

The app requests location permission because curve anticipation depends on the current GPS position. Internet permission is required for online OSM road geometry retrieval.

## DEMO controls

Tap the DEMO area from RIDE. In DEMO:

- tap the upper area to cycle curve type;
- tap the direction area to switch left/right;
- use the four lower horizontal controls for speed, distance, grade and recommended speed.

All values are marked/sourced as simulated. DEMO must never be interpreted as a real safety recommendation.

## Online / offline behaviour

Online:

`GPS -> current position -> Overpass -> OSM road geometry -> curve engine -> local SQLite cache -> display/audio`

Offline:

`GPS -> current position -> local SQLite cache -> curve engine -> display/audio`

If the local database has no usable road geometry, the app displays `DATI STRADALI NON DISPONIBILI`.

## OSM / offline data strategy

Geofabrik publishes a current Canary Islands OSM extract in PBF format. A PBF is an excellent source for a future full offline database, but it is not copied blindly into the APK. A production offline package should preprocess the PBF into a compact road graph/SQLite format containing only the geometry and tags required by ROAD DISPLAY PRO.

For the first usable release, automatic online acquisition + local caching avoids shipping fabricated or stale road data. The architecture leaves the database/provider boundary ready for a prebuilt Canary Islands road package.

## Recommended next engineering phase

1. Replace individual OSM ways with a connected road graph.
2. Stitch ways at intersections and preserve directionality.
3. Add a proper offline PBF/SQLite preprocessing pipeline.
4. Add an elevation source/database for grade.
5. Improve curve segmentation and radius fitting with smoothing.
6. Add route-aware look-ahead and S-curve handling.
7. Validate the recommended-speed algorithm against real road geometry before presenting it as anything more than informational.
8. Add a dedicated offline package for Tenerife/Canary Islands generated from a dated OSM extract.

## Testing checklist

### DEMO
- 4 curve classes
- left/right
- speed 0–180
- distance 0–500 m
- grade negative/zero/positive
- recommended speed
- audio threshold
- portrait/landscape


### ROAD DISPLAY PRO 3.34.7 — Fused GPS hardening
- Il tracking continuo usa `FusedLocationProviderClient` con `PRIORITY_HIGH_ACCURACY`, intervallo nominale 1 s e minimo 500 ms.
- `GPS INSUFFICIENTE` non viene più attivato solo perché il fix arriva dal provider `fused`: Android può fondere GNSS, Wi-Fi e rete in un singolo `Location` valido.
- Un fix fused/GNSS fresco fino a 100 m mantiene lo stato GPS vivo; l'elaborazione stradale resta più severa (50 m) per non inventare curve o limiti.
- `getLastLocation()` viene usato soltanto come seed iniziale; età e accuratezza del fix continuano a essere verificate.
- `LocationManager.GPS_PROVIDER` resta come fallback e per la diagnostica dei satelliti.

### GPS
- permission
- GPS disabled
- weak accuracy
- recovery
- speed
- road data available/unavailable

### Online
- road retrieval
- cache creation
- connection loss without crash

### Offline
- disable Wi-Fi and mobile data
- previously cached road geometry remains usable
- uncached road shows `DATI STRADALI NON DISPONIBILI`

## Release signing

For a personal test APK, the debug build is sufficient. For a distributable release APK, create and protect your own keystore and configure Gradle signing. Never put a private production keystore or its passwords into a public repository.


## ROAD DISPLAY PRO 3.1.0 — leggibilità e demo adattiva

- Audio curve attivo all'avvio, senza schermata di attivazione.
- Schermata DEMO ridisegnata con controlli e cursori adattivi a orientamento verticale/orizzontale; pulsante RIDE sempre disponibile e tasto Indietro supportato.
- Velocità numerica più grande, contrasto elevato, ora, satelliti GPS/precisione stimata e stato dei dati stradali.
- Luminosità dello schermo impostata al massimo mentre l'app è in primo piano per migliorare la leggibilità all'aperto.
- Il download delle geometrie stradali OpenStreetMap tramite Overpass resta dipendente da GPS valido, connessione Internet e disponibilità del servizio; non è una banca dati offline completa.
- La velocità consigliata è sperimentale e non è un'indicazione di sicurezza certificata.


## 3.4.0 — map loading and layout reliability
- Map loading no longer depends on a high-accuracy GPS fix: GPS, network and passive providers are requested, and last-known/saved position is used immediately.
- If no system position is available, an approximate IP-based location is attempted via ipwho.is. This may be several kilometres off and is explicitly labelled approximate; it is used for map display only, never curve alerts. The request sends the device's public IP to the geolocation provider.
- Overpass requests use POST, an identifying User-Agent, multiple public endpoints sequentially and smaller fallback radii (10 km, 5 km, 2.5 km). Empty HTTP-200 responses are treated as failures. Public services can still be unavailable or rate-limited.
- The map status is placed at the lower edge so the centred motorcycle marker does not cover it; the marker is smaller. Weather banners use the space between METEO and DEMO instead of covering ride information.


## Version 3.5.0 update
- Added visible raster map tiles with two sequential tile providers (OpenStreetMap standard tiles, then CARTO dark tiles), viewport-only requests, local cache and visible attribution. Public endpoints are best-effort; they do not guarantee service availability.
- Reworked RIDE layout: speed is centered and enlarged, grows again above 90 km/h; map occupies the former speed area.
- Added ambient-light based activity brightness and restoration of the prior window brightness when leaving the app.
- Air temperature is shown next to curve information.


## ROAD DISPLAY PRO 3.17.0
- Demo mode removed and replaced by a Trips view with daily distance history and daily/monthly/yearly totals from the app's first-install date.
- Main map zoom increased; full-screen map supports pinch zoom and rotation-following travel heading, with the GPS marker staying at its map coordinate while panning.
- Weather temperature and a compact condition symbol sit below the weather button; portrait weather banner uses two bounded lines.
- Curve detection thresholds/look-ahead and OSM road-type coverage adjusted. Road geometry coverage and GPS quality still affect detection; validate carefully before relying on it while riding.
- Custom dark/yellow road-warning launcher icon.


## 3.9.0 – mappa, cache, velocità e radar
- Zoom +/− e pinch nella mappa principale e nella mappa full-screen.
- Ancora cartografica aggiornata solo dopo movimento significativo per ridurre lo sfarfallio.
- Cache persistente delle tessere visualizzate; uso di tessere antenate durante il caricamento per evitare quadratini neri.
- Fallback raster OSM/CARTO. I servizi pubblici non vengono usati per pre-scaricare intere aree: la cache riguarda le tessere effettivamente visualizzate e un piccolo margine.
- Display velocità a 7 segmenti, soglie colore configurabili toccando la velocità.
- Limite OSM visualizzato sopra la velocità e priorità al superamento del limite disponibile.
- Rilevamento dei nodi OSM `highway=speed_camera`; a 300 m: cinque lampeggi della velocità, due beep da 2 secondi e indicatore RADAR per 5 secondi.


## ROAD DISPLAY PRO 3.17.0 map/GPS hardening
- Weather requests are made only from a valid GPS fix on the device; no IP geolocation fallback is used for weather.
- Raster tiles use a persistent local cache, a stable last-displayed tile cache, a maximum of two concurrent tile downloads, and sequential provider fallback.
- Primary raster provider: OpenStreetMap Standard. Fallbacks: OSM France, HOT, and CyclOSM Lite, subject to each provider's usage policy.
- Tile loading remains viewport-driven with a small visual margin; the app does not bulk-prefetch or offer offline downloading from public OSM tile servers.
- Road-data download is retriggered when the first accurate GPS fix replaces a coarse location, in addition to distance/time refresh rules.
- Radar proximity is filtered by travel direction when heading/speed are reliable.
- UI position refresh is more fluid while road-data processing remains throttled.


## Version 3.22.16 — HARDENING
- Unified navigation route/step synchronization with a single navigation lock.
- Route and maneuver lists are updated atomically as one coherent state.
- Curve detection, route deviation, rerouting, navigation distance, and route-map reads use the same lock.
- No UI, tachometer, TTS wording, map design, or ESCI behavior intentionally changed.


## ROAD DISPLAY PRO 3.22.20 — Full-screen map stability
- Full-screen map rewritten around a geographic viewport center; GPS updates no longer re-anchor a user-panned map.
- Removed inertial fling from the full-screen map: finger movement maps directly to geographic movement and stops immediately on release.
- Vehicle heading is frozen while browsing the full-screen map; RECENTRA GPS restores live heading.
- Tap any point on the full-screen map to place a visible destination flag; DESTINAZIONE QUI uses the selected coordinates.
- Road-data storage/download radius increased from 10/5/2.5 km to 20/10/5 km fallback sequence.
- Full-screen raster cache remains persistent on disk with RAM LRU and parent-tile fallback.


## 3.23.8 — Roundabout, sparse voice guidance e traccia GPS Web Mercator
- Landscape layout intentionally unchanged.
- Portrait navigation/curve band starts below the weather temperature/icon zone.
- Portrait navigation band is slightly shorter and more balanced to preserve readability while guaranteeing a clear weather area.

## 3.23.8 — Build fix + centred curve glyphs
- Fixed MainActivity scope for roundabout helper methods used by GPS processing.
- Preserved real-routing roundabout handling and sparse voice guidance.
- Added centred curve glyphs inside the light/medium circle/square symbols.
- Preserved Web Mercator alignment for trip-summary GPS traces.


## 3.26.0 Road Confidence Engine
- OSM road geometry remains the primary local source.
- Valhalla public locate is used only as a low-rate cross-check (not a hard dependency) to identify the current OSM way and speed limit when available.
- Curve candidates are filtered by current-road match, GPS lateral distance, heading consistency and a hysteresis lock.
- Roundabouts and explicit OSM road attributes are preserved for future deterministic classification.
- If reliable road data are unavailable, the app must not invent a curve or legal speed limit.

The public Valhalla demo is subject to fair-use/rate limits; ROAD DISPLAY PRO therefore treats it as a confidence cross-check, never as its sole navigation backend.


## 3.26.1 CI-FIX

Packaging/CI fix: the project is distributed inside the top-level `Road_Display_PRO/` directory, matching legacy unzip workflows that expect that path. The GitHub Actions workflow also auto-detects `settings.gradle` whether the project is at repository root or inside a subdirectory. Artifact name updated to `road-display-pro-3.26.1-debug`. No road-engine behavior changes are included in this release.


## 3.26.3 — Confidence & Navigation Hardening
- Route curve confidence now depends on GPS-to-route lateral distance and heading instead of a fixed 0.90 value.
- Route curve recommended speed is capped by the real matched/local speed limit when available.
- If a recent Valhalla matched way has no usable local curve candidate, the engine falls back to nearby OSM geometry instead of suppressing all candidates.
- Arrival detection is tied to the destination and the final routed geometry; on confirmed arrival navigation terminates completely and no further maneuver/reroute guidance is issued.
- Version code 467.

## 3.28.2 — SPEED LIMIT + SENSOR TELEMETRY HARDENED

- Live speed-limit display uses real road data when available.
- Speed-limit sources: local OSM `maxspeed`, direction-aware `maxspeed:forward` / `maxspeed:backward`, and Valhalla current-edge `speed_limit` fallback.
- During active navigation, the routed speed-limit profile is used as a fallback when the immediate road match is temporarily unavailable.
- Unknown legal limits are displayed explicitly as `LIMITE --`; the app never substitutes a guessed road-class/national limit.
- Curve prediction is disabled from the live display. Turn-by-turn navigation and roundabout instructions remain authoritative.
- Rerouting retains direction/bearing/radius constraints and OSRM `continue_straight=true` to reduce wrong-way/parallel-carriageway snapping.
- Added the parked 3.28 sensor-fusion/calibration/telemetry components without replacing the hardened navigation core.
- Fixed ETA/profile lock ordering so navigation-lock and ETA-lock acquisition remains consistent.


## 3.28.5 — NAV CORE COMPILE HARDENING

- Fixed the live speed-limit fallback to call `routeProgressMeters()` and `etaLimitAt()` on the `RideView` owner where those methods are actually defined.
- Added null-safe access when the view is not yet initialized.
- CI source preflight now rejects the previous incorrect `RoadEngine` method owner before Gradle compilation.
- Version/workflow/artifact metadata updated consistently to 3.28.5.
- Re-ran navigation, layout, ETA and route-index stress checks.


## 3.28.3 — DESTINATION SEARCH UX
- IME Done/Enter on destination closes the keyboard and immediately launches destination search.
- Search results combine Photon and Nominatim, are deduplicated, and are ordered by distance from the current GPS position when available.
- Results show place name/address and distance; user selects a result and presses VAI.
- Selected geocoder coordinates are passed directly to routing, avoiding a second geocoding ambiguity.
- Selected place name is retained as the navigation destination label.
- No geocoder result is silently invented; empty results remain an explicit failure.
## 3.31.0 — NAVIGATION ENGINE REWORK
- Valhalla-first turn-by-turn routing with OSRM fallback.
- Route progress and maneuver distances anchored to route geometry/shape indices.
- Explicit roundabout enter/exit guidance with exit number and outgoing road.
- Explicit left/right/straight fork guidance.
- Pure road bends/slight geometry changes are no longer presented as turns.
- Route matching uses distance + travel heading continuity and accuracy-aware rerouting.
- Rerouting uses shorter network timeouts and a dynamic off-route threshold.
- Navigation guidance glyph is generated from the actual routed geometry rather than a generic arrow.
- GPS fixes used for navigation must be fresh and precise; stale fixes are not treated as live GPS.


### ROAD DISPLAY PRO 3.35.0 — Road-locked navigation engine

This release replaces single-point route projection with a local sequence map matcher. During active navigation, the raw fused/GNSS position is kept separate from the displayed road-locked position. The matcher keeps the latest fixes and uses candidate route segments, distance, heading, speed/time continuity and monotonic route progress in a Viterbi-style dynamic program. A single GNSS jump, tunnel transition or parallel-carriageway ambiguity cannot move the displayed marker to a different road or immediately trigger rerouting. Rerouting requires persistent off-route evidence.

The implementation follows the public design principles documented by Google Navigation SDK (raw versus road-snapped location), GraphHopper map matching (candidate states + Viterbi sequence matching), and Valhalla/OSRM map matching. No proprietary Google/Waze/Sygic code is copied or embedded.


## 3.35.2 — Map marker projection fix
- The active-navigation position marker now uses exactly the same render bearing and Web-Mercator screen transform as the route/map renderer.
- Prevents the GPS marker from appearing laterally displaced from the yellow route when `mapBearing` and `navigationMatchedBearing` differ.
- Route-lock/Viterbi matcher from 3.35.x is preserved.


## 3.35.3 — navigation / speed-limit layout
- Landscape: navigation information is confined to the left column; the speed-limit sign occupies the dedicated lane between navigation and the tachometer.
- Portrait: navigation information is confined to the left side of the upper band; the speed-limit sign is immediately to its right.
- Removed the previous speed-limit badge from the tachometer renderer to prevent overlap and duplicate rendering.


## 3.35.5 — Android Location API compatibility fix

Removed the unavailable `Location.hasBearingAccuracyDegrees()` / `getBearingAccuracyDegrees()` calls from the route matcher. Heading confidence now uses a stable 35° sigma, preserving the route-lock logic while remaining compatible with the project Android SDK toolchain. CI preflight explicitly rejects those API calls.


## 3.36.0 — Map interaction, speed-limit readability and navigation resilience

- Speed-limit sign is a primary RIDE datum and is shown whenever a real road limit is available, both during active navigation and normal riding.
- Landscape and portrait speed-limit badges have a larger physical circle and corrected text units so the numeric limit remains entirely inside the white field.
- Full-screen map supports one-finger pan plus two-finger zoom and rotation. Recenter restores heading-up mode so the rider direction is at the top.
- Trip maps support panning and two-finger rotation, in addition to zoom.
- Navigation guidance is no longer suppressed by a transiently narrow layout calculation; it always gets a visible fallback guidance area while navigation is active.
- Telemetry records are retained for diagnostic and future ride-analysis use: GPS position/speed/bearing/accuracy/altitude, fused roll/pitch/yaw/gyro/accelerometer data, speed limit and matched road identifier.

### Offline map architecture research

The current raster OSM tile cache is intentionally limited to tiles actively viewed. The OpenStreetMap Foundation prohibits bulk prefetch/offline downloading from `tile.openstreetmap.org`; a true offline-map feature therefore requires a provider/package that explicitly permits offline use. The planned architecture is a vector-map renderer (MapLibre Native) with downloadable offline regions and an independent local routing graph, rather than scraping OSM raster tiles.

The navigation matcher remains route-locked and Viterbi-style. A future offline routing package will use OSM-derived regional data and preserve the motorcycle routing profile and speed-limit metadata.


## 3.37.1 — Phase B1 vector map foundation
- Added MapLibre Native Android OpenGL renderer with OpenFreeMap Liberty vector style as the full-screen map renderer.
- The existing Canvas/raster map remains the hard fallback if MapLibre/native GPU/style initialization fails.
- Full-screen map gestures are delegated to MapLibre when the vector renderer is ready; camera state is synchronized back into ROAD DISPLAY PRO.
- Manual two-finger rotation direction is corrected: the map follows the finger rotation instead of rotating opposite to it.
- Existing recenter/heading-up logic is preserved.
- Added a Spain-specific CartoCiudad search source alongside Photon and Nominatim to improve address coverage.
- True offline vector packages and local offline routing are intentionally not claimed in this version; those are Phase B2.
- Added vector-map stress/static checks and Java delimiter-balance preflight.

## 3.39.4 — stable online maps + larger cache
This release deliberately returns to the pre-offline-map architecture: no MapLibre offline-region download UI and no Valhalla Mobile dependency. Online raster map tiles retain a larger in-memory cache (192 MiB per tile cache) and a 30-day default disk freshness window, reducing repeated downloads while preserving the existing online map behavior.


## 3.40.2 — Navigazione, ricerca e stabilizzazione
- Demo rimossa definitivamente dal codice attivo.
- GPX importato tramite Android Storage Access Framework e memorizzato nell'area privata dell'app.
- Gestione file GPX: elenco, dimensione, esportazione copia, eliminazione.
- Esportazione delle tracce registrate direttamente dal dettaglio viaggio in GPX 1.1.
- Gestione memoria: spazio mappe, GPX, dati app, cache temporanea e spazio libero.
- Pulsante per svuotare esclusivamente la cache mappe.
- Cache bitmap mappe ridotta a 96 MB per ciascuna cache (192 MB complessivi) per contenere la pressione RAM, mantenendo 30 giorni di persistenza su disco.
- Correzione del rilascio delle cache mappe in `onDestroy()`.
- La registrazione viaggi resta persistente in SQLite e la cancellazione di un viaggio elimina anche i suoi punti GPS.


## 3.40.4 — Speed-limit RIDE fix
- Corretto il percorso dati del limite di velocità quando la navigazione è spenta: il match stradale Valhalla ora può fornire il limite anche se la piccola cache OSM locale è temporaneamente vuota/in aggiornamento.
- Il limite proveniente dal match di rete viene mantenuto solo per 30 s e solo entro 150 m dalla posizione che lo ha generato, evitando di trascinare un limite vecchio dopo un salto GPS.
- Nessun limite viene inventato: se non esiste un dato stradale reale valido, il badge resta assente.

## Version 3.41.5 — Sunlight display test
- Adds a gear button on the main ride screen to open display settings.
- Display theme choices: AUTO (ambient light sensor), SCURO (always dark), NEGATIVO (always inverted).
- AUTO enters negative rendering at 25,000 lux and returns to dark at 12,000 lux to reduce rapid switching.
- The existing automatic screen-brightness behavior is retained separately.
- This is an experimental visual mode: verify alert colors and legibility on the mounted phone in daylight before relying on it while riding.
- Automated source/regression tests pass; Android Gradle compilation was not available in the authoring environment.


## Version 3.41.6 — High-contrast map

The expanded MapLibre map now applies a dedicated high-contrast style: white background and polygon fills, black road/transport lines, and black labels with a white halo. Other linework is gray to preserve visual hierarchy. MapLibre symbol icons are left unchanged; the live GPS arrow and radar markers remain overlaid by the existing RideView renderer. This is a source-level implementation and still requires Android compilation and device verification. The compact map continues to use the existing raster tile path, so this change is applied to the expanded vector map only.


## Revisione 3.41.8 — confronto OsmAnd
Corretto il verso della rotazione a due dita sulla mappa principale e sulla mappa del viaggio. La revisione non dichiara mappe né routing offline completi. Vedere `OSMAND_COMPARISON_3.41.8.md` per le lacune e la roadmap.


## Revisione 3.41.9 — feedback prova su strada
- Dimezzate le soglie automatiche della modalità NEGATIVO: ingresso 12.500 lux, uscita 6.000 lux.
- Ridotti gli annunci di manovra a preavviso + richiamo finale, evitando la sequenza ripetitiva di annunci intermedi. Le false svolte su strada con lo stesso nome restano filtrate, preservando rotonde e bivi reali.
- Non si usa più il limite campionato dal profilo ETA per annunciare il limite corrente: se la strada cambia e il nuovo limite è ignoto, l'app annuncia la fine del limite noto invece di ripetere quello precedente.
- Stile cartografico vettoriale chiaro Positron; rimossa l'applicazione forzata del colore nero a tutte le strade.
- Da verificare ancora su dispositivo: rendering MapLibre online, copertura rete e precisione dei punti GPS. La compilazione Android non è certificata da questi test statici.
