package com.deepbrown.noise

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* A low-light palette: ink-violet ground, warm paper text, one brass system
 * accent, and four noise colours with enough chroma to survive dim panels. */
val Bg = Color(0xFF0A0712)
val Surface = Color(0xFF151022)
val SurfaceHigh = Color(0xFF1D1630)
val Hairline = Color(0xFF2C2442)
val OnSurface = Color(0xFFF4EEE3)
val OnSurfaceVariant = Color(0xFFB3A9BF)
val LabelMuted = Color(0xFF93889F)
val Track = Color(0xFF322A57)

val Ink = Color(0xFF171021)
val Brass = Color(0xFFD8B98D)
val BrassInk = Color(0xFF2A1B10)
val Danger = Color(0xFFB04A5D)
val DangerInk = Color(0xFFFFD6DC)
val DangerSurface = Color(0xFF2A0E1A)

val WhiteNoise = Color(0xFFF0E9DA)
val BrownNoise = Color(0xFFCE9167)
val PinkNoise = Color(0xFFD4A1CB)
val GreenNoise = Color(0xFF8FCC78)
val NeutralAura = Color(0xFF5A4B8A)

/** Saved sounds get a brighter or darker orb depending on their depth. */
fun soundOrbColor(base: Color, deep: Float): Color {
    val d = deep.coerceIn(0f, 1f)
    val lifted = lerp(base, Color.White, 0.20f * (1f - d))
    return lerp(lifted, Color.Black, 0.30f * d)
}

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

/** Shared motion language. One fast press, one quiet state change, one bloom. */
object Motion {
    const val PressMs = 90
    const val StateMs = 240
    const val EnterMs = 320
    const val BloomMs = 900
    const val AmbientSeconds = 18f

    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val Sheet = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}

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
