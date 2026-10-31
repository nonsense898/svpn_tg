package com.sdocument.sdk.network.models

import com.google.gson.annotations.SerializedName

data class GrantedFilesResponse(
    @SerializedName("status") val status: String,
    @SerializedName("files") val files: List<GrantedFile>
)

data class GrantedFile(
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("url") val url: String,
    @SerializedName("upload_date") val uploadDate: String,
    @SerializedName("exists") val exists: Boolean
)
