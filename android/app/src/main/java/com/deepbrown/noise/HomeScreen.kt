package com.deepbrown.noise

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    onQuick: (NoiseColor) -> Unit,
    onPlaySound: (SavedSound) -> Unit,
    onSoundMenu: (SavedSound) -> Unit,
    onToggleNowPlaying: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    val quickColor =
        if (loaded && soundId == null) color else null

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Colors of Noise",
                fontFamily = Fraunces,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                color = OnSurface,
            )
        }
        DividerLine()

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Quick Noise")
            ColorSelector(selected = quickColor, onSelect = onQuick)

            Spacer(Modifier.height(12.dp))
            SectionTitle("Sounds created")

            if (sounds.isEmpty()) {
                EmptySounds(onCreate = onCreate)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sounds.forEach { sound ->
                        SoundRow(
                            sound = sound,
                            onPlay = { onPlaySound(sound) },
                            onMenu = { onSoundMenu(sound) },
                        )
                    }
                }
            }
        }

        if (loaded) {
            NowPlayingCard(
                title = if (soundId == null) "Quick Noise" else soundName,
                subtitle = "${color.label} Noise",
                color = color.dot,
                playing = playing,
                onToggle = onToggleNowPlaying,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        BottomBar(
            onCollection = {},
            onCreate = onCreate,
            onSettings = onSettings,
        )
    }
}
