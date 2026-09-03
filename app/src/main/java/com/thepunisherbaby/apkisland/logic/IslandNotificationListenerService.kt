package com.thepunisherbaby.apkisland.logic

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class IslandNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        Log.d("IslandNotification", "Notificación recibida de: ${sbn.packageName}")
        // Aquí se enviaría un Intent o EventBus/StateFlow a DynamicIslandService
        // para cambiar el IslandState a ACTIVE_COMPACT y mostrar la información.
        
        // TODO: Detectar si es un MediaStyle (Música) o un Mensaje / Alarma
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d("IslandNotification", "Notificación eliminada de: ${sbn.packageName}")
        // Si no hay más notificaciones relevantes, volver a IDLE.
    }
}
