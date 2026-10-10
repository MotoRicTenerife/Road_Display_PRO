package com.riccardo.roaddisplay;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 410;
    private LocationManager locationManager;
    private TextView statusView;
    private TextView accuracyView;
    private TextView positionView;
    private boolean locationUpdatesActive = false;
    private TextView speedView;
    private RideView rideView;
    private boolean demo = false;
    private float demoSpeed = 54f;
    private Location latestLocation;
    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            latestLocation = location;
            updateLocationUi();
        }
        @Override public void onProviderEnabled(String provider) { updateLocationUi(); }
        @Override public void onProviderDisabled(String provider) { updateLocationUi(); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildUi();
        if (!hasLocationPermission()) requestPermissions(
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
            LOCATION_PERMISSION_REQUEST);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF050505);
        root.setPadding(dp(16), dp(10), dp(16), dp(12));

        TextView header = new TextView(this);
        header.setText("NEXT NAVI");
        header.setTextColor(0xFFFFFF00);
        header.setTextSize(15);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(34)));

        statusView = makeLabel("GPS IN ATTESA", 0xFFFFD740, 14);
        accuracyView = makeLabel("ACCURATEZZA: —", 0xFFCCCCCC, 13);
        positionView = makeLabel("POSIZIONE: IN ATTESA", 0xFFCCCCCC, 12);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(28)));
        root.addView(accuracyView, new LinearLayout.LayoutParams(-1, dp(25)));
        root.addView(positionView, new LinearLayout.LayoutParams(-1, dp(24)));

        rideView = new RideView(this);
        root.addView(rideView, new LinearLayout.LayoutParams(-1, 0, 1f));

        speedView = makeLabel("--", 0xFFFFFFFF, 68);
        speedView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        speedView.setGravity(Gravity.CENTER);
        root.addView(speedView, new LinearLayout.LayoutParams(-1, dp(110)));

        TextView unit = makeLabel("km/h", 0xFFEEEEEE, 16);
        unit.setGravity(Gravity.CENTER);
        root.addView(unit, new LinearLayout.LayoutParams(-1, dp(26)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button demoButton = new Button(this);
        demoButton.setText("DEMO ON / OFF");
        demoButton.setOnClickListener(v -> {
            demo = !demo;
            if (demo) {
                statusView.setText("DEMO ATTIVA — DATI SIMULATI");
                statusView.setTextColor(0xFFFFFF00);
                speedView.setText(String.format(Locale.ITALY, "%.0f", demoSpeed));
            } else updateLocationUi();
            rideView.invalidate();
        });
        buttons.addView(demoButton, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView disclaimer = makeLabel("TEST GPS ATTIVO  •  NAVIGAZIONE E MAPPA IN SVILUPPO", 0xFFFFD740, 12);
        disclaimer.setGravity(Gravity.CENTER);
        root.addView(disclaimer, new LinearLayout.LayoutParams(-1, dp(28)));
        setContentView(root);
    }

    private TextView makeLabel(String text, int color, float size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocation() {
        if (!hasLocationPermission() || locationUpdatesActive) return;
        try {
            boolean requested = false;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
                        Location last = locationManager.getLastKnownLocation(provider);
                        if (last != null && (latestLocation == null || last.getTime() > latestLocation.getTime())) latestLocation = last;
                        requested = true;
                    }
                } catch (SecurityException ignored) { }
            }
            if (!requested) statusView.setText("GPS DISATTIVATO O NON DISPONIBILE");
            locationUpdatesActive = requested;
            updateLocationUi();
        } catch (Exception e) {
            statusView.setText("ERRORE GPS — VERIFICARE I PERMESSI");
        }
    }

    private void updateLocationUi() {
        if (demo || statusView == null) return;
        if (latestLocation == null) {
            statusView.setText("GPS IN ATTESA");
            statusView.setTextColor(0xFFFFD740);
            accuracyView.setText("ACCURATEZZA: —");
            positionView.setText("POSIZIONE: IN ATTESA DI FIX");
            speedView.setText("--");
            return;
        }
        long now = System.currentTimeMillis();
        long age = Math.max(0, now - latestLocation.getTime());
        LocationFixQuality.State quality = LocationFixQuality.classify(
            true, latestLocation.getTime(), now,
            latestLocation.hasAccuracy(), latestLocation.hasAccuracy() ? latestLocation.getAccuracy() : Float.NaN);
        switch (quality) {
            case GOOD:
                statusView.setText("GPS OK");
                statusView.setTextColor(0xFF8BC34A);
                break;
            case STALE:
                statusView.setText("GPS OBSOLETO — ATTESA DI FIX");
                statusView.setTextColor(0xFFFF5252);
                break;
            case INACCURATE:
                statusView.setText("GPS INSUFFICIENTE");
                statusView.setTextColor(0xFFFFD740);
                break;
            default:
                statusView.setText("GPS IN ATTESA");
                statusView.setTextColor(0xFFFFD740);
        }
        accuracyView.setText(latestLocation.hasAccuracy()
            ? String.format(Locale.ITALY, "ACCURATEZZA: ±%.0f m  •  FIX: %ds", latestLocation.getAccuracy(), age / 1000)
            : String.format(Locale.ITALY, "ACCURATEZZA: NON DISPONIBILE  •  FIX: %ds", age / 1000));
        String coords = String.format(Locale.ITALY, "%.5f, %.5f", latestLocation.getLatitude(), latestLocation.getLongitude());
        String bearing = latestLocation.hasBearing()
            ? String.format(Locale.ITALY, "  •  DIREZIONE %.0f°", latestLocation.getBearing()) : "";
        positionView.setText("POSIZIONE: " + coords + bearing);
        if (LocationFixQuality.mayDisplaySpeed(true, latestLocation.getTime(), now, latestLocation.hasSpeed())) {
            speedView.setText(String.format(Locale.ITALY, "%.0f", LocationFixQuality.speedKmh(latestLocation)));
        } else speedView.setText("--");
        if (rideView != null) rideView.invalidate();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (hasLocationPermission()) startLocation();
            else statusView.setText("PERMESSO GPS NEGATO");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (locationManager != null && hasLocationPermission()) startLocation();
    }

    @Override protected void onPause() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(listener); } catch (SecurityException ignored) {}
            locationUpdatesActive = false;
        }
        super.onPause();
    }

    private final class RideView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RideView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            p.setColor(0xFF111111); p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(dp(8), dp(12), w-dp(8), h-dp(12), dp(18), dp(18), p);
            p.setColor(0xFF333333); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
            c.drawRoundRect(dp(8), dp(12), w-dp(8), h-dp(12), dp(18), dp(18), p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(17));
            p.setColor(0xFFFFFF00);
            c.drawText(demo ? "DEMO — ANTEPRIMA" : "RIDE — STRADA DAVANTI", w/2, dp(46), p);
            p.setTextSize(dp(15)); p.setColor(0xFFEEEEEE);
            c.drawText(demo ? "CURVA MEDIA (SIMULATA)" : "NESSUNA GEOMETRIA STRADALE", w/2, dp(80), p);
            float cx=w/2, cy=h*0.48f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(0xFF555555);
            Path path = new Path();
            path.moveTo(cx-dp(32), cy+dp(76)); path.cubicTo(cx-dp(28), cy+dp(24), cx+dp(30), cy+dp(12), cx+dp(32), cy-dp(60));
            c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD740); p.setTextSize(dp(13));
            c.drawText(demo ? "ESEMPIO NON REALE" : "DATI STRADALI NON DISPONIBILI", cx, h-dp(35), p);
        }
    }
}
