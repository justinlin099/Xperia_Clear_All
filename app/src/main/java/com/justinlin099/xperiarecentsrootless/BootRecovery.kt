package com.justinlin099.xperiarecentsrootless

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager

/** Only the enabled bit and a bounded boot journal are device-protected, never appearance data. */
internal object BootRecovery {
    private var unlockReceiver: BroadcastReceiver? = null
    fun storage(c: Context) = c.createDeviceProtectedStorageContext().getSharedPreferences("startup",0)
    fun unlocked(c: Context) = c.getSystemService(UserManager::class.java).isUserUnlocked
    @Synchronized fun event(c: Context,message:String) {
        val prefs=storage(c)
        val lines=prefs.getString("bootJournal","")!!.lines().filter { it.isNotEmpty() }
        val line="${System.currentTimeMillis()} uptime=${SystemClock.elapsedRealtime()} $message"
        prefs.edit().putString("bootJournal",(lines+line).takeLast(24).joinToString("\n")).commit()
    }
    fun processStarted(c: Context) {
        event(c,if(unlocked(c)) "PROCESS_UNLOCKED" else "PROCESS_LOCKED")
        if(unlocked(c)) initialize(c) else waitForUnlock(c)
    }
    private fun initialize(c: Context) {
        PositionEditing.finish(c.getSharedPreferences("rootless",0))
        RootlessConnection.init(c)
        event(c,"INITIALIZED enabled=${AutomaticStart.enabled(c)}")
    }
    fun afterBoot(c:Context,action:String) {
        event(c,"RECEIVED $action enabled=${AutomaticStart.enabled(c)} unlocked=${unlocked(c)}")
        if(!unlocked(c)) { waitForUnlock(c); return }
        initialize(c)
        AutomaticStart.restore(c,"broadcast:$action")
    }
    private fun waitForUnlock(c:Context) {
        if(unlockReceiver!=null) return
        val app=c.applicationContext
        val receiver=object:BroadcastReceiver() {
            override fun onReceive(context:Context,intent:Intent) {
                if(intent.action!=Intent.ACTION_USER_UNLOCKED) return
                runCatching { app.unregisterReceiver(this) };unlockReceiver=null
                afterBoot(app,Intent.ACTION_USER_UNLOCKED)
            }
        }
        unlockReceiver=receiver
        if(Build.VERSION.SDK_INT>=33) app.registerReceiver(receiver,IntentFilter(Intent.ACTION_USER_UNLOCKED),Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(receiver,IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // Close the race if unlock occurred between the initial check and registration.
        if(unlocked(app)) { app.unregisterReceiver(receiver);unlockReceiver=null;afterBoot(app,Intent.ACTION_USER_UNLOCKED) }
    }
    fun diagnostics(c:Context):String {
        val restricted=c.getSystemService(ActivityManager::class.java).isBackgroundRestricted
        val exempt=c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
        return "Background restricted: $restricted\nBattery exempt: $exempt\nBoot journal:\n"+storage(c).getString("bootJournal","none")
    }
}
