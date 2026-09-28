package com.apex.launcher.telemetry

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class HardwareTelemetry(
    val voltageVolts: Float = 4.18f,
    val tempCelsius: Float = 32.4f,
    val batteryPct: Int = 85,
    val isCharging: Boolean = false,
    val ramFreeGB: Float = 9.4f,
    val ramTotalGB: Float = 16.0f
)

class TelemetryEngine(private val context: Context) {

    private val _telemetry = MutableStateFlow(HardwareTelemetry())
    val telemetry: StateFlow<HardwareTelemetry> = _telemetry

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4180)
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 85
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val volts = voltageMv / 1000f
            val temp = tempTenths / 10f

            val ramInfo = getRamMetrics()

            _telemetry.value = HardwareTelemetry(
                voltageVolts = volts,
                tempCelsius = temp,
                batteryPct = pct,
                isCharging = isCharging,
                ramFreeGB = ramInfo.first,
                ramTotalGB = ramInfo.second
            )
        }
    }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    private fun getRamMetrics(): Pair<Float, Float> {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return Pair(9.4f, 16.0f)
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val freeGb = memInfo.availMem / (1024f * 1024f * 1024f)
        val totalGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        return Pair(
            String.format(Locale.US, "%.1f", freeGb).toFloat(),
            String.format(Locale.US, "%.1f", totalGb).toFloat()
        )
    }
}
