package com.justinlin099.xperiarecentsrootless

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RootlessSmokeTest {
    @Test fun ordinaryAppIdentityCannotClearTasks() {
        ShadowProcess.setUid(10000)
        val result = JSONObject(ShellTasksService().clearTasks())
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getString("message").contains("uid=2000"))
    }

    @Test fun rootIdentityIsRejectedRatherThanUsedAsFallback() {
        ShadowProcess.setUid(0)
        val result = JSONObject(ShellTasksService().clearTasks())
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getString("message").contains("uid=2000"))
    }

    @Test fun adbIdentityWithoutPermissionCannotInvokeRemoval() {
        ShadowProcess.setUid(2000)
        val result = JSONObject(ShellTasksService().clearTasks())
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getString("message").contains("REMOVE_TASKS"))
    }

    @Test fun settingsActivityOpensWithoutShizukuServer() {
        ShadowProcess.setUid(10000)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        assertFalse(RootlessConnection.ready)
        controller.pause().stop().destroy()
    }
}
