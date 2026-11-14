package com.deepbrown.noise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val SoundCardStride = 64.dp // row height; the collection has no gaps

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
    bands: () -> FloatArray?,
    onQuick: (NoiseColor) -> Unit,
    onPlaySound: (SavedSound) -> Unit,
    onSoundMenu: (SavedSound) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onToggleNowPlaying: () -> Unit,
    onCreate: () -> Unit,
    onCreatePreset: (Preset) -> Unit,
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
    val animatedAura by androidx.compose.animation.animateColorAsState(
        targetValue = auraColor,
        animationSpec = tween(1_200, easing = Motion.EaseOut),
        label = "home aura",
    )
    val tier = rememberVisualTier()
    val visuals = rememberAudioVisuals(scope, bands, playing, tier)
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scopeCoroutine = rememberCoroutineScope()
    val stridePx = with(density) { SoundCardStride.toPx() }
    val dragBottomInset = if (loaded) 124.dp else 48.dp
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
    val savedSound = sounds.firstOrNull { it.id == savedId }

    Box(Modifier.fillMaxSize().background(Bg)) {
        LivingAura(
            color = animatedAura,
            visuals = visuals,
            tier = tier,
            modifier = Modifier.fillMaxSize(),
        )

        if (sounds.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                HomeHeader(
                    onSettings = onSettings,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                ColorSelector(
                    selected = quickColor,
                    onSelect = onQuick,
                    activeColor = if (loaded) color else null,
                    playing = playing,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SoundsHeader(
                    count = 0,
                    onAdd = onCreate,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                EmptySounds(
                    onCreate = onCreate,
                    onPreset = onCreatePreset,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(if (loaded) 92.dp else 4.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = if (loaded) 112.dp else 24.dp,
                ),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    HomeHeader(
                        onSettings = onSettings,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                }
                item {
                    ColorSelector(
                        selected = quickColor,
                        onSelect = onQuick,
                        activeColor = if (loaded) color else null,
                        playing = playing,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item {
                    SoundsHeader(
                        count = sounds.size,
                        onAdd = onCreate,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (showReorderHint && sounds.size > 1) {
                    item {
                        Text(
                            text = "Hold and drag to reorder",
                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                            fontFamily = DmSans,
                            fontSize = 11.5.sp,
                            color = LabelMuted,
                        )
                    }
                }
                itemsIndexed(sounds, key = { _, sound -> sound.id }) { index, sound ->
                    val isDragged = sound.id == draggedId
                    val liveIndex by rememberUpdatedState(index)
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 22.dp else 0.dp,
                        topEnd = if (index == 0) 22.dp else 0.dp,
                        bottomStart = if (index == sounds.lastIndex) 22.dp else 0.dp,
                        bottomEnd = if (index == sounds.lastIndex) 22.dp else 0.dp,
                    )
                    SoundRow(
                        sound = sound,
                        active = loaded && sound.id == soundId,
                        playing = playing,
                        shape = shape,
                        first = index == 0,
                        last = index == sounds.lastIndex,
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
                                elevation = if (isDragged) 16.dp else 0.dp,
                                shape = RoundedCornerShape(22.dp),
                                clip = false,
                            )
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = dragOffset
                                    scaleX = 1.015f
                                    scaleY = 1.015f
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
                item { Spacer(Modifier.height(12.dp)) }
            }
        }

        AnimatedVisibility(
            visible = loaded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            enter = fadeIn(tween(220)) + expandVertically(tween(280), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom),
        ) {
            PlayerCard(
                title = if (soundId == null) "Colors of Noise" else soundName,
                subtitle = "${color.label} Noise",
                accent = color.dot,
                orbColor = soundOrbColor(color.dot, deep),
                playing = playing,
                visuals = visuals,
                onOpen = onOpenNowPlaying,
                onToggle = onToggleNowPlaying,
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
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = savedSound != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = if (loaded) 96.dp else 16.dp),
            enter = fadeIn(tween(160)) + slideInVertically(tween(220, easing = Motion.EaseOut)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 4 },
        ) {
            SavedToast(name = savedSound?.name.orEmpty())
        }
    }
}
