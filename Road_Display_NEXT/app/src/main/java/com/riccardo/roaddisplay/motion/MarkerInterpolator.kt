package com.riccardo.roaddisplay.motion

import kotlin.math.abs

/**
 * Pure presentation helper: interpolates only between two real, timestamped GPS fixes.
 * It must never be used to fabricate/archive a GPS fix or conceal poor GPS accuracy.
 */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val timestampNanos: Long,
    val accuracyMeters: Float
)

data class RenderPosition(val latitude: Double, val longitude: Double)

object MarkerInterpolator {
    private const val MAX_FIX_AGE_NANOS = 2_000_000_000L

    fun interpolate(previous: GeoFix, current: GeoFix, frameTimeNanos: Long): RenderPosition? {
        if (!previous.latitude.isFinite() || !previous.longitude.isFinite() ||
            !current.latitude.isFinite() || !current.longitude.isFinite()) return null
        if (previous.latitude !in -90.0..90.0 || current.latitude !in -90.0..90.0 ||
            previous.longitude !in -180.0..180.0 || current.longitude !in -180.0..180.0) return null
        if (previous.accuracyMeters <= 0f || current.accuracyMeters <= 0f) return null
        val interval = current.timestampNanos - previous.timestampNanos
        if (interval <= 0L || interval > MAX_FIX_AGE_NANOS) return null
        if (frameTimeNanos < previous.timestampNanos) return null

        // Keep the visual interpolation bounded to the observed interval. Once the next
        // fix timestamp is reached, freeze at that real fix rather than extrapolating.
        val fraction = ((frameTimeNanos - previous.timestampNanos).toDouble() / interval)
            .coerceIn(0.0, 1.0)
        // Avoid numerical noise on stationary fixes.
        if (abs(current.latitude - previous.latitude) < 1e-12 &&
            abs(current.longitude - previous.longitude) < 1e-12) {
            return RenderPosition(current.latitude, current.longitude)
        }
        return RenderPosition(
            latitude = previous.latitude + (current.latitude - previous.latitude) * fraction,
            longitude = previous.longitude + (current.longitude - previous.longitude) * fraction
        )
    }
}
