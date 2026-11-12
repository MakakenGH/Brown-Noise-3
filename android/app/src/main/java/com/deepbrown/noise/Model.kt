package com.deepbrown.noise

/** The four noise colours the app can generate. */
enum class NoiseColor(val label: String) {
    WHITE("White"),
    BROWN("Brown"),
    PINK("Pink"),
    GREEN("Green");

    companion object {
        fun fromKey(key: String?): NoiseColor =
            entries.firstOrNull { it.name == key } ?: BROWN
    }
}

/** A sound saved in the collection. Volume is global and lives on [Params]. */
data class SavedSound(
    val id: String,
    val name: String,
    val color: NoiseColor,
    val deep: Float,
    val rumble: Float,
    val width: Float,
    val swell: Float,
    val texture: Float = 0f,
    val air: Float = 0f,
    val drift: Float = 0f,
) {
    fun params(volume: Float): Params = Params(
        deep = deep,
        rumble = rumble,
        width = width,
        swell = swell,
        volume = volume,
        color = color,
        texture = texture,
        air = air,
        drift = drift,
    )
}

/** A sound being edited in the Noise Generator before it is saved. */
data class Draft(
    val name: String = "",
    val color: NoiseColor = NoiseColor.WHITE,
    val deep: Float = 0.78f,
    val rumble: Float = 0.20f,
    val width: Float = 0.20f,
    val swell: Float = 0.0f,
    val texture: Float = 0.0f,
    val air: Float = 0f,
    val drift: Float = 0.0f,
) {
    fun params(volume: Float): Params = Params(
        deep = deep,
        rumble = rumble,
        width = width,
        swell = swell,
        volume = volume,
        color = color,
        texture = texture,
        air = air,
        drift = drift,
    )

    companion object {
        fun from(sound: SavedSound): Draft = Draft(
            name = sound.name,
            color = sound.color,
            deep = sound.deep,
            rumble = sound.rumble,
            width = sound.width,
            swell = sound.swell,
            texture = sound.texture,
            air = sound.air,
            drift = sound.drift,
        )
    }
}

/** The sound a quick-noise colour plays. It is never added to the collection. */
fun quickParams(color: NoiseColor, volume: Float): Params = when (color) {
    NoiseColor.WHITE -> Params(
        deep = 0.05f, rumble = 0.15f, width = 0.55f, swell = 0f, volume = volume, color = color,
        texture = 0f, air = 0f, drift = 0f,
    )
    NoiseColor.PINK -> Params(
        deep = 0.10f, rumble = 0.20f, width = 0.60f, swell = 0f, volume = volume, color = color,
        texture = 0f, air = 0f, drift = 0f,
    )
    NoiseColor.GREEN -> Params(
        deep = 0.0f, rumble = 0.10f, width = 0.65f, swell = 0f, volume = volume, color = color,
        texture = 0f, air = 0f, drift = 0f,
    )
    NoiseColor.BROWN -> Params(
        deep = 0.50f, rumble = 0.35f, width = 0.60f, swell = 0.10f, volume = volume, color = color,
        texture = 0f, air = 0f, drift = 0f,
    )
}
