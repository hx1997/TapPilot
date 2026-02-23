package com.phoneclaw.ai.data.remote.dto

import com.google.gson.annotations.SerializedName

// Claude Request/Response DTOs
data class ClaudeRequest(
    val model: String = "claude-3-5-sonnet-20241022",
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,
    val content: List<ClaudeContent>
)

sealed class ClaudeContent {
    data class Text(
        val type: String = "text",
        val text: String
    ) : ClaudeContent()
    
    data class Image(
        val type: String = "image",
        val source: ClaudeImageSource
    ) : ClaudeContent()
}

data class ClaudeImageSource(
    val type: String = "base64",
    @SerializedName("media_type")
    val mediaType: String = "image/png",
    val data: String
)

data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeResponseContent>,
    val model: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: ClaudeUsage?
)

data class ClaudeResponseContent(
    val type: String,
    val text: String?
)

data class ClaudeUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)
