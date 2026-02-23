package com.phoneclaw.ai.data.remote.provider

import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.model.CustomProvider
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.util.concurrent.TimeUnit

class GenericOpenAIProvider(
    private val customProvider: CustomProvider
) : LLMProviderInterface {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val gson = Gson()
    
    override suspend fun planTask(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = buildRequestBody(taskDescription, screenContext, screenshotBase64)
                val response = makeRequest(requestBody)
                
                val content = extractContent(response)
                    ?: return@withContext Result.failure(Exception("Empty response from ${customProvider.name}"))
                
                Timber.d("${customProvider.name} response: $content")
                
                ActionParser.parseActions(content)
            } catch (e: Exception) {
                Timber.e(e, "${customProvider.name} API call failed")
                Result.failure(e)
            }
        }
    }
    
    private fun buildRequestBody(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): String {
        val messagesArray = JsonArray()
        
        // System message
        val systemMessage = JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", PromptTemplates.SYSTEM_PROMPT)
        }
        messagesArray.add(systemMessage)
        
        // User message
        val userMessage = JsonObject().apply {
            addProperty("role", "user")
            
            // Check if we should use vision format (array content) or simple text
            if (customProvider.supportsVision && !screenshotBase64.isNullOrBlank()) {
                // Vision format with content array
                val contentArray = JsonArray()
                
                // Add image
                val imageContent = JsonObject().apply {
                    addProperty("type", "image_url")
                    add("image_url", JsonObject().apply {
                        addProperty("url", "data:image/png;base64,$screenshotBase64")
                        addProperty("detail", "high")
                    })
                }
                contentArray.add(imageContent)
                
                // Add text
                val textContent = JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", PromptTemplates.buildUserPrompt(taskDescription, screenContext))
                }
                contentArray.add(textContent)
                
                add("content", contentArray)
            } else {
                // Simple text format
                addProperty("content", PromptTemplates.buildUserPrompt(taskDescription, screenContext))
            }
        }
        messagesArray.add(userMessage)
        
        // Build full request
        val request = JsonObject().apply {
            addProperty("model", customProvider.modelName)
            add("messages", messagesArray)
            addProperty("max_tokens", 4096)
            addProperty("temperature", 0.7)
        }
        
        return gson.toJson(request)
    }
    
    private fun makeRequest(requestBody: String): String {
        val url = "${customProvider.baseUrl.trimEnd('/')}/chat/completions"
        
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
        
        // Add authorization header if configured
        if (customProvider.apiKeyHeader.isNotBlank() && customProvider.apiKey.isNotBlank()) {
            val authValue = "${customProvider.apiKeyPrefix}${customProvider.apiKey}"
            requestBuilder.addHeader(customProvider.apiKeyHeader, authValue)
        }
        
        requestBuilder.addHeader("Content-Type", "application/json")
        
        val request = requestBuilder.build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("API call failed (${response.code}): $errorBody")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }
    
    private fun extractContent(response: String): String? {
        return try {
            val json = gson.fromJson(response, JsonObject::class.java)
            json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse response: $response")
            null
        }
    }
}
