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

/* A late-night palette: ink-violet ground, warm paper text, and four
 * desaturated noise colours that carry the interface accent. */
val Bg = Color(0xFF0B0618)
val Surface = Color(0xFF16102F)
val SurfaceHigh = Color(0xFF1F1740)
val Hairline = Color(0xFF2C2452)
val OnSurface = Color(0xFFF4F0EA)
val OnSurfaceVariant = Color(0xFFA9A2BE)
val LabelMuted = Color(0xFF8C84A6)
val Track = Color(0xFF322A57)

val Ink = Color(0xFF120B24)
val Danger = Color(0xFFB04A5D)
val DangerInk = Color(0xFFFFD6DC)
val DangerSurface = Color(0xFF2A0E1A)

val WhiteNoise = Color(0xFFE9E6DE)
val BrownNoise = Color(0xFFC68B63)
val PinkNoise = Color(0xFFC99CDC)
val GreenNoise = Color(0xFF9FBF96)
val NeutralAura = Color(0xFF5A4B8A)

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

/** Tabular figures for slider values and percentages. */
val Numeric = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
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
        fontSize = 12.5.sp,
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
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ColorsOfNoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OnSurface,
            onPrimary = Ink,
            background = Bg,
            onBackground = OnSurface,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceHigh,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Hairline,
            outlineVariant = Track,
            error = Danger,
            onError = DangerInk,
        ),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
