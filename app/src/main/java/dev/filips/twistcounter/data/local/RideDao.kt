package dev.filips.twistcounter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: String): RideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity)

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: String)

    @Query("SELECT COUNT(*) FROM rides")
    suspend fun getRideCount(): Int

    @Transaction
    suspend fun deleteRideWithEvents(rideId: String) {
        deleteCornerEventsForRide(rideId)
        deleteLeanSamplesForRide(rideId)
        deleteRide(rideId)
    }

    @Query("DELETE FROM corner_events WHERE rideId = :rideId")
    suspend fun deleteCornerEventsForRide(rideId: String)

    @Query("DELETE FROM lean_samples WHERE rideId = :rideId")
    suspend fun deleteLeanSamplesForRide(rideId: String)
}

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<dev.filips.twistcounter.data.local.WaypointEntity>)

    @Query("SELECT * FROM waypoints WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getWaypointsForRide(rideId: String): List<dev.filips.twistcounter.data.local.WaypointEntity>

    @Query("DELETE FROM waypoints WHERE rideId = :rideId")
    suspend fun deleteWaypointsForRide(rideId: String)
}

@Dao
interface CornerEventDao {
    @Query("SELECT * FROM corner_events WHERE rideId = :rideId ORDER BY startTime ASC")
    suspend fun getEventsForRide(rideId: String): List<CornerEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CornerEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CornerEventEntity>)

    @Query("SELECT COUNT(*) FROM corner_events WHERE rideId = :rideId")
    suspend fun getCornerCountForRide(rideId: String): Int

    @Query("SELECT MAX(peakLeanAngle) FROM corner_events WHERE rideId = :rideId AND direction = 'RIGHT'")
    suspend fun getMaxLeanRight(rideId: String): Float?

    @Query("SELECT MAX(peakLeanAngle) FROM corner_events WHERE rideId = :rideId AND direction = 'LEFT'")
    suspend fun getMaxLeanLeft(rideId: String): Float?

    @Query("SELECT AVG(peakLeanAngle) FROM corner_events WHERE rideId = :rideId")
    suspend fun getAvgLean(rideId: String): Float?
}

@Dao
interface LeanSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: LeanSampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<LeanSampleEntity>)

    @Query("SELECT * FROM lean_samples WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getSamplesForRide(rideId: String): List<LeanSampleEntity>

    @Query("SELECT * FROM lean_samples WHERE rideId = :rideId AND leanAngle >= :minAngle AND leanAngle <= :maxAngle")
    suspend fun getSamplesInRange(rideId: String, minAngle: Float, maxAngle: Float): List<LeanSampleEntity>
}