package dev.filips.twistcounter.sensor

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class SensorFusionProcessorTest {

    private val config = CornerDetectionConfig()

    @Test
    fun `calibration sets zero reference`() {
        val processor = SensorFusionProcessor(config)

        // Create readings with phone upright (no lean)
        val readings = listOf(
            createSensorReading(gyroZ = 0f, accelY = 0f, accelZ = 9.8f),
            createSensorReading(gyroZ = 0f, accelY = 0f, accelZ = 9.8f),
            createSensorReading(gyroZ = 0f, accelY = 0f, accelZ = 9.8f)
        )

        processor.calibrate(readings)

        assertTrue("Should be calibrated after calibration", processor.isCalibrated())
    }

    @Test
    fun `lean angle calculated correctly for right lean`() {
        val processor = SensorFusionProcessor(config)

        // Calibrate with upright position
        processor.calibrate(listOf(createSensorReading(gyroZ = 0f, accelY = 0f, accelZ = 9.8f)))

        // Simulate right lean (positive roll)
        // When leaning right, gravity component shifts to Y
        val reading = createSensorReading(gyroZ = 0.5f, accelY = 4f, accelZ = 9f)
        val result = processor.process(reading)

        // Should report positive lean angle for right lean
        assertTrue("Right lean should have positive angle", result.leanAngle > 0)
        assertTrue("Should have high confidence", result.confidence > 0.8f)
    }

    @Test
    fun `lean angle calculated correctly for left lean`() {
        val processor = SensorFusionProcessor(config)

        // Calibrate with upright position
        processor.calibrate(listOf(createSensorReading(gyroZ = 0f, accelY = 0f, accelZ = 9.8f)))

        // Simulate left lean (negative roll)
        val reading = createSensorReading(gyroZ = -0.5f, accelY = -4f, accelZ = 9f)
        val result = processor.process(reading)

        // Should report negative lean angle for left lean
        assertTrue("Left lean should have negative angle", result.leanAngle < 0)
    }

    @Test
    fun `low confidence when high acceleration`() {
        val processor = SensorFusionProcessor(config)

        processor.calibrate(listOf(createSensorReading(accelY = 0f, accelZ = 9.8f)))

        // High acceleration (hard braking or acceleration)
        val reading = createSensorReading(accelY = 0f, accelZ = 15f)
        val result = processor.process(reading)

        assertTrue("Should have low confidence with high acceleration", result.confidence < 1f)
    }

    @Test
    fun `low confidence when low gravity`() {
        val processor = SensorFusionProcessor(config)

        processor.calibrate(listOf(createSensorReading(accelY = 0f, accelZ = 9.8f)))

        // Low gravity reading (free fall or bad sensor)
        val reading = createSensorReading(accelY = 0f, accelZ = 5f)
        val result = processor.process(reading)

        assertTrue("Should have low confidence with low gravity", result.confidence < 1f)
    }

    @Test
    fun `reset clears calibration`() {
        val processor = SensorFusionProcessor(config)

        processor.calibrate(listOf(createSensorReading(accelY = 0f, accelZ = 9.8f)))
        assertTrue(processor.isCalibrated())

        processor.reset()
        assertFalse("Should not be calibrated after reset", processor.isCalibrated())
    }

    @Test
    fun `gyro integration contributes to angle`() {
        val processor = SensorFusionProcessor(config)

        // Calibrate
        processor.calibrate(listOf(createSensorReading(gyroZ = 0f, accelY = 0f, accelZ = 9.8f)))

        // First reading with rotation rate
        val reading1 = createSensorReading(gyroZ = 1.0f, accelY = 0f, accelZ = 9.8f)
        processor.process(reading1)

        // Second reading with same rotation rate (integrating)
        val reading2 = SensorReading(
            timestamp = reading1.timestamp + 20_000_000L, // 20ms later
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 1.0f,
            accelX = 0f,
            accelY = 0f,
            accelZ = 9.8f
        )
        val result = processor.process(reading2)

        // Angle should have changed due to gyro integration
        assertTrue("Angle should change with gyro input", abs(result.leanAngle) > 0.1f)
    }

    private fun createSensorReading(
        gyroZ: Float = 0f,
        accelY: Float = 0f,
        accelZ: Float = 9.8f,
        timestamp: Long = System.nanoTime()
    ): SensorReading {
        return SensorReading(
            timestamp = timestamp,
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = gyroZ,
            accelX = 0f,
            accelY = accelY,
            accelZ = accelZ
        )
    }
}