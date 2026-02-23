package com.phoneclaw.ai.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.phoneclaw.ai.databinding.ActivityEditProviderBinding
import com.phoneclaw.ai.domain.model.CustomProvider
import com.phoneclaw.ai.util.PreferencesManager

class EditProviderActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEditProviderBinding
    private lateinit var preferencesManager: PreferencesManager
    
    private var editingProviderId: String? = null
    private var isEditing = false
    
    companion object {
        const val EXTRA_PROVIDER_ID = "provider_id"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        
        editingProviderId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        isEditing = editingProviderId != null
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isEditing) "Edit Provider" else "Add Provider"
        
        setupPresetSpinner()
        setupListeners()
        
        if (isEditing) {
            loadExistingProvider()
        }
    }
    
    private fun setupPresetSpinner() {
        val presetNames = listOf("-- Select a preset --") + CustomProvider.PRESETS.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.presetSpinner.adapter = adapter
        
        binding.presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val preset = CustomProvider.PRESETS[position - 1]
                    applyPreset(preset)
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun applyPreset(preset: CustomProvider) {
        binding.nameInput.setText(preset.name)
        binding.baseUrlInput.setText(preset.baseUrl)
        binding.modelInput.setText(preset.modelName)
        binding.apiKeyHeaderInput.setText(preset.apiKeyHeader)
        binding.apiKeyPrefixInput.setText(preset.apiKeyPrefix)
        binding.supportsVisionSwitch.isChecked = preset.supportsVision
        // Don't copy API key from preset
    }
    
    private fun loadExistingProvider() {
        val provider = preferencesManager.getCustomProviderById(editingProviderId!!)
        if (provider != null) {
            binding.nameInput.setText(provider.name)
            binding.baseUrlInput.setText(provider.baseUrl)
            binding.modelInput.setText(provider.modelName)
            binding.apiKeyInput.setText(provider.apiKey)
            binding.apiKeyHeaderInput.setText(provider.apiKeyHeader)
            binding.apiKeyPrefixInput.setText(provider.apiKeyPrefix)
            binding.supportsVisionSwitch.isChecked = provider.supportsVision
            
            binding.btnDelete.visibility = View.VISIBLE
        } else {
            Toast.makeText(this, "Provider not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveProvider()
        }
        
        binding.btnDelete.setOnClickListener {
            confirmDelete()
        }
    }
    
    private fun saveProvider() {
        val name = binding.nameInput.text?.toString()?.trim() ?: ""
        val baseUrl = binding.baseUrlInput.text?.toString()?.trim() ?: ""
        val modelName = binding.modelInput.text?.toString()?.trim() ?: ""
        val apiKey = binding.apiKeyInput.text?.toString() ?: ""
        val apiKeyHeader = binding.apiKeyHeaderInput.text?.toString() ?: ""
        val apiKeyPrefix = binding.apiKeyPrefixInput.text?.toString() ?: ""
        val supportsVision = binding.supportsVisionSwitch.isChecked
        
        // Validation
        if (name.isBlank()) {
            binding.nameLayout.error = "Name is required"
            return
        }
        binding.nameLayout.error = null
        
        if (baseUrl.isBlank()) {
            binding.baseUrlLayout.error = "Base URL is required"
            return
        }
        binding.baseUrlLayout.error = null
        
        if (modelName.isBlank()) {
            binding.modelLayout.error = "Model name is required"
            return
        }
        binding.modelLayout.error = null
        
        val provider = CustomProvider(
            id = editingProviderId ?: java.util.UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            modelName = modelName,
            apiKey = apiKey,
            apiKeyHeader = apiKeyHeader,
            apiKeyPrefix = apiKeyPrefix,
            supportsVision = supportsVision
        )
        
        if (isEditing) {
            preferencesManager.updateCustomProvider(provider)
            Toast.makeText(this, "Provider updated", Toast.LENGTH_SHORT).show()
        } else {
            preferencesManager.addCustomProvider(provider)
            Toast.makeText(this, "Provider added", Toast.LENGTH_SHORT).show()
        }
        
        setResult(RESULT_OK)
        finish()
    }
    
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Provider")
            .setMessage("Are you sure you want to delete this provider?")
            .setPositiveButton("Delete") { _, _ ->
                editingProviderId?.let { preferencesManager.deleteCustomProvider(it) }
                Toast.makeText(this, "Provider deleted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
