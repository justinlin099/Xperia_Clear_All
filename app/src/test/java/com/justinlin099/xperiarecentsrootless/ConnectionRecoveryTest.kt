package com.justinlin099.xperiarecentsrootless

import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectionRecoveryTest {
    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    @Test fun retriesLateDependencyWithoutActivityAndStopsOnceConnected() {
        var available = false; var connected = false; var calls = 0
        val window = ConnectionRecovery(Handler(Looper.getMainLooper()), { !connected }, {
            calls++; if (available) connected = true
        }, {})
        window.ensure()
        advance(5); assertEquals(1,calls)
        available = true
        advance(1); assertTrue(connected); assertEquals(2,calls)
        advance(600); assertFalse(window.active); assertEquals(2,calls)
    }

    @Test fun exhaustionDoesNotRestartItselfOrContinuePolling() {
        var calls = 0; var finished = 0
        val window = ConnectionRecovery(Handler(Looper.getMainLooper()), { true }, { calls++ }, { finished++ })
        window.ensure(); advance(60)
        assertEquals(5,calls); assertEquals(1,finished); assertFalse(window.active)
        window.ensure(); advance(3600); assertEquals(5,calls)
        window.resume(); advance(2); assertEquals(6,calls)
        window.stop()
    }

    @Test fun stopRemovesPendingAttemptsAndPermanentFailureStopsEarly() {
        var enabled = true; var calls = 0
        val window = ConnectionRecovery(Handler(Looper.getMainLooper()), { enabled }, { calls++ }, {})
        window.ensure(); window.stop(); advance(600); assertEquals(0,calls)
        window.resume(); advance(2); assertEquals(1,calls)
        enabled = false; advance(4); assertFalse(window.active)
        advance(600); assertEquals(1,calls)
    }

    @Test fun duplicateLifecycleEventsDoNotExtendTheWindow() {
        var calls = 0
        val window = ConnectionRecovery(Handler(Looper.getMainLooper()), { true }, { calls++ }, {})
        window.resume(); advance(1); repeat(10) { window.resume() }
        advance(59); assertEquals(5,calls); assertFalse(window.active)
    }
}
