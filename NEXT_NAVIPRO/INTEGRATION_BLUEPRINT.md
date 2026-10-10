# NEXT NAVI PRO — piano di fusione incrementale

## Stato di partenza verificato

- Baseline applicativa: **ROAD DISPLAY PRO 3.41.9**, verificata leggendo `Road_Display_PRO/BUILD_VERSION.txt` all'interno dell'archivio `Road_Display_PRO_3.0.0.zip`. Il nome dell'archivio non rappresenta la versione applicativa.
- Package Android esistente: `com.riccardo.roaddisplay`.
- Modulo Android: `:app`, namespace già esistente; non ricreare il progetto.
- Sorgenti chiave presenti nell'archivio: `MainActivity.java`, `RouteMapMatcher.java`, `RoadEngine.java`, `RoadDb.java`, `SensorFusion.java`, `TripRecorder.java`, `TelemetryRecorder.java`, `TripDb.java`, `TelemetryDb.java`.
- Suite `roadtools/` presente: regressioni GPS/matching, navigazione e voce, radar, feedback utente, smoothing marker, mappe vettoriali, layout, ETA, GPX, qualità GNSS, stress navigazione.
- La relazione `OSMAND_COMPARISON_3.41.8.md` descrive correttamente i gap residui: mappe offline regionali complete, routing offline, ricerca offline, import GPX e test su dispositivo.
- La relazione `TEST_REPORT_3.41.9.txt` specifica che i test statici/stress erano stati eseguiti ma che compilazione Android e prova live su S22 Ultra non erano state eseguite in quell'ambiente. Non trattare quel report come prova di un build APK verificato oggi.

## Regola di integrazione

NEXT NAVI PRO si sviluppa **sopra questa baseline**. La branch `main` non viene sostituita da un prototipo e l'architettura esistente non viene cancellata. Ogni fase deve poter essere revisionata separatamente e deve mantenere i test esistenti.

## Architettura target

1. **Navigation UI / Ride HUD** — preservare la visualizzazione moto ad alto contrasto, portrait/landscape, velocità grande e posizione stabile, indicazioni svolta, limite legale solo se noto, avvisi curve, radar, audio, modalità NEGATIVO e regolazione automatica della luminosità.
2. **Location & Sensor Fusion** — usare la posizione GNSS con accuratezza/età del fix; integrare il matcher e la fusione sensori già esistenti senza usare bussola/IMU come sostituti della posizione GPS. Evitare salti tra carreggiate parallele e rendere il marker fluido senza accumulare ritardo.
3. **Road Graph / Map Matching** — evolvere `RouteMapMatcher` e `RoadEngine` con candidati stradali, direzione di marcia, senso unico, continuità del percorso, accuratezza GNSS e confidenza esplicita. Quando la confidenza è insufficiente, dichiararlo invece di inventare una strada o un limite.
4. **Universal Offline Map Manager** — catalogo mondiale per continente/paese/regione/città, non hardcoded a Tenerife. Ogni pacchetto dichiara area geografica, formato, versione/datasource, dimensione stimata, checksum, timestamp, licenza/attribuzione e compatibilità. Il gestore deve consentire selezione multi-regione, download riprendibile, spazio libero stimato, pausa/riprova, verifica integrità, controllo aggiornamenti, aggiornamento atomico (il vecchio pacchetto resta attivo finché il nuovo non è valido), rollback, eliminazione con conferma e indicazione di mappe in uso. Copertura e formati devono dipendere dal catalogo effettivamente disponibile, senza promettere dati inesistenti. Non pre-scaricare in blocco il server standard delle tile raster OSM.
5. **Rendering, Routing & Re-routing** — rendering vettoriale offline e routing locale devono usare formati/engine compatibili e testati: il solo download di un file OSM PBF non equivale a una mappa navigabile. Routing su grafo con sensi unici, accessi, rotatorie e profilo moto; ricalcolo solo dopo deviazione credibile, con soglie legate all'accuratezza GNSS. Il routing online rimane fallback esplicito finché il motore offline non è validato.
6. **Ricerca locale** — indice indirizzi/POI nel pacchetto regionale, risultati chiaramente distinguibili tra offline e online.
7. **Radar / POI** — conservare il database e la freschezza già implementati; indice spaziale per ridurre query e latenza, senza perdere i record validi quando un aggiornamento fallisce. Avvisi solo con posizione sufficientemente accurata e limiti legali effettivamente noti.
8. **Voice Guidance** — preservare gli annunci in due fasi e le correzioni 3.41.9; aggiungere semantica robusta delle uscite dalle rotonde e silenziamento dopo arrivo senza duplicare avvisi.
9. **Trip & Telemetry** — conservare `TripRecorder`, `TelemetryRecorder`, DB esistenti, registrazione asincrona, memoria limitata, export GPX e cancellazione sicura dei viaggi.
10. **Build & Quality Gate** — Java 17 e pipeline Gradle esistente come baseline; eseguire tutti i test `roadtools/*_test.py`, poi build Android reale. Non pubblicare APK come verificato senza un job riuscito e senza distinguere i test statici dalla prova sul dispositivo.

## Come riutilizzare OsmAnd

Non copiare indiscriminatamente l'intera applicazione o la sua UI. Estrarre e integrare solo componenti tecnici necessari dopo aver identificato moduli, dipendenze e licenze. La licenza pubblicata da OsmAnd indica codice GPLv3, mentre parte delle risorse grafiche è CC-BY-NC-ND e alcune risorse hanno licenze separate; l'integrazione del codice GPL può imporre obblighi di distribuzione del sorgente e va verificata prima di distribuire l'app. Preferire dati OSM regionali e componenti con licenza compatibile quando soddisfano il requisito, e registrare provenienza/attribuzione di ogni componente riutilizzato.

Riferimenti iniziali:
- OsmAnd repository: https://github.com/osmandapp/Osmand
- OsmAnd LICENSE: https://github.com/osmandapp/Osmand/blob/master/LICENSE
- OSM attribution: https://www.openstreetmap.org/copyright

## Sequenza di lavoro obbligatoria

### Fase 1 — baseline riproducibile
- verificare `versionName=3.41.9`, package, manifest, dipendenze e struttura reale estratta;
- eseguire la suite `roadtools/` e registrare esito/commit;
- ottenere un build Android riproducibile senza cambiare arbitrariamente SDK/Gradle;
- nessuna modifica funzionale finché questo punto non è riproducibile.

### Fase 2 — navigazione stabile
- testare e migliorare matcher e marker con i casi paralleli, curve strette, jitter, perdita GPS e ripresa del fix;
- testare ricalcolo, deviazione, rotonde, annunci, ETA, limiti ignoti e arrivo;
- mantenere i regressions test come guardrail.

### Fase 3 — motore offline universale
- scegliere un formato dati che supporti aree globali e pacchetti regionali senza imporre un limite alle Canarie/Spagna;
- costruire un catalogo aggiornabile continente → paese → regione → area locale, con pacchetti compatibili e metadati di licenza;
- implementare download riprendibili, checksum, spazio libero, aggiornamento atomico, rollback, rimozione e controllo versioni;
- implementare separatamente renderer offline, ricerca offline e routing offline: scaricare una mappa non deve essere confuso con scaricare solo le geometrie stradali;
- rendere i pacchetti radar/POI una sorgente distinta con propria data di aggiornamento, copertura e provenienza; sincronizzazione online e uso offline, senza inventare radar assenti;
- dichiarare esplicitamente quando dati o percorso non sono disponibili.

### Fase 4 — integrazione finale
- ricerca offline, POI/radar, GPX, viaggio/telemetria, voce e UI;
- stress test, crash test, build CI, installazione e prove reali su S22 Ultra;
- promuovere la versione soltanto dopo test superati e report verificabile.

## Gestione delle mappe — requisiti utente
- L'utente può scaricare una o più aree qualsiasi del catalogo disponibile (es. Canarie, Spagna, paesi UE, altri paesi o regioni globali), non soltanto una regione fissa.
- Per ogni area: dimensione stimata, spazio richiesto, download/progresso, pausa/riprova, versione installata, ultima verifica, aggiornamento disponibile, aggiorna, elimina.
- Aggiornamenti con verifica checksum e sostituzione atomica; un download interrotto o corrotto non deve cancellare la mappa valida precedente.
- Mappa, ricerca, routing, limiti stradali e radar/POI hanno coperture e aggiornamenti distinti: UI e messaggi devono mostrare ciò che è davvero installato.
- Il catalogo può essere esteso nel tempo; non si finge che ogni area abbia gli stessi dati o lo stesso livello di dettaglio.

## Criteri di accettazione non negoziabili

- Nessun dato stradale, limite legale, uscita di rotonda o ETA inventato.
- Nessuna regressione silenziosa: ogni test fallito deve essere spiegato e corretto.
- Nessuna cancellazione o sostituzione di sorgenti/test esistenti per far passare la build.
- La modalità offline è dichiarata completa solo se mappa, ricerca e routing funzionano davvero senza rete sul dispositivo.
- Le prestazioni GPS/map matching vanno valutate su log riproducibili e poi su strada; i test matematici da soli non provano la qualità della navigazione reale.
