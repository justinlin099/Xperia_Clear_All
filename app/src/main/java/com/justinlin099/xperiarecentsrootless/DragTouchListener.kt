package com.justinlin099.xperiarecentsrootless

import android.annotation.SuppressLint
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Native touch slop separates a click from a drag; cancellation and multiple fingers never click. */
internal class DragTouchListener(
    private val view: View,
    private val position: () -> Point,
    private val move: (Int, Int) -> Unit,
    private val commit: () -> Unit,
    private val allowClick: Boolean = true,
    private val allowDrag: Boolean = true
) : View.OnTouchListener {
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downX = 0f; private var downY = 0f
    private var start = Point()
    private var tracking = false
    private var cancelled = false
    private var travelled = false
    var dragging = false; private set
    fun cancel() {
        if (tracking && dragging) move(start.x, start.y)
        tracking = false; dragging = false; cancelled = true; view.isPressed = false
    }
    private fun moved(event: MotionEvent) = hypot(event.rawX - downX, event.rawY - downY) > slop
    @SuppressLint("ClickableViewAccessibility") // All taps use performClick; TalkBack uses the normal click action.
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!v.isEnabled) { cancel(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = position(); downX = event.rawX; downY = event.rawY
                tracking = true; dragging = false; cancelled = false; travelled = false
                v.drawableHotspotChanged(event.x,event.y);v.isPressed = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> { cancel() }
            MotionEvent.ACTION_MOVE -> if (tracking && !cancelled) {
                v.drawableHotspotChanged(event.x,event.y)
                if (moved(event)) { travelled = true; v.isPressed = false }
                if (allowDrag && !dragging && moved(event)) { dragging = true; v.isPressed = false }
                if (dragging) move(start.x + (event.rawX - downX).roundToInt(), start.y + (event.rawY - downY).roundToInt())
            }
            MotionEvent.ACTION_UP -> {
                val click = tracking && !cancelled && !dragging && !travelled && !moved(event)
                if (allowDrag && tracking && !cancelled && (dragging || moved(event))) {
                    move(start.x + (event.rawX - downX).roundToInt(), start.y + (event.rawY - downY).roundToInt())
                    commit()
                }
                tracking = false; dragging = false; v.isPressed = false
                if (click && allowClick) v.performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }
}
