package com.justinlin099.xperiarecentsrootless

import android.app.NotificationChannel
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class ShellServiceNotificationTest {
    private class Channels(var service:NotificationChannel?):ShellServiceNotification.Channels {
        val recovery=NotificationChannel(RecoveryNotifications.CHANNEL,"Alerts",NotificationManager.IMPORTANCE_DEFAULT)
        var writes=0
        override fun read(uid:Int)=service
        override fun update(uid:Int,channel:NotificationChannel) {
            assertEquals(ShellServiceNotification.CHANNEL,channel.id)
            writes++;service=channel
        }
    }
    @Test fun authorizedShellOnlyBlocksServiceChannelAndRepeatedCallsAreIdempotent() {
        val c=RuntimeEnvironment.getApplication();val appUid=c.applicationInfo.uid
        val channels=Channels(NotificationChannel(ShellServiceNotification.CHANNEL,"Service",NotificationManager.IMPORTANCE_LOW))
        ShadowProcess.setUid(2000)
        assertTrue(ShellServiceNotification.hide(c,appUid,channels))
        assertEquals(NotificationManager.IMPORTANCE_NONE,channels.service!!.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,channels.recovery.importance)
        assertTrue(ShellServiceNotification.hide(c,appUid,channels))
        assertEquals(1,channels.writes)
    }
    @Test fun rootOrdinaryAppAndUnrelatedTargetAreRejectedBeforeMutation() {
        val c=RuntimeEnvironment.getApplication();val appUid=c.applicationInfo.uid
        val channels=Channels(NotificationChannel(ShellServiceNotification.CHANNEL,"Service",NotificationManager.IMPORTANCE_LOW))
        for(uid in listOf(0,10000)) {
            ShadowProcess.setUid(uid)
            assertThrows(IllegalStateException::class.java) { ShellServiceNotification.hide(c,appUid,channels) }
        }
        ShadowProcess.setUid(2000)
        assertThrows(IllegalStateException::class.java) { ShellServiceNotification.hide(c,99999,channels) }
        assertEquals(0,channels.writes)
    }
    @Test fun missingOrUnexpectedChannelNeverChangesRecoveryChannel() {
        val c=RuntimeEnvironment.getApplication();val appUid=c.applicationInfo.uid
        ShadowProcess.setUid(2000)
        val channels=Channels(null)
        assertFalse(ShellServiceNotification.hide(c,appUid,channels))
        channels.service=channels.recovery
        assertThrows(IllegalStateException::class.java) { ShellServiceNotification.hide(c,appUid,channels) }
        assertEquals(0,channels.writes)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,channels.recovery.importance)
    }
}
