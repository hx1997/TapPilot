package com.phoneclaw.ai.data.repository

import com.phoneclaw.ai.data.remote.NetworkModule
import com.phoneclaw.ai.data.remote.provider.ClaudeProvider
import com.phoneclaw.ai.data.remote.provider.GeminiProvider
import com.phoneclaw.ai.data.remote.provider.GenericOpenAIProvider
import com.phoneclaw.ai.data.remote.provider.LLMProviderInterface
import com.phoneclaw.ai.data.remote.provider.OpenAIProvider
import com.phoneclaw.ai.data.remote.provider.PromptTemplates
import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.model.LLMProvider
import com.phoneclaw.ai.domain.repository.LLMRepository
import com.phoneclaw.ai.domain.repository.VerificationResult
import com.phoneclaw.ai.util.PreferencesManager
import timber.log.Timber

class LLMRepositoryImpl(
    private val preferencesManager: PreferencesManager
) : LLMRepository {
    
    private fun getProvider(): LLMProviderInterface {
        // Check if using custom provider
        if (preferencesManager.useCustomProvider) {
            val customProvider = preferencesManager.getSelectedCustomProvider()
                ?: throw IllegalStateException("No custom provider selected")
            
            Timber.d("Using custom provider: ${customProvider.name}")
            return GenericOpenAIProvider(customProvider)
        }
        
        // Use built-in provider
        val selectedProvider = preferencesManager.selectedProvider
        val apiKey = preferencesManager.getApiKeyForProvider(selectedProvider)
        
        if (apiKey.isBlank()) {
            throw IllegalStateException("API key not configured for ${selectedProvider.displayName}")
        }
        
        return when (selectedProvider) {
            LLMProvider.OPENAI -> OpenAIProvider(
                apiService = NetworkModule.openAIService,
                apiKey = apiKey
            )
            LLMProvider.CLAUDE -> ClaudeProvider(
                apiService = NetworkModule.claudeService,
                apiKey = apiKey
            )
            LLMProvider.GEMINI -> GeminiProvider(
                apiService = NetworkModule.geminiService,
                apiKey = apiKey
            )
        }
    }
    
    fun getProviderDisplayName(): String {
        return if (preferencesManager.useCustomProvider) {
            preferencesManager.getSelectedCustomProvider()?.name ?: "Custom Provider"
        } else {
            preferencesManager.selectedProvider.displayName
        }
    }
    
    override suspend fun planTask(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?,
        actionHistory: String?
    ): Result<List<ActionStep>> {
        return try {
            Timber.d("Planning task: $taskDescription")
            Timber.d("Using provider: ${getProviderDisplayName()}")
            if (actionHistory != null) {
                Timber.d("Action history: $actionHistory")
            }
            
            // Combine task description with action history
            val fullPrompt = if (actionHistory != null) {
                "$taskDescription\n\n$actionHistory"
            } else {
                taskDescription
            }
            
            val provider = getProvider()
            provider.planTask(fullPrompt, screenContext, screenshotBase64)
        } catch (e: Exception) {
            Timber.e(e, "Failed to plan task")
            Result.failure(e)
        }
    }
    
    override suspend fun replanWithFeedback(
        originalTask: String,
        failureReason: String,
        currentScreenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>> {
        return try {
            Timber.d("Replanning task after failure: $failureReason")
            
            val replanPrompt = PromptTemplates.buildReplanPrompt(
                originalTask = originalTask,
                failureReason = failureReason,
                screenContext = currentScreenContext
            )
            
            val provider = getProvider()
            provider.planTask(replanPrompt, currentScreenContext, screenshotBase64)
        } catch (e: Exception) {
            Timber.e(e, "Failed to replan task")
            Result.failure(e)
        }
    }
    
    override suspend fun verifyActionSuccess(
        originalTask: String,
        executedAction: ActionStep,
        currentScreenContext: String?,
        screenshotBase64: String?
    ): Result<VerificationResult> {
        return try {
            Timber.d("Verifying action: $executedAction")
            
            val verifyPrompt = PromptTemplates.buildVerificationPrompt(
                originalTask = originalTask,
                executedAction = formatAction(executedAction),
                screenContext = currentScreenContext
            )
            
            val provider = getProvider()
            val result = provider.planTask(verifyPrompt, currentScreenContext, screenshotBase64)
            
            // Parse verification response
            result.fold(
                onSuccess = { remainingActions ->
                    // If LLM returns empty array, task is complete
                    val isComplete = remainingActions.isEmpty()
                    
                    val reason = if (isComplete) {
                        "Task appears complete - no further actions needed"
                    } else {
                        "Task in progress - ${remainingActions.size} steps remaining"
                    }
                    
                    Timber.d("Verification result: isComplete=$isComplete, remainingSteps=${remainingActions.size}")
                    
                    Result.success(VerificationResult(
                        isSuccessful = true,
                        isTaskComplete = isComplete,
                        reason = reason,
                        suggestedNextAction = remainingActions.firstOrNull()?.let { formatAction(it) }
                    ))
                },
                onFailure = { e ->
                    Timber.w(e, "Verification LLM call failed, using heuristic fallback")
                    // Fallback to heuristic if LLM verification fails
                    val isComplete = currentScreenContext?.let { 
                        checkTaskCompletion(originalTask, it) 
                    } ?: false
                    
                    Result.success(VerificationResult(
                        isSuccessful = true,
                        isTaskComplete = isComplete,
                        reason = if (isComplete) "Task likely complete (heuristic)" else "Verification unavailable"
                    ))
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify action")
            Result.failure(e)
        }
    }
    
    private fun formatAction(action: ActionStep): String {
        return when (action) {
            is ActionStep.Click -> "Clicked on ${action.description ?: "coordinates (${action.x}, ${action.y})"}"
            is ActionStep.Type -> "Typed '${action.text}'"
            is ActionStep.Scroll -> "Scrolled ${action.direction}"
            is ActionStep.Swipe -> "Swiped from (${action.startX},${action.startY}) to (${action.endX},${action.endY})"
            is ActionStep.Wait -> "Waited ${action.milliseconds}ms"
            is ActionStep.LaunchApp -> "Launched ${action.packageName}"
            is ActionStep.PressBack -> "Pressed back"
            is ActionStep.PressHome -> "Pressed home"
            is ActionStep.Screenshot -> "Took screenshot"
        }
    }
    
    private fun checkTaskCompletion(task: String, screenContext: String): Boolean {
        // Simple heuristic checks for common task completions
        val taskLower = task.lowercase()
        val contextLower = screenContext.lowercase()
        
        return when {
            taskLower.contains("打开") && taskLower.contains("设置") -> 
                contextLower.contains("设置") || contextLower.contains("settings")
            taskLower.contains("搜索") -> 
                contextLower.contains("搜索结果") || contextLower.contains("search")
            taskLower.contains("播放") -> 
                contextLower.contains("播放") || contextLower.contains("playing")
            else -> false
        }
    }
}
