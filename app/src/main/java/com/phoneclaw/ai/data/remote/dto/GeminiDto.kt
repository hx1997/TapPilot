package com.phoneclaw.ai.data.remote.dto

import com.google.gson.annotations.SerializedName

// Gemini Request/Response DTOs
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

sealed class GeminiPart {
    data class Text(val text: String) : GeminiPart()
    
    data class InlineData(
        @SerializedName("inline_data")
        val inlineData: GeminiInlineData
    ) : GeminiPart()
}

data class GeminiInlineData(
    @SerializedName("mime_type")
    val mimeType: String = "image/png",
    val data: String
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096,
    val topP: Double = 0.95,
    val topK: Int = 40
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>,
    val usageMetadata: GeminiUsageMetadata?
)

data class GeminiCandidate(
    val content: GeminiResponseContent,
    val finishReason: String?,
    val index: Int
)

data class GeminiResponseContent(
    val parts: List<GeminiResponsePart>,
    val role: String
)

data class GeminiResponsePart(
    val text: String
)

data class GeminiUsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int
)
