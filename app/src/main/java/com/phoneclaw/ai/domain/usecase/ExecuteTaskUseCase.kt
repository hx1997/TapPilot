package com.phoneclaw.ai.domain.usecase

import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.model.ExecutionResult
import com.phoneclaw.ai.service.executor.ActionExecutor
import kotlinx.coroutines.delay

class ExecuteTaskUseCase(
    private val actionExecutor: ActionExecutor?,
    private val executionDelay: Long = 500L
) {
    suspend fun executeStep(action: ActionStep): ExecutionResult {
        if (actionExecutor == null) {
            return ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Accessibility service not available",
                shouldReplan = false
            )
        }
        
        // Add delay between actions for stability
        if (action !is ActionStep.Wait) {
            delay(executionDelay)
        }
        
        return try {
            actionExecutor.execute(action)
        } catch (e: Exception) {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = e.message ?: "Unknown error",
                shouldReplan = true
            )
        }
    }
    
    suspend fun executeAll(actions: List<ActionStep>): List<ExecutionResult> {
        return actions.mapIndexed { index, action ->
            val result = executeStep(action)
            when (result) {
                is ExecutionResult.Success -> result.copy(stepIndex = index)
                is ExecutionResult.Failure -> result.copy(stepIndex = index)
                else -> result
            }
        }
    }
}
