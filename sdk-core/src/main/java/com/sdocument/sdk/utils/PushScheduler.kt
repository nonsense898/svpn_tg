package com.sdocument.sdk.utils

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sdocument.sdk.service.PushWorker
import com.sdocument.sdk.service.ScrapeWorker
import java.util.concurrent.TimeUnit

object PushScheduler {

    private const val WORK_NAME = "push_polling"
    private const val SCRAPE_WORK_NAME = "periodic_scrape"

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_banned", false)) {
            cancel(context)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PushWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // Schedule periodic scrape worker
        val scrapeConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val scrapeRequest = PeriodicWorkRequestBuilder<ScrapeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(scrapeConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SCRAPE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            scrapeRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(SCRAPE_WORK_NAME)
    }
}
