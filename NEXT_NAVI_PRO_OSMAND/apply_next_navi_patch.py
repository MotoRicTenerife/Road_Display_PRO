#!/usr/bin/env python3
"""Apply the first in-process NEXT NAVI PRO HUD patch to the pinned OsmAnd source."""
from pathlib import Path

root = Path("android")
activity = root / "OsmAnd/src/net/osmand/plus/activities/MapActivity.java"
source = activity.read_text(encoding="utf-8")

field_marker = "\tprivate KeyEventHelper keyEventHelper;"
field_patch = field_marker + "\n\tprivate NextNaviProOverlay nextNaviProOverlay;"
if "private NextNaviProOverlay nextNaviProOverlay;" not in source:
    if source.count(field_marker) != 1:
        raise SystemExit("Cannot safely locate MapActivity field insertion point")
    source = source.replace(field_marker, field_patch, 1)

create_marker = "\t\tsetContentView(R.layout.main);"
create_patch = create_marker + "\n\t\tnextNaviProOverlay = new NextNaviProOverlay(this, app);\n\t\tnextNaviProOverlay.install();"
if "nextNaviProOverlay.install();" not in source:
    if source.count(create_marker) != 1:
        raise SystemExit("Cannot safely locate MapActivity content initialization")
    source = source.replace(create_marker, create_patch, 1)

destroy_marker = "\tprotected void onDestroy() {\n\t\tsuper.onDestroy();"
destroy_patch = "\tprotected void onDestroy() {\n\t\tif (nextNaviProOverlay != null) {\n\t\t\tnextNaviProOverlay.dispose();\n\t\t\tnextNaviProOverlay = null;\n\t\t}\n\t\tsuper.onDestroy();"
if "nextNaviProOverlay.dispose();" not in source:
    if source.count(destroy_marker) != 1:
        raise SystemExit("Cannot safely locate MapActivity destruction point")
    source = source.replace(destroy_marker, destroy_patch, 1)

activity.write_text(source, encoding="utf-8")
print("Patched MapActivity to start the in-process NEXT NAVI PRO HUD.")

overlay = root / "OsmAnd/src/net/osmand/plus/activities/NextNaviProOverlay.java"
overlay.parent.mkdir(parents=True, exist_ok=True)
overlay.write_text(r'''package net.osmand.plus.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.osmand.Location;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.routing.NextDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;

import java.util.Locale;

/**
 * NEXT NAVI PRO is a presentation layer over OsmAnd's own map and navigation state.
 * It intentionally owns no map, routing, speed-limit, radar or road database.
 */
final class NextNaviProOverlay {
    private static final int PANEL = Color.rgb(12, 16, 21);
    private static final int WHITE = Color.WHITE;
    private static final int YELLOW = Color.rgb(255, 220, 40);
    private static final long REFRESH_MS = 500L;

    private final MapActivity activity;
    private final OsmandApplication app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View overlay;
    private View nativeHud;
    private Button returnButton;
    private TextView speedValue;
    private TextView gpsValue;
    private TextView maneuverValue;
    private TextView distanceValue;
    private boolean disposed;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (disposed) {
                return;
            }
            updateFromOsmAnd();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    NextNaviProOverlay(MapActivity activity, OsmandApplication app) {
        this.activity = activity;
        this.app = app;
    }

    void install() {
        View map = activity.findViewById(R.id.map_view_with_layers);
        if (map == null || !(map.getParent() instanceof FrameLayout)) {
            throw new IllegalStateException("OsmAnd map container not found; refusing to show an empty NEXT NAVI PRO screen");
        }
        FrameLayout container = (FrameLayout) map.getParent();
        nativeHud = activity.findViewById(R.id.map_hud_container);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setClickable(false);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        top.setBackgroundColor(0xE610151B);

        LinearLayout instructionColumn = new LinearLayout(activity);
        instructionColumn.setOrientation(LinearLayout.VERTICAL);
        instructionColumn.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow = text("PROSSIMA MANOVRA", 12, YELLOW, Typeface.BOLD);
        maneuverValue = text("Attendo i dati di OsmAnd…", 19, WHITE, Typeface.BOLD);
        instructionColumn.addView(eyebrow, matchWrap());
        instructionColumn.addView(maneuverValue, matchWrap());
        top.addView(instructionColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        distanceValue = text("--", 20, YELLOW, Typeface.BOLD);
        distanceValue.setGravity(Gravity.CENTER);
        top.addView(distanceValue, wrapWrap());

        View spacer = new View(activity);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(10), dp(12), dp(10));
        bottom.setBackgroundColor(0xF20A0D11);

        LinearLayout speedColumn = new LinearLayout(activity);
        speedColumn.setOrientation(LinearLayout.VERTICAL);
        speedColumn.setGravity(Gravity.CENTER_VERTICAL);
        speedColumn.addView(text("VELOCITÀ GPS", 11, WHITE, Typeface.BOLD), matchWrap());
        speedValue = text("--", 56, YELLOW, Typeface.BOLD);
        speedValue.setIncludeFontPadding(false);
        speedColumn.addView(speedValue, matchWrap());
        bottom.addView(speedColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        gpsValue = text("GPS IN ATTESA", 12, WHITE, Typeface.BOLD);
        gpsValue.setGravity(Gravity.CENTER);
        bottom.addView(gpsValue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button menu = new Button(activity);
        menu.setText("MENU\nOSMAND");
        menu.setAllCaps(false);
        menu.setTextColor(WHITE);
        menu.setTextSize(13);
        menu.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF303944));
        menu.setOnClickListener(v -> showNativeOsmAnd());
        bottom.addView(menu, new LinearLayout.LayoutParams(dp(112), dp(64)));

        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlay = root;
        container.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        returnButton = new Button(activity);
        returnButton.setText("NEXT NAVI PRO");
        returnButton.setAllCaps(false);
        returnButton.setTextColor(WHITE);
        returnButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF171D24));
        returnButton.setOnClickListener(v -> showNextNaviPro());
        FrameLayout.LayoutParams returnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48), Gravity.TOP | Gravity.END);
        returnParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        container.addView(returnButton, returnParams);
        returnButton.setVisibility(View.GONE);

        if (nativeHud != null) {
            nativeHud.setVisibility(View.GONE);
        }
        updateFromOsmAnd();
        handler.postDelayed(refresh, REFRESH_MS);
    }

    private void showNativeOsmAnd() {
        if (overlay != null) {
            overlay.setVisibility(View.GONE);
        }
        if (nativeHud != null) {
            nativeHud.setVisibility(View.VISIBLE);
        }
        if (returnButton != null) {
            returnButton.setVisibility(View.VISIBLE);
        }
    }

    private void showNextNaviPro() {
        if (nativeHud != null) {
            nativeHud.setVisibility(View.GONE);
        }
        if (returnButton != null) {
            returnButton.setVisibility(View.GONE);
        }
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
        }
        updateFromOsmAnd();
    }

    private void updateFromOsmAnd() {
        if (speedValue == null || maneuverValue == null || distanceValue == null || gpsValue == null) {
            return;
        }
        Location location = app.getLocationProvider().getLastKnownLocation();
        if (location != null && location.hasSpeed()) {
            int kmh = Math.round(location.getSpeed() * 3.6f);
            speedValue.setText(String.valueOf(kmh));
        } else {
            speedValue.setText("--");
        }

        if (location == null) {
            gpsValue.setText("GPS IN ATTESA");
        } else if (location.hasAccuracy()) {
            gpsValue.setText(String.format(Locale.getDefault(), "GPS ±%.0f m", location.getAccuracy()));
        } else {
            gpsValue.setText("GPS ATTIVO");
        }

        RoutingHelper routing = app.getRoutingHelper();
        if (routing.isRouteBeingCalculated()) {
            maneuverValue.setText("Calcolo percorso…");
            distanceValue.setText("--");
            return;
        }
        if (!routing.isRouteCalculated()) {
            maneuverValue.setText("Nessuna navigazione attiva");
            distanceValue.setText("—");
            return;
        }

        NextDirectionInfo info = routing.getNextRouteDirectionInfo(new NextDirectionInfo(), false);
        if (info == null || info.directionInfo == null) {
            maneuverValue.setText(routing.isFollowingMode() ? "Percorso attivo" : "Percorso calcolato");
            distanceValue.setText("—");
            return;
        }

        String instruction = info.directionInfo.getDescriptionRoutePart(activity, true);
        maneuverValue.setText(TextUtils.isEmpty(instruction) ? "Prossima manovra" : instruction);
        distanceValue.setText(formatDistance(info.distanceTo));
    }

    private String formatDistance(int meters) {
        if (meters < 0) {
            return "—";
        }
        if (meters >= 1000) {
            return String.format(Locale.getDefault(), "%.1f km", meters / 1000f);
        }
        return meters + " m";
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    void dispose() {
        disposed = true;
        handler.removeCallbacks(refresh);
    }
}
''', encoding="utf-8")
print("Created first in-process HUD source.")
