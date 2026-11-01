package com.justinlin099.xperiarecentsrootless

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/** One notification transitions from waiting to repair, and disappears on recovery. */
internal object RecoveryNotifications {
    const val CHANNEL="shizuku-recovery"
    const val ID=31
    private var last:Pair<ShizukuIssue,Boolean>?=null
    fun ensure(c:Context) {
        val manager=c.getSystemService(NotificationManager::class.java)
        val channel=manager.getNotificationChannel(CHANNEL) ?: NotificationChannel(CHANNEL,c.getString(R.string.recovery_notifications),NotificationManager.IMPORTANCE_DEFAULT)
        channel.name=c.getString(R.string.recovery_notifications)
        manager.createNotificationChannel(channel) // Preserve the user's blocked/quiet channel choice.
    }
    fun canAlert(c:Context):Boolean {
        val manager=c.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    fun clear(c:Context) { c.getSystemService(NotificationManager::class.java).cancel(ID);last=null }
    fun update(c:Context,issue:ShizukuIssue=ShizukuRecovery.current(c)) {
        if(!BootRecovery.unlocked(c)) return
        ensure(c)
        if(!AutomaticStart.enabled(c) || issue==ShizukuIssue.READY) { clear(c);return }
        val waiting=issue==ShizukuIssue.CONNECTING || (RootlessConnection.recovering && issue.retryable)
        val state=issue to waiting
        if((!waiting && !issue.actionable) || !canAlert(c) || last==state) return
        val open=Intent(c,MainActivity::class.java).putExtra(ShizukuRecovery.EXTRA,true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val target=PendingIntent.getActivity(c,31,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message=if(waiting) c.getString(if(issue==ShizukuIssue.CONNECTING) R.string.connecting else R.string.waiting)
            else c.getString(ShizukuRecovery.heading(issue))+"\n"+c.getString(R.string.recovery_notification)
        val notification=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_rootless)
            .setContentTitle(c.getString(R.string.app_name)).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(target)
            .setAutoCancel(!waiting).setOngoing(waiting).setSilent(waiting).setOnlyAlertOnce(true)
            .setCategory(if(waiting) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_ERROR).build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(ID,notification) }
            .onSuccess { last=state }
    }
    fun settings(c:Context):Intent {
        val manager=c.getSystemService(NotificationManager::class.java)
        val blocked=manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE
        return if(blocked) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,CHANNEL)
            else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName)
    }
}
