package com.phoneclaw.ai.presentation.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phoneclaw.ai.R
import com.phoneclaw.ai.data.repository.LLMRepositoryImpl
import com.phoneclaw.ai.databinding.ActivityMainBinding
import com.phoneclaw.ai.domain.usecase.ExecuteTaskUseCase
import com.phoneclaw.ai.domain.usecase.PlanTaskUseCase
import com.phoneclaw.ai.presentation.settings.SettingsActivity
import com.phoneclaw.ai.service.AutomationAccessibilityService
import com.phoneclaw.ai.service.ScreenAnalyzer
import com.phoneclaw.ai.service.executor.ActionExecutor
import com.phoneclaw.ai.util.PreferencesManager
import com.phoneclaw.ai.util.ScreenshotCapture
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: MainViewModel
    private lateinit var screenshotCapture: ScreenshotCapture
    
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenshotCapture.setMediaProjectionData(result.resultCode, result.data!!)
            Toast.makeText(this, "Screen capture permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        screenshotCapture = ScreenshotCapture(this)
        
        initializeViewModel()
        setupUI()
        observeState()
    }
    
    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }
    
    private fun initializeViewModel() {
        val llmRepository = LLMRepositoryImpl(preferencesManager)
        val screenAnalyzer = ScreenAnalyzer()
        val actionExecutor = ActionExecutor()
        
        val planTaskUseCase = PlanTaskUseCase(
            llmRepository = llmRepository,
            screenAnalyzer = screenAnalyzer,
            screenshotCapture = if (preferencesManager.enableScreenshots) screenshotCapture else null
        )
        
        val executeTaskUseCase = ExecuteTaskUseCase(
            actionExecutor = actionExecutor,
            executionDelay = preferencesManager.executionSpeed.toLong()
        )
        
        viewModel = MainViewModel(planTaskUseCase, executeTaskUseCase, preferencesManager)
    }
    
    private fun setupUI() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.btnExecute.setOnClickListener {
            when (viewModel.state.value) {
                is TaskExecutionState.Executing, is TaskExecutionState.Planning -> {
                    viewModel.cancelTask()
                }
                else -> {
                    executeTask()
                }
            }
        }
    }
    
    private fun executeTask() {
        val taskDescription = binding.taskInput.text?.toString()?.trim() ?: ""
        
        if (taskDescription.isBlank()) {
            Toast.makeText(this, getString(R.string.error_empty_task), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityPermissionDialog()
            return
        }
        
        if (!preferencesManager.hasValidProviderConfigured()) {
            Toast.makeText(this, getString(R.string.error_no_api_key), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        
        // Request screen capture permission if not granted and screenshots are enabled
        if (preferencesManager.enableScreenshots && !ScreenshotCapture.hasPermission()) {
            requestScreenCapturePermission()
            return
        }
        
        viewModel.executeTask(taskDescription)
    }
    
    private fun observeState() {
        viewModel.state.onEach { state ->
            updateUIForState(state)
        }.launchIn(lifecycleScope)
        
        viewModel.logs.onEach { logs ->
            binding.logText.text = logs.joinToString("\n")
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }.launchIn(lifecycleScope)
    }
    
    private fun updateUIForState(state: TaskExecutionState) {
        when (state) {
            is TaskExecutionState.Idle -> {
                binding.statusText.text = getString(R.string.status_idle)
                binding.progressBar.visibility = android.view.View.GONE
                binding.currentStepText.visibility = android.view.View.GONE
                binding.btnExecute.text = getString(R.string.btn_execute)
                binding.taskInput.isEnabled = true
            }
            is TaskExecutionState.Planning -> {
                binding.statusText.text = getString(R.string.status_planning)
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.currentStepText.visibility = android.view.View.GONE
                binding.btnExecute.text = getString(R.string.btn_cancel)
                binding.taskInput.isEnabled = false
            }
            is TaskExecutionState.Executing -> {
                binding.statusText.text = getString(R.string.status_executing, state.currentStep, state.totalSteps)
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.currentStepText.visibility = android.view.View.VISIBLE
                binding.currentStepText.text = state.currentAction?.let { formatAction(it) } ?: ""
                binding.btnExecute.text = getString(R.string.btn_cancel)
                binding.taskInput.isEnabled = false
            }
            is TaskExecutionState.Success -> {
                binding.statusText.text = getString(R.string.status_completed)
                binding.progressBar.visibility = android.view.View.GONE
                binding.currentStepText.visibility = android.view.View.GONE
                binding.btnExecute.text = getString(R.string.btn_execute)
                binding.taskInput.isEnabled = true
                viewModel.resetState()
            }
            is TaskExecutionState.Error -> {
                binding.statusText.text = getString(R.string.status_failed, state.message)
                binding.progressBar.visibility = android.view.View.GONE
                binding.currentStepText.visibility = android.view.View.GONE
                binding.btnExecute.text = getString(R.string.btn_execute)
                binding.taskInput.isEnabled = true
            }
            is TaskExecutionState.Cancelled -> {
                binding.statusText.text = getString(R.string.status_cancelled)
                binding.progressBar.visibility = android.view.View.GONE
                binding.currentStepText.visibility = android.view.View.GONE
                binding.btnExecute.text = getString(R.string.btn_execute)
                binding.taskInput.isEnabled = true
            }
        }
    }
    
    private fun formatAction(action: com.phoneclaw.ai.domain.model.ActionStep): String {
        return when (action) {
            is com.phoneclaw.ai.domain.model.ActionStep.Click -> 
                "Clicking on ${action.description ?: "position"}"
            is com.phoneclaw.ai.domain.model.ActionStep.Type -> 
                "Typing text..."
            is com.phoneclaw.ai.domain.model.ActionStep.Scroll -> 
                "Scrolling ${action.direction.name.lowercase()}"
            is com.phoneclaw.ai.domain.model.ActionStep.Swipe -> 
                "Swiping..."
            is com.phoneclaw.ai.domain.model.ActionStep.Wait -> 
                "Waiting ${action.milliseconds}ms"
            is com.phoneclaw.ai.domain.model.ActionStep.LaunchApp -> 
                "Launching app..."
            is com.phoneclaw.ai.domain.model.ActionStep.PressBack -> 
                "Pressing back"
            is com.phoneclaw.ai.domain.model.ActionStep.PressHome -> 
                "Pressing home"
            is com.phoneclaw.ai.domain.model.ActionStep.Screenshot -> 
                "Taking screenshot"
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName 
        }
    }
    
    private fun updateAccessibilityStatus() {
        // Could update UI to show accessibility status
        isAccessibilityServiceEnabled()
    }
    
    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_permission_title))
            .setMessage(getString(R.string.accessibility_permission_message))
            .setPositiveButton(getString(R.string.btn_enable_accessibility)) { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun requestScreenCapturePermission() {
        screenCaptureLauncher.launch(screenshotCapture.getScreenCaptureIntent())
    }
}
