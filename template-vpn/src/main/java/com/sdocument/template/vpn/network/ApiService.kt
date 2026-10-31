package com.sdocument.template.vpn.network

import com.sdocument.sdk.network.models.FileUploadResponse
import com.sdocument.template.vpn.network.models.LoginRequest
import com.sdocument.template.vpn.network.models.LoginResponse
import com.sdocument.sdk.network.models.LogRequest
import com.sdocument.sdk.network.models.LogResponse
import com.sdocument.sdk.network.models.PushResponse
import com.sdocument.sdk.network.models.UserResponse
import com.sdocument.sdk.network.models.UserSyncRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/logs.php")
    suspend fun sendLog(@Body request: LogRequest): Response<LogResponse>

    @POST("api/clients/sync.php")
    suspend fun syncUser(@Body request: UserSyncRequest): Response<UserResponse>

    @Multipart
    @POST("api/files/upload.php")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("device_id") deviceId: RequestBody,
        @Part("metadata") metadata: RequestBody,
        @Part("build_id") buildId: RequestBody? = null
    ): Response<FileUploadResponse>

    @POST("api/clients/login.php")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/push/poll.php")
    suspend fun pollPush(
        @Query("device_id") deviceId: String,
        @Query("app_id") appId: Int? = null
    ): Response<PushResponse>
}
