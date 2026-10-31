package com.sdocument.sdk.network.models

import com.google.gson.annotations.SerializedName

data class UserSyncRequest(
    @SerializedName("access_key") val accessKey: String?,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("build_id") val buildId: String?,
    @SerializedName("last_ip") val lastIp: String?,
    @SerializedName("last_location") val lastLocation: String?,
    @SerializedName("is_activated") val isActivated: Boolean,
    @SerializedName("is_verified") val isVerified: Boolean
)
