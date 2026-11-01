package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Process
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit

/** Instantiated by Shizuku, not an Android Service. Only fixed, documented actions are exposed. */
class ShellTasksService : IShellTasks.Stub {
    private var context: Context? = null
    private var overviewWatch: ShellOverviewWatch? = null
    constructor() : super()
    constructor(context: Context) : super() { this.context = context }

    override fun restoreOverlay(startIntent: android.app.PendingIntent?): Boolean {
        val token=Binder.clearCallingIdentity()
        return try {
            if (Process.myUid()!=2000 || startIntent==null ||
                startIntent.creatorPackage!="com.justinlin099.xperiarecentsrootless") return false
            if (Build.VERSION.SDK_INT>=31 && (!startIntent.isForegroundService || !startIntent.isImmutable)) return false
            if (Build.VERSION.SDK_INT>=31) {
                // Android limits background FGS starts even when Shizuku is already running.
                // A fixed 10-second shell temp allowlist permits this one app-owned start.
                // No permanent battery exemption or scheduled/polling work is installed.
                val p=ProcessBuilder("cmd","deviceidle","tempwhitelist","-u",
                    (startIntent.creatorUid/100000).toString(),"-d","10000",
                    "com.justinlin099.xperiarecentsrootless").redirectErrorStream(true).start()
                if (!p.waitFor(2,TimeUnit.SECONDS)) { p.destroyForcibly(); return false }
                if (p.exitValue()!=0) return false
            }
            startIntent.send()
            true // Dispatch only; the target rechecks saved intent and overlay permission.
        } catch (e: Throwable) {
            android.util.Log.w("XperiaClearAll","Shell restore dispatch failed",e)
            false
        } finally { Binder.restoreCallingIdentity(token) }
    }

    private fun granted(permission: String): Boolean = context?.checkPermission(
        "android.permission.$permission", Process.myPid(), Process.myUid()
    ) == PackageManager.PERMISSION_GRANTED

    private fun taskManager(): Any = Class.forName("android.app.ActivityTaskManager")
        .getMethod("getService").invoke(null) ?: error("ActivityTaskManager unavailable")

    private fun removalMethod() = Class.forName("android.app.IActivityTaskManager")
        .getMethod("removeAllVisibleRecentTasks")

    override fun probe(): String {
        val token = Binder.clearCallingIdentity()
        return try {
            val permissions = JSONObject()
            listOf("REMOVE_TASKS", "REAL_GET_TASKS", "START_TASKS_FROM_RECENTS", "MANAGE_ACTIVITY_TASKS",
                "STATUS_BAR_SERVICE", "WRITE_SECURE_SETTINGS", "MONITOR_INPUT", "READ_LOGS").forEach {
                permissions.put(it, granted(it))
            }
            JSONObject().put("uid", Process.myUid()).put("sdk", Build.VERSION.SDK_INT)
                .put("adbMode", Process.myUid() == 2000)
                .put("contextAvailable", context != null)
                .put("permissions", permissions)
                .put("sonyLauncher", launcherInfo())
                .put("clearApiPresent", runCatching { removalMethod(); taskManager(); true }.getOrDefault(false))
                .put("quickstepComponent", readQuickstepComponent())
                .put("note", "Read-only probe. Does not change Quickstep or remove tasks. ADB UID 2000 required.")
                .toString(2)
        } catch (e: Throwable) { failure(e) }
        finally { Binder.restoreCallingIdentity(token) }
    }

    private fun launcherInfo(): Any = runCatching {
        val info = context?.packageManager?.getApplicationInfo("com.sonymobile.launcher", 0)
            ?: return@runCatching "package context unavailable"
        JSONObject().put("uid", info.uid)
            .put("debuggable", info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .put("systemApp", info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
    }.getOrElse { "lookup unavailable: ${it.message}" }

    private fun readQuickstepComponent(): String = try {
        // Read only. Does not fabricate, enable or disable any resource overlay.
        val p = ProcessBuilder("cmd", "overlay", "lookup", "--user", "current",
            "com.android.systemui", "android:string/config_recentsComponentName")
            .redirectErrorStream(true).start()
        if (p.waitFor(3, TimeUnit.SECONDS)) p.inputStream.bufferedReader().readText().take(1500).trim()
        else { p.destroyForcibly(); "lookup timed out" }
    } catch (e: Throwable) { "lookup unavailable: ${e.javaClass.simpleName}: ${e.message}" }

    override fun clearTasks(): String {
        val token = Binder.clearCallingIdentity()
        return try {
            check(Process.myUid() == 2000) { "Only ADB uid=2000 is accepted. Start Shizuku with wireless debugging." }
            check(granted("REMOVE_TASKS")) { "Shell is missing REMOVE_TASKS." }
            removalMethod().invoke(taskManager())
            JSONObject().put("ok", true).put("uid", Process.myUid())
                .put("message", "System clear API returned. Verify the visible recent tasks.")
                .put("api", "IActivityTaskManager.removeAllVisibleRecentTasks").toString(2)
        } catch (e: Throwable) { failure(e) }
        finally { Binder.restoreCallingIdentity(token) }
    }

    private fun failure(error: Throwable): String {
        val cause = if (error is InvocationTargetException) error.targetException else error
        return JSONObject().put("ok", false).put("uid", Process.myUid())
            .put("error", cause.javaClass.name).put("message", cause.message ?: "unknown").toString(2)
    }

    @Synchronized override fun startOverviewWatch(listener: IOverviewStateListener): String {
        val callerUid=Binder.getCallingUid()
        val token = Binder.clearCallingIdentity()
        return try {
            check(Process.myUid() == 2000) { "Only ADB uid=2000 is accepted." }
            check(granted("MANAGE_ACTIVITY_TASKS")) { "Missing MANAGE_ACTIVITY_TASKS" }
            check(granted("REAL_GET_TASKS")) { "Missing REAL_GET_TASKS; cannot query recent tasks" }
            overviewWatch?.close()
            val watch = ShellOverviewWatch(checkNotNull(context) { "Worker Context unavailable" })
            overviewWatch = watch
            val notification=runCatching { ShellServiceNotification.hide(checkNotNull(context),callerUid) }
            JSONObject(watch.start(listener)).apply {
                put("serviceNotificationHidden",notification.getOrDefault(false))
                notification.exceptionOrNull()?.let { put("serviceNotificationError",it.toString()) }
            }.toString()
        } catch (e: Throwable) { overviewWatch?.close(); overviewWatch = null; failure(e) }
        finally { Binder.restoreCallingIdentity(token) }
    }

    @Synchronized override fun stopOverviewWatch() {
        val token = Binder.clearCallingIdentity()
        try { overviewWatch?.close(); overviewWatch = null }
        finally { Binder.restoreCallingIdentity(token) }
    }

    override fun destroy() { stopOverviewWatch(); kotlin.system.exitProcess(0) }
}
