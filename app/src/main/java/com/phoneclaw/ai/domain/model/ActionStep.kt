package com.phoneclaw.ai.domain.model

sealed class ActionStep {
    data class Click(
        val x: Int? = null,
        val y: Int? = null,
        val description: String? = null
    ) : ActionStep()
    
    data class Type(
        val text: String,
        val targetField: String? = null
    ) : ActionStep()
    
    data class Scroll(
        val direction: ScrollDirection,
        val amount: Int = 500
    ) : ActionStep()
    
    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val duration: Long = 300
    ) : ActionStep()
    
    data class Wait(val milliseconds: Long) : ActionStep()
    
    data class LaunchApp(val packageName: String) : ActionStep()
    
    object PressBack : ActionStep()
    
    object PressHome : ActionStep()
    
    object Screenshot : ActionStep()
}

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}
