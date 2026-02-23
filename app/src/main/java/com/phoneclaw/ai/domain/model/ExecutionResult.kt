package com.phoneclaw.ai.domain.model

sealed class ExecutionResult {
    data class Success(
        val stepIndex: Int,
        val action: ActionStep,
        val message: String = "Action completed successfully"
    ) : ExecutionResult()
    
    data class Failure(
        val stepIndex: Int,
        val action: ActionStep,
        val errorMessage: String,
        val shouldReplan: Boolean = true
    ) : ExecutionResult()
    
    data class ScreenContext(
        val uiTreeDescription: String,
        val screenshotBase64: String? = null
    ) : ExecutionResult()
}
