package com.deepbrown.noise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
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

/**
 * One translucent surface treatment for cards and rows. The aura behind the
 * screen shows through; a dark warm base keeps text readable. No blur.
 */
fun Modifier.glassFill(
    shape: Shape,
    accent: Color? = null,
    strong: Boolean = false,
): Modifier {
    val top = if (accent != null) lerp(GlassTop, accent, 0.10f) else GlassTop
    val bottom = if (accent != null) lerp(GlassBottom, accent, 0.06f) else GlassBottom
    val topAlpha = if (strong) 0.60f else 0.44f
    val bottomAlpha = if (strong) 0.42f else 0.28f
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    top.copy(alpha = topAlpha),
                    bottom.copy(alpha = bottomAlpha),
                ),
            ),
        )
}

fun Modifier.glassPanel(
    shape: Shape,
    accent: Color? = null,
    strong: Boolean = false,
): Modifier = this
    .glassFill(shape, accent, strong)
    .border(1.dp, GlassRim, shape)

fun Modifier.glassChip(shape: Shape): Modifier = this
    .clip(shape)
    .background(GlassChip)
    .border(1.dp, GlassRim.copy(alpha = 0.60f), shape)

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

private fun idleWave(points: Int): FloatArray = FloatArray(points) { i ->
    val x = i.toFloat() / points
    (0.22 * sin(x * 6.28318 * 2.2) + 0.10 * sin(x * 6.28318 * 5.1)).toFloat()
}

/**
 * A heavily smoothed, low-resolution copy of the scope for the soft editor waveform.
 * It moves at 20 fps with a slow attack, so it undulates instead of twitching.
 */
@Composable
fun rememberCalmWave(
    scope: () -> FloatArray?,
    playing: Boolean,
    points: Int = 22,
): State<FloatArray> {
    val wave = remember(points) { mutableStateOf(idleWave(points)) }
    val peak = remember { mutableFloatStateOf(0.02f) }
    val currentScope by rememberUpdatedState(scope)
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last < 50_000_000L) return@withFrameNanos
                last = now
                val live = currentScope() ?: return@withFrameNanos
                if (live.isEmpty()) return@withFrameNanos
                var mx = 0f
                val next = FloatArray(points) { i ->
                    val index = (i.toFloat() / (points - 1) * (live.size - 1))
                        .roundToInt()
                        .coerceIn(0, live.size - 1)
                    val value = live[index]
                    if (abs(value) > mx) mx = abs(value)
                    value
                }
                peak.floatValue = max(peak.floatValue * 0.996f, max(mx, 0.02f))
                val previous = wave.value
                wave.value = FloatArray(points) { i ->
                    previous[i] + 0.10f * (next[i] / peak.floatValue - previous[i])
                }
            }
        }
    }
    return wave
}

/* ---------------------------------------------------------------------- */
/* Surfaces and controls                                                   */
/* ---------------------------------------------------------------------- */

@Composable
fun ColorOrb(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                // A lit sphere: a soft top-left light, a small specular glint,
                // and a quiet shadow at the lower-right edge.
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to lerp(color, Color.White, 0.44f),
                        0.32f to lerp(color, Color.White, 0.07f),
                        0.72f to color,
                        1f to lerp(color, Color.Black, 0.28f),
                        center = Offset(w * 0.30f, h * 0.24f),
                        radius = w * 0.98f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.26f),
                        0.55f to Color.White.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                        center = Offset(w * 0.28f, h * 0.22f),
                        radius = w * 0.24f,
                    ),
                    radius = w * 0.24f,
                    center = Offset(w * 0.28f, h * 0.22f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.20f),
                        center = Offset(w * 0.72f, h * 0.78f),
                        radius = w * 0.78f,
                    ),
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f),
                    ),
                ),
                CircleShape,
            ),
    )
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
            .background(
                Brush.verticalGradient(
                    listOf(lerp(background, Color.White, 0.10f), background),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
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
    selectAllOnFocus: Boolean = false,
    autoFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (field.text != value) {
            field = field.copy(text = value, selection = TextRange(value.length))
        }
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus && focusRequester != null) {
            delay(180)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .glassPanel(RoundedCornerShape(18.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
                .onFocusChanged { focus ->
                    if (focus.isFocused && selectAllOnFocus && field.text.isNotEmpty()) {
                        field = field.copy(selection = TextRange(0, field.text.length))
                    }
                },
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
            .background(Color.White.copy(alpha = 0.065f)),
    )
}

@Composable
fun ColorSelector(
    selected: NoiseColor?,
    onSelect: (NoiseColor) -> Unit,
    activeColor: NoiseColor? = null,
    playing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(94.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoiseColor.entries.forEach { color ->
                val active = selected == color
                val labelColor by animateColorAsState(
                    if (active) OnSurface else OnSurfaceVariant,
                    tween(Motion.StateMs, easing = Motion.EaseOut),
                    label = "orb label",
                )
                val orbSize by animateFloatAsState(
                    targetValue = if (active) 40f else 34f,
                    animationSpec = tween(320, easing = Motion.EaseOut),
                    label = "orb size",
                )
                val shadowAlpha by animateFloatAsState(
                    targetValue = if (activeColor == color) {
                        if (playing) 0.55f else 0.30f
                    } else {
                        0f
                    },
                    animationSpec = tween(500, easing = Motion.EaseOut),
                    label = "orb shadow",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(scale = 0.95f) {
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
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .drawBehind {
                                if (shadowAlpha > 0.01f) {
                                    val cx = size.width / 2f
                                    val cy = size.height * 0.82f
                                    scale(scaleX = 1f, scaleY = 0.34f, pivot = Offset(cx, cy)) {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    color.dot.copy(alpha = shadowAlpha),
                                                    color.dot.copy(alpha = shadowAlpha * 0.24f),
                                                    Color.Transparent,
                                                ),
                                                center = Offset(cx, cy),
                                                radius = size.width * 0.60f,
                                            ),
                                            radius = size.width * 0.60f,
                                            center = Offset(cx, cy),
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ColorOrb(color.dot, orbSize.dp)
                    }
                    Spacer(Modifier.height(7.dp))
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
}

/** The compact colour row used in the editor. The depth surface is the hero. */
@Composable
fun CompactColorSelector(
    selected: NoiseColor,
    onSelect: (NoiseColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoiseColor.entries.forEach { color ->
            val active = selected == color
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pressable(scale = 0.95f) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(color)
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) OnSurface.copy(alpha = 0.055f) else Color.Transparent)
                    .semantics {
                        contentDescription = "${color.label} Noise"
                        this.selected = active
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorOrb(color.dot, 18.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = color.label,
                    fontFamily = DmSans,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 10.5.sp,
                    color = if (active) OnSurface else OnSurfaceVariant,
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
            val thumbCenter = (value * w).coerceIn(3.dp.toPx(), w - 3.dp.toPx())
            val lineH = 4.dp.toPx()

            // Fixed dot grid; the filled part simply covers the dots it passes.
            val dot = 1.3.dp.toPx()
            val step = 14.dp.toPx()
            var x = 5.dp.toPx()
            while (x < w) {
                drawCircle(Track, radius = dot, center = Offset(x, cy))
                x += step
            }

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(lerp(accent, Color.White, 0.22f), accent),
                    startY = cy - lineH / 2f,
                    endY = cy + lineH / 2f,
                ),
                topLeft = Offset(0f, cy - lineH / 2f),
                size = Size((thumbCenter - lineH / 2f).coerceAtLeast(0f), lineH),
                cornerRadius = CornerRadius(lineH / 2f),
            )

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(thumbCenter - 4.dp.toPx(), cy - 11.dp.toPx()),
                size = Size(8.dp.toPx(), 22.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF8EE), Color(0xFFD8D0C2), Color(0xFF9A9183)),
                    startY = cy - 10.dp.toPx(),
                    endY = cy + 10.dp.toPx(),
                ),
                topLeft = Offset(thumbCenter - 3.dp.toPx(), cy - 10.dp.toPx()),
                size = Size(6.dp.toPx(), 20.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 1.dp.toPx(),
                center = Offset(thumbCenter, cy - 6.dp.toPx()),
            )
        }
    }
}

/** The shared rounded surface for a group of parameter rows. */
@Composable
fun ParameterCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(24.dp)),
        content = content,
    )
}

/** One control inside a grouped parameter surface. */
@Composable
fun ParameterRow(
    label: String,
    value: Float,
    description: String,
    accent: Color,
    onChange: (Float) -> Unit,
    startLabel: String? = null,
    endLabel: String? = null,
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
                    .background(
                        Brush.verticalGradient(
                            listOf(OnSurface.copy(alpha = 0.10f), OnSurface.copy(alpha = 0.05f)),
                        ),
                    )
                    .border(1.dp, OnSurface.copy(alpha = 0.06f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        SliderTrack(value = value, onChange = onChange, accent = accent, contentDescription = label)
        if (startLabel != null && endLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel, fontFamily = DmSans, fontSize = 10.sp, color = LabelMuted)
                Text(endLabel, fontFamily = DmSans, fontSize = 10.sp, color = LabelMuted)
            }
        }
        Text(
            text = description,
            fontFamily = DmSans,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = LabelMuted,
        )
    }
}

/** A horizontally scrolling row of starting points above the editor controls. */
@Composable
fun PresetRow(
    accent: Color,
    onPreset: (Preset) -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Start from",
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = LabelMuted,
            modifier = Modifier.padding(end = 2.dp),
        )
        Presets.forEach { preset ->
            PresetChip(label = preset.name, dot = preset.color.dot) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPreset(preset)
            }
        }
        PresetChip(label = "Surprise", dot = accent) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRandom()
        }
    }
}

@Composable
private fun PresetChip(label: String, dot: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .pressable(scale = 0.94f, onClick = onClick)
            .glassChip(RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = OnSurface,
        )
    }
}

@Composable
fun TuneGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.2.dp.toPx()
        drawLine(tint, Offset(w * 0.12f, h * 0.28f), Offset(w * 0.62f, h * 0.28f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.28f), Offset(w * 0.90f, h * 0.28f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(tint, radius = w * 0.085f, center = Offset(w * 0.70f, h * 0.28f), style = Stroke(stroke))
        drawLine(tint, Offset(w * 0.12f, h * 0.72f), Offset(w * 0.24f, h * 0.72f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, Offset(w * 0.40f, h * 0.72f), Offset(w * 0.90f, h * 0.72f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(tint, radius = w * 0.085f, center = Offset(w * 0.32f, h * 0.72f), style = Stroke(stroke))
    }
}

@Composable
fun HomeHeader(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Colors of Noise",
            fontFamily = FrauncesDisplay,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.4).sp,
            color = OnSurface,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .pressable(scale = 0.90f, onClick = onSettings)
                .glassChip(CircleShape)
                .semantics { contentDescription = "Playback settings" },
            contentAlignment = Alignment.Center,
        ) {
            TuneGlyph(tint = OnSurfaceVariant, size = 18.dp)
        }
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "My sounds",
            fontFamily = Fraunces,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
            color = OnSurface,
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                text = count.toString().padStart(2, '0'),
                style = Numeric,
                fontSize = 11.sp,
                color = LabelMuted,
            )
            Spacer(Modifier.width(4.dp))
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
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
fun SoundRow(
    sound: SavedSound,
    active: Boolean,
    playing: Boolean,
    shape: RoundedCornerShape,
    first: Boolean,
    last: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val orbColor = soundOrbColor(sound.color.dot, sound.deep)
    val dot by animateFloatAsState(if (playing && active) 1f else 0.45f, tween(400), label = "status")
    val texture = when {
        sound.rain >= 0.35f -> "rain"
        sound.plink >= 0.45f -> "plinks"
        sound.wind >= 0.25f -> "wind"
        sound.fan >= 0.35f -> "fan"
        sound.air >= 0.28f -> "air"
        sound.deep >= 0.70f -> "deep"
        else -> null
    }
    val metaText = buildString {
        append(sound.color.label)
        append(" Noise")
        if (texture != null) {
            append(" · ")
            append(texture)
        }
        if (active && playing) append(" · playing")
    }
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
            .height(64.dp)
            .glassFill(shape, accent = if (active) orbColor else null)
            .then(
                if (active) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(orbColor.copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()
                val stroke = 1.dp.toPx()
                val inset = stroke / 2f
                val rim = Color.White.copy(alpha = 0.13f)
                val radius = 22.dp.toPx()
                drawLine(rim, Offset(inset, 0f), Offset(inset, size.height), stroke)
                drawLine(rim, Offset(size.width - inset, 0f), Offset(size.width - inset, size.height), stroke)
                if (first) {
                    drawArc(
                        color = rim,
                        startAngle = 180f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(stroke),
                    )
                    drawArc(
                        color = rim,
                        startAngle = 270f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(size.width - radius * 2f - inset, inset),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(stroke),
                    )
                    drawLine(rim, Offset(radius, inset), Offset(size.width - radius, inset), stroke)
                }
                if (last) {
                    drawArc(
                        color = rim,
                        startAngle = 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(inset, size.height - radius * 2f - inset),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(stroke),
                    )
                    drawArc(
                        color = rim,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(size.width - radius * 2f - inset, size.height - radius * 2f - inset),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(stroke),
                    )
                    drawLine(rim, Offset(radius, size.height - inset), Offset(size.width - radius, size.height - inset), stroke)
                }
                if (!last) {
                    drawLine(
                        Color.White.copy(alpha = 0.075f),
                        Offset(48.dp.toPx(), size.height - inset),
                        Offset(size.width - 12.dp.toPx(), size.height - inset),
                        stroke,
                    )
                }
            }
            .pressable(scale = 0.998f, onClick = onPlay)
            .semantics {
                this.selected = active
                if (moveActions.isNotEmpty()) customActions = moveActions
            }
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorOrb(orbColor, 29.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sound.name,
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp,
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
                    text = metaText,
                    fontFamily = DmSans,
                    fontSize = 11.5.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .pressable(scale = 0.86f, onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "${sound.name} options",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
fun SavedToast(name: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brass.copy(alpha = 0.09f))
            .border(1.dp, Brass.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Brass,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "Saved",
            fontFamily = DmSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Brass,
        )
        if (name.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "· $name",
                fontFamily = DmSans,
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp),
            )
        }
    }
}

@Composable
fun PlayerChip(
    text: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = OnSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    val click = if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .height(20.dp)
            .glassChip(RoundedCornerShape(7.dp))
            .then(click)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = Numeric,
            fontSize = 10.sp,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * The one player card used above the home list and in the editor.
 * The orb is the only meter: it breathes with the low end while the card's
 * light field drifts around it. There is no second waveform to compete with it.
 */
@Composable
fun PlayerCard(
    title: String,
    subtitle: String,
    accent: Color,
    orbColor: Color,
    playing: Boolean,
    visuals: State<AudioVisuals>,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    statusChip: (@Composable () -> Unit)? = null,
    playDescription: String = "Play",
    pauseDescription: String = "Pause",
) {
    val cardAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label = "player accent",
    )
    val orbTint by animateColorAsState(
        targetValue = orbColor,
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label = "player orb",
    )
    val pauseFade by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(650, easing = Motion.EaseOut),
        label = "card energy",
    )
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .pressable(scale = 0.995f, onClick = onOpen)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .glassFill(shape, accent = cardAccent, strong = true)
            .drawBehind {
                drawPlayerAura(
                    accent = cardAccent,
                    energy = (visuals.value.level * pauseFade).coerceIn(0f, 1f),
                    low = (visuals.value.low * pauseFade).coerceIn(0f, 1f),
                    air = (visuals.value.air * pauseFade).coerceIn(0f, 1f),
                    phase = visuals.value.phase,
                )
            }
            .border(1.dp, cardAccent.copy(alpha = 0.26f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer {
                        val s = 1f + visuals.value.level * pauseFade * 0.16f
                        scaleX = s
                        scaleY = s
                    }
                    .drawBehind {
                        val min = size.minDimension
                        val e = (visuals.value.level * pauseFade).coerceIn(0f, 1f)
                        val halo = min * (0.58f + e * 0.28f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    cardAccent.copy(alpha = 0.24f + e * 0.30f),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = halo,
                            ),
                            radius = halo,
                        )
                        drawCircle(
                            color = cardAccent.copy(alpha = 0.16f + e * 0.18f),
                            radius = min * (0.54f + e * 0.04f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                ColorOrb(
                    color = orbTint,
                    size = 42.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    fontFamily = Fraunces,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        fontFamily = DmSans,
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (statusChip != null) {
                        Spacer(Modifier.width(7.dp))
                        statusChip()
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .pressable(scale = 0.90f, onClick = onToggle)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(lerp(cardAccent, Color.White, 0.16f), cardAccent),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .semantics {
                        contentDescription = if (playing) pauseDescription else playDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(playing, animationSpec = tween(180), label = "play state") { isPlaying ->
                    PlayPauseIcon(isPlaying, Ink, 18.dp)
                }
            }
        }
    }
}

/** Two slow colour fields, behind the card content. */
private fun DrawScope.drawPlayerAura(
    accent: Color,
    energy: Float,
    low: Float,
    air: Float,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    val e = energy.coerceIn(0f, 1f)
    val drift = sin(phase * 2.0 * PI).toFloat()
    val drift2 = sin(phase * 2.0 * PI + 1.7).toFloat()

    val primaryX = w * (0.16f + drift * 0.035f)
    val primaryY = h * (0.58f + drift2 * 0.06f)
    val primaryRadius = h * (0.98f + e * 0.30f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to accent.copy(alpha = 0.22f + e * 0.22f + low * 0.06f),
            0.48f to accent.copy(alpha = 0.08f + e * 0.09f),
            1f to Color.Transparent,
            center = Offset(primaryX, primaryY),
            radius = primaryRadius,
        ),
        radius = primaryRadius,
        center = Offset(primaryX, primaryY),
    )

    val secondaryX = w * (0.84f - drift * 0.03f)
    val secondaryY = h * (0.28f + drift2 * 0.05f)
    val secondaryRadius = h * (0.82f + e * 0.22f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to lerp(accent, Color.White, 0.20f).copy(alpha = 0.10f + e * 0.10f + air * 0.06f),
            1f to Color.Transparent,
            center = Offset(secondaryX, secondaryY),
            radius = secondaryRadius,
        ),
        radius = secondaryRadius,
        center = Offset(secondaryX, secondaryY),
    )

    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to accent.copy(alpha = 0.04f + e * 0.05f),
        ),
    )
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
fun EmptySounds(
    onCreate: () -> Unit,
    onPreset: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(NeutralAura.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.46f),
                    radius = size.minDimension * 0.66f,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = size.minDimension * 0.66f,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 52.dp),
        ) {
            Text(
                text = "Nothing saved yet",
                fontFamily = Fraunces,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.3).sp,
                color = OnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick a color to start listening, or shape a sound from a preset.",
                fontFamily = DmSans,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .pressable(scale = 0.97f, onClick = onCreate)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFF8EA), Color(0xFFE0D4C2)),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayPauseIcon(playing = false, tint = Ink, size = 12.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Create a sound",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val ringColor = OnSurface.copy(alpha = 0.027f)
                    val cy = size.height * 0.5f
                    drawOval(
                        color = ringColor,
                        topLeft = Offset(size.width * 0.02f, cy - 46.dp.toPx()),
                        size = Size(size.width * 0.96f, 92.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawOval(
                        color = ringColor.copy(alpha = 0.76f),
                        topLeft = Offset(size.width * 0.15f, cy - 32.dp.toPx()),
                        size = Size(size.width * 0.70f, 64.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Presets.take(3).forEach { preset ->
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .pressable(scale = 0.95f) { onPreset(preset) }
                                .glassChip(RoundedCornerShape(14.dp))
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(preset.color.dot),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = preset.name,
                                fontFamily = DmSans,
                                fontSize = 10.5.sp,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Made on this phone. No internet, no loops.",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            fontFamily = DmSans,
            fontSize = 10.sp,
            color = OnSurface.copy(alpha = 0.32f),
        )
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
    var confirming by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (!confirming) return@LaunchedEffect
        delay(120)
        closing = true
        delay(220)
        onConfirm()
    }
    Dialog(
        onDismissRequest = { if (!confirming) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = !closing,
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .glassPanel(RoundedCornerShape(28.dp), strong = true)
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
                            .glassChip(RoundedCornerShape(16.dp))
                            .clickable { if (!confirming) onDismiss() },
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
                            .clickable { if (!confirming) confirming = true },
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
}
