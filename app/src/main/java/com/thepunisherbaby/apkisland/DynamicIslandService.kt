package com.thepunisherbaby.apkisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.Surface
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
    private var windowParams: WindowManager.LayoutParams? = null
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var currentRotation = Surface.ROTATION_0

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
            Log.d("DynamicIslandService", "Broadcast recibido: ${intent?.action}")
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.d("DynamicIslandService", "Teléfono DESBLOQUEADO (ACTION_USER_PRESENT) -> onUnlock()")
                    IslandStateHolder.onUnlock()
                    startGyro()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("DynamicIslandService", "Pantalla encendida (ACTION_SCREEN_ON)")
                    startGyro()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("DynamicIslandService", "Pantalla apagada (ACTION_SCREEN_OFF) -> onScreenOff()")
                    IslandStateHolder.onScreenOff()
                    stopGyro()
                }
            }
        }
    }

    private fun getScreenRotation(): Int {
        return try {
            displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation
                ?: @Suppress("DEPRECATION") windowManager.defaultDisplay?.rotation
                ?: Surface.ROTATION_0
        } catch (e: Exception) {
            Surface.ROTATION_0
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

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val rot = getScreenRotation()
                if (rot != currentRotation) {
                    currentRotation = rot
                    updateIslandPosition(rot)
                }
            }
        }
        displayManager?.registerDisplayListener(displayListener, null)

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
            var downX = 0f
            var downY = 0f
            setOnTouchListener { _, event ->
                if (IslandStateHolder.currentState == com.thepunisherbaby.apkisland.ui.IslandState.IDLE) {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val dx = kotlin.math.abs(event.x - downX)
                            val dy = kotlin.math.abs(event.y - downY)
                            if (dx < 30f && dy < 30f) {
                                Log.d("DynamicIslandService", "Tap en IDLE detectado -> triggerIdleAura()!")
                                IslandStateHolder.triggerIdleAura()
                            }
                            true
                        }
                        else -> true
                    }
                } else {
                    false
                }
            }
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = offsetY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

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

        windowParams = params
        windowManager.addView(cv, params)

        // Sincronizar posición inicial según orientación actual
        currentRotation = getScreenRotation()
        updateIslandPosition(currentRotation)
    }

    private fun updateIslandPosition(rotation: Int) {
        IslandStateHolder.screenRotation = rotation
        val params = windowParams ?: return
        val cv = composeView ?: return

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val cameraCenterYPx = 65.75f
        val pillHeightDp = 34f
        val glowPaddingDp = 12f
        val pillCenterInComposePx = (glowPaddingDp + (pillHeightDp / 2f)) * density
        val offsetY = (cameraCenterYPx - pillCenterInComposePx).toInt()

        when (rotation) {
            Surface.ROTATION_90 -> {
                // Modo horizontal (rotación a la izquierda): la cámara física queda en el marco izquierdo
                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                params.x = offsetY
                params.y = 0
            }
            Surface.ROTATION_270 -> {
                // Modo horizontal invertido (rotación a la derecha): la cámara física queda en el marco derecho
                params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                params.x = offsetY
                params.y = 0
            }
            Surface.ROTATION_180 -> {
                // Invertido vertical
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = offsetY
            }
            else -> {
                // Surface.ROTATION_0: Vertical estándar
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = offsetY
            }
        }

        try {
            windowManager.updateViewLayout(cv, params)
            Log.d("DynamicIslandService", "Posición de isla actualizada para rotación: $rotation")
        } catch (e: Exception) {
            Log.e("DynamicIslandService", "Error actualizando layout en rotación", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val rot = getScreenRotation()
        if (rot != currentRotation) {
            currentRotation = rot
            updateIslandPosition(rot)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGyro()
        displayListener?.let { displayManager?.unregisterDisplayListener(it) }
        displayListener = null
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
        windowParams = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
