package com.deepbrown.noise

import android.app.ActivityManager
import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Effects are chosen from the device and the system's motion preference. */
enum class VisualTier { Full, Lite }

@Composable
fun rememberVisualTier(): VisualTier {
    val context = LocalContext.current
    return remember(context) {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (activity.isLowRamDevice || power.isPowerSaveMode || !ValueAnimator.areAnimatorsEnabled()) {
            VisualTier.Lite
        } else {
            VisualTier.Full
        }
    }
}

/** One smoothed frame of sound + time, shared by every living surface. */
data class AudioVisuals(
    val level: Float = 0f,
    val sub: Float = 0f,
    val low: Float = 0f,
    val mid: Float = 0f,
    val air: Float = 0f,
    val phase: Float = 0f,
)

/**
 * Samples the existing scope and the new spectral bands at 20 or 30 fps.
 * Only a small snapshot is published; the audio thread never waits for Compose.
 */
@Composable
fun rememberAudioVisuals(
    scope: () -> FloatArray?,
    bands: () -> FloatArray?,
    playing: Boolean,
    tier: VisualTier,
): State<AudioVisuals> {
    val visuals = remember { mutableStateOf(AudioVisuals()) }
    val currentScope by rememberUpdatedState(scope)
    val currentBands by rememberUpdatedState(bands)
    val frameBudget = if (tier == VisualTier.Lite) 50_000_000L else 33_000_000L

    LaunchedEffect(playing, tier) {
        if (!playing) {
            visuals.value = visuals.value.copy(level = 0f, sub = 0f, low = 0f, mid = 0f, air = 0f)
            return@LaunchedEffect
        }
        var last = 0L
        var phase = visuals.value.phase
        var level = visuals.value.level
        var sub = visuals.value.sub
        var low = visuals.value.low
        var mid = visuals.value.mid
        var air = visuals.value.air
        while (isActive) {
            withFrameNanos { now ->
                if (now - last < frameBudget) return@withFrameNanos
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now

                val data = currentScope()
                val target = if (data != null && data.isNotEmpty()) {
                    var sum = 0.0
                    for (v in data) sum += v.toDouble() * v
                    (sqrt(sum / data.size) * 3.8).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                level = approach(level, target, attack = 0.34f, release = 0.09f)

                val spectrum = currentBands()
                if (spectrum != null && spectrum.size >= 4) {
                    sub = approach(sub, spectrum[0], 0.30f, 0.08f)
                    low = approach(low, spectrum[1], 0.32f, 0.08f)
                    mid = approach(mid, spectrum[2], 0.34f, 0.08f)
                    air = approach(air, spectrum[3], 0.36f, 0.10f)
                } else {
                    sub = approach(sub, level * 0.62f, 0.30f, 0.08f)
                    low = approach(low, level * 0.82f, 0.32f, 0.08f)
                    mid = approach(mid, level * 0.58f, 0.34f, 0.08f)
                    air = approach(air, level * 0.32f, 0.36f, 0.10f)
                }

                phase = (phase + dt / Motion.AmbientSeconds) % 1f
                visuals.value = AudioVisuals(level, sub, low, mid, air, phase)
            }
        }
    }
    return visuals
}

private fun approach(value: Float, target: Float, attack: Float, release: Float): Float =
    value + (target - value) * if (target > value) attack else release

/** The room light behind the app. Two fields plus a low pool behind the dock. */
fun DrawScope.drawLivingAura(
    color: Color,
    visuals: AudioVisuals,
    tier: VisualTier,
) {
    val w = size.width
    val h = size.height
    val phase = visuals.phase * 2.0 * PI
    val drift = sin(phase).toFloat()
    val drift2 = sin(phase + 1.7).toFloat()
    val energy = visuals.level.coerceIn(0f, 1f)

    val primaryRadius = h * (0.90f + visuals.low * 0.16f)
    val primaryX = w * (0.30f + drift * 0.022f)
    val primaryY = h * (0.83f + drift2 * 0.015f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.26f + visuals.low * 0.16f),
            0.44f to color.copy(alpha = 0.09f + visuals.low * 0.055f),
            1f to Color.Transparent,
            center = Offset(primaryX, primaryY),
            radius = primaryRadius,
        ),
        center = Offset(primaryX, primaryY),
        radius = primaryRadius,
    )

    if (tier == VisualTier.Full) {
        val secondaryRadius = h * (0.55f + visuals.air * 0.12f)
        val secondaryX = w * (0.83f - drift * 0.02f)
        val secondaryY = h * (0.20f + drift2 * 0.015f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = 0.10f + visuals.air * 0.10f),
                1f to Color.Transparent,
                center = Offset(secondaryX, secondaryY),
                radius = secondaryRadius,
            ),
            center = Offset(secondaryX, secondaryY),
            radius = secondaryRadius,
        )
    }

    val poolRadius = w * (0.72f + energy * 0.10f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.14f + energy * 0.10f),
            1f to Color.Transparent,
            center = Offset(w * 0.18f, h * 0.97f),
            radius = poolRadius,
        ),
        center = Offset(w * 0.18f, h * 0.97f),
        radius = poolRadius,
    )
}

/** Kept tiny so the aura can be placed without another wrapper. */
@Composable
fun LivingAura(
    color: Color,
    visuals: State<AudioVisuals>,
    tier: VisualTier,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        drawLivingAura(color, visuals.value, tier)
    }
}
