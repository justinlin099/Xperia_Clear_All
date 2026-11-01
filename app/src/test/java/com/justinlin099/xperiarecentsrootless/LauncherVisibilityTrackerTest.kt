package com.justinlin099.xperiarecentsrootless

import org.junit.Assert.*
import org.junit.Test

class LauncherVisibilityTrackerTest {
    private fun state(phase: String, value: String) =
        "D/LauncherStateManager( 1234): StateManager.onStateTransition$phase: state: $value"
    private val launch = "D/TaskView( 1234): [42] - launchWithAnimation - initiating launch, partial trace: ignored"

    @Test fun cardLaunchHidesBeforeAnyForegroundChange() {
        val tracker = LauncherVisibilityTracker()
        assertTrue(tracker.accept(state("Start", "Overview"))!!.visible)
        assertEquals(LauncherVisibilityTracker.Event(false, "TASK_LAUNCH_START"), tracker.accept(launch))
    }
    @Test fun delayedOverviewEndDoesNotReshowButFreshEntryDoes() {
        val tracker = LauncherVisibilityTracker()
        tracker.accept(state("Start", "Overview"))
        tracker.accept(launch)
        assertFalse(tracker.accept(state("End", "Overview"))!!.visible)
        assertFalse(tracker.accept(state("Start", "Normal"))!!.visible)
        assertTrue(tracker.accept(state("Start", "Overview"))!!.visible)
    }
    @Test fun freshEntryCanRecoverWhenNoDepartureLogArrives() {
        val tracker = LauncherVisibilityTracker()
        tracker.accept(launch)
        assertTrue(tracker.accept(state("Start", "Overview"))!!.visible)
    }
    @Test fun unrelatedTagsAndTaskLogsDoNotAffectVisibility() {
        val tracker = LauncherVisibilityTracker()
        assertNull(tracker.accept(launch.replace("TaskView", "OtherTag")))
        assertNull(tracker.accept("D/TaskView( 1234): [42] - launchWithAnimation - launchCompleted"))
        assertNull(tracker.accept("D/TaskView( 1234): onBind private content"))
        assertNull(tracker.accept("D/TaskView( 1234): StateManager.onStateTransitionStart: state: Overview"))
        assertNull(tracker.accept(state("Start", "Unsupported")))
    }
}
