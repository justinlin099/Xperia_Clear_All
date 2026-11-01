package com.justinlin099.xperiarecentsrootless

import android.app.PendingIntent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import org.robolectric.shadows.ShadowSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class ShizukuRestoreTest {
    private class Worker(val onRestore:(PendingIntent)->Boolean) : IShellTasks.Stub() {
        override fun probe()="{\"ok\":true}"
        override fun clearTasks()="{}"
        override fun startOverviewWatch(listener:IOverviewStateListener?)="{}"
        override fun stopOverviewWatch() {}
        override fun restoreOverlay(startIntent:PendingIntent)=onRestore(startIntent)
        override fun destroy() {}
    }
    @Test fun lateShizukuConnectionRestoresWithoutActivityOrBootBroadcast() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,true);ShadowSettings.setCanDrawOverlays(true)
        RootlessConnection.init(c)
        val delivered=CountDownLatch(1)
        var capability:PendingIntent?=null
        val worker=Worker { capability=it;delivered.countDown();true }
        val field=RootlessConnection::class.java.getDeclaredField("connection").apply { isAccessible=true }
        val connection=field.get(RootlessConnection) as ServiceConnection
        connection.onServiceConnected(ComponentName(c,ShellTasksService::class.java),worker)
        assertTrue("Worker-ready event must restore a saved enabled service",delivered.await(3,TimeUnit.SECONDS))
        val pending=capability!!
        assertEquals(c.packageName,pending.creatorPackage)
        assertTrue(pending.isImmutable);assertTrue(pending.isForegroundService)
        val target=shadowOf(pending).savedIntent
        assertEquals(ShellOverlayService::class.java.name,target.component!!.className)
        assertEquals(AutomaticStart.RESTORE,target.action)
        assertTrue(AutomaticStart.enabled(c))
        // A user can turn off after capability creation: target rejects that stale restore.
        AutomaticStart.remember(c,false)
        val executor=RootlessConnection::class.java.getDeclaredField("worker").apply { isAccessible=true }
            .get(RootlessConnection) as java.util.concurrent.ExecutorService
        executor.submit {}.get(3,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        RootlessConnection.disconnectIfIdle()
    }
    @Test fun disconnectedUserStopAndRevokedOverlayNeverDispatchRestore() {
        val c=RuntimeEnvironment.getApplication()
        var calls=0;val worker=Worker { calls++;true }
        AutomaticStart.remember(c,false);ShadowSettings.setCanDrawOverlays(true)
        assertFalse(AutomaticStart.restoreFromShizuku(c,worker))
        AutomaticStart.remember(c,true);ShadowSettings.setCanDrawOverlays(false)
        assertFalse(AutomaticStart.restoreFromShizuku(c,worker))
        assertEquals(0,calls)
    }
    @Test fun rejectionAndBinderFailureRetainIntentAndRecordRecoveryFailure() {
        val c=RuntimeEnvironment.getApplication()
        AutomaticStart.remember(c,true);ShadowSettings.setCanDrawOverlays(true)
        assertFalse(AutomaticStart.restoreFromShizuku(c,Worker { false }))
        val prefs=c.getSharedPreferences("rootless",0)
        assertTrue(prefs.getString("startupEvent","")!!.contains("REJECTED"))
        assertFalse(AutomaticStart.restoreFromShizuku(c,Worker { throw IllegalStateException("disconnected") }))
        assertTrue(prefs.getString("startupEvent","")!!.contains("ERROR"))
        assertTrue(AutomaticStart.enabled(c))
    }
    @Test fun ordinaryAndRootIdentitiesCannotUseRestoreDispatch() {
        val c=RuntimeEnvironment.getApplication()
        val pending=PendingIntent.getForegroundService(c,99,
            android.content.Intent(c,ShellOverlayService::class.java).setAction(AutomaticStart.RESTORE),PendingIntent.FLAG_IMMUTABLE)
        for(uid in listOf(10000,0)) {
            ShadowProcess.setUid(uid)
            assertFalse(ShellTasksService().restoreOverlay(pending))
        }
    }
    @Test fun shellRejectsMutableAndNonServiceCapabilitiesBeforeAnyCommand() {
        val c=RuntimeEnvironment.getApplication()
        ShadowProcess.setUid(2000)
        val intent=android.content.Intent(c,ShellOverlayService::class.java).setAction(AutomaticStart.RESTORE)
        val broadcast=PendingIntent.getBroadcast(c,101,intent,PendingIntent.FLAG_IMMUTABLE)
        val mutable=PendingIntent.getForegroundService(c,102,intent,PendingIntent.FLAG_MUTABLE)
        assertFalse(ShellTasksService().restoreOverlay(broadcast))
        assertFalse(ShellTasksService().restoreOverlay(mutable))
        assertFalse(ShellTasksService().restoreOverlay(null))
    }

}
