package dev.filips.twistcounter.sensor

/**
 * Raw sensor reading from IMU
 */
data class SensorReading(
    val timestamp: Long,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float
)

/**
 * Processed lean angle data
 */
data class LeanReading(
    val timestamp: Long,
    val leanAngle: Float, // degrees, positive = right lean
    val confidence: Float // 0-1, based on sensor quality
)

/**
 * State machine for corner detection
 */
enum class CornerState {
    STRAIGHT,
    CORNER_START,
    CORNER_PEAK,
    CORNER_END
}

/**
 * Configuration for corner detection
 */
data class CornerDetectionConfig(
    var leanThresholdDegrees: Float = 12f,
    var minSpeedKmh: Float = 20f,
    val minCornerDurationMs: Long = 2000, // reject jerks < 2 seconds
    val hysteresisDegrees: Float = 2f, // lean must drop below threshold - hysteresis
    val sampleRateHz: Int = 50
)