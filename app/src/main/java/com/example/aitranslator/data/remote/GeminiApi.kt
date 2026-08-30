package com.example.aitranslator.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): Response<GeminiGenerateContentResponse>

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String,
        @Query("pageSize") pageSize: Int = 100
    ): Response<GeminiListModelsResponse>
}
