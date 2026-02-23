package com.phoneclaw.ai.domain.repository

import com.phoneclaw.ai.domain.model.ActionStep

interface LLMRepository {
    suspend fun planTask(
        taskDescription: String,
        screenContext: String? = null,
        screenshotBase64: String? = null,
        actionHistory: String? = null
    ): Result<List<ActionStep>>
    
    suspend fun replanWithFeedback(
        originalTask: String,
        failureReason: String,
        currentScreenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>>
    
    suspend fun verifyActionSuccess(
        originalTask: String,
        executedAction: ActionStep,
        currentScreenContext: String?,
        screenshotBase64: String?
    ): Result<VerificationResult>
}

data class VerificationResult(
    val isSuccessful: Boolean,
    val isTaskComplete: Boolean,
    val reason: String,
    val suggestedNextAction: String? = null
)
