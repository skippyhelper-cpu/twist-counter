package dev.filips.twistcounter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RideEntity::class,
        CornerEventEntity::class,
        LeanSampleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun cornerEventDao(): CornerEventDao
    abstract fun leanSampleDao(): LeanSampleDao
}