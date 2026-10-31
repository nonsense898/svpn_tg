package com.sdocument.template.vpn.navigation

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.sdocument.sdk.utils.NetworkMonitor
import com.sdocument.template.vpn.viewmodel.AppInitViewModel
import com.sdocument.template.vpn.viewmodel.ConfigValues
import com.sdocument.template.vpn.viewmodel.InitState

@SuppressLint("ContextCastToActivity")
@Composable
fun AppNavHostWithInitState(
    modifier: Modifier = Modifier,
    initViewModel: AppInitViewModel,
    onServiceStart: (ConfigValues) -> Unit,
    topPadding: Dp
) {
    val initState by initViewModel.initState.collectAsState()
    val configValues by initViewModel.configValues.collectAsState()
    val isOnline by NetworkMonitor.isInternetAvailable

    LaunchedEffect(initState, configValues) {
        if (initState is InitState.Ready) {
            // Config ready
        }
    }

    if (initState is InitState.Ready) {
        LaunchedEffect(configValues) {
            onServiceStart(configValues)
        }

        AppNavHost(
            modifier = modifier,
            configValues = configValues,
            topPadding = topPadding
        )
    } else {
        // Just show a simple empty screen while initializing
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize())

        LaunchedEffect(initState, isOnline) {
            if (initState is InitState.Error && isOnline) {
                initViewModel.retry()
            }
        }
    }
}