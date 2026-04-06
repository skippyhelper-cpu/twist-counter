package dev.filips.twistcounter.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.filips.twistcounter.R
import dev.filips.twistcounter.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RideForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ride_logging_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_RIDE = "dev.filips.twistcounter.START_RIDE"
        const val ACTION_END_RIDE = "dev.filips.twistcounter.END_RIDE"
        const val ACTION_UPDATE_CORNER_COUNT = "dev.filips.twistcounter.UPDATE_CORNER_COUNT"
        
        const val EXTRA_CORNER_COUNT = "corner_count"
        
        // Shared state for notification updates
        private val _cornerCount = MutableStateFlow(0)
        val cornerCount: StateFlow<Int> = _cornerCount.asStateFlow()
        
        fun updateCornerCount(count: Int) {
            _cornerCount.value = count
        }
    }

    private val binder = LocalBinder()
    private var notificationUpdateJob: Job? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): RideForegroundService = this@RideForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RIDE -> startRideLogging()
            ACTION_END_RIDE -> stopRideLogging()
            ACTION_UPDATE_CORNER_COUNT -> {
                val count = intent.getIntExtra(EXTRA_CORNER_COUNT, 0)
                updateCornerCount(count)
                updateNotification(count)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(cornerCount: Int = 0): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content_with_count, cornerCount))
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startRideLogging() {
        startForeground(NOTIFICATION_ID, createNotification(0))
        
        // Start periodic notification updates
        notificationUpdateJob?.cancel()
        notificationUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val count = cornerCount.value
                updateNotification(count)
                delay(5000) // Update every 5 seconds
            }
        }
    }
    
    private fun updateNotification(cornerCount: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(cornerCount))
    }

    private fun stopRideLogging() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        _cornerCount.value = 0
        stopSelf()
    }
}