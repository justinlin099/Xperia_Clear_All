package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Parcel

/** Shizuku's rish REQUEST_BINDER wire protocol; requests a Binder, never starts an Activity. */
internal class ShizukuBinderRequest(
    private val context: Context,
    private val handler: Handler,
    private val eligible: () -> Boolean,
    private val received: (IBinder) -> Unit
) {
    companion object { const val ACTION = "rikka.shizuku.intent.action.REQUEST_BINDER" }
    private var generation = 0
    private var pending = false
    private val timeout = Runnable { pending = false }

    fun cancel() {
        generation++
        pending = false
        handler.removeCallbacks(timeout)
    }

    fun request() {
        if (pending || !eligible()) return
        val managerUid = runCatching {
            context.packageManager.getApplicationInfo(ShizukuRecovery.PACKAGE, 0).uid
        }.getOrNull() ?: return
        val token = ++generation
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != 1) return super.onTransact(code, data, reply, flags)
                // The callback is only a capability for the explicitly addressed manager.
                if (getCallingUid() != managerUid) return false
                val binder = data.readStrongBinder()
                // rish also receives sourceDir. We deliberately never load manager code.
                handler.post {
                    if (token != generation || !eligible()) return@post
                    pending = false
                    handler.removeCallbacks(timeout)
                    BootRecovery.event(context, if (binder == null) "BINDER_REQUEST_EMPTY" else "BINDER_REQUEST_RECEIVED")
                    if (binder?.pingBinder() == true) received(binder)
                }
                return true
            }
        }
        val data = Bundle().apply { putBinder("binder", callback) }
        val intent = Intent(ACTION).setPackage(ShizukuRecovery.PACKAGE)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra("data", data)
        pending = true
        handler.postDelayed(timeout, 1500)
        runCatching { context.sendBroadcast(intent) }
            .onSuccess { BootRecovery.event(context, "BINDER_REQUEST_SENT") }
            .onFailure {
                cancel()
                BootRecovery.event(context, "BINDER_REQUEST_ERROR ${it.javaClass.simpleName}")
            }
    }
}
