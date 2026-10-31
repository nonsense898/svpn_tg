package com.tgvpn.offapp.navigation


import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class NavRoute : NavKey {
    @Serializable
    data object SdkOnboarding : NavRoute()

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