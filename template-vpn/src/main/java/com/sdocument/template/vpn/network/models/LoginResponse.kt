package com.sdocument.template.vpn.network.models

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("status") val status: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("is_verification_needed") val isVerificationNeeded: Boolean? = null,
    @SerializedName("is_verified") val isVerified: Boolean? = null,
    @SerializedName("error") val error: String? = null
)
