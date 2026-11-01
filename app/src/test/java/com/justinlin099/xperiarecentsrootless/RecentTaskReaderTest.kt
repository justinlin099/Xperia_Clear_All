package com.justinlin099.xperiarecentsrootless

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentTaskReaderTest {
    @Test fun ordinaryAppCardCountsWithoutBeingCurrentlyVisible() {
        assertTrue(RecentTaskReader.eligible(1, Intent(Intent.ACTION_MAIN)))
    }
    @Test fun launcherAndRecentsContainersDoNotCountAsCards() {
        assertFalse(RecentTaskReader.eligible(2, Intent()))
        assertFalse(RecentTaskReader.eligible(3, Intent()))
        assertFalse(RecentTaskReader.eligible(1, Intent().addCategory(Intent.CATEGORY_HOME)))
    }
    @Test fun excludedTasksDoNotKeepClearButtonVisible() {
        assertFalse(RecentTaskReader.eligible(1, Intent().addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)))
    }
}
