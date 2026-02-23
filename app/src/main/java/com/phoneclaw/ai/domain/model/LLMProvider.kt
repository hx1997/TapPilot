package com.phoneclaw.ai.domain.model

enum class LLMProvider(val displayName: String, val supportsVision: Boolean) {
    OPENAI("OpenAI GPT-4", true),
    CLAUDE("Claude 3", true),
    GEMINI("Gemini Pro", true);
    
    companion object {
        fun fromString(name: String): LLMProvider {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OPENAI
        }
    }
}
