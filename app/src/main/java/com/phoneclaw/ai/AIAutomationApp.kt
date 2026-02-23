package com.phoneclaw.ai

import android.app.Application
import timber.log.Timber

class AIAutomationApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
