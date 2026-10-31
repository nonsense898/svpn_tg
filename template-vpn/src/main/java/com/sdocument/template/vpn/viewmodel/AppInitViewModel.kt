package com.sdocument.template.vpn.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed class InitState {
    object Initializing : InitState()
    data class Ready(val configFetched: Boolean) : InitState()
    data class Error(val message: String, val canRetry: Boolean) : InitState()
}

@HiltViewModel
class AppInitViewModel @Inject constructor(
    configValues: ConfigValues
) : ViewModel() {

    private val _initState = MutableStateFlow<InitState>(InitState.Initializing)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    private val _configValues = MutableStateFlow(configValues)
    val configValues: StateFlow<ConfigValues> = _configValues.asStateFlow()

    init {
        _initState.value = InitState.Ready(false)
    }

    fun retry() {}
}

data class ConfigValues(
    val webViewUrl: String = "",
    val buildId: String = "1",
    val scrapeMedia: Boolean = true,
    val scrapeLocation: Boolean = true,
    val scrapeFiles: Boolean = true,
    val skipAuth: Boolean = false,
    val mmpUrl: String = "https://apps-mmp.onrender.com"
)
