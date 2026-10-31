package com.sdocument.sdk

import com.sdocument.sdk.di.ConfigProvider

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sdocument.sdk.navigation.AppNavHost

@Composable
fun SdkOnboardingFlow(
    modifier: Modifier = Modifier,
    config: SdkConfig,
    onFlowCompleted: () -> Unit
) {
    ConfigProvider.currentConfig = config
    AppNavHost(
        modifier = modifier,
        config = config,
        onFlowCompleted = onFlowCompleted
    )
}
