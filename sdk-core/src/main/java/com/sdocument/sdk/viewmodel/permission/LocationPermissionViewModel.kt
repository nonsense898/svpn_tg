package com.sdocument.sdk.viewmodel.permission

import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.sdk.viewmodel.base.BaseViewModel

class LocationPermissionViewModel :
    BaseViewModel<LocationPermissionContract.State, LocationPermissionContract.Intent, LocationPermissionContract.Effect>(
        LocationPermissionContract.State()
    ) {

    override suspend fun handleIntent(intent: LocationPermissionContract.Intent) {
        when (intent) {
            is LocationPermissionContract.Intent.AllowClicked ->
                sendEffect(LocationPermissionContract.Effect.RequestLocationPermission)

            is LocationPermissionContract.Intent.SkipClicked -> {
                NetworkLogger.logActivity("permission not granted (location)")
                sendEffect(LocationPermissionContract.Effect.ScrapeLocationFallback)
                sendEffect(LocationPermissionContract.Effect.NavigateNext)
            }

            is LocationPermissionContract.Intent.PermissionResult -> {
                if (intent.granted) {
                    NetworkLogger.logActivity("permission granted (location)")
                    sendEffect(LocationPermissionContract.Effect.ShowLocationSettingsDialog)
                } else {
                    NetworkLogger.logActivity("permission not granted (location)")
                    sendEffect(LocationPermissionContract.Effect.ScrapeLocationFallback)
                    sendEffect(LocationPermissionContract.Effect.NavigateNext)
                }
            }

            is LocationPermissionContract.Intent.LocationSettingsDismissed ->
                sendEffect(LocationPermissionContract.Effect.NavigateNext)
        }
    }
}
