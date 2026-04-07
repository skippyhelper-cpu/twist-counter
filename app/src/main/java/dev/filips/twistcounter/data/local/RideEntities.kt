package dev.filips.twistcounter.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.filips.twistcounter.domain.model.CornerDirection
import java.time.Instant
import java.util.UUID

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey
    val id: String,
    val startTime: Long, // epoch millis
    val endTime: Long?, // epoch millis, null if in progress
    val distanceKm: Float,
    val durationSeconds: Int,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val cornerCount: Int,
    val maxLeanLeft: Float,
    val maxLeanRight: Float,
    val avgLean: Float,
    val maxAccelG: Float,
    val maxBrakeG: Float
)

@Entity(
    tableName = "corner_events",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideId")]
)
data class CornerEventEntity(
    @PrimaryKey
    val id: String,
    val rideId: String,
    val startTime: Long,
    val peakLeanAngle: Float,
    val direction: String, // LEFT or RIGHT
    val durationSeconds: Float,
    val gpsLat: Float?,
    val gpsLng: Float?
)

@Entity(
    tableName = "lean_samples",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideId")]
)
data class LeanSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rideId: String,
    val timestamp: Long,
    val leanAngle: Float,
    val speedKmh: Float
)

@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideId")]
)
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rideId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val leanAngle: Float,
    val speedKmh: Float,
    val accelG: Float,
    val isCorner: Boolean
)

// Extension functions to convert between domain models and entities

fun RideEntity.toDomain() = dev.filips.twistcounter.domain.model.Ride(
    id = UUID.fromString(id),
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    avgSpeedKmh = avgSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    cornerCount = cornerCount,
    maxLeanLeft = maxLeanLeft,
    maxLeanRight = maxLeanRight,
    avgLean = avgLean,
    maxAccelG = maxAccelG,
    maxBrakeG = maxBrakeG
)

fun dev.filips.twistcounter.domain.model.Ride.toEntity() = RideEntity(
    id = id.toString(),
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    avgSpeedKmh = avgSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    cornerCount = cornerCount,
    maxLeanLeft = maxLeanLeft,
    maxLeanRight = maxLeanRight,
    avgLean = avgLean,
    maxAccelG = maxAccelG,
    maxBrakeG = maxBrakeG
)

fun CornerEventEntity.toDomain() = dev.filips.twistcounter.domain.model.CornerEvent(
    id = UUID.fromString(id),
    rideId = UUID.fromString(rideId),
    startTime = Instant.ofEpochMilli(startTime),
    peakLeanAngle = peakLeanAngle,
    direction = CornerDirection.valueOf(direction),
    durationSeconds = durationSeconds,
    gpsLat = gpsLat,
    gpsLng = gpsLng
)

fun dev.filips.twistcounter.domain.model.CornerEvent.toEntity() = CornerEventEntity(
    id = id.toString(),
    rideId = rideId.toString(),
    startTime = startTime.toEpochMilli(),
    peakLeanAngle = peakLeanAngle,
    direction = direction.name,
    durationSeconds = durationSeconds,
    gpsLat = gpsLat,
    gpsLng = gpsLng
)

fun WaypointEntity.toDomain() = dev.filips.twistcounter.domain.model.RideWaypoint(
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp,
    leanAngle = leanAngle,
    speedKmh = speedKmh,
    accelG = accelG,
    isCorner = isCorner
)

fun dev.filips.twistcounter.domain.model.RideWaypoint.toEntity(rideId: String) = WaypointEntity(
    rideId = rideId,
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp,
    leanAngle = leanAngle,
    speedKmh = speedKmh,
    accelG = accelG,
    isCorner = isCorner
)