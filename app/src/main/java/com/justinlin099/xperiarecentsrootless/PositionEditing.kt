package com.justinlin099.xperiarecentsrootless

import android.content.SharedPreferences
import android.os.SystemClock

/** Explicit, one-use adjustment window. Normal use is always position-locked. */
internal object PositionEditing {
    const val KEY = "positionEditUntil"
    const val DURATION = 60_000L
    fun active(p: SharedPreferences): Boolean {
        val remaining = remaining(p)
        return remaining in 1..DURATION
    }
    fun remaining(p: SharedPreferences) = p.getLong(KEY, 0L) - SystemClock.elapsedRealtime()
    fun begin(p: SharedPreferences) { p.edit().putLong(KEY, SystemClock.elapsedRealtime() + DURATION).apply() }
    fun finish(p: SharedPreferences) { p.edit().remove(KEY).apply() }
}
