package com.example.documentsummarizer.data.openai

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAIService {
    @POST("v1/responses")
    suspend fun responses(
        @Header("Authorization") auth: String,
        @Body body: ResponsesRequest
    ): ResponsesResponse
}