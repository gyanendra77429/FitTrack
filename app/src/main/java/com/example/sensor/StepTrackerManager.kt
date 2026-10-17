package com.example.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StepTrackerState(
    val isSensorAvailable: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isTracking: Boolean = false,
    val sensorTypeDetected: String = "None",
    val todaySteps: Long = 0L,
    val currentSessionSteps: Long = 0L,
    val lastUpdateTimestamp: Long = 0L,
    val statusMessage: String = "Initializing..."
)

class StepTrackerManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounterSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _state = MutableStateFlow(StepTrackerState())
    val state: StateFlow<StepTrackerState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("fittrack_sensor_prefs", Context.MODE_PRIVATE)

    private var initialBootOffset = -1L
    private var lastRecordedBootSteps = -1L
    private var currentDate = getTodayDate()

    init {
        checkSensorStatus()
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun checkPermissionGranted(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _state.update { it.copy(isPermissionGranted = granted) }
        return granted
    }

    fun checkSensorStatus() {
        val hasSensor = stepCounterSensor != null || stepDetectorSensor != null
        val sensorType = when {
            stepCounterSensor != null -> "Hardware Step Counter (TYPE_STEP_COUNTER)"
            stepDetectorSensor != null -> "Hardware Step Detector (TYPE_STEP_DETECTOR)"
            else -> "Not Detected (Emulator / Device without Sensor)"
        }
        val permissionOk = checkPermissionGranted()

        _state.update {
            it.copy(
                isSensorAvailable = hasSensor,
                sensorTypeDetected = sensorType,
                isPermissionGranted = permissionOk,
                statusMessage = if (hasSensor) {
                    if (permissionOk) "Hardware Sensor Ready" else "Requires Activity Recognition Permission"
                } else {
                    "Hardware Sensor Absent — Emulator / Manual Simulation Mode Active"
                }
            )
        }
    }

    fun startTracking(onNewStepsListener: ((deltaSteps: Long) -> Unit)? = null) {
        checkPermissionGranted()
        if (sensorManager == null) return

        val today = getTodayDate()
        if (today != currentDate) {
            // Midnight rollover: reset daily offset
            currentDate = today
            initialBootOffset = -1L
            prefs.edit().putString("last_date", today).apply()
        }

        var registered = false
        if (stepCounterSensor != null) {
            registered = sensorManager.registerListener(
                this,
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        } else if (stepDetectorSensor != null) {
            registered = sensorManager.registerListener(
                this,
                stepDetectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        _state.update {
            it.copy(
                isTracking = registered,
                statusMessage = if (registered) "Active Sensor Tracking" else "Standby (Tap +Steps to test)"
            )
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        _state.update { it.copy(isTracking = false) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalStepsSinceBoot = event.values[0].toLong()

                // Check for midnight rollover or device reboot
                val today = getTodayDate()
                val savedDate = prefs.getString("saved_date", "")
                val savedOffset = prefs.getLong("boot_offset", -1L)

                if (today != savedDate || savedOffset == -1L || totalStepsSinceBoot < savedOffset) {
                    // New day or device was rebooted (since total steps since boot reset to 0)
                    initialBootOffset = totalStepsSinceBoot
                    prefs.edit()
                        .putString("saved_date", today)
                        .putLong("boot_offset", initialBootOffset)
                        .apply()
                } else {
                    initialBootOffset = savedOffset
                }

                val calculatedTodaySteps = (totalStepsSinceBoot - initialBootOffset).coerceAtLeast(0L)
                val delta = if (lastRecordedBootSteps > 0) {
                    (totalStepsSinceBoot - lastRecordedBootSteps).coerceAtLeast(0L)
                } else {
                    0L
                }
                lastRecordedBootSteps = totalStepsSinceBoot

                _state.update {
                    it.copy(
                        todaySteps = calculatedTodaySteps,
                        currentSessionSteps = it.currentSessionSteps + delta,
                        lastUpdateTimestamp = System.currentTimeMillis()
                    )
                }
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                // Event fired for each discrete step
                val stepEvent = event.values[0]
                if (stepEvent >= 1.0f) {
                    _state.update {
                        it.copy(
                            todaySteps = it.todaySteps + 1L,
                            currentSessionSteps = it.currentSessionSteps + 1L,
                            lastUpdateTimestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for standard step sensors
    }

    /**
     * Interactive step simulation helper for testing on emulator
     * or manual walk logging.
     */
    fun simulateSteps(delta: Long) {
        _state.update {
            it.copy(
                todaySteps = it.todaySteps + delta,
                currentSessionSteps = it.currentSessionSteps + delta,
                lastUpdateTimestamp = System.currentTimeMillis(),
                statusMessage = "Simulated Step Pulse: +$delta steps"
            )
        }
    }

    fun resetSessionSteps() {
        _state.update { it.copy(currentSessionSteps = 0L) }
    }
}
