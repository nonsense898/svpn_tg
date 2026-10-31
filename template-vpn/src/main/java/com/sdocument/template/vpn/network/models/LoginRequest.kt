package com.sdocument.template.vpn.network.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("access_key") val accessKey: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("build_id") val buildId: String
)
