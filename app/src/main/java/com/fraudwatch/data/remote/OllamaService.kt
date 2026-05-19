package com.fraudwatch.data.remote

import com.fraudwatch.data.model.OllamaRequest
import com.fraudwatch.data.model.OllamaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaService {

    @POST("api/generate")
    suspend fun analyzeImage(@Body request: OllamaRequest): Response<OllamaResponse>
}
