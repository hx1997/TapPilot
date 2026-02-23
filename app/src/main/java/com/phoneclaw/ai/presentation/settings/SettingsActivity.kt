package com.phoneclaw.ai.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.phoneclaw.ai.R
import com.phoneclaw.ai.databinding.ActivitySettingsBinding
import com.phoneclaw.ai.domain.model.CustomProvider
import com.phoneclaw.ai.domain.model.LLMProvider
import com.phoneclaw.ai.util.PreferencesManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferencesManager: PreferencesManager
    
    private var customProviders = listOf<CustomProvider>()
    
    private val editProviderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshCustomProviders()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        
        preferencesManager = PreferencesManager(this)
        
        loadSettings()
        setupListeners()
        refreshCustomProviders()
    }
    
    private fun loadSettings() {
        // Load provider selection
        if (preferencesManager.useCustomProvider) {
            binding.radioCustom.isChecked = true
            binding.customProviderSection.visibility = View.VISIBLE
        } else {
            when (preferencesManager.selectedProvider) {
                LLMProvider.OPENAI -> binding.radioOpenAI.isChecked = true
                LLMProvider.CLAUDE -> binding.radioClaude.isChecked = true
                LLMProvider.GEMINI -> binding.radioGemini.isChecked = true
            }
            binding.customProviderSection.visibility = View.GONE
        }
        
        // Load API keys (show masked if set)
        if (preferencesManager.openAiApiKey.isNotBlank()) {
            binding.openAiKeyInput.setText(preferencesManager.openAiApiKey)
        }
        if (preferencesManager.claudeApiKey.isNotBlank()) {
            binding.claudeKeyInput.setText(preferencesManager.claudeApiKey)
        }
        if (preferencesManager.geminiApiKey.isNotBlank()) {
            binding.geminiKeyInput.setText(preferencesManager.geminiApiKey)
        }
        
        // Load options
        binding.switchScreenshots.isChecked = preferencesManager.enableScreenshots
        binding.speedSeekBar.progress = preferencesManager.executionSpeed
        updateSpeedLabel(preferencesManager.executionSpeed)
        
        // Load max execution steps
        binding.maxStepsSeekBar.progress = preferencesManager.maxExecutionSteps
        updateMaxStepsLabel(preferencesManager.maxExecutionSteps)
    }
    
    private fun setupListeners() {
        // Provider radio group
        binding.providerRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isCustom = checkedId == R.id.radioCustom
            binding.customProviderSection.visibility = if (isCustom) View.VISIBLE else View.GONE
        }
        
        // Custom provider spinner
        binding.customProviderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Position 0 is "Select provider", actual providers start at 1
                // Position after providers is "Edit..." options
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Add provider button
        binding.btnAddProvider.setOnClickListener {
            val intent = Intent(this, EditProviderActivity::class.java)
            editProviderLauncher.launch(intent)
        }
        
        // Speed seekbar
        binding.speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSpeedLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Max steps seekbar
        binding.maxStepsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Ensure minimum of 5 steps
                val steps = maxOf(5, progress)
                updateMaxStepsLabel(steps)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun refreshCustomProviders() {
        customProviders = preferencesManager.customProviders
        
        val spinnerItems = mutableListOf<String>()
        
        if (customProviders.isEmpty()) {
            spinnerItems.add("No custom providers")
        } else {
            customProviders.forEach { provider ->
                spinnerItems.add(provider.toDisplayString())
            }
        }
        
        // Add edit options for existing providers
        if (customProviders.isNotEmpty()) {
            spinnerItems.add("──────────")
            customProviders.forEach { provider ->
                spinnerItems.add("Edit: ${provider.name}")
            }
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.customProviderSpinner.adapter = adapter
        
        // Select current provider
        val selectedId = preferencesManager.selectedCustomProviderId
        if (selectedId != null) {
            val index = customProviders.indexOfFirst { it.id == selectedId }
            if (index >= 0) {
                binding.customProviderSpinner.setSelection(index)
            }
        }
        
        // Handle spinner item selection
        binding.customProviderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                handleSpinnerSelection(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun handleSpinnerSelection(position: Int) {
        if (customProviders.isEmpty()) return
        
        // Check if it's an edit option
        val providerCount = customProviders.size
        val editStartIndex = providerCount + 1 // +1 for separator
        
        if (position >= editStartIndex) {
            val editIndex = position - editStartIndex
            if (editIndex < customProviders.size) {
                val provider = customProviders[editIndex]
                val intent = Intent(this, EditProviderActivity::class.java)
                intent.putExtra(EditProviderActivity.EXTRA_PROVIDER_ID, provider.id)
                editProviderLauncher.launch(intent)
                
                // Reset to actual provider selection
                val selectedId = preferencesManager.selectedCustomProviderId
                val currentIndex = customProviders.indexOfFirst { it.id == selectedId }
                if (currentIndex >= 0) {
                    binding.customProviderSpinner.setSelection(currentIndex)
                }
            }
        }
    }
    
    private fun updateSpeedLabel(progress: Int) {
        binding.speedLabel.text = "${progress}ms delay between actions"
    }
    
    private fun updateMaxStepsLabel(steps: Int) {
        binding.maxStepsLabel.text = "$steps steps maximum"
    }
    
    private fun saveSettings() {
        // Save provider selection
        val isCustom = binding.radioCustom.isChecked
        preferencesManager.useCustomProvider = isCustom
        
        if (isCustom) {
            // Save custom provider selection
            val spinnerPosition = binding.customProviderSpinner.selectedItemPosition
            if (spinnerPosition < customProviders.size && customProviders.isNotEmpty()) {
                preferencesManager.selectedCustomProviderId = customProviders[spinnerPosition].id
            } else {
                Toast.makeText(this, "Please add a custom provider first", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            // Save built-in provider
            val selectedProvider = when {
                binding.radioOpenAI.isChecked -> LLMProvider.OPENAI
                binding.radioClaude.isChecked -> LLMProvider.CLAUDE
                binding.radioGemini.isChecked -> LLMProvider.GEMINI
                else -> LLMProvider.OPENAI
            }
            preferencesManager.selectedProvider = selectedProvider
        }
        
        // Save API keys
        val openAiKey = binding.openAiKeyInput.text?.toString() ?: ""
        val claudeKey = binding.claudeKeyInput.text?.toString() ?: ""
        val geminiKey = binding.geminiKeyInput.text?.toString() ?: ""
        
        if (openAiKey.isNotBlank()) {
            preferencesManager.openAiApiKey = openAiKey
        }
        if (claudeKey.isNotBlank()) {
            preferencesManager.claudeApiKey = claudeKey
        }
        if (geminiKey.isNotBlank()) {
            preferencesManager.geminiApiKey = geminiKey
        }
        
        // Save options
        preferencesManager.enableScreenshots = binding.switchScreenshots.isChecked
        preferencesManager.executionSpeed = binding.speedSeekBar.progress
        preferencesManager.maxExecutionSteps = maxOf(5, binding.maxStepsSeekBar.progress)
        
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
