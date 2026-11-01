package com.justinlin099.xperiarecentsrootless

import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResourceUsageTest {
    private val c get() = RuntimeEnvironment.getApplication()
    @Test fun stoppedSettingsUnsubscribesAndReturningRestoresSubscription() {
        AutomaticStart.remember(c,false)
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity=controller.get()
        val observer=MainActivity::class.java.getDeclaredField("observer").apply { isAccessible=true }.get(activity)
        assertTrue(RootlessConnection.observers.contains(observer))
        controller.pause().stop()
        assertFalse(RootlessConnection.observers.contains(observer))
        controller.restart().start().resume()
        assertTrue(RootlessConnection.observers.contains(observer))
        controller.pause().stop().destroy()
        assertFalse(RootlessConnection.observers.contains(observer))
    }

    @Test fun overviewEventNotifiesOnceAndOnlyHealthChangesTouchRecoveryChannel() {
        AutomaticStart.remember(c,false)
        RootlessConnection.init(c)
        RootlessConnection.watchProblem(false)
        val notifications=c.getSystemService(NotificationManager::class.java)
        notifications.deleteNotificationChannel(RecoveryNotifications.CHANNEL)
        var updates=0
        val observer:()->Unit={ updates++ }
        RootlessConnection.observers.add(observer)
        try {
            RootlessConnection.overviewEvent("OVERVIEW","test","visible")
            assertEquals(1,updates)
            assertNull(notifications.getNotificationChannel(RecoveryNotifications.CHANNEL))
            RootlessConnection.overviewEvent("STREAM_ERROR","test","error")
            assertEquals(2,updates)
            assertNotNull(notifications.getNotificationChannel(RecoveryNotifications.CHANNEL))
        } finally {
            RootlessConnection.observers.remove(observer)
            RootlessConnection.watchProblem(false)
        }
    }

    private fun set(watch:ShellOverviewWatch,name:String,value:Any) {
        ShellOverviewWatch::class.java.getDeclaredField(name).apply { isAccessible=true }.set(watch,value)
    }
    @Test fun outsideOverviewSkipsFocusQueriesButVisibleOverviewStillHidesOnDeparture() {
        val watch=ShellOverviewWatch(c)
        val events=mutableListOf<String?>()
        val callback=object:IOverviewStateListener.Stub() {
            override fun onState(visible:Boolean,state:String?,detail:String?) { events+=state }
        }
        var queries=0
        val iface=Class.forName("android.app.IActivityTaskManager")
        val manager=Proxy.newProxyInstance(iface.classLoader,arrayOf(iface)) { _,method,_ ->
            if(method.name=="getFocusedRootTaskInfo") queries++
            null
        }
        set(watch,"manager",manager);set(watch,"callback",callback)
        val check=ShellOverviewWatch::class.java.getDeclaredField("checkFocus").apply { isAccessible=true }.get(watch) as Runnable
        try {
            repeat(100) { check.run() }
            assertEquals(0,queries);assertTrue(events.isEmpty())
            set(watch,"overviewRequested",true)
            check.run()
            assertEquals(1,queries);assertEquals(listOf("OUTSIDE_SONY"),events)
            repeat(100) { check.run() }
            assertEquals(1,queries)
        } finally { watch.close() }
    }

    @Test fun repeatedOverviewEventCanRestoreButtonAfterExternalScreenOffHide() {
        val watch=ShellOverviewWatch(c)
        var showing=false
        set(watch,"callback",object:IOverviewStateListener.Stub() {
            override fun onState(visible:Boolean,state:String?,detail:String?) { showing=visible }
        })
        val publish=ShellOverviewWatch::class.java.getDeclaredMethod("publish",Boolean::class.javaPrimitiveType,String::class.java,String::class.java).apply { isAccessible=true }
        try {
            publish.invoke(watch,true,"OVERVIEW","tasks")
            assertTrue(showing)
            showing=false // App process hides on ACTION_SCREEN_OFF, independently of shell state.
            publish.invoke(watch,true,"OVERVIEW","tasks")
            assertTrue(showing)
            publish.invoke(watch,false,"TASK_LAUNCH_START","launch")
            assertFalse(showing)
            publish.invoke(watch,false,"STREAM_ERROR","lost")
            assertFalse(showing)
        } finally { watch.close() }
    }
}
