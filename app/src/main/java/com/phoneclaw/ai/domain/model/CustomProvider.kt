package com.phoneclaw.ai.domain.model

import java.util.UUID

data class CustomProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val supportsVision: Boolean = true,
    val apiKeyHeader: String = "Authorization",
    val apiKeyPrefix: String = "Bearer ",
    val isEnabled: Boolean = true
) {
    companion object {
        // Common presets for easy setup
        val PRESET_OPENAI_COMPATIBLE = CustomProvider(
            id = "preset_openai",
            name = "OpenAI Compatible",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "",
            modelName = "gpt-4o",
            apiKeyHeader = "Authorization",
            apiKeyPrefix = "Bearer "
        )
        
        val PRESET_OLLAMA = CustomProvider(
            id = "preset_ollama",
            name = "Ollama (Local)",
            baseUrl = "http://localhost:11434/v1",
            apiKey = "",
            modelName = "llama3",
            supportsVision = false,
            apiKeyHeader = "",
            apiKeyPrefix = ""
        )
        
        val PRESET_LM_STUDIO = CustomProvider(
            id = "preset_lmstudio",
            name = "LM Studio (Local)",
            baseUrl = "http://localhost:1234/v1",
            apiKey = "",
            modelName = "local-model",
            supportsVision = false,
            apiKeyHeader = "",
            apiKeyPrefix = ""
        )
        
        val PRESET_DEEPSEEK = CustomProvider(
            id = "preset_deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "",
            modelName = "deepseek-chat",
            apiKeyHeader = "Authorization",
            apiKeyPrefix = "Bearer "
        )
        
        val PRESET_GROQ = CustomProvider(
            id = "preset_groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "",
            modelName = "llama-3.3-70b-versatile",
            supportsVision = false,
            apiKeyHeader = "Authorization",
            apiKeyPrefix = "Bearer "
        )
        
        val PRESETS = listOf(
            PRESET_OPENAI_COMPATIBLE,
            PRESET_OLLAMA,
            PRESET_LM_STUDIO,
            PRESET_DEEPSEEK,
            PRESET_GROQ
        )
    }
    
    fun toDisplayString(): String = "$name ($modelName)"
}
