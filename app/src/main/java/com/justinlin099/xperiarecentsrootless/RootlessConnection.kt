package com.justinlin099.xperiarecentsrootless

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

internal object RootlessConnection {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var context: Context
    private var initialized = false
    private var binderRequest: ShizukuBinderRequest? = null
    private var localeTags = ""
    @Volatile private var epoch=0
    private val recovery = ConnectionRecovery(handler,
        eligible = {
            initialized && BootRecovery.unlocked(context) && AutomaticStart.enabled(context) &&
                android.provider.Settings.canDrawOverlays(context) && !ready &&
                ShizukuRecovery.current(context).retryable
        },
        attempt = {
            BootRecovery.event(context,"CONNECTION_RETRY binder=${Shizuku.pingBinder()} binding=$binding")
            if (workerFailed && !binding) retry() else connect()
        },
        completed = {
            if (!ready && !Shizuku.pingBinder()) status = context.getString(R.string.shizuku_not_running)
            BootRecovery.event(context,"CONNECTION_WAIT_FINISHED ready=$ready"); changed()
        })
    val recovering: Boolean get() = recovery.active
    private var binding = false
    var workerFailed = false
        private set
    var watchFailed = false
        private set
    @Volatile private var remote: IShellTasks? = null
    @Volatile var busy = false
        private set
    var status = ""
        private set
    var diagnostics = ""
        private set
    var detection = ""
        private set
    val observers = linkedSetOf<() -> Unit>()
    val ready: Boolean get() = remote != null
    val authorized: Boolean get() = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun args() = Shizuku.UserServiceArgs(ComponentName(context, ShellTasksService::class.java))
        .tag("xperia-rootless-tasks").version(30).daemon(false).processNameSuffix("rootless_tasks")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder == null) { disconnected(context.getString(R.string.service_disconnected)); return }
            recovery.connected()
            binderRequest?.cancel()
            BootRecovery.event(context,"WORKER_CONNECTED")
            epoch++
            workerFailed=false;watchFailed=false
            remote = IShellTasks.Stub.asInterface(binder)
            try {
                binder.linkToDeath({ handler.post {
                    if(remote?.asBinder()===binder) disconnected(context.getString(R.string.service_disconnected))
                } },0)
            } catch (e: Throwable) { disconnected(context.getString(R.string.connection_lost, e.javaClass.simpleName)); return }
            status = context.getString(R.string.worker_connected)
            changed()
            // Recover even if BOOT_COMPLETED was missed/blocked or arrived before Shizuku.
            val connected = remote ?: return
            worker.execute { AutomaticStart.restoreFromShizuku(context,connected) }
            probe()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            if((remote!=null || binding) && remote?.asBinder()?.isBinderAlive!=true) disconnected(context.getString(R.string.service_disconnected))
        }
    }

    fun init(c: Context) {
        val localizedContext = c.applicationContext.createConfigurationContext(
            android.content.res.Configuration(c.resources.configuration))
        if (initialized) { context = localizedContext; refreshLocale(); return }
        initialized = true
        context = localizedContext
        binderRequest = ShizukuBinderRequest(context, handler,
            eligible = { BootRecovery.unlocked(context) && AutomaticStart.enabled(context) &&
                android.provider.Settings.canDrawOverlays(context) && !Shizuku.pingBinder() },
            received = { Shizuku.onBinderReceived(it, context.packageName) })
        localeTags = context.resources.configuration.locales.toLanguageTags()
        status = context.getString(R.string.waiting)
        detection = context.getString(R.string.inactive)
        diagnostics = context.getString(R.string.no_check)
        diagnostics = context.getSharedPreferences("rootless", 0).getString("diagnostics", diagnostics)!!
        Shizuku.addBinderReceivedListenerSticky { handler.post {
            // A provider-only wake while disabled must not bind a worker just to idle.
            if (AutomaticStart.enabled(context)) resume("binder-received")
            else if (observers.isNotEmpty() && !AutomaticStart.explicitlyDisabled(context)) connect()
        } }
        Shizuku.addBinderDeadListener { handler.post { disconnected(context.getString(R.string.shizuku_restart)) } }
        Shizuku.addRequestPermissionResultListener { _, grant ->
            handler.post {
                if (grant == PackageManager.PERMISSION_GRANTED) connect()
                else { status = context.getString(R.string.permission_denied); changed() }
            }
        }
        if (AutomaticStart.enabled(context)) resume("process-initialized")
    }

    fun resume(source: String) {
        if (!initialized || !BootRecovery.unlocked(context) || !AutomaticStart.enabled(context)) return
        if (ready) return
        recovery.resume()
        BootRecovery.event(context,"CONNECTION_RESUME source=$source binder=${Shizuku.pingBinder()}")
        connect()
    }

    fun refreshLocale() {
        if (!initialized) return
        val current = context.resources.configuration.locales.toLanguageTags()
        if (current == localeTags) return
        localeTags = current
        status = context.getString(if (ready) R.string.worker_connected else R.string.waiting)
        detection = context.getString(if (ShellOverlayService.active != null) R.string.watch_started else R.string.inactive)
        changed()
    }

    fun authorize() {
        try {
            check(Shizuku.pingBinder()) { context.getString(R.string.shizuku_missing) }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) connect()
            else Shizuku.requestPermission(100)
        } catch (e: Throwable) { status = e.message ?: e.toString(); changed() }
    }

    fun connect() {
        try {
            if (!Shizuku.pingBinder()) {
                binderRequest?.request()
                disconnected(context.getString(R.string.shizuku_not_running)); return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                disconnected(context.getString(R.string.authorize_hint));return
            }
            if (Shizuku.getVersion() < 13) {
                disconnected(context.getString(R.string.shizuku_version));return
            }
            if (Shizuku.getUid() != 2000) {
                disconnected(context.getString(R.string.adb_required,Shizuku.getUid()));return
            }
            if (remote != null || binding) return
            recovery.ensure()
            binding = true
            status = context.getString(R.string.connecting)
            changed()
            val attemptEpoch=epoch
            Shizuku.bindUserService(args(), connection)
            handler.postDelayed({
                if (binding && epoch==attemptEpoch) {
                    binding = false; workerFailed=true
                    status = context.getString(R.string.connection_timeout)
                    recovery.ensure(); changed()
                }
            }, 12000)
        } catch (e: Throwable) {
            binding = false; workerFailed=true
            status = context.getString(R.string.connection_error, e.javaClass.simpleName)
            recovery.ensure(); changed()
        }
    }

    private fun disconnected(message: String) {
        epoch++
        remote = null; binding = false; busy = false;workerFailed=true; status = message
        recovery.ensure()
        if (recovery.active) status = context.getString(R.string.waiting)
        changed()
    }

    fun retry() {
        epoch++
        remote=null;binding=false;busy=false;workerFailed=false;watchFailed=false
        recovery.resume()
        runCatching { Shizuku.unbindUserService(args(),connection,true) }
        connect()
    }
    fun watchProblem(failed:Boolean) {
        if(watchFailed==failed) return
        watchFailed=failed;changed()
    }
    fun probe() {
        val service = remote
        if (service == null) { connect(); return }
        execute { service.probe() }
    }

    fun clear(onDone: (Boolean) -> Unit = {}) {
        val service = remote
        if (service == null) { status = context.getString(R.string.connect_first); changed(); onDone(false); return }
        execute(onDone) { service.clearTasks() }
    }

    fun startWatch(listener: IOverviewStateListener, onDone: (Boolean) -> Unit) {
        val service = remote ?: return
        execute(onDone) { service.startOverviewWatch(listener) }
    }

    fun stopWatch() {
        val service = remote ?: return
        worker.execute { runCatching { service.stopOverviewWatch() } }
    }

    private fun execute(onDone: (Boolean) -> Unit = {}, action: () -> String) {
        if (busy) return
        val requestEpoch=epoch
        busy = true; changed(false)
        worker.execute {
            val attempt=runCatching(action)
            val lost=attempt.exceptionOrNull() is android.os.RemoteException || attempt.exceptionOrNull() is SecurityException
            val result = attempt.getOrElse { context.getString(R.string.action_error, it.javaClass.simpleName) }
            val ok = runCatching { JSONObject(result).optBoolean("ok", false) }.getOrDefault(false)
            context.getSharedPreferences("rootless", 0).edit().putString("diagnostics", result).commit()
            handler.post {
                if(requestEpoch!=epoch) return@post
                diagnostics = result; busy = false
                if(lost) disconnected(context.getString(R.string.service_disconnected)) else changed(false)
                onDone(ok)
            }
        }
    }

    var lastEvent = ""
        private set
    fun recordEvent(state: String?, detail: String?) { lastEvent = "$state: $detail" }

    /** One incoming event produces one observer/render pass. Detection text is not connection health. */
    fun overviewEvent(state: String?, detail: String?, message: String) {
        recordEvent(state, detail)
        val failed = state?.endsWith("ERROR")==true || state=="STREAM_ENDED"
        val healthChanged = failed != watchFailed
        watchFailed = failed
        detection = message
        changed(healthChanged)
    }

    fun detection(message: String) {
        if (detection == message) return
        detection = message; changed(false)
    }

    private fun changed(recoveryChanged: Boolean = true) {
        if(initialized && recoveryChanged) RecoveryNotifications.update(context)
        observers.toList().forEach { it() }
    }

    fun cancelRecovery() { recovery.stop(); binderRequest?.cancel() }

    fun disconnectIfIdle() {
        if (ShellOverlayService.active != null || busy) return
        recovery.stop()
        epoch++
        remote = null; binding = false; workerFailed=false;watchFailed=false
        status=context.getString(R.string.inactive)
        runCatching { Shizuku.unbindUserService(args(), connection, true) }
        changed()
    }
}
