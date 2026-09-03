package com.thepunisherbaby.apkisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService
import com.thepunisherbaby.apkisland.ui.IslandMediaData
import com.thepunisherbaby.apkisland.ui.IslandState
import com.thepunisherbaby.apkisland.ui.IslandStateHolder
import com.thepunisherbaby.apkisland.ui.IslandUI

class DynamicIslandService : Service(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store


    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()



        if (Settings.canDrawOverlays(this)) {
            addIslandView()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } else {
            stopSelf()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "island_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Dynamic Island Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Mantiene la isla dinámica activa"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Isla Dinámica Activa")
            .setContentText("Tapando cámara y escuchando notificaciones")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun addIslandView() {
        val cv = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandService)
            setViewTreeViewModelStoreOwner(this@DynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandService)
            setContent {
                IslandUI()
            }
        }
        composeView = cv

        val displayMetrics = resources.displayMetrics
        val pillHeightPx = (36 * displayMetrics.density).toInt()
        val cameraCenterY = (16 * displayMetrics.density).toInt()
        val offsetY = cameraCenterY - (pillHeightPx / 2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = offsetY
        }

        windowManager.addView(cv, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
