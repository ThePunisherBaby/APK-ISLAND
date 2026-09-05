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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                // Rotación lateral (roll) y rotación sobre la pantalla (yaw)
                val roll = event.values[1]
                val yaw = event.values[2]
                IslandStateHolder.gyroBias = roll * 0.7f + yaw * 0.8f
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startGyro() {
        gyroSensor?.let {
            sensorManager?.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopGyro() {
        try {
            sensorManager?.unregisterListener(gyroListener)
        } catch (e: Exception) {
            // Ignorar
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    IslandStateHolder.triggerUnlock()
                    startGyro()
                }
                Intent.ACTION_SCREEN_ON -> {
                    startGyro()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopGyro()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        startGyro()

        startForegroundNotification()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(unlockReceiver, filter)

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
        val density = displayMetrics.density

        // Especificaciones físicas del Pixel 8 (1080x2400, 420dpi, density = 2.625):
        // Hardware Cutout: Centro en Y = 65.75 px (25.05 dp), Diámetro = 72.5 px (27.6 dp)
        val cameraCenterYPx = 65.75f

        // La píldora de 34dp está centrada dentro de un contenedor con 12dp de margen para el blur:
        // Centro de la píldora dentro del ComposeView = (12dp + 17dp) * density = 29dp * density
        val pillHeightDp = 34f
        val glowPaddingDp = 12f
        val pillCenterInComposePx = (glowPaddingDp + (pillHeightDp / 2f)) * density

        // Alineación concéntrica exacta al ras de la lente de la cámara
        val offsetY = (cameraCenterYPx - pillCenterInComposePx).toInt()

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

            // Forzar tasa de refresco a 120Hz nativos en el panel del Pixel 8
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                preferredRefreshRate = 120f
                try {
                    @Suppress("DEPRECATION")
                    val display = windowManager.defaultDisplay
                    val mode120 = display?.supportedModes?.find { it.refreshRate >= 119f }
                    if (mode120 != null) {
                        preferredDisplayModeId = mode120.modeId
                    }
                } catch (e: Exception) {
                    // Fallback a preferredRefreshRate
                }
            }
        }

        windowManager.addView(cv, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGyro()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // No registrado
        }
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
