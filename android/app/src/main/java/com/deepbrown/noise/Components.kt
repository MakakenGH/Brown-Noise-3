package com.deepbrown.noise

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Removes the ripple flash from clickables; state changes and haptics give feedback. */
val NoIndication: IndicationNodeFactory = object : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

/* ---------------------------------------------------------------------- */
/* Icons drawn locally                                                     */
/* ---------------------------------------------------------------------- */

@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (playing) {
            drawRoundRect(
                tint,
                topLeft = Offset(w * 0.26f, h * 0.18f),
                size = Size(w * 0.17f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.05f),
            )
            drawRoundRect(
                tint,
                topLeft = Offset(w * 0.57f, h * 0.18f),
                size = Size(w * 0.17f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.05f),
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.18f)
                lineTo(w * 0.32f, h * 0.82f)
                lineTo(w * 0.82f, h * 0.50f)
                close()
            }
            drawPath(path, tint)
        }
    }
}

@Composable
fun MusicNoteIcon(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.62f),
            size = Size(w * 0.36f, h * 0.28f),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.20f),
            size = Size(w * 0.09f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
        val flag = Path().apply {
            moveTo(w * 0.48f, h * 0.20f)
            quadraticTo(w * 0.78f, h * 0.22f, w * 0.86f, h * 0.46f)
            lineTo(w * 0.78f, h * 0.44f)
            quadraticTo(w * 0.68f, h * 0.30f, w * 0.48f, h * 0.32f)
            close()
        }
        drawPath(flag, tint)
    }
}

/* ---------------------------------------------------------------------- */
/* Atmosphere                                                              */
/* ---------------------------------------------------------------------- */

/** A slow colour field that connects the interface to the loaded noise. */
@Composable
fun NoiseAura(color: Color, modifier: Modifier = Modifier) {
    val aura by animateColorAsState(
        targetValue = color,
        animationSpec = tween(1_600, easing = FastOutSlowInEasing),
        label = "aura colour",
    )
    val context = LocalContext.current
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            while (isActive) {
                phase.animateTo(1f, tween(16_000, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }
    Canvas(modifier) {
        val breathe = if (!reduceMotion) {
            0.975f + 0.025f * sin(phase.value * 2f * PI.toFloat())
        } else {
            1f
        }
        val topAlpha = (if (aura.luminance() > 0.7f) 0.15f else 0.22f) * (0.96f + (breathe - 0.975f) * 1.6f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(aura.copy(alpha = topAlpha), Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.06f),
                radius = size.width * 1.05f * breathe,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(aura.copy(alpha = 0.095f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.72f),
                radius = size.width * 0.75f,
            ),
        )
    }
}

/** The live scope buffer, drawn as a thin line. Frozen while paused. */
@Composable
fun LiveWaveform(
    color: Color,
    playing: Boolean,
    scope: () -> FloatArray?,
    modifier: Modifier = Modifier,
    points: Int = 56,
) {
    val wave = remember(points) { mutableStateOf(idleWave(points)) }
    val peak = remember { mutableFloatStateOf(0.02f) }
    val currentScope by rememberUpdatedState(scope)
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last < 33_000_000L) return@withFrameNanos
                last = now
                val live = currentScope() ?: return@withFrameNanos
                val previous = wave.value
                if (live.size < previous.size) return@withFrameNanos
                var mx = 0f
                for (v in live) mx = max(mx, abs(v))
                peak.floatValue = max(peak.floatValue * 0.994f, max(mx, 0.02f))
                val next = FloatArray(previous.size)
                for (i in previous.indices) {
                    next[i] = previous[i] + 0.55f * (live[i] / peak.floatValue - previous[i])
                }
                wave.value = next
            }
        }
    }
    Canvas(modifier) {
        val values = wave.value
        val mid = size.height / 2f
        val amp = size.height * 0.34f
        val dx = size.width / (values.size - 1)
        val path = Path()
        path.moveTo(0f, mid + values[0] * amp)
        for (i in 1 until values.size) {
            path.lineTo(i * dx, mid + values[i] * amp)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = if (playing) 0.85f else 0.40f),
            style = Stroke(width = 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

private fun idleWave(points: Int): FloatArray = FloatArray(points) { i ->
    val x = i.toFloat() / points
    (0.22 * sin(x * 6.28318 * 2.2) + 0.10 * sin(x * 6.28318 * 5.1)).toFloat()
}

/* ---------------------------------------------------------------------- */
/* Surfaces and controls                                                   */
/* ---------------------------------------------------------------------- */

@Composable
fun ColorOrb(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Boolean = false,
) {
    Box(
        modifier = modifier.size(size + if (ring) 8.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (ring) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.5.dp, color.copy(alpha = 0.85f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(lerp(color, Color.White, 0.42f), color),
                    ),
                ),
        )
    }
}

@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 18.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Track),
    )
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        fontFamily = Fraunces,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        color = OnSurface,
    )
}

@Composable
fun SheetSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        fontFamily = DmSans,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        color = OnSurfaceVariant,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    accent: Color = OnSurface,
    enabled: Boolean = true,
) {
    val background by animateColorAsState(
        if (enabled) accent else SurfaceHigh,
        tween(220),
        label = "primary background",
    )
    val content by animateColorAsState(
        if (enabled) Ink else LabelMuted,
        tween(220),
        label = "primary content",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = content)
    }
}

@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Name",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceHigh)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnSurface,
            ),
            cursorBrush = SolidColor(OnSurface),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = DmSans, fontSize = 14.sp, color = LabelMuted)
                }
                inner()
            },
        )
    }
}

/** A row inside a shared list surface, separated by hairlines. */
@Composable
fun ActionRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    content: Color = OnSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(label, fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = content)
    }
}

@Composable
fun HairlineDivider(startPadding: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(1.dp)
            .background(Hairline),
    )
}

@Composable
fun ColorSelector(
    selected: NoiseColor?,
    onSelect: (NoiseColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoiseColor.entries.forEach { color ->
            val active = selected == color
            val labelColor by animateColorAsState(
                if (active) OnSurface else OnSurfaceVariant,
                tween(350, easing = FastOutSlowInEasing),
                label = "orb label",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(color)
                    }
                    .semantics {
                        contentDescription = "${color.label} noise"
                        this.selected = active
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    if (active) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(color.dot.copy(alpha = 0.28f), Color.Transparent),
                                    ),
                                    CircleShape,
                                ),
                        )
                    }
                    ColorOrb(color.dot, if (active) 38.dp else 32.dp, ring = active)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    text = color.label,
                    fontFamily = DmSans,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = labelColor,
                )
            }
        }
    }
}

@Composable
fun SliderTrack(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = OnSurface,
    haptics: Boolean = true,
    contentDescription: String? = null,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val feedback = LocalHapticFeedback.current
    var lastStep by remember { mutableStateOf((value * 10).roundToInt()) }
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(accent) {
                detectTapGestures { offset ->
                    val next = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnChange(next)
                }
            }
            .pointerInput(accent) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val next = (change.position.x / size.width).coerceIn(0f, 1f)
                    val step = (next * 10).roundToInt()
                    if (haptics && step != lastStep) {
                        lastStep = step
                        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    currentOnChange(next)
                }
            }
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress {
                    currentOnChange(it.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val thumbX = value * w
            val lineH = 3.dp.toPx()

            // Fixed dot grid; the filled part simply covers the dots it passes.
            val dot = 1.3.dp.toPx()
            val step = 14.dp.toPx()
            var x = 5.dp.toPx()
            while (x < w) {
                drawCircle(Track, radius = dot, center = Offset(x, cy))
                x += step
            }

            drawRoundRect(
                color = accent,
                topLeft = Offset(0f, cy - lineH / 2f),
                size = Size((thumbX - lineH / 2f).coerceAtLeast(0f), lineH),
                cornerRadius = CornerRadius(lineH / 2f),
            )

            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(thumbX - 1.75.dp.toPx(), cy - 9.dp.toPx()),
                size = Size(3.5.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(1.75.dp.toPx()),
            )
        }
    }
}

/** One control inside a grouped parameter surface. */
@Composable
fun ParameterRow(
    label: String,
    value: Float,
    description: String,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = DmSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = OnSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = Numeric,
                fontSize = 11.5.sp,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(OnSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        SliderTrack(value = value, onChange = onChange, accent = accent, contentDescription = label)
        Text(
            text = description,
            fontFamily = DmSans,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = LabelMuted,
        )
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontFamily = Fraunces,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
            color = OnSurface,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, style = Numeric, fontSize = 11.5.sp, color = LabelMuted)
        }
    }
}

@Composable
fun SoundRow(
    sound: SavedSound,
    active: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
) {
    val bg by animateColorAsState(
        if (active) sound.color.dot.copy(alpha = 0.07f) else Color.Transparent,
        tween(550, easing = FastOutSlowInEasing),
        label = "row background",
    )
    val dot by animateFloatAsState(if (playing && active) 1f else 0.45f, tween(400), label = "status")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(bg)
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorOrb(sound.color.dot, 32.dp, ring = active)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sound.name,
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(sound.color.dot.copy(alpha = dot)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "${sound.color.label} noise",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "${sound.name} options",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun NowPlayingCard(
    title: String,
    subtitle: String,
    color: Color,
    playing: Boolean,
    scope: () -> FloatArray?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(color, tween(900, easing = FastOutSlowInEasing), label = "now playing colour")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceHigh)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.16f), SurfaceHigh, SurfaceHigh),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorOrb(accent, 38.dp, ring = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontFamily = DmSans,
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
            )
        }
        LiveWaveform(
            color = accent,
            playing = playing,
            scope = scope,
            modifier = Modifier
                .width(46.dp)
                .height(20.dp)
                .padding(end = 10.dp),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onToggle)
                .semantics { contentDescription = if (playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(playing, animationSpec = tween(180), label = "play state") { isPlaying ->
                PlayPauseIcon(isPlaying, Ink, 18.dp)
            }
        }
    }
}

@Composable
fun EmptySounds(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(24.dp))
            .clickable(onClick = onCreate)
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No sounds yet",
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = OnSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Start from Quick noise above, or create your own.",
            fontFamily = DmSans,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = LabelMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}

@Composable
fun BottomBar(
    accent: Color,
    onCollection: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    val activeDot by animateColorAsState(accent, tween(900, easing = FastOutSlowInEasing), label = "nav accent")
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Bg, Bg))),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .height(76.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 88.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onCollection)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MusicNoteIcon(OnSurface, 20.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Collection",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp,
                    color = OnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(activeDot))
            }
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceHigh)
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                    .clickable(onClick = onCreate)
                    .padding(start = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(activeDot),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    "New sound",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = OnSurface,
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(min = 88.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onSettings)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Settings",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceHigh)
                .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                .padding(24.dp),
        ) {
            Text(
                text = title,
                fontFamily = Fraunces,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.2).sp,
                color = OnSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                fontFamily = DmSans,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = OnSurface)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (destructive) Danger else OnSurface)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = confirmLabel,
                        fontFamily = DmSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (destructive) DangerInk else Ink,
                    )
                }
            }
        }
    }
}
