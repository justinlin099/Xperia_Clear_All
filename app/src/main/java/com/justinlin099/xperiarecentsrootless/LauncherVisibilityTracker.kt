package com.justinlin099.xperiarecentsrootless

/** Parse only the two logd-filtered Sony tags; never retain task IDs or stack traces. */
internal class LauncherVisibilityTracker {
    data class Event(val visible: Boolean, val state: String)
    private val envelope = Regex("^[VDIWEF]/(LauncherStateManager|TaskView)\\s*\\(\\s*\\d+\\): (.*)$")
    private val launch = Regex("^\\[[0-9, -]+\\] - launchWithAnimation - initiating launch, partial trace:.*$")
    private var launching = false

    fun accept(line: String): Event? {
        val match = envelope.matchEntire(line) ?: return null
        val tag = match.groupValues[1]
        val message = match.groupValues[2]
        if (tag == "TaskView") {
            if (!launch.matches(message)) return null
            launching = true
            return Event(false, "TASK_LAUNCH_START")
        }
        val state = LauncherStateParser.parse(message) ?: return null
        val overview = LauncherStateParser.isOverview(state)
        // An old Overview end can arrive after the card click. Only a fresh entry
        // or a completed departure may clear the launch suppression.
        if (!overview || message.startsWith("StateManager.onStateTransitionStart:")) launching = false
        return Event(overview && !launching, state)
    }
}
