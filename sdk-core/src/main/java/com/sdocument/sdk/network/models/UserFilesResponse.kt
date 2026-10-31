package com.sdocument.sdk.network.models

import com.google.gson.annotations.SerializedName

data class UserFilesResponse(
    @SerializedName("status") val status: String,
    @SerializedName("files") val files: List<UserFileItem>?
)

data class UserFileItem(
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("location") val location: String,
    @SerializedName("upload_date") val uploadDate: String
)
