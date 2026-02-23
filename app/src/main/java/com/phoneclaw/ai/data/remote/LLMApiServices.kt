package com.phoneclaw.ai.data.remote

import com.phoneclaw.ai.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface OpenAIApiService {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun createCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse
}

interface ClaudeApiService {
    @Headers("Content-Type: application/json", "anthropic-version: 2023-06-01")
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Body request: ClaudeRequest
    ): ClaudeResponse
}

interface GeminiApiService {
    @Headers("Content-Type: application/json")
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-1.5-pro",
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
