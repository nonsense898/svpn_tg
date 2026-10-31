package com.sdocument.template.vpn.network.models

import com.google.gson.annotations.SerializedName

data class LogResponse(
    @SerializedName("status") val status: String,
    @SerializedName("id") val id: Int? = null
)
