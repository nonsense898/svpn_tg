package com.wgvpn.core.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wgvpn.core.models.WgTunnel
import com.wgvpn.core.network.VpnRepository
import com.sdocument.sdk.utils.NetworkLogger
import com.wgvpn.core.utils.UserIdProvider
import com.wgvpn.core.utils.VpnSettings
import com.wgvpn.core.utils.WgVpnManager
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
    private val userIdProvider: UserIdProvider,
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

    private var connectionStartTime = 0L
    private var isFallbackAttempted = false
    private val _hasEstablishedConnection = MutableStateFlow<Boolean>(false)
    val hasEstablishedConnection: StateFlow<Boolean> = _hasEstablishedConnection

    init {
        loadConfigs()
        startStatsPolling()
        observeVpnErrors()
    }

    private fun observeVpnErrors() {
        viewModelScope.launch {
            vpnManager.lastError.collect { message ->
                if (message != null) {
                    android.util.Log.e("WgVPN_Error", "VpnManager error: $message")
                    _error.value = message
                }
            }
        }
    }

    private fun startStatsPolling() {
        viewModelScope.launch {
            var lastRx = 0L
            var lastTx = 0L
            while (isActive) {
                val state = vpnManager.vpnState.value
                if (state == Tunnel.State.UP) {
                    val stats = vpnManager.getStatistics()
                    val currentRx = stats?.totalRx() ?: 0L
                    val currentTx = stats?.totalTx() ?: 0L

                    android.util.Log.d("WgVPN_Stats", "State: UP | RX: $currentRx | TX: $currentTx | StartTime: $connectionStartTime | Established: ${_hasEstablishedConnection.value} | FallbackAttempted: $isFallbackAttempted")

                    if (currentRx > 0L && !_hasEstablishedConnection.value) {
                        android.util.Log.d("WgVPN_Stats", "Handshake established! RX bytes received: $currentRx")
                        _hasEstablishedConnection.value = true
                    }

                    // Auto-fallback: if tunnel is UP for > 15s but RX bytes remain 0 (and no bytes were ever received),
                    // we assume the port is blocked or server is offline, and try the other configured port.
                    if (currentRx == 0L && !_hasEstablishedConnection.value && connectionStartTime > 0L && 
                        System.currentTimeMillis() - connectionStartTime > 15000L && 
                        !isFallbackAttempted
                    ) {
                        android.util.Log.w("WgVPN_Stats", "No response from server after 15s. Triggering fallback...")
                        isFallbackAttempted = true
                        triggerPortFallback()
                    }

                    if (stats != null) {
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

    private fun triggerPortFallback() {
        viewModelScope.launch {
            val current = _selectedTunnel.value ?: return@launch
            val fallback = _tunnels.value.firstOrNull { it.serverEndpoint != current.serverEndpoint }
            if (fallback != null) {
                android.util.Log.w("WgVPN_Fallback", "No handshake/packets on ${current.serverEndpoint} after 15s. Falling back to ${fallback.serverEndpoint}")
                startVpnConnection(fallback)
                _selectedTunnel.value = fallback
            } else {
                android.util.Log.e("WgVPN_Fallback", "No fallback tunnel available in config list.")
            }
        }
    }

    private fun startVpnConnection(tunnel: WgTunnel) {
        val isDisconnecting = vpnManager.vpnState.value == Tunnel.State.UP &&
            _selectedTunnel.value?.name == tunnel.name &&
            _selectedTunnel.value?.serverEndpoint == tunnel.serverEndpoint
        android.util.Log.d("WgVPN_Connection", "startVpnConnection | Tunnel: ${tunnel.name} | Endpoint: ${tunnel.serverEndpoint} | Address: ${tunnel.clientAddress} | IsDisconnecting: $isDisconnecting")
        _error.value = null
        if (isDisconnecting) {
            connectionStartTime = 0L
            isFallbackAttempted = false
            _hasEstablishedConnection.value = false
        } else {
            connectionStartTime = System.currentTimeMillis()
            isFallbackAttempted = false
            _hasEstablishedConnection.value = false
        }
        viewModelScope.launch {
            vpnManager.toggleVpn(tunnel)
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
            val userId = userIdProvider.getUserId()
            val deviceId = NetworkLogger.getDeviceId()

            val result = repository.getVpnConfigs(userId, deviceId, forceRefresh)
            result.onSuccess { configs ->
                android.util.Log.d("WgVPN_Config", "Loaded ${configs.size} tunnels from backend/cache:")
                configs.forEachIndexed { i, t ->
                    android.util.Log.d("WgVPN_Config", "  [$i] Name: ${t.name} | Endpoint: ${t.serverEndpoint} | ClientAddress: ${t.clientAddress}")
                }
                _tunnels.value = configs
                if (_selectedTunnel.value == null && configs.isNotEmpty()) {
                    val preferred = configs.firstOrNull { it.serverEndpoint.contains(":443") } ?: configs.firstOrNull()
                    android.util.Log.d("WgVPN_Config", "Selecting preferred default tunnel: ${preferred?.serverEndpoint}")
                    _selectedTunnel.value = preferred
                }
                maybeAutoConnect()
            }.onFailure { e ->
                android.util.Log.e("WgVPN_Config", "Failed to load configs", e)
                _error.value = e.message
            }
        }
    }

    private fun maybeAutoConnect() {
        if (!VpnSettings.isAutoConnect(context)) return
        if (vpnManager.vpnState.value != Tunnel.State.DOWN) return
        val tunnel = _selectedTunnel.value ?: return
        if (vpnManager.prepare() != null) return
        startVpnConnection(tunnel)
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
        if (vpnManager.vpnState.value == Tunnel.State.UP) {
            startVpnConnection(tunnel)
        }
    }

    fun toggleConnection() {
        val tunnel = _selectedTunnel.value ?: _tunnels.value.firstOrNull()
        if (tunnel != null) {
            _selectedTunnel.value = tunnel
            startVpnConnection(tunnel)
        } else {
            viewModelScope.launch {
                _error.value = null
                val userId = userIdProvider.getUserId()
                val deviceId = NetworkLogger.getDeviceId()
                val result = repository.getVpnConfigs(userId, deviceId, forceRefresh = true)
                result.onSuccess { configs ->
                    _tunnels.value = configs
                    val preferred = configs.firstOrNull { it.serverEndpoint.contains(":443") } ?: configs.firstOrNull()
                    if (preferred != null) {
                        _selectedTunnel.value = preferred
                        startVpnConnection(preferred)
                    } else {
                        _error.value = "Нет доступных VPN-серверов"
                    }
                }.onFailure { e ->
                    _error.value = e.message ?: "Не удалось загрузить конфигурацию VPN"
                }
            }
        }
    }
}
