package com.thepunisherbaby.apkisland

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestNotification = { requestNotificationPermission() },
                        onStartService = {
                            startForegroundService(Intent(this, DynamicIslandService::class.java))
                        }
                    )
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

@Composable
fun MainScreen(
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onStartService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "APK Island Config", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onRequestOverlay) {
            Text("Permiso de Superposición")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRequestNotification) {
            Text("Permiso de Notificaciones")
        }
        Spacer(modifier = Modifier.height(32.dp))
        
        // TODO: Añadir DataStore y Sliders para X, Y, Width, Height (Pixel 8)
        Text(text = "Ajustes de Posición y Escala (Pixel 8)", style = MaterialTheme.typography.titleMedium)
        var sliderY by remember { mutableFloatStateOf(0f) }
        Slider(value = sliderY, onValueChange = { sliderY = it }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartService) {
            Text("Iniciar Isla Dinámica")
        }
    }
}
