package com.thepunisherbaby.apkisland.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.thepunisherbaby.apkisland.DynamicIslandService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, DynamicIslandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
