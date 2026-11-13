package com.deepbrown.noise

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val sound: SavedSound?, val seed: Draft? = null) : Screen
}

private sealed interface HomeSheet {
    data class Sound(val sound: SavedSound) : HomeSheet
    data class Rename(val sound: SavedSound) : HomeSheet
    data object Playback : HomeSheet
}

@Composable
fun ColorsOfNoiseApp(
    ui: UiState,
    sounds: List<SavedSound>,
    onQuick: (NoiseColor) -> Unit,
    onPlaySound: (SavedSound) -> Unit,
    onToggleNowPlaying: () -> Unit,
    onSaveDraft: (Draft, String?, Boolean) -> SavedSound,
    onDeleteSound: (SavedSound) -> Unit,
    onMoveSound: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit,
    onReorderHintSeen: () -> Unit,
    showReorderHint: Boolean,
    onRenameSound: (SavedSound, String) -> Unit,
    onVolume: (Float) -> Unit,
    onTimer: (Int) -> Unit,
    onFadeIn: (Int) -> Unit,
    onFadeOut: (Int) -> Unit,
    onPreviewStart: (Draft) -> Unit,
    onPreviewStop: () -> Unit,
    onDiscardPreview: () -> Unit,
    onPreviewParams: (Draft) -> Unit,
    previewHintSeen: Boolean,
    onPreviewHintSeen: () -> Unit,
    scope: () -> FloatArray?,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    var previewActive by remember { mutableStateOf(false) }
    var previewTouched by remember { mutableStateOf(false) }
    var previewHintDismissed by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedSound?>(null) }
    var savedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedId) {
        if (savedId != null) {
            delay(1_800)
            savedId = null
        }
    }

    LaunchedEffect(ui.playing) {
        if (!ui.playing) previewActive = false
    }

    fun openEditor(sound: SavedSound?, seed: Draft? = null) {
        previewActive = false
        previewTouched = false
        screen = Screen.Editor(sound, seed)
    }

    fun openNowPlaying() {
        if (ui.soundId != null) {
            sounds.firstOrNull { it.id == ui.soundId }?.let { openEditor(it) }
            return
        }
        if (!ui.loaded) return
        openEditor(
            sound = null,
            seed = Draft(
                name = "",
                color = ui.params.color,
                deep = ui.params.deep,
                rumble = ui.params.rumble,
                width = ui.params.width,
                swell = ui.params.swell,
                rain = ui.params.rain,
                drops = ui.params.drops,
                plink = ui.params.plink,
                wind = ui.params.wind,
                fan = ui.params.fan,
                air = ui.params.air,
                drift = ui.params.drift,
            ),
        )
    }

    fun leaveEditor() {
        if (previewTouched) onDiscardPreview()
        previewActive = false
        previewTouched = false
        screen = Screen.Home
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState is Screen.Editor) {
                (slideInVertically(tween(260)) { it / 8 } + fadeIn(tween(220))) togetherWith
                    fadeOut(tween(140))
            } else {
                fadeIn(tween(220)) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(240)) { it / 10 })
            }
        },
        label = "screen",
    ) { current ->
        when (current) {
        Screen.Home -> {
            HomeScreen(
                sounds = sounds,
                playing = ui.playing,
                loaded = ui.loaded,
                soundId = ui.soundId,
                soundName = ui.soundName,
                color = ui.params.color,
                deep = ui.params.deep,
                scope = scope,
                onQuick = onQuick,
                onPlaySound = onPlaySound,
                onSoundMenu = { sheet = HomeSheet.Sound(it) },
                onOpenNowPlaying = { openNowPlaying() },
                onToggleNowPlaying = onToggleNowPlaying,
                onCreate = { openEditor(null) },
                onSettings = { sheet = HomeSheet.Playback },
                timerEndWall = ui.timerEndWall,
                savedId = savedId,
                showReorderHint = showReorderHint,
                onReorderHintSeen = onReorderHintSeen,
                onMoveSound = onMoveSound,
                onReorderFinished = onReorderFinished,
            )
        }
        is Screen.Editor -> {
            EditorScreen(
                initial = current.sound?.let { Draft.from(it) }
                    ?: current.seed
                    ?: Draft(color = if (ui.loaded) ui.params.color else NoiseColor.WHITE),
                editing = current.sound != null,
                previewing = previewActive,
                showPreviewHint = !previewHintSeen && !previewHintDismissed,
                scope = scope,
                onDraftChanged = { draft ->
                    if (previewActive) onPreviewParams(draft)
                },
                onBack = { leaveEditor() },
                onSave = { draft ->
                    val saved = onSaveDraft(draft, current.sound?.id, previewTouched)
                    savedId = saved.id
                    previewActive = false
                    previewTouched = false
                    screen = Screen.Home
                },
                onTogglePreview = { draft ->
                    if (previewActive) {
                        onPreviewStop()
                        previewActive = false
                    } else {
                        previewHintDismissed = true
                        onPreviewHintSeen()
                        onPreviewStart(draft)
                        previewActive = true
                        previewTouched = true
                    }
                },
            )
        }
        }
    }

    when (val current = sheet) {
        is HomeSheet.Sound -> {
            SoundSettingsSheet(
                sound = current.sound,
                onEdit = {
                    sheet = null
                    openEditor(current.sound)
                },
                onRename = { sheet = HomeSheet.Rename(current.sound) },
                onDelete = {
                    deleteTarget = current.sound
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
        is HomeSheet.Rename -> {
            NameSheet(
                title = "Rename sound",
                initial = current.sound.name,
                placeholder = "Placeholder text",
                confirmLabel = "Save changes",
                accent = current.sound.color.dot,
                onSave = { name ->
                    onRenameSound(current.sound, name)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
        HomeSheet.Playback -> {
            PlaybackSettingsSheet(
                volume = ui.params.volume,
                timerMin = ui.timerMin,
                fadeInSec = ui.fadeInSec,
                fadeOutSec = ui.fadeOutSec,
                onVolume = onVolume,
                onTimer = onTimer,
                onFadeIn = onFadeIn,
                onFadeOut = onFadeOut,
                onDismiss = { sheet = null },
            )
        }
        null -> Unit
    }

    deleteTarget?.let { target ->
        val playingThis = target.id == ui.soundId
        ConfirmDialog(
            title = "Delete \u201C${target.name}\u201D?",
            message = if (playingThis) {
                "Playback will stop and it will be removed from your collection."
            } else {
                "It will be removed from your collection."
            },
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteSound(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
