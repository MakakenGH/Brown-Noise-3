package com.deepbrown.noise

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    initial: Draft,
    editing: Boolean,
    previewing: Boolean,
    showPreviewHint: Boolean,
    scope: () -> FloatArray?,
    onDraftChanged: (Draft) -> Unit,
    onBack: () -> Unit,
    onSave: (Draft) -> Unit,
    onTogglePreview: (Draft) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var showNameSheet by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val accent by androidx.compose.animation.animateColorAsState(
        targetValue = draft.color.dot,
        animationSpec = tween(1_200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "editor accent",
    )

    fun update(next: Draft) {
        draft = next
        onDraftChanged(next)
    }

    fun requestBack() {
        if (draft != initial) showDiscard = true else onBack()
    }

    BackHandler { requestBack() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        NoiseAura(
            color = draft.color.dot,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pressable(scale = 0.90f) { requestBack() }
                        .clip(CircleShape)
                        .background(SurfaceHigh)
                        .border(1.dp, Hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = OnSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = if (editing) "Edit sound" else "New sound",
                    modifier = Modifier.weight(1f),
                    fontFamily = Fraunces,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = OnSurface,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(14.dp, CircleShape)
                        .pressable(scale = 0.90f) {
                            if (draft.name.isBlank()) showNameSheet = true else onSave(draft)
                        }
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Ink, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DeepnessField(
                    draft = draft,
                    previewing = previewing,
                    scope = scope,
                    onChange = { update(draft.copy(deep = it)) },
                )
                ColorSelector(
                    selected = draft.color,
                    onSelect = { update(draft.copy(color = it)) },
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Surface)
                        .border(1.dp, Hairline, RoundedCornerShape(24.dp)),
                ) {
                    ParameterRow(
                        label = "Rumble",
                        value = draft.rumble,
                        description = "Extra weight below 90 Hz",
                        accent = draft.color.dot,
                        onChange = { update(draft.copy(rumble = it)) },
                    )
                    HairlineDivider(startPadding = 18.dp)
                    ParameterRow(
                        label = "Width",
                        value = draft.width,
                        description = "From centred to enveloping",
                        accent = draft.color.dot,
                        onChange = { update(draft.copy(width = it)) },
                    )
                    HairlineDivider(startPadding = 18.dp)
                    ParameterRow(
                        label = "Swell",
                        value = draft.swell,
                        description = "Slow, never-repeating breathing",
                        accent = draft.color.dot,
                        onChange = { update(draft.copy(swell = it)) },
                    )
                }
            }
        }

        PlayerCard(
            title = draft.name.ifBlank { "Untitled sound" },
            subtitle = "${draft.color.label} Noise",
            accent = draft.color.dot,
            orbColor = soundOrbColor(draft.color.dot, draft.deep),
            playing = previewing,
            scope = scope,
            statusChip = {
                PlayerChip(
                    text = if (previewing) "Previewing" else "Preview",
                    leading = {
                        PlayPauseIcon(
                            playing = previewing,
                            tint = OnSurfaceVariant,
                            size = 9.dp,
                        )
                    },
                )
            },
            onOpen = { onTogglePreview(draft) },
            onToggle = { onTogglePreview(draft) },
            playDescription = "Play preview",
            pauseDescription = "Pause preview",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        AnimatedVisibility(
            visible = showPreviewHint && !previewing,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 100.dp),
            enter = fadeIn(tween(260, delayMillis = 320)) +
                slideInVertically(tween(300, delayMillis = 320)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 4 },
        ) {
            PreviewHint(accent = accent)
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this sound will be lost.",
            confirmLabel = "Discard",
            onConfirm = {
                showDiscard = false
                onBack()
            },
            onDismiss = { showDiscard = false },
        )
    }

    if (showNameSheet) {
        NameSheet(
            title = "Name your sound",
            initial = draft.name,
            placeholder = "e.g. Deep velvet",
            confirmLabel = "Save sound",
            accent = accent,
            onSave = { name ->
                val named = draft.copy(name = name)
                draft = named
                showNameSheet = false
                onSave(named)
            },
            onDismiss = { showNameSheet = false },
        )
    }
}

/** Points down at the play button; shown until preview has been used once. */
@Composable
private fun PreviewHint(accent: Color) {
    val pulse by rememberInfiniteTransition(label = "preview hint").animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "preview hint pulse",
    )
    val shape = remember { HintShape() }
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                transformOrigin = TransformOrigin(
                    1f - 36.dp.toPx() / size.width,
                    1f,
                )
            }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.25f),
                spotColor = accent.copy(alpha = 0.25f),
            )
            .background(SurfaceHigh, shape)
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayPauseIcon(playing = false, tint = accent, size = 10.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Press play to preview",
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = OnSurface,
            )
        }
    }
}

/** One path for the body and tail, so the fill and border never show a seam. */
private class HintShape(
    private val corner: Dp = 12.dp,
    private val tailWidth: Dp = 12.dp,
    private val tailHeight: Dp = 6.dp,
    private val tailCenterFromEnd: Dp = 36.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cornerPx = with(density) { corner.toPx() }
        val tailWidthPx = with(density) { tailWidth.toPx() }
        val tailHeightPx = with(density) { tailHeight.toPx() }
        val tailCenter = size.width - with(density) { tailCenterFromEnd.toPx() }
        val bodyBottom = size.height - tailHeightPx
        val path = Path().apply {
            moveTo(cornerPx, 0f)
            lineTo(size.width - cornerPx, 0f)
            quadraticTo(size.width, 0f, size.width, cornerPx)
            lineTo(size.width, bodyBottom - cornerPx)
            quadraticTo(size.width, bodyBottom, size.width - cornerPx, bodyBottom)
            lineTo(tailCenter + tailWidthPx / 2f, bodyBottom)
            lineTo(tailCenter, size.height)
            lineTo(tailCenter - tailWidthPx / 2f, bodyBottom)
            lineTo(cornerPx, bodyBottom)
            quadraticTo(0f, bodyBottom, 0f, bodyBottom - cornerPx)
            lineTo(0f, cornerPx)
            quadraticTo(0f, 0f, cornerPx, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun DeepnessField(
    draft: Draft,
    previewing: Boolean,
    scope: () -> FloatArray?,
    onChange: (Float) -> Unit,
) {
    val currentDraft by rememberUpdatedState(draft)
    val currentOnChange by rememberUpdatedState(onChange)
    val feedback = LocalHapticFeedback.current
    var lastStep by remember { mutableStateOf((draft.deep * 100).roundToInt()) }
    var touched by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val accent = draft.color.dot
    val handleY = draft.deep
    val level = rememberAudioLevel(scope, previewing)
    val fade = animateFloatAsState(
        targetValue = if (previewing) 1f else 0f,
        animationSpec = tween(900),
        label = "deepness energy",
    )
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(268.dp)
            .shadow(
                elevation = 20.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1238), Color(0xFF0D071E))))
            .border(1.dp, Hairline, shape)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        touched = true
                        dragging = true
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val next = (currentDraft.deep + dy / size.height.toFloat()).coerceIn(0f, 1f)
                        val step = (next * 100).roundToInt()
                        if (step / 10 != lastStep / 10) {
                            lastStep = step
                            feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentOnChange(next)
                    },
                )
            }
            .semantics {
                contentDescription = "Deepness"
                progressBarRangeInfo = ProgressBarRangeInfo(draft.deep, 0f..1f)
                setProgress {
                    currentOnChange(it.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val energy = (level.value * fade.value).coerceIn(0f, 1f)
                    val accentRadius = w * (1.02f + energy * 0.08f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.24f + energy * 0.12f),
                                Color.Transparent,
                            ),
                            center = Offset(w * 0.88f, 0f),
                            radius = accentRadius,
                        ),
                        radius = accentRadius,
                        center = Offset(w * 0.88f, 0f),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.60f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.18f),
                        ),
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.68f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.10f),
                        ),
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(28.dp.toPx(), 1.dp.toPx()),
                        end = Offset(w - 28.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        )

        SoftWaveform(
            color = accent,
            playing = previewing,
            scope = scope,
            level = level,
            fade = fade,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp),
        )

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val top = 30.dp.toPx()
            val bottom = h - 100.dp.toPx()

            val ticks = 16
            for (i in 0 until ticks) {
                val y = top + (bottom - top) * i / (ticks - 1)
                val major = i % 4 == 0
                drawLine(
                    color = OnSurfaceVariant.copy(alpha = if (major) 0.45f else 0.24f),
                    start = Offset(w - if (major) 34.dp.toPx() else 26.dp.toPx(), y),
                    end = Offset(w - 18.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val y = top + (bottom - top) * handleY
            if (dragging) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(w - 29.dp.toPx(), y),
                        radius = 16.dp.toPx(),
                    ),
                    radius = 16.dp.toPx(),
                    center = Offset(w - 29.dp.toPx(), y),
                )
            }
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(w - 45.dp.toPx(), y - 4.dp.toPx()),
                size = Size(32.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF8EE), Color(0xFFD8D0C2), Color(0xFF9A9183)),
                    startY = y - 3.dp.toPx(),
                    endY = y + 3.dp.toPx(),
                ),
                topLeft = Offset(w - 44.dp.toPx(), y - 3.dp.toPx()),
                size = Size(30.dp.toPx(), 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFDF7), Color(0xFFD6CEC0), Color(0xFF8E8578)),
                    center = Offset(w - 18.dp.toPx(), y - 1.dp.toPx()),
                    radius = 5.dp.toPx(),
                ),
                radius = 5.dp.toPx(),
                center = Offset(w - 17.dp.toPx(), y),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .width(124.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(OnSurface.copy(alpha = 0.10f), OnSurface.copy(alpha = 0.05f)),
                    ),
                )
                .border(1.dp, OnSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = (draft.deep * 100).roundToInt().toString(),
                fontFamily = FrauncesDisplay,
                fontSize = 50.sp,
                lineHeight = 52.sp,
                color = OnSurface,
            )
            Text(
                text = "Deepness",
                fontFamily = DmSans,
                fontSize = 11.5.sp,
                color = OnSurfaceVariant,
            )
            Text(
                text = "${((Sound.cutoffHz(draft.deep) / 5).roundToInt() * 5)} Hz",
                style = Numeric,
                fontSize = 10.sp,
                color = LabelMuted,
            )
        }

        if (!touched) {
            Text(
                text = "Drag to deepen",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 96.dp),
                fontFamily = DmSans,
                fontSize = 10.5.sp,
                color = OnSurfaceVariant,
            )
        }
    }
}

/** The blurred waveform gets the space it needs here, instead of inside the player card. */
@Composable
private fun SoftWaveform(
    color: Color,
    playing: Boolean,
    scope: () -> FloatArray?,
    level: State<Float>,
    fade: State<Float>,
    modifier: Modifier = Modifier,
) {
    val wave = rememberCalmWave(scope, playing, points = 34)
    val alpha by animateFloatAsState(
        targetValue = if (playing) 1f else 0.55f,
        animationSpec = tween(700),
        label = "soft wave alpha",
    )
    Canvas(modifier) {
        val values = wave.value
        val w = size.width
        val h = size.height
        val energy = (level.value * fade.value).coerceIn(0f, 1f)
        val mid = h * 0.56f
        val amp = h * (0.17f + energy * 0.07f)
        val dx = w / (values.size - 1)
        val path = Path()
        var previousX = 0f
        var previousY = mid + values[0] * amp
        path.moveTo(previousX, previousY)
        for (i in 1 until values.size) {
            val x = i * dx
            val y = mid + values[i] * amp
            path.quadraticTo(previousX, previousY, (previousX + x) / 2f, (previousY + y) / 2f)
            previousX = x
            previousY = y
        }
        path.lineTo(previousX, previousY)
        drawPath(
            path = path,
            color = color.copy(alpha = 0.05f * alpha),
            style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.08f * alpha),
            style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.11f * alpha),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.30f * alpha),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
