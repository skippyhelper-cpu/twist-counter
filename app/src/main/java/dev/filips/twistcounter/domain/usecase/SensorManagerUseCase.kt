package dev.filips.twistcounter.domain.usecase

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.filips.twistcounter.data.preferences.RideSettings
import dev.filips.twistcounter.sensor.CornerDetector
import dev.filips.twistcounter.sensor.CornerDetectionConfig
import dev.filips.twistcounter.sensor.LeanReading
import dev.filips.twistcounter.sensor.SensorFusionProcessor
import dev.filips.twistcounter.sensor.SensorReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sensor data acquisition and processing.
 * Combines gyroscope and accelerometer data to estimate lean angles.
 */
@Singleton
class SensorManagerUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rideSettings: RideSettings
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasRequiredSensors: Boolean
        get() = gyroscope != null && accelerometer != null
    
    private var sensorJob: Job? = null
    private var recalibrationJob: Job? = null
    
    // Use config with mutable thresholds from settings
    private val cornerConfig = CornerDetectionConfig(
        leanThresholdDegrees = rideSettings.leanThresholdDegrees,
        minSpeedKmh = rideSettings.speedThresholdKmh
    )
    private val fusionProcessor = SensorFusionProcessor(cornerConfig)
    private val cornerDetector = CornerDetector(cornerConfig)
    
    private val _currentLeanAngle = MutableStateFlow(0f)
    val currentLeanAngle: StateFlow<Float> = _currentLeanAngle.asStateFlow()
    
    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()
    
    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated.asStateFlow()
    
    private val _cornerDetected = Channel<Float>(Channel.CONFLATED)
    val cornerDetected = _cornerDetected.receiveAsFlow()
    
    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()
    
    private var latestAccelData: FloatArray? = null
    private var latestGyroData: FloatArray? = null
    private var lastSensorTimestamp: Long = 0L
    
    private val calibrationReadings = mutableListOf<SensorReading>()
    
    private var cornerCount = 0
    private val _cornerCountFlow = MutableStateFlow(0)
    val cornerCountFlow: StateFlow<Int> = _cornerCountFlow.asStateFlow()
    
    private var maxLeanLeft = 0f
    private var maxLeanRight = 0f
    private val _maxLeanLeftFlow = MutableStateFlow(0f)
    private val _maxLeanRightFlow = MutableStateFlow(0f)
    val maxLeanLeftFlow: StateFlow<Float> = _maxLeanLeftFlow.asStateFlow()
    val maxLeanRightFlow: StateFlow<Float> = _maxLeanRightFlow.asStateFlow()
    
    // Auto-recalibration state
    private var baselineLeanAngle: Float = 0f // The calibrated zero-lean reference
    private var driftAccumulator: Float = 0f
    private var driftStartTime: Long = 0L
    private var isRecalibrating: Boolean = false
    
    companion object {
        private const val DRIFT_THRESHOLD_DEGREES = 10f // Trigger recalibration at this drift
        private const val DRIFT_DURATION_THRESHOLD_MS = 5000L // Must persist for this duration
        private const val RECALIBRATION_DURATION_SECONDS = 5
    }
    
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val timestamp = event.timestamp
            
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    latestGyroData = event.values.copyOf()
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    latestAccelData = event.values.copyOf()
                }
            }
            
            // Process when we have both readings - NULL SAFETY FIX
            val gyro = latestGyroData
            val accel = latestAccelData
            if (gyro != null && accel != null && timestamp > lastSensorTimestamp) {
                lastSensorTimestamp = timestamp
                
                val reading = SensorReading(
                    timestamp = timestamp,
                    gyroX = gyro[0],
                    gyroY = gyro[1],
                    gyroZ = gyro[2],
                    accelX = accel[0],
                    accelY = accel[1],
                    accelZ = accel[2]
                )
                
                processSensorReading(reading)
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // No action needed
        }
    }
    
    fun startCalibration(scope: CoroutineScope, durationSeconds: Int = 15, onComplete: () -> Unit = {}) {
        calibrationReadings.clear()
        fusionProcessor.reset()
        _calibrationProgress.value = 0f
        _isCalibrated.value = false
        baselineLeanAngle = 0f

        startSensors()

        // Collect readings during calibration
        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val durationMs = durationSeconds * 1000L

            while (System.currentTimeMillis() - startTime < durationMs && !_isCalibrated.value) {
                val elapsed = System.currentTimeMillis() - startTime
                _calibrationProgress.value = elapsed.toFloat() / durationMs

                // Collect readings - NULL SAFETY FIX
                val gyro = latestGyroData
                val accel = latestAccelData
                if (gyro != null && accel != null && lastSensorTimestamp > 0L) {
                    calibrationReadings.add(SensorReading(
                        timestamp = lastSensorTimestamp,
                        gyroX = gyro[0],
                        gyroY = gyro[1],
                        gyroZ = gyro[2],
                        accelX = accel[0],
                        accelY = accel[1],
                        accelZ = accel[2]
                    ))
                }

                kotlinx.coroutines.delay(100)
            }

            // Perform calibration
            fusionProcessor.calibrate(calibrationReadings)
            baselineLeanAngle = fusionProcessor.getCalibrationOffset()
            _isCalibrated.value = true
            _calibrationProgress.value = 1f
            onComplete() // notify immediately when done
        }
    }

    fun recalibrate(scope: CoroutineScope, onComplete: () -> Unit = {}) {
        stopSensors()
        startCalibration(scope, durationSeconds = 10, onComplete = onComplete)
    }
    
    fun startRideTracking(scope: CoroutineScope) {
        // Apply current settings to config
        cornerConfig.leanThresholdDegrees = rideSettings.leanThresholdDegrees
        cornerConfig.minSpeedKmh = rideSettings.speedThresholdKmh
        
        cornerDetector.reset()
        cornerDetector.isPaused = false
        cornerCount = 0
        maxLeanLeft = 0f
        maxLeanRight = 0f
        _cornerCountFlow.value = 0
        _maxLeanLeftFlow.value = 0f
        _maxLeanRightFlow.value = 0f
        
        // Reset auto-recalibration state
        driftAccumulator = 0f
        driftStartTime = 0L
        isRecalibrating = false
        
        // Ensure sensors are running
        startSensors()
    }
    
    private fun startSensors() {
        gyroscope?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    
    private fun processSensorReading(reading: SensorReading) {
        if (!_isCalibrated.value) return
        
        val leanReading = fusionProcessor.process(reading)
        _currentLeanAngle.value = leanReading.leanAngle
        
        // Track max lean
        if (leanReading.leanAngle < 0 && kotlin.math.abs(leanReading.leanAngle) > maxLeanLeft) {
            maxLeanLeft = kotlin.math.abs(leanReading.leanAngle)
            _maxLeanLeftFlow.value = maxLeanLeft
        } else if (leanReading.leanAngle > 0 && leanReading.leanAngle > maxLeanRight) {
            maxLeanRight = leanReading.leanAngle
            _maxLeanRightFlow.value = maxLeanRight
        }
        
        // Auto-recalibration: monitor for drift from baseline
        checkForDrift(leanReading)
        
        // Detect corners (will skip if isPaused/isRecalibrating)
        val cornerEvent = cornerDetector.process(leanReading, _currentSpeed.value)
        if (cornerEvent != null) {
            cornerCount++
            _cornerCountFlow.value = cornerCount
            _cornerDetected.trySend(cornerEvent.peakLeanAngle)
        }
    }
    
    private fun checkForDrift(reading: LeanReading) {
        if (isRecalibrating) return
        
        // Check if current "steady" lean differs from baseline
        // If the lean angle stays near a constant offset for too long, phone may have shifted
        val currentOffset = reading.leanAngle
        
        // Only check during "steady" state (confidence high, not actively cornering)
        if (reading.confidence < 0.7f) {
            // Reset drift tracking during uncertain readings
            driftAccumulator = 0f
            driftStartTime = 0L
            return
        }
        
        val driftFromBaseline = kotlin.math.abs(currentOffset - baselineLeanAngle)
        
        if (driftFromBaseline > DRIFT_THRESHOLD_DEGREES) {
            if (driftStartTime == 0L) {
                driftStartTime = System.currentTimeMillis()
            }
            
            val driftDuration = System.currentTimeMillis() - driftStartTime
            
            // If drift persists for threshold duration, trigger silent recalibration
            if (driftDuration >= DRIFT_DURATION_THRESHOLD_MS) {
                triggerSilentRecalibration()
            }
        } else {
            // Reset if drift returns below threshold
            driftStartTime = 0L
            driftAccumulator = 0f
        }
    }
    
    private fun triggerSilentRecalibration() {
        if (isRecalibrating) return
        
        isRecalibrating = true
        cornerDetector.isPaused = true // Pause corner detection
        
        val recalibrationReadings = mutableListOf<SensorReading>()
        val recalibrationStartTime = System.currentTimeMillis()
        
        recalibrationJob?.cancel()
        recalibrationJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            // Collect readings for recalibration duration
            while (System.currentTimeMillis() - recalibrationStartTime < RECALIBRATION_DURATION_SECONDS * 1000L) {
                val gyro = latestGyroData
                val accel = latestAccelData
                if (gyro != null && accel != null && lastSensorTimestamp > 0L) {
                    recalibrationReadings.add(SensorReading(
                        timestamp = lastSensorTimestamp,
                        gyroX = gyro[0],
                        gyroY = gyro[1],
                        gyroZ = gyro[2],
                        accelX = accel[0],
                        accelY = accel[1],
                        accelZ = accel[2]
                    ))
                }
                kotlinx.coroutines.delay(100)
            }
            
            // Apply new calibration offset
            if (recalibrationReadings.isNotEmpty()) {
                fusionProcessor.calibrate(recalibrationReadings)
                baselineLeanAngle = fusionProcessor.getCalibrationOffset()
            }
            
            // Resume corner detection
            isRecalibrating = false
            cornerDetector.isPaused = false
            driftStartTime = 0L
            driftAccumulator = 0f
        }
    }
    
    fun updateSpeed(speedKmh: Float) {
        _currentSpeed.value = speedKmh
    }
    
    fun stopSensors() {
        sensorManager.unregisterListener(sensorListener)
        latestAccelData = null
        latestGyroData = null
        lastSensorTimestamp = 0L
        recalibrationJob?.cancel()
        recalibrationJob = null
        isRecalibrating = false
        cornerDetector.isPaused = false
    }
    
    fun getRideStats(): RideStats {
        return RideStats(
            cornerCount = cornerCount,
            maxLeanLeft = maxLeanLeft,
            maxLeanRight = maxLeanRight
        )
    }
    
    fun getInMemoryLeanHistogram(): List<Pair<FloatRange, Int>> {
        // Return in-memory histogram data for ride summary
        // This will be used to populate RideSummary before saving
        return emptyList() // Placeholder - actual histogram would be tracked during ride
    }
}

data class RideStats(
    val cornerCount: Int,
    val maxLeanLeft: Float,
    val maxLeanRight: Float
)

data class FloatRange(val start: Float, val end: Float)