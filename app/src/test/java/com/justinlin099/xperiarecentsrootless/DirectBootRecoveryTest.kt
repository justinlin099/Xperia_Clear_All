package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import android.os.UserManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class DirectBootRecoveryTest {
    @Test fun lockedBootNeverReadsCredentialPreferencesAndUnlockRestoresWithoutActivity() {
        val c=RuntimeEnvironment.getApplication()
        val user=shadowOf(c.getSystemService(UserManager::class.java))
        user.setUserUnlocked(true)
        AutomaticStart.remember(c,true);ShadowSettings.setCanDrawOverlays(true)
        user.setUserUnlocked(false)
        val locked=object:ContextWrapper(c) {
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
                error("Credential storage accessed before unlock: $name")
            }
        }
        BootReceiver().onReceive(locked,Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertNull(shadowOf(c).nextStartedService)
        assertTrue(AutomaticStart.enabled(locked))
        assertTrue(BootRecovery.storage(c).getString("bootJournal","")!!.contains("LOCKED_BOOT_COMPLETED"))
        user.setUserUnlocked(true)
        c.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()
        val start=shadowOf(c).nextStartedService
        assertNotNull("Unlock must resume saved intent without an Activity",start)
        assertEquals(AutomaticStart.RESTORE,start.action)
        assertTrue(BootRecovery.storage(c).getString("bootJournal","")!!.contains("USER_UNLOCKED"))
    }
    @Test fun enabledStateUsesOneDeviceProtectedStoreAndJournalIsBounded() {
        val c=RuntimeEnvironment.getApplication()
        shadowOf(c.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        val old=c.getSharedPreferences("rootless",0)
        old.edit().putInt("filledTextPadding",27).commit()
        BootRecovery.storage(c).edit().clear().commit()
        AutomaticStart.remember(c,true)
        assertTrue(BootRecovery.storage(c).getBoolean(AutomaticStart.KEY,false))
        assertFalse(BootRecovery.storage(c).contains("filledTextPadding"))
        assertEquals(27,old.getInt("filledTextPadding",0))
        repeat(30) { BootRecovery.event(c,"event-$it") }
        val journal=BootRecovery.storage(c).getString("bootJournal","")!!
        assertEquals(24,journal.lines().size)
        assertTrue(journal.endsWith("event-29"));assertFalse(journal.contains("event-0\n"))
        AutomaticStart.remember(c,false)
        assertFalse(BootRecovery.storage(c).getBoolean(AutomaticStart.KEY,true))
    }
    @Test fun manifestAllowsProviderAndReceiverBeforeUnlockButKeepsOverlayPrivate() {
        val c=RuntimeEnvironment.getApplication()
        val receiver=c.packageManager.getReceiverInfo(android.content.ComponentName(c,BootReceiver::class.java),0)
        assertTrue(receiver.directBootAware)
        assertTrue(receiver.exported) // All three accepted actions are system-protected broadcasts.
        val provider=c.packageManager.resolveContentProvider(c.packageName+".shizuku",android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE)
        assertTrue(provider!!.directBootAware)
        val service=c.packageManager.getServiceInfo(android.content.ComponentName(c,ShellOverlayService::class.java),0)
        assertFalse(service.exported)
        assertFalse(service.directBootAware) // Overlay/appearance access waits for credentials.
    }
}
