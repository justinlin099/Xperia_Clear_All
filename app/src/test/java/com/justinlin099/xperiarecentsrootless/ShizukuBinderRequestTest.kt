package com.justinlin099.xperiarecentsrootless

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShizukuBinderRequestTest {
    private val c get() = RuntimeEnvironment.getApplication()
    private val requests get() = shadowOf(c).broadcastIntents.filter { it.action == ShizukuBinderRequest.ACTION }
    @Before fun managerInstalled() {
        shadowOf(c.packageManager).installPackage(PackageInfo().apply {
            packageName = ShizukuRecovery.PACKAGE
            applicationInfo = ApplicationInfo().apply { packageName = ShizukuRecovery.PACKAGE; uid = 12345 }
        })
    }
    @After fun resetCaller() { ShadowBinder.reset() }
    private fun reply(intent: Intent, uid: Int, binder: IBinder?): Boolean {
        val callback = intent.getBundleExtra("data")!!.getBinder("binder")!!
        val data = Parcel.obtain()
        return try {
            data.writeStrongBinder(binder); data.writeString("/ignored/manager.apk")
            ShadowBinder.setCallingUid(uid)
            callback.transact(1, data, null, IBinder.FLAG_ONEWAY)
        } finally { data.recycle(); ShadowBinder.reset() }
    }
    @Test fun requestsRealBinderThroughPackageScopedRishProtocolWithoutActivity() {
        var delivered: IBinder? = null
        val bridge = ShizukuBinderRequest(c, Handler(Looper.getMainLooper()), { true }, { delivered=it })
        bridge.request()
        val intent = requests.last()
        assertEquals(ShizukuRecovery.PACKAGE,intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES != 0)
        val binder = Binder()
        assertTrue(reply(intent,12345,binder))
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(binder,delivered)
        assertNull(shadowOf(c).nextStartedActivity)
        bridge.cancel()
    }
    @Test fun forgedSenderCannotDeliverBinder() {
        var calls = 0
        val bridge = ShizukuBinderRequest(c, Handler(Looper.getMainLooper()), { true }, { calls++ })
        bridge.request()
        assertFalse(reply(requests.last(),99999,Binder()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,calls)
        bridge.cancel()
    }
    @Test fun noReplyTimesOutAndNextAttemptActuallyRequestsAgain() {
        val bridge = ShizukuBinderRequest(c, Handler(Looper.getMainLooper()), { true }, {})
        val before = requests.size
        bridge.request(); bridge.request()
        assertEquals(before+1,requests.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        bridge.request(); assertEquals(before+2,requests.size)
        bridge.cancel()
    }
    @Test fun stoppedServerReplyAndLateReplyAfterStopAreNotConnections() {
        var enabled=true; var calls=0
        val bridge=ShizukuBinderRequest(c,Handler(Looper.getMainLooper()),{ enabled },{ calls++ })
        bridge.request(); reply(requests.last(),12345,null)
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(0,calls)
        bridge.request(); val stale=requests.last()
        enabled=false; bridge.cancel(); enabled=true
        reply(stale,12345,Binder())
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(0,calls)
        bridge.cancel()
    }
    @Test fun disabledAppNeverRequests() {
        val bridge=ShizukuBinderRequest(c,Handler(Looper.getMainLooper()),{ false },{})
        val before=requests.size
        bridge.request(); assertEquals(before,requests.size)
    }
}
