package com.sdocument.sdk.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import com.sdocument.sdk.utils.NetworkLogger

object MMPTracker {
    private const val TAG = "MMPTracker"
    
    suspend fun trackFirstOpen(context: Context) {
        val prefs = context.getSharedPreferences("mmp_prefs", Context.MODE_PRIVATE)
        val scraperPrefs = context.getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        val enableMmp = scraperPrefs.getBoolean("enable_mmp", true)
        if (!enableMmp) {
            Log.d(TAG, "MMP tracking is disabled in config. Skipping.")
            return
        }
        val baseUrl = scraperPrefs.getString("mmp_url", "https://apps-mmp.onrender.com") ?: "https://apps-mmp.onrender.com"
        
        Log.d(TAG, "trackFirstOpen called. Base MMP URL: $baseUrl")
        if (prefs.getBoolean("has_tracked_install", false)) {
            Log.d(TAG, "Install already tracked previously. Skipping.")
            return
        }

        try {
            Log.d(TAG, "Starting install tracking flow...")
            val installId = sendInstallEvent(context, baseUrl)
            if (installId != null) {
                Log.d(TAG, "Successfully received install_id: $installId. Fetching attribution...")
                val attribution = fetchAttribution(installId, baseUrl)
                if (attribution != null) {
                    prefs.edit().apply {
                        putBoolean("has_tracked_install", true)
                        putString("attribution_data", attribution.toString())
                        apply()
                    }
                    Log.d(TAG, "Attribution data fetched and saved: $attribution")
                    val subid = attribution.optString("subid", "")
                    if (subid.isNotEmpty() && subid != "null") {
                        Log.d(TAG, "Found subid: $subid. Sending to NetworkLogger.")
                        NetworkLogger.logAttribution(subid)
                    } else {
                        Log.d(TAG, "No valid subid found in attribution (organic or not found).")
                    }
                } else {
                    Log.e(TAG, "Failed to fetch attribution (null response).")
                }
            } else {
                Log.e(TAG, "Failed to get install_id from /install.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tracking error during trackFirstOpen", e)
        }
    }

    @SuppressLint("HardwareIds")
    private suspend fun sendInstallEvent(context: Context, baseUrl: String): String? = withContext(Dispatchers.IO) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val payload = JSONObject().apply {
            put("android_id", androidId)
            put("device_model", Build.MODEL)
            put("brand", Build.BRAND)
            put("os_version", Build.VERSION.RELEASE)
            put("language", Locale.getDefault().language)
            put("timezone", TimeZone.getDefault().id)
        }

        val urlStr = "$baseUrl/install"
        Log.d(TAG, "POST $urlStr | Payload: $payload")
        
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true

        try {
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode
            Log.d(TAG, "POST /install Response Code: $responseCode")

            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "POST /install Response Body: $response")
                return@withContext JSONObject(response).optString("install_id")
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "POST /install Error Body: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during POST /install", e)
        }
        return@withContext null
    }

    private suspend fun fetchAttribution(installId: String, baseUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        val urlStr = "$baseUrl/attribution/$installId"
        Log.d(TAG, "GET $urlStr")
        
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")

        try {
            val responseCode = conn.responseCode
            Log.d(TAG, "GET /attribution Response Code: $responseCode")
            
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "GET /attribution Response Body: $response")
                return@withContext JSONObject(response)
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "GET /attribution Error Body: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during GET /attribution", e)
        }
        return@withContext null
    }
}
