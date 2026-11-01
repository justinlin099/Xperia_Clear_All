package com.justinlin099.xperiarecentsrootless

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Persist user intent, independently of the service/process lifetime. No polling or alarms. */
internal object AutomaticStart {
    const val KEY = "monitoringEnabled"
    const val RESTORE = "restore-shell-overlay"
    fun enabled(context: Context) = BootRecovery.storage(context).getBoolean(KEY,false)
    fun explicitlyDisabled(context:Context)=BootRecovery.storage(context).contains(KEY) && !enabled(context)
    fun remember(context: Context, enabled: Boolean) {
        BootRecovery.storage(context).edit().putBoolean(KEY,enabled).commit()
        if(!enabled && BootRecovery.unlocked(context)) {
            RootlessConnection.cancelRecovery()
            RecoveryNotifications.clear(context)
        }
    }
    /** Only a fresh setup may choose the initial on state; an explicit off always wins. */
    fun activateAfterSetup(context:Context,complete:Boolean):Boolean {
        if(!complete || BootRecovery.storage(context).contains(KEY)) return false
        remember(context,true)
        if(ShellOverlayService.active!=null || restore(context,"setup-complete")) return true
        remember(context,false)
        return false
    }
    private fun needed(context: Context) = BootRecovery.unlocked(context) && enabled(context) && Settings.canDrawOverlays(context) && ShellOverlayService.active == null
    private fun record(context: Context, event: String) {
        context.getSharedPreferences("rootless",0).edit()
            .putString("startupEvent","${System.currentTimeMillis()} $event").apply()
    }
    fun serviceEvent(context: Context,event:String) {
        context.getSharedPreferences("rootless",0).edit()
            .putString("startupServiceEvent","${System.currentTimeMillis()} $event").apply()
    }
    fun restoreFromShizuku(context: Context, service: IShellTasks): Boolean {
        if (!needed(context)) return false
        // Immutable, explicit, app-owned capability: the private service stays unexported.
        val pending = PendingIntent.getForegroundService(context,170,
            Intent(context,ShellOverlayService::class.java).setAction(RESTORE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching {
            val sent=service.restoreOverlay(pending)
            record(context,if(sent) "SHIZUKU_RESTORE_SENT" else "SHIZUKU_RESTORE_REJECTED")
            sent
        }.getOrElse {
            record(context,"SHIZUKU_RESTORE_ERROR ${it.javaClass.simpleName}")
            android.util.Log.w("XperiaClearAll","Shizuku restore failed",it)
            false
        }
    }
    fun restore(context: Context,source:String="direct"): Boolean {
        if (!needed(context)) {
            BootRecovery.event(context,"RESTORE_SKIPPED source=$source unlocked=${BootRecovery.unlocked(context)} enabled=${enabled(context)} active=${ShellOverlayService.active!=null}")
            return false
        }
        BootRecovery.event(context,"RESTORE_ATTEMPT source=$source")
        return runCatching {
            context.startForegroundService(Intent(context,ShellOverlayService::class.java).setAction(RESTORE))
            record(context,"DIRECT_RESTORE_SENT source=$source")
            BootRecovery.event(context,"RESTORE_SENT source=$source")
            true
        }.getOrElse {
            record(context,"DIRECT_RESTORE_ERROR ${it.javaClass.simpleName} source=$source")
            BootRecovery.event(context,"RESTORE_ERROR source=$source ${it.javaClass.simpleName}")
            android.util.Log.w("XperiaClearAll","Automatic service restore failed",it)
            false
        }
    }
}
