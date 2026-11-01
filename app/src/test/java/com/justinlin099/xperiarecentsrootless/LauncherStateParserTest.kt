package com.justinlin099.xperiarecentsrootless

import org.junit.Assert.*
import org.junit.Test

class LauncherStateParserTest {
    @Test fun sonyIntegratedLauncherEntryAndHomeExit() {
        val entry = LauncherStateParser.parse("StateManager.onStateTransitionStart: state: Overview")!!
        assertTrue(LauncherStateParser.isOverview(entry))
        val home = LauncherStateParser.parse("StateManager.onStateTransitionEnd: state: Normal")!!
        assertFalse(LauncherStateParser.isOverview(home))
    }
    @Test fun fallbackRecentsAndBackgroundExit() {
        assertTrue(LauncherStateParser.isOverview(LauncherStateParser.parse("StateManager.onStateTransitionEnd: state: RECENTS_DEFAULT")!!))
        assertFalse(LauncherStateParser.isOverview(LauncherStateParser.parse("StateManager.onStateTransitionStart: state: RECENTS_BACKGROUND_APP")!!))
    }
    @Test fun plannedOrCancelledTransitionsMustNotDisplayTheButton() {
        assertNull(LauncherStateParser.parse("StateManager.goToState: fromState: Normal, toState: Overview, partial trace:"))
        assertNull(LauncherStateParser.parse("StateManager.createAtomicAnimation: fromState: Normal, toState: Overview, partial trace:"))
        assertNull(LauncherStateParser.parse("StateManager.cancelAnimation: animation ongoing: true, partial trace:"))
    }
    @Test fun quickSwitchAndSplitSelectionAreNotOrdinaryOverview() {
        for (state in listOf("QuickSwitch", "OverviewSplitSelect", "OverviewModal", "AllApps", "RECENTS_SPLIT_SELECT")) {
            assertFalse(LauncherStateParser.isOverview(LauncherStateParser.parse("StateManager.onStateTransitionStart: state: $state")!!))
        }
    }
    @Test fun unknownAndUnrelatedLogLinesAreIgnored() {
        assertNull(LauncherStateParser.parse("private app content"))
        assertNull(LauncherStateParser.parse("StateManager.onStateTransitionStart: state: UnsupportedState"))
        assertNull(LauncherStateParser.parse("prefix StateManager.onStateTransitionStart: state: Overview"))
    }
}
