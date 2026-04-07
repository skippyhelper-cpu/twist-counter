package dev.filips.twistcounter.presentation

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import dagger.hilt.android.AndroidEntryPoint
import dev.filips.twistcounter.R
import dev.filips.twistcounter.databinding.ActivityMainBinding
import dev.filips.twistcounter.domain.model.Ride
import dev.filips.twistcounter.domain.model.RideTrack
import dev.filips.twistcounter.presentation.adapter.RideHistoryAdapter
import dev.filips.twistcounter.presentation.viewmodel.RideState
import dev.filips.twistcounter.presentation.viewmodel.RideViewModel
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: RideViewModel by viewModels()
    private lateinit var rideHistoryAdapter: RideHistoryAdapter
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.startCalibration()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        
        // Handle back button to restore home screen when fragments are showing
        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                showHomeScreen()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }
    
    private fun setupRecyclerView() {
        rideHistoryAdapter = RideHistoryAdapter { ride ->
            openRideSummary(ride)
        }
        
        binding.ridesRecyclerView.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            adapter = rideHistoryAdapter
        }
        
        // Add swipe-to-delete functionality
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(
            object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                0, // No drag support
                androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                    target: androidx.recyclerview.widget.RecyclerView.ViewHolder
                ): Boolean = false // Don't support drag

                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val ride = rideHistoryAdapter.currentList[position]
                    
                    // Delete the ride
                    viewModel.deleteRide(ride.id)
                    
                    // Show confirmation
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Ride deleted",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        itemTouchHelper.attachToRecyclerView(binding.ridesRecyclerView)
    }
    
    private fun openRideSummary(ride: Ride) {
        // Hide home screen elements
        binding.startRideButton.visibility = View.GONE
        binding.settingsButton.visibility = View.GONE
        binding.rideHistoryHeader.visibility = View.GONE
        binding.noRidesText.visibility = View.GONE
        binding.ridesRecyclerView.visibility = View.GONE
        
        val fragment = RideSummaryFragment.newInstance(rideId = ride.id)
        supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun setupObservers() {
        viewModel.rideStateLiveData.observe(this) { state ->
            when (state) {
                RideState.Idle -> showHomeScreen()
                RideState.Calibrating -> showCalibrationScreen()
                RideState.InProgress -> showRideInProgressScreen()
                RideState.Finished -> showRideSummaryScreen()
                RideState.Settings -> showSettingsScreen()
            }
        }
        
        // Observe ride history
        viewModel.rideHistoryLiveData.observe(this) { rides ->
            rideHistoryAdapter.submitList(rides)
            updateRideHistoryVisibility(rides.isNotEmpty())
        }
    }
    
    private fun updateRideHistoryVisibility(hasRides: Boolean) {
        if (hasRides) {
            binding.noRidesText.visibility = View.GONE
            binding.ridesRecyclerView.visibility = View.VISIBLE
        } else {
            binding.noRidesText.visibility = View.VISIBLE
            binding.ridesRecyclerView.visibility = View.GONE
        }
    }
    
    private fun setupClickListeners() {
        binding.startRideButton.setOnClickListener {
            checkPermissionsAndStart()
        }
        
        binding.settingsButton.setOnClickListener {
            showSettingsScreen()
        }
    }
    
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        // ACTIVITY_RECOGNITION only needed for Android 14+ (API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        val needsRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needsRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(needsRequest)
        } else {
            viewModel.startCalibration()
        }
    }
    
    private fun showHomeScreen() {
        binding.startRideButton.visibility = View.VISIBLE
        binding.settingsButton.visibility = View.VISIBLE
        binding.rideHistoryHeader.visibility = View.VISIBLE
        binding.ridesRecyclerView.visibility = View.VISIBLE
        
        // Refresh ride history visibility based on current data
        val hasRides = rideHistoryAdapter.currentList.isNotEmpty()
        binding.noRidesText.visibility = if (hasRides) View.GONE else View.VISIBLE
        binding.ridesRecyclerView.visibility = if (hasRides) View.VISIBLE else View.GONE
        
        // Clear any fragments that were added (not replaced)
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
        }
    }
    
    private fun showCalibrationScreen() {
        binding.startRideButton.visibility = View.GONE
        binding.settingsButton.visibility = View.GONE
        binding.rideHistoryHeader.visibility = View.GONE
        binding.noRidesText.visibility = View.GONE
        
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, CalibrationFragment())
            .commit()
    }
    
    private fun showRideInProgressScreen() {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, RideInProgressFragment())
            .commit()
    }
    
    private fun showRideSummaryScreen() {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, RideSummaryFragment())
            .commit()
    }
    
    private fun showSettingsScreen() {
        binding.startRideButton.visibility = View.GONE
        binding.settingsButton.visibility = View.GONE
        binding.rideHistoryHeader.visibility = View.GONE
        binding.noRidesText.visibility = View.GONE
        
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .addToBackStack("settings")
            .commit()
    }
}

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val leanThresholdSlider = view.findViewById<SeekBar>(R.id.leanThresholdSlider)
        val leanThresholdValue = view.findViewById<TextView>(R.id.leanThresholdValue)
        val speedThresholdSlider = view.findViewById<SeekBar>(R.id.speedThresholdSlider)
        val speedThresholdValue = view.findViewById<TextView>(R.id.speedThresholdValue)
        val saveButton = view.findViewById<Button>(R.id.saveSettingsButton)
        val backButton = view.findViewById<Button>(R.id.backButton)
        
        // Load current settings
        val prefs = requireContext().getSharedPreferences("ride_settings", android.content.Context.MODE_PRIVATE)
        val currentLeanThreshold = prefs.getFloat("lean_threshold_degrees", 12f)
        val currentSpeedThreshold = prefs.getFloat("speed_threshold_kmh", 20f)
        
        leanThresholdSlider.progress = currentLeanThreshold.toInt()
        leanThresholdValue.text = "${currentLeanThreshold.toInt()}°"
        
        speedThresholdSlider.progress = currentSpeedThreshold.toInt()
        speedThresholdValue.text = "${currentSpeedThreshold.toInt()} km/h"
        
        // Update display when sliders change
        leanThresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(8, 20)
                leanThresholdValue.text = "${value}°"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        speedThresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(10, 50)
                speedThresholdValue.text = "${value} km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        saveButton.setOnClickListener {
            val leanValue = leanThresholdSlider.progress.coerceIn(8, 20)
            val speedValue = speedThresholdSlider.progress.coerceIn(10, 50)
            
            prefs.edit()
                .putFloat("lean_threshold_degrees", leanValue.toFloat())
                .putFloat("speed_threshold_kmh", speedValue.toFloat())
                .apply()
        }
        
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
            // Show home screen elements again
            requireActivity().findViewById<View>(R.id.startRideButton).visibility = View.VISIBLE
            requireActivity().findViewById<View>(R.id.settingsButton).visibility = View.VISIBLE
            requireActivity().findViewById<View>(R.id.rideHistoryHeader).visibility = View.VISIBLE
            requireActivity().findViewById<View>(R.id.noRidesText).visibility = View.VISIBLE
        }
    }
}

class CalibrationFragment : Fragment(R.layout.fragment_calibration) {
    
    private val viewModel: RideViewModel by activityViewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val liveLeanAngle = view.findViewById<TextView>(R.id.liveLeanAngle)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = view.findViewById<TextView>(R.id.progressText)
        val calibratedStatus = view.findViewById<TextView>(R.id.calibratedStatus)
        val startRideButton = view.findViewById<Button>(R.id.startRideButton)
        
        viewModel.currentLeanAngleLiveData.observe(viewLifecycleOwner) { angle ->
            // Only show lean angle after calibration is complete
            val isCalibrated = viewModel.isCalibratedLiveData.value ?: false
            if (isCalibrated) {
                val displayAngle = abs(angle)
                liveLeanAngle.text = "${displayAngle.toInt()}°"
                liveLeanAngle.setTextColor(
                    when {
                        angle < -2f -> resources.getColor(R.color.lean_left, null)
                        angle > 2f -> resources.getColor(R.color.lean_right, null)
                        else -> resources.getColor(R.color.text_primary, null)
                    }
                )
            } else {
                liveLeanAngle.text = "--°"
                liveLeanAngle.setTextColor(resources.getColor(R.color.text_primary, null))
            }
        }
        
        viewModel.calibrationProgressLiveData.observe(viewLifecycleOwner) { progress ->
            progressBar.progress = (progress * 100).toInt()
            progressText.text = "${(progress * 100).toInt()}%"
        }
        
        viewModel.isCalibratedLiveData.observe(viewLifecycleOwner) { calibrated ->
            // Auto-start ride when calibration completes (no button needed)
            if (calibrated) {
                viewModel.startRideFromCalibration()
            }
        }
    }
}

class RideInProgressFragment : Fragment(R.layout.fragment_ride_in_progress) {
    
    private val viewModel: RideViewModel by activityViewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val cornerCountValue = view.findViewById<TextView>(R.id.cornerCountValue)
        val maxLeanLeft = view.findViewById<TextView>(R.id.maxLeanLeft)
        val maxLeanRight = view.findViewById<TextView>(R.id.maxLeanRight)
        val maxAccelG = view.findViewById<TextView>(R.id.maxAccelG)
        val maxBrakeG = view.findViewById<TextView>(R.id.maxBrakeG)
        val currentSpeed = view.findViewById<TextView>(R.id.currentSpeed)
        val distance = view.findViewById<TextView>(R.id.distance)
        val endRideButton = view.findViewById<Button>(R.id.endRideButton)
        val recalibrateButton = view.findViewById<TextView>(R.id.recalibrateButton)
        
        viewModel.cornerCountLiveData.observe(viewLifecycleOwner) { count ->
            cornerCountValue.text = count.toString()
        }
        
        viewModel.maxLeanLeftLiveData.observe(viewLifecycleOwner) { lean ->
            maxLeanLeft.text = "${lean.toInt()}°"
        }
        
        viewModel.maxLeanRightLiveData.observe(viewLifecycleOwner) { lean ->
            maxLeanRight.text = "${lean.toInt()}°"
        }
        
        viewModel.maxAccelGLiveData.observe(viewLifecycleOwner) { accel ->
            maxAccelG?.text = String.format("%.2fG", accel)
        }
        
        viewModel.maxBrakeGLiveData.observe(viewLifecycleOwner) { brake ->
            maxBrakeG?.text = String.format("%.2fG", brake)
        }
        
        viewModel.currentSpeedLiveData.observe(viewLifecycleOwner) { speed ->
            currentSpeed.text = "${speed.toInt()} km/h"
        }
        
        viewModel.totalDistanceLiveData.observe(viewLifecycleOwner) { dist ->
            distance.text = String.format("%.1f km", dist)
        }
        
        endRideButton.setOnClickListener {
            viewModel.endRide()
        }

        recalibrateButton.setOnClickListener {
            viewModel.recalibrate()
        }
    }
}

class RideSummaryFragment : Fragment(R.layout.fragment_ride_summary) {
    
    private val viewModel: RideViewModel by activityViewModels()
    private var mapView: org.osmdroid.views.MapView? = null
    private var mapInitialized = false
    private var rideId: java.util.UUID? = null
    private var isHistoricRide = false
    
    companion object {
        private const val ARG_RIDE_ID = "ride_id"
        
        fun newInstance(rideId: java.util.UUID? = null): RideSummaryFragment {
            return RideSummaryFragment().apply {
                arguments = Bundle().apply {
                    rideId?.let { putString(ARG_RIDE_ID, it.toString()) }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_RIDE_ID)?.let {
            rideId = java.util.UUID.fromString(it)
            isHistoricRide = true
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val cornerCountValue = view.findViewById<TextView>(R.id.cornerCountValue)
        val maxLeanLeft = view.findViewById<TextView>(R.id.maxLeanLeft)
        val maxLeanRight = view.findViewById<TextView>(R.id.maxLeanRight)
        val maxAccelG = view.findViewById<TextView>(R.id.maxAccelG)
        val maxBrakeG = view.findViewById<TextView>(R.id.maxBrakeG)
        val distanceValue = view.findViewById<TextView>(R.id.distanceValue)
        val durationValue = view.findViewById<TextView>(R.id.durationValue)
        val avgSpeedValue = view.findViewById<TextView>(R.id.avgSpeedValue)
        val maxSpeedValue = view.findViewById<TextView>(R.id.maxSpeedValue)
        val saveButton = view.findViewById<Button>(R.id.saveButton)
        val discardButton = view.findViewById<Button>(R.id.discardButton)
        val shareButton = view.findViewById<Button>(R.id.shareButton)
        
        // Initialize OSM MapView
        mapView = view.findViewById(R.id.rideMap)
        android.util.Log.d("TwistCounter", "MAP: Found MapView: ${mapView != null}")
        
        mapView?.apply {
            android.util.Log.d("TwistCounter", "MAP: Configuring MapView...")
            
            // Set explicit size to ensure map renders
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            
            // Set default zoom and center
            controller.setZoom(15.0)
            
            // Force tile loading
            setTilesScaledToDpi(true)
            
            // Explicitly set online mode and tile provider
            setUseDataConnection(true)
            
            mapInitialized = true
            android.util.Log.d("TwistCounter", "MAP: MapView configured, initialized=$mapInitialized")
            invalidate()
        }
        
        // Handle historic ride viewing vs current ride summary
        if (isHistoricRide && rideId != null) {
            // Load historic ride data
            viewModel.loadHistoricRide(rideId!!)
            
            // Hide save/discard buttons for historic rides
            saveButton.visibility = View.GONE
            discardButton.visibility = View.GONE
            
            // Observe historic ride data
            viewModel.historicRideSummaryLiveData.observe(viewLifecycleOwner) { summary ->
                summary?.let {
                    cornerCountValue.text = it.ride.cornerCount.toString()
                    maxLeanLeft.text = "${it.ride.maxLeanLeft.toInt()}°"
                    maxLeanRight.text = "${it.ride.maxLeanRight.toInt()}°"
                    distanceValue.text = String.format("%.1f km", it.ride.distanceKm)
                    durationValue.text = formatDuration(it.ride.durationSeconds)
                    avgSpeedValue.text = "${it.ride.avgSpeedKmh.toInt()} km/h"
                    maxSpeedValue.text = "${it.ride.maxSpeedKmh.toInt()} km/h"
                    maxAccelG?.text = String.format("%.2fG", it.ride.maxAccelG)
                    maxBrakeG?.text = String.format("%.2fG", it.ride.maxBrakeG)
                }
            }
            
            viewModel.historicRideTrackLiveData.observe(viewLifecycleOwner) { track ->
                android.util.Log.d("TwistCounter", "MAP: Historic track loaded: ${track?.waypoints?.size ?: 0} waypoints")
                track?.let {
                    if (it.waypoints.isNotEmpty() && mapInitialized) {
                        android.util.Log.d("TwistCounter", "MAP: Drawing historic route...")
                        drawColorCodedRoute(it)
                    } else {
                        android.util.Log.w("TwistCounter", "MAP: Cannot draw - waypoints=${it.waypoints.size}, initialized=$mapInitialized")
                    }
                }
            }
        } else {
            // Current ride - use existing observers
            viewModel.currentRideSummaryLiveData.observe(viewLifecycleOwner) { summary ->
                if (summary != null) {
                    cornerCountValue.text = summary.ride.cornerCount.toString()
                    maxLeanLeft.text = "${summary.ride.maxLeanLeft.toInt()}°"
                    maxLeanRight.text = "${summary.ride.maxLeanRight.toInt()}°"
                    distanceValue.text = String.format("%.1f km", summary.ride.distanceKm)
                    durationValue.text = formatDuration(summary.ride.durationSeconds)
                    avgSpeedValue.text = "${summary.ride.avgSpeedKmh.toInt()} km/h"
                    maxSpeedValue.text = "${summary.ride.maxSpeedKmh.toInt()} km/h"
                }
            }
            
            // Observe track data separately and draw map when ready
            viewModel.rideTrackLiveData.observe(viewLifecycleOwner) { track ->
                if (track.waypoints.isNotEmpty() && mapInitialized) {
                    drawColorCodedRoute(track)
                }
            }
            
            // Observe max accel/brake
            viewModel.maxAccelGLiveData.observe(viewLifecycleOwner) { accel ->
                maxAccelG?.text = String.format("%.2fG", accel)
            }
            
            viewModel.maxBrakeGLiveData.observe(viewLifecycleOwner) { brake ->
                maxBrakeG?.text = String.format("%.2fG", brake)
            }
            
            saveButton.setOnClickListener {
                viewModel.saveRide()
            }
            
            discardButton.setOnClickListener {
                viewModel.discardRide()
            }
        }
        
        shareButton.setOnClickListener {
            shareRide()
        }
    }
    
    private fun drawColorCodedRoute(track: dev.filips.twistcounter.domain.model.RideTrack) {
        if (!mapInitialized || mapView == null) {
            android.util.Log.w("TwistCounter", "Map not initialized, skipping draw")
            return
        }
        
        if (track.waypoints.size < 2) {
            android.util.Log.w("TwistCounter", "Not enough waypoints to draw route: ${track.waypoints.size}")
            return
        }
        
        try {
            mapView?.overlays?.clear()
            
            // Draw route segments with colors based on lean angle
            for (i in 1 until track.waypoints.size) {
                val start = track.waypoints[i - 1]
                val end = track.waypoints[i]
                
                val color = end.getLeanColor()
                
                val polyline = org.osmdroid.views.overlay.Polyline().apply {
                    addPoint(start.toGeoPoint())
                    addPoint(end.toGeoPoint())
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 12f
                }
                
                mapView?.overlays?.add(polyline)
            }
            
            // Fit map to show entire route
            val bounds = track.getBounds()
            if (bounds != null) {
                val boundingBox = org.osmdroid.util.BoundingBox(
                    bounds.second.latitude,   // north
                    bounds.second.longitude,  // east
                    bounds.first.latitude,    // south
                    bounds.first.longitude    // west
                )
                
                // Post to handler to ensure map is laid out before zooming
                mapView?.post {
                    try {
                        // Center on route first
                        val centerLat = (bounds.first.latitude + bounds.second.latitude) / 2
                        val centerLng = (bounds.first.longitude + bounds.second.longitude) / 2
                        mapView?.controller?.setCenter(org.osmdroid.util.GeoPoint(centerLat, centerLng))
                        
                        // Then zoom to bounding box
                        mapView?.zoomToBoundingBox(boundingBox, false, 100)
                        
                        // Force tile loading
                        mapView?.setTilesScaledToDpi(true)
                        mapView?.setUseDataConnection(true)
                        
                        // Invalidate multiple times to force redraw
                        mapView?.invalidate()
                        mapView?.postDelayed({ mapView?.invalidate() }, 500)
                        mapView?.postDelayed({ mapView?.invalidate() }, 1000)
                        
                        android.util.Log.d("TwistCounter", "MAP: Zoomed to bounds, center=($centerLat, $centerLng)")
                    } catch (e: Exception) {
                        android.util.Log.e("TwistCounter", "Error zooming map: ${e.message}")
                    }
                }
            }
            
            android.util.Log.d("TwistCounter", "Drew route with ${track.waypoints.size} waypoints")
        } catch (e: Exception) {
            android.util.Log.e("TwistCounter", "Error drawing route: ${e.message}", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }
    
    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "${mins}:${secs.toString().padStart(2, '0')}"
    }
    
    private fun shareRide() {
        val summary = viewModel.currentRideSummaryLiveData.value
        if (summary == null) return
        
        val ride = summary.ride
        val shareText = """
            🏍️ TwistCounter Ride Summary
            
            Corners: ${ride.cornerCount}
            Max Lean Left: ${ride.maxLeanLeft.toInt()}°
            Max Lean Right: ${ride.maxLeanRight.toInt()}°
            Distance: ${String.format("%.1f", ride.distanceKm)} km
            Duration: ${formatDuration(ride.durationSeconds)}
            Avg Speed: ${ride.avgSpeedKmh.toInt()} km/h
            Max Speed: ${ride.maxSpeedKmh.toInt()} km/h
            
            #TwistCounter
        """.trimIndent()
        
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, shareText)
        startActivity(Intent.createChooser(intent, "Share Ride"))
    }
}
