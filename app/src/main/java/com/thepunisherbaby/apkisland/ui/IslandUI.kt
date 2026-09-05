package com.thepunisherbaby.apkisland.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thepunisherbaby.apkisland.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random

// ─── Fuentes Custom ──────────────────────────────────────────────────
val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

// ─── Color negro OLED puro ───────────────────────────────────────────
private val OledBlack = Color(0xFF000000)

// ─── Estados de la isla ───────────────────────────────────────────────
enum class IslandState {
    IDLE,
    MUSIC_COMPACT,
    MUSIC_EXPANDED,
    TIMER_COMPACT,
    TIMER_EXPANDED,
    CALL_COMPACT,
    CALL_EXPANDED,
    LOCK_ANIM
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
    val progress: Float = 0f,
    val baseTime: Long = 0L
)

// ─── Estado global compartido con el servicio ────────────────────────
object IslandStateHolder {
    var currentState by mutableStateOf(IslandState.IDLE)
    var mediaData by mutableStateOf(IslandMediaData())
    var callData by mutableStateOf(IslandCallData())
    var timerData by mutableStateOf(IslandTimerData())
    var currentArtwork by mutableStateOf<android.graphics.Bitmap?>(null)
    var isScreenOff by mutableStateOf(false)
    var unlockEvent by mutableLongStateOf(0L)
    var gyroBias by mutableFloatStateOf(0f)
    var screenRotation by mutableIntStateOf(android.view.Surface.ROTATION_0)
    var idleAuraTrigger by mutableLongStateOf(0L)

    fun onScreenOff() {
        isScreenOff = true
    }

    fun onUnlock() {
        isScreenOff = false
        unlockEvent = System.currentTimeMillis()
    }

    fun triggerIdleAura() {
        idleAuraTrigger = System.currentTimeMillis()
    }

    fun triggerUnlock() {
        onUnlock()
    }
}

// ─── Composable raíz ─────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandUI() {
    val state = IslandStateHolder.currentState

    val activeCompactStates = buildList {
        if (IslandStateHolder.mediaData.isPlaying) add(IslandState.MUSIC_COMPACT)
        if (IslandStateHolder.timerData.remaining != "0:00") add(IslandState.TIMER_COMPACT)
        if (IslandStateHolder.callData.name.isNotEmpty()) add(IslandState.CALL_COMPACT)
    }
    var compactIndex by remember { mutableIntStateOf(0) }

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

    // Estados de activación de aura temporal (por desbloqueo o toque en reposo)
    var isIdleAuraActive by remember { mutableStateOf(false) }
    var isUnlockAuraActive by remember { mutableStateOf(false) }

    LaunchedEffect(IslandStateHolder.idleAuraTrigger) {
        if (IslandStateHolder.idleAuraTrigger > 0L) {
            isIdleAuraActive = true
            delay(5500)
            isIdleAuraActive = false
        }
    }

    // Geometría basada en Retícula de Diseño (grid 4dp/8dp) y Píldora 100% redondeada
    val targetW: Dp = when {
        isExpanded -> 340.dp
        state == IslandState.MUSIC_COMPACT -> 144.dp
        state == IslandState.TIMER_COMPACT || state == IslandState.CALL_COMPACT -> 112.dp
        isIdleAuraActive || isUnlockAuraActive -> 88.dp
        else -> 72.dp // Píldora de reposo simétrica sobre el orificio de la cámara
    }
    val targetH: Dp = when {
        isExpanded -> 180.dp
        else       -> 34.dp
    }
    // Bordes 100% redondeados (radio = mitad de la altura = 17dp) para la cápsula en reposo y compacta
    val targetCorner: Dp = when {
        isExpanded -> 36.dp
        else       -> 17.dp
    }

    val morphSpec: AnimationSpec<Dp> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )
    val width  by animateDpAsState(targetW, morphSpec, label = "w")
    val height by animateDpAsState(targetH, morphSpec, label = "h")
    val corner by animateDpAsState(targetCorner, morphSpec, label = "c")

    // Desplazamiento hacia abajo al expandir para respetar la barra de estado
    val expandOffsetY by animateDpAsState(
        targetValue = if (isExpanded) 18.dp else 0.dp,
        animationSpec = morphSpec,
        label = "offsetY"
    )

    val scaleAnim = remember { Animatable(1f) }
    LaunchedEffect(state) {
        scaleAnim.snapTo(0.96f)
        scaleAnim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }

    // Brillo sutil por estado funcional
    val glowColor by animateColorAsState(
        targetValue = when (state) {
            IslandState.MUSIC_COMPACT, IslandState.MUSIC_EXPANDED -> Color.Transparent
            IslandState.TIMER_COMPACT, IslandState.TIMER_EXPANDED -> Color(0x40FFCC00)
            IslandState.CALL_COMPACT, IslandState.CALL_EXPANDED   -> Color(0x4030D158)
            IslandState.LOCK_ANIM                                  -> Color(0x40FFFFFF)
            else                                                   -> Color.Transparent
        },
        animationSpec = tween(500),
        label = "glow"
    )

    // Animación de desbloqueo: arranca de 0 sin encogerse, con ráfaga de giro rápido
    val unlockScaleX = remember { Animatable(1f) }
    var spinBurst by remember { mutableFloatStateOf(0f) }
    var auraRotation by remember { mutableFloatStateOf(0f) }
    var smoothGyro by remember { mutableFloatStateOf(0f) }

    // Silenciosamente resetea la escala a 0 al apagarse la pantalla en la oscuridad
    LaunchedEffect(IslandStateHolder.isScreenOff) {
        if (IslandStateHolder.isScreenOff) {
            unlockScaleX.snapTo(0f)
            isUnlockAuraActive = false
        }
    }

    // Al desbloquear: brota desde la cámara de 0 a 1 con ráfaga inicial de alta velocidad
    LaunchedEffect(IslandStateHolder.unlockEvent) {
        if (IslandStateHolder.unlockEvent > 0L) {
            isUnlockAuraActive = true
            spinBurst = 850f // Giro súper rápido al despertar
            unlockScaleX.snapTo(0f)
            unlockScaleX.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow)
            )
            delay(4200)
            isUnlockAuraActive = false
        }
    }

    // Al tocar en reposo: ráfaga de giro y sutil rebote táctil
    LaunchedEffect(IslandStateHolder.idleAuraTrigger) {
        if (IslandStateHolder.idleAuraTrigger > 0L) {
            spinBurst = 650f
            scaleAnim.snapTo(0.92f)
            scaleAnim.animateTo(1f, spring(dampingRatio = 0.45f))
        }
    }

    // Bucle de física fluida a 120Hz gobernada por giroscopio y ráfaga con decaimiento
    LaunchedEffect(Unit) {
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now

                // Decaimiento exponencial de la ráfaga de giro
                if (spinBurst > 1f) {
                    spinBurst *= (1f - dt * 2.2f).coerceAtLeast(0f)
                } else {
                    spinBurst = 0f
                }

                // Suavizado del sensor giroscópico
                val targetGyro = IslandStateHolder.gyroBias
                smoothGyro += (targetGyro - smoothGyro) * 0.18f

                // Velocidad base (45°/s) + giroscopio + ráfaga de giro
                val baseSpeed = 45f
                val currentSpeed = baseSpeed + (smoothGyro * 220f) + spinBurst
                auraRotation = (auraRotation + currentSpeed * dt) % 360f
                if (auraRotation < 0f) auraRotation += 360f
            }
        }
    }

    // Visibilidad del aura de colores vivos (música, desbloqueo o toque en reposo)
    val isAuraVisible = isIdleAuraActive || isUnlockAuraActive || state == IslandState.MUSIC_COMPACT || state == IslandState.MUSIC_EXPANDED
    val auraAlpha by animateFloatAsState(
        targetValue = if (isAuraVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "aura_alpha"
    )

    // Dimensiones exactas del contenedor que garantizan alineación concéntrica con el orificio de la cámara
    val isLandscape = IslandStateHolder.screenRotation == android.view.Surface.ROTATION_90 ||
                      IslandStateHolder.screenRotation == android.view.Surface.ROTATION_270
    val pillW = if (isLandscape) height else width
    val pillH = if (isLandscape) width else height
    val boxWidth = pillW + 24.dp
    val boxHeight = pillH + 24.dp

    Box(
        modifier = Modifier
            .offset(y = if (isLandscape) 0.dp else expandOffsetY)
            .width(boxWidth)
            .height(boxHeight)
            .graphicsLayer {
                if (isLandscape) {
                    scaleX = 1f
                    scaleY = unlockScaleX.value
                } else {
                    scaleX = unlockScaleX.value
                    scaleY = 1f
                }
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
        contentAlignment = Alignment.Center
    ) {
        // Aura Cromática Gemini Abstracta y Contrarrotatoria con colores híper vívidos
        GeminiChromaticAura(
            pillWidth = pillW,
            pillHeight = pillH,
            corner = corner,
            rotationAngle = auraRotation,
            alpha = auraAlpha
        )

        if (glowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .width(pillW + 2.dp)
                    .height(pillH + 2.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(glowColor)
                    .blur(12.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(pillW)
                .height(pillH)
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                }
                .clip(RoundedCornerShape(corner))
                .background(OledBlack)
                .then(
                    if (state == IslandState.IDLE) {
                        Modifier.pointerInput(state) {
                            detectTapGestures(
                                onTap = {
                                    android.util.Log.d("IslandUI", "Tap en IDLE detectado -> invocando aura!")
                                    IslandStateHolder.triggerIdleAura()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                IslandState.IDLE           -> { }
                IslandState.LOCK_ANIM      -> LockAnimation()
                IslandState.MUSIC_COMPACT  -> MusicCompactContent(
                    data = IslandStateHolder.mediaData, 
                    artwork = IslandStateHolder.currentArtwork,
                    isLandscape = isLandscape,
                    onExpand = { IslandStateHolder.currentState = IslandState.MUSIC_EXPANDED }
                )
                IslandState.MUSIC_EXPANDED -> MusicExpandedContent(IslandStateHolder.mediaData, IslandStateHolder.currentArtwork)
                IslandState.TIMER_COMPACT  -> TimerCompactContent(IslandStateHolder.timerData)
                IslandState.TIMER_EXPANDED -> TimerExpandedContent(IslandStateHolder.timerData)
                IslandState.CALL_COMPACT   -> CallCompactContent(IslandStateHolder.callData)
                IslandState.CALL_EXPANDED  -> CallExpandedContent(IslandStateHolder.callData)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  MÚSICA – COMPACTO (Soporta swipe elástico a la izq / der para cambiar canción)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicCompactContent(
    data: IslandMediaData, 
    artwork: android.graphics.Bitmap?,
    isLandscape: Boolean = false,
    onExpand: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var swipeTotal by remember { mutableFloatStateOf(0f) }

    val contentModifier = if (isLandscape) {
        Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp)
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { swipeTotal = 0f },
                    onDragEnd = {
                        if (swipeTotal > 20f) {
                            android.util.Log.d("IslandUI", "Swipe vertical abajo -> skipToNext()")
                            com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.skipToNext()
                        } else if (swipeTotal < -20f) {
                            android.util.Log.d("IslandUI", "Swipe vertical arriba -> skipToPrevious()")
                            com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.skipToPrevious()
                        }
                        swipeTotal = 0f
                        coroutineScope.launch {
                            dragOffset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                        }
                    },
                    onDragCancel = {
                        swipeTotal = 0f
                        coroutineScope.launch { dragOffset.animateTo(0f) }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    swipeTotal += dragAmount
                    coroutineScope.launch {
                        dragOffset.snapTo(dragOffset.value + dragAmount * 0.4f)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (data.packageName.isNotEmpty()) {
                            val intent = context.packageManager.getLaunchIntentForPackage(data.packageName)
                            if (intent != null) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onLongPress = { onExpand() }
                )
            }
    } else {
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp)
            .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeTotal = 0f },
                    onDragEnd = {
                        if (swipeTotal > 20f) {
                            android.util.Log.d("IslandUI", "Swipe horizontal derecha -> skipToNext()")
                            com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.skipToNext()
                        } else if (swipeTotal < -20f) {
                            android.util.Log.d("IslandUI", "Swipe horizontal izquierda -> skipToPrevious()")
                            com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.skipToPrevious()
                        }
                        swipeTotal = 0f
                        coroutineScope.launch {
                            dragOffset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                        }
                    },
                    onDragCancel = {
                        swipeTotal = 0f
                        coroutineScope.launch { dragOffset.animateTo(0f) }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    swipeTotal += dragAmount
                    coroutineScope.launch {
                        dragOffset.snapTo(dragOffset.value + dragAmount * 0.4f)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (data.packageName.isNotEmpty()) {
                            val intent = context.packageManager.getLaunchIntentForPackage(data.packageName)
                            if (intent != null) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onLongPress = { onExpand() }
                )
            }
    }

    if (isLandscape) {
        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                )
            } else {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF2C2C2E)))
            }

            Spacer(Modifier.weight(1f))

            if (data.isPlaying) {
                EqualizerBars(barCount = 3, barWidth = 2.5.dp, maxHeight = 14.dp, color = Color(0xFF1DB954))
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    } else {
        Row(
            modifier = contentModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                )
            } else {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF2C2C2E)))
            }

            Spacer(Modifier.weight(1f))

            if (data.isPlaying) {
                EqualizerBars(barCount = 3, barWidth = 3.dp, maxHeight = 16.dp, color = Color(0xFF1DB954))
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.size(24.dp)) 
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  MÚSICA – EXPANDIDO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicExpandedContent(data: IslandMediaData, artwork: android.graphics.Bitmap?) {
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
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    data.title.ifEmpty { "Sin reproducción" },
                    color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    data.artist.ifEmpty { "—" },
                    color = Color(0xFF8E8E93), fontSize = 13.sp, fontFamily = PoppinsFontFamily,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (data.isPlaying) {
                EqualizerBars(barCount = 5, barWidth = 3.dp, maxHeight = 22.dp, color = Color(0xFF1DB954))
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(progress = data.progress)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(data.elapsed, color = Color(0xFF8E8E93), fontSize = 11.sp, fontFamily = PoppinsFontFamily)
            Text(data.remaining, color = Color(0xFF8E8E93), fontSize = 11.sp, fontFamily = PoppinsFontFamily)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "⏮", 
                color = Color.White, 
                fontSize = 24.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.skipToPrevious()
                    }
                    .padding(8.dp)
            )
            Text(
                if (data.isPlaying) "⏸" else "▶", 
                color = Color.White, 
                fontSize = 30.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.playPause()
                    }
                    .padding(8.dp)
            )
            Text(
                "⏭", 
                color = Color.White, 
                fontSize = 24.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        com.thepunisherbaby.apkisland.logic.IslandNotificationListenerService.skipToNext()
                    }
                    .padding(8.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  TIMER – COMPACTO (Diseño Apple: Outline como barra de progreso)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TimerCompactContent(data: IslandTimerData) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(data.baseTime) {
        if (data.baseTime > 0) {
            while (true) {
                delay(1000)
                tick = System.currentTimeMillis()
            }
        }
    }

    val remainingMs = if (data.baseTime > 0) (data.baseTime - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) else 0L
    val min = remainingMs / 60000
    val sec = (remainingMs % 60000) / 1000
    val displayTime = if (data.baseTime > 0) "$min:${sec.toString().padStart(2, '0')}" else data.remaining

    Box(modifier = Modifier.fillMaxSize()) {
        // Outline como barra de progreso que envuelve la píldora
        Canvas(modifier = Modifier.fillMaxSize().padding(1.dp)) {
            val path = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2)))
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            
            val progressPath = Path()
            measure.getSegment(0f, measure.length * data.progress, progressPath, true)
            
            // Fondo tenue
            drawPath(path, color = Color(0xFF3A3A3C), style = Stroke(width = 2.dp.toPx()))
            // Progreso amarillo Apple
            drawPath(progressPath, color = Color(0xFFFFCC00), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⏱", fontSize = 14.sp)
            Text(
                displayTime, 
                color = Color(0xFFFFCC00), 
                fontSize = 14.sp, 
                fontFamily = PoppinsFontFamily, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  TIMER – EXPANDIDO 
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TimerExpandedContent(data: IslandTimerData) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(data.baseTime) {
        if (data.baseTime > 0) {
            while (true) {
                delay(1000)
                tick = System.currentTimeMillis()
            }
        }
    }

    val remainingMs = if (data.baseTime > 0) (data.baseTime - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) else 0L
    val min = remainingMs / 60000
    val sec = (remainingMs % 60000) / 1000
    val displayTime = if (data.baseTime > 0) "$min:${sec.toString().padStart(2, '0')}" else data.remaining

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(90.dp)) {
                drawArc(color = Color(0xFF3A3A3C), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = Color(0xFFFFCC00), startAngle = -90f, sweepAngle = data.progress * 360f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(displayTime, color = Color.White, fontSize = 22.sp, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Text("Cancel", color = Color(0xFF8E8E93), fontSize = 14.sp, fontFamily = PoppinsFontFamily)
            Text("Pause", color = Color(0xFFFFCC00), fontSize = 14.sp, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LLAMADA – COMPACTO 
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CallCompactContent(data: IslandCallData) {
    val infiniteTransition = rememberInfiniteTransition(label = "call")
    val pulse by infiniteTransition.animateFloat(0.7f, 1f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(24.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Color(0xFF30D158)),
            contentAlignment = Alignment.Center
        ) { Text("📞", fontSize = 12.sp) }
        Text(data.duration, color = Color(0xFF30D158), fontSize = 14.sp, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LLAMADA – EXPANDIDO 
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CallExpandedContent(data: IslandCallData) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Llamada Entrante", color = Color(0xFF8E8E93), fontSize = 12.sp, fontFamily = PoppinsFontFamily)
        Text(data.name.ifEmpty { "Desconocido" }, color = Color.White, fontSize = 18.sp, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
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
    // Animación de ecualizador muchísimo más lenta como pediste (hasta 3 segundos)
    val phases = remember { List(barCount) { Random.nextInt(1500, 3000) } }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        phases.forEachIndexed { i, duration ->
            val anim by infiniteTransition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse), label = "bar$i")
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * anim)
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .blur(1.5.dp) // Blur simulado de movimiento
            )
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

// ═══════════════════════════════════════════════════════════════════════
//  AURA CROMÁTICA GEMINI ABSTRACTA Y CONTRARROTATORIA (Hipervívida y orgánica)
// ═══════════════════════════════════════════════════════════════════════

// Capa A: Sentido Horario (Espectro Eléctrico Frío / Cósmico con tonos vivos de Google)
private val GeminiCoolVividColorsInt = intArrayOf(
    0xFF0066FF.toInt(), // Azul Eléctrico Profundo
    0xFF00B0FF.toInt(), // Cian Neón Resplandeciente
    0xFF00F5FF.toInt(), // Cian Espectral
    0xFF00FF88.toInt(), // Verde Neón Menta
    0xFF76FF03.toInt(), // Lima Ácido Vivo
    0xFFFFEA00.toInt(), // Amarillo Solar Neón
    0xFF0066FF.toInt()  // Cierre seamless
)

// Posiciones asimétricas no lineales para romper la uniformidad de rueda mecánica
private val GeminiCoolVividPositions = floatArrayOf(
    0.00f, 0.16f, 0.38f, 0.62f, 0.86f, 0.95f, 1.00f
)

// Capa B: Sentido Antihorario (Espectro Cálido / Fuego / Magenta Ultravioleta)
private val GeminiWarmVividColorsInt = intArrayOf(
    0xFFFF0033.toInt(), // Carmesí Neón Fuego
    0xFFFF5500.toInt(), // Naranja Llamarada
    0xFFFF00D4.toInt(), // Magenta Eléctrico Hipervívido
    0xFF9C27B0.toInt(), // Púrpura Orquídea Intenso
    0xFF7928CA.toInt(), // Púrpura Ultravioleta Cósmico
    0xFF3D5AFE.toInt(), // Índigo Eléctrico
    0xFFFF0033.toInt()  // Cierre seamless
)

// Posiciones asimétricas distintas para la Capa B (exactamente 7 elementos)
private val GeminiWarmVividPositions = floatArrayOf(
    0.00f, 0.18f, 0.38f, 0.58f, 0.75f, 0.90f, 1.00f
)

@Composable
private fun GeminiChromaticAura(
    pillWidth: Dp,
    pillHeight: Dp,
    corner: Dp,
    rotationAngle: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    val pillWidthPx = with(density) { pillWidth.toPx() }
    val pillHeightPx = with(density) { pillHeight.toPx() }
    val cornerPx = with(density) { corner.toPx() }

    // Respiración orgánica suave para dinamismo continuo
    val infiniteTransition = rememberInfiniteTransition(label = "aura_breathe")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier.size(pillWidth + 24.dp, pillHeight + 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // CAPA 1: Sentido Horario - Resplandor difuminado profundo (14dp blur)
        Canvas(
            modifier = Modifier
                .size(pillWidth + 24.dp, pillHeight + 24.dp)
                .blur(14.dp)
        ) {
            val left = (size.width - pillWidthPx) / 2f
            val top = (size.height - pillHeightPx) / 2f
            val pillCenter = Offset(left + pillWidthPx / 2f, top + pillHeightPx / 2f)

            val sweepShader = android.graphics.SweepGradient(
                pillCenter.x,
                pillCenter.y,
                GeminiCoolVividColorsInt,
                GeminiCoolVividPositions
            )
            val matrix = android.graphics.Matrix()
            matrix.setRotate(rotationAngle, pillCenter.x, pillCenter.y)
            sweepShader.setLocalMatrix(matrix)

            drawRoundRect(
                brush = ShaderBrush(sweepShader),
                topLeft = Offset(left - 0.5.dp.toPx(), top - 0.5.dp.toPx()),
                size = Size(pillWidthPx + 1.dp.toPx(), pillHeightPx + 1.dp.toPx()),
                cornerRadius = CornerRadius(cornerPx + 0.5.dp.toPx(), cornerPx + 0.5.dp.toPx()),
                style = Stroke(width = 6.dp.toPx()),
                alpha = 0.72f * alpha * breathe
            )
        }

        // CAPA 2: Sentido Antihorario - Vórtice complementario dinámico (8dp blur)
        // Al girar en sentido inverso (-rotationAngle * 0.82f), los colores cálidos y fríos se cruzan
        // produciendo mezclas abstractas vivas en constante mutación
        Canvas(
            modifier = Modifier
                .size(pillWidth + 24.dp, pillHeight + 24.dp)
                .blur(8.dp)
        ) {
            val left = (size.width - pillWidthPx) / 2f
            val top = (size.height - pillHeightPx) / 2f
            val pillCenter = Offset(left + pillWidthPx / 2f, top + pillHeightPx / 2f)

            val sweepShader = android.graphics.SweepGradient(
                pillCenter.x,
                pillCenter.y,
                GeminiWarmVividColorsInt,
                GeminiWarmVividPositions
            )
            val matrix = android.graphics.Matrix()
            matrix.setRotate(-rotationAngle * 0.82f, pillCenter.x, pillCenter.y)
            sweepShader.setLocalMatrix(matrix)

            drawRoundRect(
                brush = ShaderBrush(sweepShader),
                topLeft = Offset(left, top),
                size = Size(pillWidthPx, pillHeightPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 4.5.dp.toPx()),
                alpha = 0.68f * alpha * breathe
            )
        }

        // CAPA 3: Borde ceñido de alta definición (2.5dp blur) al ras del notch
        Canvas(
            modifier = Modifier
                .size(pillWidth + 24.dp, pillHeight + 24.dp)
                .blur(2.5.dp)
        ) {
            val left = (size.width - pillWidthPx) / 2f
            val top = (size.height - pillHeightPx) / 2f
            val pillCenter = Offset(left + pillWidthPx / 2f, top + pillHeightPx / 2f)

            val sweepShader = android.graphics.SweepGradient(
                pillCenter.x,
                pillCenter.y,
                GeminiCoolVividColorsInt,
                GeminiCoolVividPositions
            )
            val matrix = android.graphics.Matrix()
            matrix.setRotate(rotationAngle * 1.25f, pillCenter.x, pillCenter.y)
            sweepShader.setLocalMatrix(matrix)

            drawRoundRect(
                brush = ShaderBrush(sweepShader),
                topLeft = Offset(left, top),
                size = Size(pillWidthPx, pillHeightPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 2.dp.toPx()),
                alpha = 0.82f * alpha * breathe
            )
        }
    }
}

