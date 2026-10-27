package com.deepbrown.noise

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The five user-facing sound controls. */
data class Params(
    val deep: Float = 0.50f,
    val rumble: Float = 0.35f,
    val width: Float = 0.60f,
    val swell: Float = 0.10f,
    val volume: Float = 0.55f,
) {
    fun clamped(): Params = Params(
        deep = deep.coerceIn(0f, 1f),
        rumble = rumble.coerceIn(0f, 1f),
        width = width.coerceIn(0f, 1f),
        swell = swell.coerceIn(0f, 1f),
        volume = volume.coerceIn(0f, 1f),
    )

    fun with(key: String, value: Float): Params = when (key) {
        "deep" -> copy(deep = value)
        "rumble" -> copy(rumble = value)
        "width" -> copy(width = value)
        "swell" -> copy(swell = value)
        "volume" -> copy(volume = value)
        else -> this
    }
}

data class Preset(
    val id: String,
    val name: String,
    val deep: Float,
    val rumble: Float,
    val width: Float,
    val swell: Float,
) {
    fun applyTo(p: Params): Params = p.copy(deep = deep, rumble = rumble, width = width, swell = swell)
}

object Presets {
    val all: List<Preset> = listOf(
        Preset("velvet", "Velvet", 0.50f, 0.35f, 0.60f, 0.10f),
        Preset("deep-sleep", "Deep sleep", 0.78f, 0.50f, 0.70f, 0.15f),
        Preset("abyss", "Abyss", 0.97f, 0.75f, 0.45f, 0.05f),
        Preset("engine-room", "Engine room", 0.30f, 0.60f, 0.30f, 0.00f),
        Preset("tide", "Tide", 0.62f, 0.40f, 0.90f, 0.65f),
        Preset("focus", "Focus", 0.20f, 0.15f, 0.55f, 0.00f),
    )

    fun byId(id: String?): Preset? = all.firstOrNull { it.id == id }

    /** Same tolerance as the web app: a preset is active only when all four shape controls match. */
    fun match(p: Params): Preset? = all.firstOrNull {
        abs(p.deep - it.deep) < 0.006f &&
            abs(p.rumble - it.rumble) < 0.006f &&
            abs(p.width - it.width) < 0.006f &&
            abs(p.swell - it.swell) < 0.006f
    }
}

/** Parameters in the units the audio thread wants. */
data class DspParams(
    val cutoffHz: Double,
    val shelfDb: Double,
    val width: Double,
    val swellCents: Double,
    val volume: Double,
)

object Sound {
    const val SHELF_HZ = 90.0
    const val HP_HZ = 18.0
    const val TARGET_RMS = 0.14

    fun cutoffHz(deep: Float): Double = 900.0 * (40.0 / 900.0).pow(deep.toDouble())
    fun shelfDb(rumble: Float): Double = rumble.toDouble() * 12.0
    fun swellCents(swell: Float): Double = swell.toDouble() * 700.0
    fun volumeGain(volume: Float): Double = volume.toDouble() * volume.toDouble()

    /* Loudness model: the RMS of the filtered noise from the filters' exact
     * frequency responses, so the level stays even while depth and rumble
     * change. Ported one-to-one from noise-clip.js. */
    fun levelGain(cut: Double, shelf: Double, fs: Int = 48000): Double {
        val filters = filterSet(cut, shelf, fs)
        val a = exp(-2.0 * PI * 10.0 / fs)
        val n = 360
        val f0 = 0.1
        val f1 = fs / 2.0 * 0.999
        val dl = ln(f1 / f0) / (n - 1)
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val f = f0 * exp(i * dl)
            val w = 2.0 * PI * f / fs
            val psd = 1.0 / (1.0 - 2.0 * a * cos(w) + a * a)
            var h = 1.0
            for (c in filters) h *= mag2(c, w)
            num += psd * h * f
            den += psd * f
        }
        val rmsPre = 0.5 * sqrt(num / den)
        return (TARGET_RMS / rmsPre).coerceIn(0.05, 14.0)
    }

    private const val Q_BUTTER_DB = -3.0103
    private const val Q_LP2_DB = 1.5

    private fun filterSet(cut: Double, shelf: Double, fs: Int): List<DoubleArray> = listOf(
        coeffs("highpass", HP_HZ, Q_BUTTER_DB, 0.0, fs),
        coeffs("lowpass", cut, Q_BUTTER_DB, 0.0, fs),
        coeffs("lowpass", cut, Q_LP2_DB, 0.0, fs),
        coeffs("lowshelf", SHELF_HZ, 0.0, shelf, fs),
    )

    private fun coeffs(type: String, f0: Double, qDb: Double, gDb: Double, fs: Int): DoubleArray {
        val w0 = 2.0 * PI * f0 / fs
        val cosW = cos(w0)
        val sinW = sin(w0)
        var b0: Double
        var b1: Double
        var b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        if (type == "lowpass" || type == "highpass") {
            val alpha = sinW / (2.0 * 10.0.pow(qDb / 20.0))
            a0 = 1.0 + alpha
            a1 = -2.0 * cosW
            a2 = 1.0 - alpha
            if (type == "lowpass") {
                b0 = (1.0 - cosW) / 2.0
                b1 = 1.0 - cosW
                b2 = b0
            } else {
                b0 = (1.0 + cosW) / 2.0
                b1 = -(1.0 + cosW)
                b2 = b0
            }
        } else {
            val amp = 10.0.pow(gDb / 40.0)
            val k = 2.0 * sqrt(amp) * (sinW / 2.0) * sqrt(2.0)
            b0 = amp * ((amp + 1.0) - (amp - 1.0) * cosW + k)
            b1 = 2.0 * amp * ((amp - 1.0) - (amp + 1.0) * cosW)
            b2 = amp * ((amp + 1.0) - (amp - 1.0) * cosW - k)
            a0 = (amp + 1.0) + (amp - 1.0) * cosW + k
            a1 = -2.0 * ((amp - 1.0) + (amp + 1.0) * cosW)
            a2 = (amp + 1.0) + (amp - 1.0) * cosW - k
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun mag2(c: DoubleArray, w: Double): Double {
        val cw = cos(w)
        val sw = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)
        val nr = c[0] + c[1] * cw + c[2] * c2
        val ni = -(c[1] * sw + c[2] * s2)
        val dr = 1.0 + c[3] * cw + c[4] * c2
        val di = -(c[3] * sw + c[4] * s2)
        return (nr * nr + ni * ni) / (dr * dr + di * di)
    }
}

fun Params.toDsp(): DspParams {
    val cut = Sound.cutoffHz(deep)
    val shelf = Sound.shelfDb(rumble)
    return DspParams(
        cutoffHz = cut,
        shelfDb = shelf,
        width = width.toDouble(),
        swellCents = Sound.swellCents(swell),
        volume = Sound.volumeGain(volume),
    )
}
