package com.deepbrown.noise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    val quickColor = if (loaded && soundId == null) color else null
    val auraColor = if (loaded) color.dot else NeutralAura
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize().background(Bg)) {
        NoiseAura(
            color = auraColor,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("Colors of Noise")
                ColorSelector(selected = quickColor, onSelect = onQuick)

                Spacer(Modifier.height(12.dp))
                SoundsHeader(
                    count = sounds.size,
                    accent = if (loaded) color.dot else OnSurface,
                    onAdd = onCreate,
                )

                if (sounds.isEmpty()) {
                    EmptySounds(onCreate = onCreate)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sounds.forEach { sound ->
                            SoundRow(
                                sound = sound,
                                active = loaded && sound.id == soundId,
                                playing = playing,
                                onPlay = { onPlaySound(sound) },
                                onMenu = { onSoundMenu(sound) },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = loaded,
                enter = fadeIn(tween(220)) + expandVertically(tween(280), expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom),
            ) {
                NowPlayingCard(
                    title = if (soundId == null) "Colors of Noise" else soundName,
                    subtitle = "${color.label} Noise",
                    color = soundOrbColor(color.dot, deep),
                    playing = playing,
                    scope = scope,
                    onOpen = onOpenNowPlaying,
                    onToggle = onToggleNowPlaying,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            BottomBar(onSettings = onSettings)
        }
    }
}
