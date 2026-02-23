package com.phoneclaw.ai.presentation.main

import com.phoneclaw.ai.domain.model.ActionStep

sealed class TaskExecutionState {
    object Idle : TaskExecutionState()
    object Planning : TaskExecutionState()
    data class Executing(
        val currentStep: Int,
        val totalSteps: Int,
        val currentAction: ActionStep? = null
    ) : TaskExecutionState()
    data class Success(val message: String = "Task completed successfully") : TaskExecutionState()
    data class Error(val message: String) : TaskExecutionState()
    object Cancelled : TaskExecutionState()
}
