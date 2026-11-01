package com.justinlin099.xperiarecentsrootless

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    companion object {
        internal const val DONATION_URL="https://buymeacoffee.com/5b32VpsEp5"
        internal const val GITHUB_URL="https://github.com/JustinLin099"
    }
    private var askSupportOnStart=true
    private var externalNavigation=false
    private var supportDialog: androidx.appcompat.app.AlertDialog?=null
    private var permissionGuideDialog: androidx.appcompat.app.AlertDialog?=null
    private lateinit var preview: RecentsPreviewView
    private val appearanceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key in ButtonAppearance.keys) preview.configure(ButtonAppearance.read(prefs))
    }
    private lateinit var status: TextView
    private lateinit var detection: TextView
    private lateinit var result: TextView
    private lateinit var connectionState: TextView
    private lateinit var overlayState: TextView
    private lateinit var switchBar: MaterialCardView
    private lateinit var mode: MaterialSwitch
    private var updatingMode = false
    private lateinit var setupCard: View
    private lateinit var shizukuSetup: View
    private lateinit var overlaySetup: View
    private lateinit var restrictedCard: View
    private lateinit var backgroundCard: View
    private lateinit var recoveryText: TextView
    private lateinit var recoveryAction: MaterialButton
    private lateinit var recoveryRetry: MaterialButton
    private lateinit var notificationCard: View
    private lateinit var mainScroll: ScrollView
    private lateinit var probe: MaterialButton
    private lateinit var clear: MaterialButton
    private val observer: () -> Unit = { refresh() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun color(attr: Int) = MaterialColors.getColor(this, attr, "Recents")
    private fun step(n:Int,title:String)=java.text.NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(n)+". "+title
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun LinearLayout.space(n: Int) { addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(n))) }
    private fun card(parent: LinearLayout, title: String, caption: String? = null): LinearLayout {
        val container = column().apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat(); cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
            addView(container)
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        container.addView(label(title, 20f, true))
        if (caption != null) {
            container.space(6)
            container.addView(label(caption, 14f).apply {
                setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        container.space(16)
        return container
    }
    private fun action(parent: LinearLayout, title: String, primary: Boolean = false, run: () -> Unit): MaterialButton {
        val button = MaterialButton(this, null, if (primary) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = title; textSize = 15f; isAllCaps = false
            cornerRadius = dp(18); minimumHeight = dp(54)
            setOnClickListener { run() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2))
        return button
    }
    private fun note(parent: LinearLayout, value: String): TextView = label(value, 13f).apply {
        setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        parent.addView(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askSupportOnStart=savedInstanceState==null
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
            clipToPadding = false
        }
        val body = column().apply {
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        }
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            body.setPadding(dp(20) + bars.left, dp(24) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom)
            insets
        }
        mainScroll=scroll
        setContentView(scroll)
        body.space(48)
        body.addView(label(getString(R.string.app_name), 36f).apply { tag="screen-title" })
        body.space(28)
        preview = RecentsPreviewView(this).apply {
            tag="main-preview"
            configure(ButtonAppearance.read(getSharedPreferences("rootless",0)))
        }
        val previewHeight = (resources.configuration.screenHeightDp * .36f).roundToInt().coerceIn(240,340)
        body.addView(preview,LinearLayout.LayoutParams(-1,dp(previewHeight)))
        body.space(32)
        switchBar = MaterialCardView(this).apply {
            tag="enable-bar"
            radius=dp(40).toFloat(); cardElevation=0f; strokeWidth=0
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorPrimaryContainer))
        }
        body.addView(switchBar,LinearLayout.LayoutParams(-1,-2))
        mode = MaterialSwitch(this).apply {
            tag = "monitoring-switch"
            text = getString(R.string.enable); textSize = 20f; minHeight = dp(80)
            setPaddingRelative(dp(24),dp(12),dp(20),dp(12))
            setTextColor(color(com.google.android.material.R.attr.colorOnPrimaryContainer))
            setOnCheckedChangeListener { _, checked ->
                if (!updatingMode) {
                    if (!checked) {
                        AutomaticStart.remember(this@MainActivity,false)
                        stopService(Intent(this@MainActivity,ShellOverlayService::class.java))
                        RootlessConnection.disconnectIfIdle()
                        post { refresh() }
                    } else {
                        AutomaticStart.remember(this@MainActivity,true)
                        if(RootlessConnection.workerFailed || RootlessConnection.watchFailed) RootlessConnection.retry()
                        else RootlessConnection.connect()
                        if(!Settings.canDrawOverlays(this@MainActivity)) overlaySettings()
                        else {
                            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED &&
                                !getSharedPreferences("rootless",0).getBoolean("notificationPermissionAsked",false)) notificationPermission()
                            runCatching { startForegroundService(Intent(this@MainActivity,ShellOverlayService::class.java)) }
                                .onFailure {
                                    RootlessConnection.detection(getString(R.string.service_error,it.javaClass.simpleName))
                                }
                        }
                    }
                    refresh()
                }
            }
        }
        switchBar.addView(mode,android.widget.FrameLayout.LayoutParams(-1,-2))
        body.space(32)

        val setup = card(body, step(1,getString(R.string.connect)))
        setupCard = (setup.parent as View).apply { tag = "permission-setup-card" }
        val shizuku = column().apply { tag = "shizuku-setup" }
        setup.addView(shizuku); shizukuSetup = shizuku
        connectionState = label(getString(R.string.shizuku_disconnected), 15f, true)
        shizuku.addView(connectionState)
        recoveryText=note(shizuku,getString(R.string.wireless_hint)).apply { tag="recovery-instructions" }
        recoveryAction=action(shizuku,getString(R.string.open_shizuku),true) {
            when(ShizukuRecovery.current(this)) {
                ShizukuIssue.MISSING,ShizukuIssue.OLD_VERSION -> ShizukuRecovery.play(this)
                ShizukuIssue.PERMISSION -> RootlessConnection.authorize()
                else -> ShizukuRecovery.open(this)
            }
        }.apply { tag="recovery-primary";maxLines=3 }
        recoveryRetry=action(shizuku,getString(R.string.connect)) {
            val issue=ShizukuRecovery.current(this)
            if(issue==ShizukuIssue.WORKER_ERROR || issue==ShizukuIssue.WATCH_ERROR) RootlessConnection.retry()
            else RootlessConnection.authorize()
        }.apply { tag="recovery-retry" }
        val overlay=card(body,step(2,getString(R.string.overlay_settings)))
        overlaySetup=(overlay.parent as View).apply { tag="overlay-setup" }
        overlayState = label(getString(R.string.overlay_denied), 15f, true)
        overlay.addView(overlayState)
        note(overlay, getString(R.string.overlay_hint))
        action(overlay, step(1,getString(R.string.overlay_settings)),true) { overlaySettings() }.apply { tag="overlay-permission-action" }

        // One overlay-permission guide: attempt first, then offer conditional help.
        // Visiting Settings does not tell us whether Android actually blocked access.
        val restricted=column().apply { tag="restricted-settings-card" }
        restrictedCard=restricted
        overlay.addView(restricted)
        restricted.space(20)
        restricted.addView(label(step(2,getString(R.string.restricted_settings)),16f,true))
        restricted.space(6)
        note(restricted,getString(R.string.restricted_settings_hint))
        restricted.space(12)
        action(restricted,getString(R.string.app_info)) {
            showPermissionGuide(step(2,getString(R.string.restricted_settings)),R.string.restricted_settings_hint,
                listOf(R.drawable.overlay_permission_app_info),R.string.app_info) {
                startActivity(RestrictedSettingsStep.appInfo(this))
            }
        }
            .apply { tag="restricted-settings-app-info" }
        restricted.space(12)
        action(restricted,step(3,getString(R.string.restricted_settings_done)),true) {
            openOverlaySettings()
        }.apply { tag="restricted-settings-done" }

        val background=card(body,step(3,getString(R.string.background_start)),getString(R.string.background_hint))
        backgroundCard=(background.parent as View).apply { tag="background-start-card" }
        action(background,getString(R.string.background_settings)) {
            val restricted=getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted
            val target=if(restricted) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))
                else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName"))
            runCatching { startActivity(target) }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
            }
        }

        val notifications=card(body,step(4,getString(R.string.recovery_notifications)),getString(R.string.notification_hint))
        notificationCard=(notifications.parent as View).apply { tag="notification-permission-card" }
        action(notifications,getString(R.string.allow_notifications)) { notificationPermission() }

        val appearance = card(body, getString(R.string.appearance_title))
        appearance.addView(AppearanceSettings(this, showPreview=false))

        val support=card(body,getString(R.string.support_title),getString(R.string.support_message))
        (support.parent as View).tag="support-card"
        action(support,getString(R.string.donate),true) { openLink(DONATION_URL) }.tag="donate-action"

        val language = card(body, getString(R.string.language_title))
        (language.parent as View).tag="language-card"
        note(language, getString(R.string.locale_name))
        if (Build.VERSION.SDK_INT >= 33) {
            action(language, getString(R.string.language_settings)) {
                startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:$packageName")))
            }
        } else note(language, getString(R.string.language_system))

        val tools = card(body, getString(R.string.diagnostics_title), getString(R.string.diagnostics_caption))
        (tools.parent as View).tag="diagnostics-card"
        val details = column().apply { visibility = View.GONE }
        val expand = action(tools, getString(R.string.expand)) {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        details.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            expand.text = if (details.visibility == View.VISIBLE) getString(R.string.collapse) else getString(R.string.expand)
        }
        tools.addView(details)
        details.space(12)
        status=label(getString(R.string.inactive),14f,true)
        details.addView(status)
        detection=note(details,getString(R.string.connect_hint))
        details.space(12)
        probe = action(details, getString(R.string.probe)) { RootlessConnection.probe() }
        clear = action(details, getString(R.string.manual_clear)) {
            RootlessConnection.clear()
        }
        action(details, getString(R.string.copy)) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Xperia Clear All diagnostics",
                getString(R.string.diagnostic_format, BuildConfig.VERSION_NAME,
                    getString(if (ShellOverlayService.active != null) R.string.yes else R.string.no),
                    getString(if (Settings.canDrawOverlays(this)) R.string.yes else R.string.no),
                    RootlessConnection.status, RootlessConnection.detection, RootlessConnection.lastEvent,
                    RootlessConnection.diagnostics + "\nStartup: " +
                        getSharedPreferences("rootless",0).getString("startupEvent","none") +
                        "\nService lifecycle: " + getSharedPreferences("rootless",0).getString("startupServiceEvent","none") + "\n" + BootRecovery.diagnostics(this))))
            toast(getString(R.string.copied))
        }
        details.space(12)
        result = note(details, getString(R.string.no_check)).apply { setTextIsSelectable(true); typeface = Typeface.MONOSPACE }
        val github=action(body,"GitHub · JustinLin099") { openLink(GITHUB_URL) }
        github.tag="github-action"
        body.space(16)
        body.addView(label(getString(R.string.footer, BuildConfig.VERSION_NAME), 12f).apply { gravity = Gravity.CENTER })
        body.space(6)
        body.addView(label("Made with ❤️ by JustinLin099",13f).apply { gravity=Gravity.CENTER;tag="author-credit";textDirection=View.TEXT_DIRECTION_LTR })
        body.space(12)
        RootlessConnection.init(this)
        refresh()
    }

    override fun startActivity(intent:Intent,options:Bundle?) {
        externalNavigation=true
        try { super.startActivity(intent,options) }
        catch(e:RuntimeException) { externalNavigation=false;throw e }
    }
    private fun openLink(url:String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure {
            val address=label(url).apply { setTextIsSelectable(true);setPadding(dp(24),dp(16),dp(24),dp(16)) }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(R.string.open_link_failed).setView(address).setPositiveButton(android.R.string.ok,null).show()
        }
    }
    private fun showSupportPrompt() {
        if(supportDialog?.isShowing==true) return
        supportDialog=com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.support_title).setMessage(R.string.support_message)
            .setPositiveButton(R.string.donate) { _,_->openLink(DONATION_URL) }
            .setNegativeButton(R.string.not_now,null).show()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun overlaySettings() {
        showPermissionGuide(step(1,getString(R.string.overlay_settings)),R.string.overlay_hint,
            listOf(R.drawable.overlay_permission_app_list,R.drawable.overlay_permission_blocked),R.string.overlay_settings) {
            openOverlaySettings()
        }
    }
    private fun showPermissionGuide(title:String,hint:Int,images:List<Int>,action:Int,onContinue:()->Unit) {
        if(permissionGuideDialog?.isShowing==true) return
        val content=column().apply { setPadding(dp(24),dp(12),dp(24),dp(12)) }
        content.addView(label(getString(hint),14f))
        for(image in images) {
            content.space(16)
            content.addView(ImageView(this).apply {
                adjustViewBounds=true
                scaleType=ImageView.ScaleType.FIT_CENTER
                setImageResource(image)
                // The adjacent localized instructions describe each action for screen readers.
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tag="permission-guide-screenshot"
            },LinearLayout.LayoutParams(-1,-2))
        }
        val scroll=ScrollView(this).apply { addView(content) }
        permissionGuideDialog=com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title).setView(scroll)
            .setPositiveButton(action) { _,_->onContinue() }
            .setNegativeButton(android.R.string.cancel,null)
            .create().apply {
                setOnDismissListener { permissionGuideDialog=null }
                show()
            }
    }
    private fun openOverlaySettings() {
        RestrictedSettingsStep.visitOverlay(this)
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
    override fun onStart() {
        super.onStart()
        RootlessConnection.observers.add(observer)
        val prefs=getSharedPreferences("rootless",0)
        prefs.registerOnSharedPreferenceChangeListener(appearanceListener)
        preview.configure(ButtonAppearance.read(prefs))
        if(askSupportOnStart && !externalNavigation) showSupportPrompt()
        askSupportOnStart=true;externalNavigation=false
    }
    override fun onStop() {
        RootlessConnection.observers.remove(observer)
        getSharedPreferences("rootless",0).unregisterOnSharedPreferenceChangeListener(appearanceListener)
        super.onStop()
    }
    override fun onResume() {
        super.onResume();RootlessConnection.init(this)
        if(!AutomaticStart.explicitlyDisabled(this)) RootlessConnection.connect()
        AutomaticStart.restore(this,"activity-resume");refresh();showRecoveryIfRequested()
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent);refresh();showRecoveryIfRequested()
    }
    private fun showRecoveryIfRequested() {
        if(!intent.getBooleanExtra(ShizukuRecovery.EXTRA,false)) return
        intent.removeExtra(ShizukuRecovery.EXTRA)
        mainScroll.post {
            if(shizukuSetup.visibility==View.VISIBLE) shizukuSetup.requestRectangleOnScreen(android.graphics.Rect(0,0,shizukuSetup.width,shizukuSetup.height),true)
            else mainScroll.smoothScrollTo(0,0)
        }
    }
    private fun notificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs=getSharedPreferences("rootless",0)
            if(!prefs.getBoolean("notificationPermissionAsked",false) || shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                prefs.edit().putBoolean("notificationPermissionAsked",true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),190)
                return
            }
        }
        runCatching { startActivity(RecoveryNotifications.settings(this)) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
        }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==190) refresh()
    }
    private fun refresh() {
        if (!::result.isInitialized) return
        val running = ShellOverlayService.active != null
        status.text = if (running) getString(R.string.active) else getString(R.string.inactive)
        val issue=ShizukuRecovery.current(this)
        connectionState.text=getString(ShizukuRecovery.heading(issue))
        recoveryText.text=ShizukuRecovery.instructions(this,issue)
        recoveryAction.setText(when(issue) {
            ShizukuIssue.MISSING,ShizukuIssue.OLD_VERSION -> R.string.get_shizuku
            ShizukuIssue.PERMISSION -> R.string.connect
            else -> R.string.open_shizuku
        })
        recoveryRetry.visibility=if(issue==ShizukuIssue.MISSING || issue==ShizukuIssue.PERMISSION) View.GONE else View.VISIBLE
        recoveryRetry.isEnabled=issue!=ShizukuIssue.CONNECTING
        recoveryAction.isEnabled=issue!=ShizukuIssue.CONNECTING
        overlayState.text = if (Settings.canDrawOverlays(this)) getString(R.string.overlay_granted) else getString(R.string.overlay_denied)
        detection.text = if (running) RootlessConnection.detection else RootlessConnection.status
        restrictedCard.visibility=if(RestrictedSettingsStep.needsReview(this)) View.VISIBLE else View.GONE
        updateSetupVisibility(issue==ShizukuIssue.READY || AutomaticStart.explicitlyDisabled(this),Settings.canDrawOverlays(this))
        RecoveryNotifications.update(this,issue)
        notificationCard.visibility=if(!RecoveryNotifications.canAlert(this)) View.VISIBLE else View.GONE
        val restricted=getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted
        val exempt=getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        backgroundCard.visibility=if(restricted || !exempt) View.VISIBLE else View.GONE
        AutomaticStart.activateAfterSetup(this,issue==ShizukuIssue.READY && Settings.canDrawOverlays(this) &&
            !restricted && exempt && RecoveryNotifications.canAlert(this))
        updatingMode = true
        mode.isChecked = AutomaticStart.enabled(this)
        mode.isEnabled=true
        updatingMode = false
        switchBar.setCardBackgroundColor(color(if(mode.isChecked) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurfaceContainerHighest))
        mode.setTextColor(color(if(mode.isChecked) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurface))
        result.text = RootlessConnection.diagnostics
        probe.isEnabled = !RootlessConnection.busy
        clear.isEnabled = RootlessConnection.ready && !RootlessConnection.busy
    }
    internal fun updateSetupVisibility(shizukuAllowed: Boolean, overlayAllowed: Boolean) {
        shizukuSetup.visibility = if(shizukuAllowed) View.GONE else View.VISIBLE
        overlaySetup.visibility = if(overlayAllowed) View.GONE else View.VISIBLE
        setupCard.visibility = if(shizukuAllowed) View.GONE else View.VISIBLE
    }
    override fun onDestroy() {
        permissionGuideDialog?.dismiss();permissionGuideDialog=null
        supportDialog?.dismiss();supportDialog=null
        RootlessConnection.observers.remove(observer)
        if (isFinishing) RootlessConnection.disconnectIfIdle()
        super.onDestroy()
    }
}
