package dev.filips.twistcounter.presentation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.filips.twistcounter.data.local.toEntity
import dev.filips.twistcounter.data.repository.RideRepository
import dev.filips.twistcounter.domain.model.CornerDirection
import dev.filips.twistcounter.domain.model.CornerEvent
import dev.filips.twistcounter.domain.model.LeanBucket
import dev.filips.twistcounter.domain.model.LeanHistogram
import dev.filips.twistcounter.domain.model.LeanSample
import dev.filips.twistcounter.domain.model.Ride
import dev.filips.twistcounter.domain.model.RideSummary
import dev.filips.twistcounter.domain.service.RideForegroundService
import dev.filips.twistcounter.domain.usecase.LocationManagerUseCase
import dev.filips.twistcounter.domain.usecase.LocationStats
import dev.filips.twistcounter.domain.usecase.RideStats
import dev.filips.twistcounter.domain.usecase.SensorManagerUseCase
import dev.filips.twistcounter.sensor.SensorFusionProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RideViewModel @Inject constructor(
    application: Application,
    private val rideRepository: RideRepository,
    private val cornerEventDao: dev.filips.twistcounter.data.local.CornerEventDao,
    private val sensorManagerUseCase: SensorManagerUseCase,
    private val locationManagerUseCase: LocationManagerUseCase
) : AndroidViewModel(application) {

    // Ride state
    private val _rideState = MutableStateFlow<RideState>(RideState.Idle)
    val rideState: StateFlow<RideState> = _rideState.asStateFlow()
    val rideStateLiveData = rideState.asLiveData()
    
    // Current lean angle (live display)
    val currentLeanAngle: StateFlow<Float> = sensorManagerUseCase.currentLeanAngle
    val currentLeanAngleLiveData = currentLeanAngle.asLiveData()
    
    // Calibration
    val calibrationProgress: StateFlow<Float> = sensorManagerUseCase.calibrationProgress
    val calibrationProgressLiveData = calibrationProgress.asLiveData()
    
    val isCalibrated: StateFlow<Boolean> = sensorManagerUseCase.isCalibrated
    val isCalibratedLiveData = isCalibrated.asLiveData()
    
    // Ride tracking stats
    val cornerCount: StateFlow<Int> = sensorManagerUseCase.cornerCountFlow
    val cornerCountLiveData = cornerCount.asLiveData()
    
    val maxLeanLeft: StateFlow<Float> = sensorManagerUseCase.maxLeanLeftFlow
    val maxLeanLeftLiveData = maxLeanLeft.asLiveData()
    
    val maxLeanRight: StateFlow<Float> = sensorManagerUseCase.maxLeanRightFlow
    val maxLeanRightLiveData = maxLeanRight.asLiveData()
    
    // Location stats
    val currentSpeed: StateFlow<Float> = locationManagerUseCase.currentSpeed
    val currentSpeedLiveData = currentSpeed.asLiveData()
    
    val totalDistance: StateFlow<Float> = locationManagerUseCase.totalDistance
    val totalDistanceLiveData = totalDistance.asLiveData()
    
    // Ride history
    private val _rideHistory = MutableStateFlow<List<Ride>>(emptyList())
    val rideHistory: StateFlow<List<Ride>> = _rideHistory.asStateFlow()
    val rideHistoryLiveData = rideHistory.asLiveData()
    
    // Current ride summary (post-ride)
    private val _currentRideSummary = MutableStateFlow<RideSummary?>(null)
    val currentRideSummary: StateFlow<RideSummary?> = _currentRideSummary.asStateFlow()
    val currentRideSummaryLiveData = currentRideSummary.asLiveData()
    
    private var currentRideId: UUID? = null
    private val cornerEvents = mutableListOf<CornerEvent>()
    private val leanSamples = mutableListOf<LeanSample>()
    
    init {
        loadRideHistory()
        
        // Update foreground service notification when corner count changes
        viewModelScope.launch {
            cornerCount.collect { count ->
                RideForegroundService.updateCornerCount(count)
            }
        }
    }
    
    fun loadRideHistory() {
        viewModelScope.launch {
            rideRepository.getAllRides().collect { rides ->
                _rideHistory.value = rides
            }
        }
    }
    
    fun startCalibration() {
        _rideState.value = RideState.Calibrating
        sensorManagerUseCase.startCalibration(viewModelScope, durationSeconds = 15) {
            startRideFromCalibration()
        }
    }

    fun recalibrate() {
        _rideState.value = RideState.Calibrating
        sensorManagerUseCase.recalibrate(viewModelScope) {
            startRideFromCalibration()
        }
    }
    
    fun startRideFromCalibration() {
        startRide()
    }

    private fun startRide() {
        if (_rideState.value == RideState.InProgress) return // Prevent double-start
        currentRideId = UUID.randomUUID()
        leanSamples.clear()
        cornerEvents.clear()

        _rideState.value = RideState.InProgress
        
        // Set up corner event persistence callback
        sensorManagerUseCase.onCornerDetected = { cornerEvent ->
            // Assign ride ID and add to list
            val eventWithRideId = cornerEvent.copy(rideId = currentRideId!!)
            cornerEvents.add(eventWithRideId)
        }
        
        // Set up accelerometer-based speed fallback for GPS outages
        sensorManagerUseCase.onAccelSpeedUpdate = { accelMagnitude, dt ->
            locationManagerUseCase.updateSpeedFromAccelerometer(accelMagnitude, dt)
        }
        
        // Start sensor tracking
        sensorManagerUseCase.startRideTracking(viewModelScope)
        
        // Start location tracking
        locationManagerUseCase.startLocationTracking()
        
        // Start foreground service
        val context: android.content.Context = getApplication()
        val intent = Intent(context, RideForegroundService::class.java).apply {
            action = RideForegroundService.ACTION_START_RIDE
        }
        context.startForegroundService(intent)
    }
    
    fun endRide() {
        _rideState.value = RideState.Finished
        
        // Clear corner detection callback
        sensorManagerUseCase.onCornerDetected = null
        sensorManagerUseCase.onAccelSpeedUpdate = null
        
        // Stop sensors
        sensorManagerUseCase.stopSensors()
        
        // Stop location tracking
        locationManagerUseCase.stopLocationTracking()
        
        // Stop foreground service
        val context: android.content.Context = getApplication()
        val intent = Intent(context, RideForegroundService::class.java).apply {
            action = RideForegroundService.ACTION_END_RIDE
        }
        context.stopService(intent)
        
        // Collect stats from in-memory data
        val rideStats = sensorManagerUseCase.getRideStats()
        val locationStats = locationManagerUseCase.getLocationStats()
        
        // Create ride object
        val ride = Ride(
            id = currentRideId!!,
            startTime = Instant.now().minusSeconds(locationStats.durationSeconds.toLong()),
            endTime = Instant.now(),
            distanceKm = locationStats.distanceKm,
            durationSeconds = locationStats.durationSeconds,
            avgSpeedKmh = locationStats.avgSpeedKmh,
            maxSpeedKmh = locationStats.maxSpeedKmh,
            cornerCount = rideStats.cornerCount,
            maxLeanLeft = rideStats.maxLeanLeft,
            maxLeanRight = rideStats.maxLeanRight,
            avgLean = if (rideStats.cornerCount > 0) {
                (rideStats.maxLeanLeft + rideStats.maxLeanRight) / 2f
            } else 0f
        )
        
        // Build RideSummary from in-memory data BEFORE saving
        val histogramData = sensorManagerUseCase.getInMemoryLeanHistogram()
        val leanHistogram = LeanHistogram(
            buckets = histogramData.map { (range, count) ->
                LeanBucket(range.start, range.end, count)
            }
        )
        val summary = RideSummary(
            ride = ride,
            cornerEvents = cornerEvents.toList(),
            leanHistogram = leanHistogram
        )
        
        // Set summary immediately for display
        _currentRideSummary.value = summary
        
        viewModelScope.launch {
            // Save ride to database
            rideRepository.saveRide(ride)
            
            // Save corner events
            if (cornerEvents.isNotEmpty()) {
                cornerEvents.forEach { event ->
                    cornerEventDao.insert(event.toEntity())
                }
            }
            
            // Save lean samples if available
            if (leanSamples.isNotEmpty()) {
                rideRepository.saveLeanSamples(leanSamples)
            }
        }
    }
    
    fun discardRide() {
        currentRideId?.let { id ->
            viewModelScope.launch {
                rideRepository.deleteRide(id)
            }
        }
        _rideState.value = RideState.Idle
        _currentRideSummary.value = null
    }
    
    fun saveRide() {
        // Ride is already saved in endRide()
        _rideState.value = RideState.Idle
        _currentRideSummary.value = null
    }
    
    fun deleteRide(rideId: UUID) {
        viewModelScope.launch {
            rideRepository.deleteRide(rideId)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        sensorManagerUseCase.stopSensors()
        locationManagerUseCase.stopLocationTracking()
    }
}

sealed class RideState {
    object Idle : RideState()
    object Calibrating : RideState()
    object InProgress : RideState()
    object Finished : RideState()
    object Settings : RideState()
}