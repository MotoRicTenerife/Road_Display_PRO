package net.osmand.plus.activities;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import net.osmand.Location;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import net.osmand.binary.RouteDataObject;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.routing.NextDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.utils.FormattedValue;
import net.osmand.plus.utils.OsmAndFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Original NEXT NAVI PRO rider-glance composition, backed by OsmAnd's live state. */
final class NextNaviProOverlay extends View {
    private static final int YELLOW = 0xFFFFD400, WHITE = Color.WHITE, BLACK = Color.BLACK;
    private static final int PANEL = 0xF20B1016, MUTED = 0xFF9AA6B2, GREEN = 0xFF00E676;
    private final MapActivity activity;
    private final OsmandApplication app;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mapWindow = new RectF(), menu = new RectF(), back = new RectF();
    private View nativeHud, mapView;
    private FrameLayout container;
    private FrameLayout.LayoutParams originalMapParams;
    private float density;
    private boolean nativeMode, disposed;
    private int speed = -1;
    private String gps = "GPS IN ATTESA", limit = "", instruction = "NAVIGAZIONE NON ATTIVA", distance = "—";

    NextNaviProOverlay(MapActivity activity, OsmandApplication app) {
        super(activity);
        this.activity = activity;
        this.app = app;
        density = getResources().getDisplayMetrics().density;
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    void install() {
        mapView = activity.findViewById(R.id.map_view_with_layers);
        if (mapView == null || !(mapView.getParent() instanceof FrameLayout)) {
            throw new IllegalStateException("OsmAnd map container not found; refusing to show a fake map");
        }
        container = (FrameLayout) mapView.getParent();
        originalMapParams = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) mapView.getLayoutParams());
        nativeHud = activity.findViewById(R.id.map_hud_container);
        if (nativeHud != null) nativeHud.setVisibility(View.GONE);
        container.addView(this, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateData();
        postDelayed(refresh, 300);
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (disposed) return;
            updateData();
            invalidate();
            postDelayed(this, 300);
        }
    };

    private void updateData() {
        Location l = app.getLocationProvider().getLastKnownLocation();
        speed = l != null && l.hasSpeed() && (!l.hasAccuracy() || l.getAccuracy() <= 100f)
                ? Math.max(0, Math.round(l.getSpeed() * 3.6f)) : -1;
        gps = l == null ? "GPS IN ATTESA" : l.hasAccuracy()
                ? String.format(Locale.getDefault(), "GPS ±%.0f m", l.getAccuracy()) : "GPS ATTIVO";
        RoutingHelper r = app.getRoutingHelper();
        updateLimit(r, l);
        if (r.isRouteBeingCalculated()) {
            instruction = "CALCOLO PERCORSO…"; distance = "—"; return;
        }
        if (!r.isRouteCalculated()) {
            instruction = "NAVIGAZIONE NON ATTIVA"; distance = "—"; return;
        }
        NextDirectionInfo info = r.getNextRouteDirectionInfo(new NextDirectionInfo(), false);
        if (info == null || info.directionInfo == null) {
            instruction = r.isFollowingMode() ? "PERCORSO ATTIVO" : "PERCORSO CALCOLATO";
            distance = "—"; return;
        }
        String d = info.directionInfo.getDescriptionRoutePart(activity, true);
        instruction = d == null || d.trim().isEmpty() ? "PROSSIMA MANOVRA" : d.trim();
        distance = info.distanceTo < 1000 ? info.distanceTo + " m"
                : String.format(Locale.getDefault(), "%.1f km", info.distanceTo / 1000f);
    }

    private void updateLimit(RoutingHelper r, Location l) {
        float v = 0;
        if ((!r.isFollowingMode() || r.isDeviatedFromRoute()
                || (r.getCurrentGPXRoute() != null && !r.isCurrentGPXRouteV2()))
                && app.getMapViewTrackingUtilities().isMapLinkedToLocation()) {
            RouteDataObject road = app.getLocationProvider().getLastKnownRouteSegment();
            if (road != null && l != null) {
                v = road.getMaximumSpeed(road.bearingVsRouteDirection(l),
                        app.getSettings().getApplicationMode().getRouteTypeProfile());
            }
        } else if (r.isFollowingMode()) v = r.getCurrentMaxSpeed();
        if (v <= 0 || v == RouteDataObject.NONE_MAX_SPEED) { limit = ""; return; }
        FormattedValue f = OsmAndFormatter.getFormattedSpeedValue(v, app);
        limit = f.value == null ? "" : f.value;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        boolean land = w > h;
        float top = dp(land ? 38 : 50);
        if (nativeMode) {
            restoreMapSize();
            back.set(w - dp(184), dp(8), w - dp(8), dp(54));
            panel(c, back, PANEL, dp(8));
            text(c, "TORNA A NEXT NAVI PRO", back.centerX(), back.centerY() + dp(5), 13, WHITE, Paint.Align.CENTER);
            return;
        }
        if (land) mapWindow.set(w * .685f, h * .17f, w - dp(10), h * .84f);
        else mapWindow.set(w * .055f, h * .825f, w * .945f, h * .975f);
        applyMapWindow();

        // Leave a transparent viewport for OsmAnd's real, live map; black out its other UI.
        int save = c.save();
        c.clipOutRect(mapWindow);
        c.drawColor(BLACK);
        c.restoreToCount(save);
        panel(c, new RectF(0, 0, w, top), 0xFF111820, 0);
        text(c, gps, land ? w * .43f : w * .5f, top * .64f, 12, gps.contains("DEBOLE") ? 0xFFFFB84D : GREEN, Paint.Align.CENTER);
        text(c, clock(), w - dp(126), top * .64f, 13, WHITE, Paint.Align.CENTER);
        menu.set(w - dp(112), dp(5), w - dp(7), top - dp(4));
        panel(c, menu, 0xFF252E37, dp(7));
        text(c, "MENU OSMAND", menu.centerX(), menu.centerY() + dp(4), 10, YELLOW, Paint.Align.CENTER);

        RectF nav;
        float speedX, speedY, digitH, limitX, limitY, limitR;
        if (land) {
            nav = new RectF(dp(12), top + dp(8), w * .33f, h * .43f);
            speedX = w * .34f; speedY = h * .77f; digitH = Math.min(h * .30f, dp(185));
            limitX = w * .565f; limitY = h * .51f; limitR = Math.min(dp(43), h * .11f);
        } else {
            nav = new RectF(dp(12), top + dp(8), w * .66f, h * .405f);
            speedX = w * .50f; speedY = h * .735f; digitH = Math.min(h * .275f, dp(205));
            limitX = w * .80f; limitY = h * .31f; limitR = Math.min(dp(40), w * .105f);
        }
        panel(c, nav, PANEL, dp(10));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(0xFF47535E);
        c.drawRoundRect(nav, dp(10), dp(10), p); p.setStyle(Paint.Style.FILL);
        drawArrow(c, nav.centerX(), nav.top + nav.height() * .29f, Math.min(dp(65), nav.width() * .24f), instruction);
        text(c, instruction.toUpperCase(Locale.ROOT), nav.centerX(), nav.top + nav.height() * .60f, land ? 11 : 13, WHITE, Paint.Align.CENTER);
        text(c, distance, nav.centerX(), nav.top + nav.height() * .82f, land ? 20 : 23, YELLOW, Paint.Align.CENTER);

        text(c, clock(), speedX, speedY - digitH * .94f, 20, WHITE, Paint.Align.CENTER);
        drawSpeed(c, speedX, speedY, digitH);
        if (!limit.isEmpty()) drawLimit(c, limitX, limitY, limitR, limit);
        // Do not fill the viewport: the transparent hole must show the live OsmAnd map.
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(0xFF596570);
        c.drawRoundRect(mapWindow, dp(8), dp(8), p); p.setStyle(Paint.Style.FILL);
        if (speed < 0) text(c, "VELOCITÀ NON DISPONIBILE", speedX, speedY + dp(24), 10, MUTED, Paint.Align.CENTER);
    }

    private void applyMapWindow() {
        if (nativeMode || mapView == null || mapWindow.isEmpty()) return;
        ViewGroup.LayoutParams raw = mapView.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
        int width = Math.max(1, Math.round(mapWindow.width()));
        int height = Math.max(1, Math.round(mapWindow.height()));
        int left = Math.round(mapWindow.left), top = Math.round(mapWindow.top);
        if (lp.width == width && lp.height == height && lp.leftMargin == left && lp.topMargin == top) return;
        lp.width = width;
        lp.height = height;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        mapView.setLayoutParams(lp);
    }

    private void restoreMapSize() {
        if (mapView != null && originalMapParams != null) {
            ViewGroup.LayoutParams raw = mapView.getLayoutParams();
            if (!(raw instanceof FrameLayout.LayoutParams)) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            if (lp.width != originalMapParams.width || lp.height != originalMapParams.height
                    || lp.leftMargin != originalMapParams.leftMargin || lp.topMargin != originalMapParams.topMargin) {
                mapView.setLayoutParams(new FrameLayout.LayoutParams(originalMapParams));
            }
        }
    }

    private void drawArrow(Canvas c, float x, float y, float s, String label) {
        String a = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (a.contains("roundabout") || a.contains("rotary") || a.contains("rotonda")) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(dp(4), s * .12f));
            p.setColor(YELLOW);
            c.drawCircle(x, y, s * .30f, p);
            p.setStyle(Paint.Style.FILL);
            Path head = new Path();
            head.moveTo(x + s * .39f, y - s * .05f);
            head.lineTo(x + s * .10f, y - s * .12f);
            head.lineTo(x + s * .27f, y - s * .35f);
            head.close();
            c.drawPath(head, p);
            return;
        }
        Path path = new Path();
        if (a.contains("left") || a.contains("sinistra")) {
            path.moveTo(x-s*.42f,y-s*.02f); path.lineTo(x-s*.04f,y-s*.02f);
            path.lineTo(x-s*.04f,y+s*.34f); path.lineTo(x+s*.10f,y+s*.34f);
            path.lineTo(x+s*.10f,y-s*.02f); path.lineTo(x+s*.38f,y-s*.02f);
            path.lineTo(x,y-s*.40f); path.close();
        } else if (a.contains("right") || a.contains("destra")) {
            path.moveTo(x+s*.42f,y-s*.02f); path.lineTo(x+s*.04f,y-s*.02f);
            path.lineTo(x+s*.04f,y+s*.34f); path.lineTo(x-s*.10f,y+s*.34f);
            path.lineTo(x-s*.10f,y-s*.02f); path.lineTo(x-s*.38f,y-s*.02f);
            path.lineTo(x,y-s*.40f); path.close();
        } else {
            path.moveTo(x-s*.14f,y+s*.36f); path.lineTo(x+s*.14f,y+s*.36f);
            path.lineTo(x+s*.14f,y-s*.10f); path.lineTo(x+s*.42f,y-s*.10f);
            path.lineTo(x,y-s*.43f); path.lineTo(x-s*.42f,y-s*.10f);
            path.lineTo(x-s*.14f,y-s*.10f); path.close();
        }
        p.setColor(YELLOW); p.setStyle(Paint.Style.FILL); c.drawPath(path,p);
    }

    private void drawSpeed(Canvas c, float cx, float base, float h) {
        if (speed < 0) { text(c, "—", cx, base, h*.5f/density, WHITE, Paint.Align.CENTER); return; }
        String digits = String.format(Locale.US, "%03d", Math.min(999, speed));
        float dw = h*.48f, gap = h*.08f, left = cx - (dw*3+gap*2)*.5f;
        for (int i=0;i<3;i++) drawDigit(c,left+i*(dw+gap),base,dw,h,digits.charAt(i),i<2&&digits.charAt(i)=='0'&&(i==0||digits.charAt(0)=='0')?0xFF41464C:WHITE);
        text(c, "km/h", cx, base+dp(25), 13, WHITE, Paint.Align.CENTER);
    }

    private void drawDigit(Canvas c,float x,float b,float w,float h,char ch,int color) {
        final boolean[][] map={{true,true,true,true,true,true,false},{false,true,true,false,false,false,false},{true,true,false,true,true,false,true},{true,true,true,true,false,false,true},{false,true,true,false,false,true,true},{true,false,true,true,false,true,true},{true,false,true,true,true,true,true},{true,true,true,false,false,false,false},{true,true,true,true,true,true,true},{true,true,true,true,false,true,true}};
        int n=ch>='0'&&ch<='9'?ch-'0':0; float t=Math.max(dp(4),h*.10f), mid=b-h*.5f;
        RectF[] r={new RectF(x+t,b-h,x+w-t,b-h+t),new RectF(x+w-t,b-h+t,x+w,mid),new RectF(x+w-t,mid,x+w,b-t),new RectF(x+t,b-t,x+w-t,b),new RectF(x,mid,x+t,b-t),new RectF(x,b-h+t,x+t,mid),new RectF(x+t,mid-t*.5f,x+w-t,mid+t*.5f)};
        for(int i=0;i<7;i++) if(map[n][i]) { p.setColor(color); c.drawRoundRect(r[i],t*.25f,t*.25f,p); }
    }

    private void drawLimit(Canvas c,float x,float y,float r,String value) {
        p.setColor(WHITE); c.drawCircle(x,y,r,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(dp(3),r*.105f)); p.setColor(Color.RED); c.drawCircle(x,y,r,p); p.setStyle(Paint.Style.FILL);
        String n=value.replaceAll("[^0-9]","");
        if(n.isEmpty()) return;
        float size=Math.min(dp(32),r*.95f);
        p.setColor(Color.BLACK); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size);
        while(size>dp(15)&&p.measureText(n)>r*1.45f){size-=dp(1);p.setTextSize(size);}
        c.drawText(n,x,y+size*.34f,p);
    }

    private void panel(Canvas c,RectF r,int color,float radius){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(r,radius,radius,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(Math.max(dp(8),size*density));p.setTextAlign(align);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));c.drawText(s==null?"":s,x,y,p);}
    private String clock(){return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());}
    private float dp(float v){return v*density;}

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(), y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN) return nativeMode ? back.contains(x,y) : menu.contains(x,y);
        if(e.getAction()==MotionEvent.ACTION_UP) {
            if(nativeMode&&back.contains(x,y)){nativeMode=false;if(nativeHud!=null)nativeHud.setVisibility(View.GONE);invalidate();return true;}
            if(!nativeMode&&menu.contains(x,y)){nativeMode=true;restoreMapSize();if(nativeHud!=null)nativeHud.setVisibility(View.VISIBLE);invalidate();return true;}
        }
        return false;
    }

    void dispose(){disposed=true;removeCallbacks(refresh);restoreMapSize();if(nativeHud!=null)nativeHud.setVisibility(View.VISIBLE);}
}
