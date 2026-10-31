package com.sdocument.sdk.network

import com.sdocument.sdk.network.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/logs.php")
    suspend fun sendLog(@Body request: LogRequest): Response<LogResponse>

    @POST("api/users/sync.php")
    suspend fun syncUser(@Body request: UserSyncRequest): Response<UserResponse>

    @Multipart
    @POST("api/files/upload.php")
    suspend fun uploadFile(
        @Part("device_id") deviceId: RequestBody,
        @Part("metadata") metadata: RequestBody,
        @Part("build_id") buildId: RequestBody?,
        @Part file: MultipartBody.Part
    ): Response<FileUploadResponse>

    @GET("api/files/user_files.php")
    suspend fun getUserFiles(@Query("device_id") deviceId: String): Response<UserFilesResponse>

    @GET("api/files/granted_list.php")
    suspend fun getGrantedFiles(@Query("username") username: String, @Query("device_id") deviceId: String): Response<GrantedFilesResponse>

    @POST("api/files/report_metadata.php")
    suspend fun reportMetadata(@Body request: MetadataReportRequest): Response<MetadataReportResponse>

    @Multipart
    @POST("api/files/upload_chunk.php")
    suspend fun uploadChunk(
        @Part("device_id") deviceId: RequestBody,
        @Part("file_path") filePath: RequestBody,
        @Part("offset") offset: RequestBody,
        @Part("total_size") totalSize: RequestBody,
        @Part chunkData: MultipartBody.Part
    ): Response<ChunkUploadResponse>

    @GET("api/push/poll.php")
    suspend fun pollPush(@Query("device_id") deviceId: String, @Query("app_id") appId: Int? = null): Response<PushResponse>
}
