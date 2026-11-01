package com.justinlin099.xperiarecentsrootless

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],qualifiers="b+zh+Hant-w393dp-h851dp-xxhdpi")
class RecoveryFlowTest {
    private val c get()=RuntimeEnvironment.getApplication()
    private val manager get()=c.getSystemService(NotificationManager::class.java)
    private fun children(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    @Before fun reset() {
        AutomaticStart.remember(c,false)
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
        RecoveryNotifications.ensure(c)
    }
    @Test fun classifierSeparatesInstallationAuthorizationModeAndWorkerFailures() {
        val base=ShizukuRecovery.Snapshot(true,true,true)
        assertEquals(ShizukuIssue.MISSING,ShizukuRecovery.classify(base.copy(installed=false)))
        assertEquals(ShizukuIssue.STOPPED,ShizukuRecovery.classify(base.copy(running=false)))
        assertEquals(ShizukuIssue.PERMISSION,ShizukuRecovery.classify(base.copy(granted=false)))
        assertEquals(ShizukuIssue.WRONG_MODE,ShizukuRecovery.classify(base.copy(uid=0)))
        assertEquals(ShizukuIssue.OLD_VERSION,ShizukuRecovery.classify(base.copy(version=12)))
        assertEquals(ShizukuIssue.CONNECTING,ShizukuRecovery.classify(base))
        assertEquals(ShizukuIssue.WORKER_ERROR,ShizukuRecovery.classify(base.copy(failed=true)))
        assertEquals(ShizukuIssue.WATCH_ERROR,ShizukuRecovery.classify(base.copy(ready=true,watchFailed=true)))
        assertEquals(ShizukuIssue.READY,ShizukuRecovery.classify(base.copy(ready=true)))
    }
    @Test fun alertRoutesThroughOurAppIsDeduplicatedAndClearsOnRecoveryOrStop() {
        RecoveryNotifications.update(c,ShizukuIssue.STOPPED)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
        AutomaticStart.remember(c,true)
        RecoveryNotifications.update(c,ShizukuIssue.STOPPED)
        val alert=shadowOf(manager).getNotification(RecoveryNotifications.ID)
        assertNotNull(alert)
        val target=shadowOf(alert.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name,target.component!!.className)
        assertTrue(target.getBooleanExtra(ShizukuRecovery.EXTRA,false))
        assertTrue(alert.contentIntent.isImmutable)
        assertEquals(RecoveryNotifications.CHANNEL,alert.channelId)
        RecoveryNotifications.update(c,ShizukuIssue.STOPPED)
        assertSame(alert,shadowOf(manager).getNotification(RecoveryNotifications.ID))
        RecoveryNotifications.update(c,ShizukuIssue.READY)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
        RecoveryNotifications.update(c,ShizukuIssue.PERMISSION)
        assertNotNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
        AutomaticStart.remember(c,false)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
    }
    @Test fun deniedPermissionAndDisabledNotificationsNeverPostOrCrash() {
        AutomaticStart.remember(c,true)
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(RecoveryNotifications.canAlert(c))
        RecoveryNotifications.update(c,ShizukuIssue.MISSING)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(false)
        RecoveryNotifications.update(c,ShizukuIssue.MISSING)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,RecoveryNotifications.settings(c).action)
        shadowOf(manager).setNotificationsEnabled(true)
        RecoveryNotifications.update(c,ShizukuIssue.MISSING)
        assertNotNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
    }
    @Test fun blockedChannelOffersChannelSettingsAndConnectingDoesNotCreateAlert() {
        manager.deleteNotificationChannel(RecoveryNotifications.CHANNEL)
        manager.createNotificationChannel(NotificationChannel(RecoveryNotifications.CHANNEL,"blocked",NotificationManager.IMPORTANCE_NONE))
        AutomaticStart.remember(c,true)
        assertFalse(RecoveryNotifications.canAlert(c))
        assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,RecoveryNotifications.settings(c).action)
        RecoveryNotifications.update(c,ShizukuIssue.STOPPED)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
        RecoveryNotifications.update(c,ShizukuIssue.CONNECTING)
        assertNull(shadowOf(manager).getNotification(RecoveryNotifications.ID))
    }
    @Test fun playStoreFallsBackToBrowserAndAllMissingHandlersReturnSafely() {
        val attempts=mutableListOf<Intent>()
        val noPlay=object:ContextWrapper(c) {
            override fun startActivity(intent:Intent) {
                attempts.add(intent)
                if(intent.`package`=="com.android.vending") throw ActivityNotFoundException()
            }
        }
        assertTrue(ShizukuRecovery.play(noPlay))
        assertEquals(2,attempts.size)
        assertEquals(ShizukuRecovery.PLAY,attempts.last().dataString)
        val none=object:ContextWrapper(c) {
            override fun startActivity(intent:Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(ShizukuRecovery.play(none))
        assertFalse(ShizukuRecovery.open(none)) // Missing installation follows the same safe download path.
    }
    @Test fun installedButUnlaunchableManagerOpensItsOwnAppInfo() {
        shadowOf(c.packageManager).addPackage(ShizukuRecovery.PACKAGE)
        val attempts=mutableListOf<Intent>()
        val target=object:ContextWrapper(c) { override fun startActivity(intent:Intent) { attempts.add(intent) } }
        assertTrue(ShizukuRecovery.open(target))
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,attempts.last().action)
        assertEquals("package:"+ShizukuRecovery.PACKAGE,attempts.last().dataString)
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun firstRunGroupsOverlayHelpWithItsPermissionAndHasOneMainSwitch() {
        BootRecovery.storage(c).edit().clear().commit()
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        shadowOf(c.getSystemService(android.os.PowerManager::class.java)).setIgnoringBatteryOptimizations(c.packageName,false)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val a=controller.get();val root=a.window.decorView
        val cards=listOf("permission-setup-card","overlay-setup","background-start-card","notification-permission-card").map { root.findViewWithTag<View>(it) }
        assertEquals(4,cards.toSet().size)
        cards.forEachIndexed { i,v ->
            assertTrue(v is MaterialCardView);assertEquals(View.VISIBLE,v.visibility)
            assertTrue(children(v).filterIsInstance<TextView>().first().text.toString().startsWith("${i+1}."))
        }
        val restricted=root.findViewWithTag<View>("restricted-settings-card")
        assertEquals(View.GONE,restricted.visibility)
        assertTrue(children(cards[1]).contains(restricted))
        assertTrue(root.findViewWithTag<MaterialSwitch>("monitoring-switch").isEnabled)
        assertEquals(1,children(root).filterIsInstance<MaterialSwitch>().size)
        assertEquals(a.getString(R.string.get_shizuku),root.findViewWithTag<MaterialButton>("recovery-primary").text)
        assertTrue(root.findViewWithTag<TextView>("recovery-instructions").text.contains(a.getString(R.string.return_and_connect)))
        root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2553,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,2553)
        val body=children(root).filterIsInstance<android.widget.ScrollView>().first().getChildAt(0)
        children(body).forEach { it.setLayerType(View.LAYER_TYPE_SOFTWARE,null) }
        val bitmap=android.graphics.Bitmap.createBitmap(body.width,body.height,android.graphics.Bitmap.Config.ARGB_8888)
        body.draw(android.graphics.Canvas(bitmap))
        val encoded=java.io.ByteArrayOutputStream();assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,encoded));bitmap.recycle()
        File("build/ui-preview/first-run-recovery-zh.png").apply { parentFile.mkdirs();writeBytes(encoded.toByteArray()) }
        controller.pause().stop().destroy()
    }
    @Test fun notificationPermissionButtonRequestsOnlyOnUserActionAndDeepLinkUsesCurrentState() {
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val target=Intent(c,MainActivity::class.java).putExtra(ShizukuRecovery.EXTRA,true)
        val controller=Robolectric.buildActivity(MainActivity::class.java,target).setup()
        val a=controller.get();val root=a.window.decorView
        assertNull(shadowOf(a).lastRequestedPermission)
        val card=root.findViewWithTag<View>("notification-permission-card")
        children(card).filterIsInstance<MaterialButton>().first().performClick()
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS),shadowOf(a).lastRequestedPermission.requestedPermissions)
        assertFalse(a.intent.hasExtra(ShizukuRecovery.EXTRA))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(a.getString(R.string.shizuku_not_installed),children(root).filterIsInstance<TextView>().first { it.text==a.getString(R.string.shizuku_not_installed) }.text)
        controller.pause().stop().destroy()
    }
}
