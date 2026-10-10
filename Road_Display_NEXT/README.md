# Road Display NEXT

Nuova linea di sviluppo per Road Display PRO, progettata per il cockpit motociclistico della Yamaha Tricity 300.

## Obiettivo

Costruire un'app Android modulare, verificabile e fluida, mantenendo la vecchia app intatta su `main`. Questa cartella è una prima base indipendente: non è ancora una build pronta per l'uso su strada.

## Priorità

1. **Marker GPS fluido**: separare le misure GPS grezze dalla posizione visualizzata; filtraggio e interpolazione temporale, senza inventare posizione o precisione.
2. **Aggancio alla strada**: scegliere il segmento stradale plausibile usando posizione, accuratezza, direzione e continuità del percorso; evitare salti su strade parallele.
3. **Cartografia leggibile**: tema chiaro/scuro con strade visibili, route distinta dal marker; nessuna mappa nera senza geometria leggibile.
4. **Navigazione affidabile**: distinguere continuazione sulla stessa strada da svolta reale; istruzioni deduplicate e anticipate.
5. **Limiti di velocità**: quando un limite noto termina e quello successivo è sconosciuto, annunciare la fine del limite noto; non trascinare il vecchio valore.
6. **Cockpit**: portrait/landscape, velocità grande e fissa, pannello limite non sovrapposto, modalità negativa per il sole, pulsante impostazioni e luminosità automatica.
7. **Test e CI**: test unitari, regressioni su GPS/route/limiti/audio, build Android e APK pubblicato come artifact solo se la build riesce.

## Stato iniziale

- La vecchia app resta su `main` e non viene sovrascritta.
- Questo scaffold è sperimentale e non va usato come navigatore finché i test e le prove sul dispositivo non sono superati.
- Package previsto: `com.riccardo.roaddisplay`.
- Dispositivo di riferimento: Samsung Galaxy S22 Ultra; mantenere compatibilità Android anche con il Note 10 Plus.

## Sorgenti e licenze

OpenStreetMap è la base dati cartografica da valutare con corretta attribuzione. OsmAnd è il progetto di riferimento per studiare rendering, GPS e navigazione offline; non si copiano alla cieca codice, icone, stili o risorse. Prima di integrare codice OsmAnd bisogna verificare la licenza di ogni componente e rispettare GPLv3 e le restrizioni separate su artwork/risorse. Vedi `LICENSE_POLICY.md`.

## Build

La struttura Android iniziale è in questa cartella. La build reale va verificata in CI prima di distribuire un APK.
