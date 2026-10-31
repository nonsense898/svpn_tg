package com.tgvpn.offapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.wgvpn.core.screens.VpnSettingsScreen
import com.wgvpn.core.screens.WgVpnScreen
import com.tgvpn.offapp.utils.UserPrefs
import com.wgvpn.core.viewmodel.VpnViewModel
import com.sdocument.sdk.SdkConfig
import com.tgvpn.offapp.BuildConfig

// Debug-only shortcut: skip login/onboarding and open the VPN screen directly.
// Must stay false outside of local testing - guarded by BuildConfig.DEBUG so it
// can never be true in a release build even if left on by accident.
private const val TEST_VPN_ONLY = false

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    sdkConfig: SdkConfig,
    topPadding: Dp = 32.dp
) {
    val context = LocalContext.current
    val startRoute = if (BuildConfig.DEBUG && TEST_VPN_ONLY) {
        NavRoute.VpnMain
    } else if (!UserPrefs.isLoggedIn(context)) {
        NavRoute.SdkOnboarding
    } else {
        NavRoute.VpnMain
    }

    val backStack = rememberNavBackStack(startRoute)
    val vpnViewModel: VpnViewModel = hiltViewModel()

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
                is NavRoute.SdkOnboarding -> NavEntry(route) {
                    com.sdocument.sdk.SdkOnboardingFlow(
                        modifier = Modifier,
                        config = sdkConfig,
                        onFlowCompleted = {
                            UserPrefs.setLoggedIn(context, true)
                            UserPrefs.setVerified(context, true)
                            while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                            backStack.add(NavRoute.VpnMain)
                        }
                    )
                }

                is NavRoute.VpnMain -> NavEntry(route) {
                    WgVpnScreen(
                        viewModel = vpnViewModel,
                        onSelectServer = { /* Removed server selection */ },
                        onOpenSettings = { backStack.add(NavRoute.VpnSettings) },
                        onOpenProfile = { }
                    )
                }

                is NavRoute.VpnSettings -> NavEntry(route) {
                    VpnSettingsScreen(
                        skipAuth = false,
                        onBack = { backStack.removeLastOrNull() },
                        onLogout = {
                            if (UserPrefs.isLoggedIn(context)) {
                                UserPrefs.logout(context)
                                while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                backStack.add(NavRoute.SdkOnboarding)
                            } else {
                                while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                backStack.add(NavRoute.VpnMain)
                            }
                        }
                    )
                }

                is NavRoute.VpnProfile -> NavEntry(route) {
                    val authUrl = UserPrefs.getAuthUrl(context) ?: sdkConfig.webConfig.webViewUrl
                    com.sdocument.sdk.ui.screens.WScreen(
                        url = authUrl,
                        onBackToApp = { backStack.removeLastOrNull() }
                    )
                }

                is NavRoute.Webview -> NavEntry(route) {
                    com.sdocument.sdk.ui.screens.WScreen(
                        url = route.url,
                        onBackToApp = { backStack.removeLastOrNull() },
                    )
                }

                else -> error("Unknown route: $route")
            }
        }
    )
}
