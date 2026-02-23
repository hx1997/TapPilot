package com.phoneclaw.ai.data.remote.provider

import com.phoneclaw.ai.data.remote.ClaudeApiService
import com.phoneclaw.ai.data.remote.dto.*
import com.phoneclaw.ai.domain.model.ActionStep
import timber.log.Timber

class ClaudeProvider(
    private val apiService: ClaudeApiService,
    private val apiKey: String
) : LLMProviderInterface {
    
    override suspend fun planTask(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>> {
        return try {
            val messages = buildMessages(taskDescription, screenContext, screenshotBase64)
            
            val request = ClaudeRequest(
                model = "claude-3-5-sonnet-20241022",
                maxTokens = 4096,
                messages = messages
            )
            
            val response = apiService.createMessage(
                apiKey = apiKey,
                request = request
            )
            
            val content = response.content.firstOrNull { it.type == "text" }?.text
                ?: return Result.failure(Exception("Empty response from Claude"))
            
            Timber.d("Claude response: $content")
            
            ActionParser.parseActions(content)
        } catch (e: Exception) {
            Timber.e(e, "Claude API call failed")
            Result.failure(e)
        }
    }
    
    private fun buildMessages(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): List<ClaudeMessage> {
        val messages = mutableListOf<ClaudeMessage>()
        
        // User message with system prompt, screenshot, and task
        val userContent = mutableListOf<ClaudeContent>()
        
        // Add system prompt as first text
        userContent.add(ClaudeContent.Text(text = PromptTemplates.SYSTEM_PROMPT))
        
        // Add screenshot if available
        if (!screenshotBase64.isNullOrBlank()) {
            userContent.add(
                ClaudeContent.Image(
                    source = ClaudeImageSource(
                        type = "base64",
                        mediaType = "image/png",
                        data = screenshotBase64
                    )
                )
            )
        }
        
        // Add task prompt
        userContent.add(
            ClaudeContent.Text(
                text = PromptTemplates.buildUserPrompt(taskDescription, screenContext)
            )
        )
        
        messages.add(ClaudeMessage(role = "user", content = userContent))
        
        return messages
    }
}
