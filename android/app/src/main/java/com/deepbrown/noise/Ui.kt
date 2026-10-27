package com.deepbrown.noise

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/* ---------------------------------------------------------------------- *
 * Palette and theme (taken from the web app's styles.css)
 * ---------------------------------------------------------------------- */
private val Bg = Color(0xFF120C09)
private val Sand = Color(0xFFF0E2CF)
private val Muted = Color(0xFFB39D85)
private val Line = Color(0x24F0E2CF)
private val Track = Color(0x29F0E2CF)
private val Chip = Color(0x12F0E2CF)
private val Accent = Color(0xFF86D1C0)
private val AccentInk = Color(0xFF052019)
private val Warm = Color(0xFFF0A58F)
private val DarkButtonInk = Color(0xFF2A1A10)

@Composable
fun DeepBrownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = AccentInk,
            background = Bg,
            onBackground = Sand,
            surface = Bg,
            onSurface = Sand,
            secondary = Muted,
            onSecondary = Bg,
            error = Warm,
            onError = Bg,
        ),
        content = content,
    )
}

/* ---------------------------------------------------------------------- *
 * Screen
 * ---------------------------------------------------------------------- */
@Composable
fun DeepBrownScreen(
    state: UiState,
    onToggle: () -> Unit,
    onDeep: (Float) -> Unit,
    onPreset: (Preset) -> Unit,
    onPrevPreset: () -> Unit,
    onNextPreset: () -> Unit,
    onParam: (String, Float) -> Unit,
    onTimer: (Int) -> Unit,
    scope: () -> FloatArray?,
) {
    val params = state.params
    val presetName = Presets.byId(state.presetId)?.name ?: "Custom"
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val fieldHeight = (screenHeight * 0.38f).coerceIn(230.dp, 390.dp)

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
            Text(
                text = "Deep Brown",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                letterSpacing = (-0.3).sp,
            )

            DepthField(
                state = state,
                height = fieldHeight,
                onDeep = onDeep,
                scope = scope,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(52.dp, Chip, "Previous preset", onPrevPreset) {
                    TransportIcon(TransportKind.Previous, Sand, 22.dp)
                }
                Spacer(Modifier.width(28.dp))
                RoundButton(
                    size = 88.dp,
                    background = Accent,
                    description = if (state.playing) "Pause" else "Play",
                    onClick = onToggle,
                ) {
                    TransportIcon(
                        if (state.playing) TransportKind.Pause else TransportKind.Play,
                        AccentInk,
                        38.dp,
                    )
                }
                Spacer(Modifier.width(28.dp))
                RoundButton(52.dp, Chip, "Next preset", onNextPreset) {
                    TransportIcon(TransportKind.Next, Sand, 22.dp)
                }
            }

            Text(
                text = state.error ?: if (state.playing) "Playing $presetName" else "Paused",
                modifier = Modifier.fillMaxWidth(),
                color = if (state.error != null) Warm else Muted,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Presets.all.forEach { preset ->
                    PresetChip(
                        name = preset.name,
                        selected = state.presetId == preset.id,
                        onClick = { onPreset(preset) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlSlider(
                    label = "Rumble",
                    value = params.rumble,
                    description = "Extra weight below 90 Hz",
                    onChange = { onParam("rumble", it) },
                )
                ControlSlider(
                    label = "Width",
                    value = params.width,
                    description = "From centred to enveloping",
                    onChange = { onParam("width", it) },
                )
                ControlSlider(
                    label = "Swell",
                    value = params.swell,
                    description = "Slow, never-repeating breathing",
                    onChange = { onParam("swell", it) },
                )
                ControlSlider(
                    label = "Volume",
                    value = params.volume,
                    description = "Level stays even as depth changes",
                    onChange = { onParam("volume", it) },
                )
            }

            TimerSection(
                timerEndWall = state.timerEndWall,
                timerMin = state.timerMin,
                onTimer = onTimer,
            )

            Text(
                text = "Deep settings need headphones or a speaker that reaches below 100 Hz. " +
                    "Phone speakers can't reproduce them.",
                color = Muted,
                fontSize = 13.sp,
            )
            Text(
                text = "Version 1.0 · Android",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            }
        }
    }
}

/* ---------------------------------------------------------------------- *
 * Depth field: gradient panel + live waveform; drag vertically to set depth
 * ---------------------------------------------------------------------- */
@Composable
private fun DepthField(
    state: UiState,
    height: Dp,
    onDeep: (Float) -> Unit,
    scope: () -> FloatArray?,
) {
    val deep = state.params.deep
    val cut = Sound.cutoffHz(deep)
    var hintGone by rememberSaveable { mutableStateOf(false) }
    val currentDeep by rememberUpdatedState(deep)
    val currentOnDeep by rememberUpdatedState(onDeep)

    val points = remember { mutableStateOf(FloatArray(SCOPE_POINTS)) }
    val peak = remember { mutableFloatStateOf(0.05f) }

    LaunchedEffect(state.playing) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last < 33_000_000L) return@withFrameNanos
                last = now

                val previous = points.value
                val next = FloatArray(previous.size)
                val live = if (state.playing) scope() else null
                if (live != null && live.size >= previous.size) {
                    var mx = 0f
                    for (v in live) mx = max(mx, abs(v))
                    peak.floatValue = max(peak.floatValue * 0.995f, max(mx, 0.02f))
                    for (i in previous.indices) {
                        next[i] = previous[i] + 0.55f * (live[i] / peak.floatValue - previous[i])
                    }
                } else {
                    val t = now / 1_000_000_000.0
                    for (i in previous.indices) {
                        val x = i.toFloat() / previous.size
                        val target = (
                            0.18 * sin(x * 6.28318 * 1.3 + t * 0.4) +
                                0.08 * sin(x * 6.28318 * 3.1 - t * 0.25)
                            ).toFloat()
                        next[i] = previous[i] + 0.1f * (target - previous[i])
                    }
                }
                points.value = next
            }
        }
    }

    val top = mixColor(intArrayOf(122, 77, 44), intArrayOf(38, 24, 16), deep)
    val bottom = mixColor(intArrayOf(32, 20, 13), intArrayOf(7, 4, 3), deep)
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, Line, RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { hintGone = true },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val h = size.height.toFloat()
                        currentOnDeep((currentDeep + dy / (h * Y_SPAN)).coerceIn(0f, 1f))
                    },
                )
            }
            .semantics {
                contentDescription = "Deepness"
                stateDescription = "${(deep * 100).roundToInt()} percent, rolls off above " +
                    "${(cut / 5).roundToInt() * 5} hertz"
                progressBarRangeInfo = ProgressBarRangeInfo(deep, 0f..1f)
                setProgress { target ->
                    currentOnDeep(target.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val y0 = yOf(deep) * h
            val amp = h * 0.07f
            val values = points.value
            val dx = w / (values.size - 1)

            val line = Path()
            line.moveTo(0f, y0 + values[0] * amp)
            for (i in 1 until values.size) {
                line.lineTo(i * dx, y0 + values[i] * amp)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Color(0x6B060403))
            drawPath(
                path = line,
                color = Accent,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // scale marks and labels on the right edge
            for (p in 0..4) {
                val y = yOf(p / 4f) * h
                drawLine(
                    color = Sand.copy(alpha = 0.38f),
                    start = Offset(w - 20.dp.toPx(), y),
                    end = Offset(w - 10.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            labelPaint.textSize = 12.sp.toPx()
            labelPaint.color = Sand.copy(alpha = 0.62f).toArgb()
            drawContext.canvas.nativeCanvas.apply {
                drawText("Shallow", w - 28.dp.toPx(), yOf(0f) * h + 4.dp.toPx(), labelPaint)
                drawText("Deep", w - 28.dp.toPx(), yOf(1f) * h + 4.dp.toPx(), labelPaint)
            }

            // current depth handle
            drawRoundRect(
                color = Sand,
                topLeft = Offset(w - 30.dp.toPx(), y0 - 2.dp.toPx()),
                size = Size(22.dp.toPx(), 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }

        Column(Modifier.align(Alignment.TopStart).padding(22.dp)) {
            Text(
                text = (deep * 100).roundToInt().toString(),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 60.sp,
                lineHeight = 60.sp,
                letterSpacing = (-1).sp,
            )
            Text("Deepness", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Rolls off above ${(cut / 5).roundToInt() * 5} Hz",
                fontSize = 13.sp,
                color = Sand.copy(alpha = 0.78f),
            )
        }

        if (!hintGone) {
            Text(
                text = "Drag the line down to go deeper",
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 16.dp),
                fontSize = 13.sp,
                color = Sand.copy(alpha = 0.7f),
            )
        }
    }
}

/* ---------------------------------------------------------------------- *
 * Small components
 * ---------------------------------------------------------------------- */
@Composable
private fun RoundButton(
    size: Dp,
    background: Color,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private enum class TransportKind { Play, Pause, Previous, Next }

@Composable
private fun TransportIcon(kind: TransportKind, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            TransportKind.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.32f, h * 0.18f)
                    lineTo(w * 0.32f, h * 0.82f)
                    lineTo(w * 0.82f, h * 0.50f)
                    close()
                }
                drawPath(path, tint)
            }
            TransportKind.Pause -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.28f, h * 0.20f),
                    size = Size(w * 0.15f, h * 0.60f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.57f, h * 0.20f),
                    size = Size(w * 0.15f, h * 0.60f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
            }
            TransportKind.Previous -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.25f, h * 0.22f),
                    size = Size(w * 0.08f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.02f),
                )
                val path = Path().apply {
                    moveTo(w * 0.78f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.78f)
                    lineTo(w * 0.38f, h * 0.50f)
                    close()
                }
                drawPath(path, tint)
            }
            TransportKind.Next -> {
                val path = Path().apply {
                    moveTo(w * 0.22f, h * 0.22f)
                    lineTo(w * 0.22f, h * 0.78f)
                    lineTo(w * 0.62f, h * 0.50f)
                    close()
                }
                drawPath(path, tint)
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.67f, h * 0.22f),
                    size = Size(w * 0.08f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.02f),
                )
            }
        }
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Accent.copy(alpha = 0.12f) else Chip)
            .border(1.dp, if (selected) Accent else Color.Transparent, shape)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            color = if (selected) Accent else Sand,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    description: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(value * 100).roundToInt()}%",
                color = Muted,
                fontSize = 15.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Sand,
                activeTrackColor = Accent,
                inactiveTrackColor = Track,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(description, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun TimerSection(
    timerEndWall: Long,
    timerMin: Int,
    onTimer: (Int) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerEndWall) {
        now = System.currentTimeMillis()
        while (timerEndWall > 0L) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val remaining = (timerEndWall - now).coerceAtLeast(0L)
    val timerText = when {
        timerEndWall == 0L -> ""
        remaining >= 120_000L -> "Stops in ${ceil(remaining / 60_000.0).roundToInt()} min"
        else -> {
            val minutes = remaining / 60_000L
            val seconds = (remaining % 60_000L) / 1000L
            "Stops in $minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep timer", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(timerText, color = Accent, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Chip, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "60 min", 90 to "90 min")
                .forEach { (minutes, label) ->
                    val selected = timerMin == minutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Sand else Color.Transparent)
                            .selectable(selected = selected, onClick = { onTimer(minutes) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) DarkButtonInk else Sand,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
        }
    }
}

/* ---------------------------------------------------------------------- *
 * Helpers
 * ---------------------------------------------------------------------- */
private const val SCOPE_POINTS = 72
private const val Y_START = 0.32f
private const val Y_SPAN = 0.52f

private fun yOf(depth: Float): Float = Y_START + Y_SPAN * depth

private fun mixColor(from: IntArray, to: IntArray, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = (from[0] + (to[0] - from[0]) * u).roundToInt(),
        green = (from[1] + (to[1] - from[1]) * u).roundToInt(),
        blue = (from[2] + (to[2] - from[2]) * u).roundToInt(),
    )
}
