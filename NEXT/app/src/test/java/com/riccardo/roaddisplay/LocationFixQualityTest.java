package com.riccardo.roaddisplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocationFixQualityTest {
    @Test public void noFixWaits() {
        assertEquals(LocationFixQuality.State.WAITING,
                LocationFixQuality.classify(false, 0L, 1000L, false, 0f));
    }
    @Test public void fixOlderThanTenSecondsIsStale() {
        assertEquals(LocationFixQuality.State.STALE,
                LocationFixQuality.classify(true, 1000L, 11001L, true, 2f));
    }
    @Test public void accuracyOverFiftyMetersIsRejected() {
        assertEquals(LocationFixQuality.State.INACCURATE,
                LocationFixQuality.classify(true, 1000L, 2000L, true, 51f));
    }
    @Test public void missingAccuracyIsRejected() {
        assertEquals(LocationFixQuality.State.INACCURATE,
                LocationFixQuality.classify(true, 1000L, 2000L, false, 0f));
    }
    @Test public void recentAccurateFixIsGood() {
        assertEquals(LocationFixQuality.State.GOOD,
                LocationFixQuality.classify(true, 1000L, 2000L, true, 8f));
    }
    @Test public void futureTimestampDoesNotBecomeStale() {
        assertEquals(LocationFixQuality.State.GOOD,
                LocationFixQuality.classify(true, 3000L, 2000L, true, 8f));
    }
    @Test public void speedRequiresRecentFixAndSpeedField() {
        assertTrue(LocationFixQuality.mayDisplaySpeed(true, 1000L, 2000L, true));
        assertFalse(LocationFixQuality.mayDisplaySpeed(true, 1000L, 12001L, true));
        assertFalse(LocationFixQuality.mayDisplaySpeed(true, 1000L, 2000L, false));
        assertFalse(LocationFixQuality.mayDisplaySpeed(false, 1000L, 2000L, true));
    }
}
