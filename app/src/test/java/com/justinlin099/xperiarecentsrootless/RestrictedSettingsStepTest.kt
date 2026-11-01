package com.justinlin099.xperiarecentsrootless

import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
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
class RestrictedSettingsStepTest {
    @Test fun overlayVisitPrecedesRestrictedHelpAndReturnsToPermissionScreen() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("rootless",0).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(false)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get();val root=a.window.decorView
        val card=root.findViewWithTag<View>("restricted-settings-card")
        val overlay=root.findViewWithTag<MaterialButton>("overlay-permission-action")
        assertEquals(View.GONE,card.visibility);assertTrue(overlay.isEnabled)
        overlay.performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(a).nextStartedActivity.action)
        controller.pause().resume()
        assertEquals(View.VISIBLE,card.visibility)
        val help=card as android.view.ViewGroup
        fun descendants(v:View):List<View> = listOf(v)+if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        descendants(help).filterIsInstance<MaterialButton>().first().performClick()
        val target=shadowOf(a).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,target.action)
        assertEquals("package:"+a.packageName,target.dataString)
        root.findViewWithTag<MaterialButton>("restricted-settings-done").performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(a).nextStartedActivity.action)
        ShadowSettings.setCanDrawOverlays(true)
        controller.pause().resume()
        assertEquals(View.GONE,card.visibility)
        controller.pause().stop().destroy()
    }
    @Test fun existingOverlayPermissionSkipsReviewWithoutChangingMasterState() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,true)
        RestrictedSettingsStep.visitOverlay(c)
        ShadowSettings.setCanDrawOverlays(true)
        assertFalse(RestrictedSettingsStep.needsReview(c))
        ShadowSettings.setCanDrawOverlays(false)
        BootRecovery.afterBoot(c,Intent.ACTION_MY_PACKAGE_REPLACED)
        assertTrue(AutomaticStart.enabled(c))
    }
    @Test @Config(sdk=[32]) fun versionsWithoutRestrictedSettingsNeedNoConfirmation() {
        val c=RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(false)
        RestrictedSettingsStep.visitOverlay(c)
        assertFalse(RestrictedSettingsStep.needsReview(c))
    }
}
