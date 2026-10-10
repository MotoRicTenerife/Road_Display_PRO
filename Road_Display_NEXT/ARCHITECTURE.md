# Architettura prevista

## Moduli

- `location/`: ricezione GPS, qualità/accuratezza, heading e timestamp.
- `motion/`: filtro temporale e interpolazione del marker tra fix reali.
- `map/`: rendering stradale con palette leggibile e trasformazione coerente tra mappa, route e marker.
- `road/`: map-matching, segmenti OSM, continuità su strade parallele e dati mancanti espliciti.
- `routing/`: percorso, deviazioni e ricalcolo; il motore concreto sarà scelto dopo una prova d'integrazione offline.
- `guidance/`: istruzioni di svolta reali, deduplicazione e anticipo.
- `speedlimit/`: transizioni del limite con stato noto/sconosciuto e fine del limite.
- `cockpit/`: UI RIDE/DEMO, portrait/landscape, velocità e simboli di curva.
- `settings/`: luminosità automatica, modalità negativa/sole e preferenze.
- `storage/`: tracce e dati del viaggio, export GPX e cancellazione controllata.
- `test/`: test unitari e regressione.

## Marker GPS senza scatti

1. Conservare ogni fix originale con latitudine, longitudine, timestamp e accuratezza.
2. Rifiutare o ridurre il peso di fix vecchi o imprecisi; non mascherare un GPS insufficiente.
3. Usare un filtro con velocità/direzione plausibili per ridurre il rumore.
4. Animare il marker tra fix validi usando il tempo monotono, senza cambiare la posizione misurata archiviata.
5. Agganciare il marker alla geometria solo quando la confidenza è sufficiente; evitare snap aggressivi su strade parallele.
6. Quando manca un fix nuovo, l'interpolazione è solo visiva e limitata nel tempo; poi indicare GPS debole invece di proseguire indefinitamente.
7. Mappa, route e marker devono usare la stessa trasformazione e lo stesso bearing.

## Regole di navigazione

- La permanenza sullo stesso percorso non genera una falsa istruzione “svolta a destra/sinistra”.
- Ogni manovra ha un identificatore/stato per evitare annunci ripetuti in sequenza.
- Una deviazione reale può avviare ricalcolo; una rotonda è trattata separatamente da una curva stradale.
- Nessun limite, curva o tratto stradale viene inventato: i dati sconosciuti rimangono sconosciuti.

## Qualità prima del rilascio

- Test di interpolazione, GPS rumoroso, fix persi, inversione del bearing, strade parallele.
- Test di limiti noti -> sconosciuti e noti -> nuovo limite.
- Test che impediscono sovrapposizioni del pannello limite e delle istruzioni in portrait/landscape.
- Build CI e test su S22 Ultra; prova su strada solo dopo verifica statica e senza usare il telefono in movimento da parte del conducente.
