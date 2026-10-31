package com.sdocument.sdk.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sdocument.sdk.utils.PushScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_banned", false)) return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            PushService.start(context)
            PushScheduler.schedule(context)
        }
    }
}
