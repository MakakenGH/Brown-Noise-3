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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.sin

/** Removes the ripple flash from clickables; state changes and haptics give feedback. */
val NoIndication: IndicationNodeFactory = object : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

@Composable
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Quiet press feedback: a small scale, no ripple or flash. */
@Composable
fun Modifier.pressable(
    scale: Float = 0.97f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentScale by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = tween(90),
        label = "press scale",
    )
    return this
        .graphicsLayer {
            scaleX = currentScale
            scaleY = currentScale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
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

/** A single colour field rising from the bottom of the screen. */
@Composable
fun NoiseAura(color: Color, modifier: Modifier = Modifier) {
    val aura by animateColorAsState(
        targetValue = color,
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label = "aura colour",
    )
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    aura.copy(alpha = 0.32f),
                    aura.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 1.02f),
                radius = size.height * 0.90f,
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

@Composable
fun rememberAudioLevel(scope: () -> FloatArray?, playing: Boolean): androidx.compose.runtime.State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    val currentScope by rememberUpdatedState(scope)
    LaunchedEffect(playing) {
        if (!playing) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last < 33_000_000L) return@withFrameNanos
                last = now
                val data = currentScope() ?: return@withFrameNanos
                var sum = 0.0
                for (v in data) sum += v.toDouble() * v
                val rms = sqrt(sum / data.size)
                val target = (rms * 4.0).toFloat().coerceIn(0f, 1f)
                level.floatValue = level.floatValue * 0.80f + target * 0.20f
            }
        }
    }
    return level
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
    ringAlpha: Float = 1f,
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
                    .border(1.5.dp, color.copy(alpha = 0.85f * ringAlpha), CircleShape),
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
            .pressable(scale = 0.98f, enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(background),
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
    autoFocus: Boolean = false,
    selectAll: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    var field by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = if (selectAll) TextRange(0, value.length) else TextRange(value.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(value) {
        if (field.text != value) {
            field = field.copy(text = value, selection = TextRange(value.length))
        }
    }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

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
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnSurface,
            ),
            cursorBrush = SolidColor(OnSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                if (field.text.isEmpty()) {
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
            .pressable(scale = 0.995f, onClick = onClick)
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
                    .pressable(scale = 0.96f) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(color)
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .semantics {
                        contentDescription = "${color.label} Noise"
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
fun SoundsHeader(
    count: Int,
    onAdd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "My sounds",
            fontFamily = Fraunces,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
            color = OnSurface,
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                text = count.toString().padStart(2, '0'),
                style = Numeric,
                fontSize = 11.5.sp,
                color = LabelMuted,
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .pressable(scale = 0.86f, onClick = onAdd)
                .semantics { contentDescription = "Create sound" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SoundRow(
    sound: SavedSound,
    active: Boolean,
    playing: Boolean,
    highlight: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val orbColor = soundOrbColor(sound.color.dot, sound.deep)
    val dot by animateFloatAsState(if (playing && active) 1f else 0.45f, tween(400), label = "status")
    val border by animateColorAsState(
        when {
            highlight -> orbColor.copy(alpha = 0.95f)
            active -> orbColor.copy(alpha = 0.55f)
            else -> Hairline
        },
        tween(if (highlight) 260 else 450, easing = FastOutSlowInEasing),
        label = "row border",
    )
    val moveActions = buildList {
        onMoveUp?.let { move ->
            add(CustomAccessibilityAction("Move up") { move(); true })
        }
        onMoveDown?.let { move ->
            add(CustomAccessibilityAction("Move down") { move(); true })
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(
                elevation = if (highlight) 12.dp else 0.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .noRippleClickable(onClick = onPlay)
            .semantics {
                if (moveActions.isNotEmpty()) customActions = moveActions
            }
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorOrb(orbColor, 32.dp, ring = active)
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
                            .background(orbColor.copy(alpha = dot)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "${sound.color.label} Noise",
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
                .pressable(scale = 0.86f, onClick = onMenu),
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
    timerText: String?,
    onTimerClick: () -> Unit,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(
        targetValue = color,
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label = "now playing colour",
    )
    val level = rememberAudioLevel(scope, playing)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .pressable(scale = 0.995f, onClick = onOpen)
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
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    val s = 1f + level.value * 0.05f
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    drawCircle(
                        color = accent.copy(alpha = 0.08f + level.value * 0.26f),
                        radius = size.minDimension * 0.48f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ColorOrb(accent, 38.dp, ring = true)
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                )
                if (timerText != null) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .height(20.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(OnSurface.copy(alpha = 0.07f))
                            .noRippleClickable(onClick = onTimerClick)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimerGlyph(OnSurfaceVariant, 10.dp)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = timerText,
                            style = Numeric,
                            fontSize = 10.sp,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }
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
                .pressable(scale = 0.90f, onClick = onToggle)
                .clip(CircleShape)
                .background(accent)
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
fun TimerGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            color = tint.copy(alpha = 0.8f),
            radius = minOf(w, h) * 0.42f,
            style = Stroke(width = 1.1.dp.toPx()),
        )
        drawLine(
            color = tint.copy(alpha = 0.8f),
            start = Offset(w * 0.5f, h * 0.5f),
            end = Offset(w * 0.5f, h * 0.24f),
            strokeWidth = 1.1.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = tint.copy(alpha = 0.8f),
            start = Offset(w * 0.5f, h * 0.5f),
            end = Offset(w * 0.72f, h * 0.58f),
            strokeWidth = 1.1.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
fun EmptySounds(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(scale = 0.995f, onClick = onCreate)
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(24.dp))
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
            text = "Pick a color above, or create your own.",
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
fun BottomBar(onSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Bg, Bg)))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressable(scale = 0.99f, onClick = onSettings)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceHigh)
                .border(1.dp, Hairline, RoundedCornerShape(18.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = OnSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Playback settings",
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = OnSurface,
            )
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
