package com.deepbrown.noise

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The brown-noise generator and filter chain.
 *
 * It is pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 * [render] is called from one audio thread only; parameter setters may be
 * called from other threads and are read once per block.
 */
class NoiseDsp(private val sampleRate: Int = 48000) {
    private val twoPi = 2.0 * PI
    private val qButter = sqrt(0.5)
    private val qPeak = 10.0.pow(1.5 / 20.0)

    private var a: Int
    private var b: Int
    private var yL = 0.0
    private var yR = 0.0

    private val p: Double
    private val g: Double

    private val c = DoubleArray(20)
    private val z = DoubleArray(16)

    private val shelfCos: Double
    private val shelfSin: Double

    private var ready = false
    private var sCut = 0.0
    private var sShelf = 0.0
    private var sMake = 1.0
    private var sVol = 0.0
    private var sSwell = 0.0
    private var sWidth = 0.6
    private var sAir = 0.0
    private var sRain = 0.0
    private var sDrops = 0.0
    private var sPlink = 0.0
    private var sWind = 0.0
    private var sFan = 0.0
    private val ks: Double

    /* Parallel air layer: a high-passed white source mixed after the deep
     * filters, so Air stays independent of Deepness. */
    private val airHp = Sound.highPassCoeffs(Sound.AIR_HZ, Sound.AIR_Q, sampleRate)
    private var az1L = 0.0
    private var az2L = 0.0
    private var az1R = 0.0
    private var az2R = 0.0

    /* Rain bed: a pink source through a time-varying high-pass and a fixed
     * low-pass, per channel. */
    private val rainBedHp = DoubleArray(5)
    private val rainBedLp = Sound.lowPassCoeffs(Sound.RAIN_BED_LP_HZ, qButter, sampleRate)
    private var lastRainBedHz = 0.0
    private var rhp1L = 0.0
    private var rhp2L = 0.0
    private var rhp1R = 0.0
    private var rhp2R = 0.0
    private var rlp1L = 0.0
    private var rlp2L = 0.0
    private var rlp1R = 0.0
    private var rlp2R = 0.0
    private var rpk0L = 0.0
    private var rpk1L = 0.0
    private var rpk2L = 0.0
    private var rpk0R = 0.0
    private var rpk1R = 0.0
    private var rpk2R = 0.0

    /* Rain drops: fixed bands and short noise-burst voices. */
    private val rainBands = Array(Sound.RAIN_BANDS_HZ.size) {
        Sound.bandPassCoeffs(Sound.RAIN_BANDS_HZ[it], Sound.RAIN_DROP_Q, sampleRate)
    }
    private val rainBandZ = DoubleArray(Sound.RAIN_BANDS_HZ.size * 4)
    private val rainBandInL = DoubleArray(Sound.RAIN_BANDS_HZ.size)
    private val rainBandInR = DoubleArray(Sound.RAIN_BANDS_HZ.size)
    private val rainVoiceEnv = DoubleArray(Sound.RAIN_VOICES)
    private val rainVoiceDecay = DoubleArray(Sound.RAIN_VOICES)
    private val rainVoiceBand = IntArray(Sound.RAIN_VOICES)
    private val rainVoicePanL = DoubleArray(Sound.RAIN_VOICES)
    private val rainVoicePanR = DoubleArray(Sound.RAIN_VOICES)
    private var rainVoiceNext = 0
    private var rainDropCountdown = 0
    private var rainRng = 0
    private var rainA = 0
    private var rainB = 0

    /* Rising-pitch bubble plinks. */
    private val plinkEnv = DoubleArray(Sound.PLINK_VOICES)
    private val plinkAttackSamples = IntArray(Sound.PLINK_VOICES)
    private val plinkAttackStep = DoubleArray(Sound.PLINK_VOICES)
    private val plinkPhase = DoubleArray(Sound.PLINK_VOICES)
    private val plinkInc = DoubleArray(Sound.PLINK_VOICES)
    private val plinkStep = DoubleArray(Sound.PLINK_VOICES)
    private val plinkDecay = DoubleArray(Sound.PLINK_VOICES)
    private val plinkPanL = DoubleArray(Sound.PLINK_VOICES)
    private val plinkPanR = DoubleArray(Sound.PLINK_VOICES)
    private var plinkNext = 0
    private var plinkCountdown = 0

    /* Slow gust on the rain bed. */
    private var rainGust = 1.0
    private var rainGustTarget = 1.0
    private var rainGustCountdown = 0
    private val kGust: Double

    /* Airflow: wind and fan share a dedicated pink source. */
    private var airA = 0
    private var airB = 0
    private var airRng = 0
    private var apk0L = 0.0
    private var apk1L = 0.0
    private var apk2L = 0.0
    private var apk0R = 0.0
    private var apk1R = 0.0
    private var apk2R = 0.0

    private val windHp = Sound.highPassCoeffs(Sound.WIND_HP_HZ, qButter, sampleRate)
    private val windBandA = Sound.bandPassCoeffs(Sound.WIND_BAND_A_HZ, Sound.WIND_BAND_Q, sampleRate)
    private val windBandB = Sound.bandPassCoeffs(Sound.WIND_BAND_B_HZ, Sound.WIND_BAND_Q, sampleRate)
    private val windLp = Sound.lowPassCoeffs(Sound.WIND_LP_HZ, qButter, sampleRate)
    private var whp1L = 0.0
    private var whp2L = 0.0
    private var whp1R = 0.0
    private var whp2R = 0.0
    private var wba1L = 0.0
    private var wba2L = 0.0
    private var wba1R = 0.0
    private var wba2R = 0.0
    private var wbb1L = 0.0
    private var wbb2L = 0.0
    private var wbb1R = 0.0
    private var wbb2R = 0.0
    private var wlp1L = 0.0
    private var wlp2L = 0.0
    private var wlp1R = 0.0
    private var wlp2R = 0.0
    private var windMove = 0.5
    private var windMoveTarget = 0.5
    private var windMoveCountdown = 0
    private var windGust = 1.0
    private var windGustTarget = 1.0
    private var windGustCountdown = 0
    private val kWindMove: Double
    private val kWindGust: Double

    private val fanHp = Sound.highPassCoeffs(Sound.FAN_HP_HZ, qButter, sampleRate)
    private val fanLp = Sound.lowPassCoeffs(Sound.FAN_LP_HZ, qButter, sampleRate)
    private val fanShelf = Sound.lowShelfCoeffs(
        Sound.FAN_LOW_SHELF_HZ,
        Sound.FAN_LOW_SHELF_DB,
        sampleRate,
    )
    private var fhp1L = 0.0
    private var fhp2L = 0.0
    private var fhp1R = 0.0
    private var fhp2R = 0.0
    private var flp1L = 0.0
    private var flp2L = 0.0
    private var flp1R = 0.0
    private var flp2R = 0.0
    private var fs1L = 0.0
    private var fs2L = 0.0
    private var fs1R = 0.0
    private var fs2R = 0.0
    private var fanPhase = 0.0
    private val fanPhaseInc = twoPi * 0.28 / sampleRate

    /* Slow organic drift: random targets with a long smoothing time. */
    private var driftTarget = 0.0
    private var driftValue = 0.0
    private var driftPanTarget = 0.0
    private var driftPan = 0.0
    private var driftCountdown = 0
    private var driftRng = 0
    private val kDrift: Double

    private var phA = 0.0
    private var phB = 1.7
    private val dA = twoPi * 0.043 / sampleRate
    private val dB = twoPi * 0.117 / sampleRate
    private var n = 0
    private var sCor = 0.8
    private var sInd = 0.6

    /* Pink (Paul Kellet) and green (fixed band-pass) source state. */
    private var pk0L = 0.0
    private var pk1L = 0.0
    private var pk2L = 0.0
    private var pk0R = 0.0
    private var pk1R = 0.0
    private var pk2R = 0.0
    private val gc = Sound.greenCoeffs(sampleRate)
    private var gx1L = 0.0
    private var gx2L = 0.0
    private var gy1L = 0.0
    private var gy2L = 0.0
    private var gx1R = 0.0
    private var gx2R = 0.0
    private var gy1R = 0.0
    private var gy2R = 0.0

    @Volatile private var target: DspParams = Params().toDsp()
    private var lastTarget: DspParams? = null
    private var makeup = 1.0
    @Volatile private var masterTarget = 0.0
    @Volatile private var sleepGain = 1.0
    private var master = 0.0
    private val ksMaster: Double
    private var userFade = 1.0
    @Volatile private var userFadeStep = 0.0

    private val scopeRing = FloatArray(SCOPE_RING)
    @Volatile private var scopePos = 0

    init {
        val seed = Random.nextInt()
        a = (seed xor -0x61c88647) or 1
        b = ((seed * 1664525 + 1013904223) xor -0x7a143589) or 1
        rainRng = (seed xor 0x5f356495.toInt()) or 1
        rainA = ((seed * 22695477 + 1) xor 0x3c6ef372) or 1
        rainB = ((seed * 1103515245 + 12345) xor 0x2b7e1516) or 1
        airA = ((seed * 1664525 + 1013904223) xor 0x1b56c4e9) or 1
        airB = ((seed * 22695477 + 1) xor 0x7f4a7c15) or 1
        airRng = ((seed * 1103515245 + 12345) xor 0x4d2a6f1b) or 1
        driftRng = (seed xor 0x2c1b3c6d.toInt()) or 1
        kDrift = 1.0 - exp(-1.0 / (sampleRate * 2.5))
        kGust = 1.0 - exp(-1.0 / (sampleRate * 4.0))
        kWindMove = 1.0 - exp(-1.0 / (sampleRate * 1.5))
        kWindGust = 1.0 - exp(-1.0 / (sampleRate * 0.45))
        p = exp(-twoPi * 10.0 / sampleRate)
        g = 0.5 * sqrt(1.0 - p * p) / sqrt(1.0 / 3.0)
        setHighpass()
        shelfCos = cos(twoPi * Sound.SHELF_HZ / sampleRate)
        shelfSin = sin(twoPi * Sound.SHELF_HZ / sampleRate)
        ks = 1.0 - exp(-1.0 / (sampleRate * 0.05))
        ksMaster = 1.0 - exp(-1.0 / (sampleRate * 0.08))
        userFadeStep = 1.0 / (sampleRate * 2.0)
    }

    fun setParams(params: DspParams) {
        target = params
    }

    fun setMasterTarget(value: Double) {
        masterTarget = value.coerceIn(0.0, 1.0)
    }

    fun setSleepGain(value: Double) {
        sleepGain = value.coerceIn(0.0, 1.0)
    }

    /** Starts the gentle fade-in used when playback begins. */
    fun resetUserFade() {
        userFade = 0.0
    }

    fun setFadeIn(seconds: Int) {
        val s = if (seconds <= 0) 0.08 else seconds.toDouble()
        userFadeStep = 1.0 / (sampleRate * s)
    }

    private fun setHighpass() {
        val w0 = twoPi * Sound.HP_HZ / sampleRate
        val cs = cos(w0)
        val al = sin(w0) / (2.0 * qButter)
        val a0 = 1.0 + al
        c[0] = ((1.0 + cs) / 2.0) / a0
        c[1] = -(1.0 + cs) / a0
        c[2] = c[0]
        c[3] = (-2.0 * cs) / a0
        c[4] = (1.0 - al) / a0
    }

    private fun setCoeffs(lnCut: Double, shelfDb: Double) {
        val f = minOf(exp(lnCut), sampleRate * 0.45)
        val w0 = twoPi * f / sampleRate
        val cs = cos(w0)
        val sn = sin(w0)

        var al = sn / (2.0 * qButter)
        var a0 = 1.0 + al
        c[5] = ((1.0 - cs) / 2.0) / a0
        c[6] = (1.0 - cs) / a0
        c[7] = c[5]
        c[8] = (-2.0 * cs) / a0
        c[9] = (1.0 - al) / a0

        al = sn / (2.0 * qPeak)
        a0 = 1.0 + al
        c[10] = ((1.0 - cs) / 2.0) / a0
        c[11] = (1.0 - cs) / a0
        c[12] = c[10]
        c[13] = (-2.0 * cs) / a0
        c[14] = (1.0 - al) / a0

        val amp = 10.0.pow(shelfDb / 40.0)
        val k = 2.0 * sqrt(amp) * (shelfSin / 2.0) * sqrt(2.0)
        val s = shelfCos
        val sa0 = (amp + 1.0) + (amp - 1.0) * s + k
        c[15] = (amp * ((amp + 1.0) - (amp - 1.0) * s + k)) / sa0
        c[16] = (2.0 * amp * ((amp - 1.0) - (amp + 1.0) * s)) / sa0
        c[17] = (amp * ((amp + 1.0) - (amp - 1.0) * s - k)) / sa0
        c[18] = (-2.0 * ((amp - 1.0) + (amp + 1.0) * s)) / sa0
        c[19] = ((amp + 1.0) + (amp - 1.0) * s - k) / sa0
    }

    private fun nextDriftUnit(): Double {
        driftRng = driftRng xor (driftRng shl 13)
        driftRng = driftRng xor (driftRng ushr 17)
        driftRng = driftRng xor (driftRng shl 5)
        return ((driftRng.toLong() and 0xFFFFFFFFL) + 1.0) / 4294967297.0
    }

    private fun nextRainUnit(): Double {
        rainRng = rainRng xor (rainRng shl 13)
        rainRng = rainRng xor (rainRng ushr 17)
        rainRng = rainRng xor (rainRng shl 5)
        return ((rainRng.toLong() and 0xFFFFFFFFL) + 1.0) / 4294967297.0
    }

    private fun nextAirUnit(): Double {
        airRng = airRng xor (airRng shl 13)
        airRng = airRng xor (airRng ushr 17)
        airRng = airRng xor (airRng shl 5)
        return ((airRng.toLong() and 0xFFFFFFFFL) + 1.0) / 4294967297.0
    }

    private fun setRainBedCoeffs(hz: Double) {
        Sound.highPassCoeffsInto(rainBedHp, hz.coerceIn(50.0, 2000.0), qButter, sampleRate)
        lastRainBedHz = hz
    }

    /** Renders [frames] interleaved stereo frames into [out] (out.size >= frames * 2). */
    fun render(out: ShortArray, frames: Int) {
        require(out.size >= frames * 2)
        val t = target
        if (t !== lastTarget) {
            lastTarget = t
            // Computed here rather than on the UI thread: dragging a slider must
            // not run the loudness model on every pointer event.
            makeup = Sound.levelGain(t.cutoffHz, t.shelfDb, t.color, sampleRate)
        }
        val tCut = ln(max(t.cutoffHz, 10.0))
        val tShelf = t.shelfDb
        val tMake = makeup
        val tVol = t.volume
        val tSwell = t.swellCents
        val tWidth = t.width
        val tColor = t.color
        val tAir = t.air
        val tRain = t.rain
        val tDrops = t.drops
        val tPlink = t.plink
        val tWind = t.wind
        val tFan = t.fan
        val tDrift = t.drift

        if (!ready) {
            sCut = tCut
            sShelf = tShelf
            sMake = tMake
            sVol = tVol
            sSwell = tSwell
            sWidth = tWidth
            sAir = tAir
            sRain = tRain
            sDrops = tDrops
            sPlink = tPlink
            sWind = tWind
            sFan = tFan
            ready = true
        }

        val ks = this.ks
        val kd = this.kDrift
        val p = this.p
        val g = this.g
        val c = this.c
        val z = this.z
        var a = this.a
        var b = this.b
        var rainA = this.rainA
        var rainB = this.rainB
        var airA = this.airA
        var airB = this.airB
        var yL = this.yL
        var yR = this.yR
        var sCut = this.sCut
        var sShelf = this.sShelf
        var sMake = this.sMake
        var sVol = this.sVol
        var sSwell = this.sSwell
        var sWidth = this.sWidth
        var sAir = this.sAir
        var sRain = this.sRain
        var sDrops = this.sDrops
        var sPlink = this.sPlink
        var sWind = this.sWind
        var sFan = this.sFan
        var az1L = this.az1L
        var az2L = this.az2L
        var az1R = this.az1R
        var az2R = this.az2R
        var rainDropCountdown = this.rainDropCountdown
        var rainVoiceNext = this.rainVoiceNext
        var rainGust = this.rainGust
        var rainGustTarget = this.rainGustTarget
        var rainGustCountdown = this.rainGustCountdown
        var plinkNext = this.plinkNext
        var plinkCountdown = this.plinkCountdown
        var windMove = this.windMove
        var windMoveTarget = this.windMoveTarget
        var windMoveCountdown = this.windMoveCountdown
        var windGust = this.windGust
        var windGustTarget = this.windGustTarget
        var windGustCountdown = this.windGustCountdown
        var fanPhase = this.fanPhase
        var rh0 = rainBedHp[0]
        var rh1 = rainBedHp[1]
        var rh2 = rainBedHp[2]
        var rh3 = rainBedHp[3]
        var rh4 = rainBedHp[4]
        val rl0 = rainBedLp[0]
        val rl1 = rainBedLp[1]
        val rl2 = rainBedLp[2]
        val rl3 = rainBedLp[3]
        val rl4 = rainBedLp[4]
        var driftTarget = this.driftTarget
        var driftValue = this.driftValue
        var driftPanTarget = this.driftPanTarget
        var driftPan = this.driftPan
        var driftCountdown = this.driftCountdown
        val ah0 = airHp[0]
        val ah1 = airHp[1]
        val ah2 = airHp[2]
        val ah3 = airHp[3]
        val ah4 = airHp[4]
        var phA = this.phA
        var phB = this.phB
        var n = this.n
        var sCor = this.sCor
        var sInd = this.sInd
        var pk0L = this.pk0L
        var pk1L = this.pk1L
        var pk2L = this.pk2L
        var pk0R = this.pk0R
        var pk1R = this.pk1R
        var pk2R = this.pk2R
        var gx1L = this.gx1L
        var gx2L = this.gx2L
        var gy1L = this.gy1L
        var gy2L = this.gy2L
        var gx1R = this.gx1R
        var gx2R = this.gx2R
        var gy1R = this.gy1R
        var gy2R = this.gy2R
        var master = this.master
        val masterTarget = this.masterTarget
        val blockSleep = this.sleepGain
        var userFade = this.userFade
        val userFadeStep = this.userFadeStep
        var pos = this.scopePos

        for (i in 0 until frames) {
            sCut += ks * (tCut - sCut)
            sShelf += ks * (tShelf - sShelf)
            sMake += ks * (tMake - sMake)
            sVol += ks * (tVol - sVol)
            sSwell += ks * (tSwell - sSwell)
            sWidth += ks * (tWidth - sWidth)
            sAir += ks * (tAir - sAir)
            sRain += ks * (tRain - sRain)
            sDrops += ks * (tDrops - sDrops)
            sPlink += ks * (tPlink - sPlink)
            sWind += ks * (tWind - sWind)
            sFan += ks * (tFan - sFan)

            if (--driftCountdown <= 0) {
                driftTarget = nextDriftUnit() * 2.0 - 1.0
                driftPanTarget = nextDriftUnit() * 2.0 - 1.0
                driftCountdown = (sampleRate * (6.0 + 8.0 * nextDriftUnit())).toInt()
            }
            driftValue += kd * (driftTarget - driftValue)
            driftPan += kd * (driftPanTarget - driftPan)

            phA += dA
            if (phA > twoPi) phA -= twoPi
            phB += dB
            if (phB > twoPi) phB -= twoPi

            if ((n++ and 7) == 0) {
                val cents = sSwell * (0.65 * sin(phA) + 0.35 * sin(phB)) +
                    driftValue * tDrift * 200.0
                setCoeffs(sCut + cents * LN2 / 1200.0, sShelf)
                sInd = sWidth
                sCor = sqrt(1.0 - sWidth * sWidth)

                if (sRain > 0.001) {
                    val hz = Sound.rainBedHz(sRain.toFloat())
                    if (abs(hz - lastRainBedHz) > 0.5) {
                        setRainBedCoeffs(hz)
                        rh0 = rainBedHp[0]
                        rh1 = rainBedHp[1]
                        rh2 = rainBedHp[2]
                        rh3 = rainBedHp[3]
                        rh4 = rainBedHp[4]
                    }
                }
            }

            // xorshift32 -> one uniform white sample per channel
            a = a xor (a shl 13)
            a = a xor (a ushr 17)
            a = a xor (a shl 5)
            b = b xor (b shl 13)
            b = b xor (b ushr 17)
            b = b xor (b shl 5)
            val wL = (a.toLong() and 0xFFFFFFFFL) / 2147483648.0 - 1.0
            val wR = (b.toLong() and 0xFFFFFFFFL) / 2147483648.0 - 1.0

            // Rain uses its own white source so it adds to the body instead of
            // phase-cancelling it.
            rainA = rainA xor (rainA shl 13)
            rainA = rainA xor (rainA ushr 17)
            rainA = rainA xor (rainA shl 5)
            rainB = rainB xor (rainB shl 13)
            rainB = rainB xor (rainB ushr 17)
            rainB = rainB xor (rainB shl 5)
            val rwL = (rainA.toLong() and 0xFFFFFFFFL) / 2147483648.0 - 1.0
            val rwR = (rainB.toLong() and 0xFFFFFFFFL) / 2147483648.0 - 1.0

            // Airflow uses a third source so wind/fan stay independent of rain.
            airA = airA xor (airA shl 13)
            airA = airA xor (airA ushr 17)
            airA = airA xor (airA shl 5)
            airB = airB xor (airB shl 13)
            airB = airB xor (airB ushr 17)
            airB = airB xor (airB shl 5)
            val awL = (airA.toLong() and 0xFFFFFFFFL) / 2147483648.0 - 1.0
            val awR = (airB.toLong() and 0xFFFFFFFFL) / 2147483648.0 - 1.0

            // Colour source. All sources are normalised to roughly 0.5 RMS
            // (white is 0.577) so the loudness model can keep levels even.
            var srcL: Double
            var srcR: Double
            when (tColor) {
                NoiseColor.BROWN -> {
                    yL = p * yL + g * wL
                    yR = p * yR + g * wR
                    srcL = yL
                    srcR = yR
                }
                NoiseColor.WHITE -> {
                    srcL = wL
                    srcR = wR
                }
                NoiseColor.PINK -> {
                    pk0L = 0.99765 * pk0L + wL * 0.0990460
                    pk1L = 0.96300 * pk1L + wL * 0.2965164
                    pk2L = 0.57000 * pk2L + wL * 1.0526913
                    pk0R = 0.99765 * pk0R + wR * 0.0990460
                    pk1R = 0.96300 * pk1R + wR * 0.2965164
                    pk2R = 0.57000 * pk2R + wR * 1.0526913
                    srcL = (pk0L + pk1L + pk2L + wL * 0.1848) * Sound.PINK_GAIN
                    srcR = (pk0R + pk1R + pk2R + wR * 0.1848) * Sound.PINK_GAIN
                }
                NoiseColor.GREEN -> {
                    val yl = gc[0] * wL + gc[1] * gx1L + gc[2] * gx2L - gc[3] * gy1L - gc[4] * gy2L
                    gx2L = gx1L
                    gx1L = wL
                    gy2L = gy1L
                    gy1L = yl
                    val yr = gc[0] * wR + gc[1] * gx1R + gc[2] * gx2R - gc[3] * gy1R - gc[4] * gy2R
                    gx2R = gx1R
                    gx1R = wR
                    gy2R = gy1R
                    gy1R = yr
                    srcL = yl * Sound.GREEN_GAIN
                    srcR = yr * Sound.GREEN_GAIN
                }
            }
            var xL = srcL
            var xR = sCor * srcL + sInd * srcR

            // four biquad stages, transposed direct form II, per channel
            var s = 0
            var o = 0
            while (s < 4) {
                val b0 = c[o]
                val b1 = c[o + 1]
                val b2 = c[o + 2]
                val a1 = c[o + 3]
                val a2 = c[o + 4]
                var zi = s * 2
                var y = b0 * xL + z[zi]
                z[zi] = b1 * xL - a1 * y + z[zi + 1]
                z[zi + 1] = b2 * xL - a2 * y
                xL = y
                zi += 8
                y = b0 * xR + z[zi]
                z[zi] = b1 * xR - a1 * y + z[zi + 1]
                z[zi + 1] = b2 * xR - a2 * y
                xR = y
                s++
                o += 5
            }

            xL *= sMake
            xR *= sMake

            // Air is a parallel high-passed white layer, added after the deep
            // filters so it can restore detail that Deepness removed.
            if (sAir > 0.001) {
                val hpL = ah0 * wL + az1L
                az1L = ah1 * wL - ah3 * hpL + az2L
                az2L = ah2 * wL - ah4 * hpL
                val hpR = ah0 * wR + az1R
                az1R = ah1 * wR - ah3 * hpR + az2R
                az2R = ah2 * wR - ah4 * hpR
                val airGain = sAir * Sound.AIR_NORM
                xL += hpL * airGain
                xR += (sCor * hpL + sInd * hpR) * airGain
            }

            // Airflow: wind is a moving band-pass, fan is low-mid airflow with
            // a slow rotation. They share a dedicated pink source.
            if (sWind > 0.001 || sFan > 0.001) {
                apk0L = 0.99765 * apk0L + awL * 0.0990460
                apk1L = 0.96300 * apk1L + awL * 0.2965164
                apk2L = 0.57000 * apk2L + awL * 1.0526913
                apk0R = 0.99765 * apk0R + awR * 0.0990460
                apk1R = 0.96300 * apk1R + awR * 0.2965164
                apk2R = 0.57000 * apk2R + awR * 1.0526913
                val airflowL = (apk0L + apk1L + apk2L + awL * 0.1848) * Sound.PINK_GAIN
                val airflowR = (apk0R + apk1R + apk2R + awR * 0.1848) * Sound.PINK_GAIN

                var windL = 0.0
                var windR = 0.0
                if (sWind > 0.001) {
                    if (--windMoveCountdown <= 0) {
                        windMoveTarget = nextAirUnit()
                        windMoveCountdown = (sampleRate * (3.0 + 5.0 * nextAirUnit())).toInt()
                    }
                    windMove += kWindMove * (windMoveTarget - windMove)
                    if (--windGustCountdown <= 0) {
                        windGustTarget = 0.75 + 0.5 * nextAirUnit()
                        windGustCountdown = (sampleRate * (1.0 + 2.0 * nextAirUnit())).toInt()
                    }
                    windGust += kWindGust * (windGustTarget - windGust)
                    val move = windMove.coerceIn(0.0, 1.0)
                    val windGain = Sound.windGain(sWind.toFloat()) * windGust

                    val whL = windHp[0] * airflowL + whp1L
                    whp1L = windHp[1] * airflowL - windHp[3] * whL + whp2L
                    whp2L = windHp[2] * airflowL - windHp[4] * whL
                    val whR = windHp[0] * airflowR + whp1R
                    whp1R = windHp[1] * airflowR - windHp[3] * whR + whp2R
                    whp2R = windHp[2] * airflowR - windHp[4] * whR

                    val baL = windBandA[0] * whL + wba1L
                    wba1L = windBandA[1] * whL - windBandA[3] * baL + wba2L
                    wba2L = windBandA[2] * whL - windBandA[4] * baL
                    val bbL = windBandB[0] * whL + wbb1L
                    wbb1L = windBandB[1] * whL - windBandB[3] * bbL + wbb2L
                    wbb2L = windBandB[2] * whL - windBandB[4] * bbL
                    val baR = windBandA[0] * whR + wba1R
                    wba1R = windBandA[1] * whR - windBandA[3] * baR + wba2R
                    wba2R = windBandA[2] * whR - windBandA[4] * baR
                    val bbR = windBandB[0] * whR + wbb1R
                    wbb1R = windBandB[1] * whR - windBandB[3] * bbR + wbb2R
                    wbb2R = windBandB[2] * whR - windBandB[4] * bbR

                    val mixL = baL * (1.0 - move) + bbL * move
                    val mixR = baR * (1.0 - move) + bbR * move
                    val wlL = windLp[0] * mixL + wlp1L
                    wlp1L = windLp[1] * mixL - windLp[3] * wlL + wlp2L
                    wlp2L = windLp[2] * mixL - windLp[4] * wlL
                    val wlR = windLp[0] * mixR + wlp1R
                    wlp1R = windLp[1] * mixR - windLp[3] * wlR + wlp2R
                    wlp2R = windLp[2] * mixR - windLp[4] * wlR

                    windL = wlL * windGain
                    windR = wlR * windGain
                }

                var fanL = 0.0
                var fanR = 0.0
                if (sFan > 0.001) {
                    fanPhase += fanPhaseInc
                    if (fanPhase > twoPi) fanPhase -= twoPi
                    val wobble = 1.0 + 0.06 * sin(fanPhase)
                    val fanGain = Sound.fanGain(sFan.toFloat()) * wobble

                    val fhL = fanHp[0] * airflowL + fhp1L
                    fhp1L = fanHp[1] * airflowL - fanHp[3] * fhL + fhp2L
                    fhp2L = fanHp[2] * airflowL - fanHp[4] * fhL
                    val fhR = fanHp[0] * airflowR + fhp1R
                    fhp1R = fanHp[1] * airflowR - fanHp[3] * fhR + fhp2R
                    fhp2R = fanHp[2] * airflowR - fanHp[4] * fhR

                    val flL = fanLp[0] * fhL + flp1L
                    flp1L = fanLp[1] * fhL - fanLp[3] * flL + flp2L
                    flp2L = fanLp[2] * fhL - fanLp[4] * flL
                    val flR = fanLp[0] * fhR + flp1R
                    flp1R = fanLp[1] * fhR - fanLp[3] * flR + flp2R
                    flp2R = fanLp[2] * fhR - fanLp[4] * flR

                    val fsL = fanShelf[0] * flL + fs1L
                    fs1L = fanShelf[1] * flL - fanShelf[3] * fsL + fs2L
                    fs2L = fanShelf[2] * flL - fanShelf[4] * fsL
                    val fsR = fanShelf[0] * flR + fs1R
                    fs1R = fanShelf[1] * flR - fanShelf[3] * fsR + fs2R
                    fs2R = fanShelf[2] * flR - fanShelf[4] * fsR

                    fanL = fsL * fanGain
                    fanR = fsR * fanGain
                }

                val flowL = windL + fanL
                val flowR = windR + fanR
                val flowMid = (flowL + flowR) * 0.5
                val flowSide = (flowL - flowR) * 0.5 * sWidth
                xL += flowMid + flowSide
                xR += flowMid - flowSide
            }

            // Rain: a pink bed, short drop bursts and occasional plinks.
            if (sRain > 0.001) {
                // Dedicated pink filter so the bed is rain-like rather than flat
                // white hiss, and independent of the main body.
                rpk0L = 0.99765 * rpk0L + rwL * 0.0990460
                rpk1L = 0.96300 * rpk1L + rwL * 0.2965164
                rpk2L = 0.57000 * rpk2L + rwL * 1.0526913
                rpk0R = 0.99765 * rpk0R + rwR * 0.0990460
                rpk1R = 0.96300 * rpk1R + rwR * 0.2965164
                rpk2R = 0.57000 * rpk2R + rwR * 1.0526913
                val pinkL = (rpk0L + rpk1L + rpk2L + rwL * 0.1848) * Sound.PINK_GAIN
                val pinkR = (rpk0R + rpk1R + rpk2R + rwR * 0.1848) * Sound.PINK_GAIN

                var dropL = 0.0
                var dropR = 0.0
                if (sDrops > 0.001) {
                    rainDropCountdown--
                    if (rainDropCountdown <= 0) {
                        val density = Sound.rainDensity(sRain.toFloat(), sDrops.toFloat())
                        rainDropCountdown = (-ln(nextRainUnit()) / density * sampleRate)
                            .toInt().coerceAtLeast(1)

                        val v = rainVoiceNext
                        rainVoiceNext = (rainVoiceNext + 1) % Sound.RAIN_VOICES
                        val ampU = nextRainUnit()
                        rainVoiceEnv[v] = ampU.pow(2.5) *
                            Sound.rainDropGain(sRain.toFloat(), sDrops.toFloat())
                        val dur = 0.002 + 0.003 * nextRainUnit()
                        rainVoiceDecay[v] = exp(-1.0 / (sampleRate * dur))
                        val maxBand = (2 + (sRain * 3).toInt())
                            .coerceIn(2, Sound.RAIN_BANDS_HZ.size - 1)
                        rainVoiceBand[v] = (nextRainUnit() * (maxBand + 1))
                            .toInt().coerceIn(0, maxBand)
                        val pan = nextRainUnit() * 2.0 - 1.0
                        rainVoicePanL[v] = sqrt(0.5 * (1.0 - pan))
                        rainVoicePanR[v] = sqrt(0.5 * (1.0 + pan))
                    }

                    for (b in Sound.RAIN_BANDS_HZ.indices) {
                        rainBandInL[b] = 0.0
                        rainBandInR[b] = 0.0
                    }
                    for (v in 0 until Sound.RAIN_VOICES) {
                        val e = rainVoiceEnv[v]
                        if (e > 1e-5) {
                            rainVoiceEnv[v] = e * rainVoiceDecay[v]
                            val b = rainVoiceBand[v]
                            rainBandInL[b] += rwL * e * rainVoicePanL[v]
                            rainBandInR[b] += rwR * e * rainVoicePanR[v]
                        }
                    }

                    for (b in Sound.RAIN_BANDS_HZ.indices) {
                        val band = rainBands[b]
                        val inL = rainBandInL[b]
                        val inR = rainBandInR[b]
                        val zi = b * 4
                        val yL = band[0] * inL + rainBandZ[zi]
                        rainBandZ[zi] = band[1] * inL - band[3] * yL + rainBandZ[zi + 1]
                        rainBandZ[zi + 1] = band[2] * inL - band[4] * yL
                        val yR = band[0] * inR + rainBandZ[zi + 2]
                        rainBandZ[zi + 2] = band[1] * inR - band[3] * yR + rainBandZ[zi + 3]
                        rainBandZ[zi + 3] = band[2] * inR - band[4] * yR
                        dropL += yL
                        dropR += yR
                    }
                }

                var plinkL = 0.0
                var plinkR = 0.0
                if (sPlink > 0.001) {
                    plinkCountdown--
                    if (plinkCountdown <= 0) {
                        val rate = Sound.plinkRate(sRain.toFloat(), sPlink.toFloat())
                        if (rate > 0.0) {
                            plinkCountdown = (-ln(nextRainUnit()) / rate * sampleRate)
                                .toInt().coerceAtLeast(1)
                            val v = plinkNext
                            plinkNext = (plinkNext + 1) % Sound.PLINK_VOICES
                            val u = nextRainUnit()
                            val f0 = Sound.PLINK_F0_MIN *
                                (Sound.PLINK_F0_MAX / Sound.PLINK_F0_MIN).pow(u)
                            val rise = Sound.PLINK_RISE_MIN +
                                (Sound.PLINK_RISE_MAX - Sound.PLINK_RISE_MIN) * nextRainUnit()
                            val dur = Sound.PLINK_DURATION_MIN +
                                (Sound.PLINK_DURATION_MAX - Sound.PLINK_DURATION_MIN) * nextRainUnit()
                            val inc0 = twoPi * f0 / sampleRate
                            val inc1 = twoPi * (f0 * rise) / sampleRate
                            plinkPhase[v] = 0.0
                            plinkInc[v] = inc0
                            plinkStep[v] = (inc1 - inc0) / (sampleRate * dur)
                            plinkDecay[v] = exp(-1.0 / (sampleRate * dur))
                            val peak = Sound.PLINK_NORM * sPlink *
                                (0.35 + 0.65 * sRain) *
                                (0.4 + 0.6 * u) * (0.5 + 0.5 * nextRainUnit())
                            plinkEnv[v] = 0.0
                            val attack = (sampleRate * Sound.PLINK_ATTACK)
                                .toInt().coerceAtLeast(1)
                            plinkAttackSamples[v] = attack
                            plinkAttackStep[v] = peak / attack
                            val pan = nextRainUnit() * 2.0 - 1.0
                            plinkPanL[v] = sqrt(0.5 * (1.0 - pan))
                            plinkPanR[v] = sqrt(0.5 * (1.0 + pan))
                        } else {
                            plinkCountdown = sampleRate
                        }
                    }

                    for (p in 0 until Sound.PLINK_VOICES) {
                        val e = plinkEnv[p]
                        if (e > 1e-5 || plinkAttackSamples[p] > 0) {
                            val y = e * sin(plinkPhase[p])
                            var ph = plinkPhase[p] + plinkInc[p]
                            if (ph > twoPi) ph -= twoPi
                            plinkPhase[p] = ph
                            plinkInc[p] += plinkStep[p]
                            if (plinkAttackSamples[p] > 0) {
                                plinkEnv[p] = e + plinkAttackStep[p]
                                plinkAttackSamples[p]--
                            } else {
                                plinkEnv[p] = e * plinkDecay[p]
                            }
                            plinkL += y * plinkPanL[p]
                            plinkR += y * plinkPanR[p]
                        }
                    }
                }

                // Bed: pink through high-pass/low-pass, with a slow gust.
                val hpL = rh0 * pinkL + rhp1L
                rhp1L = rh1 * pinkL - rh3 * hpL + rhp2L
                rhp2L = rh2 * pinkL - rh4 * hpL
                val hpR = rh0 * pinkR + rhp1R
                rhp1R = rh1 * pinkR - rh3 * hpR + rhp2R
                rhp2R = rh2 * pinkR - rh4 * hpR

                val bedL = rl0 * hpL + rlp1L
                rlp1L = rl1 * hpL - rl3 * bedL + rlp2L
                rlp2L = rl2 * hpL - rl4 * bedL
                val bedR = rl0 * hpR + rlp1R
                rlp1R = rl1 * hpR - rl3 * bedR + rlp2R
                rlp2R = rl2 * hpR - rl4 * bedR

                if (--rainGustCountdown <= 0) {
                    val depth = 0.08 + 0.08 * sRain
                    rainGustTarget = 1.0 + (nextRainUnit() * 2.0 - 1.0) * depth
                    rainGustCountdown = (sampleRate * (5.0 + 4.0 * nextRainUnit())).toInt()
                }
                rainGust += kGust * (rainGustTarget - rainGust)

                val bedGain = Sound.rainBedGain(sRain.toFloat(), sDrops.toFloat()) * rainGust
                val rainL = bedL * bedGain + dropL + plinkL
                val rainR = bedR * bedGain + dropR + plinkR
                val mid = (rainL + rainR) * 0.5
                val side = (rainL - rainR) * 0.5 * sWidth
                xL += mid + side
                xR += mid - side
            }
            var m = if (xL < 0) -xL else xL
            if (m > 0.7) {
                m = 0.7 + 0.3 * tanh((m - 0.7) / 0.3)
                xL = if (xL < 0) -m else m
            }
            m = if (xR < 0) -xR else xR
            if (m > 0.7) {
                m = 0.7 + 0.3 * tanh((m - 0.7) / 0.3)
                xR = if (xR < 0) -m else m
            }

            master += ksMaster * (masterTarget - master)
            if (userFade < 1.0) userFade = minOf(1.0, userFade + userFadeStep)
            val gain = master * blockSleep
            val driftGain = 1.0 + driftValue * tDrift * 0.04
            val pan = driftPan * tDrift * 0.06
            val outL = xL * sVol * gain * driftGain * (1.0 - pan) * userFade
            val outR = xR * sVol * gain * driftGain * (1.0 + pan) * userFade

            out[i * 2] = toPcm(outL)
            out[i * 2 + 1] = toPcm(outR)

            scopeRing[pos] = (outL + outR).toFloat() * 0.5f
            pos++
            if (pos == SCOPE_RING) pos = 0
        }

        this.a = a
        this.b = b
        this.rainA = rainA
        this.rainB = rainB
        this.airA = airA
        this.airB = airB
        this.yL = yL
        this.yR = yR
        this.sCut = sCut
        this.sShelf = sShelf
        this.sMake = sMake
        this.sVol = sVol
        this.sSwell = sSwell
        this.sWidth = sWidth
        this.sAir = sAir
        this.sRain = sRain
        this.sDrops = sDrops
        this.sPlink = sPlink
        this.sWind = sWind
        this.sFan = sFan
        this.az1L = az1L
        this.az2L = az2L
        this.az1R = az1R
        this.az2R = az2R
        this.rainDropCountdown = rainDropCountdown
        this.rainVoiceNext = rainVoiceNext
        this.rainGust = rainGust
        this.rainGustTarget = rainGustTarget
        this.rainGustCountdown = rainGustCountdown
        this.plinkNext = plinkNext
        this.plinkCountdown = plinkCountdown
        this.windMove = windMove
        this.windMoveTarget = windMoveTarget
        this.windMoveCountdown = windMoveCountdown
        this.windGust = windGust
        this.windGustTarget = windGustTarget
        this.windGustCountdown = windGustCountdown
        this.fanPhase = fanPhase
        this.driftTarget = driftTarget
        this.driftValue = driftValue
        this.driftPanTarget = driftPanTarget
        this.driftPan = driftPan
        this.driftCountdown = driftCountdown
        this.phA = phA
        this.phB = phB
        this.n = n
        this.sCor = sCor
        this.sInd = sInd
        this.pk0L = pk0L
        this.pk1L = pk1L
        this.pk2L = pk2L
        this.pk0R = pk0R
        this.pk1R = pk1R
        this.pk2R = pk2R
        this.gx1L = gx1L
        this.gx2L = gx2L
        this.gy1L = gy1L
        this.gy2L = gy2L
        this.gx1R = gx1R
        this.gx2R = gx2R
        this.gy1R = gy1R
        this.gy2R = gy2R
        this.master = master
        this.userFade = userFade
        this.scopePos = pos
    }

    /** 72 points spanning the most recent scope buffer, for the waveform UI. */
    fun copyScope(points: Int = 72): FloatArray {
        val out = FloatArray(points)
        val step = SCOPE_RING / points
        val start = scopePos
        for (i in 0 until points) {
            var sum = 0.0
            val base = (start + i * step) % SCOPE_RING
            for (j in 0 until step) sum += scopeRing[(base + j) % SCOPE_RING]
            out[i] = (sum / step).toFloat()
        }
        return out
    }

    private fun toPcm(value: Double): Short {
        val v = when {
            value > 1.0 -> 1.0
            value < -1.0 -> -1.0
            else -> value
        }
        return (v * 32767.0).toInt().toShort()
    }

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)

    companion object {
        private const val SCOPE_RING = 2048
        private const val LN2 = 0.6931471805599453
    }
}
