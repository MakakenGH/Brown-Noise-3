package com.deepbrown.noise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    initial: Draft,
    editing: Boolean,
    previewing: Boolean,
    onDraftChanged: (Draft) -> Unit,
    onBack: () -> Unit,
    onSave: (Draft) -> Unit,
    onTogglePreview: (Draft) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var showNameSheet by remember { mutableStateOf(false) }

    fun update(next: Draft) {
        draft = next
        onDraftChanged(next)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = OnSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = "Noise Generator",
                    modifier = Modifier.weight(1f),
                    fontFamily = Fraunces,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    color = OnSurface,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OnSurface)
                        .clickable {
                            if (draft.name.isBlank()) showNameSheet = true else onSave(draft)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DeepnessField(
                    draft = draft,
                    onChange = { update(draft.copy(deep = it)) },
                )
                ColorSelector(
                    selected = draft.color,
                    onSelect = { update(draft.copy(color = it)) },
                )
                NoiseSlider(
                    label = "Rumble",
                    value = draft.rumble,
                    description = "Extra weight below 90 Hz",
                    onChange = { update(draft.copy(rumble = it)) },
                )
                NoiseSlider(
                    label = "Width",
                    value = draft.width,
                    description = "From centred to enveloping",
                    onChange = { update(draft.copy(width = it)) },
                )
                NoiseSlider(
                    label = "Swell",
                    value = draft.swell,
                    description = "Slow, never-repeating breathing",
                    onChange = { update(draft.copy(swell = it)) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Bg)
                    .navigationBarsPadding(),
            ) {
                DividerLine()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (editing) "Currently editing" else "Currently creating",
                            fontFamily = DmSans,
                            fontSize = 11.sp,
                            color = LabelMuted,
                        )
                        Text(
                            text = if (editing) draft.name else "Currently creating",
                            fontFamily = Fraunces,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            color = OnSurface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Track)
                            .clickable { onTogglePreview(draft) }
                            .semantics { contentDescription = if (previewing) "Pause preview" else "Play preview" },
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayPauseIcon(previewing, OnSurface, 20.dp)
                    }
                }
            }
        }
    }

    if (showNameSheet) {
        NameSheet(
            title = "Give your sound a name",
            initial = draft.name,
            placeholder = "Placeholder text",
            confirmLabel = "Save Changes",
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
    onChange: (Float) -> Unit,
) {
    val currentDraft by rememberUpdatedState(draft)
    val currentOnChange by rememberUpdatedState(onChange)
    val deep = draft.deep

    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF100630), Color(0xFF322A4D)),
                ),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    val next = (currentDraft.deep + dy / size.height.toFloat()).coerceIn(0f, 1f)
                    currentOnChange(next)
                }
            }
            .semantics {
                contentDescription = "Deepness"
                progressBarRangeInfo = ProgressBarRangeInfo(deep, 0f..1f)
                setProgress {
                    currentOnChange(it.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val top = 18.dp.toPx()
            val bottom = h - 18.dp.toPx()
            val ticks = 12
            for (i in 0 until ticks) {
                val y = top + (bottom - top) * i / (ticks - 1)
                drawLine(
                    color = OnSurfaceVariant.copy(alpha = 0.40f),
                    start = Offset(w - 30.dp.toPx(), y),
                    end = Offset(w - 12.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val handleY = top + (bottom - top) * deep
            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(w - 40.dp.toPx(), handleY - 1.5.dp.toPx()),
                size = Size(28.dp.toPx(), 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .width(104.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface.copy(alpha = 0.65f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = (draft.deep * 100).roundToInt().toString(),
                fontFamily = FrauncesDisplay,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                color = OnSurface,
            )
            Text(
                text = "Deepness",
                fontFamily = DmSans,
                fontSize = 12.sp,
                color = OnSurface,
            )
        }
    }
}
