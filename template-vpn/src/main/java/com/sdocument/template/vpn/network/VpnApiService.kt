package com.sdocument.template.vpn.network

import com.sdocument.template.vpn.models.VpnConfigRequest
import com.sdocument.template.vpn.models.VpnConfigResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface VpnApiService {
    @POST("api/vpn/get-config.php")
    suspend fun getVpnConfig(@Body request: VpnConfigRequest): VpnConfigResponse

    companion object {
        fun create(): VpnApiService {
            return RetrofitClient.createService(VpnApiService::class.java)
        }
    }
}
