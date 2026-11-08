package com.deepbrown.noise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    onSaveDraft: (Draft, String?, Boolean) -> Unit,
    onDeleteSound: (SavedSound) -> Unit,
    onPutFirst: (SavedSound) -> Unit,
    onRenameSound: (SavedSound, String) -> Unit,
    onVolume: (Float) -> Unit,
    onTimer: (Int) -> Unit,
    onPreviewStart: (Draft) -> Unit,
    onPreviewStop: () -> Unit,
    onDiscardPreview: () -> Unit,
    onPreviewParams: (Draft) -> Unit,
    scope: () -> FloatArray?,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    var previewActive by remember { mutableStateOf(false) }
    var previewTouched by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedSound?>(null) }

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
            ),
        )
    }

    fun leaveEditor() {
        if (previewTouched) onDiscardPreview()
        previewActive = false
        previewTouched = false
        screen = Screen.Home
    }

    when (val current = screen) {
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
            )
        }
        is Screen.Editor -> {
            EditorScreen(
                initial = current.sound?.let { Draft.from(it) }
                    ?: current.seed
                    ?: Draft(color = if (ui.loaded) ui.params.color else NoiseColor.WHITE),
                editing = current.sound != null,
                previewing = previewActive,
                scope = scope,
                onDraftChanged = { draft ->
                    if (previewActive) onPreviewParams(draft)
                },
                onBack = { leaveEditor() },
                onSave = { draft ->
                    onSaveDraft(draft, current.sound?.id, previewTouched)
                    previewActive = false
                    previewTouched = false
                    screen = Screen.Home
                },
                onTogglePreview = { draft ->
                    if (previewActive) {
                        onPreviewStop()
                        previewActive = false
                    } else {
                        onPreviewStart(draft)
                        previewActive = true
                        previewTouched = true
                    }
                },
            )
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
                onPutFirst = {
                    onPutFirst(current.sound)
                    sheet = null
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
                onVolume = onVolume,
                onTimer = onTimer,
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
