package com.justinlin099.xperiarecentsrootless

import android.content.Intent
import android.content.DialogInterface
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowDialog

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
        assertNull("Settings must wait for the screenshot guide confirmation",shadowOf(a).nextStartedActivity)
        assertFalse(RestrictedSettingsStep.needsReview(c))
        val overlayGuide=ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(2,screenshots(overlayGuide).size)
        continueGuide(overlayGuide)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(a).nextStartedActivity.action)
        controller.pause().resume()
        assertEquals(View.VISIBLE,card.visibility)
        assertTrue(descendantOf(card,root.findViewWithTag("overlay-setup")))
        val help=card as android.view.ViewGroup
        fun descendants(v:View):List<View> = listOf(v)+if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        descendants(help).filterIsInstance<MaterialButton>().first().performClick()
        assertNull(shadowOf(a).nextStartedActivity)
        val infoGuide=ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(1,screenshots(infoGuide).size)
        continueGuide(infoGuide)
        val target=shadowOf(a).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,target.action)
        assertEquals("package:"+a.packageName,target.dataString)
        controller.pause().resume()
        assertEquals("Opening App info must not count as granting overlay access",View.VISIBLE,card.visibility)
        root.findViewWithTag<MaterialButton>("restricted-settings-done").performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(a).nextStartedActivity.action)
        ShadowSettings.setCanDrawOverlays(true)
        controller.pause().resume()
        assertEquals(View.GONE,card.visibility)
        controller.pause().stop().destroy()
    }
    @Test fun cancellingScreenshotGuideDoesNotCountAsVisitingSettings() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("rootless",0).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(false)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get();val root=a.window.decorView
        val overlay=root.findViewWithTag<MaterialButton>("overlay-permission-action")
        overlay.performClick()
        val guide=ShadowDialog.getLatestDialog() as AlertDialog
        overlay.performClick()
        assertSame("Repeated taps must not stack screenshot dialogs",guide,ShadowDialog.getLatestDialog())
        guide.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        controller.pause().resume()
        assertFalse(guide.isShowing)
        assertFalse(RestrictedSettingsStep.needsReview(c))
        assertEquals(View.GONE,root.findViewWithTag<View>("restricted-settings-card").visibility)
        assertNull(shadowOf(a).nextStartedActivity)
        controller.pause().stop().destroy()
    }
    @Test fun permissionRetryDoesNotRequireConfirmingRestrictedSettings() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("rootless",0).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(false)
        RestrictedSettingsStep.visitOverlay(c)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get();val root=a.window.decorView
        val retry=root.findViewWithTag<MaterialButton>("restricted-settings-done")
        assertTrue(retry.isEnabled)
        retry.performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(a).nextStartedActivity.action)
        controller.pause().resume()
        assertEquals(View.VISIBLE,root.findViewWithTag<View>("restricted-settings-card").visibility)
        ShadowSettings.setCanDrawOverlays(true)
        controller.pause().resume()
        assertEquals(View.GONE,root.findViewWithTag<View>("overlay-setup").visibility)
        assertEquals(View.GONE,root.findViewWithTag<View>("restricted-settings-card").visibility)
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

    private fun descendantOf(view:View,ancestor:View):Boolean {
        var parent=view.parent
        while(parent is View) {
            if(parent===ancestor) return true
            parent=parent.parent
        }
        return false
    }
    private fun continueGuide(dialog:AlertDialog) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun screenshots(dialog:AlertDialog):List<ImageView> {
        fun children(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
        return children(dialog.window!!.decorView).filterIsInstance<ImageView>().filter {
            it.tag=="permission-guide-screenshot" && it.drawable!=null
        }
    }
}
