package com.apex.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apex.launcher.adapter.AppDrawerAdapter
import com.apex.launcher.camera.HardwareCameraManager
import com.apex.launcher.model.AppInfo
import com.apex.launcher.telemetry.TelemetryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : AppCompatActivity() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvBatteryVolt: TextView
    private lateinit var tvRamUsage: TextView
    private lateinit var tvLuxReading: TextView
    private lateinit var tvAutoParams: TextView
    private lateinit var btnLaunchCamera: Button
    private lateinit var etSearchApps: EditText
    private lateinit var rvApps: RecyclerView

    private lateinit var telemetryEngine: TelemetryEngine
    private lateinit var cameraManager: HardwareCameraManager
    private lateinit var appAdapter: AppDrawerAdapter

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateClock()
            timeHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        initViews()
        setupBackPress()
        setupAdapter()

        telemetryEngine = TelemetryEngine(this)
        cameraManager = HardwareCameraManager(this)

        observeTelemetry()
        observeCameraCalibration()
        loadInstalledApplications()
        setupSearch()
    }

    private fun initViews() {
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvBatteryVolt = findViewById(R.id.tvBatteryVolt)
        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvLuxReading = findViewById(R.id.tvLuxReading)
        tvAutoParams = findViewById(R.id.tvAutoParams)
        btnLaunchCamera = findViewById(R.id.btnLaunchCamera)
        etSearchApps = findViewById(R.id.etSearchApps)
        rvApps = findViewById(R.id.rvApps)

        btnLaunchCamera.setOnClickListener {
            cameraManager.launchHardwareCamera()
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Stay on Home screen
                etSearchApps.setText("")
                rvApps.scrollToPosition(0)
            }
        })
    }

    private fun setupAdapter() {
        appAdapter = AppDrawerAdapter { appInfo ->
            try {
                startActivity(appInfo.launchIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Uygulama başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        rvApps.layoutManager = GridLayoutManager(this, 4)
        rvApps.adapter = appAdapter
    }

    private fun setupSearch() {
        etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateClock() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
        tvClock.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now).uppercase(Locale("tr", "TR"))
    }

    private fun observeTelemetry() {
        lifecycleScope.launch {
            telemetryEngine.telemetry.collect { data ->
                val voltStr = String.format(Locale.US, "%.2fV", data.voltageVolts)
                val tempStr = String.format(Locale.US, "%.1f°C", data.tempCelsius)
                tvBatteryVolt.text = "$voltStr • $tempStr"

                val ramFree = String.format(Locale.US, "%.1f", data.ramFreeGB)
                val ramTotal = String.format(Locale.US, "%.1f", data.ramTotalGB)
                tvRamUsage.text = "RAM: ${ramFree}GB / ${ramTotal}GB"
            }
        }
    }

    private fun observeCameraCalibration() {
        lifecycleScope.launch {
            cameraManager.calibration.collect { cal ->
                tvLuxReading.text = "Ortam Işığı: ${cal.lux.toInt()} lx"
                tvAutoParams.text = cal.profileLabel
            }
        }
    }

    private fun loadInstalledApplications() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val apps = resolveInfos.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null // hide self

                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                AppInfo(label, pkg, icon, launchIntent)
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }

            withContext(Dispatchers.Main) {
                appAdapter.submitList(apps)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        timeHandler.post(timeRunnable)
        telemetryEngine.start()
        cameraManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacks(timeRunnable)
        telemetryEngine.stop()
        cameraManager.stopListening()
    }
}
