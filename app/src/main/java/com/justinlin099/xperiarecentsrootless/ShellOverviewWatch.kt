package com.justinlin099.xperiarecentsrootless

import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lives exclusively in the Shizuku shell process. No accessibility, screenshots or polling. */
internal class ShellOverviewWatch(private val context: Context) {
    private val thread = HandlerThread("sony-overview-events").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var stopped = false
    private var callback: IOverviewStateListener? = null
    private var process: java.lang.Process? = null
    private var taskListener: Any? = null
    private val death = IBinder.DeathRecipient { close() }
    private var manager: Any? = null
    @Volatile private var overviewRequested = false
    private val target = "com.sonymobile.launcher"
    private val taskEvents = setOf("onTaskStackChanged", "onTaskMovedToFront", "onTaskFocusChanged", "onTaskRemoved", "onRecentTaskListUpdated")

    private fun publish(visible: Boolean, state: String, detail: String) {
        // A repeat entry can restore an overlay hidden by screen-off in the app process.
        if (!stopped) runCatching { callback?.onState(visible, state, detail) }
    }

    fun start(listener: IOverviewStateListener): String {
        callback = listener
        listener.asBinder().linkToDeath(death, 0)
        try {
            registerTaskEvents()
            val uid = context.packageManager.getApplicationInfo(target, 0).uid
            val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            // logd filters by Sony UID and this single tag before data reaches our parser.
            // Start now: never replay old Overview lines from the ring buffer.
            process = ProcessBuilder("logcat", "-b", "main", "--uid=$uid", "-v", "brief", "-T", since,
                "LauncherStateManager:D", "TaskView:D", "*:S").redirectErrorStream(true).start()
            val stream = process!!.inputStream
            Thread({
                try {
                    val visibility = LauncherVisibilityTracker()
                    stream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (stopped) return@forEach
                            val event = visibility.accept(line) ?: return@forEach
                            handler.post {
                                overviewRequested = event.visible
                                if (event.visible) publishTaskPresence(event.state)
                                else publish(false, event.state, "Launcher state / task launch event")
                            }
                        }
                    }
                    if (!stopped) publish(false, "STREAM_ENDED", "State stream ended; restart monitoring")
                } catch (e: Throwable) {
                    if (!stopped) publish(false, "STREAM_ERROR", "State stream error: ${e.javaClass.simpleName}")
                }
            }, "sony-state-log-reader").apply { isDaemon = true; start() }
            return JSONObject().put("ok", true).put("uid", android.os.Process.myUid())
                .put("watch", "Sony UID + LauncherStateManager / TaskView")
                .put("taskListener", true).put("polling", false)
                .put("message", "Monitoring started. Return home, then open Recents to verify state events.").toString(2)
        } catch (e: Throwable) { close(); throw e }
    }

    private fun registerTaskEvents() {
        val iface = Class.forName("android.app.ITaskStackListener")
        val stub = Class.forName("android.app.ITaskStackListener\$Stub")
        val nameOf = stub.getMethod("getDefaultTransactionName", Int::class.javaPrimitiveType)
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString("android.app.ITaskStackListener"); return true
                }
                if (code !in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) {
                    return super.onTransact(code, data, reply, flags)
                }
                data.enforceInterface("android.app.ITaskStackListener")
                // Sony log entry queries task presence itself. Outside overview there is no overlay to hide.
                if (!overviewRequested || stopped) return true
                val name = nameOf.invoke(null, code) as? String
                if (name in taskEvents) {
                    handler.removeCallbacks(checkFocus)
                    handler.post(checkFocus)
                }
                // No task snapshots, intent contents or other app information are decoded.
                return true
            }
        }
        val listener = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
        Class.forName("android.app.IActivityTaskManager").getMethod("registerTaskStackListener", iface)
            .invoke(atm, listener)
        manager = atm
        taskListener = listener
    }

    private val checkFocus = Runnable {
        if (!stopped && overviewRequested) {
            val token = Binder.clearCallingIdentity()
            try {
                val info = Class.forName("android.app.IActivityTaskManager")
                    .getMethod("getFocusedRootTaskInfo").invoke(manager)
                val top = info?.javaClass?.getField("topActivity")?.get(info) as? ComponentName
                if (top?.packageName != target) {
                    overviewRequested = false
                    publish(false, "OUTSIDE_SONY", "Launcher left foreground; hiding overlay")
                } else if (overviewRequested) publishTaskPresence("TASK_LIST_CHANGED")
            } catch (e: Throwable) {
                publish(false, "FOCUS_ERROR", "Focus check failed; overlay hidden: ${e.javaClass.simpleName}")
            } finally { Binder.restoreCallingIdentity(token) }
        }
    }

    private fun publishTaskPresence(state: String) {
        if (stopped || !overviewRequested) return
        try {
            val count = RecentTaskReader.count(checkNotNull(manager))
            publish(count > 0, if (count == 0) "EMPTY_RECENTS" else state,
                if (count == 0) "Recents empty; hiding overlay" else "Recents has tasks; showing overlay")
        } catch (e: Throwable) {
            publish(false, "TASK_QUERY_ERROR", "Task query failed; overlay hidden: ${e.javaClass.simpleName}")
        }
    }

    fun close() {
        if (stopped) return
        stopped = true
        runCatching { callback?.asBinder()?.unlinkToDeath(death, 0) }
        callback = null
        process?.destroyForcibly(); process = null
        handler.removeCallbacksAndMessages(null)
        taskListener?.let { listener ->
            runCatching {
                Class.forName("android.app.IActivityTaskManager").getMethod("unregisterTaskStackListener",
                    Class.forName("android.app.ITaskStackListener")).invoke(manager, listener)
            }
        }
        taskListener = null
        thread.quitSafely()
    }
}
