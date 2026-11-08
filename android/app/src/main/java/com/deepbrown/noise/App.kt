package com.deepbrown.noise

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val sound: SavedSound?) : Screen
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
    onSaveDraft: (Draft, String?, Boolean) -> Unit,
    onDeleteSound: (SavedSound) -> Unit,
    onPutFirst: (SavedSound) -> Unit,
    onRenameSound: (SavedSound, String) -> Unit,
    onVolume: (Float) -> Unit,
    onTimer: (Int) -> Unit,
    onPreviewStart: (Draft) -> Unit,
    onPreviewStop: () -> Unit,
    onPreviewParams: (Draft) -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    var previewActive by remember { mutableStateOf(false) }

    LaunchedEffect(ui.playing) {
        if (!ui.playing) previewActive = false
    }

    fun leaveEditor() {
        if (previewActive) onPreviewStop()
        previewActive = false
        screen = Screen.Home
    }

    when (val current = screen) {
        Screen.Home -> {
            HomeScreen(
                sounds = sounds,
                playing = ui.playing,
                activeId = ui.soundId,
                quickColor = if (ui.playing && ui.soundId == null) ui.params.color else null,
                onQuick = onQuick,
                onPlaySound = onPlaySound,
                onSoundMenu = { sheet = HomeSheet.Sound(it) },
                onCreate = { screen = Screen.Editor(null) },
                onSettings = { sheet = HomeSheet.Playback },
            )
        }
        is Screen.Editor -> {
            BackHandler { leaveEditor() }
            EditorScreen(
                initial = current.sound?.let { Draft.from(it) } ?: Draft(),
                editing = current.sound != null,
                previewing = previewActive,
                onDraftChanged = { draft ->
                    if (previewActive) onPreviewParams(draft)
                },
                onBack = { leaveEditor() },
                onSave = { draft ->
                    onSaveDraft(draft, current.sound?.id, previewActive)
                    previewActive = false
                    screen = Screen.Home
                },
                onTogglePreview = { draft ->
                    if (previewActive) {
                        onPreviewStop()
                        previewActive = false
                    } else {
                        onPreviewStart(draft)
                        previewActive = true
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
                    screen = Screen.Editor(current.sound)
                },
                onPutFirst = {
                    onPutFirst(current.sound)
                    sheet = null
                },
                onRename = { sheet = HomeSheet.Rename(current.sound) },
                onDelete = {
                    onDeleteSound(current.sound)
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
                confirmLabel = "Save Changes",
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
}
