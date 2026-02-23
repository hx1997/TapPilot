package com.phoneclaw.ai.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.phoneclaw.ai.domain.model.CustomProvider
import com.phoneclaw.ai.domain.model.LLMProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ai_automation_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val gson = Gson()
    
    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()
    
    var claudeApiKey: String
        get() = prefs.getString(KEY_CLAUDE_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLAUDE_API_KEY, value).apply()
    
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()
    
    var selectedProvider: LLMProvider
        get() = LLMProvider.fromString(prefs.getString(KEY_SELECTED_PROVIDER, LLMProvider.OPENAI.name) ?: LLMProvider.OPENAI.name)
        set(value) = prefs.edit().putString(KEY_SELECTED_PROVIDER, value.name).apply()
    
    // Custom provider support
    var selectedCustomProviderId: String?
        get() = prefs.getString(KEY_SELECTED_CUSTOM_PROVIDER_ID, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_CUSTOM_PROVIDER_ID, value).apply()
    
    var useCustomProvider: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_PROVIDER, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_PROVIDER, value).apply()
    
    var customProviders: List<CustomProvider>
        get() {
            val json = prefs.getString(KEY_CUSTOM_PROVIDERS, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<CustomProvider>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString(KEY_CUSTOM_PROVIDERS, json).apply()
        }
    
    fun addCustomProvider(provider: CustomProvider) {
        val current = customProviders.toMutableList()
        current.add(provider)
        customProviders = current
    }
    
    fun updateCustomProvider(provider: CustomProvider) {
        val current = customProviders.toMutableList()
        val index = current.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            current[index] = provider
            customProviders = current
        }
    }
    
    fun deleteCustomProvider(providerId: String) {
        customProviders = customProviders.filter { it.id != providerId }
        if (selectedCustomProviderId == providerId) {
            selectedCustomProviderId = null
            useCustomProvider = false
        }
    }
    
    fun getCustomProviderById(id: String): CustomProvider? {
        return customProviders.find { it.id == id }
    }
    
    fun getSelectedCustomProvider(): CustomProvider? {
        val id = selectedCustomProviderId ?: return null
        return getCustomProviderById(id)
    }
    
    var executionSpeed: Int
        get() = prefs.getInt(KEY_EXECUTION_SPEED, 500)
        set(value) = prefs.edit().putInt(KEY_EXECUTION_SPEED, value).apply()
    
    var maxExecutionSteps: Int
        get() = prefs.getInt(KEY_MAX_EXECUTION_STEPS, 20)
        set(value) = prefs.edit().putInt(KEY_MAX_EXECUTION_STEPS, value).apply()
    
    var enableScreenshots: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SCREENSHOTS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SCREENSHOTS, value).apply()
    
    fun getApiKeyForProvider(provider: LLMProvider): String {
        return when (provider) {
            LLMProvider.OPENAI -> openAiApiKey
            LLMProvider.CLAUDE -> claudeApiKey
            LLMProvider.GEMINI -> geminiApiKey
        }
    }
    
    fun hasApiKeyForProvider(provider: LLMProvider): Boolean {
        return getApiKeyForProvider(provider).isNotBlank()
    }
    
    fun hasValidProviderConfigured(): Boolean {
        if (useCustomProvider) {
            val custom = getSelectedCustomProvider()
            return custom != null && (custom.apiKey.isNotBlank() || custom.apiKeyHeader.isBlank())
        }
        return hasApiKeyForProvider(selectedProvider)
    }
    
    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_SELECTED_CUSTOM_PROVIDER_ID = "selected_custom_provider_id"
        private const val KEY_USE_CUSTOM_PROVIDER = "use_custom_provider"
        private const val KEY_CUSTOM_PROVIDERS = "custom_providers"
        private const val KEY_EXECUTION_SPEED = "execution_speed"
        private const val KEY_MAX_EXECUTION_STEPS = "max_execution_steps"
        private const val KEY_ENABLE_SCREENSHOTS = "enable_screenshots"
    }
}
