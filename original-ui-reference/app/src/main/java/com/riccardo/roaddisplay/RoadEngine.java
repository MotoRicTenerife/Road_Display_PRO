package com.riccardo.roaddisplay;

import android.location.Location;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RoadEngine {
    RoadDb db;
    Location loc;
    Curve current = new Curve();
    boolean noRoad = true;
    // Published as immutable-by-convention snapshots: readers can safely see a complete
    // reference while the background road loader prepares a replacement list.
    volatile ArrayList<RoadWay> ways = new ArrayList<>();
    volatile ArrayList<RoadWay> mapWays = new ArrayList<>();
    volatile ArrayList<RadarPoint> radars = new ArrayList<>();
    Location lastRoadQueryLocation;
    long lastRoadQueryAt = 0L;
    String matchedWayId = "";
    int matchedSpeedLimitKmh = 0;
    long matchedAt = 0L;
    Location matchedLocation;
    volatile boolean crossCheckInFlight = false;
    Curve lockedCurve = new Curve();
    long lockedUntil = 0L;

    RoadEngine(RoadDb d) { db = d; }

    void reloadRoads(Location l) {
        if (l == null) return;
        // Curve engine uses nearby roads; map cache keeps the wider preloaded area for panning.
        ways = db.near(l.getLatitude(), l.getLongitude(), 0.006);
        mapWays = db.near(l.getLatitude(), l.getLongitude(), 0.12);
        radars = db.nearRadars(l.getLatitude(), l.getLongitude(), 0.12);
        lastRoadQueryLocation = new Location(l);
        lastRoadQueryAt = System.currentTimeMillis();
    }

    boolean update(Location l) {
        loc = l;
        float oldDistance = current.valid ? current.distance : 99999f;
        boolean refresh = lastRoadQueryLocation == null
                || lastRoadQueryLocation.distanceTo(l) >= 80.0f
                || System.currentTimeMillis() - lastRoadQueryAt >= 8000L;
        if (refresh) reloadRoads(l);
        long now = System.currentTimeMillis();
        // Speed-limit matching is useful much more often than the old 60 s
        // confidence refresh. Retry quickly while the current edge has no known
        // limit, then settle to a lower network cadence once a real limit is known.
        long crossCheckAge = matchedSpeedLimitKmh > 0 ? 30000L : 10000L;
        if (gpsGood() && (matchedAt == 0L || now - matchedAt > crossCheckAge)) requestValhallaCrossCheck(l);

        // Curve prediction is deliberately disabled in the live display. The router
        // is the authority for turns/roundabouts; road geometry is retained here for
        // speed-limit matching and the future curve-on-map feature.
        current = new Curve();
        noRoad = ways.isEmpty();
        return false;
    }

    Curve stabilizeCurve(Curve candidate) {
        long now=System.currentTimeMillis();
        if(candidate!=null && candidate.valid){
            boolean same = lockedCurve.valid && !lockedCurve.roadId.isEmpty() && lockedCurve.roadId.equals(candidate.roadId)
                    && lockedCurve.dir==candidate.dir;
            if(!same && lockedCurve.valid && lockedCurve.distance>18f && candidate.distance>18f) {
                // Do not jump between unrelated nearby ways or flip direction on one GPS update.
                if(candidate.confidence < 0.72f) return lockedCurve;
            }
            if(!same || candidate.type==lockedCurve.type || Math.abs(candidate.distance-lockedCurve.distance)<35f){
                if(!same && candidate.confidence<0.82f && lockedCurve.valid && lockedCurve.distance>12f) return lockedCurve;
                lockedCurve=candidate; lockedUntil=now+12000L; return candidate;
            }
            if(candidate.confidence>=lockedCurve.confidence || candidate.distance<lockedCurve.distance){
                lockedCurve=candidate; lockedUntil=now+12000L;
            }
            return lockedCurve;
        }
        if(lockedCurve.valid && now<lockedUntil && lockedCurve.distance>12f){
            lockedCurve.distance=Math.max(0f,lockedCurve.distance-1.5f);
            return lockedCurve;
        }
        lockedCurve=new Curve(); return lockedCurve;
    }

    String readConnection(HttpURLConnection c) throws Exception {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    void requestValhallaCrossCheck(Location l){
        if(crossCheckInFlight || l==null || !gpsGood()) return;
        crossCheckInFlight=true; final Location q=new Location(l);
        new Thread(()->{
            HttpURLConnection c=null;
            try{
                String json="{\"locations\":[{\"lat\":"+q.getLatitude()+",\"lon\":"+q.getLongitude()+"}],\"costing\":\"auto\",\"verbose\":true}";
                String u="https://valhalla1.openstreetmap.de/locate?json="+URLEncoder.encode(json,"UTF-8");
                c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setRequestMethod("GET");
                c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","ROAD-DISPLAY-PRO/3.26 (Android motorcycle display)");
                if(c.getResponseCode()==HttpURLConnection.HTTP_OK){
                    String body=readConnection(c); org.json.JSONArray arr=new org.json.JSONArray(body);
                    if(arr.length()>0){org.json.JSONObject x=arr.getJSONObject(0); org.json.JSONArray edges=x.optJSONArray("edges");
                        if(edges!=null && edges.length()>0){
                            org.json.JSONObject bestEdge=null; double bestScore=Double.MAX_VALUE;
                            for(int ei=0;ei<edges.length();ei++){
                                org.json.JSONObject e=edges.getJSONObject(ei); String id=e.optString("way_id",""); if(id.isEmpty()) continue;
                                double score=0.0; double clat=e.optDouble("correlated_lat",Double.NaN), clon=e.optDouble("correlated_lon",Double.NaN);
                                if(Double.isFinite(clat)&&Double.isFinite(clon)) score += distanceToPoint(q,clat,clon);
                                if(q.hasBearing()&&q.getSpeed()>=3f&&Double.isFinite(clat)&&Double.isFinite(clon)){
                                    double cb=bearingBetween(q.getLatitude(),q.getLongitude(),clat,clon); double diff=Math.abs(normalizeAngle(cb-q.getBearing())); diff=Math.min(diff,360.0-diff); score += diff*0.45;
                                }
                                if(score<bestScore){bestScore=score;bestEdge=e;}
                            }
                            if(bestEdge!=null){String id=bestEdge.optString("way_id",""); int lim=bestEdge.optInt("speed_limit",0); if(lim<=0){org.json.JSONObject ei=bestEdge.optJSONObject("edge_info"); if(ei!=null) lim=ei.optInt("speed_limit",0);} if(!id.isEmpty()){matchedWayId=id;matchedSpeedLimitKmh=(lim>=10&&lim<=160)?lim:0;matchedAt=System.currentTimeMillis();matchedLocation=new Location(q);}}
                        }
                    }
                }
            }catch(Exception ignored){} finally{if(c!=null)c.disconnect();crossCheckInFlight=false;}
        },"road-confidence-crosscheck").start();
    }


    double distanceToPoint(Location q,double lat,double lon){ float[] d=new float[1]; Location.distanceBetween(q.getLatitude(),q.getLongitude(),lat,lon,d); return d[0]; }
    double bearingBetween(double lat1,double lon1,double lat2,double lon2){ double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dl=Math.toRadians(lon2-lon1); double y=Math.sin(dl)*Math.cos(p2); double x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl); return (Math.toDegrees(Math.atan2(y,x))+360.0)%360.0; }

    RadarApproach nearestRadar(Location l) {
        if (l == null || radars == null || radars.isEmpty()) return null;
        RadarPoint bestPoint = null; double best = Double.POSITIVE_INFINITY;
        for (RadarPoint r : radars) {
            float[] d = new float[1];
            Location.distanceBetween(l.getLatitude(), l.getLongitude(), r.lat, r.lon, d);
            if (d[0] > 1500.0f) continue;
            // Avoid most opposite-direction/behind points, but retain tolerance for roadside cameras.
            if (l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 5f) {
                Location radarLocation = new Location("radar"); radarLocation.setLatitude(r.lat); radarLocation.setLongitude(r.lon);
                float bearingTo = l.bearingTo(radarLocation);
                double delta = Math.abs(normalizeAngle(bearingTo - l.getBearing())); delta = Math.min(delta, 360.0 - delta);
                if (delta > 115.0 && d[0] > 80.0f) continue;
            }
            if (d[0] < best) { best = d[0]; bestPoint = r; }
        }
        return bestPoint == null ? null : new RadarApproach(bestPoint, best);
    }

    double nearestRadarMeters(Location l) {
        RadarApproach a = nearestRadar(l);
        return a == null ? Double.POSITIVE_INFINITY : a.distanceMeters;
    }

    String highwayNear(Location l) {
        if (l == null || ways == null || ways.isEmpty()) return "";
        RoadWay best = null; double bestScore = Double.MAX_VALUE;
        for (RoadWay way : ways) {
            if (way.pts == null || way.pts.size() < 2) continue;
            for (int i=0;i+1<way.pts.size();i++) {
                Geo a=way.pts.get(i), b=way.pts.get(i+1);
                double d=distanceToSegmentMeters(l.getLatitude(),l.getLongitude(),a,b);
                if(d>55.0) continue;
                double score=d;
                if(l.hasBearing()&&l.getSpeed()>=3f){
                    double delta=Math.abs(normalizeAngle(bearing(a,b)-l.getBearing()));
                    delta=Math.min(delta,360.0-delta);
                    if(delta>105.0&&d>18.0) continue;
                    score += delta*0.30;
                }
                if(score<bestScore){bestScore=score;best=way;}
            }
        }
        return best==null?"":best.highway;
    }

    // Returns a conservative local road snap for map display only. It never
    // fabricates coordinates when road geometry is absent or too far away.
    Location mapMatchedLocation(Location l) {
        if (l == null || ways == null || ways.isEmpty()) return null;
        RoadWay bestWay = null; Geo bestA = null, bestB = null;
        double bestScore = Double.MAX_VALUE, bestT = 0.0, bestDistance = Double.MAX_VALUE;
        for (RoadWay way : ways) {
            if (way.pts == null || way.pts.size() < 2) continue;
            for (int i = 0; i + 1 < way.pts.size(); i++) {
                Geo a = way.pts.get(i), b = way.pts.get(i + 1);
                double cos = Math.cos(Math.toRadians(l.getLatitude()));
                double ax = (a.lon - l.getLongitude()) * 111320.0 * cos;
                double ay = (a.lat - l.getLatitude()) * 110540.0;
                double bx = (b.lon - l.getLongitude()) * 111320.0 * cos;
                double by = (b.lat - l.getLatitude()) * 110540.0;
                double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
                double t = len2 < 0.001 ? 0.0 : Math.max(0.0, Math.min(1.0, -(ax * dx + ay * dy) / len2));
                double px = ax + t * dx, py = ay + t * dy;
                double distance = Math.hypot(px, py);
                if (distance > 28.0) continue;
                double score = distance;
                if (l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 5.0f && "yes".equalsIgnoreCase(way.oneway)) {
                    double seg = bearing(a, b);
                    double delta = Math.abs(normalizeAngle(seg - l.getBearing()));
                    delta = Math.min(delta, 360.0 - delta);
                    if (delta > 100.0) continue;
                    score += delta * 0.35;
                }
                if (score < bestScore) { bestScore = score; bestDistance = distance; bestWay = way; bestA = a; bestB = b; bestT = t; }
            }
        }
        if (bestWay == null || bestDistance > 22.0) return null;
        Location snapped = new Location("road-matched");
        snapped.setLatitude(bestA.lat + (bestB.lat - bestA.lat) * bestT);
        snapped.setLongitude(bestA.lon + (bestB.lon - bestA.lon) * bestT);
        if (l.hasAccuracy()) snapped.setAccuracy(l.getAccuracy());
        if (l.hasSpeed()) snapped.setSpeed(l.getSpeed());
        if (l.hasBearing()) snapped.setBearing(l.getBearing());
        snapped.setTime(l.getTime());
        return snapped;
    }

    int speedLimitKmh(Location l) {
        if (l == null) return 0;
        // The network road match is a real current-edge result and must remain usable
        // even when the small local OSM cache is temporarily empty or being refreshed.
        // Keep it spatially fresh so a GPS jump cannot leave an old limit on screen.
        if(!matchedWayId.isEmpty() && matchedSpeedLimitKmh>0 && matchedLocation!=null
                && System.currentTimeMillis()-matchedAt < 30000L
                && matchedLocation.distanceTo(l) <= 150.0f){
            return matchedSpeedLimitKmh;
        }
        if (ways == null || ways.isEmpty()) return 0;
        RoadWay best = null;
        double bestScore = Double.MAX_VALUE;
        int bestLimit = 0;
        for (RoadWay way : ways) {
            if (way.pts == null || way.pts.size() < 2) continue;
            for (int i = 0; i + 1 < way.pts.size(); i++) {
                Geo a = way.pts.get(i), b = way.pts.get(i + 1);
                double d = distanceToSegmentMeters(l.getLatitude(), l.getLongitude(), a, b);
                if (d > 70.0) continue;
                double segmentBearing = bearing(a, b);
                double headingPenalty = 0.0;
                double delta = 0.0;
                if (l.hasBearing() && l.getSpeed() >= 3f) {
                    delta = Math.abs(normalizeAngle(segmentBearing - l.getBearing()));
                    delta = Math.min(delta, 360.0 - delta);
                    headingPenalty = delta * 0.30;
                    // Strongly reject a parallel/opposite carriageway when GPS is good.
                    if (delta > 105.0 && d > 18.0) continue;
                }
                int limit;
                if (l.hasBearing() && l.getSpeed() >= 3f && delta > 90.0) {
                    limit = parseSpeedLimit(way.maxspeedBackward);
                    if (limit <= 0) limit = parseSpeedLimit(way.maxspeed);
                } else if (l.hasBearing() && l.getSpeed() >= 3f) {
                    limit = parseSpeedLimit(way.maxspeedForward);
                    if (limit <= 0) limit = parseSpeedLimit(way.maxspeed);
                } else {
                    limit = parseSpeedLimit(way.maxspeed);
                    if (limit <= 0) limit = parseSpeedLimit(way.maxspeedForward);
                }
                if (limit <= 0) continue;
                double score = d + headingPenalty;
                if (score < bestScore) { bestScore = score; best = way; bestLimit = limit; }
            }
        }
        return best == null || bestScore > 55.0 ? 0 : bestLimit;
    }

    double distanceToSegmentMeters(double lat, double lon, Geo a, Geo b) {
        double cosLat = Math.cos(Math.toRadians(lat));
        double ax = (a.lon - lon) * 111320.0 * cosLat;
        double ay = (a.lat - lat) * 110540.0;
        double bx = (b.lon - lon) * 111320.0 * cosLat;
        double by = (b.lat - lat) * 110540.0;
        double dx = bx - ax, dy = by - ay;
        double length2 = dx * dx + dy * dy;
        double t = length2 <= 0.001 ? 0.0 : Math.max(0.0, Math.min(1.0, -(ax * dx + ay * dy) / length2));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    int parseSpeedLimit(String raw) {
        if (raw == null) return 0;
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.contains(";") || value.contains("@") || value.contains("conditional") || value.contains("signals") || value.contains("urban") || value.contains("rural") || value.contains("none")) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{1,3})(?:\\s*(km/?h|kmh|mph))?$").matcher(value);
        if (!matcher.matches()) return 0;
        try {
            int n = Integer.parseInt(matcher.group(1));
            if (matcher.group(2) != null && matcher.group(2).equals("mph")) n = Math.round(n * 1.60934f);
            return n >= 10 && n <= 160 ? n : 0;
        } catch (Exception ignored) { return 0; }
    }

    boolean gpsGood() {
        return loc != null && loc.getAccuracy() <= 50f;
    }

    Curve findCurve(Location l, ArrayList<RoadWay> roadWays) {
        Curve best = findCurveInternal(l, roadWays, true);
        // A recent Valhalla match is a strong hint, not an absolute gate. If it
        // produces no usable curve (for example because the local OSM cache does
        // not contain that way_id), retry against the local geometry without the
        // matched-way restriction and let confidence decide.
        if (!best.valid && !matchedWayId.isEmpty()) best = findCurveInternal(l, roadWays, false);
        return best;
    }

    Curve findCurveInternal(Location l, ArrayList<RoadWay> roadWays, boolean enforceMatchedWay) {
        Curve best = new Curve();
        double bestDistance = Double.MAX_VALUE;
        if (l == null || roadWays == null) return best;
        float travelBearing = l.hasBearing() ? l.getBearing() : 0f;
        for (RoadWay way : roadWays) {
            ArrayList<Geo> points = way.pts;
            if (points == null || points.size() < 4) continue;
            int nearest = nearestIndex(l, points);
            double lateral = dist(l.getLatitude(), l.getLongitude(), points.get(nearest).lat, points.get(nearest).lon);
            if (lateral > 70.0) continue;
            if (enforceMatchedWay && !matchedWayId.isEmpty() && System.currentTimeMillis()-matchedAt < 90000L && !matchedWayId.equals(way.id)) continue;
            if (way.pts.size()>=2 && "yes".equalsIgnoreCase(way.oneway) && l.hasBearing()) { double wh=bearing(way.pts.get(0),way.pts.get(way.pts.size()-1)); double hd=Math.abs(normalizeAngle(wh-l.getBearing())); hd=Math.min(hd,360-hd); if(hd>110.0) continue; }
            // OSM ways may be stored in either direction. Follow the segment that best
            // matches the current GPS heading instead of blindly walking array-forward.
            int step = 1;
            if (nearest < points.size() - 1 && nearest > 0 && l.hasBearing()) {
                double fwd = Math.abs(normalizeAngle(bearing(points.get(nearest), points.get(nearest + 1)) - travelBearing));
                double rev = Math.abs(normalizeAngle(bearing(points.get(nearest), points.get(nearest - 1)) - travelBearing));
                if (rev < fwd) step = -1;
            } else if (nearest >= points.size() - 1) step = -1;
            ArrayList<Geo> ahead = new ArrayList<>();
            ahead.add(points.get(nearest));
            double along = 0.0;
            int idx = nearest;
            while (ahead.size() < 45) {
                int next = idx + step;
                if (next < 0 || next >= points.size()) break;
                Geo a = points.get(idx), b = points.get(next);
                along += dist(a.lat, a.lon, b.lat, b.lon);
                if (along > 1000.0) break;
                ahead.add(b); idx = next;
            }
            if (ahead.size() < 4) continue;
            double cumulative = 0.0;
            for (int i = 1; i + 4 < ahead.size(); i++) {
                Geo prev = ahead.get(i - 1), here = ahead.get(i), next = ahead.get(i + 1);
                cumulative += dist(prev.lat, prev.lon, here.lat, here.lon);
                if (cumulative < 8.0 || cumulative > 800.0) continue;
                int end = Math.min(ahead.size() - 1, i + 5);
                double turn = turnSum(ahead, i - 1, end);
                if (Math.abs(turn) < 10.0) continue;
                double radius = radius(prev, here, next);
                if (radius > 2200.0 && Math.abs(turn) < 16.0) continue;
                double distance = lateral + cumulative;
                if (distance <= 8.0 || distance >= bestDistance) continue;
                Curve candidate = new Curve();
                candidate.valid = true;
                candidate.distance = (float) distance;
                candidate.dir = turn < 0 ? -1 : 1;
                candidate.radius = radius;
                candidate.type = classify(Math.abs(turn), radius);
                candidate.label = new String[]{"", "LEGGERA", "MEDIA", "STRETTA", "TORNANTE"}[candidate.type];
                candidate.roadId = way.id; candidate.source = "OSM";
                double headingConfidence = 1.0;
                if(l.hasBearing() && ahead.size()>2){ double ah=bearing(ahead.get(0),ahead.get(Math.min(3,ahead.size()-1))); double hd=Math.abs(normalizeAngle(ah-l.getBearing())); hd=Math.min(hd,360-hd); headingConfidence=Math.max(0.0,1.0-hd/100.0); }
                double lateralConfidence=Math.max(0.0,1.0-lateral/70.0);
                candidate.confidence=(float)(0.45*lateralConfidence+0.35*headingConfidence+0.20);
                if(enforceMatchedWay && way.id.equals(matchedWayId)) candidate.confidence=Math.min(1f,candidate.confidence+0.25f);
                candidate.recommended = recommend(candidate, matchedSpeedLimitKmh, way.highway);
                best = candidate; bestDistance = distance;
            }
        }
        return best;
    }

    Curve findCurveOnRoute(Location l, ArrayList<MainActivity.RoutePoint> route) {
        Curve best=new Curve();
        if(l==null||route==null||route.size()<4)return best;
        int nearest=0; double lateralBest=Double.MAX_VALUE;
        for(int i=0;i<route.size();i++){
            MainActivity.RoutePoint p=route.get(i);
            double d=dist(l.getLatitude(),l.getLongitude(),p.lat,p.lon);
            if(d<lateralBest){lateralBest=d;nearest=i;}
        }
        if(lateralBest>100.0)return best;

        // OSRM route geometry is already ordered in travel direction. Never reverse it
        // from a noisy phone bearing: doing so can mirror the detected turn and make a
        // right-hand bend render as a left-hand bend.
        int direction=1;
        ArrayList<MainActivity.RoutePoint> ahead=new ArrayList<>();
        ahead.add(route.get(nearest));
        double along=0.0; int idx=nearest;
        while(ahead.size()<180){
            int next=idx+direction;
            if(next<0||next>=route.size())break;
            MainActivity.RoutePoint a=route.get(idx),b=route.get(next);
            along+=dist(a.lat,a.lon,b.lat,b.lon);
            if(along>900.0)break;
            ahead.add(b);idx=next;
        }
        if(ahead.size()<4)return best;

        // Route geometries can contain many very short points. Measure heading over
        // a physical baseline instead of between adjacent vertices, which suppresses
        // map-geometry noise and makes real bends visible.
        double cumulative=0.0;
        double bestDistance=Double.MAX_VALUE;
        for(int i=1;i<ahead.size()-1;i++){
            cumulative+=dist(ahead.get(i-1).lat,ahead.get(i-1).lon,ahead.get(i).lat,ahead.get(i).lon);
            if(cumulative<18.0||cumulative>750.0)continue;
            int before=i,after=i;
            double back=0,forward=0;
            while(before>0&&back<28.0){back+=dist(ahead.get(before-1).lat,ahead.get(before-1).lon,ahead.get(before).lat,ahead.get(before).lon);before--;}
            while(after<ahead.size()-1&&forward<28.0){forward+=dist(ahead.get(after).lat,ahead.get(after).lon,ahead.get(after+1).lat,ahead.get(after+1).lon);after++;}
            if(before==i||after==i)continue;
            double turn=normalizeAngle(bearing(new Geo(ahead.get(after).lat,ahead.get(after).lon),new Geo(ahead.get(Math.min(after+1,ahead.size()-1)).lat,ahead.get(Math.min(after+1,ahead.size()-1)).lon))-
                    bearing(new Geo(ahead.get(Math.max(before-1,0)).lat,ahead.get(Math.max(before-1,0)).lon),new Geo(ahead.get(before).lat,ahead.get(before).lon)));
            // A bend must accumulate a meaningful heading change across a real road length.
            if(Math.abs(turn)<18.0)continue;
            double radius=radius(new Geo(ahead.get(Math.max(before-1,0)).lat,ahead.get(Math.max(before-1,0)).lon),
                    new Geo(ahead.get(i).lat,ahead.get(i).lon),
                    new Geo(ahead.get(Math.min(after+1,ahead.size()-1)).lat,ahead.get(Math.min(after+1,ahead.size()-1)).lon));
            if(radius>3000.0&&Math.abs(turn)<25.0)continue;
            Curve c=new Curve();
            c.valid=true;
            c.distance=(float)(lateralBest+cumulative);
            c.dir=turn<0?-1:1;
            c.radius=radius;
            c.type=classify(Math.abs(turn),radius);
            double lateralConfidence=Math.max(0.0,1.0-Math.min(lateralBest,100.0)/100.0);
            double headingConfidence=0.55;
            if(l.hasBearing() && ahead.size()>3){
                double ah=bearing(new Geo(ahead.get(0).lat,ahead.get(0).lon),new Geo(ahead.get(Math.min(3,ahead.size()-1)).lat,ahead.get(Math.min(3,ahead.size()-1)).lon));
                double hd=Math.abs(normalizeAngle(ah-l.getBearing())); hd=Math.min(hd,360.0-hd);
                headingConfidence=Math.max(0.0,1.0-hd/100.0);
            }
            c.confidence=(float)Math.min(0.95,0.55*lateralConfidence+0.30*headingConfidence+0.15);
            c.source="ROUTE";
            c.label=new String[]{"","LEGGERA","MEDIA","STRETTA","TORNANTE"}[c.type];
            int routeLimit=speedLimitKmh(l);
            c.recommended=recommend(c,routeLimit,highwayNear(l));
            if(c.distance>10&&c.distance<bestDistance){best=c;bestDistance=c.distance;}
        }
        return best;
    }

    int nearestIndex(Location l, ArrayList<Geo> points) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            Geo point = points.get(i);
            double distance = dist(l.getLatitude(), l.getLongitude(), point.lat, point.lon);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    // endIndex is inclusive; calculate heading changes only where all three points exist.
    double turnSum(ArrayList<Geo> points, int startIndex, int endIndex) {
        double sum = 0.0;
        for (int k = startIndex; k + 2 <= endIndex; k++) {
            double h1 = bearing(points.get(k), points.get(k + 1));
            double h2 = bearing(points.get(k + 1), points.get(k + 2));
            sum += normalizeAngle(h2 - h1);
        }
        return sum;
    }

    double normalizeAngle(double angle) {
        while (angle > 180.0) angle -= 360.0;
        while (angle < -180.0) angle += 360.0;
        return angle;
    }

    double radius(Geo a, Geo b, Geo c) {
        // Local metric coordinates correct for longitude compression at the current latitude.
        double cosLat = Math.cos(Math.toRadians(b.lat));
        double ax = (a.lon - b.lon) * 111320.0 * cosLat;
        double ay = (a.lat - b.lat) * 110540.0;
        double cx = (c.lon - b.lon) * 111320.0 * cosLat;
        double cy = (c.lat - b.lat) * 110540.0;
        double ab = Math.hypot(ax, ay);
        double bc = Math.hypot(cx, cy);
        double ac = dist(a.lat, a.lon, c.lat, c.lon);
        double area = Math.abs(ax * cy - ay * cx) / 2.0;
        if (area < 0.01) return 9999.0;
        return (ab * bc * ac) / (4.0 * area);
    }

    int classify(double turn, double radius) {
        // Stable road-riding classification. The angle alone is never sufficient.
        // Hairpins require both a very large heading change and a genuinely small radius.
        if (turn >= 155.0 && radius < 42.0) return 4;
        if (turn >= 115.0 && radius < 75.0) return 3;
        if (turn >= 48.0 && radius < 230.0) return 2;
        return 1;
    }

    float recommend(Curve curve) { return recommend(curve, 0, ""); }

    float recommend(Curve curve, int speedLimitKmh) { return recommend(curve, speedLimitKmh, ""); }

    float recommend(Curve curve, int speedLimitKmh, String highway) {
        // Dynamic curve estimate. It is deliberately conservative and is NOT a
        // certified road-safety model. The legal limit, when known from road data,
        // is always an upper bound and never gets replaced by a guessed default.
        if (curve == null || !curve.valid || curve.radius <= 0.0 || curve.radius > 5000.0) return 0f;
        double lateralAccel = 2.0;
        double speed = Math.sqrt(Math.max(1.0, curve.radius) * lateralAccel) * 3.6;
        double cap;
        switch (curve.type) {
            case 4: cap = 30.0; break;
            case 3: cap = 45.0; break;
            case 2: cap = 65.0; break;
            default: cap = 90.0; break;
        }
        double kmh = Math.min(speed, cap);
        // Road context is used only as a conservative contextual ceiling when the
        // explicit legal maxspeed tag is missing. It is never exposed as the legal limit.
        if (speedLimitKmh <= 0 && highway != null) {
            String h = highway.toLowerCase(java.util.Locale.ROOT);
            if (h.contains("living_street")) kmh = Math.min(kmh, 30.0);
            else if (h.contains("residential")) kmh = Math.min(kmh, 50.0);
            else if (h.contains("service")) kmh = Math.min(kmh, 30.0);
            else if (h.contains("tertiary")) kmh = Math.min(kmh, 70.0);
            else if (h.contains("secondary")) kmh = Math.min(kmh, 90.0);
            else if (h.contains("primary")) kmh = Math.min(kmh, 90.0);
        }
        if (speedLimitKmh > 0) kmh = Math.min(kmh, speedLimitKmh);
        return (float)Math.max(15.0, Math.round(kmh / 5.0) * 5.0);
    }

    double bearing(Geo a, Geo b) {
        double y = Math.sin(Math.toRadians(b.lon - a.lon)) * Math.cos(Math.toRadians(b.lat));
        double x = Math.cos(Math.toRadians(a.lat)) * Math.sin(Math.toRadians(b.lat))
                - Math.sin(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat))
                * Math.cos(Math.toRadians(b.lon - a.lon));
        return Math.toDegrees(Math.atan2(y, x));
    }

    double dist(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }

    boolean fetchOnline(Location location) {
        if (location == null) return false;
        // Public Overpass servers can be busy. Use one sequential request per server,
        // reducing the radius on fallback rather than flooding any single endpoint.
        int[] radii = {20000, 10000, 5000};
        String[] endpoints = {
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass.private.coffee/api/interpreter"
        };
        for (int attempt = 0; attempt < endpoints.length; attempt++) {
            int radius = radii[attempt];
            String query = "[out:xml][timeout:35];(way(around:" + radius + "," + location.getLatitude() + "," + location.getLongitude()
                    + ")[highway~\"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|road)$\"];node(around:" + radius + "," + location.getLatitude() + "," + location.getLongitude() + ")[highway=speed_camera];);out tags geom;";
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(endpoints[attempt]).openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(40000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("User-Agent", "ROAD-DISPLAY-PRO/3.10.0 (Android; OpenStreetMap data client)");
                connection.setRequestProperty("Accept", "application/xml,text/xml;q=0.9,*/*;q=0.5");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                byte[] payload = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
                int response = connection.getResponseCode();
                if (response != HttpURLConnection.HTTP_OK) continue;
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
                int savedWays = 0;
                int savedRadars = 0;
                ArrayList<RadarPoint> fetchedRadars = new ArrayList<>();
                try (InputStream input = connection.getInputStream()) {
                    Document document = factory.newDocumentBuilder().parse(input);
                    NodeList nodes = document.getElementsByTagName("way");
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element way = (Element) nodes.item(i);
                        String id = way.getAttribute("id"), name = "", maxspeed = "", maxspeedForward = "", maxspeedBackward = "", highway = "", maxspeedType = "", junction = "", oneway = "";
                        NodeList tags = way.getElementsByTagName("tag");
                        for (int j = 0; j < tags.getLength(); j++) {
                            Element tag = (Element) tags.item(j);
                            if ("name".equals(tag.getAttribute("k"))) name = tag.getAttribute("v");
                            else if ("highway".equals(tag.getAttribute("k"))) highway = tag.getAttribute("v");
                            else if ("maxspeed:type".equals(tag.getAttribute("k"))) maxspeedType = tag.getAttribute("v");
                            else if ("maxspeed".equals(tag.getAttribute("k"))) maxspeed = tag.getAttribute("v");
                            else if ("maxspeed:forward".equals(tag.getAttribute("k"))) maxspeedForward = tag.getAttribute("v");
                            else if ("maxspeed:backward".equals(tag.getAttribute("k"))) maxspeedBackward = tag.getAttribute("v");
                            else if ("junction".equals(tag.getAttribute("k"))) junction = tag.getAttribute("v");
                            else if ("oneway".equals(tag.getAttribute("k"))) oneway = tag.getAttribute("v");
                        }
                        NodeList geometry = way.getElementsByTagName("nd");
                        if (geometry.getLength() < 2) continue;
                        StringBuilder encodedPoints = new StringBuilder();
                        double minLat = 90, minLon = 180, maxLat = -90, maxLon = -180;
                        for (int j = 0; j < geometry.getLength(); j++) {
                            Element point = (Element) geometry.item(j);
                            double lat = Double.parseDouble(point.getAttribute("lat"));
                            double lon = Double.parseDouble(point.getAttribute("lon"));
                            if (j > 0) encodedPoints.append(';');
                            encodedPoints.append(lat).append(',').append(lon);
                            minLat = Math.min(minLat, lat); minLon = Math.min(minLon, lon);
                            maxLat = Math.max(maxLat, lat); maxLon = Math.max(maxLon, lon);
                        }
                        db.put(id, name, maxspeed, maxspeedForward, maxspeedBackward, highway, maxspeedType, junction, oneway, minLat, minLon, maxLat, maxLon, encodedPoints.toString());
                        savedWays++;
                    }
                    NodeList radarNodes = document.getElementsByTagName("node");
                    for (int i = 0; i < radarNodes.getLength(); i++) {
                        Element node = (Element) radarNodes.item(i);
                        NodeList tags = node.getElementsByTagName("tag");
                        boolean isRadar = false;
                        for (int j = 0; j < tags.getLength(); j++) {
                            Element tag = (Element) tags.item(j);
                            if ("highway".equals(tag.getAttribute("k")) && "speed_camera".equals(tag.getAttribute("v"))) { isRadar = true; break; }
                        }
                        if (!isRadar) continue;
                        try {
                            double lat = Double.parseDouble(node.getAttribute("lat"));
                            double lon = Double.parseDouble(node.getAttribute("lon"));
                            fetchedRadars.add(new RadarPoint(node.getAttribute("id"), lat, lon));
                            savedRadars++;
                        } catch (Exception ignored) { }
                    }
                }
                // An HTTP 200 with an Overpass error/empty result is not a successful map load.
                if (savedWays > 0 || savedRadars > 0) { db.replaceRadarsNear(location.getLatitude(), location.getLongitude(), radius, fetchedRadars); radars = db.nearRadars(location.getLatitude(), location.getLongitude(), 0.12); return true; }
            } catch (Exception ignored) {
                // Try the next independent endpoint with a smaller fallback radius.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return false;
    }
}
