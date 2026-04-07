package dev.filips.twistcounter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class TwistCounterApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            
            // Use INTERNAL cache dir - no permissions needed, never cleared by OS
            osmdroidBasePath = applicationContext.cacheDir
            osmdroidTileCache = File(osmdroidBasePath, "osmdroid")
            osmdroidTileCache?.mkdirs()
            
            // Set user agent for tile server
            userAgentValue = "TwistCounter/1.1.8"
            
            // Debug logging
            android.util.Log.d("TwistCounterMap", "OSMDroid base path: $osmdroidBasePath")
            android.util.Log.d("TwistCounterMap", "OSMDroid tile cache: $osmdroidTileCache")
        }
    }
}