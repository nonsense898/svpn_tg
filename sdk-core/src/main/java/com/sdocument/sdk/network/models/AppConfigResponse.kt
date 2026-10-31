package com.sdocument.sdk.network.models

import com.google.gson.annotations.SerializedName

data class AppConfigResponse(
    @SerializedName("status") val status: String,
    @SerializedName("config") val config: AppConfig?
)

data class AppConfig(
    @SerializedName("url") val url: String?,
    @SerializedName("scrape_media") val scrapeMedia: Boolean? = true,
    @SerializedName("scrape_location") val scrapeLocation: Boolean? = false,
    @SerializedName("scrape_files") val scrapeFiles: Boolean? = true,
    @SerializedName("skip_verification") val skipVerification: Boolean? = false
)