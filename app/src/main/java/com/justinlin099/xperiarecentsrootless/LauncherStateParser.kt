package com.justinlin099.xperiarecentsrootless

/** Accept actual transition notifications only, never an intended/cancelled goToState. */
internal object LauncherStateParser {
    private val transition = Regex("^StateManager\\.onStateTransition(?:Start|End): state: ([A-Za-z_]+)\\s*$")
    private val known = setOf("Normal", "SpringLoaded", "Overview", "OverviewModal", "QuickSwitch",
        "AllApps", "Background", "Hint", "Hint2Button", "OverviewSplitSelect", "EditMode", "DesktopDragMode",
        "RECENTS_DEFAULT", "RECENTS_MODAL_TASK", "RECENTS_BACKGROUND_APP", "RECENTS_HOME",
        "RECENTS_BG_LAUNCHER", "RECENTS_SPLIT_SELECT", "RECENTS_HIDDEN")
    fun parse(line: String): String? = transition.matchEntire(line)?.groupValues?.get(1)?.takeIf { it in known }
    fun isOverview(state: String): Boolean = state == "Overview" || state == "RECENTS_DEFAULT"
}
