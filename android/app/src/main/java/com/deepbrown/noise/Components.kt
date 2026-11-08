package com.deepbrown.noise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* ---------------------------------------------------------------------- */
/* Icons drawn locally so the app needs no icon dependency                */
/* ---------------------------------------------------------------------- */

@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (playing) {
            drawRoundRect(
                tint,
                topLeft = Offset(w * 0.26f, h * 0.18f),
                size = Size(w * 0.17f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.05f),
            )
            drawRoundRect(
                tint,
                topLeft = Offset(w * 0.57f, h * 0.18f),
                size = Size(w * 0.17f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.05f),
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.18f)
                lineTo(w * 0.32f, h * 0.82f)
                lineTo(w * 0.82f, h * 0.50f)
                close()
            }
            drawPath(path, tint)
        }
    }
}

@Composable
fun MusicNoteIcon(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.62f),
            size = Size(w * 0.36f, h * 0.28f),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.20f),
            size = Size(w * 0.09f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
        val flag = Path().apply {
            moveTo(w * 0.48f, h * 0.20f)
            quadraticTo(w * 0.78f, h * 0.22f, w * 0.86f, h * 0.46f)
            lineTo(w * 0.78f, h * 0.44f)
            quadraticTo(w * 0.68f, h * 0.30f, w * 0.48f, h * 0.32f)
            close()
        }
        drawPath(flag, tint)
    }
}

/* ---------------------------------------------------------------------- */
/* Shared building blocks                                                  */
/* ---------------------------------------------------------------------- */

@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 14.dp)
            .width(32.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Track),
    )
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        fontFamily = Fraunces,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        textAlign = TextAlign.Center,
        color = OnSurface,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(OnSurface)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
    }
}

@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Placeholder text",
    singleLine: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Surface)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnSurface,
            ),
            cursorBrush = SolidColor(OnSurface),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = DmSans, fontSize = 14.sp, color = LabelMuted)
                }
                inner()
            },
        )
    }
}

@Composable
fun ActionRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    container: Color = Surface,
    content: Color = OnSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = content)
    }
}

@Composable
fun ColorSelector(
    selected: NoiseColor?,
    onSelect: (NoiseColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Surface)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoiseColor.entries.forEach { color ->
            val active = selected == color
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (active) OnSurface else Color.Transparent)
                    .clickable { onSelect(color) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color.dot),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = color.label,
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (active) Ink else OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SliderTrack(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress {
                    currentOnChange(it.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val thumbX = value * w
            val barH = 4.dp.toPx()

            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(0f, cy - barH / 2f),
                size = Size((thumbX - barH / 2f).coerceAtLeast(0f), barH),
                cornerRadius = CornerRadius(barH / 2f),
            )

            val dot = 1.6.dp.toPx()
            val step = 13.dp.toPx()
            var x = thumbX + 12.dp.toPx()
            while (x < w) {
                drawCircle(Track, radius = dot, center = Offset(x, cy))
                x += step
            }

            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(thumbX - 1.5.dp.toPx(), cy - 9.dp.toPx()),
                size = Size(3.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
    }
}

@Composable
fun NoiseSlider(
    label: String,
    value: Float,
    description: String,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = DmSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = OnSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(value * 100).roundToInt()}%",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = LabelMuted,
            )
        }
        SliderTrack(
            value = value,
            onChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            fontFamily = DmSans,
            fontSize = 13.sp,
            color = LabelMuted,
        )
    }
}

@Composable
fun EmptySounds(onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(onClick = onCreate),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Track,
                cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 5.dp.toPx()),
                    ),
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Create your first custom sound",
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "It will appear here!",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = LabelMuted,
            )
        }
    }
}

@Composable
fun SoundRow(
    sound: SavedSound,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Surface)
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(lerp(sound.color.dot, Color.White, 0.45f), sound.color.dot),
                    ),
                ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sound.name,
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = OnSurface,
            )
            Text(
                text = "${sound.color.label} Noise",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "${sound.name} options",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun NowPlayingCard(
    title: String,
    subtitle: String,
    color: Color,
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(RoundedCornerShape(28.dp))
            .background(Surface)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(lerp(color, Color.White, 0.45f), color),
                    ),
                ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = OnSurface,
            )
            Text(
                text = subtitle,
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Track)
                .clickable(onClick = onToggle)
                .semantics { contentDescription = if (playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseIcon(playing, OnSurface, 18.dp)
        }
    }
}

@Composable
fun BottomBar(onCollection: () -> Unit, onCreate: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Track.copy(alpha = 0.45f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onCollection),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MusicNoteIcon(OnSurface, 22.dp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Your Collection",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = OnSurface,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onCreate),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OnSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "Create new",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = OnSurface,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onSettings),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Settings",
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontFamily = Fraunces,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = OnSurface,
    )
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Track.copy(alpha = 0.45f)))
}

@Composable
fun SheetScrimClickable(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(onClick = onDismiss),
    )
}

@Composable
fun BorderBox(
    modifier: Modifier = Modifier,
    color: Color = Track,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, color, RoundedCornerShape(28.dp)),
    ) {
        content()
    }
}
