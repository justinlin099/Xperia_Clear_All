package com.justinlin099.xperiarecentsrootless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Both boot stages are recorded; credential-protected settings are read only after unlock. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action=intent.action ?: return
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED || action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)
            BootRecovery.afterBoot(context,action)
    }
}
