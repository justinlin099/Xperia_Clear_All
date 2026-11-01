package com.justinlin099.xperiarecentsrootless

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku

internal enum class ShizukuIssue {
    MISSING, STOPPED, PERMISSION, WRONG_MODE, OLD_VERSION, CONNECTING, WORKER_ERROR, WATCH_ERROR, READY;
    val actionable get() = this!=READY && this!=CONNECTING
    val retryable get() = this==STOPPED || this==CONNECTING || this==WORKER_ERROR
}
internal object ShizukuRecovery {
    const val PACKAGE="moe.shizuku.privileged.api"
    const val EXTRA="show-shizuku-recovery"
    const val PLAY="https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
    const val DOWNLOAD="https://shizuku.rikka.app/download/"
    data class Snapshot(val installed:Boolean,val running:Boolean,val granted:Boolean,val uid:Int=2000,
        val version:Int=13,val ready:Boolean=false,val failed:Boolean=false,val watchFailed:Boolean=false)
    fun classify(s:Snapshot):ShizukuIssue = when {
        !s.installed -> ShizukuIssue.MISSING
        !s.running -> ShizukuIssue.STOPPED
        !s.granted -> ShizukuIssue.PERMISSION
        s.uid!=2000 -> ShizukuIssue.WRONG_MODE
        s.version<13 -> ShizukuIssue.OLD_VERSION
        s.ready && s.watchFailed -> ShizukuIssue.WATCH_ERROR
        s.ready -> ShizukuIssue.READY
        s.failed -> ShizukuIssue.WORKER_ERROR
        else -> ShizukuIssue.CONNECTING
    }
    fun current(c:Context):ShizukuIssue {
        val installed=runCatching { c.packageManager.getApplicationInfo(PACKAGE,0);true }.getOrDefault(false)
        val running=runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted=running && runCatching { Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        return classify(Snapshot(installed,running,granted,
            if(granted) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1,
            if(granted) runCatching { Shizuku.getVersion() }.getOrDefault(0) else 0,
            RootlessConnection.ready,RootlessConnection.workerFailed,RootlessConnection.watchFailed))
    }
    fun heading(issue:ShizukuIssue)=when(issue) {
        ShizukuIssue.MISSING -> R.string.shizuku_not_installed
        ShizukuIssue.STOPPED -> R.string.shizuku_not_running
        ShizukuIssue.PERMISSION -> R.string.permission_denied
        ShizukuIssue.WRONG_MODE -> R.string.wireless_hint
        ShizukuIssue.OLD_VERSION -> R.string.shizuku_version
        ShizukuIssue.CONNECTING -> R.string.connecting
        ShizukuIssue.WORKER_ERROR -> R.string.connection_timeout
        ShizukuIssue.WATCH_ERROR -> R.string.event_error
        ShizukuIssue.READY -> R.string.shizuku_connected
    }
    fun instructions(c:Context,issue:ShizukuIssue):String {
        fun steps(vararg ids:Int):String = ids.mapIndexed { i,id -> "${java.text.NumberFormat.getIntegerInstance(c.resources.configuration.locales[0]).format(i+1)}. ${c.getString(id)}" }.joinToString("\n")
        return when(issue) {
            ShizukuIssue.MISSING -> steps(R.string.get_shizuku,R.string.wireless_hint,R.string.return_and_connect)
            ShizukuIssue.PERMISSION -> c.getString(R.string.authorize_hint)
            ShizukuIssue.WORKER_ERROR,ShizukuIssue.WATCH_ERROR -> steps(R.string.connect,R.string.open_shizuku,R.string.wireless_hint)
            ShizukuIssue.OLD_VERSION -> steps(R.string.get_shizuku,R.string.open_shizuku,R.string.return_and_connect)
            ShizukuIssue.CONNECTING -> c.getString(R.string.waiting)
            ShizukuIssue.READY -> c.getString(R.string.worker_connected)
            else -> steps(R.string.open_shizuku,R.string.wireless_hint,R.string.return_and_connect)
        }
    }
    private fun launch(c:Context,intent:Intent):Boolean = runCatching {
        if(c !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(intent);true
    }.getOrDefault(false)
    fun play(c:Context):Boolean {
        val targets=listOf(Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=$PACKAGE")).setPackage("com.android.vending"),
            Intent(Intent.ACTION_VIEW,Uri.parse(PLAY)),Intent(Intent.ACTION_VIEW,Uri.parse(DOWNLOAD)))
        if(targets.any { launch(c,it) }) return true
        failure(c);return false
    }
    fun open(c:Context):Boolean {
        val intent=c.packageManager.getLaunchIntentForPackage(PACKAGE)
        if(intent!=null && launch(c,intent)) return true
        // Package may have been removed between render and tap; never crash on a stale button.
        val present=runCatching { c.packageManager.getApplicationInfo(PACKAGE,0);true }.getOrDefault(false)
        if(!present) return play(c)
        if(launch(c,Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$PACKAGE")))) return true
        failure(c);return false
    }
    private fun failure(c:Context) {
        if(c is Activity) com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
            .setMessage(c.getString(R.string.open_link_failed)+"\n"+PLAY+"\n"+DOWNLOAD)
            .setNeutralButton(android.R.string.copy) { _,_ ->
                c.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Shizuku",PLAY))
            }.setPositiveButton(android.R.string.ok,null).show()
    }
}
