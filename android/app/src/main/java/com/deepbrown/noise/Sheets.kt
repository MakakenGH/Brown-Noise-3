package com.deepbrown.noise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Bg,
        scrimColor = Color.Black.copy(alpha = 0.52f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetHandle() },
    ) {
        content()
    }
}

@Composable
fun SoundSettingsSheet(
    sound: SavedSound,
    onEdit: () -> Unit,
    onPutFirst: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorOrb(sound.color.dot, 44.dp, ring = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SheetTitle(sound.name)
                    SheetSubtitle("${sound.color.label} Noise")
                }
            }
            Spacer(Modifier.height(22.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(24.dp)),
            ) {
                ActionRow(
                    label = "Edit sound",
                    icon = { MusicNoteIcon(OnSurface, 20.dp) },
                    onClick = onEdit,
                )
                HairlineDivider(startPadding = 50.dp)
                ActionRow(
                    label = "Move to top",
                    icon = { Icon(Icons.Default.List, null, tint = OnSurface, modifier = Modifier.size(20.dp)) },
                    onClick = onPutFirst,
                )
                HairlineDivider(startPadding = 50.dp)
                ActionRow(
                    label = "Rename",
                    icon = { Icon(Icons.Default.Edit, null, tint = OnSurface, modifier = Modifier.size(20.dp)) },
                    onClick = onRename,
                )
            }

            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DangerSurface)
                    .border(1.dp, Danger.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
            ) {
                ActionRow(
                    label = "Delete sound",
                    icon = { Icon(Icons.Default.Delete, null, tint = DangerInk, modifier = Modifier.size(20.dp)) },
                    onClick = onDelete,
                    content = DangerInk,
                )
            }
        }
    }
}

@Composable
fun NameSheet(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    accent: Color,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Sheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(title)
            SheetSubtitle("A name makes it easy to find in your collection.")
            NameField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
            )
            PrimaryButton(
                text = confirmLabel,
                accent = accent,
                enabled = value.isNotBlank(),
                onClick = { onSave(value.trim()) },
            )
        }
    }
}

@Composable
fun PlaybackSettingsSheet(
    volume: Float,
    timerMin: Int,
    onVolume: (Float) -> Unit,
    onTimer: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Sheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            SheetTitle("Playback")
            Spacer(Modifier.height(4.dp))
            SheetSubtitle("Level and how long it keeps going.")
            Spacer(Modifier.height(20.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(24.dp)),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Volume",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = OnSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(volume * 100).roundToInt()}%",
                            style = Numeric,
                            fontSize = 13.sp,
                            color = OnSurfaceVariant,
                        )
                    }
                    SliderTrack(value = volume, onChange = onVolume, contentDescription = "Volume")
                }
                HairlineDivider(startPadding = 18.dp)
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        "Sleep timer",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = OnSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours")
                            .forEach { (minutes, label) ->
                                val selected = timerMin == minutes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) OnSurface else SurfaceHigh)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTimer(minutes)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = DmSans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (selected) Ink else OnSurfaceVariant,
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
