package com.deepbrown.noise

import kotlin.random.Random

/** A named starting point for the editor. Tapping one only fills the draft. */
data class Preset(
    val name: String,
    val color: NoiseColor,
    val deep: Float,
    val rumble: Float,
    val width: Float,
    val swell: Float,
    val rain: Float,
    val drops: Float,
    val plink: Float,
    val air: Float,
    val drift: Float,
)

val Presets: List<Preset> = listOf(
    Preset("Drizzle", NoiseColor.PINK, 0.30f, 0.10f, 0.80f, 0.08f, 0.20f, 0.70f, 0.15f, 0.00f, 0.15f),
    Preset("Rain", NoiseColor.PINK, 0.30f, 0.15f, 0.85f, 0.10f, 0.45f, 0.65f, 0.10f, 0.00f, 0.20f),
    Preset("Downpour", NoiseColor.BROWN, 0.35f, 0.50f, 0.95f, 0.05f, 0.85f, 0.75f, 0.04f, 0.00f, 0.25f),
    Preset("Plinks", NoiseColor.PINK, 0.20f, 0.05f, 0.80f, 0.05f, 0.15f, 0.30f, 0.90f, 0.00f, 0.10f),
    Preset("Still", NoiseColor.BROWN, 0.82f, 0.35f, 0.55f, 0.05f, 0.00f, 0.00f, 0.00f, 0.10f, 0.05f),
    Preset("Ocean", NoiseColor.BROWN, 0.55f, 0.45f, 0.90f, 0.35f, 0.00f, 0.00f, 0.00f, 0.25f, 0.50f),
    Preset("Fan", NoiseColor.BROWN, 0.55f, 0.25f, 0.45f, 0.00f, 0.00f, 0.00f, 0.00f, 0.35f, 0.00f),
    Preset("Night", NoiseColor.GREEN, 0.30f, 0.15f, 0.85f, 0.10f, 0.00f, 0.00f, 0.00f, 0.45f, 0.30f),
    Preset("Focus", NoiseColor.PINK, 0.38f, 0.15f, 0.30f, 0.00f, 0.00f, 0.00f, 0.00f, 0.65f, 0.00f),
)

fun Preset.toDraft(name: String = ""): Draft = Draft(
    name = name,
    color = color,
    deep = deep,
    rumble = rumble,
    width = width,
    swell = swell,
    rain = rain,
    drops = drops,
    plink = plink,
    air = air,
    drift = drift,
)

/** A pleasant random starting point, kept away from the harsh extremes. */
fun randomDraft(): Draft = Draft(
    color = NoiseColor.entries.random(),
    deep = 0.2f + Random.nextFloat() * 0.65f,
    rumble = 0.05f + Random.nextFloat() * 0.50f,
    width = 0.25f + Random.nextFloat() * 0.70f,
    swell = Random.nextFloat() * 0.40f,
    rain = Random.nextFloat() * 0.60f,
    drops = 0.30f + Random.nextFloat() * 0.60f,
    plink = Random.nextFloat() * 0.40f,
    air = 0.25f + Random.nextFloat() * 0.50f,
    drift = Random.nextFloat() * 0.35f,
)
