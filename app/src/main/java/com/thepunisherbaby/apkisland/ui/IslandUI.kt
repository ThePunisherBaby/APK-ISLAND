package com.thepunisherbaby.apkisland.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.random.Random

// ─── Estados de la isla ───────────────────────────────────────────────
enum class IslandState {
    IDLE,            // Píldora negra base (sin contenido)
    MUSIC_COMPACT,   // Píldora ensanchada: carátula + ecualizador
    MUSIC_EXPANDED,  // Panel grande: reproductor completo
    TIMER_COMPACT,   // Píldora: ícono + cuenta regresiva
    TIMER_EXPANDED,  // Panel grande: temporizador circular
    CALL_COMPACT,    // Píldora: ícono teléfono + duración
    CALL_EXPANDED,   // Panel grande: llamada con botones
    LOCK_ANIM        // Animación rápida candado al bloquear/desbloquear
}

// ─── Composable raíz ─────────────────────────────────────────────────
@Composable
fun IslandUI() {
    var state by remember { mutableStateOf(IslandState.IDLE) }
    var swipeAccum by remember { mutableFloatStateOf(0f) }

    // Lista de estados compactos para hacer swipe entre ellos
    val compactStates = listOf(
        IslandState.MUSIC_COMPACT,
        IslandState.TIMER_COMPACT,
        IslandState.CALL_COMPACT
    )
    var compactIndex by remember { mutableIntStateOf(0) }

    // ── Dimensiones animadas con spring (morphing) ──
    val isExpanded = state in listOf(
        IslandState.MUSIC_EXPANDED,
        IslandState.TIMER_EXPANDED,
        IslandState.CALL_EXPANDED
    )
    val isCompact = state in compactStates
    val isIdle = state == IslandState.IDLE || state == IslandState.LOCK_ANIM

    val targetW: Dp = when {
        isExpanded -> 340.dp
        isCompact  -> 210.dp
        else       -> 126.dp   // píldora base idéntica a Apple
    }
    val targetH: Dp = when {
        isExpanded -> 180.dp
        isCompact  -> 38.dp
        else       -> 36.dp
    }
    val targetCorner: Dp = when {
        isExpanded -> 38.dp
        else       -> 50.dp
    }

    val morphSpec: AnimationSpec<Dp> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )
    val width  by animateDpAsState(targetW, morphSpec, label = "w")
    val height by animateDpAsState(targetH, morphSpec, label = "h")
    val corner by animateDpAsState(targetCorner, morphSpec, label = "c")

    // Escala "bounce" al expandir
    val scaleAnim = remember { Animatable(1f) }
    LaunchedEffect(state) {
        scaleAnim.snapTo(0.97f)
        scaleAnim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }

    // Color del glow que cambia según estado
    val glowColor by animateColorAsState(
        targetValue = when (state) {
            IslandState.MUSIC_COMPACT, IslandState.MUSIC_EXPANDED -> Color(0x40FF375F)
            IslandState.TIMER_COMPACT, IslandState.TIMER_EXPANDED -> Color(0x40FF9F0A)
            IslandState.CALL_COMPACT, IslandState.CALL_EXPANDED   -> Color(0x4030D158)
            IslandState.LOCK_ANIM                                  -> Color(0x40FFFFFF)
            else                                                   -> Color(0x00000000) // Sin glow en IDLE
        },
        animationSpec = tween(500),
        label = "glow"
    )

    // Contenedor exterior: dibuja el glow difuminado DETRÁS de la píldora
    Box(
        modifier = Modifier
            .width(width + 16.dp)  // extra espacio para el resplandor
            .height(height + 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Capas de glow difuminado (de más ancha/transparente a más estrecha/opaca)
        Canvas(
            modifier = Modifier
                .width(width + 12.dp)
                .height(height + 12.dp)
        ) {
            // Capa exterior (más difusa)
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * 0.3f),
                cornerRadius = CornerRadius((corner + 8.dp).toPx()),
                size = size
            )
        }
        Canvas(
            modifier = Modifier
                .width(width + 6.dp)
                .height(height + 6.dp)
        ) {
            // Capa intermedia
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * 0.5f),
                cornerRadius = CornerRadius((corner + 4.dp).toPx()),
                size = size
            )
        }
        Canvas(
            modifier = Modifier
                .width(width + 2.dp)
                .height(height + 2.dp)
        ) {
            // Capa interior (más nítida)
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * 0.8f),
                cornerRadius = CornerRadius((corner + 1.dp).toPx()),
                size = size
            )
        }

        // ── La píldora real ──
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF1C1C1E)) // Negro Apple
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeAccum.absoluteValue > 80f && isCompact) {
                            if (swipeAccum > 0) {
                                compactIndex = (compactIndex + 1) % compactStates.size
                            } else {
                                compactIndex = (compactIndex - 1 + compactStates.size) % compactStates.size
                            }
                            state = compactStates[compactIndex]
                        }
                        swipeAccum = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    swipeAccum += amount
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                state = when (state) {
                    IslandState.IDLE           -> IslandState.MUSIC_COMPACT
                    IslandState.MUSIC_COMPACT  -> IslandState.MUSIC_EXPANDED
                    IslandState.MUSIC_EXPANDED -> IslandState.MUSIC_COMPACT
                    IslandState.TIMER_COMPACT  -> IslandState.TIMER_EXPANDED
                    IslandState.TIMER_EXPANDED -> IslandState.TIMER_COMPACT
                    IslandState.CALL_COMPACT   -> IslandState.CALL_EXPANDED
                    IslandState.CALL_EXPANDED  -> IslandState.CALL_COMPACT
                    IslandState.LOCK_ANIM      -> IslandState.IDLE
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            IslandState.IDLE           -> { /* Píldora vacía – solo negro */ }
            IslandState.LOCK_ANIM      -> LockAnimation()
            IslandState.MUSIC_COMPACT  -> MusicCompactContent()
            IslandState.MUSIC_EXPANDED -> MusicExpandedContent()
            IslandState.TIMER_COMPACT  -> TimerCompactContent()
            IslandState.TIMER_EXPANDED -> TimerExpandedContent()
            IslandState.CALL_COMPACT   -> CallCompactContent()
            IslandState.CALL_EXPANDED  -> CallExpandedContent()
        }
    }
    } // cierre del Box exterior (glow)
}

// ═══════════════════════════════════════════════════════════════════════
//  MÚSICA – COMPACTO  (carátula izquierda + ecualizador animado derecha)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicCompactContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Carátula circular (gradiente como placeholder)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF6B6B), Color(0xFFE040FB))
                    )
                )
        )
        Spacer(Modifier.width(6.dp))
        // Barras de ecualizador animadas
        EqualizerBars(barCount = 4, barWidth = 3.dp, maxHeight = 16.dp, color = Color(0xFF30D158))
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  MÚSICA – EXPANDIDO  (reproductor completo estilo Apple)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicExpandedContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Fila superior: carátula + info
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Carátula cuadrada redondeada
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF6B6B), Color(0xFFE040FB))
                        )
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gravity",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Beach Bunny",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Ecualizador
            EqualizerBars(barCount = 5, barWidth = 3.dp, maxHeight = 22.dp, color = Color(0xFFFF375F))
        }

        Spacer(Modifier.height(8.dp))

        // Barra de progreso
        ProgressBar(progress = 0.45f)

        // Tiempos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1:24", color = Color(0xFF8E8E93), fontSize = 11.sp)
            Text("-1:47", color = Color(0xFF8E8E93), fontSize = 11.sp)
        }

        // Controles de reproducción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon("⏮", 22.sp)
            ControlIcon("⏸", 28.sp)
            ControlIcon("⏭", 22.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  TIMER – COMPACTO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TimerCompactContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "timer")
    val seconds by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            tween(60000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "sec"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Ícono timer con progreso circular mini
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(22.dp)) {
                // Fondo
                drawArc(
                    color = Color(0xFF3A3A3C),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                // Progreso naranja
                drawArc(
                    color = Color(0xFFFF9F0A),
                    startAngle = -90f,
                    sweepAngle = (seconds / 60f) * 360f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Text(
            "2:47",
            color = Color(0xFFFF9F0A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  TIMER – EXPANDIDO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TimerExpandedContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "timerExp")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(10000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "prog"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Círculo grande de progreso estilo Apple
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(90.dp)) {
                drawArc(
                    color = Color(0xFF3A3A3C),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFFF9F0A),
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                "02:47",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Text("Cancel", color = Color(0xFF8E8E93), fontSize = 14.sp)
            Text("Pause", color = Color(0xFFFF9F0A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LLAMADA – COMPACTO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CallCompactContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "call")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Ícono teléfono verde pulsante
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { alpha = pulse }
                .clip(CircleShape)
                .background(Color(0xFF30D158)),
            contentAlignment = Alignment.Center
        ) {
            Text("📞", fontSize = 12.sp)
        }
        Text(
            "0:08",
            color = Color(0xFF30D158),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        // Waveform mini
        WaveformBars(barCount = 8, color = Color(0xFF30D158))
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LLAMADA – EXPANDIDO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CallExpandedContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("mobile", color = Color(0xFF8E8E93), fontSize = 12.sp)
        Text(
            "Tania Castillo",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        WaveformBars(barCount = 20, color = Color(0xFF30D158), barHeight = 28.dp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Botón colgar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30)),
                contentAlignment = Alignment.Center
            ) {
                Text("📞", fontSize = 18.sp)
            }
            // Botón contestar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30D158)),
                contentAlignment = Alignment.Center
            ) {
                Text("📞", fontSize = 18.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  ANIMACIÓN DE CANDADO (Bloqueo / Desbloqueo)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun LockAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "lock")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "bounce"
    )
    Box(contentAlignment = Alignment.Center) {
        Text(
            if (bounce > 0.5f) "🔒" else "🔓",
            fontSize = 18.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = 0.8f + bounce * 0.4f
                scaleY = 0.8f + bounce * 0.4f
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  COMPONENTES REUTILIZABLES
// ═══════════════════════════════════════════════════════════════════════

/** Barras de ecualizador animadas (estilo Apple Music) */
@Composable
private fun EqualizerBars(
    barCount: Int,
    barWidth: Dp,
    maxHeight: Dp,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val phases = remember {
        List(barCount) { Random.nextInt(200, 600) }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { i, duration ->
            val anim by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(duration, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ), label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * anim)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

/** Barras de waveform para llamadas */
@Composable
private fun WaveformBars(
    barCount: Int,
    color: Color,
    barHeight: Dp = 14.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wf")
    val phases = remember { List(barCount) { Random.nextInt(150, 500) } }

    Row(
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { i, duration ->
            val anim by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(duration, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ), label = "wbar$i"
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(barHeight * anim)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

/** Barra de progreso estilo Apple (fondo gris, progreso blanco) */
@Composable
private fun ProgressBar(progress: Float) {
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "progress"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        // Fondo
        drawRoundRect(
            color = Color(0xFF3A3A3C),
            cornerRadius = CornerRadius(4f, 4f),
            size = Size(size.width, size.height)
        )
        // Progreso
        drawRoundRect(
            color = Color.White,
            cornerRadius = CornerRadius(4f, 4f),
            size = Size(size.width * animProgress, size.height)
        )
    }
}

/** Ícono de control de reproducción */
@Composable
private fun ControlIcon(symbol: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        symbol,
        color = Color.White,
        fontSize = size
    )
}
