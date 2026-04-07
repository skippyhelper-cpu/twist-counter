package dev.filips.twistcounter.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.filips.twistcounter.data.local.CornerEventDao
import dev.filips.twistcounter.data.local.LeanSampleDao
import dev.filips.twistcounter.data.local.RideDao
import dev.filips.twistcounter.data.local.RideDatabase
import dev.filips.twistcounter.data.local.WaypointDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RideDatabase {
        return Room.databaseBuilder(
            context,
            RideDatabase::class.java,
            "twistcounter_db"
        )
        .fallbackToDestructiveMigration() // Clear data on version change (v1 to v2 adds waypoints)
        .build()
    }

    @Provides
    fun provideRideDao(database: RideDatabase): RideDao {
        return database.rideDao()
    }

    @Provides
    fun provideCornerEventDao(database: RideDatabase): CornerEventDao {
        return database.cornerEventDao()
    }

    @Provides
    fun provideLeanSampleDao(database: RideDatabase): LeanSampleDao {
        return database.leanSampleDao()
    }

    @Provides
    fun provideWaypointDao(database: RideDatabase): WaypointDao {
        return database.waypointDao()
    }
}