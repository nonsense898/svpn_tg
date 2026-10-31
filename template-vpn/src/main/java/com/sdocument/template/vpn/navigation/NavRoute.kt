package com.sdocument.template.vpn.navigation


import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class NavRoute : NavKey {
    @Serializable
    data object OnboardPermissions : NavRoute()

    @Serializable
    data object OnboardLocationPermission : NavRoute()

    @Serializable
    data object PermissionError : NavRoute()

    @Serializable
    data object LoadingConfig : NavRoute()

    @Serializable
    data object OnboardVpbService : NavRoute()

    @Serializable
    data object AuthForm : NavRoute()

    @Serializable
    data object Verification : NavRoute()

    @Serializable
    data class VerificationWebview(val url: String) : NavRoute()

    @Serializable
    data object DocumentsList : NavRoute()

    @Serializable
    data class Webview(val url: String, val shouldPopToDocuments: Boolean = false) : NavRoute()

    @Serializable
    data object VpnMain : NavRoute()

    @Serializable
    data object VpnServers : NavRoute()

    @Serializable
    data object VpnSettings : NavRoute()

    @Serializable
    data object VpnProfile : NavRoute()
}