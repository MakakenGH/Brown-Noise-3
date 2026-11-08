package com.deepbrown.noise

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Palette measured from the mockups. */
val Bg = Color(0xFF100630)
val Surface = Color(0xFF403859)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceVariant = Color(0xFFA09CAC)
val LabelMuted = Color(0xFF878297)
val Track = Color(0xFF534C69)
val Outline = Color(0xFFCAC4D0)
val Danger = Color(0xFF87293E)
val Ink = Color(0xFF100630)

val WhiteNoise = Color(0xFFD3D7D9)
val BrownNoise = Color(0xFFB0815E)
val PinkNoise = Color(0xFFA381B4)
val GreenNoise = Color(0xFF8BA681)

val NoiseColor.dot: Color
    get() = when (this) {
        NoiseColor.WHITE -> WhiteNoise
        NoiseColor.BROWN -> BrownNoise
        NoiseColor.PINK -> PinkNoise
        NoiseColor.GREEN -> GreenNoise
    }

val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
)

val FrauncesDisplay = FontFamily(
    Font(R.font.fraunces_display, FontWeight.Normal),
)

val DmSans = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_semibold, FontWeight.SemiBold),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ColorsOfNoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OnSurface,
            onPrimary = Ink,
            background = Bg,
            onBackground = OnSurface,
            surface = Bg,
            onSurface = OnSurface,
            surfaceVariant = Surface,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
            outlineVariant = Track,
            error = Danger,
            onError = OnSurface,
        ),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
