package com.thepunisherbaby.apkisland.logic
import android.util.Log
import android.service.notification.StatusBarNotification

object NotificationDumper {
    fun dump(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        Log.d("IslandNLS_Dump", "Package: ${sbn.packageName}")
        for (key in extras.keySet()) {
            Log.d("IslandNLS_Dump", "Extra: $key = ${extras.get(key)}")
        }
    }
}
