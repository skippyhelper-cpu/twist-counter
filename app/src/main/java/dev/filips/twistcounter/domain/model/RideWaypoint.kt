package dev.filips.twistcounter.domain.model

import org.osmdroid.util.GeoPoint

/**
 * A GPS waypoint with associated sensor data for color-coded map display.
 */
data class RideWaypoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val leanAngle: Float,      // degrees, positive = right, negative = left
    val speedKmh: Float,       // km/h
    val accelG: Float,         // longitudinal acceleration in Gs (positive = accel, negative = brake)
    val isCorner: Boolean = false
) {
    /**
     * Get OSMDroid GeoPoint for map display.
     */
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
    
    /**
     * Get color based on lean angle intensity.
     * Blue (left) to Red (right) gradient.
     */
    fun getLeanColor(): Int {
        return when {
            leanAngle < -30 -> 0xFF0066FF.toInt()  // Deep blue - hard left
            leanAngle < -15 -> 0xFF00B4FF.toInt()  // Light blue - medium left
            leanAngle < -5  -> 0xFF66CCFF.toInt()  // Cyan - light left
            leanAngle > 30  -> 0xFFFF3300.toInt()  // Deep red - hard right
            leanAngle > 15  -> 0xFFFF6B35.toInt()  // Orange - medium right
            leanAngle > 5   -> 0xFFFF9966.toInt()  // Light orange - light right
            else -> 0xFF888888.toInt()             // Gray - straight
        }
    }
    
    /**
     * Get color based on acceleration/braking.
     * Green (accel) to Red (brake) gradient.
     */
    fun getAccelColor(): Int {
        return when {
            accelG > 0.5f  -> 0xFF00C853.toInt()  // Hard acceleration - bright green
            accelG > 0.2f  -> 0xFF69F0AE.toInt()  // Moderate acceleration - light green
            accelG < -0.5f -> 0xFFFF1744.toInt()  // Hard braking - bright red
            accelG < -0.2f -> 0xFFFF5252.toInt()  // Moderate braking - light red
            else -> 0xFF888888.toInt()             // Gray - neutral
        }
    }
    
    /**
     * Get color based on combined intensity (lean + accel).
     * Purple gradient for exciting sections.
     */
    fun getIntensityColor(): Int {
        val leanIntensity = kotlin.math.abs(leanAngle) / 45f  // 0-1 normalized
        val accelIntensity = kotlin.math.abs(accelG) / 1f     // 0-1 normalized
        val totalIntensity = (leanIntensity + accelIntensity).coerceIn(0f, 1f)
        
        return when {
            totalIntensity > 0.8f -> 0xFFD500F9.toInt()  // Bright purple - extreme
            totalIntensity > 0.5f -> 0xFFE040FB.toInt()  // Medium purple - exciting
            totalIntensity > 0.2f -> 0xFFEA80FC.toInt()  // Light purple - moderate
            else -> 0xFF424242.toInt()                    // Dark gray - boring
        }
    }
}

/**
 * Collection of waypoints for a ride.
 */
data class RideTrack(
    val waypoints: List<RideWaypoint> = emptyList()
) {
    fun getBounds(): Pair<GeoPoint, GeoPoint>? {
        if (waypoints.isEmpty()) return null
        
        val lats = waypoints.map { it.latitude }
        val lngs = waypoints.map { it.longitude }
        
        val southWest = GeoPoint(lats.minOrNull()!!, lngs.minOrNull()!!)
        val northEast = GeoPoint(lats.maxOrNull()!!, lngs.maxOrNull()!!)
        
        return Pair(southWest, northEast)
    }
    
    fun getTotalDistanceKm(): Float {
        if (waypoints.size < 2) return 0f
        
        var distance = 0f
        for (i in 1 until waypoints.size) {
            distance += calculateDistanceBetween(
                waypoints[i - 1].latitude, waypoints[i - 1].longitude,
                waypoints[i].latitude, waypoints[i].longitude
            )
        }
        return distance
    }
    
    private fun calculateDistanceBetween(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            startLat, startLng,
            endLat, endLng,
            results
        )
        return results[0] / 1000f  // Convert meters to km
    }
}
