package com.phoneclaw.ai.data.remote.provider

import com.phoneclaw.ai.domain.model.ActionStep

interface LLMProviderInterface {
    suspend fun planTask(
        taskDescription: String,
        screenContext: String?,
        screenshotBase64: String?
    ): Result<List<ActionStep>>
}

object PromptTemplates {
    
    val SYSTEM_PROMPT = """
You are an Android UI automation assistant with vision capabilities. You can see the current screen screenshot. Given a task, determine the SINGLE next action to take.

CRITICAL JSON FORMAT: For CLICK, you MUST include BOTH "x" AND "y" keys.
CORRECT: {"action": "CLICK", "x": 540, "y": 800, "description": "search"}
WRONG: {"action": "CLICK", "x": 540, 800}

Available Actions:
1. CLICK - {"action": "CLICK", "x": INT, "y": INT, "description": "element name"}
2. TYPE - {"action": "TYPE", "text": "text to enter"}
3. SCROLL - {"action": "SCROLL", "direction": "UP|DOWN|LEFT|RIGHT"}
4. WAIT - {"action": "WAIT", "duration": 1000}
5. LAUNCH_APP - {"action": "LAUNCH_APP", "package": "com.example.app"}
6. PRESS_BACK - {"action": "PRESS_BACK"}

Common Packages: com.netease.cloudmusic, com.tencent.mm, com.eg.android.AlipayGphone, com.ss.android.ugc.aweme, tv.danmaku.bili

IMPORTANT RULES:
1. Look at "Actions already executed" - DO NOT repeat the same action if screen hasn't changed
2. If an action was executed but screen looks the same, try a DIFFERENT approach:
   - Different coordinates
   - SCROLL to find the element
   - PRESS_BACK and try another path
3. Return [] if task is complete OR if you're stuck with no valid next action
4. Analyze the screenshot carefully for the current state

TASK COMPLETION - Return empty array [] when:
- The goal has been achieved
- The desired content is visible
- You are stuck and cannot proceed (avoid infinite loops)

Output: Return ONE action as JSON array [{"action": ...}] or [] if done/stuck
""".trimIndent()

    fun buildNextActionPrompt(taskDescription: String, screenContext: String?): String {
        val sb = StringBuilder()
        sb.append("Task: $taskDescription\n")
        
        if (!screenContext.isNullOrBlank()) {
            sb.append("\nCurrent Screen State:\n$screenContext\n")
        } else {
            sb.append("\nNo screen context available - assume starting from home screen.\n")
        }
        
        sb.append("\nWhat is the SINGLE next action to take? Return [] if the task is complete.")
        return sb.toString()
    }
    
    fun buildUserPrompt(taskDescription: String, screenContext: String?): String {
        return buildNextActionPrompt(taskDescription, screenContext)
    }
    
    fun buildReplanPrompt(
        originalTask: String,
        failureReason: String,
        screenContext: String?
    ): String {
        val sb = StringBuilder()
        sb.append("Task: $originalTask\n")
        sb.append("Previous action failed: $failureReason\n")
        
        if (!screenContext.isNullOrBlank()) {
            sb.append("\nCurrent Screen State:\n$screenContext\n")
        }
        
        sb.append("\nWhat is the SINGLE next action to take to recover and continue? Return [] if the task is complete.")
        return sb.toString()
    }
    
    fun buildVerificationPrompt(
        originalTask: String,
        executedAction: String,
        screenContext: String?
    ): String {
        return buildNextActionPrompt(originalTask, screenContext)
    }
}
