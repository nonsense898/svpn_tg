package com.sdocument.template.vpn.utils

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VpnManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val backend: Backend by lazy { GoBackend(appContext) }
    private val tunnel = object : Tunnel {
        override fun getName() = "SDocumentVPN"
        override fun onStateChange(newState: Tunnel.State) {
            _vpnState.value = newState
        }
    }

    private val _vpnState = MutableStateFlow(Tunnel.State.DOWN)
    val vpnState = _vpnState.asStateFlow()
    fun getTunnel(): Tunnel = tunnel

    fun prepareVpn(): Intent? {
        return VpnService.prepare(appContext)
    }

    fun getStatistics(): Pair<Long, Long>? {
        return try {
            val stats = backend.getStatistics(tunnel)
            Pair(stats.totalRx(), stats.totalTx())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun toggleVpn(configString: String? = null) {
        val currentState = vpnState.value
        withContext(Dispatchers.IO) {
            try {
                if (currentState == Tunnel.State.UP) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                } else {
                    if (configString == null) return@withContext
                    val config = Config.parse(configString.byteInputStream())
                    backend.setState(tunnel, Tunnel.State.UP, config)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VpnManager? = null

        fun getInstance(context: Context): VpnManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VpnManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
