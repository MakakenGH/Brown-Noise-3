package com.deepbrown.noise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    activeId: String?,
    quickColor: NoiseColor?,
    onQuick: (NoiseColor) -> Unit,
    onPlaySound: (SavedSound) -> Unit,
    onSoundMenu: (SavedSound) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
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
                            active = sound.id == activeId,
                            playing = playing && sound.id == activeId,
                            onPlay = { onPlaySound(sound) },
                            onMenu = { onSoundMenu(sound) },
                        )
                    }
                }
            }
        }

        BottomBar(
            onCollection = {},
            onCreate = onCreate,
            onSettings = onSettings,
        )
    }
}
