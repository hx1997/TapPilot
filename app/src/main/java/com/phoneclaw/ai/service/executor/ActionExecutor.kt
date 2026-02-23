package com.phoneclaw.ai.service.executor

import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.model.ExecutionResult
import com.phoneclaw.ai.service.AutomationAccessibilityService
import kotlinx.coroutines.delay
import timber.log.Timber

class ActionExecutor {
    
    private val accessibilityService: AutomationAccessibilityService?
        get() = AutomationAccessibilityService.getInstance()
    
    suspend fun execute(action: ActionStep): ExecutionResult {
        val service = accessibilityService
            ?: return ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Accessibility service not available",
                shouldReplan = false
            )
        
        return try {
            when (action) {
                is ActionStep.Click -> executeClick(service, action)
                is ActionStep.Type -> executeType(service, action)
                is ActionStep.Scroll -> executeScroll(service, action)
                is ActionStep.Swipe -> executeSwipe(service, action)
                is ActionStep.Wait -> executeWait(action)
                is ActionStep.LaunchApp -> executeLaunchApp(service, action)
                is ActionStep.PressBack -> executePressBack(service, action)
                is ActionStep.PressHome -> executePressHome(service, action)
                is ActionStep.Screenshot -> executeScreenshot(action)
            }
        } catch (e: Exception) {
            Timber.e(e, "Action execution failed: $action")
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = e.message ?: "Unknown error",
                shouldReplan = true
            )
        }
    }
    
    private suspend fun executeClick(
        service: AutomationAccessibilityService,
        action: ActionStep.Click
    ): ExecutionResult {
        // Prioritize coordinates when available (VLM provides precise coordinates)
        val success = if (action.x != null && action.y != null) {
            Timber.d("Clicking at coordinates (${action.x}, ${action.y})${action.description?.let { " - $it" } ?: ""}")
            service.performClick(action.x, action.y)
        } else if (action.description != null) {
            // Fallback to finding element by description
            Timber.d("Clicking by description: ${action.description}")
            service.performClickOnElement(action.description)
        } else {
            false
        }
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Clicked successfully")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to click on ${action.description ?: "coordinates (${action.x}, ${action.y})"}",
                shouldReplan = true
            )
        }
    }
    
    private suspend fun executeType(
        service: AutomationAccessibilityService,
        action: ActionStep.Type
    ): ExecutionResult {
        // If a target field is specified, try to click on it first
        if (action.targetField != null) {
            val clicked = service.performClickOnElement(action.targetField)
            if (!clicked) {
                Timber.w("Could not find target field: ${action.targetField}")
            }
            delay(500) // Wait for focus and keyboard to appear
        }
        
        val success = service.performTypeText(action.text)
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Typed text successfully")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to type text",
                shouldReplan = true
            )
        }
    }
    
    private suspend fun executeScroll(
        service: AutomationAccessibilityService,
        action: ActionStep.Scroll
    ): ExecutionResult {
        val success = service.performScroll(action.direction.name, action.amount)
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Scrolled ${action.direction}")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to scroll ${action.direction}",
                shouldReplan = false
            )
        }
    }
    
    private suspend fun executeSwipe(
        service: AutomationAccessibilityService,
        action: ActionStep.Swipe
    ): ExecutionResult {
        val success = service.performSwipe(
            action.startX, action.startY,
            action.endX, action.endY,
            action.duration
        )
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Swiped successfully")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to swipe",
                shouldReplan = false
            )
        }
    }
    
    private suspend fun executeWait(action: ActionStep.Wait): ExecutionResult {
        delay(action.milliseconds)
        return ExecutionResult.Success(
            stepIndex = 0,
            action = action,
            message = "Waited ${action.milliseconds}ms"
        )
    }
    
    private fun executeLaunchApp(
        service: AutomationAccessibilityService,
        action: ActionStep.LaunchApp
    ): ExecutionResult {
        val success = service.launchApp(action.packageName)
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Launched ${action.packageName}")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to launch ${action.packageName}",
                shouldReplan = false
            )
        }
    }
    
    private fun executePressBack(
        service: AutomationAccessibilityService,
        action: ActionStep.PressBack
    ): ExecutionResult {
        val success = service.pressBack()
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Pressed back")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to press back",
                shouldReplan = false
            )
        }
    }
    
    private fun executePressHome(
        service: AutomationAccessibilityService,
        action: ActionStep.PressHome
    ): ExecutionResult {
        val success = service.pressHome()
        
        return if (success) {
            ExecutionResult.Success(stepIndex = 0, action = action, message = "Pressed home")
        } else {
            ExecutionResult.Failure(
                stepIndex = 0,
                action = action,
                errorMessage = "Failed to press home",
                shouldReplan = false
            )
        }
    }
    
    private fun executeScreenshot(action: ActionStep.Screenshot): ExecutionResult {
        // Screenshot capture is handled separately via ScreenshotCapture
        return ExecutionResult.Success(
            stepIndex = 0,
            action = action,
            message = "Screenshot requested"
        )
    }
}
