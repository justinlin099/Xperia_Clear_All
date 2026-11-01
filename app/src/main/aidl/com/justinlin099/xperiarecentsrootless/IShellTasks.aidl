package com.justinlin099.xperiarecentsrootless;
import android.app.PendingIntent;
import com.justinlin099.xperiarecentsrootless.IOverviewStateListener;
interface IShellTasks {
    String probe() = 0;
    String clearTasks() = 1;
    String startOverviewWatch(IOverviewStateListener listener) = 2;
    void stopOverviewWatch() = 3;
    boolean restoreOverlay(in PendingIntent startIntent) = 4;
    void destroy() = 16777114;
}
