package com.justinlin099.xperiarecentsrootless

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/** Normal application overlay: never calls AccessibilityService APIs. */
class ShellOverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        var active: ShellOverlayService? = null
            private set
        const val STOP = "stop-shell-overlay"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("rootless", 0) }
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private var overlay: ClearButtonView? = null
    private var dragTouch: DragTouchListener? = null
    private val relockPosition = Runnable { PositionEditing.finish(prefs) }
    private var savingManualPosition = false
    private var watchRequested = false
    private var inOverview = false
    private var fader: OverlayFader? = null
    private var receiverRegistered = false
    private var notificationStatus = ""
    private val stateListener = object : IOverviewStateListener.Stub() {
        override fun onState(visible: Boolean, state: String?, detail: String?) {
            handler.post {
                if (active !== this@ShellOverlayService) return@post
                inOverview = visible
                val message = when {
                    state == "EMPTY_RECENTS" -> R.string.event_empty
                    state?.endsWith("ERROR") == true || state == "STREAM_ENDED" -> R.string.event_error
                    visible -> R.string.event_visible
                    else -> R.string.event_hidden
                }
                RootlessConnection.overviewEvent(state, detail, getString(message))
            }
        }
    }
    private val observer: () -> Unit = {
        if (!RootlessConnection.ready) {
            watchRequested = false
            inOverview = false
        } else if (!watchRequested && !RootlessConnection.busy) {
            watchRequested = true
            RootlessConnection.startWatch(stateListener) { ok ->
                if (active === this) {
                    RootlessConnection.watchProblem(!ok)
                    RootlessConnection.detection(if(ok) getString(R.string.watch_started) else getString(R.string.watch_failed))
                }
            }
        }
        val text = notificationText()
        if (text != notificationStatus) {
            notificationStatus = text
            getSystemService(NotificationManager::class.java).notify(30, notification())
        }
        render()
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                RootlessConnection.resume("screen-unlocked")
            } else {
                inOverview = false
                render()
            }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(ShellServiceNotification.CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        startForeground(30, notification())
        active = this
        AutomaticStart.serviceEvent(this,"CREATED")
        prefs.registerOnSharedPreferenceChangeListener(this)
        if (PositionEditing.active(prefs)) handler.postDelayed(relockPosition, PositionEditing.remaining(prefs))
        RootlessConnection.init(this)
        RootlessConnection.observers.add(observer)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
        receiverRegistered = true
    }

    private fun notificationText() = if (RootlessConnection.ready) getString(R.string.notification_text)
        else RootlessConnection.status.ifEmpty { getString(R.string.waiting) }

    private fun notification(): Notification {
        val main = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ShellOverlayService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, ShellServiceNotification.CHANNEL).setSmallIcon(com.justinlin099.xperiarecentsrootless.R.drawable.ic_rootless)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationText())
            .setContentIntent(main).setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build()).build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(ShellServiceNotification.CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        notifications.notify(30, notification())
        RootlessConnection.init(this)
        removeOverlay()
        render()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            AutomaticStart.serviceEvent(this,"USER_STOP")
            AutomaticStart.remember(this,false)
            stopSelf(); return START_NOT_STICKY
        }
        if ((intent == null || intent.action == AutomaticStart.RESTORE) && !AutomaticStart.enabled(this)) {
            AutomaticStart.serviceEvent(this,"RESTORE_DISABLED")
            stopSelf(); return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            AutomaticStart.serviceEvent(this,"OVERLAY_PERMISSION_MISSING")
            RootlessConnection.detection(getString(R.string.overlay_required))
            stopSelf(); return START_NOT_STICKY
        }
        AutomaticStart.serviceEvent(this,"START_ACCEPTED")
        AutomaticStart.remember(this,true)
        RootlessConnection.resume("overlay-service")
        observer()
        // Retain enabled intent when Android reclaims the process. A user Stop clears it.
        // While Shizuku is unavailable, its sticky binder callback resumes the watcher.
        return START_STICKY
    }

    private fun render() {
        val canShow = inOverview && RootlessConnection.ready && !RootlessConnection.watchFailed &&
            Settings.canDrawOverlays(this) && getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!canShow) { dragTouch?.cancel(); fader?.hide(); return }
        val appearance = ButtonAppearance.read(prefs)
        if (overlay == null) {
            val button = ClearButtonView(this).apply {
                visibility = View.GONE; alpha = 0f
                configure(appearance)
                setOnClickListener {
                    if (PositionEditing.active(prefs)) return@setOnClickListener
                    RootlessConnection.clear { ok ->
                        if (ok) {
                            inOverview = false; render()
                            runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                }
            }
            val size = button.preferredSize()
            val edgeSpace = if (appearance.position == ButtonPosition.BOTTOM_CENTER) 24 else appearance.effectiveHorizontalMargin + 12
            val availableWidth = if (appearance.position == ButtonPosition.BOTTOM_RIGHT) usableArea().width() else resources.displayMetrics.widthPixels - dp(edgeSpace)
            val width = size.first.coerceAtMost(availableWidth.coerceAtLeast(1))
            val params = WindowManager.LayoutParams(width, size.second, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply {
                gravity = if (appearance.position == ButtonPosition.BOTTOM_RIGHT) android.view.Gravity.TOP or android.view.Gravity.LEFT else appearance.gravity
                x = if (appearance.position != ButtonPosition.TOP_RIGHT) 0 else dp(appearance.effectiveHorizontalMargin)-button.feedbackInset
                y = if (appearance.position == ButtonPosition.BOTTOM_RIGHT) 0 else dp(appearance.effectiveVerticalMargin).coerceAtMost((resources.displayMetrics.heightPixels - size.second - dp(96)).coerceAtLeast(dp(12)))
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                windowAnimations = 0
                title = "Recents ClearAll"
                if (appearance.position == ButtonPosition.BOTTOM_RIGHT) {
                    flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    val point = manualPoint(appearance, width, height, button.feedbackInset)
                    val display = wm.currentWindowMetrics.bounds
                    x = point.x - display.left; y = point.y - display.top
                }
            }
            try { wm.addView(button, params); overlay = button; fader = OverlayFader(button) }
            catch (e: Throwable) {
                inOverview = false
                RootlessConnection.detection(getString(R.string.overlay_error, e.javaClass.simpleName))
                return
            }
        }
        if (appearance.position == ButtonPosition.BOTTOM_RIGHT && dragTouch == null) {
            val button = overlay ?: return
            val params = button.layoutParams as WindowManager.LayoutParams
            val display = wm.currentWindowMetrics.bounds
            var point = android.graphics.Point(display.left + params.x, display.top + params.y)
            fun move(x: Int, y: Int) {
                point = ManualPlacement.clamp(usableArea(button.feedbackInset), params.width, params.height, x, y)
                params.x = point.x - display.left; params.y = point.y - display.top
                runCatching { wm.updateViewLayout(button, params) }
            }
            val editing = PositionEditing.active(prefs)
            dragTouch = DragTouchListener(button, { android.graphics.Point(point) }, ::move, {
                val value = ManualPlacement.normalized(usableArea(button.feedbackInset), params.width, params.height, point.x, point.y)
                val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                savingManualPosition = true
                try {
                    prefs.edit().putInt(ManualPlacement.key(appearance, landscape, true), value.first)
                        .putInt(ManualPlacement.key(appearance, landscape, false), value.second).apply()
                } finally { savingManualPosition = false }
                handler.post { PositionEditing.finish(prefs) }
            }, allowClick = !editing, allowDrag = editing)
            button.setOnTouchListener(dragTouch)
        }
        fader?.show()
        overlay?.isEnabled = !RootlessConnection.busy
    }

    private fun usableArea(feedbackInset:Int=0): android.graphics.Rect {
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        val b = metrics.bounds
        return android.graphics.Rect(b.left + insets.left + dp(4)-feedbackInset, b.top + insets.top + dp(4),
            b.right - insets.right - dp(4)+feedbackInset, b.bottom - insets.bottom - dp(4))
    }
    private fun manualPoint(style: ButtonAppearance, width: Int, height: Int, feedbackInset:Int): android.graphics.Point {
        val fractions = ManualPlacement.fractions(style, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        return ManualPlacement.pixels(usableArea(feedbackInset), width, height, fractions.first, fractions.second)
    }
    private fun removeOverlay() {
        dragTouch?.cancel(); dragTouch = null
        fader?.cancel(); fader = null
        overlay?.let { runCatching { wm.removeViewImmediate(it) } }
        overlay = null
    }
    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == PositionEditing.KEY) {
            handler.removeCallbacks(relockPosition)
            if (PositionEditing.active(prefs)) handler.postDelayed(relockPosition, PositionEditing.remaining(prefs))
            removeOverlay(); render(); return
        }
        if (key == "buttonPosition") PositionEditing.finish(prefs)
        if (savingManualPosition && key in ManualPlacement.keys) return
        if (key in ButtonAppearance.keys) removeOverlay()
        if (key in ButtonAppearance.keys) render()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        AutomaticStart.serviceEvent(this,"DESTROYED")
        active = null
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        PositionEditing.finish(prefs)
        RootlessConnection.observers.remove(observer)
        RootlessConnection.stopWatch()
        RootlessConnection.detection(getString(R.string.watch_stopped))
        RootlessConnection.disconnectIfIdle()
        super.onDestroy()
    }
}
