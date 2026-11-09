package com.deepbrown.noise

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    initial: Draft,
    editing: Boolean,
    previewing: Boolean,
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
    val audioLevel = rememberAudioLevel(scope, previewing)

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

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(76.dp)
                .pressable(scale = 0.995f) { onTogglePreview(draft) }
                .shadow(
                    elevation = 22.dp,
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
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        val s = 1f + audioLevel.value * 0.05f
                        scaleX = s
                        scaleY = s
                    }
                    .drawBehind {
                        drawCircle(
                            color = accent.copy(alpha = 0.08f + audioLevel.value * 0.24f),
                            radius = size.minDimension * 0.48f,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                ColorOrb(soundOrbColor(draft.color.dot, draft.deep), 34.dp, ring = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .offset(y = (-1.5).dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (previewing) "Previewing" else "Preview",
                    style = TextStyle(
                        fontFamily = DmSans,
                        fontSize = 10.5.sp,
                        lineHeight = 12.sp,
                        color = LabelMuted,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
                Text(
                    text = draft.name.ifBlank { "Untitled sound" },
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        color = OnSurface,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LiveWaveform(
                color = accent,
                playing = previewing,
                scope = scope,
                modifier = Modifier
                    .width(42.dp)
                    .height(18.dp)
                    .padding(end = 10.dp),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .semantics { contentDescription = if (previewing) "Pause preview" else "Play preview" },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(previewing, animationSpec = tween(180), label = "preview state") { playing ->
                    PlayPauseIcon(playing, Ink, 19.dp)
                }
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this sound will be lost.",
            confirmLabel = "Discard",
            onConfirm = onBack,
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

    Box(
        Modifier
            .fillMaxWidth()
            .height(268.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1238), Color(0xFF0D071E))))
            .border(1.dp, Hairline, RoundedCornerShape(32.dp))
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(size.width * 0.88f, 0f),
                            radius = size.width * 1.05f,
                        ),
                    )
                },
        )

        LiveWaveform(
            color = accent,
            playing = previewing,
            scope = scope,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    color = accent.copy(alpha = 0.22f),
                    radius = 14.dp.toPx(),
                    center = Offset(w - 29.dp.toPx(), y),
                )
            }
            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(w - 44.dp.toPx(), y - 1.5.dp.toPx()),
                size = Size(30.dp.toPx(), 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
            drawCircle(
                color = OnSurface,
                radius = 2.5.dp.toPx(),
                center = Offset(w - 16.dp.toPx(), y),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .width(124.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(OnSurface.copy(alpha = 0.06f))
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
