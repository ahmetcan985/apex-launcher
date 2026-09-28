package com.apex.launcher.camera

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CameraCalibrationData(
    val lux: Float = 340f,
    val iso: Int = 100,
    val shutterSpeed: String = "1/120s",
    val exposureCompensation: Int = 0,
    val profileLabel: String = "Auto ISO 100 • 1/120s • EV +0.0"
)

class HardwareCameraManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _calibration = MutableStateFlow(CameraCalibrationData())
    val calibration: StateFlow<CameraCalibrationData> = _calibration

    fun startListening() {
        lightSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            val data = calculateOptimalParameters(lux)
            _calibration.value = data
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Hardware calibration mathematical model based on ambient luminous flux (lux)
     */
    private fun calculateOptimalParameters(lux: Float): CameraCalibrationData {
        val (iso, shutter, ev) = when {
            lux < 15f -> Triple(3200, "1/15s", 2)
            lux < 60f -> Triple(1600, "1/30s", 1)
            lux < 250f -> Triple(400, "1/60s", 0)
            lux < 1000f -> Triple(100, "1/120s", 0)
            lux < 5000f -> Triple(50, "1/500s", -1)
            else -> Triple(50, "1/1000s", -2) // extreme sunlight highlight preservation
        }

        val evFormatted = if (ev >= 0) "+$ev.0" else "$ev.0"
        val label = "Auto ISO $iso • $shutter • EV $evFormatted"

        return CameraCalibrationData(
            lux = lux,
            iso = iso,
            shutterSpeed = shutter,
            exposureCompensation = ev,
            profileLabel = label
        )
    }

    /**
     * Launches the native camera application
     */
    fun launchHardwareCamera() {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }
}
