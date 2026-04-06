package dev.filips.twistcounter.domain.usecase

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location

/**
 * Manages GPS location tracking for speed and distance.
 */
@Singleton
class LocationManagerUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensorManagerUseCase: SensorManagerUseCase
) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()
    
    private val _totalDistance = MutableStateFlow(0f)
    val totalDistance: StateFlow<Float> = _totalDistance.asStateFlow()
    
    private val _avgSpeed = MutableStateFlow(0f)
    val avgSpeed: StateFlow<Float> = _avgSpeed.asStateFlow()
    
    private val _maxSpeed = MutableStateFlow(0f)
    val maxSpeed: StateFlow<Float> = _maxSpeed.asStateFlow()
    
    private var lastLocation: Location? = null
    private var speedSamples = mutableListOf<Float>()
    private var rideStartTime: Long = 0L
    private var lastLocationTime: Long = 0L
    
    companion object {
        private const val GPS_STALE_THRESHOLD_MS = 5000L // 5 seconds
        private const val ACCEL_SPEED_DECAY = 0.95f // Speed decay factor when no GPS
    }
    
    // Accelerometer-based speed estimation for GPS outages
    private var accelEstimatedSpeed = 0f
    private var lastAccelMagnitude = 0f
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                processLocation(location)
            }
        }
    }
    
    fun startLocationTracking() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        rideStartTime = System.currentTimeMillis()
        lastLocationTime = 0L
        speedSamples.clear()
        accelEstimatedSpeed = 0f
        lastAccelMagnitude = 0f
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L // 2 second interval
        ).build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                context.mainLooper
            )
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    private fun processLocation(location: Location) {
        val speedKmh = location.speed * 3.6f // m/s to km/h
        _currentSpeed.value = speedKmh
        lastLocationTime = System.currentTimeMillis()
        accelEstimatedSpeed = speedKmh // Reset estimated speed to GPS reading
        
        // Update sensor manager for corner detection speed filter
        sensorManagerUseCase.updateSpeed(speedKmh)
        
        // Track max speed
        if (speedKmh > _maxSpeed.value) {
            _maxSpeed.value = speedKmh
        }
        
        // Track distance
        if (lastLocation != null) {
            val distanceMeters = lastLocation!!.distanceTo(location)
            _totalDistance.value += distanceMeters / 1000f // meters to km
        }
        lastLocation = location
        
        // Track average speed
        speedSamples.add(speedKmh)
        _avgSpeed.value = speedSamples.sum() / speedSamples.size
    }
    
    /**
     * Update speed estimate from accelerometer when GPS is unavailable.
     * Call this periodically from sensor processing.
     */
    fun updateSpeedFromAccelerometer(accelMagnitude: Float, dtSeconds: Float) {
        val now = System.currentTimeMillis()
        
        // Only use accelerometer if GPS is stale (tunnel, woods, etc.)
        if (now - lastLocationTime < GPS_STALE_THRESHOLD_MS) {
            return // GPS is fresh, don't override
        }
        
        // Simple acceleration integration with decay
        // Filter out gravity (approx 9.8 m/s²)
        val accelWithoutGravity = kotlin.math.abs(accelMagnitude - 9.8f)
        
        // Threshold to ignore small vibrations
        if (accelWithoutGravity > 0.5f) {
            // Rough estimate: integrate forward acceleration, apply decay
            val speedChangeMs = accelWithoutGravity * dtSeconds * 0.3f // 0.3 factor for rough estimate
            accelEstimatedSpeed = (accelEstimatedSpeed * ACCEL_SPEED_DECAY) + (speedChangeMs * 3.6f)
        } else {
            // Decay speed when no acceleration
            accelEstimatedSpeed *= ACCEL_SPEED_DECAY
        }
        
        // Clamp to reasonable range
        accelEstimatedSpeed = accelEstimatedSpeed.coerceIn(0f, 150f)
        
        // Update current speed with estimated value
        _currentSpeed.value = accelEstimatedSpeed
        sensorManagerUseCase.updateSpeed(accelEstimatedSpeed)
    }
    
    /**
     * Check if GPS signal is currently stale (lost for more than threshold).
     */
    fun isGpsStale(): Boolean {
        if (lastLocationTime == 0L) return false // Never had GPS
        return System.currentTimeMillis() - lastLocationTime > GPS_STALE_THRESHOLD_MS
    }
    
    fun stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    fun getRideDurationSeconds(): Int {
        return ((System.currentTimeMillis() - rideStartTime) / 1000).toInt()
    }
    
    fun getLocationStats(): LocationStats {
        return LocationStats(
            distanceKm = _totalDistance.value,
            avgSpeedKmh = _avgSpeed.value,
            maxSpeedKmh = _maxSpeed.value,
            durationSeconds = getRideDurationSeconds()
        )
    }
}

data class LocationStats(
    val distanceKm: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val durationSeconds: Int
)