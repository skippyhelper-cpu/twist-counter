package dev.filips.twistcounter.domain.model

import java.time.Instant
import java.util.UUID

data class Ride(
    val id: UUID = UUID.randomUUID(),
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    val distanceKm: Float = 0f,
    val durationSeconds: Int = 0,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val cornerCount: Int = 0,
    val maxLeanLeft: Float = 0f,
    val maxLeanRight: Float = 0f,
    val avgLean: Float = 0f
)

data class CornerEvent(
    val id: UUID = UUID.randomUUID(),
    val rideId: UUID,
    val startTime: Instant,
    val peakLeanAngle: Float,
    val direction: CornerDirection,
    val durationSeconds: Float,
    val gpsLat: Float? = null,
    val gpsLng: Float? = null
)

enum class CornerDirection {
    LEFT, RIGHT
}

data class LeanSample(
    val rideId: UUID,
    val timestamp: Instant,
    val leanAngle: Float,
    val speedKmh: Float
)

data class RideSummary(
    val ride: Ride,
    val cornerEvents: List<CornerEvent>,
    val leanHistogram: LeanHistogram
)

data class LeanHistogram(
    val buckets: List<LeanBucket>
)

data class LeanBucket(
    val rangeStart: Float,
    val rangeEnd: Float,
    val count: Int
)