package com.sdocument.sdk.network.models

import com.google.gson.annotations.SerializedName

data class FileUploadResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("location") val location: String
)
