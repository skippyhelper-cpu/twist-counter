package dev.filips.twistcounter.sensor

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Complementary filter for sensor fusion.
 * Combines gyroscope (fast, drifts) and accelerometer (slow, noisy) data.
 */
class SensorFusionProcessor(
    private val config: CornerDetectionConfig = CornerDetectionConfig()
) {
    companion object {
        private const val ALPHA = 0.98f // gyro weight
        private const val RAD_TO_DEG = 180f / kotlin.math.PI.toFloat()
    }

    private var currentAngle: Float = 0f
    private var lastTimestamp: Long = 0L
    private var isCalibrated: Boolean = false
    private var calibrationOffset: Float = 0f

    /**
     * Process a sensor reading and return the estimated lean angle.
     */
    fun process(reading: SensorReading): LeanReading {
        // Calculate dt in seconds
        val dt = if (lastTimestamp == 0L) {
            1f / config.sampleRateHz
        } else {
            (reading.timestamp - lastTimestamp) / 1_000_000_000f // ns to seconds
        }
        lastTimestamp = reading.timestamp

        // Gyroscope: rotation rate around X axis (roll for vertical phone in breast pocket)
        // Note: gyroX positive = rolling LEFT (when phone facing chest)
        // We negate to make positive = leaning RIGHT (motorcycle convention)
        val gyroRate = -reading.gyroX // rad/s, positive = leaning right
        val gyroDelta = gyroRate * dt

        // Accelerometer roll calculation (gravity vector)
        // For breast pocket: phone vertical, screen facing chest
        // Roll = atan2(-accelX, accelY) measures left/right lean angle
        // When leaning right: accelX goes negative, so we negate for positive angle
        val accelRoll = atan2(
            -reading.accelX.toDouble(),  // Negate: left/right gravity component
            reading.accelY.toDouble()   // Vertical gravity component
        )

        // Complementary filter
        currentAngle = ALPHA * (currentAngle + gyroDelta) + (1 - ALPHA) * accelRoll.toFloat()

        // Convert to degrees and apply calibration
        val leanDegrees = (currentAngle * RAD_TO_DEG) - calibrationOffset

        // Calculate confidence based on acceleration magnitude
        val accelMag = sqrt(
            reading.accelX * reading.accelX +
            reading.accelY * reading.accelY +
            reading.accelZ * reading.accelZ
        )
        val confidence = when {
            accelMag < 8f -> 0.5f // Low gravity - motorcycle might be accelerating hard
            accelMag > 12f -> 0.5f // High acceleration - bumps or braking
            else -> 1f
        }

        return LeanReading(
            timestamp = reading.timestamp,
            leanAngle = leanDegrees,
            confidence = confidence
        )
    }

    /**
     * Calibrate the zero-lean reference.
     * Call when the motorcycle is stationary and upright.
     */
    fun calibrate(readings: List<SensorReading>) {
        if (readings.isEmpty()) return

        // Average the accel roll over calibration period
        // For breast pocket: atan2(-accelX, accelY) gives roll angle
        var sumAngles = 0f
        readings.forEach { reading ->
            val accelRoll = atan2(
                -reading.accelX.toDouble(),  // Negated for correct sign
                reading.accelY.toDouble()   // Vertical component
            )
            sumAngles += (accelRoll * RAD_TO_DEG).toFloat()
        }
        calibrationOffset = sumAngles / readings.size
        
        // CRITICAL: Seed currentAngle to match calibration baseline
        currentAngle = (calibrationOffset / RAD_TO_DEG)
        
        isCalibrated = true
    }

    fun reset() {
        currentAngle = 0f
        lastTimestamp = 0L
        isCalibrated = false
        calibrationOffset = 0f
    }

    fun isCalibrated(): Boolean = isCalibrated
    
    /**
     * Get the current calibration offset (baseline zero-lean reference).
     */
    fun getCalibrationOffset(): Float = calibrationOffset
}