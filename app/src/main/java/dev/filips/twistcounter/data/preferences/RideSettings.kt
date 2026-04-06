package dev.filips.twistcounter.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rider-configurable settings persisted via SharedPreferences.
 */
@Singleton
class RideSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("ride_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_LEAN_THRESHOLD = "lean_threshold_degrees"
        const val KEY_SPEED_THRESHOLD = "speed_threshold_kmh"
        
        // Defaults
        const val DEFAULT_LEAN_THRESHOLD = 12f
        const val DEFAULT_SPEED_THRESHOLD = 20f
        
        // Ranges
        const val LEAN_THRESHOLD_MIN = 8f
        const val LEAN_THRESHOLD_MAX = 20f
        const val SPEED_THRESHOLD_MIN = 10f
        const val SPEED_THRESHOLD_MAX = 50f
    }

    var leanThresholdDegrees: Float
        get() = prefs.getFloat(KEY_LEAN_THRESHOLD, DEFAULT_LEAN_THRESHOLD)
        set(value) {
            val clamped = value.coerceIn(LEAN_THRESHOLD_MIN, LEAN_THRESHOLD_MAX)
            prefs.edit().putFloat(KEY_LEAN_THRESHOLD, clamped).apply()
        }

    var speedThresholdKmh: Float
        get() = prefs.getFloat(KEY_SPEED_THRESHOLD, DEFAULT_SPEED_THRESHOLD)
        set(value) {
            val clamped = value.coerceIn(SPEED_THRESHOLD_MIN, SPEED_THRESHOLD_MAX)
            prefs.edit().putFloat(KEY_SPEED_THRESHOLD, clamped).apply()
        }
}