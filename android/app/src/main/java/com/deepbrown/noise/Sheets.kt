package com.deepbrown.noise

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sound.name,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = Fraunces,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                color = OnSurface,
            )
            Text(
                text = "${sound.color.label} Noise",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = DmSans,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ActionRow(
                label = "Edit Sound",
                icon = { MusicNoteIcon(OnSurface, 20.dp) },
                onClick = onEdit,
            )
            ActionRow(
                label = "Put this sound first in list",
                icon = { Icon(Icons.Default.List, null, tint = OnSurface, modifier = Modifier.size(20.dp)) },
                onClick = onPutFirst,
            )
            ActionRow(
                label = "Rename sound",
                icon = { Icon(Icons.Default.Edit, null, tint = OnSurface, modifier = Modifier.size(20.dp)) },
                onClick = onRename,
            )
            ActionRow(
                label = "Delete Sound",
                icon = { Icon(Icons.Default.Delete, null, tint = OnSurface, modifier = Modifier.size(20.dp)) },
                onClick = onDelete,
                container = Danger,
            )
        }
    }
}

@Composable
fun NameSheet(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Sheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = Fraunces,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                color = OnSurface,
            )
            NameField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
            )
            PrimaryButton(
                text = confirmLabel,
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
    Sheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Settings",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = Fraunces,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                color = OnSurface,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Surface)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Volume",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = OnSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        fontFamily = DmSans,
                        fontSize = 13.sp,
                        color = LabelMuted,
                    )
                }
                SliderTrack(value = volume, onChange = onVolume)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Sleep Timer",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = OnSurface,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours")
                        .forEach { (minutes, label) ->
                            val selected = timerMin == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) OnSurface else Color.Transparent)
                                    .clickable { onTimer(minutes) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = DmSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (selected) Ink else OnSurfaceVariant,
                                )
                            }
                        }
                }
            }
        }
    }
}
