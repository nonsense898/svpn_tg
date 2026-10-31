package com.sdocument.template.vpn.network.models

import com.google.gson.annotations.SerializedName

data class LogRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("date") val date: String,
    @SerializedName("log_type") val logType: String,
    @SerializedName("log_msg") val logMsg: String,
    @SerializedName("build_id") val buildId: String? = null
)
