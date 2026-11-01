package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Help appears only after the user has visited the overlay permission screen. */
internal object RestrictedSettingsStep {
    private const val OVERLAY_VISITED="overlaySettingsVisited"
    fun visitOverlay(c:Context) { c.getSharedPreferences("rootless",0).edit().putBoolean(OVERLAY_VISITED,true).apply() }
    fun needsReview(c:Context)=Build.VERSION.SDK_INT>=33 && !Settings.canDrawOverlays(c) &&
        c.getSharedPreferences("rootless",0).getBoolean(OVERLAY_VISITED,false)
    fun appInfo(c:Context)=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))
}
