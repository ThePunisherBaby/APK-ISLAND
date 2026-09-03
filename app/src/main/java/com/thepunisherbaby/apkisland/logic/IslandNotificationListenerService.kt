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

    companion object {
        const val ACTION_MEDIA_UPDATE = "com.thepunisherbaby.apkisland.MEDIA_UPDATE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_ELAPSED = "elapsed"
        const val EXTRA_REMAINING = "remaining"
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (controllers != null && controllers.isNotEmpty()) {
            attachToController(controllers[0])
        } else {
            detachController()
            sendMediaBroadcast("", "", false, 0f, "0:00", "0:00")
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

        sendMediaBroadcast(
            title, artist, isPlaying, progress,
            formatTime(position),
            if (duration > 0) "-${formatTime(duration - position)}" else "0:00"
        )
    }

    private fun sendMediaBroadcast(title: String, artist: String, isPlaying: Boolean, progress: Float, elapsed: String, remaining: String) {
        val intent = Intent(ACTION_MEDIA_UPDATE).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ARTIST, artist)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_ELAPSED, elapsed)
            putExtra(EXTRA_REMAINING, remaining)
        }
        sendBroadcast(intent)
        Log.d("IslandNLS", "Media broadcast: $title - $artist playing=$isPlaying")
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d("IslandNLS", "Notificación de: ${sbn.packageName}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d("IslandNLS", "Notificación removida: ${sbn.packageName}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detachController()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        Log.d("IslandNLS", "NotificationListener desconectado")
    }
}
