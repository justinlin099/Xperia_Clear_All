package com.justinlin099.xperiarecentsrootless

import android.app.Activity
import android.os.Looper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@LooperMode(LooperMode.Mode.PAUSED)
class OverlayFaderTest {
    @Test fun fadeInAndRapidReentryCancelPendingHide() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = View(controller.get()).apply { visibility = View.GONE; alpha = 0f }
        controller.get().setContentView(view)
        controller.visible()
        val fader = OverlayFader(view)
        val looper = shadowOf(Looper.getMainLooper())
        fader.show()
        assertEquals(View.VISIBLE, view.visibility)
        looper.idleFor(Duration.ofMillis(250))
        assertEquals(1f, view.alpha, .01f)
        fader.hide()
        assertFalse(view.isEnabled)
        looper.idleFor(Duration.ofMillis(35))
        fader.show()
        looper.idleFor(Duration.ofMillis(250))
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha, .01f)
        fader.hide()
        looper.idleFor(Duration.ofMillis(250))
        assertEquals(View.GONE, view.visibility)
        controller.pause().stop().destroy()
    }
}
