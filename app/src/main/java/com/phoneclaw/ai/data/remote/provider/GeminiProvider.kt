package com.phoneclaw.ai.data.remote.provider

import com.phoneclaw.ai.data.remote.GeminiApiService
import com.phoneclaw.ai.data.remote.dto.*
import com.phoneclaw.ai.domain.model.ActionStep
import timber.log.Timber

class GeminiProvider(
    private val apiService: GeminiApiService,
    private val apiKey: String
) : LLMProviderInterface {
    
    override suspend fun planTask(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>> {
        return try {
            val contents = buildContents(taskDescription, screenContext, screenshotBase64)
            
            val request = GeminiRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.7,
                    maxOutputTokens = 4096
                )
            )
            
            val response = apiService.generateContent(
                model = "gemini-1.5-pro",
                apiKey = apiKey,
                request = request
            )
            
            val content = response.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
                ?: return Result.failure(Exception("Empty response from Gemini"))
            
            Timber.d("Gemini response: $content")
            
            ActionParser.parseActions(content)
        } catch (e: Exception) {
            Timber.e(e, "Gemini API call failed")
            Result.failure(e)
        }
    }
    
    private fun buildContents(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): List<GeminiContent> {
        val parts = mutableListOf<GeminiPart>()
        
        // Add system prompt
        parts.add(GeminiPart.Text(text = PromptTemplates.SYSTEM_PROMPT))
        
        // Add screenshot if available
        if (!screenshotBase64.isNullOrBlank()) {
            parts.add(
                GeminiPart.InlineData(
                    inlineData = GeminiInlineData(
                        mimeType = "image/png",
                        data = screenshotBase64
                    )
                )
            )
        }
        
        // Add task prompt
        parts.add(
            GeminiPart.Text(
                text = PromptTemplates.buildUserPrompt(taskDescription, screenContext)
            )
        )
        
        return listOf(GeminiContent(parts = parts, role = "user"))
    }
}
