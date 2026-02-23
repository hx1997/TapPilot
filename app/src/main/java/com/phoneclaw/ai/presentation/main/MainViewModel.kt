package com.phoneclaw.ai.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.model.ExecutionResult
import com.phoneclaw.ai.domain.model.Task
import com.phoneclaw.ai.domain.model.TaskStatus
import com.phoneclaw.ai.domain.usecase.ExecuteTaskUseCase
import com.phoneclaw.ai.domain.usecase.PlanTaskUseCase
import com.phoneclaw.ai.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val planTaskUseCase: PlanTaskUseCase,
    private val executeTaskUseCase: ExecuteTaskUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    companion object {
        private const val MAX_RETRIES_PER_STEP = 3
        private const val STEP_DELAY_MS = 1000L
    }
    
    private val _state = MutableStateFlow<TaskExecutionState>(TaskExecutionState.Idle)
    val state: StateFlow<TaskExecutionState> = _state.asStateFlow()
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    
    private val _currentTask = MutableStateFlow<Task?>(null)
    val currentTask: StateFlow<Task?> = _currentTask.asStateFlow()
    
    private var executionJob: Job? = null
    
    fun executeTask(taskDescription: String) {
        if (taskDescription.isBlank()) {
            _state.value = TaskExecutionState.Error("Task description cannot be empty")
            return
        }
        
        // Use Dispatchers.Default to continue execution even when app is in background
        executionJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                addLog("Starting task: $taskDescription")
                addLog("Using reactive execution model - planning one step at a time")
                
                val task = Task(description = taskDescription, status = TaskStatus.EXECUTING)
                _currentTask.value = task
                
                executeReactively(task)
                
            } catch (e: Exception) {
                _state.value = TaskExecutionState.Error("Unexpected error: ${e.message}")
                addLog("ERROR: ${e.message}")
                Timber.e(e, "Execution failed")
            }
        }
    }
    
    private suspend fun executeReactively(task: Task) {
        var stepCount = 0
        var consecutiveFailures = 0
        val executedActions = mutableListOf<ActionStep>()
        val maxSteps = preferencesManager.maxExecutionSteps
        
        addLog("Max steps configured: $maxSteps")
        
        while (stepCount < maxSteps) {
            stepCount++
            _state.value = TaskExecutionState.Executing(
                currentStep = stepCount,
                totalSteps = maxSteps,
                currentAction = null
            )
            
            // Ask LLM for the next action based on current screen state
            addLog("Step $stepCount: Analyzing screen and planning next action...")
            
            val nextActionResult = planTaskUseCase.execute(task.description, executedActions)
            
            if (nextActionResult.isFailure) {
                val error = nextActionResult.exceptionOrNull()
                addLog("  ERROR: Failed to get next action - ${error?.message}")
                Timber.e(error, "Failed to plan next action")
                consecutiveFailures++
                
                if (consecutiveFailures >= MAX_RETRIES_PER_STEP) {
                    _state.value = TaskExecutionState.Error("Failed to plan after $consecutiveFailures attempts")
                    return
                }
                
                delay(STEP_DELAY_MS)
                continue
            }
            
            val nextActions = nextActionResult.getOrThrow()
            
            // Check if task is complete (empty array returned)
            if (nextActions.isEmpty()) {
                addLog("Task completed! LLM confirms no more actions needed.")
                _state.value = TaskExecutionState.Success()
                _currentTask.value = task.copy(
                    status = TaskStatus.COMPLETED,
                    actionPlan = executedActions,
                    completedAt = System.currentTimeMillis()
                )
                return
            }
            
            // Execute the single next action
            val action = nextActions.first()
            val actionDescription = formatAction(action)
            
            addLog("  Executing: $actionDescription")
            
            _state.value = TaskExecutionState.Executing(
                currentStep = stepCount,
                totalSteps = maxSteps,
                currentAction = action
            )
            
            val result = executeTaskUseCase.executeStep(action)
            
            when (result) {
                is ExecutionResult.Success -> {
                    addLog("  Action completed successfully")
                    executedActions.add(action)
                    consecutiveFailures = 0  // Reset failure counter on success
                    
                    _currentTask.value = task.copy(
                        actionPlan = executedActions,
                        currentStepIndex = stepCount
                    )
                }
                is ExecutionResult.Failure -> {
                    addLog("  Action failed: ${result.errorMessage}")
                    consecutiveFailures++
                    
                    if (consecutiveFailures >= MAX_RETRIES_PER_STEP) {
                        _state.value = TaskExecutionState.Error("Failed after $consecutiveFailures consecutive failures: ${result.errorMessage}")
                        return
                    }
                    
                    addLog("  Will retry with fresh screen analysis...")
                }
                is ExecutionResult.ScreenContext -> {
                    addLog("  Screen context updated")
                }
            }
            
            // Wait for screen to settle before next iteration
            delay(STEP_DELAY_MS)
        }
        
        // Reached max steps
        addLog("Warning: Reached maximum step limit ($maxSteps)")
        _state.value = TaskExecutionState.Success("Completed (max steps reached)")
        _currentTask.value = task.copy(
            status = TaskStatus.COMPLETED,
            actionPlan = executedActions,
            completedAt = System.currentTimeMillis()
        )
    }
    
    fun cancelTask() {
        executionJob?.cancel()
        executionJob = null
        _state.value = TaskExecutionState.Cancelled
        _currentTask.value = _currentTask.value?.copy(status = TaskStatus.CANCELLED)
        addLog("Task cancelled by user")
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
    
    fun resetState() {
        _state.value = TaskExecutionState.Idle
        _currentTask.value = null
    }
    
    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        _logs.value = _logs.value + logEntry
        Timber.d(message)
    }
    
    private fun formatAction(action: ActionStep): String {
        return when (action) {
            is ActionStep.Click -> "Click on ${action.description ?: "coordinates (${action.x}, ${action.y})"}"
            is ActionStep.Type -> "Type '${action.text}' ${action.targetField?.let { "in $it" } ?: ""}"
            is ActionStep.Scroll -> "Scroll ${action.direction.name.lowercase()}"
            is ActionStep.Swipe -> "Swipe from (${action.startX},${action.startY}) to (${action.endX},${action.endY})"
            is ActionStep.Wait -> "Wait ${action.milliseconds}ms"
            is ActionStep.LaunchApp -> "Launch ${action.packageName}"
            is ActionStep.PressBack -> "Press Back button"
            is ActionStep.PressHome -> "Press Home button"
            is ActionStep.Screenshot -> "Take screenshot"
        }
    }
}
