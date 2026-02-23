package com.phoneclaw.ai.data.remote.provider

import com.phoneclaw.ai.data.remote.OpenAIApiService
import com.phoneclaw.ai.data.remote.dto.*
import com.phoneclaw.ai.domain.model.ActionStep
import timber.log.Timber

class OpenAIProvider(
    private val apiService: OpenAIApiService,
    private val apiKey: String
) : LLMProviderInterface {
    
    override suspend fun planTask(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>> {
        return try {
            val messages = buildMessages(taskDescription, screenContext, screenshotBase64)
            
            val request = OpenAIRequest(
                model = "gpt-4o",
                messages = messages,
                maxTokens = 4096,
                temperature = 0.7
            )
            
            val response = apiService.createCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            
            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response from OpenAI"))
            
            Timber.d("OpenAI response: $content")
            
            ActionParser.parseActions(content)
        } catch (e: Exception) {
            Timber.e(e, "OpenAI API call failed")
            Result.failure(e)
        }
    }
    
    private fun buildMessages(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): List<OpenAIMessage> {
        val messages = mutableListOf<OpenAIMessage>()
        
        // System message
        messages.add(
            OpenAIMessage(
                role = "system",
                content = listOf(OpenAIContent.Text(text = PromptTemplates.SYSTEM_PROMPT))
            )
        )
        
        // User message with optional screenshot
        val userContent = mutableListOf<OpenAIContent>()
        
        // Add screenshot if available
        if (!screenshotBase64.isNullOrBlank()) {
            userContent.add(
                OpenAIContent.ImageUrl(
                    imageUrl = ImageUrlData(
                        url = "data:image/png;base64,$screenshotBase64",
                        detail = "high"
                    )
                )
            )
        }
        
        // Add text prompt
        userContent.add(
            OpenAIContent.Text(
                text = PromptTemplates.buildUserPrompt(taskDescription, screenContext)
            )
        )
        
        messages.add(OpenAIMessage(role = "user", content = userContent))
        
        return messages
    }
}
