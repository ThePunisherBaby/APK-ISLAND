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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thepunisherbaby.apkisland.R
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
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
    var unlockTrigger by mutableLongStateOf(0L)

    fun triggerUnlock() {
        unlockTrigger = System.currentTimeMillis()
    }
}

// ─── Composable raíz ─────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandUI() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf(IslandState.IDLE) }
    var swipeAccum by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(IslandStateHolder.currentState) {
        val systemState = IslandStateHolder.currentState
        if (systemState != IslandState.IDLE) {
            if (state == IslandState.IDLE) {
                state = systemState
            }
        } else {
            state = IslandState.IDLE
        }
    }

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

    // TAMAÑO INICIAL FIJO PARA IDLE Y COMPACTO (Aún menos ancha: 92dp)
    val targetW: Dp = when {
        isExpanded -> 340.dp
        else       -> 92.dp   
    }
    val targetH: Dp = when {
        isExpanded -> 180.dp
        else       -> 34.dp
    }
    val targetCorner: Dp = when {
        isExpanded -> 38.dp
        else       -> 50.dp
    }

    val morphSpec: AnimationSpec<Dp> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )
    val width  by animateDpAsState(targetW, morphSpec, label = "w")
    val height by animateDpAsState(targetH, morphSpec, label = "h")
    val corner by animateDpAsState(targetCorner, morphSpec, label = "c")

    // Shift hacia abajo al expandir para no tapar iconos de la barra de estado
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

    // Ya no hay outline rosa para música
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

    // Animación elástica horizontal (0 a 100) al desbloquear desde la cámara
    val unlockScaleX = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        unlockScaleX.snapTo(0f)
        unlockScaleX.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
        )
    }
    LaunchedEffect(IslandStateHolder.unlockTrigger) {
        if (IslandStateHolder.unlockTrigger > 0L) {
            unlockScaleX.snapTo(0f)
            unlockScaleX.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(y = expandOffsetY)
            .width(width + 24.dp)
            .height(height + 24.dp)
            .graphicsLayer {
                scaleX = unlockScaleX.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
        contentAlignment = Alignment.Center
    ) {
        // Outline sutil de estrellitas orbitando el perímetro
        StarOrbitOutline(
            pillWidth = width,
            pillHeight = height,
            corner = corner
        )

        if (glowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .width(width + 2.dp)
                    .height(height + 2.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(glowColor)
                    .blur(12.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                }
                .clip(RoundedCornerShape(corner))
                .background(OledBlack)
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
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        // Un toque simple abre la app de origen o colapsa si está expandida
                        when (state) {
                            IslandState.MUSIC_COMPACT -> {
                                val pkg = IslandStateHolder.mediaData.packageName
                                if (pkg.isNotEmpty()) {
                                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                    if (intent != null) {
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                            IslandState.MUSIC_EXPANDED -> state = IslandState.MUSIC_COMPACT
                            IslandState.TIMER_EXPANDED -> state = IslandState.TIMER_COMPACT
                            IslandState.CALL_EXPANDED  -> state = IslandState.CALL_COMPACT
                            else -> {}
                        }
                    },
                    onLongClick = {
                        // Presión larga expande la isla
                        state = when (state) {
                            IslandState.MUSIC_COMPACT  -> IslandState.MUSIC_EXPANDED
                            IslandState.TIMER_COMPACT  -> IslandState.TIMER_EXPANDED
                            IslandState.CALL_COMPACT   -> IslandState.CALL_EXPANDED
                            IslandState.LOCK_ANIM      -> IslandState.IDLE
                            else -> state
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                IslandState.IDLE           -> { }
                IslandState.LOCK_ANIM      -> LockAnimation()
                IslandState.MUSIC_COMPACT  -> MusicCompactContent(IslandStateHolder.mediaData, IslandStateHolder.currentArtwork)
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
//  MÚSICA – COMPACTO 
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun MusicCompactContent(data: IslandMediaData, artwork: android.graphics.Bitmap?) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
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
            Text("⏮", color = Color.White, fontSize = 22.sp)
            Text(if (data.isPlaying) "⏸" else "▶", color = Color.White, fontSize = 28.sp)
            Text("⏭", color = Color.White, fontSize = 22.sp)
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
//  OUTLINE DE ESTRELLITAS ORBITANDO EL BORDE (Difuminadas y sutiles)
// ═══════════════════════════════════════════════════════════════════════
private data class StarParticle(
    val offset: Float,
    val speedMultiplier: Float,
    val radiusDp: Float,
    val glowRadiusDp: Float,
    val baseAlpha: Float
)

private val starParticles = listOf(
    StarParticle(offset = 0.00f, speedMultiplier = 1.00f, radiusDp = 1.2f, glowRadiusDp = 3.5f, baseAlpha = 0.9f),
    StarParticle(offset = 0.07f, speedMultiplier = 1.05f, radiusDp = 0.8f, glowRadiusDp = 2.5f, baseAlpha = 0.6f),
    StarParticle(offset = 0.14f, speedMultiplier = 0.95f, radiusDp = 1.6f, glowRadiusDp = 4.5f, baseAlpha = 0.95f),
    StarParticle(offset = 0.21f, speedMultiplier = 1.02f, radiusDp = 1.0f, glowRadiusDp = 3.0f, baseAlpha = 0.7f),
    StarParticle(offset = 0.28f, speedMultiplier = 0.98f, radiusDp = 1.4f, glowRadiusDp = 4.0f, baseAlpha = 0.85f),
    StarParticle(offset = 0.35f, speedMultiplier = 1.08f, radiusDp = 0.9f, glowRadiusDp = 2.8f, baseAlpha = 0.65f),
    StarParticle(offset = 0.42f, speedMultiplier = 0.92f, radiusDp = 1.7f, glowRadiusDp = 5.0f, baseAlpha = 0.9f),
    StarParticle(offset = 0.49f, speedMultiplier = 1.03f, radiusDp = 1.1f, glowRadiusDp = 3.2f, baseAlpha = 0.75f),
    StarParticle(offset = 0.56f, speedMultiplier = 0.97f, radiusDp = 1.5f, glowRadiusDp = 4.2f, baseAlpha = 0.85f),
    StarParticle(offset = 0.63f, speedMultiplier = 1.06f, radiusDp = 0.8f, glowRadiusDp = 2.6f, baseAlpha = 0.6f),
    StarParticle(offset = 0.70f, speedMultiplier = 0.94f, radiusDp = 1.8f, glowRadiusDp = 5.2f, baseAlpha = 1.0f),
    StarParticle(offset = 0.77f, speedMultiplier = 1.04f, radiusDp = 1.0f, glowRadiusDp = 3.0f, baseAlpha = 0.7f),
    StarParticle(offset = 0.84f, speedMultiplier = 0.96f, radiusDp = 1.3f, glowRadiusDp = 3.8f, baseAlpha = 0.8f),
    StarParticle(offset = 0.91f, speedMultiplier = 1.02f, radiusDp = 0.9f, glowRadiusDp = 2.7f, baseAlpha = 0.65f),
    StarParticle(offset = 0.96f, speedMultiplier = 0.99f, radiusDp = 1.4f, glowRadiusDp = 4.0f, baseAlpha = 0.85f)
)

@Composable
private fun StarOrbitOutline(
    pillWidth: Dp,
    pillHeight: Dp,
    corner: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val pillWidthPx = with(density) { pillWidth.toPx() }
    val pillHeightPx = with(density) { pillHeight.toPx() }
    val cornerPx = with(density) { corner.toPx() }

    Canvas(modifier = Modifier.size(pillWidth + 24.dp, pillHeight + 24.dp)) {
        val left = (size.width - pillWidthPx) / 2f
        val top = (size.height - pillHeightPx) / 2f
        val rect = android.graphics.RectF(left, top, left + pillWidthPx, top + pillHeightPx)
        val androidPath = android.graphics.Path().apply {
            addRoundRect(rect, cornerPx, cornerPx, android.graphics.Path.Direction.CW)
        }
        val measure = android.graphics.PathMeasure(androidPath, true)
        val length = measure.length

        if (length > 0f) {
            val pos = floatArrayOf(0f, 0f)

            // Trazo cósmico tenue para dar continuidad al outline
            drawRoundRect(
                color = Color(0x12FFFFFF),
                topLeft = Offset(left, top),
                size = Size(pillWidthPx, pillHeightPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 0.75.dp.toPx())
            )

            starParticles.forEach { star ->
                val rawProg = (orbitProgress * star.speedMultiplier + star.offset) % 1f
                val progress = if (rawProg < 0f) rawProg + 1f else rawProg
                measure.getPosTan(progress * length, pos, null)
                val center = Offset(pos[0], pos[1])

                val starAlpha = (star.baseAlpha * twinkle).coerceIn(0.15f, 1f)
                val glowRadius = star.glowRadiusDp.dp.toPx() * twinkle
                val starRadius = star.radiusDp.dp.toPx()

                // Glow difuminado exterior
                drawCircle(
                    color = Color(0x35D0E2FF),
                    radius = glowRadius,
                    center = center
                )
                // Núcleo brillante de la estrella
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha),
                    radius = starRadius,
                    center = center
                )
            }
        }
    }
}

