package com.wgvpn.core.models

import com.google.gson.annotations.SerializedName

data class WgTunnel(
    @SerializedName("name") val name: String,
    @SerializedName("clientPrivateKey") val clientPrivateKey: String,
    @SerializedName("clientAddress") val clientAddress: String,
    @SerializedName("serverPublicKey") val serverPublicKey: String,
    @SerializedName("serverEndpoint") val serverEndpoint: String
)

data class VpnConfigRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("device_id") val deviceId: String
)

data class VpnConfigResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("tunnels") val tunnels: List<WgTunnel>?
)
