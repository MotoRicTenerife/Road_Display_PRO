package com.riccardo.roaddisplay.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerInterpolatorTest {
    @Test fun interpolatesBetweenRealFixes() {
        val a = GeoFix(28.0, -16.0, 1_000_000_000L, 4f)
        val b = GeoFix(28.002, -16.004, 2_000_000_000L, 5f)
        val result = MarkerInterpolator.interpolate(a, b, 1_500_000_000L)!!
        assertEquals(28.001, result.latitude, 1e-9)
        assertEquals(-16.002, result.longitude, 1e-9)
    }

    @Test fun rejectsNonIncreasingTimestamps() {
        val a = GeoFix(28.0, -16.0, 2_000_000_000L, 4f)
        val b = GeoFix(28.002, -16.004, 1_000_000_000L, 5f)
        assertNull(MarkerInterpolator.interpolate(a, b, 2_000_000_000L))
    }

    @Test fun rejectsPoorAccuracySentinel() {
        val a = GeoFix(28.0, -16.0, 1_000_000_000L, 0f)
        val b = GeoFix(28.002, -16.004, 2_000_000_000L, 5f)
        assertNull(MarkerInterpolator.interpolate(a, b, 1_500_000_000L))
    }
}
