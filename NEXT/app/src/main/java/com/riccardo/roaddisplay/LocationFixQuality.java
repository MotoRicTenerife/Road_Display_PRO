package com.riccardo.roaddisplay;

import android.location.Location;

/** Deterministic classification for a location fix. Does not infer road geometry. */
public final class LocationFixQuality {
    public enum State { WAITING, STALE, INACCURATE, GOOD }

    private LocationFixQuality() {}

    public static State classify(boolean hasFix, long fixTimeMillis, long nowMillis,
                                 boolean hasAccuracy, float accuracyMeters) {
        if (!hasFix) return State.WAITING;
        long age = Math.max(0L, nowMillis - fixTimeMillis);
        if (age > 10_000L) return State.STALE;
        if (!hasAccuracy || !Float.isFinite(accuracyMeters) || accuracyMeters > 50f || accuracyMeters < 0f) {
            return State.INACCURATE;
        }
        return State.GOOD;
    }

    public static boolean mayDisplaySpeed(boolean hasFix, long fixTimeMillis, long nowMillis,
                                          boolean hasSpeed) {
        return hasFix && hasSpeed && Math.max(0L, nowMillis - fixTimeMillis) <= 10_000L;
    }

    public static float speedKmh(Location location) {
        return location != null && location.hasSpeed() ? location.getSpeed() * 3.6f : Float.NaN;
    }
}
