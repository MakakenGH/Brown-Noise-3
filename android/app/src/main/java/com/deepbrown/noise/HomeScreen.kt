package com.deepbrown.noise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private val SoundCardStride = 84.dp // card 72 + list spacing 12

@Composable
fun HomeScreen(
    sounds: List<SavedSound>,
    playing: Boolean,
    loaded: Boolean,
    soundId: String?,
    soundName: String,
    color: NoiseColor,
    deep: Float,
    scope: () -> FloatArray?,
    onQuick: (NoiseColor) -> Unit,
    onPlaySound: (SavedSound) -> Unit,
    onSoundMenu: (SavedSound) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onToggleNowPlaying: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    timerEndWall: Long,
    savedId: String?,
    showReorderHint: Boolean,
    onReorderHintSeen: () -> Unit,
    onMoveSound: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit,
) {
    val quickColor = if (loaded && soundId == null) color else null
    val auraColor = if (loaded) color.dot else NeutralAura
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scopeCoroutine = rememberCoroutineScope()
    val stridePx = with(density) { SoundCardStride.toPx() }
    val dragBottomInset = if (loaded) 160.dp else 68.dp
    val currentDragBottomInset by rememberUpdatedState(dragBottomInset)
    val currentSounds by rememberUpdatedState(sounds)

    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var draggedIndex by remember { mutableIntStateOf(-1) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerEndWall) {
        now = System.currentTimeMillis()
        while (timerEndWall > 0L && timerEndWall > now) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = (timerEndWall - now).coerceAtLeast(0L)
    val timerText = when {
        timerEndWall == 0L -> null
        remaining >= 60_000L -> "${ceil(remaining / 60_000.0).roundToInt()} min"
        else -> "${(remaining / 1_000L).coerceAtLeast(1L)}s"
    }
    val timerLabel = timerText

    Box(Modifier.fillMaxSize().background(Bg)) {
        NoiseAura(
            color = auraColor,
            modifier = Modifier.fillMaxSize(),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                item {
                    SectionHeader("Colors of Noise")
                }
                item {
                    ColorSelector(selected = quickColor, onSelect = onQuick)
                }
                item {
                    SoundsHeader(
                        count = sounds.size,
                        onAdd = onCreate,
                    )
                }
                if (showReorderHint && sounds.size > 1) {
                    item {
                        Text(
                            text = "Hold and drag to reorder",
                            fontFamily = DmSans,
                            fontSize = 11.5.sp,
                            color = LabelMuted,
                        )
                    }
                }
                if (sounds.isEmpty()) {
                    item {
                        EmptySounds(onCreate = onCreate)
                    }
                } else {
                    itemsIndexed(sounds, key = { _, sound -> sound.id }) { index, sound ->
                        val isDragged = sound.id == draggedId
                        val liveIndex by rememberUpdatedState(index)
                        SoundRow(
                            sound = sound,
                            active = loaded && sound.id == soundId,
                            playing = playing,
                            justSaved = sound.id == savedId,
                            onPlay = { onPlaySound(sound) },
                            onMenu = { onSoundMenu(sound) },
                            onMoveUp = if (index > 0) {
                                {
                                    onMoveSound(index, index - 1)
                                    onReorderFinished()
                                }
                            } else null,
                            onMoveDown = if (index < sounds.lastIndex) {
                                {
                                    onMoveSound(index, index + 1)
                                    onReorderFinished()
                                }
                            } else null,
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .then(if (isDragged) Modifier else Modifier.animateItem())
                                .shadow(
                                    elevation = if (isDragged) 18.dp else 0.dp,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                                    clip = false,
                                )
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationY = dragOffset
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .pointerInput(sound.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedId = sound.id
                                            draggedIndex = liveIndex
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val from = draggedIndex
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            val target = (from + (dragOffset / stridePx).roundToInt())
                                                .coerceIn(0, currentSounds.lastIndex)
                                            if (target != from) {
                                                onMoveSound(from, target)
                                                dragOffset -= (target - from) * stridePx
                                                draggedIndex = target
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            val info = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == sound.id }
                                            if (info != null) {
                                                val top = info.offset + dragOffset
                                                val bottom = top + info.size
                                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                                val viewportEnd = listState.layoutInfo.viewportEndOffset
                                                val bottomInsetPx = with(density) { currentDragBottomInset.toPx() }
                                                if (top < viewportStart + 72) {
                                                    scopeCoroutine.launch { listState.scrollBy(-12f) }
                                                } else if (bottom > viewportEnd - bottomInsetPx - 72) {
                                                    scopeCoroutine.launch { listState.scrollBy(12f) }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedId = null
                                            draggedIndex = -1
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onReorderHintSeen()
                                            onReorderFinished()
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            draggedIndex = -1
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onReorderHintSeen()
                                            onReorderFinished()
                                        },
                                    )
                                },
                        )
                    }
                }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = loaded,
                enter = fadeIn(tween(220)) + expandVertically(tween(280), expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom),
            ) {
                PlayerCard(
                    title = if (soundId == null) "Colors of Noise" else soundName,
                    subtitle = "${color.label} Noise",
                    accent = color.dot,
                    orbColor = soundOrbColor(color.dot, deep),
                    playing = playing,
                    scope = scope,
                    statusChip = if (timerLabel != null) {
                        {
                            PlayerChip(
                                text = timerLabel,
                                leading = { TimerGlyph(OnSurfaceVariant, 10.dp) },
                                onClick = onSettings,
                            )
                        }
                    } else {
                        null
                    },
                    onOpen = onOpenNowPlaying,
                    onToggle = onToggleNowPlaying,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            BottomBar(onSettings = onSettings)
        }
    }
}
