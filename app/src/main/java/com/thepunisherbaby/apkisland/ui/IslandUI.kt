package com.thepunisherbaby.apkisland.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import android.graphics.RenderEffect
// import android.graphics.Shader

enum class IslandState {
    IDLE, // Píldora persistente negra tapando la cámara
    ACTIVE_COMPACT, // Píldora un poco ensanchada (ej. mostrando ícono y ecualizador)
    DETAIL_EXPANDED // Modo detalle expandido
}

@Composable
fun IslandUI() {
    var islandState by remember { mutableStateOf(IslandState.IDLE) }
    
    // Animaciones fluidas (Morphing/Spring) a 120Hz
    val width by animateDpAsState(
        targetValue = when (islandState) {
            IslandState.IDLE -> 120.dp
            IslandState.ACTIVE_COMPACT -> 200.dp
            IslandState.DETAIL_EXPANDED -> 320.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "width"
    )

    val height by animateDpAsState(
        targetValue = when (islandState) {
            IslandState.IDLE -> 35.dp
            IslandState.ACTIVE_COMPACT -> 40.dp
            IslandState.DETAIL_EXPANDED -> 160.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "height"
    )

    val cornerRadius by animateDpAsState(
        targetValue = when (islandState) {
            IslandState.IDLE -> 50.dp
            IslandState.ACTIVE_COMPACT -> 50.dp
            IslandState.DETAIL_EXPANDED -> 32.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "corner"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                // Motion Blur / RenderEffect para Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // TODO: Implementar blur dinámico basado en la velocidad de expansión
                    // renderEffect = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { /* Manejar swipe finalizado */ }
                ) { change, dragAmount ->
                    change.consume()
                    // Si hay swipe horizontal, cambiar app activa
                    if (dragAmount.x > 10) {
                        // Swipe Right
                    } else if (dragAmount.x < -10) {
                        // Swipe Left
                    }
                }
            }
            .clickable {
                // Alternar estado al hacer clic
                islandState = when (islandState) {
                    IslandState.IDLE -> IslandState.ACTIVE_COMPACT // Solo para prueba manual
                    IslandState.ACTIVE_COMPACT -> IslandState.DETAIL_EXPANDED
                    IslandState.DETAIL_EXPANDED -> IslandState.ACTIVE_COMPACT
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (islandState == IslandState.DETAIL_EXPANDED) {
            Text("Detalles Expandidos", color = Color.White, fontSize = 16.sp)
        } else if (islandState == IslandState.ACTIVE_COMPACT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fake Album Art
                Box(modifier = Modifier.size(24.dp).background(Color.Green, RoundedCornerShape(12.dp)))
                // Fake Equalizer
                Text("|||", color = Color.Yellow, fontSize = 12.sp)
            }
        }
    }
}
