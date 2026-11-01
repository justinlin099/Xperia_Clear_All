package com.justinlin099.xperiarecentsrootless

import android.app.ActivityManager
import android.content.Intent

/** Shell-only task query. Retains no names, intents, IDs or thumbnails. */
internal object RecentTaskReader {
    fun eligible(activityType: Int, intent: Intent): Boolean =
        activityType != 2 && activityType != 3 &&
            !intent.hasCategory(Intent.CATEGORY_HOME) &&
            intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS == 0

    fun count(manager: Any): Int {
        val user = Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null) as Int
        // Sony SystemUiProxy also requests flags=2 (RECENT_IGNORE_UNAVAILABLE).
        val slice = Class.forName("android.app.IActivityTaskManager")
            .getMethod("getRecentTasks", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType).invoke(manager, 128, 2, user)
            ?: error("No recent task result")
        val tasks = slice.javaClass.getMethod("getList").invoke(slice) as List<*>
        return tasks.count { item ->
            val task = item as? ActivityManager.RecentTaskInfo ?: return@count false
            val type = task.javaClass.getMethod("getActivityType").invoke(task) as Int
            eligible(type, task.baseIntent)
        }
    }
}
