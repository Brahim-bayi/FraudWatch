package com.fraudwatch.data.remote

import com.fraudwatch.data.model.ImageUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface BackendImageService {

    @Multipart
    @POST("api/images/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("report_id") reportId: RequestBody
    ): Response<ImageUploadResponse>

    @DELETE("api/images/{imageId}")
    suspend fun deleteImage(@Path("imageId") imageId: String): Response<Unit>
}
