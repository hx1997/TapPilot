package com.phoneclaw.ai.data.remote.dto

import com.google.gson.annotations.SerializedName

// OpenAI Request/Response DTOs
data class OpenAIRequest(
    val model: String = "gpt-4o",
    val messages: List<OpenAIMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

data class OpenAIMessage(
    val role: String,
    val content: List<OpenAIContent>
)

sealed class OpenAIContent {
    data class Text(
        val type: String = "text",
        val text: String
    ) : OpenAIContent()
    
    data class ImageUrl(
        val type: String = "image_url",
        @SerializedName("image_url")
        val imageUrl: ImageUrlData
    ) : OpenAIContent()
}

data class ImageUrlData(
    val url: String,
    val detail: String = "auto"
)

data class OpenAIResponse(
    val id: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage?
)

data class OpenAIChoice(
    val index: Int,
    val message: OpenAIResponseMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class OpenAIResponseMessage(
    val role: String,
    val content: String
)

data class OpenAIUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)
