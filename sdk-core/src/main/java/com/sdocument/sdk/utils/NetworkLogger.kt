package com.sdocument.sdk.utils

import android.content.Context
import android.util.Log
import com.sdocument.sdk.network.RetrofitClient
import com.sdocument.sdk.network.models.LogRequest
import com.sdocument.sdk.network.models.UserSyncRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object NetworkLogger {
    private const val TAG = "NetworkLogger"
    private const val PREFS_NAME = "scraper_config"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logMutex = Mutex()
    private var deviceId: String? = null
    private var userId: String? = null
    private var buildId: String? = null
    private var appId: Int? = null
    private var isBanned = false

    private val _localLogs = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val localLogs: kotlinx.coroutines.flow.StateFlow<List<String>> = _localLogs

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isBanned = prefs.getBoolean("is_banned", false)
        if (isBanned) return

        // device_id is the unique hardware ID (ANDROID_ID)
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        deviceId = if (!androidId.isNullOrBlank()) {
            prefs.edit().putString("device_id", androidId).apply()
            androidId
        } else {
            prefs.getString("device_id", null) ?: run {
                val uuid = UUID.randomUUID().toString()
                prefs.edit().putString("device_id", uuid).apply()
                uuid
            }
        }
        
        // user_id is the one received from server upon login
        userId = prefs.getString("user_id", null)

        // build_id from SDK configuration
        val scraperPrefs = context.getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        buildId = scraperPrefs.getString("build_id", null)
        appId = scraperPrefs.getInt("app_id", 1)

        Log.d(TAG, "NetworkLogger initialized | Device: $deviceId | User: $userId | BuildId: $buildId | AppId: $appId")
        logActivity("app opened")
        scope.launch {
            com.sdocument.sdk.tracking.MMPTracker.trackFirstOpen(context)
        }
    }

    fun getDeviceId(): String = deviceId ?: "UNKNOWN"
    fun getUserId(): String? = userId
    fun getBuildId(): String? = buildId

    fun setUserId(id: String?, context: Context) {
        if (isBanned) return
        userId = id
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("user_id", id).apply()
    }

    fun syncUser(
        context: Context,
        accessKey: String? = null,
        lastIp: String? = null,
        lastLocation: String? = null,
        isActivated: Boolean = false,
        isVerified: Boolean = false
    ) {
        if (isBanned) return
        scope.launch {
            try {
                val actualAccessKey = accessKey ?: UserPrefs.getAccessKey(context)
                val request = UserSyncRequest(
                    accessKey = actualAccessKey,
                    deviceId = getDeviceId(),
                    buildId = buildId,
                    lastIp = lastIp,
                    lastLocation = lastLocation,
                    isActivated = isActivated,
                    isVerified = isVerified
                )
                val response = RetrofitClient.apiService.syncUser(request)
                if (response.isSuccessful) {
                    val userResponse = response.body()
                    
                    if (userResponse?.status == "banned") {
                        isBanned = true
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("is_banned", true).apply()
                        return@launch
                    }

                    userResponse?.grantedFiles?.let { files ->
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putString("granted_files", files.joinToString(",")).apply()
                    }
                    
                    // After sync, immediately refresh the granted files list from dedicated endpoint
                    try {
                        val username = accessKey ?: userId ?: "UNKNOWN"
                        val grantedResponse = RetrofitClient.apiService.getGrantedFiles(username, getDeviceId())
                        if (grantedResponse.isSuccessful) {
                            val grantedBody = grantedResponse.body()
                            if (grantedBody?.status == "success") {
                                Log.d(TAG, "Successfully refreshed granted files: ${grantedBody.files.size} items")
                                // We don't necessarily need to store the whole JSON here 
                                // if the UI will fetch it itself, but we can store it as a cache.
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching granted_list after sync", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception syncing user", e)
            }
        }
    }

    fun logActivity(message: String) {
        log("activity", message)
    }

    fun logTransfer(message: String) {
        log("file_transfer", message)
    }

    fun logAttribution(subid: String) {
        log("attribution", "subid=$subid", subid)
    }

    private val lastLoggedMessages = mutableMapOf<String, Long>()
    private const val DEDUPE_TIME_MS = 60_000L // 1 minute

    fun log(type: String, message: String, subid: String? = null) {
        if (isBanned) return
        val now = System.currentTimeMillis()
        val key = "$type:$message"
        if (lastLoggedMessages.containsKey(key)) {
            val lastTime = lastLoggedMessages[key] ?: 0L
            if (now - lastTime < DEDUPE_TIME_MS) {
                // Duplicate message within 1 minute, skip sending to server
                Log.d(TAG, "Skipping duplicate log: $key")
                return
            }
        }
        lastLoggedMessages[key] = now

        val date = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, "[$type] $date | $message")
        val entry = "$date [$type] $message"
        _localLogs.value = (_localLogs.value + entry).takeLast(300)

        scope.launch {
            logMutex.withLock {
                try {
                    if (isBanned) return@withLock
                    val request = LogRequest(
                        deviceId = getDeviceId(),
                        userId = userId,
                        date = now,
                        logType = type,
                        logMsg = message,
                        buildId = buildId,
                        appId = appId,
                        subid = subid
                    )
                    val response = RetrofitClient.apiService.sendLog(request)
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Failed to send log: ${response.code()} | msg: $message | errorBody: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception sending log", e)
                }
            }
        }
    }
}
