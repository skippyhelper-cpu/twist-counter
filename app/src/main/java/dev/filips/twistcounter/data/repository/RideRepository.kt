package dev.filips.twistcounter.data.repository

import dev.filips.twistcounter.data.local.CornerEventDao
import dev.filips.twistcounter.data.local.CornerEventEntity
import dev.filips.twistcounter.data.local.LeanSampleDao
import dev.filips.twistcounter.data.local.LeanSampleEntity
import dev.filips.twistcounter.data.local.RideDao
import dev.filips.twistcounter.data.local.RideDatabase
import dev.filips.twistcounter.data.local.RideEntity
import dev.filips.twistcounter.data.local.WaypointDao
import dev.filips.twistcounter.data.local.toDomain
import dev.filips.twistcounter.data.local.toEntity
import dev.filips.twistcounter.domain.model.CornerEvent
import dev.filips.twistcounter.domain.model.LeanBucket
import dev.filips.twistcounter.domain.model.LeanHistogram
import dev.filips.twistcounter.domain.model.LeanSample
import dev.filips.twistcounter.domain.model.Ride
import dev.filips.twistcounter.domain.model.RideSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepository @Inject constructor(
    private val database: RideDatabase,
    private val rideDao: RideDao,
    private val cornerEventDao: CornerEventDao,
    private val leanSampleDao: LeanSampleDao,
    private val waypointDao: WaypointDao
) {
    fun getAllRides(): Flow<List<Ride>> {
        return rideDao.getAllRides().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getRideById(rideId: UUID): Ride? {
        return rideDao.getRideById(rideId.toString())?.toDomain()
    }

    suspend fun saveRide(ride: Ride) {
        rideDao.insertRide(ride.toEntity())
    }

    suspend fun deleteRide(rideId: UUID) {
        rideDao.deleteRideWithEvents(rideId.toString())
        waypointDao.deleteWaypointsForRide(rideId.toString())
    }

    suspend fun saveWaypoints(rideId: UUID, waypoints: List<dev.filips.twistcounter.domain.model.RideWaypoint>) {
        if (waypoints.isEmpty()) return
        val entities = waypoints.map { it.toEntity(rideId.toString()) }
        waypointDao.insertWaypoints(entities)
    }

    suspend fun getWaypointsForRide(rideId: UUID): List<dev.filips.twistcounter.domain.model.RideWaypoint> {
        return waypointDao.getWaypointsForRide(rideId.toString()).map { it.toDomain() }
    }

    suspend fun saveCornerEvent(event: CornerEvent) {
        cornerEventDao.insertEvent(event.toEntity())
    }

    suspend fun saveCornerEvents(events: List<CornerEvent>) {
        cornerEventDao.insertEvents(events.map { it.toEntity() })
    }

    suspend fun getCornerEventsForRide(rideId: UUID): List<CornerEvent> {
        return cornerEventDao.getEventsForRide(rideId.toString()).map { it.toDomain() }
    }

    suspend fun saveLeanSamples(samples: List<LeanSample>) {
        if (samples.isEmpty()) return
        leanSampleDao.insertSamples(samples.map { sample ->
            LeanSampleEntity(
                rideId = sample.rideId.toString(),
                timestamp = sample.timestamp.toEpochMilli(),
                leanAngle = sample.leanAngle,
                speedKmh = sample.speedKmh
            )
        })
    }

    suspend fun getRideSummary(rideId: UUID): RideSummary? {
        val ride = getRideById(rideId) ?: return null
        val corners = getCornerEventsForRide(rideId)
        val histogram = calculateLeanHistogram(rideId)
        
        return RideSummary(
            ride = ride,
            cornerEvents = corners,
            leanHistogram = histogram
        )
    }

    private suspend fun calculateLeanHistogram(rideId: UUID): LeanHistogram {
        val samples = leanSampleDao.getSamplesForRide(rideId.toString())
        
        // Create buckets: 0-10, 10-20, 20-30, 30-40, 40-50, 50+
        val buckets = listOf(
            LeanBucket(0f, 10f, 0),
            LeanBucket(10f, 20f, 0),
            LeanBucket(20f, 30f, 0),
            LeanBucket(30f, 40f, 0),
            LeanBucket(40f, 50f, 0),
            LeanBucket(50f, 90f, 0)
        ).toMutableList()

        samples.forEach { sample ->
            val absAngle = kotlin.math.abs(sample.leanAngle)
            val bucketIndex = when {
                absAngle < 10 -> 0
                absAngle < 20 -> 1
                absAngle < 30 -> 2
                absAngle < 40 -> 3
                absAngle < 50 -> 4
                else -> 5
            }
            buckets[bucketIndex] = buckets[bucketIndex].copy(
                count = buckets[bucketIndex].count + 1
            )
        }

        return LeanHistogram(buckets)
    }

    suspend fun getTotalCornerCount(): Int {
        return rideDao.getRideCount()
    }
}