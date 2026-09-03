package com.thepunisherbaby.apkisland.logic

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.thepunisherbaby.apkisland.ui.IslandMediaData
import com.thepunisherbaby.apkisland.ui.IslandState
import com.thepunisherbaby.apkisland.ui.IslandStateHolder

class IslandNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (controllers != null && controllers.isNotEmpty()) {
            attachToController(controllers[0])
        } else {
            detachController()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("IslandNLS", "NotificationListener conectado")
        
        try {
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, IslandNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            
            // Verificar sesiones activas actuales
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

        if (metadata != null) {
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val position = playbackState?.position ?: 0L
            val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

            IslandStateHolder.mediaData = IslandMediaData(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                progress = progress,
                elapsed = formatTime(position),
                remaining = if (duration > 0) "-${formatTime(duration - position)}" else "0:00"
            )

            if (isPlaying) {
                IslandStateHolder.currentState = IslandState.MUSIC_COMPACT
            }
        }

        if (!isPlaying && IslandStateHolder.currentState == IslandState.MUSIC_COMPACT) {
            // Mantener compacto por un momento tras pausar, luego volver a IDLE
            IslandStateHolder.currentState = IslandState.IDLE
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Las notificaciones de media se manejan vía MediaSessionManager
        // Aquí podríamos manejar llamadas, timers, etc.
        Log.d("IslandNLS", "Notificación de: ${sbn.packageName}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d("IslandNLS", "Notificación removida de: ${sbn.packageName}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detachController()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
    }
}
