package dev.filips.twistcounter.sensor

import dev.filips.twistcounter.domain.model.CornerDirection
import org.junit.Test
import org.junit.Assert.*

class CornerDetectorTest {

    private val config = CornerDetectionConfig(
        leanThresholdDegrees = 12f,
        hysteresisDegrees = 2f,
        minSpeedKmh = 20f,
        minCornerDurationMs = 500f
    )

    @Test
    fun `no corner detected when lean below threshold`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // Simulate straight riding (lean = 5°)
        val reading = createLeanReading(leanAngle = 5f, timestamp = 1_000_000_000L)
        val event = detector.process(reading, speedKmh = 50f)

        assertNull("No corner should be detected below threshold", event)
        assertEquals(0, detector.getDetectedCorners().size)
    }

    @Test
    fun `corner detected when lean exceeds threshold and returns`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // Simulate entering a corner (lean = 15°)
        var event = detector.process(createLeanReading(15f, 1_000_000_000L), 50f)
        assertNull("Corner not complete yet", event)

        // Peak lean
        event = detector.process(createLeanReading(25f, 1_500_000_000L), 50f)
        assertNull("Still at peak", event)

        // Return below threshold with hysteresis (15° - 2° = 13°, still above 12° threshold)
        // Need to drop below 10° (12° - 2° hysteresis) to end
        event = detector.process(createLeanReading(9f, 2_500_000_000L), 50f)

        // Corner should be detected after returning below threshold with delay
        assertNotNull("Corner should be detected", event)
        assertEquals(25f, event?.peakLeanAngle ?: 0f, 0.1f)
        assertEquals(CornerDirection.RIGHT, event?.direction)
    }

    @Test
    fun `left corner detected for negative lean angles`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // Enter left corner (lean = -20°)
        detector.process(createLeanReading(-20f, 1_000_000_000L), 50f)
        detector.process(createLeanReading(-30f, 1_500_000_000L), 50f)
        val event = detector.process(createLeanReading(-5f, 2_500_000_000L), 50f)

        assertNotNull("Left corner should be detected", event)
        assertEquals(30f, event?.peakLeanAngle ?: 0f, 0.1f)
        assertEquals(CornerDirection.LEFT, event?.direction)
    }

    @Test
    fun `corner not detected below speed threshold`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // Simulate cornering but too slow (10 km/h < 20 km/h threshold)
        detector.process(createLeanReading(20f, 1_000_000_000L), 10f)
        detector.process(createLeanReading(30f, 1_500_000_000L), 10f)
        val event = detector.process(createLeanReading(5f, 2_500_000_000L), 10f)

        assertNull("Corner should not be detected below speed threshold", event)
    }

    @Test
    fun `multiple corners counted correctly`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // First corner (right)
        detector.process(createLeanReading(20f, 1_000_000_000L), 50f)
        detector.process(createLeanReading(5f, 2_000_000_000L), 50f)

        // Second corner (left)
        detector.process(createLeanReading(-20f, 3_000_000_000L), 50f)
        detector.process(createLeanReading(-5f, 4_000_000_000L), 50f)

        // Third corner (right)
        detector.process(createLeanReading(25f, 5_000_000_000L), 50f)
        val event = detector.process(createLeanReading(5f, 6_000_000_000L), 50f)

        assertNotNull("Third corner should be detected", event)
        assertEquals(3, detector.getDetectedCorners().size)
    }

    @Test
    fun `reset clears all state`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // Detect a corner
        detector.process(createLeanReading(20f, 1_000_000_000L), 50f)
        detector.process(createLeanReading(5f, 2_000_000_000L), 50f)

        assertEquals(1, detector.getDetectedCorners().size)

        // Reset
        detector.reset()

        assertEquals(0, detector.getDetectedCorners().size)

        // Next corner should work normally
        val event = detector.process(createLeanReading(20f, 3_000_000_000L), 50f)
            ?.let { detector.process(createLeanReading(5f, 4_000_000_000L), 50f) }
        assertNotNull("Corner detection should work after reset", event)
    }

    @Test
    fun `paused detection blocks corners`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())
        detector.isPaused = true

        val event = detector.process(createLeanReading(25f, 1_000_000_000L), 50f)
        assertNull("No corner when paused", event)

        detector.isPaused = false
        detector.process(createLeanReading(25f, 2_000_000_000L), 50f)
        val eventAfterResume = detector.process(createLeanReading(5f, 3_000_000_000L), 50f)
        assertNotNull("Corner detection resumes after unpausing", eventAfterResume)
    }

    @Test
    fun `brief lean below minimum duration is filtered`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        // Quick lean and return (200ms < 500ms minimum)
        detector.process(createLeanReading(20f, 1_000_000_000L), 50f)
        val event = detector.process(createLeanReading(5f, 1_200_000_000L), 50f)

        assertNull("Brief lean should be filtered out", event)
    }

    @Test
    fun `corner stats calculated correctly`() {
        val detector = CornerDetector(config)
        detector.setRideId(java.util.UUID.randomUUID())

        val startTime = 1_000_000_000L
        val peakTime = 2_000_000_000L
        val endTime = 3_500_000_000L

        detector.process(createLeanReading(15f, startTime), 50f)
        detector.process(createLeanReading(35f, peakTime), 50f)
        val event = detector.process(createLeanReading(5f, endTime), 50f)

        assertNotNull(event)
        assertEquals(35f, event?.peakLeanAngle ?: 0f, 0.1f)
        assertEquals(CornerDirection.RIGHT, event?.direction)

        // Duration in seconds: (3.5s - 1.0s) = 2.5s
        val expectedDuration = (endTime - startTime) / 1_000_000_000f
        assertEquals(expectedDuration, event?.durationSeconds ?: 0f, 0.1f)
    }

    private fun createLeanReading(leanAngle: Float, timestamp: Long): LeanReading {
        return LeanReading(
            timestamp = timestamp,
            leanAngle = leanAngle,
            confidence = 1f
        )
    }
}