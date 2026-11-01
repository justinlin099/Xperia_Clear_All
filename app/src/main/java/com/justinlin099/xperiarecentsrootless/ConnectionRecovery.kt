package com.justinlin099.xperiarecentsrootless

import android.os.Handler

/** A bounded retry window, never a permanent polling loop or a wake lock. */
internal class ConnectionRecovery(
    private val handler: Handler,
    private val eligible: () -> Boolean,
    private val attempt: () -> Unit,
    private val completed: () -> Unit
) {
    companion object {
        // Cumulative retry times: 2, 6, 14, 30, 60 seconds.
        private val delays = longArrayOf(2000, 4000, 8000, 16000, 30000)
    }
    var active = false
        private set
    private var exhausted = false
    private var next = 0
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (!eligible()) { stop(); completed(); return }
            attempt()
            if (!active) return // A synchronous connection may have cancelled the window.
            if (!eligible() || next == delays.size) {
                exhausted = true
                stop()
                completed()
            } else schedule()
        }
    }
    fun ensure() {
        if (!eligible()) { stop(); return }
        if (active || exhausted) return
        active = true
        next = 0
        schedule()
    }
    /** A real lifecycle/binder event may open a fresh window after a previous timeout. */
    fun resume() {
        if (!active) exhausted = false
        ensure()
    }
    fun stop() {
        active = false
        handler.removeCallbacks(tick)
    }
    fun connected() { stop(); exhausted = false }
    private fun schedule() { handler.postDelayed(tick, delays[next++]) }
}
