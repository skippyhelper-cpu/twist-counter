package dev.filips.twistcounter.sensor

import dev.filips.twistcounter.domain.model.CornerDirection
import dev.filips.twistcounter.domain.model.CornerEvent
import java.time.Instant
import java.util.UUID

/**
 * Detects corners from lean angle readings using a state machine.
 */
class CornerDetector(
    private val config: CornerDetectionConfig = CornerDetectionConfig()
) {
    // Pause detection during recalibration
    var isPaused: Boolean = false
    private var state: CornerState = CornerState.STRAIGHT
    private var currentCornerStart: Long = 0L
    private var currentCornerPeak: Float = 0f
    private var currentCornerDirection: CornerDirection = CornerDirection.LEFT
    private var lastLeanBelowThreshold: Long = 0L

    private val detectedCorners = mutableListOf<CornerEvent>()
    private var rideId: UUID? = null

    fun setRideId(id: UUID) {
        rideId = id
    }

    /**
     * Process a lean reading and detect corners.
     * Returns a CornerEvent if a corner was just completed.
     */
    fun process(reading: LeanReading, speedKmh: Float): CornerEvent? {
        // Skip if detection is paused (e.g., during recalibration)
        if (isPaused) return null
        
        val leanAbs = kotlin.math.abs(reading.leanAngle)
        val now = reading.timestamp

        // Skip if below speed threshold (uses mutable config)
        if (speedKmh < config.minSpeedKmh) {
            return null
        }

        when (state) {
            CornerState.STRAIGHT -> {
                if (leanAbs >= config.leanThresholdDegrees) {
                    state = CornerState.CORNER_START
                    currentCornerStart = now
                    currentCornerPeak = leanAbs
                    currentCornerDirection = if (reading.leanAngle > 0) {
                        CornerDirection.RIGHT
                    } else {
                        CornerDirection.LEFT
                    }
                }
            }

            CornerState.CORNER_START -> {
                // Track peak
                if (leanAbs > currentCornerPeak) {
                    currentCornerPeak = leanAbs
                }

                // Check if we're past the peak
                if (leanAbs < currentCornerPeak - 5f) { // 5 degree drop from peak
                    state = CornerState.CORNER_PEAK
                } else if (leanAbs < config.leanThresholdDegrees) {
                    // Brief lean, might be noise - stay in START
                    lastLeanBelowThreshold = now
                }
            }

            CornerState.CORNER_PEAK -> {
                // Track if lean keeps dropping
                if (leanAbs < config.leanThresholdDegrees - config.hysteresisDegrees) {
                    if (lastLeanBelowThreshold == 0L) {
                        lastLeanBelowThreshold = now
                    }

                    // Confirm corner end after hysteresis delay
                    if (now - lastLeanBelowThreshold > 500_000_000L) { // 0.5 seconds in nanoseconds
                        state = CornerState.CORNER_END
                    }
                } else {
                    // Lean increased again, back to tracking
                    lastLeanBelowThreshold = 0L
                    if (leanAbs > currentCornerPeak) {
                        currentCornerPeak = leanAbs
                        state = CornerState.CORNER_START
                    }
                }
            }

            CornerState.CORNER_END -> {
                // Corner completed, create event
                val durationMs = (now - currentCornerStart) / 1_000_000f
                
                // Filter out very brief leans (jerks/potholes)
                if (durationMs >= config.minCornerDurationMs) {
                    val event = CornerEvent(
                        id = UUID.randomUUID(),
                        rideId = rideId ?: UUID.randomUUID(),
                        startTime = Instant.ofEpochMilli(currentCornerStart / 1_000_000),
                        peakLeanAngle = currentCornerPeak,
                        direction = currentCornerDirection,
                        durationSeconds = durationMs / 1000f
                    )
                    detectedCorners.add(event)
                    state = CornerState.STRAIGHT
                    return event
                }

                // Too brief, discard
                state = CornerState.STRAIGHT
            }
        }

        return null
    }

    fun getDetectedCorners(): List<CornerEvent> = detectedCorners.toList()

    fun reset() {
        state = CornerState.STRAIGHT
        currentCornerStart = 0L
        currentCornerPeak = 0f
        lastLeanBelowThreshold = 0L
        detectedCorners.clear()
    }
}