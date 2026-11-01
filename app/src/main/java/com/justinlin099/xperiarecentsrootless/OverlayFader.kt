package com.justinlin099.xperiarecentsrootless

import android.view.View
import android.view.animation.DecelerateInterpolator

/** One animator per overlay. Repeated state events never restart a transition. */
internal class OverlayFader(private val view: View) {
    private var visible = false
    fun show() {
        if (visible) return
        visible = true
        view.animate().withEndAction(null).cancel()
        if (view.visibility != View.VISIBLE) view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(120L).setInterpolator(DecelerateInterpolator()).start()
    }
    fun hide() {
        view.isEnabled = false
        if (!visible) return
        visible = false
        view.animate().withEndAction(null).cancel()
        view.animate().alpha(0f).setDuration(100L).withEndAction {
            if (!visible) { view.visibility = View.GONE; view.alpha = 0f }
        }.start()
    }
    fun cancel() { visible = false; view.animate().withEndAction(null).cancel() }
}
