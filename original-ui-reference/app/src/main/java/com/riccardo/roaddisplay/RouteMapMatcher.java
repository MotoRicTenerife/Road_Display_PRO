package com.riccardo.roaddisplay;

import android.location.Location;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Local route map matcher for ROAD DISPLAY PRO.
 *
 * It deliberately works on the active routed geometry rather than snapping the
 * raw GNSS point to whichever nearby road happens to be closest.  A short
 * sequence of fixes is matched with a Viterbi-style dynamic program using:
 * distance, heading, speed/time continuity and monotonic route progress.
 *
 * Network matchers (Valhalla/OSRM) remain useful for route calculation and
 * recovery, but the rider must not depend on a network request every second.
 */
final class RouteMapMatcher {
    static final class Result {
        boolean valid;
        double progressM;
        int segmentIndex;
        double t;
        double lat, lon;
        double bearing;
        double lateralM;
        double score;
        double confidence;
        boolean offRouteEvidence;
    }

    private static final class Fix {
        final Location location;
        final long elapsedMs;
        Fix(Location source) {
            location = new Location(source);
            elapsedMs = source.getElapsedRealtimeNanos() > 0
                    ? source.getElapsedRealtimeNanos() / 1_000_000L
                    : source.getTime();
        }
    }

    private static final class Candidate {
        final int segment;
        final double t;
        final double progress;
        final double lateral;
        final double bearing;
        final double emission;
        Candidate(int segment, double t, double progress, double lateral,
                  double bearing, double emission) {
            this.segment = segment; this.t = t; this.progress = progress;
            this.lateral = lateral; this.bearing = bearing; this.emission = emission;
        }
    }

    private final Deque<Fix> history = new ArrayDeque<>();
    private double lastProgress = 0.0;
    private int lastSegment = 0;
    private boolean locked = false;
    private int offRouteSamples = 0;

    void reset() {
        history.clear();
        lastProgress = 0.0;
        lastSegment = 0;
        locked = false;
        offRouteSamples = 0;
    }

    int historySize() { return history.size(); }
    int offRouteSamples() { return offRouteSamples; }

    Result update(Location location, List<MainActivity.RoutePoint> route,
                  List<Double> cumulative, double routeDistanceM) {
        Result empty = new Result();
        if (location == null || route == null || route.size() < 2 ||
                cumulative == null || cumulative.size() != route.size()) return empty;

        history.addLast(new Fix(location));
        while (history.size() > 8) history.removeFirst();

        List<Fix> fixes = new ArrayList<>(history);
        Fix currentFix = fixes.get(fixes.size() - 1);
        List<Candidate> previous = null;
        List<Candidate> current = null;
        double previousProgress = fixes.size() > 1 ? lastProgress : 0.0;
        int previousSegment = fixes.size() > 1 ? lastSegment : nearestSegment(location, route, cumulative, routeDistanceM);

        // Dynamic-programming state for each history point.
        double[] costs = null;
        Candidate[][] states = new Candidate[fixes.size()][];
        int[][] parents = new int[fixes.size()][];

        for (int k = 0; k < fixes.size(); k++) {
            Fix f = fixes.get(k);
            double referenceProgress = k == 0 ?
                    (fixes.size() == 1 ? previousProgress : nearestProgress(f.location, route, cumulative, routeDistanceM)) : previousProgress;
            int referenceSegment = k == 0 ? previousSegment : nearestSegmentForProgress(referenceProgress, cumulative);
            double lookAhead = Math.max(220.0,
                    Math.min(1000.0, speedMps(f.location) * 8.0 + accuracy(f.location) * 3.0 + 120.0));
            current = candidates(f.location, route, cumulative, routeDistanceM,
                    referenceProgress, referenceSegment, lookAhead, k == fixes.size() - 1);
            if (current.isEmpty()) {
                // On the first fix allow a full search; after a lock, never invent a
                // distant jump merely because the local candidate window is empty.
                if (k == 0 || !locked) {
                    current = candidates(f.location, route, cumulative, routeDistanceM,
                            0.0, 0, routeDistanceM + 1.0, k == fixes.size() - 1);
                }
            }
            if (current.isEmpty()) {
                states[k] = new Candidate[0];
                parents[k] = new int[0];
                previous = current;
                continue;
            }

            states[k] = current.toArray(new Candidate[0]);
            parents[k] = new int[current.size()];
            if (k == 0 || previous == null || previous.isEmpty()) {
                costs = new double[current.size()];
                for (int i = 0; i < current.size(); i++) {
                    costs[i] = current.get(i).emission;
                    parents[k][i] = -1;
                }
            } else {
                double[] nextCosts = new double[current.size()];
                for (int j = 0; j < current.size(); j++) {
                    Candidate b = current.get(j);
                    double best = Double.POSITIVE_INFINITY;
                    int bestParent = 0;
                    for (int i = 0; i < previous.size(); i++) {
                        Candidate a = previous.get(i);
                        double transition = transitionCost(a, b, fixes.get(k - 1), f);
                        double total = costs[i] + transition + b.emission;
                        if (total < best) { best = total; bestParent = i; }
                    }
                    nextCosts[j] = best;
                    parents[k][j] = bestParent;
                }
                costs = nextCosts;
            }
            previous = current;
            previousProgress = referenceProgress;
            previousSegment = referenceSegment;
        }

        if (states.length == 0 || states[states.length - 1] == null || states[states.length - 1].length == 0 || costs == null) {
            offRouteSamples++;
            return empty;
        }

        int bestIndex = 0;
        double bestCost = costs[0];
        double secondCost = Double.POSITIVE_INFINITY;
        for (int i = 1; i < costs.length; i++) {
            if (costs[i] < bestCost) {
                secondCost = bestCost;
                bestCost = costs[i];
                bestIndex = i;
            } else if (costs[i] < secondCost) secondCost = costs[i];
        }

        Candidate chosen = states[states.length - 1][bestIndex];
        // Walk the Viterbi path back far enough to obtain the immediately previous
        // matched candidate. This makes route-progress jumps physically plausible.
        Candidate previousChosen = null;
        int cursor = bestIndex;
        for (int k = states.length - 1; k > 0; k--) {
            int parent = parents[k][cursor];
            if (k == states.length - 1 && parent >= 0) previousChosen = states[k - 1][parent];
            cursor = parent;
            if (cursor < 0) break;
        }

        double margin = secondCost == Double.POSITIVE_INFINITY ? 10.0 : Math.max(0.0, secondCost - bestCost);
        double confidence = confidence(chosen, margin, location, previousChosen);

        // Route progress must be monotonic. A candidate that goes backwards is kept
        // only when the backward movement is tiny GNSS jitter; otherwise the active
        // route lock wins until an actual off-route condition is confirmed.
        double acceptedProgress = chosen.progress;
        if (locked && acceptedProgress + 12.0 < lastProgress) {
            acceptedProgress = lastProgress;
            chosen = new Candidate(lastSegment, 0.0, acceptedProgress, chosen.lateral,
                    chosen.bearing, chosen.emission + 8.0);
        }

        boolean off = chosen.lateral > Math.max(45.0, accuracy(location) * 2.4)
                && confidence < 0.60;
        if (off) offRouteSamples++; else offRouteSamples = Math.max(0, offRouteSamples - 1);

        // A single bad fix can never break the lock. Require multiple consecutive
        // pieces of evidence before the caller is allowed to reroute.
        if (!locked || confidence >= 0.48 || !off) {
            locked = true;
            lastProgress = Math.max(lastProgress, acceptedProgress);
            lastSegment = chosen.segment;
        }

        Result r = new Result();
        r.valid = true;
        r.progressM = Math.max(0.0, Math.min(routeDistanceM, lastProgress));
        r.segmentIndex = chosen.segment;
        r.t = chosen.t;
        r.lateralM = chosen.lateral;
        r.bearing = chosen.bearing;
        r.score = bestCost;
        r.confidence = confidence;
        r.offRouteEvidence = off && offRouteSamples >= 3;
        MainActivity.RoutePoint a = route.get(Math.max(0, Math.min(route.size() - 2, chosen.segment)));
        MainActivity.RoutePoint b = route.get(Math.max(1, Math.min(route.size() - 1, chosen.segment + 1)));
        r.lat = a.lat + (b.lat - a.lat) * chosen.t;
        r.lon = a.lon + (b.lon - a.lon) * chosen.t;
        return r;
    }

    private List<Candidate> candidates(Location l, List<MainActivity.RoutePoint> route,
                                       List<Double> cumulative, double total,
                                       double referenceProgress, int referenceSegment,
                                       double window, boolean current) {
        ArrayList<Candidate> out = new ArrayList<>();
        double acc = accuracy(l);
        int from = Math.max(0, referenceSegment - 80);
        int to = Math.min(route.size() - 2, referenceSegment + 160);
        if (window >= total) { from = 0; to = route.size() - 2; }
        for (int i = from; i <= to; i++) {
            double segStart = cumulative.get(i), segEnd = cumulative.get(i + 1);
            if (segEnd + 90.0 < referenceProgress || segStart - referenceProgress > window) continue;
            MainActivity.RoutePoint a = route.get(i), b = route.get(i + 1);
            double[] pr = project(l.getLatitude(), l.getLongitude(), a.lat, a.lon, b.lat, b.lon);
            double lateral = pr[0];
            double progress = segStart + pr[1] * (segEnd - segStart);
            if (locked && progress + 100.0 < lastProgress) continue;
            double bearing = bearing(a.lat, a.lon, b.lat, b.lon);
            double headingPenalty = 0.0;
            if (l.hasBearing()) {
                float speed = l.hasSpeed() ? l.getSpeed() : 0f;
                if (speed >= 2.0f) {
                    double diff = angleDiff(l.getBearing(), bearing);
                    double headingSigma = 35.0;
                    headingPenalty = Math.min(8.0, (diff / headingSigma) * 1.6);
                    if (diff > 125.0 && lateral > 10.0) headingPenalty += 8.0;
                }
            }
            double emission = lateral / Math.max(5.0, acc) + headingPenalty;
            if (lateral > Math.max(90.0, acc * 3.5)) emission += 20.0;
            out.add(new Candidate(i, pr[1], progress, lateral, bearing, emission));
        }
        Collections.sort(out, Comparator.comparingDouble(c -> c.emission));
        if (out.size() > 24) return new ArrayList<>(out.subList(0, 24));
        return out;
    }

    private double transitionCost(Candidate a, Candidate b, Fix prev, Fix cur) {
        double dt = Math.max(0.25, Math.min(8.0, (cur.elapsedMs - prev.elapsedMs) / 1000.0));
        double observed = Math.max(0.0, speedMps(prev.location) * dt);
        double delta = b.progress - a.progress;
        double tolerance = Math.max(18.0, accuracy(cur.location) * 1.6 + observed * 0.45);
        double cost = Math.abs(delta - observed) / tolerance;
        if (delta < -8.0) cost += 18.0 + Math.min(30.0, -delta / 3.0);
        double maxPhys = Math.max(100.0, observed * 3.0 + accuracy(cur.location) * 2.0 + 60.0);
        if (delta > maxPhys) cost += (delta - maxPhys) / 15.0 + 8.0;
        // A route jump to a distant segment is much less plausible than staying on
        // the current segment, even when the raw GPS point is momentarily closer.
        int segmentJump = Math.abs(b.segment - a.segment);
        if (segmentJump > 60) cost += 12.0;
        return cost;
    }

    private double confidence(Candidate c, double margin, Location l, Candidate prev) {
        double distScore = Math.max(0.0, 1.0 - c.lateral / Math.max(20.0, accuracy(l) * 3.0));
        double marginScore = Math.max(0.0, Math.min(1.0, margin / 4.0));
        double continuity = prev == null ? 0.7 : (c.progress + 20.0 >= prev.progress ? 1.0 : 0.2);
        return Math.max(0.0, Math.min(1.0, 0.52 * distScore + 0.28 * marginScore + 0.20 * continuity));
    }

    private int nearestSegment(Location l, List<MainActivity.RoutePoint> route,
                               List<Double> cumulative, double total) {
        double best = Double.MAX_VALUE; int idx = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            double d = project(l.getLatitude(), l.getLongitude(), route.get(i).lat, route.get(i).lon,
                    route.get(i + 1).lat, route.get(i + 1).lon)[0];
            if (d < best) { best = d; idx = i; }
        }
        return idx;
    }

    private double nearestProgress(Location l, List<MainActivity.RoutePoint> route,
                                   List<Double> cumulative, double total) {
        int i = nearestSegment(l, route, cumulative, total);
        double[] p = project(l.getLatitude(), l.getLongitude(), route.get(i).lat, route.get(i).lon,
                route.get(i + 1).lat, route.get(i + 1).lon);
        return cumulative.get(i) + p[1] * (cumulative.get(i + 1) - cumulative.get(i));
    }

    private int nearestSegmentForProgress(double progress, List<Double> cumulative) {
        int lo = 0, hi = cumulative.size() - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cumulative.get(mid) <= progress) lo = mid; else hi = mid - 1;
        }
        return Math.max(0, Math.min(cumulative.size() - 2, lo));
    }

    private static double accuracy(Location l) {
        return l != null && l.hasAccuracy() && Float.isFinite(l.getAccuracy())
                ? Math.max(3.0, Math.min(80.0, l.getAccuracy())) : 25.0;
    }

    private static double speedMps(Location l) {
        return l != null && l.hasSpeed() && Float.isFinite(l.getSpeed())
                ? Math.max(0.0, Math.min(70.0, l.getSpeed())) : 0.0;
    }


    private static double angleDiff(double a, double b) {
        double d = Math.abs((a - b) % 360.0);
        return d > 180.0 ? 360.0 - d : d;
    }

    private static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static double[] project(double lat, double lon, double alat, double alon,
                                    double blat, double blon) {
        double cos = Math.cos(Math.toRadians(lat));
        double ax = (alon - lon) * 111320.0 * cos, ay = (alat - lat) * 110540.0;
        double bx = (blon - lon) * 111320.0 * cos, by = (blat - lat) * 110540.0;
        double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
        double t = len2 < 0.001 ? 0.0 : Math.max(0.0, Math.min(1.0, -(ax * dx + ay * dy) / len2));
        return new double[]{Math.hypot(ax + t * dx, ay + t * dy), t};
    }
}
