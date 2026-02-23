package com.phoneclaw.ai.domain.usecase

import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.repository.LLMRepository
import com.phoneclaw.ai.domain.repository.VerificationResult
import com.phoneclaw.ai.service.ScreenAnalyzer
import com.phoneclaw.ai.util.ScreenshotCapture

class PlanTaskUseCase(
    private val llmRepository: LLMRepository,
    private val screenAnalyzer: ScreenAnalyzer?,
    private val screenshotCapture: ScreenshotCapture?
) {
    suspend fun execute(
        taskDescription: String,
        executedActions: List<ActionStep> = emptyList()
    ): Result<List<ActionStep>> {
        // Capture current screen context
        val screenContext = screenAnalyzer?.analyzeCurrentScreen()
        val screenshotBase64 = screenshotCapture?.captureAndEncode()
        
        // Build action history string
        val actionHistory = if (executedActions.isNotEmpty()) {
            buildActionHistory(executedActions)
        } else {
            null
        }
        
        return llmRepository.planTask(
            taskDescription = taskDescription,
            screenContext = screenContext,
            screenshotBase64 = screenshotBase64,
            actionHistory = actionHistory
        )
    }
    
    private fun buildActionHistory(actions: List<ActionStep>): String {
        val sb = StringBuilder()
        sb.append("Actions already executed:\n")
        actions.forEachIndexed { index, action ->
            sb.append("${index + 1}. ${actionToJson(action)}\n")
        }
        return sb.toString()
    }
    
    private fun actionToJson(action: ActionStep): String {
        return when (action) {
            is ActionStep.Click -> {
                val parts = mutableListOf("\"action\": \"CLICK\"")
                action.x?.let { parts.add("\"x\": $it") }
                action.y?.let { parts.add("\"y\": $it") }
                action.description?.let { parts.add("\"description\": \"$it\"") }
                "{${parts.joinToString(", ")}}"
            }
            is ActionStep.Type -> "{\"action\": \"TYPE\", \"text\": \"${action.text}\"}"
            is ActionStep.Scroll -> "{\"action\": \"SCROLL\", \"direction\": \"${action.direction}\"}"
            is ActionStep.Swipe -> "{\"action\": \"SWIPE\", \"startX\": ${action.startX}, \"startY\": ${action.startY}, \"endX\": ${action.endX}, \"endY\": ${action.endY}}"
            is ActionStep.Wait -> "{\"action\": \"WAIT\", \"duration\": ${action.milliseconds}}"
            is ActionStep.LaunchApp -> "{\"action\": \"LAUNCH_APP\", \"package\": \"${action.packageName}\"}"
            is ActionStep.PressBack -> "{\"action\": \"PRESS_BACK\"}"
            is ActionStep.PressHome -> "{\"action\": \"PRESS_HOME\"}"
            is ActionStep.Screenshot -> "{\"action\": \"SCREENSHOT\"}"
        }
    }
    
    suspend fun replan(
        originalTask: String,
        failureReason: String
    ): Result<List<ActionStep>> {
        val screenContext = screenAnalyzer?.analyzeCurrentScreen()
        val screenshotBase64 = screenshotCapture?.captureAndEncode()
        
        return llmRepository.replanWithFeedback(
            originalTask = originalTask,
            failureReason = failureReason,
            currentScreenContext = screenContext,
            screenshotBase64 = screenshotBase64
        )
    }
    
    suspend fun verifyAction(
        originalTask: String,
        executedAction: ActionStep
    ): Result<VerificationResult> {
        val screenContext = screenAnalyzer?.analyzeCurrentScreen()
        val screenshotBase64 = screenshotCapture?.captureAndEncode()
        
        return llmRepository.verifyActionSuccess(
            originalTask = originalTask,
            executedAction = executedAction,
            currentScreenContext = screenContext,
            screenshotBase64 = screenshotBase64
        )
    }
}
