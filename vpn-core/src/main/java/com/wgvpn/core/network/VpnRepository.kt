package com.wgvpn.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wgvpn.core.models.VpnConfigRequest
import com.wgvpn.core.models.WgTunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: VpnApiService
) {

    private val gson = Gson()
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("vpn_secure_prefs", Context.MODE_PRIVATE)
    }

    suspend fun getVpnConfigs(userId: String, deviceId: String, forceRefresh: Boolean = false): Result<List<WgTunnel>> = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh) {
                val cached = getCachedConfigs()
                if (cached.isNotEmpty()) {
                    return@withContext Result.success(cached)
                }
            }

            val response = apiService.getVpnConfig(VpnConfigRequest(userId, deviceId))
            val tunnels = response.tunnels
            if (response.success && !tunnels.isNullOrEmpty()) {
                saveConfigs(tunnels)
                Result.success(tunnels)
            } else {
                val cached = getCachedConfigs()
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception("Сервер не вернул конфигурацию VPN"))
                }
            }
        } catch (e: Exception) {
            val cached = getCachedConfigs()
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    fun clearCache() {
        sharedPreferences.edit().remove("cached_tunnels").apply()
    }

    private fun saveConfigs(tunnels: List<WgTunnel>) {
        sharedPreferences.edit().putString("cached_tunnels", gson.toJson(tunnels)).apply()
    }

    fun getCachedConfigs(): List<WgTunnel> {
        val json = sharedPreferences.getString("cached_tunnels", null) ?: return emptyList()
        val type = object : TypeToken<List<WgTunnel>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
