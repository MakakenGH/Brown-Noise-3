package com.deepbrown.noise

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EditorScreen(
    initial: Draft,
    editing: Boolean,
    previewing: Boolean,
    showPreviewHint: Boolean,
    scope: () -> FloatArray?,
    bands: () -> FloatArray?,
    onDraftChanged: (Draft) -> Unit,
    onBack: () -> Unit,
    onSave: (Draft) -> Unit,
    onTogglePreview: (Draft) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var showNameSheet by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf(EditorGroup.Body) }
    val tier = rememberVisualTier()
    val visuals = rememberAudioVisuals(scope, bands, previewing, tier)
    val wave = rememberCalmWave(scope, previewing, points = 30)
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
        LivingAura(
            color = accent,
            visuals = visuals,
            tier = tier,
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
                        .glassChip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = OnSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (editing) "Edit sound" else "New sound",
                        fontFamily = Fraunces,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = OnSurface,
                    )
                }
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .pressable(scale = 0.94f) {
                            if (draft.name.isBlank()) showNameSheet = true else onSave(draft)
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFF8EA), Color(0xFFDDD1BF)),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Save",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Ink,
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 6.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PresetRow(
                    accent = draft.color.dot,
                    onPreset = { preset -> update(preset.toDraft(draft.name)) },
                    onRandom = { update(randomDraft().copy(name = draft.name)) },
                )
                DeepnessField(
                    draft = draft,
                    visuals = visuals,
                    wave = wave,
                    tier = tier,
                    onChange = { update(draft.copy(deep = it)) },
                )
                CompactColorSelector(
                    selected = draft.color,
                    onSelect = { update(draft.copy(color = it)) },
                )
                EditorTabs(
                    selected = group,
                    onSelect = { group = it },
                )
                AnimatedContent(
                    targetState = group,
                    modifier = Modifier.animateContentSize(),
                    transitionSpec = {
                        fadeIn(tween(170, easing = Motion.EaseOut)) togetherWith
                            fadeOut(tween(90))
                    },
                    label = "editor group",
                ) { active ->
                    ParameterGroup(
                        group = active,
                        draft = draft,
                        onChange = { update(it) },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        PlayerCard(
            title = draft.name.ifBlank { "Untitled sound" },
            subtitle = "${draft.color.label} Noise",
            accent = draft.color.dot,
            orbColor = soundOrbColor(draft.color.dot, draft.deep),
            playing = previewing,
            visuals = visuals,
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

private enum class EditorGroup(val label: String) {
    Body("Body"),
    Air("Air"),
    Water("Water"),
    Motion("Motion"),
}

@Composable
private fun EditorTabs(
    selected: EditorGroup,
    onSelect: (EditorGroup) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .glassChip(RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EditorGroup.entries.forEach { group ->
            val active = selected == group
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pressable(scale = 0.96f) {
                        if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(group)
                    }
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) OnSurface.copy(alpha = 0.94f) else Color.Transparent)
                    .semantics { this.selected = active },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = group.label,
                    fontFamily = DmSans,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (active) Ink else OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParameterGroup(
    group: EditorGroup,
    draft: Draft,
    onChange: (Draft) -> Unit,
) {
    ParameterCard {
        when (group) {
            EditorGroup.Body -> {
                ParameterRow(
                    label = "Rumble",
                    value = draft.rumble,
                    description = "Extra weight below 90 Hz",
                    accent = draft.color.dot,
                    startLabel = "Light",
                    endLabel = "Heavy",
                    onChange = { onChange(draft.copy(rumble = it)) },
                )
                HairlineDivider(startPadding = 18.dp)
                ParameterRow(
                    label = "Width",
                    value = draft.width,
                    description = "From centred to enveloping",
                    accent = draft.color.dot,
                    startLabel = "Centred",
                    endLabel = "Enveloping",
                    onChange = { onChange(draft.copy(width = it)) },
                )
            }
            EditorGroup.Air -> {
                ParameterRow(
                    label = "Air",
                    value = draft.air,
                    description = "Top-end detail above the body",
                    accent = draft.color.dot,
                    startLabel = "Hushed",
                    endLabel = "Airy",
                    onChange = { onChange(draft.copy(air = it)) },
                )
                HairlineDivider(startPadding = 18.dp)
                ParameterRow(
                    label = "Wind",
                    value = draft.wind,
                    description = "Moving low-mid airflow with slow gusts",
                    accent = draft.color.dot,
                    startLabel = "Calm",
                    endLabel = "Gusty",
                    onChange = { onChange(draft.copy(wind = it)) },
                )
                HairlineDivider(startPadding = 18.dp)
                ParameterRow(
                    label = "Fan",
                    value = draft.fan,
                    description = "Steady low-mid airflow with slow rotation",
                    accent = draft.color.dot,
                    startLabel = "Still",
                    endLabel = "Rotating",
                    onChange = { onChange(draft.copy(fan = it)) },
                )
            }
            EditorGroup.Water -> {
                ParameterRow(
                    label = "Rain",
                    value = draft.rain,
                    description = "Continuous wash — light drizzle to downpour",
                    accent = draft.color.dot,
                    startLabel = "Light",
                    endLabel = "Heavy",
                    onChange = { onChange(draft.copy(rain = it)) },
                )
                HairlineDivider(startPadding = 18.dp)
                ParameterRow(
                    label = "Drops",
                    value = draft.drops,
                    description = "Close droplets over the bed",
                    accent = draft.color.dot,
                    startLabel = "None",
                    endLabel = "Many",
                    onChange = { onChange(draft.copy(drops = it)) },
                )
                HairlineDivider(startPadding = 18.dp)
                ParameterRow(
                    label = "Plink",
                    value = draft.plink,
                    description = "Occasional rising water bubbles",
                    accent = draft.color.dot,
                    startLabel = "None",
                    endLabel = "Often",
                    onChange = { onChange(draft.copy(plink = it)) },
                )
            }
            EditorGroup.Motion -> {
                ParameterRow(
                    label = "Swell",
                    value = draft.swell,
                    description = "Slow breathing above the deepness",
                    accent = draft.color.dot,
                    startLabel = "Still",
                    endLabel = "Breathing",
                    onChange = { onChange(draft.copy(swell = it)) },
                )
                HairlineDivider(startPadding = 18.dp)
                ParameterRow(
                    label = "Drift",
                    value = draft.drift,
                    description = "Still to drifting — slow, organic movement",
                    accent = draft.color.dot,
                    startLabel = "Still",
                    endLabel = "Drifting",
                    onChange = { onChange(draft.copy(drift = it)) },
                )
            }
        }
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
            .glassFill(shape, accent = accent, strong = true)
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
    visuals: State<AudioVisuals>,
    wave: State<FloatArray>,
    tier: VisualTier,
    onChange: (Float) -> Unit,
) {
    val currentDraft by rememberUpdatedState(draft)
    val currentOnChange by rememberUpdatedState(onChange)
    val feedback = LocalHapticFeedback.current
    var lastStep by remember { mutableStateOf((draft.deep * 100).roundToInt()) }
    var touched by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val accent = draft.color.dot
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .shadow(
                elevation = 20.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF19152C), Color(0xFF0C0818))))
            .border(1.dp, GlassRim, shape)
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
        Canvas(Modifier.fillMaxSize()) {
            drawDepthScene(
                accent = accent,
                deep = draft.deep,
                visuals = visuals.value,
                wave = wave.value,
                dragging = dragging,
                tier = tier,
            )
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp),
        ) {
            Text(
                text = (draft.deep * 100).roundToInt().toString(),
                fontFamily = FrauncesDisplay,
                fontSize = 50.sp,
                lineHeight = 50.sp,
                letterSpacing = (-1.5).sp,
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
                    .padding(start = 18.dp, bottom = 12.dp),
                fontFamily = DmSans,
                fontSize = 10.5.sp,
                color = OnSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private fun DrawScope.drawDepthScene(
    accent: Color,
    deep: Float,
    visuals: AudioVisuals,
    wave: FloatArray,
    dragging: Boolean,
    tier: VisualTier,
) {
    val w = size.width
    val h = size.height
    val top = 84.dp.toPx()
    val bottom = h - 38.dp.toPx()
    val lineY = top + (bottom - top) * deep.coerceIn(0f, 1f)
    val phase = visuals.phase * 2.0 * PI
    val energy = visuals.level.coerceIn(0f, 1f)

    val ambientRadius = w * (0.92f + energy * 0.08f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.22f + energy * 0.10f),
                accent.copy(alpha = 0.055f),
                Color.Transparent,
            ),
            center = Offset(w * 0.86f, 0f),
            radius = ambientRadius,
        ),
        center = Offset(w * 0.86f, 0f),
        radius = ambientRadius,
    )

    // Denser strata below the surface; the air detail lives above it as dust.
    val strata = if (tier == VisualTier.Lite) 4 else 6
    for (i in 0 until strata) {
        val p = (i + 1) / (strata + 1f)
        val sy = lineY + (h - lineY - 14.dp.toPx()) * p
        val amplitude = (1.4f + p * 2.0f + energy * 3.8f).dp.toPx()
        val path = Path()
        var x = 0f
        while (x <= w) {
            val y = sy +
                sin(x / w * 5.4f * PI.toFloat() + phase.toFloat() * 0.6f + i * 1.7f) * amplitude +
                sin(x / w * 11.2f * PI.toFloat() - phase.toFloat() * 0.4f + i) * amplitude * 0.34f
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 8.dp.toPx()
        }
        drawPath(
            path = path,
            color = accent.copy(alpha = 0.05f + p * 0.10f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    val surface = Path()
    val points = wave.size.coerceAtLeast(2)
    val amplitude = (4.0f + visuals.low * 10.0f + energy * 6.0f).dp.toPx()
    for (i in 0 until points) {
        val x = w * i / (points - 1f)
        val value = (wave[i] * 2.2f).coerceIn(-1f, 1f)
        val y = lineY + value * amplitude
        if (i == 0) surface.moveTo(x, y) else surface.lineTo(x, y)
    }
    drawPath(
        path = surface,
        color = accent.copy(alpha = 0.18f + energy * 0.12f),
        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        path = surface,
        color = accent.copy(alpha = 0.95f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )

    // Air detail is sparse on purpose. It should read as dust, not an EQ.
    val dust = if (tier == VisualTier.Full && energy > 0.06f) 9 else 0
    for (i in 0 until dust) {
        val x = (i * 43.dp.toPx() + (visuals.phase * 300.dp.toPx())) % w
        val y = lineY - (16.dp.toPx() + (i * 17.dp.toPx()) % ((lineY - top).coerceAtLeast(18.dp.toPx())))
        drawCircle(
            color = accent.copy(alpha = 0.12f + visuals.air * 0.24f),
            radius = (0.7f + (i % 3) * 0.3f).dp.toPx(),
            center = Offset(x, y),
        )
    }

    // A warm physical grip rather than a standard slider thumb.
    val knobW = 30.dp.toPx()
    val knobH = 8.dp.toPx()
    val knobX = w - 14.dp.toPx() - knobW
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.30f),
        topLeft = Offset(knobX, lineY - knobH / 2f + 1.dp.toPx()),
        size = Size(knobW, knobH),
        cornerRadius = CornerRadius(knobH / 2f),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF8EA), Color(0xFFC9BDA9)),
            startY = lineY - knobH / 2f,
            endY = lineY + knobH / 2f,
        ),
        topLeft = Offset(knobX, lineY - knobH / 2f),
        size = Size(knobW, knobH),
        cornerRadius = CornerRadius(knobH / 2f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = 0.8.dp.toPx(),
        center = Offset(knobX + knobW * 0.5f, lineY - 1.2.dp.toPx()),
    )

    if (dragging) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(knobX + knobW / 2f, lineY),
                radius = 18.dp.toPx(),
            ),
            center = Offset(knobX + knobW / 2f, lineY),
            radius = 18.dp.toPx(),
        )
    }

    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.18f),
        ),
        topLeft = Offset(0f, lineY),
        size = Size(w, h - lineY),
    )
}