package com.phoneclaw.ai.domain.model

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val actionPlan: List<ActionStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

enum class TaskStatus {
    PENDING,
    PLANNING,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}
