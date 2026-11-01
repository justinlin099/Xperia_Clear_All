package com.justinlin099.xperiarecentsrootless

import android.content.DialogInterface
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class SupportLinksTest {
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun click(dialog:AlertDialog,button:Int) {
        dialog.getButton(button).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    @Test fun eachFreshLaunchAsksAndDonateOpensOnlyTheRequestedUrl() {
        repeat(2) { turn ->
            val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
            val a=controller.get()
            val dialog=ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertEquals(a.getString(R.string.support_message),dialog.findViewById<TextView>(android.R.id.message)!!.text.toString())
            assertNull(shadowOf(a).nextStartedActivity)
            if(turn==0) {
                click(dialog,DialogInterface.BUTTON_POSITIVE)
                val intent=shadowOf(a).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW,intent.action)
                assertEquals(MainActivity.DONATION_URL,intent.dataString)
            } else click(dialog,DialogInterface.BUTTON_NEGATIVE)
            assertFalse(dialog.isShowing)
            controller.pause().stop().destroy()
        }
    }
    @Test fun foregroundReentryAsksButExternalReturnDoesNotImmediatelyAskAgain() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val first=ShadowDialog.getLatestDialog() as AlertDialog
        click(first,DialogInterface.BUTTON_NEGATIVE)
        controller.pause().stop().start().resume()
        val second=ShadowDialog.getLatestDialog() as AlertDialog
        assertNotSame(first,second);assertTrue(second.isShowing)
        click(second,DialogInterface.BUTTON_POSITIVE)
        controller.pause().stop().start().resume()
        assertSame(second,ShadowDialog.getLatestDialog())
        assertFalse(second.isShowing)
        controller.pause().stop().destroy()
    }
    @Test fun supportGitHubAndCreditOccupyRequestedPositions() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val a=controller.get();val root=a.window.decorView
        click(ShadowDialog.getLatestDialog() as AlertDialog,DialogInterface.BUTTON_NEGATIVE)
        val appearance=descendants(root).filterIsInstance<AppearanceSettings>().single().parent.parent as View
        val support=root.findViewWithTag<View>("support-card")
        val language=root.findViewWithTag<View>("language-card")
        val diagnostics=root.findViewWithTag<View>("diagnostics-card")
        val github=root.findViewWithTag<View>("github-action")
        val credit=root.findViewWithTag<TextView>("author-credit")
        val body=support.parent as ViewGroup
        assertSame(body,appearance.parent)
        assertTrue(body.indexOfChild(appearance)<body.indexOfChild(support))
        assertTrue(body.indexOfChild(support)<body.indexOfChild(language))
        assertTrue(body.indexOfChild(diagnostics)<body.indexOfChild(github))
        assertTrue(body.indexOfChild(github)<body.indexOfChild(credit))
        assertEquals("Made with ❤️ by JustinLin099",credit.text.toString())
        github.performClick()
        assertEquals("https://github.com/justinlin099/Xperia_Clear_All",shadowOf(a).nextStartedActivity.dataString)
        controller.pause().stop().destroy()
    }
}
