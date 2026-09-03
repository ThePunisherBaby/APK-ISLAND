package com.thepunisherbaby.apkisland.logic

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class IslandNotificationListenerService : NotificationListenerService() {



    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (controllers != null && controllers.isNotEmpty()) {
            attachToController(controllers[0])
        } else {
            detachController()
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.mediaData = com.thepunisherbaby.apkisland.ui.IslandMediaData()
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentArtwork = null
            if (com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState == com.thepunisherbaby.apkisland.ui.IslandState.MUSIC_COMPACT) {
                com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState = com.thepunisherbaby.apkisland.ui.IslandState.IDLE
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("IslandNLS", "NotificationListener conectado!")

        try {
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, IslandNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)

            val controllers = mediaSessionManager?.getActiveSessions(component)
            if (controllers != null && controllers.isNotEmpty()) {
                attachToController(controllers[0])
            }
        } catch (e: Exception) {
            Log.e("IslandNLS", "Error al iniciar media listener", e)
        }
    }

    private fun attachToController(controller: MediaController) {
        detachController()
        activeController = controller

        mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateMediaState(controller)
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMediaState(controller)
            }
        }

        controller.registerCallback(mediaCallback!!)
        updateMediaState(controller)
    }

    private fun detachController() {
        mediaCallback?.let { activeController?.unregisterCallback(it) }
        activeController = null
        mediaCallback = null
    }

    private fun updateMediaState(controller: MediaController) {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L
        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        val packageName = controller.packageName ?: ""
        
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            
        com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentArtwork = artwork

        com.thepunisherbaby.apkisland.ui.IslandStateHolder.mediaData = com.thepunisherbaby.apkisland.ui.IslandMediaData(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            progress = progress,
            elapsed = formatTime(position),
            remaining = if (duration > 0) "-${formatTime(duration - position)}" else "0:00",
            packageName = packageName
        )

        if (isPlaying && com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState == com.thepunisherbaby.apkisland.ui.IslandState.IDLE) {
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState = com.thepunisherbaby.apkisland.ui.IslandState.MUSIC_COMPACT
        } else if (!isPlaying && com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState == com.thepunisherbaby.apkisland.ui.IslandState.MUSIC_COMPACT) {
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState = com.thepunisherbaby.apkisland.ui.IslandState.IDLE
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d("IslandNLS", "Notificación de: ${sbn.packageName}")
        
        // Dump all deskclock/timer notifications for reverse engineering
        if (sbn.packageName.contains("deskclock") || sbn.packageName.contains("clock")) {
            Log.d("IslandNLS_Dump", "====== DUMPING CLOCK NOTIFICATION ======")
            val extras = sbn.notification.extras
            if (extras != null) {
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    Log.d("IslandNLS_Dump", "Key: $key = $value")
                }
            }
            Log.d("IslandNLS_Dump", "========================================")
        }

        val category = sbn.notification.category
        if (category == android.app.Notification.CATEGORY_CALL) {
            val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: "Llamada entrante"
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.callData = com.thepunisherbaby.apkisland.ui.IslandCallData(
                name = title,
                duration = "0:00"
            )
            // Expandir inmediatamente para esquivar la cámara y emular iOS
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState = com.thepunisherbaby.apkisland.ui.IslandState.CALL_EXPANDED
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d("IslandNLS", "Notificación removida: ${sbn.packageName}")
        val category = sbn.notification.category
        if (category == android.app.Notification.CATEGORY_CALL) {
            com.thepunisherbaby.apkisland.ui.IslandStateHolder.callData = com.thepunisherbaby.apkisland.ui.IslandCallData()
            if (com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState == com.thepunisherbaby.apkisland.ui.IslandState.CALL_COMPACT ||
                com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState == com.thepunisherbaby.apkisland.ui.IslandState.CALL_EXPANDED) {
                com.thepunisherbaby.apkisland.ui.IslandStateHolder.currentState = com.thepunisherbaby.apkisland.ui.IslandState.IDLE
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detachController()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        Log.d("IslandNLS", "NotificationListener desconectado")
    }
}
