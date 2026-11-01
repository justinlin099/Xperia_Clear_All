package com.justinlin099.xperiarecentsrootless

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Process

/** A fixed action under the already authorized ADB shell; never changes other channels or apps. */
internal object ShellServiceNotification {
    const val PACKAGE="com.justinlin099.xperiarecentsrootless"
    const val CHANNEL="shell-overview"
    interface Channels {
        fun read(uid:Int):NotificationChannel?
        fun update(uid:Int,channel:NotificationChannel)
    }
    private object PlatformChannels:Channels {
        private val api get()=Class.forName("android.app.INotificationManager")
        private fun service()=NotificationManager::class.java.getDeclaredMethod("getService").invoke(null)
        override fun read(uid:Int)=api.getMethod("getNotificationChannel",String::class.java,Int::class.javaPrimitiveType,String::class.java,String::class.java)
            .invoke(service(),"com.android.shell",uid/100000,PACKAGE,CHANNEL) as NotificationChannel?
        override fun update(uid:Int,channel:NotificationChannel) {
            api.getMethod("updateNotificationChannelForPackage",String::class.java,Int::class.javaPrimitiveType,NotificationChannel::class.java)
                .invoke(service(),PACKAGE,uid,channel)
        }
    }
    fun hide(context:Context,appUid:Int,channels:Channels=PlatformChannels):Boolean {
        check(Process.myUid()==2000) { "ADB shell required" }
        check(context.packageManager.getPackagesForUid(appUid)?.contains(PACKAGE)==true) { "Only this app is allowed" }
        val channel=channels.read(appUid) ?: return false
        check(channel.id==CHANNEL) { "Unexpected channel" }
        if(channel.importance!=NotificationManager.IMPORTANCE_NONE) {
            channel.importance=NotificationManager.IMPORTANCE_NONE
            channels.update(appUid,channel)
        }
        return channels.read(appUid)?.importance==NotificationManager.IMPORTANCE_NONE
    }
}
