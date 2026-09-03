package com.thepunisherbaby.apkisland.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
import kotlin.random.Random

// ─── Color negro OLED puro ───────────────────────────────────────────
private val OledBlack = Color(0xFF000000)

// ─── Estados de la isla ───────────────────────────────────────────────
enum class IslandState {
    IDLE,            // Píldora negra OLED base (sin contenido)
    MUSIC_COMPACT,   // Píldora ensanchada: carátula + ecualizador
    MUSIC_EXPANDED,  // Panel grande: reproductor completo
    TIMER_COMPACT,   // Píldora: ícono + cuenta regresiva
    TIMER_EXPANDED,  // Panel grande: temporizador circular
    CALL_COMPACT,    // Píldora: ícono teléfono + duración
    CALL_EXPANDED,   // Panel grande: llamada con botones
    LOCK_ANIM        // Animación rápida candado
}

// ─── Datos reales del sistema ────────────────────────────────────────
data class IslandMediaData(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val elapsed: String = "0:00",
    val remaining: String = "0:00",
    val packageName: String = ""
)

data class IslandCallData(
    val name: String = "",
    val duration: String = "0:00"
)

data class IslandTimerData(
    val remaining: String = "0:00",
    val progress: Float = 0f
)

// ─── Estado global compartido con el servicio ────────────────────────
object IslandStateHolder {
    var currentState by mutableStateOf(IslandState.IDLE)
    var mediaData by mutableStateOf(IslandMediaData())
    var callData by mutableStateOf(IslandCallData())
    var timerData by mutableStateOf(IslandTimerData())
}

// ─── Composable raíz ─────────────────────────────────────────────────
@Composable
fun IslandUI() {
    var state by remember { mutableStateOf(IslandState.IDLE) }
    var swipeAccum by remember { mutableFloatStateOf(0f) }

    // Sincronizar con el estado global (que viene del NotificationListener)
    LaunchedEffect(IslandStateHolder.currentState) {
        val systemState = IslandStateHolder.currentState
        // Solo cambiar a compacto si viene del sistema, no sobreescribir expanded
        if (systemState != IslandState.IDLE) {
            if (state == IslandState.IDLE) {
                state = systemState
            }
        } else {
            state = IslandState.IDLE
        }
    }

    // Lista de estados compactos activos para swipe
    val activeCompactStates = buildList {
        if (IslandStateHolder.mediaData.isPlaying) add(IslandState.MUSIC_COMPACT)
        if (IslandStateHolder.timerData.remaining != "0:00") add(IslandState.TIMER_COMPACT)
        if (IslandStateHolder.callData.name.isNotEmpty()) add(IslandState.CALL_COMPACT)
    }
    var compactIndex by remember { mutableIntStateOf(0) }

    // ── Dimensiones animadas con spring (morphing) ──
    val isExpanded = state in listOf(
        IslandState.MUSIC_EXPANDED,
        IslandState.TIMER_EXPANDED,
        IslandState.CALL_EXPANDED
    )
    val isCompact = state in listOf(
        IslandState.MUSIC_COMPACT,
        IslandState.TIMER_COMPACT,
        IslandState.CALL_COMPACT
    )

    val targetW: Dp = when {
        isExpanded -> 340.dp
        isCompact  -> 180.dp
        else       -> 100.dp   // píldora base más delgada
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

    // Color del glow según estado
    val glowColor by animateColorAsState(
        targetValue = when (state) {
            IslandState.MUSIC_COMPACT, IslandState.MUSIC_EXPANDED -> Color(0x40FF375F)
            IslandState.TIMER_COMPACT, IslandState.TIMER_EXPANDED -> Color(0x40FF9F0A)
            IslandState.CALL_COMPACT, IslandState.CALL_EXPANDED   -> Color(0x4030D158)
            IslandState.LOCK_ANIM                                  -> Color(0x40FFFFFF)
            else                                                   -> Color(0x00000000)
        },
        animationSpec = tween(500),
        label = "glow"
    )

    // Contenedor exterior: glow difuminado
    Box(
        modifier = Modifier
            .width(width + 16.dp)
            .height(height + 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Capas de glow
        if (state != IslandState.IDLE) {
            Canvas(modifier = Modifier.width(width + 12.dp).height(height + 12.dp)) {
                drawRoundRect(color = glowColor.copy(alpha = glowColor.alpha * 0.3f), cornerRadius = CornerRadius((corner + 8.dp).toPx()), size = size)
            }
            Canvas(modifier = Modifier.width(width + 6.dp).height(height + 6.dp)) {
                drawRoundRect(color = glowColor.copy(alpha = glowColor.alpha * 0.5f), cornerRadius = CornerRadius((corner + 4.dp).toPx()), size = size)
            }
            Canvas(modifier = Modifier.width(width + 2.dp).height(height + 2.dp)) {
                drawRoundRect(color = glowColor.copy(alpha = glowColor.alpha * 0.8f), cornerRadius = CornerRadius((corner + 1.dp).toPx()), size = size)
            }
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
                .background(OledBlack) // ← Negro OLED puro
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeAccum.absoluteValue > 80f && isCompact && activeCompactStates.size > 1) {
                                compactIndex = if (swipeAccum > 0) {
                                    (compactIndex + 1) % activeCompactStates.size
                                } else {
                                    (compactIndex - 1 + activeCompactStates.size) % activeCompactStates.size
                                }
                                state = activeCompactStates[compactIndex]
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
                    // Solo expandir/colapsar si hay contenido activo
                    state = when (state) {
                        IslandState.MUSIC_COMPACT  -> IslandState.MUSIC_EXPANDED
                        IslandState.MUSIC_EXPANDED -> IslandState.MUSIC_COMPACT
                        IslandState.TIMER_COMPACT  -> IslandState.TIMER_EXPANDED
                        IslandState.TIMER_EXPANDED -> IslandState.TIMER_COMPACT
                        IslandState.CALL_COMPACT   -> IslandState.CALL_EXPANDED
                        IslandState.CALL_EXPANDED  -> IslandState.CALL_COMPACT
                        IslandState.LOCK_ANIM      -> IslandState.IDLE
                        IslandState.IDLE           -> IslandState.IDLE // No hacer nada en IDLE
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                IslandState.IDLE           -> { /* Píldora negra OLED vacía */ }
                IslandState.LOCK_ANIM      -> LockAnimation()
                IslandState.MUSIC_COMPACT  -> MusicCompactContent(IslandStateHolder.mediaData)
                IslandState.MUSIC_EXPANDED -> MusicExpandedContent(IslandStateHolder.mediaData)
                IslandState.TIMER_COMPACT  -> TimerCompactContent(IslandStateHolder.timerData)
                IslandState.TIMER_EXPANDED -> TimerExpandedContent(IslandStateHolder.timerData)
                IslandState.CALL_COMPACT   -> CallCompactContent(IslandStateHolder.callData)
                IslandState.CALL_EXPANDED  -> CallExpandedContent(IslandStateHolder.callData)
            }
        }
    } // cierre del Box exterior (glow)
}

// ═══════════════════════════════════════════════════════════════════════
//  MÚSICA – COMPACTO (datos reales del MediaController)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicCompactContent(data: IslandMediaData) {
    Row(
        modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Carátula circular
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E))
        )
        Spacer(Modifier.width(6.dp))
        if (data.isPlaying) {
            EqualizerBars(barCount = 4, barWidth = 3.dp, maxHeight = 16.dp, color = Color(0xFFFF375F))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  MÚSICA – EXPANDIDO (datos reales)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicExpandedContent(data: IslandMediaData) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (data.packageName.isNotEmpty()) {
                    val intent = context.packageManager.getLaunchIntentForPackage(data.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        IslandStateHolder.currentState = IslandState.MUSIC_COMPACT
                    }
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2E))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    data.title.ifEmpty { "Sin reproducción" },
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    data.artist.ifEmpty { "—" },
                    color = Color(0xFF8E8E93), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (data.isPlaying) {
                EqualizerBars(barCount = 5, barWidth = 3.dp, maxHeight = 22.dp, color = Color(0xFFFF375F))
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(progress = data.progress)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(data.elapsed, color = Color(0xFF8E8E93), fontSize = 11.sp)
            Text(data.remaining, color = Color(0xFF8E8E93), fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("⏮", color = Color.White, fontSize = 22.sp)
            Text(if (data.isPlaying) "⏸" else "▶", color = Color.White, fontSize = 28.sp)
            Text("⏭", color = Color.White, fontSize = 22.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  TIMER – COMPACTO (datos reales)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TimerCompactContent(data: IslandTimerData) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(22.dp)) {
                drawArc(color = Color(0xFF3A3A3C), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = Color(0xFFFF9F0A), startAngle = -90f, sweepAngle = data.progress * 360f, useCenter = false, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(data.remaining, color = Color(0xFFFF9F0A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  TIMER – EXPANDIDO (datos reales)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TimerExpandedContent(data: IslandTimerData) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(90.dp)) {
                drawArc(color = Color(0xFF3A3A3C), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = Color(0xFFFF9F0A), startAngle = -90f, sweepAngle = data.progress * 360f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(data.remaining, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Text("Cancel", color = Color(0xFF8E8E93), fontSize = 14.sp)
            Text("Pause", color = Color(0xFFFF9F0A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LLAMADA – COMPACTO (datos reales)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CallCompactContent(data: IslandCallData) {
    val infiniteTransition = rememberInfiniteTransition(label = "call")
    val pulse by infiniteTransition.animateFloat(0.7f, 1f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(24.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Color(0xFF30D158)),
            contentAlignment = Alignment.Center
        ) { Text("📞", fontSize = 12.sp) }
        Text(data.duration, color = Color(0xFF30D158), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        WaveformBars(barCount = 8, color = Color(0xFF30D158))
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LLAMADA – EXPANDIDO (datos reales)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CallExpandedContent(data: IslandCallData) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("mobile", color = Color(0xFF8E8E93), fontSize = 12.sp)
        Text(data.name.ifEmpty { "Llamada" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        WaveformBars(barCount = 20, color = Color(0xFF30D158), barHeight = 28.dp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFF3B30)), contentAlignment = Alignment.Center) { Text("📞", fontSize = 18.sp) }
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF30D158)), contentAlignment = Alignment.Center) { Text("📞", fontSize = 18.sp) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  ANIMACIÓN DE CANDADO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun LockAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "lock")
    val bounce by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce")
    Box(contentAlignment = Alignment.Center) {
        Text(if (bounce > 0.5f) "🔒" else "🔓", fontSize = 18.sp, modifier = Modifier.graphicsLayer { scaleX = 0.8f + bounce * 0.4f; scaleY = 0.8f + bounce * 0.4f })
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  COMPONENTES REUTILIZABLES
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun EqualizerBars(barCount: Int, barWidth: Dp, maxHeight: Dp, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val phases = remember { List(barCount) { Random.nextInt(200, 600) } }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        phases.forEachIndexed { i, duration ->
            val anim by infiniteTransition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar$i")
            Box(modifier = Modifier.width(barWidth).height(maxHeight * anim).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
private fun WaveformBars(barCount: Int, color: Color, barHeight: Dp = 14.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "wf")
    val phases = remember { List(barCount) { Random.nextInt(150, 500) } }
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.CenterVertically) {
        phases.forEachIndexed { i, duration ->
            val anim by infiniteTransition.animateFloat(0.15f, 1f, infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wbar$i")
            Box(modifier = Modifier.width(2.dp).height(barHeight * anim).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val animProgress by animateFloatAsState(progress, tween(400), label = "progress")
    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
        drawRoundRect(color = Color(0xFF3A3A3C), cornerRadius = CornerRadius(4f, 4f), size = Size(size.width, size.height))
        drawRoundRect(color = Color.White, cornerRadius = CornerRadius(4f, 4f), size = Size(size.width * animProgress, size.height))
    }
}
