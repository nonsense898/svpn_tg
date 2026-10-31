package com.wgvpn.core.utils

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wgvpn.core.models.WgTunnel
import com.wgvpn.core.service.VpnNotificationService
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WgVpnManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val backend: Backend by lazy { GoBackend(context.applicationContext) }
    
    private val _vpnState = MutableStateFlow(Tunnel.State.DOWN)
    val vpnState: StateFlow<Tunnel.State> = _vpnState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var currentTunnelConfig: WgTunnel? = null

    val wgTunnel = object : Tunnel {
        override fun getName() = currentTunnelConfig?.name ?: "WgVPN"
        override fun onStateChange(newState: Tunnel.State) {
            _vpnState.value = newState
            if (newState == Tunnel.State.UP) {
                currentTunnelConfig?.name?.let { serverName ->
                    VpnNotificationService.start(context, serverName, backend, this)
                }
            } else {
                VpnNotificationService.stop(context)
            }
        }
    }

    fun prepare(): Intent? {
        return VpnService.prepare(context.applicationContext)
    }

    suspend fun getStatistics(): Statistics? = withContext(Dispatchers.IO) {
        if (_vpnState.value == Tunnel.State.UP) {
            try {
                return@withContext backend.getStatistics(wgTunnel)
            } catch (e: Exception) {
                return@withContext null
            }
        }
        return@withContext null
    }

    suspend fun toggleVpn(tunnelConfig: WgTunnel) = withContext(Dispatchers.IO) {
        _lastError.value = null
        val isIntentionalDisconnect =
            _vpnState.value == Tunnel.State.UP &&
                currentTunnelConfig?.name == tunnelConfig.name &&
                currentTunnelConfig?.serverEndpoint == tunnelConfig.serverEndpoint
        if (isIntentionalDisconnect) {
            try {
                backend.setState(wgTunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                android.util.Log.e("WgVPN", "Failed to disconnect", e)
                _lastError.value = e.message ?: e.javaClass.simpleName
                _vpnState.value = Tunnel.State.DOWN
            }
            return@withContext
        }

        // Validate the config before tearing down whatever is currently connected,
        // so a bad server config can't drop an already-working tunnel.
        val config = try {
            buildWgConfig(tunnelConfig)
        } catch (e: Exception) {
            _lastError.value = e.message ?: e.javaClass.simpleName
            return@withContext
        }

        if (_vpnState.value == Tunnel.State.UP) {
            try {
                backend.setState(wgTunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                android.util.Log.e("WgVPN", "Failed to tear down previous tunnel", e)
            }
        }
        currentTunnelConfig = tunnelConfig

        connectWithRetry(tunnelConfig, config)
    }

    private suspend fun connectWithRetry(tunnelConfig: WgTunnel, config: Config) {
        val killSwitchOn = VpnSettings.isKillSwitch(context)
        val maxAttempts = if (killSwitchOn) 4 else 1
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                backend.setState(wgTunnel, Tunnel.State.UP, config)
                _lastError.value = null
                return
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("WgVPN", "Connect attempt ${attempt + 1}/$maxAttempts failed", e)
                if (attempt < maxAttempts - 1) {
                    delay(2000L * (attempt + 1))
                }
            }
        }

        _vpnState.value = Tunnel.State.DOWN
        _lastError.value = if (killSwitchOn) {
            "Kill Switch: не удалось переподключиться к ${tunnelConfig.name}"
        } else {
            lastException?.message ?: lastException?.javaClass?.simpleName
        }
    }

    private fun buildWgConfig(tunnel: WgTunnel): Config {
        val configStr = """
            [Interface]
            PrivateKey = ${tunnel.clientPrivateKey}
            Address = ${tunnel.clientAddress}
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1200
            ExcludedApplications = ${context.packageName}

            [Peer]
            PublicKey = ${tunnel.serverPublicKey}
            Endpoint = ${tunnel.serverEndpoint}
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 15
        """.trimIndent()
        
        return try {
            Config.parse(configStr.byteInputStream())
        } catch (e: Exception) {
            android.util.Log.e("WgVPN", "Error parsing config", e)
            throw e
        }
    }
}
