package com.sdocument.sdk.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class NavRoute : NavKey {
    @Serializable
    data object Empty : NavRoute()
    @Serializable
    data object OnboardPermissions : NavRoute()

    @Serializable
    data object OnboardLocationPermission : NavRoute()

    @Serializable
    data object PermissionError : NavRoute()

    @Serializable
    data object LoadingConfig : NavRoute()
}