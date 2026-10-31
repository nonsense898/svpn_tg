package com.sdocument.template.vpn.network.models

import com.google.gson.annotations.SerializedName

data class UserResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("granted_files") val grantedFiles: List<String>? = null
)
