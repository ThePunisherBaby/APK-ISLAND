package com.thepunisherbaby.apkisland

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // Refrescar estado de permisos al volver a la app
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF1C1C1E),
                    primary = Color(0xFFFF375F),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        hasOverlay = Settings.canDrawOverlays(this),
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestNotification = { requestNotificationPermission() },
                        onStartService = { startIslandService() }
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
        } else {
            Toast.makeText(this, "✅ Permiso de superposición ya concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun startIslandService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "⚠️ Primero otorga el permiso de superposición", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        startForegroundService(Intent(this, DynamicIslandService::class.java))
        Toast.makeText(this, "🏝️ Isla Dinámica Activada", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    hasOverlay: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onStartService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header
        Text(
            "🏝️",
            fontSize = 48.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "APK Island",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Dynamic Island para Pixel 8",
            color = Color(0xFF8E8E93),
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(40.dp))

        // Paso 1: Permiso de superposición
        IslandButton(
            text = "1. Permiso de Superposición",
            subtitle = if (hasOverlay) "✅ Concedido" else "⚠️ Requerido",
            gradient = listOf(Color(0xFFFF375F), Color(0xFFFF6B6B)),
            onClick = onRequestOverlay
        )

        Spacer(Modifier.height(16.dp))

        // Paso 2: Permiso de notificaciones
        IslandButton(
            text = "2. Acceso a Notificaciones",
            subtitle = "Requerido para música y llamadas",
            gradient = listOf(Color(0xFFFF9F0A), Color(0xFFFFCC02)),
            onClick = onRequestNotification
        )

        Spacer(Modifier.height(16.dp))

        // Paso 3: Iniciar
        IslandButton(
            text = "3. Iniciar Isla Dinámica",
            subtitle = "Lanzar la isla flotante",
            gradient = listOf(Color(0xFF30D158), Color(0xFF34C759)),
            onClick = onStartService
        )
    }
}

@Composable
fun IslandButton(
    text: String,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(gradient)),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}
