package com.example.aitranslator.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface TranslationApi {
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>

    @POST("v1/translate")
    suspend fun translate(
        @Body request: TranslateRequest
    ): Response<TranslateResponse>
}
