package com.justinlin099.xperiarecentsrootless

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Binder
import android.os.Parcel
import org.robolectric.shadows.ShadowBinder
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSettings
import rikka.shizuku.Shizuku
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Implements(value = Shizuku::class, isInAndroidSdk = false)
class StartupShizukuShadow {
    companion object {
        var running = false
        var grant = true
        var stallFirst = false
        var binds = 0
        var restored = CountDownLatch(1)
        private val listeners = mutableListOf<Shizuku.OnBinderReceivedListener>()
        @JvmStatic @Implementation fun addBinderReceivedListenerSticky(listener: Shizuku.OnBinderReceivedListener) {
            listeners.add(listener)
            if (running) listener.onBinderReceived()
        }
        @JvmStatic @Implementation fun onBinderReceived(binder: IBinder, packageName: String) {
            running = true
            listeners.toList().forEach { it.onBinderReceived() }
        }
        private val worker = object : IShellTasks.Stub() {
            override fun probe() = "{\"ok\":true}"
            override fun clearTasks() = "{}"
            override fun startOverviewWatch(listener: IOverviewStateListener?) = "{\"ok\":true}"
            override fun stopOverviewWatch() {}
            override fun restoreOverlay(startIntent: PendingIntent): Boolean { restored.countDown(); return true }
            override fun destroy() {}
        }
        @JvmStatic @Implementation fun pingBinder() = running
        @JvmStatic @Implementation fun checkSelfPermission() = if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        @JvmStatic @Implementation fun getUid() = 2000
        @JvmStatic @Implementation fun getVersion() = 13
        @JvmStatic @Implementation fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) {}
        @JvmStatic @Implementation fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
            binds++
            if (stallFirst && binds == 1) return
            Handler(Looper.getMainLooper()).post { connection.onServiceConnected(ComponentName("test","worker"),worker) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [StartupShizukuShadow::class])
class LateShizukuRecoveryTest {
    private val c get() = RuntimeEnvironment.getApplication()
    private val notifications get() = shadowOf(c.getSystemService(NotificationManager::class.java))
    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    @After fun cleanup() {
        AutomaticStart.remember(c,false)
        val executor = RootlessConnection::class.java.getDeclaredField("worker").apply { isAccessible=true }
            .get(RootlessConnection) as java.util.concurrent.ExecutorService
        executor.submit {}.get(3,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        RootlessConnection.disconnectIfIdle()
    }
    @Before fun setup() {
        RootlessConnection.init(c)
        cleanup()
        StartupShizukuShadow.running = false; StartupShizukuShadow.grant = true
        StartupShizukuShadow.stallFirst = false; StartupShizukuShadow.binds = 0
        StartupShizukuShadow.restored = CountDownLatch(1)
        shadowOf(c.packageManager).addPackage(ShizukuRecovery.PACKAGE)
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.setNotificationsEnabled(true)
        ShadowSettings.setCanDrawOverlays(true)
        AutomaticStart.remember(c,true)
        RootlessConnection.init(c)
    }
    @Test fun lateShizukuWithoutReadyCallbackConnectsAndRestoresWithoutOpeningApp() {
        RootlessConnection.resume("boot-test")
        assertTrue(RootlessConnection.recovering)
        val waiting=notifications.getNotification(RecoveryNotifications.ID)!!
        assertEquals(Notification.CATEGORY_PROGRESS,waiting.category)
        assertTrue(waiting.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(c.getString(R.string.waiting),waiting.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        advance(5)
        StartupShizukuShadow.running = true // No binder-ready event or Activity resume.
        advance(1)
        assertTrue(RootlessConnection.ready)
        assertFalse(RootlessConnection.recovering)
        assertEquals(1,StartupShizukuShadow.binds)
        assertTrue(StartupShizukuShadow.restored.await(3,TimeUnit.SECONDS))
        assertNull(notifications.getNotification(RecoveryNotifications.ID))
    }
    @Test fun missingBinderIsActivelyRequestedAndHandedToSdkWithoutActivity() {
        RootlessConnection.resume("boot-test")
        advance(5)
        assertFalse(StartupShizukuShadow.running)
        assertEquals(0,StartupShizukuShadow.binds)
        val request=shadowOf(c).broadcastIntents.last { it.action==ShizukuBinderRequest.ACTION }
        assertEquals(ShizukuRecovery.PACKAGE,request.`package`)
        val callback=request.getBundleExtra("data")!!.getBinder("binder")!!
        val parcel=Parcel.obtain()
        try {
            parcel.writeStrongBinder(Binder()); parcel.writeString("/ignored/manager.apk")
            ShadowBinder.setCallingUid(c.packageManager.getApplicationInfo(ShizukuRecovery.PACKAGE,0).uid)
            assertTrue(callback.transact(1,parcel,null,IBinder.FLAG_ONEWAY))
        } finally { parcel.recycle(); ShadowBinder.reset() }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(RootlessConnection.ready)
        assertEquals(1,StartupShizukuShadow.binds)
        assertTrue(StartupShizukuShadow.restored.await(3,TimeUnit.SECONDS))
        assertNull(notifications.getNotification(RecoveryNotifications.ID))
        assertNull(shadowOf(c).nextStartedActivity)
    }
    @Test fun timedOutWorkerBindingRetriesAutomatically() {
        StartupShizukuShadow.running = true; StartupShizukuShadow.stallFirst = true
        RootlessConnection.resume("boot-test")
        advance(14)
        assertTrue(RootlessConnection.ready)
        assertEquals(2,StartupShizukuShadow.binds)
    }
    @Test fun genuineFailureAlertsAfterGraceButManualOffCancelsRetry() {
        RootlessConnection.resume("boot-test")
        advance(59)
        val waiting=notifications.getNotification(RecoveryNotifications.ID)!!
        assertEquals(Notification.CATEGORY_PROGRESS,waiting.category)
        advance(1)
        val failed=notifications.getNotification(RecoveryNotifications.ID)!!
        assertEquals(Notification.CATEGORY_ERROR,failed.category)
        assertEquals(0,failed.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(1,notifications.allNotifications.size)
        assertFalse(RootlessConnection.recovering)
        RootlessConnection.resume("unlock-test")
        assertTrue(RootlessConnection.recovering)
        AutomaticStart.remember(c,false)
        StartupShizukuShadow.running = true
        advance(600)
        assertEquals(0,StartupShizukuShadow.binds)
        assertNull(notifications.getNotification(RecoveryNotifications.ID))
    }
    @Test fun revokedAuthorizationIsActionableImmediately() {
        StartupShizukuShadow.running = true; StartupShizukuShadow.grant = false
        RootlessConnection.resume("boot-test")
        assertFalse(RootlessConnection.recovering)
        assertNotNull(notifications.getNotification(RecoveryNotifications.ID))
        advance(600); assertEquals(0,StartupShizukuShadow.binds)
    }
}
