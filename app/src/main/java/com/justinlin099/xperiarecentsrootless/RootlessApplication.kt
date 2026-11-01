package com.justinlin099.xperiarecentsrootless

import android.app.Application
import android.content.Context
import rikka.shizuku.ShizukuProvider

class RootlessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)
        BootRecovery.processStarted(this)
    }
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // This build deliberately verifies ADB identity even on a rooted test phone.
        // Do not silently choose Sui's root backend instead of wireless-debug Shizuku.
        ShizukuProvider.disableAutomaticSuiInitialization()
    }
}
