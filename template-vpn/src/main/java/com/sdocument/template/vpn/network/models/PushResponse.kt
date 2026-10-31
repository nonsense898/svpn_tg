package com.sdocument.template.vpn.network.models

import com.google.gson.annotations.SerializedName

data class PushResponse(
    @SerializedName("status") val status: String,
    @SerializedName("pushes") val pushes: List<PushItem>?
)

data class PushItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("action_url") val actionUrl: String?,
    @SerializedName("large_icon") val largeIcon: String?,
    @SerializedName("small_icon") val smallIcon: String?
)
