package dev.filips.twistcounter.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
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
import dev.filips.twistcounter.presentation.viewmodel.RideState
import dev.filips.twistcounter.presentation.viewmodel.RideViewModel
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: RideViewModel by viewModels()
    
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
        
        setupObservers()
        setupClickListeners()
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
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS
        )

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
        binding.noRidesText.visibility = View.VISIBLE
        
        // Clear any fragments
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().remove(it).commit()
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
            val displayAngle = abs(angle)
            liveLeanAngle.text = "${displayAngle.toInt()}°"
            liveLeanAngle.setTextColor(
                when {
                    angle < -2f -> resources.getColor(R.color.lean_left, null)
                    angle > 2f -> resources.getColor(R.color.lean_right, null)
                    else -> resources.getColor(R.color.text_primary, null)
                }
            )
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
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val cornerCountValue = view.findViewById<TextView>(R.id.cornerCountValue)
        val maxLeanLeft = view.findViewById<TextView>(R.id.maxLeanLeft)
        val maxLeanRight = view.findViewById<TextView>(R.id.maxLeanRight)
        val distanceValue = view.findViewById<TextView>(R.id.distanceValue)
        val durationValue = view.findViewById<TextView>(R.id.durationValue)
        val avgSpeedValue = view.findViewById<TextView>(R.id.avgSpeedValue)
        val maxSpeedValue = view.findViewById<TextView>(R.id.maxSpeedValue)
        val saveButton = view.findViewById<Button>(R.id.saveButton)
        val discardButton = view.findViewById<Button>(R.id.discardButton)
        val shareButton = view.findViewById<Button>(R.id.shareButton)
        
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
        
        saveButton.setOnClickListener {
            viewModel.saveRide()
        }
        
        discardButton.setOnClickListener {
            viewModel.discardRide()
        }
        
        shareButton.setOnClickListener {
            shareRide()
        }
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