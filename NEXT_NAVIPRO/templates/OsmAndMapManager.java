package com.riccardo.roaddisplay;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads official OsmAnd vector-map packages (.obf) and installs them atomically.
 *
 * Important: this component manages data only. The app must not claim offline
 * navigation until the OsmAnd route engine is wired to these installed files and
 * a route-calculation integration test passes.
 */
public final class OsmAndMapManager {
    private static final String CATALOG_URL = "https://download.osmand.net/list.php?d=1";
    private static final String DOWNLOAD_BASE = "https://download.osmand.net/download?file=";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Pattern CATALOG_LINE = Pattern.compile(
            "^\\s*([A-Za-z0-9._-]+\\.obf\\.zip)\\s*\\|\\s*([^|]+)\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\|\\s*(.*?)\\s*$");

    private OsmAndMapManager() { }

    private static final class MapItem {
        final String fileName;
        final String updated;
        final String sizeMb;
        final String description;
        MapItem(String f, String u, String s, String d) {
            fileName = f; updated = u; sizeMb = s; description = d;
        }
        String label(File mapsDir) {
            String obf = fileName.substring(0, fileName.length() - 4);
            return (new File(mapsDir, obf).isFile() ? "✓ " : "↓ ")
                    + humanName(fileName) + " · " + sizeMb + " MB · " + updated;
        }
    }

    public static void show(Context context) {
        final Context app = context.getApplicationContext();
        final File mapsDir = new File(app.getExternalFilesDir(null) != null
                ? app.getExternalFilesDir(null) : app.getFilesDir(), "osmand/maps");
        if (!mapsDir.exists() && !mapsDir.mkdirs()) {
            new AlertDialog.Builder(context).setTitle("MAPPE OSMAND")
                    .setMessage("Impossibile creare la cartella locale per le mappe.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 12);
        root.setPadding(pad, pad, pad, 0);
        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint("Cerca paese o regione (es. Canarias)");
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(context, 48)));
        TextView status = new TextView(context);
        status.setText("Caricamento catalogo ufficiale OsmAnd…");
        status.setTextSize(13f);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(context, 44)));
        ListView list = new ListView(context);
        root.addView(list, new LinearLayout.LayoutParams(-1, dp(context, 300)));
        TextView note = new TextView(context);
        note.setText("I file .obf contengono dati vettoriali stradali. Il download, da solo, non attiva il routing offline: il motore OsmAnd deve calcolare un percorso reale. Dati cartografici © OpenStreetMap contributors (ODbL); pacchetti distribuiti da OsmAnd.");
        note.setTextSize(12f);
        root.addView(note, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ArrayList<MapItem> all = new ArrayList<>();
        final ArrayList<MapItem> visible = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_activated_1, new ArrayList<>());
        list.setAdapter(adapter);
        final int[] selected = {-1};
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setOnItemClickListener((parent, view, position, id) -> {
            selected[0] = position;
            list.setItemChecked(position, true);
        });

        Runnable filter = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (MapItem item : all) {
                String hay = (item.fileName + " " + item.description).toLowerCase(Locale.ROOT);
                if (q.isEmpty() || hay.contains(q)) visible.add(item);
            }
            Collections.sort(visible, Comparator.comparing((MapItem m) ->
                    !m.fileName.toLowerCase(Locale.ROOT).contains("canarias")));
            adapter.clear();
            for (MapItem item : visible) adapter.add(item.label(mapsDir));
            adapter.notifyDataSetChanged();
            selected[0] = -1;
            status.setText(visible.size() + " mappe nel filtro · installate: " + installedCount(mapsDir));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });

        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("MAPPE OSMAND · DOWNLOAD")
                .setView(root).setNegativeButton("CHIUDI", null)
                .setNeutralButton("AGGIORNA ELENCO", null)
                .setPositiveButton("SCARICA SELEZIONATA", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    loadCatalog(all, visible, adapter, mapsDir, status, search.getText().toString()));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (selected[0] < 0 || selected[0] >= visible.size()) {
                    status.setText("Seleziona prima una mappa dall'elenco.");
                    return;
                }
                MapItem item = visible.get(selected[0]);
                downloadAndInstall(context, item, mapsDir, status, () -> {
                    filter.run();
                    status.setText("Installata " + humanName(item.fileName)
                            + ". OsmAnd tenterà il routing offline; la disponibilità dipende dalla copertura e dal calcolo reale del percorso.");
                });
            });
            loadCatalog(all, visible, adapter, mapsDir, status, "Canarias");
        });
        dialog.show();
    }

    private static void loadCatalog(ArrayList<MapItem> all, ArrayList<MapItem> visible,
                                    ArrayAdapter<String> adapter, File mapsDir, TextView status, String query) {
        status.setText("Connessione al catalogo ufficiale OsmAnd…");
        IO.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.47 (OsmAnd map data)");
                c.setRequestProperty("Accept", "text/plain,*/*");
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK)
                    throw new IllegalStateException("Catalogo HTTP " + c.getResponseCode());
                ArrayList<MapItem> found = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new BufferedInputStream(c.getInputStream()), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher m = CATALOG_LINE.matcher(line);
                        if (m.matches()) found.add(new MapItem(m.group(1), m.group(2).trim(),
                                m.group(3), m.group(4).trim()));
                    }
                }
                if (found.isEmpty()) throw new IllegalStateException("Formato catalogo non riconosciuto");
                MAIN.post(() -> {
                    all.clear(); all.addAll(found);
                    String q = query == null || query.trim().isEmpty() ? "Canarias" : query;
                    visible.clear();
                    for (MapItem item : all) {
                        if ((item.fileName + " " + item.description).toLowerCase(Locale.ROOT)
                                .contains(q.toLowerCase(Locale.ROOT))) visible.add(item);
                    }
                    Collections.sort(visible, Comparator.comparing((MapItem m) ->
                            !m.fileName.toLowerCase(Locale.ROOT).contains("canarias")));
                    adapter.clear();
                    for (MapItem item : visible) adapter.add(item.label(mapsDir));
                    adapter.notifyDataSetChanged();
                    status.setText("Catalogo aggiornato: " + all.size() + " pacchetti · seleziona una mappa");
                });
            } catch (Exception e) {
                MAIN.post(() -> status.setText("Catalogo non disponibile: " + safeMessage(e)
                        + ". Verifica la connessione e riprova."));
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private static void downloadAndInstall(Context context, MapItem item, File mapsDir,
                                           TextView status, Runnable success) {
        ProgressDialog progress = new ProgressDialog(context);
        progress.setTitle("Download mappa OsmAnd");
        progress.setMessage("Preparazione…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        IO.execute(() -> {
            HttpURLConnection c = null;
            File archive = new File(mapsDir, item.fileName + ".part");
            File staging = new File(mapsDir, "staging-" + System.currentTimeMillis());
            try {
                String encoded = URLEncoder.encode(item.fileName, "UTF-8");
                c = (HttpURLConnection) new URL(DOWNLOAD_BASE + encoded).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(45000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.47 (OsmAnd map data)");
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK)
                    throw new IllegalStateException("Download HTTP " + c.getResponseCode());
                int total = c.getContentLength();
                MAIN.post(() -> { progress.setIndeterminate(total <= 0); if (total > 0) progress.setMax(100); });
                long read = 0;
                try (InputStream in = new BufferedInputStream(c.getInputStream());
                     FileOutputStream out = new FileOutputStream(archive)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n); read += n;
                        if (total > 0) {
                            int percent = (int) Math.min(100, read * 100L / total);
                            MAIN.post(() -> { progress.setIndeterminate(false); progress.setProgress(percent);
                                progress.setMessage("Scaricamento " + humanName(item.fileName) + " · " + percent + "%"); });
                        } else if ((read & 0x7FFFFF) < buffer.length) {
                            long mb = read / (1024 * 1024);
                            MAIN.post(() -> progress.setMessage("Scaricamento… " + mb + " MB"));
                        }
                    }
                    out.getFD().sync();
                }
                if (archive.length() < 1024) throw new IllegalStateException("Archivio troppo piccolo");
                if (!staging.mkdirs()) throw new IllegalStateException("Cartella temporanea non creata");
                File extracted = null;
                try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(archive)))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[64 * 1024];
                    while ((entry = zin.getNextEntry()) != null) {
                        String name = new File(entry.getName()).getName();
                        if (!entry.isDirectory() && name.endsWith(".obf")) {
                            File candidate = new File(staging, name);
                            try (FileOutputStream out = new FileOutputStream(candidate)) {
                                int n; while ((n = zin.read(buffer)) != -1) out.write(buffer, 0, n);
                                out.getFD().sync();
                            }
                            if (candidate.length() < 1024) throw new IllegalStateException("File OBF non valido");
                            extracted = candidate;
                            break;
                        }
                    }
                }
                if (extracted == null) throw new IllegalStateException("Nessun file .obf nell'archivio");
                File target = new File(mapsDir, extracted.getName());
                File ready = new File(mapsDir, extracted.getName() + ".new");
                File backup = new File(mapsDir, extracted.getName() + ".previous");
                if (!extracted.renameTo(ready)) throw new IllegalStateException("Impossibile preparare il file");
                if (backup.exists() && !backup.delete()) { ready.delete(); throw new IllegalStateException("Backup precedente non eliminabile"); }
                boolean hadTarget = target.exists();
                if (hadTarget && !target.renameTo(backup)) { ready.delete(); throw new IllegalStateException("Impossibile proteggere la mappa precedente"); }
                if (!ready.renameTo(target)) {
                    if (hadTarget) backup.renameTo(target);
                    throw new IllegalStateException("Installazione atomica non riuscita");
                }
                if (backup.exists()) backup.delete();
                MAIN.post(() -> { progress.dismiss(); success.run(); });
            } catch (Exception e) {
                MAIN.post(() -> {
                    progress.dismiss();
                    status.setText("Download/installazione fallita: " + safeMessage(e)
                            + ". La mappa precedente, se presente, non deve essere considerata aggiornata.");
                    new AlertDialog.Builder(context).setTitle("DOWNLOAD NON RIUSCITO")
                            .setMessage(safeMessage(e)).setPositiveButton("OK", null).show();
                });
            } finally {
                if (c != null) c.disconnect();
                if (archive.exists()) archive.delete();
                deleteTree(staging);
            }
        });
    }

    private static int installedCount(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".obf") && new File(d, n).length() > 1024);
        return files == null ? 0 : files.length;
    }
    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        f.delete();
    }
    private static int dp(Context c, int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
    private static String humanName(String file) {
        return file.replace("_europe_2.obf.zip", "").replace("_2.obf.zip", "")
                .replace('_', ' ').replace('-', ' ');
    }
    private static String safeMessage(Exception e) {
        String s = e.getMessage();
        return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }
}
