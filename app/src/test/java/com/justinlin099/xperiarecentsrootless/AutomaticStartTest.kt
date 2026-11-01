package com.justinlin099.xperiarecentsrootless

import android.app.Service
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class AutomaticStartTest {
    @Test fun firstCompletedSetupStartsOnceAndNeverOverridesManualOff() {
        val c=RuntimeEnvironment.getApplication()
        BootRecovery.storage(c).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        assertFalse(AutomaticStart.activateAfterSetup(c,false))
        assertFalse(BootRecovery.storage(c).contains(AutomaticStart.KEY))
        assertNull(shadowOf(c).nextStartedService)
        assertTrue(AutomaticStart.activateAfterSetup(c,true))
        assertTrue(AutomaticStart.enabled(c))
        assertEquals(ShellOverlayService::class.java.name,shadowOf(c).nextStartedService.component!!.className)
        assertFalse(AutomaticStart.activateAfterSetup(c,true))
        assertNull(shadowOf(c).nextStartedService)
        AutomaticStart.remember(c,false)
        assertFalse(AutomaticStart.activateAfterSetup(c,true))
        assertFalse(AutomaticStart.enabled(c))
        assertNull(shadowOf(c).nextStartedService)
    }
    @Test fun bootAndUpgradeRestoreOnlyEnabledAndPermittedService() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("rootless",0).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val receiver=BootReceiver()
        receiver.onReceive(c,Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(c).nextStartedService)
        AutomaticStart.remember(c,true)
        ShadowSettings.setCanDrawOverlays(false)
        receiver.onReceive(c,Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(c).nextStartedService)
        ShadowSettings.setCanDrawOverlays(true)
        receiver.onReceive(c,Intent("unrelated"))
        assertNull(shadowOf(c).nextStartedService)
        for(action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED)) {
            receiver.onReceive(c,Intent(action))
            val start=shadowOf(c).nextStartedService
            assertEquals(ShellOverlayService::class.java.name,start.component!!.className)
            assertEquals(AutomaticStart.RESTORE,start.action)
        }
    }
    @Test fun enabledIntentSurvivesServiceDestructionAndStickyRestartButStopDisables() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("rootless",0).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val first=Robolectric.buildService(ShellOverlayService::class.java).create()
        assertEquals(Service.START_STICKY,first.get().onStartCommand(Intent(c,ShellOverlayService::class.java),0,1))
        assertTrue(AutomaticStart.enabled(c))
        assertFalse(RootlessConnection.ready) // Waiting for Shizuku must not require opening the app.
        assertFalse(AutomaticStart.restore(c)) // Already active: no duplicate service start.
        first.destroy()
        assertTrue(AutomaticStart.enabled(c))
        val restarted=Robolectric.buildService(ShellOverlayService::class.java).create()
        assertEquals(Service.START_STICKY,restarted.get().onStartCommand(null,0,2))
        assertEquals(Service.START_NOT_STICKY,restarted.get().onStartCommand(Intent().setAction(ShellOverlayService.STOP),0,3))
        assertFalse(AutomaticStart.enabled(c))
        restarted.destroy()
        assertFalse(AutomaticStart.restore(c))
    }
    @Test fun disabledServiceCannotBeRevivedByQueuedRestoreOrNullIntent() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,false)
        ShadowSettings.setCanDrawOverlays(true)
        for(intent in listOf(null,Intent().setAction(AutomaticStart.RESTORE))) {
            val controller=Robolectric.buildService(ShellOverlayService::class.java).create()
            assertEquals(Service.START_NOT_STICKY,controller.get().onStartCommand(intent,0,1))
            assertFalse(AutomaticStart.enabled(c))
            controller.destroy()
        }
    }
}
