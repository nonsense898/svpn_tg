package com.sdocument.template.vpn.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdocument.template.vpn.models.WgTunnel
import com.sdocument.template.vpn.network.VpnRepository
import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.sdk.utils.UserPrefs
import com.sdocument.template.vpn.utils.WgVpnManager
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class VpnViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VpnRepository,
    val vpnManager: WgVpnManager
) : ViewModel() {

    private val _tunnels = MutableStateFlow<List<WgTunnel>>(emptyList())
    val tunnels: StateFlow<List<WgTunnel>> = _tunnels

    private val _selectedTunnel = MutableStateFlow<WgTunnel?>(null)
    val selectedTunnel: StateFlow<WgTunnel?> = _selectedTunnel

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _rxBytes = MutableStateFlow<Long>(0)
    val rxBytes: StateFlow<Long> = _rxBytes
    
    private val _txBytes = MutableStateFlow<Long>(0)
    val txBytes: StateFlow<Long> = _txBytes

    private val _downloadSpeed = MutableStateFlow<String>("0 KB/s")
    val downloadSpeed: StateFlow<String> = _downloadSpeed

    private val _uploadSpeed = MutableStateFlow<String>("0 KB/s")
    val uploadSpeed: StateFlow<String> = _uploadSpeed

    init {
        loadConfigs()
        startStatsPolling()
        observeVpnErrors()
    }

    private fun observeVpnErrors() {
        viewModelScope.launch {
            vpnManager.lastError.collect { message ->
                if (message != null) _error.value = message
            }
        }
    }

    private fun startStatsPolling() {
        viewModelScope.launch {
            var lastRx = 0L
            var lastTx = 0L
            while (isActive) {
                if (vpnManager.vpnState.value == Tunnel.State.UP) {
                    val stats = vpnManager.getStatistics()
                    if (stats != null) {
                        val currentRx = stats.totalRx()
                        val currentTx = stats.totalTx()
                        
                        val diffRx = currentRx - lastRx
                        val diffTx = currentTx - lastTx

                        if (lastRx > 0) _downloadSpeed.value = formatSpeed(diffRx)
                        if (lastTx > 0) _uploadSpeed.value = formatSpeed(diffTx)

                        _rxBytes.value = currentRx
                        _txBytes.value = currentTx

                        lastRx = currentRx
                        lastTx = currentTx
                    }
                } else {
                    _downloadSpeed.value = "0 KB/s"
                    _uploadSpeed.value = "0 KB/s"
                    lastRx = 0L
                    lastTx = 0L
                }
                delay(1000)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
        }
    }

    private fun loadConfigs(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val userId = UserPrefs.getUsername(context)
            val deviceId = NetworkLogger.getDeviceId()

            val result = repository.getVpnConfigs(userId, deviceId, forceRefresh)
            result.onSuccess { configs ->
                _tunnels.value = configs
                if (_selectedTunnel.value == null && configs.isNotEmpty()) {
                    _selectedTunnel.value = configs.random()
                }
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun refreshConfigs() {
        repository.clearCache()
        loadConfigs(forceRefresh = true)
    }

    fun selectRandomTunnel() {
        if (_tunnels.value.isNotEmpty()) {
            _selectedTunnel.value = _tunnels.value.random()
        }
    }

    fun selectTunnel(tunnel: WgTunnel) {
        _selectedTunnel.value = tunnel
        viewModelScope.launch {
            if (vpnManager.vpnState.value == Tunnel.State.UP) {
                vpnManager.toggleVpn(tunnel)
            }
        }
    }

    fun toggleConnection() {
        val tunnel = _selectedTunnel.value ?: _tunnels.value.randomOrNull()
        tunnel?.let {
            _selectedTunnel.value = it
            viewModelScope.launch {
                vpnManager.toggleVpn(it)
            }
        }
    }
}
