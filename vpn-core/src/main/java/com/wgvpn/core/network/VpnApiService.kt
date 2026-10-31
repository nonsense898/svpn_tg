package com.wgvpn.core.network

import com.wgvpn.core.models.VpnConfigRequest
import com.wgvpn.core.models.VpnConfigResponse
import retrofit2.http.Body
import retrofit2.http.POST
import com.sdocument.sdk.network.RetrofitClient

interface VpnApiService {
    @POST("api/vpn/get-config.php")
    suspend fun getVpnConfig(@Body request: VpnConfigRequest): VpnConfigResponse

    companion object {
        fun create(): VpnApiService {
            return RetrofitClient.createService(VpnApiService::class.java)
        }
    }
}
