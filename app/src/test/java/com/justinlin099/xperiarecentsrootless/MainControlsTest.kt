package com.justinlin099.xperiarecentsrootless

import android.view.View
import android.widget.CompoundButton
import com.google.android.material.materialswitch.MaterialSwitch
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
class MainControlsTest {
    @Test fun offHidesConnectionWarningAndReenableStartsServiceWhileWorkerReconnects() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,false)
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get();val root=a.window.decorView
        val toggle=root.findViewWithTag<MaterialSwitch>("monitoring-switch")
        val setup=root.findViewWithTag<View>("permission-setup-card")
        assertTrue(toggle.isEnabled);assertFalse(toggle.isChecked)
        assertEquals(View.GONE,setup.visibility)
        toggle.performClick()
        assertTrue(toggle.isChecked);assertTrue(AutomaticStart.enabled(c))
        assertEquals(ShellOverlayService::class.java.name,shadowOf(c).nextStartedService.component!!.className)
        toggle.performClick()
        assertFalse(AutomaticStart.enabled(c));assertTrue(toggle.isEnabled)
        assertEquals(View.GONE,setup.visibility)
        val connection=RootlessConnection::class.java.getDeclaredField("connection").apply { isAccessible=true }.get(RootlessConnection) as android.content.ServiceConnection
        connection.onServiceDisconnected(null) // A callback from the intentional unbind is not a worker failure.
        assertFalse(RootlessConnection.workerFailed)
        controller.pause().stop().destroy()
    }
    @Test fun nativeMaterialSwitchHasStateIconsAndBarUsesCurrentThemeRoles() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,false)
        ShadowSettings.setCanDrawOverlays(false)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get();val root=a.window.decorView
        val toggle=root.findViewWithTag<MaterialSwitch>("monitoring-switch")
        val bar=root.findViewWithTag<com.google.android.material.card.MaterialCardView>("enable-bar")
        fun theme(attr:Int)=com.google.android.material.color.MaterialColors.getColor(a,attr,"test")
        assertNotNull(toggle.thumbIconDrawable)
        assertTrue(toggle.thumbIconDrawable!!.isStateful)
        assertEquals(theme(com.google.android.material.R.attr.colorSurfaceContainerHighest),bar.cardBackgroundColor.defaultColor)
        AutomaticStart.remember(c,true)
        controller.pause().resume()
        assertTrue(toggle.isChecked)
        assertEquals(theme(com.google.android.material.R.attr.colorPrimaryContainer),bar.cardBackgroundColor.defaultColor)
        assertEquals(theme(com.google.android.material.R.attr.colorOnPrimaryContainer),toggle.currentTextColor)
        controller.pause().stop().destroy()
    }
    @Test fun mainPageOrdersTitlePreviewSwitchAndSettingsWithoutDuplicatePreview() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val root=controller.get().window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY))
        root.layout(0,0,1080,2400)
        val title=root.findViewWithTag<View>("screen-title")
        val preview=root.findViewWithTag<View>("main-preview")
        val bar=root.findViewWithTag<View>("enable-bar")
        val setup=root.findViewWithTag<View>("permission-setup-card")
        assertSame(title.parent,preview.parent);assertSame(preview.parent,bar.parent)
        assertTrue(title.bottom<=preview.top);assertTrue(preview.bottom<=bar.top);assertTrue(bar.bottom<=setup.top)
        assertTrue(bar is com.google.android.material.card.MaterialCardView)
        assertSame(bar,root.findViewWithTag<MaterialSwitch>("monitoring-switch").parent)
        fun descendants(v:View):List<View> = listOf(v)+if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        val settings=descendants(root).filterIsInstance<AppearanceSettings>().single()
        assertFalse((0 until settings.childCount).any { settings.getChildAt(it) is RecentsPreviewView })
        controller.pause().stop().destroy()
    }
    @Test fun completedSetupSectionsDisappearAndReturnIndependently() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get(); val root=a.window.decorView
        val card=root.findViewWithTag<View>("permission-setup-card")
        val shizuku=root.findViewWithTag<View>("shizuku-setup")
        val overlay=root.findViewWithTag<View>("overlay-setup")
        a.updateSetupVisibility(true,false)
        assertEquals(View.GONE,shizuku.visibility);assertEquals(View.VISIBLE,overlay.visibility)
        assertEquals(View.GONE,card.visibility)
        a.updateSetupVisibility(true,true)
        assertEquals(View.GONE,card.visibility)
        a.updateSetupVisibility(false,true)
        assertEquals(View.VISIBLE,card.visibility);assertEquals(View.VISIBLE,shizuku.visibility)
        assertEquals(View.GONE,overlay.visibility)
        a.updateSetupVisibility(false,false)
        assertEquals(View.VISIBLE,overlay.visibility)
        controller.pause().stop().destroy()
    }
    @Test fun mainSwitchReflectsSavedIntentWithoutStartingAndCanDisableWhileDisconnected() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,true)
        ShadowSettings.setCanDrawOverlays(false) // No automatic restore during test.
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val toggle=controller.get().window.decorView.findViewWithTag<MaterialSwitch>("monitoring-switch")
        assertTrue(toggle.isChecked)
        assertNull(shadowOf(c).nextStartedService) // Rendering checked state must not fire enable action.
        toggle.performClick()
        assertFalse(toggle.isChecked);assertFalse(AutomaticStart.enabled(c))
        toggle.performClick() // Re-enable checks prerequisites and opens permission settings without locking the switch.
        assertTrue(toggle.isChecked);assertTrue(AutomaticStart.enabled(c))
        assertTrue(toggle.isEnabled)
        assertEquals(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(controller.get()).nextStartedActivity.action)
        assertNull(shadowOf(c).nextStartedService)
        controller.pause().stop().destroy()
    }
    @Test fun backgroundSetupUsesNativeSettingsAndHidesAfterPermission() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,true)
        ShadowSettings.setCanDrawOverlays(false)
        val power=shadowOf(c.getSystemService(android.os.PowerManager::class.java))
        power.setIgnoringBatteryOptimizations(c.packageName,false)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get()
        val card=a.window.decorView.findViewWithTag<View>("background-start-card") as android.view.ViewGroup
        assertEquals(View.VISIBLE,card.visibility)
        fun descendants(v:View):List<View> = listOf(v)+if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        descendants(card).filterIsInstance<com.google.android.material.button.MaterialButton>().first().performClick()
        val target=shadowOf(a).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,target.action)
        assertEquals("package:"+a.packageName,target.dataString)
        power.setIgnoringBatteryOptimizations(c.packageName,true)
        controller.pause().resume()
        assertEquals(View.GONE,card.visibility)
        controller.pause().stop().destroy()
    }

}
