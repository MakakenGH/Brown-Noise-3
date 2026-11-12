package com.deepbrown.noise

import kotlin.math.PI
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
    private var sGrain = 0.0
    private val ks: Double

    /* Parallel air layer: a high-passed white source mixed after the deep
     * filters, so Air stays independent of Deepness. */
    private val airHp = Sound.highPassCoeffs(Sound.AIR_HZ, Sound.AIR_Q, sampleRate)
    private var az1L = 0.0
    private var az2L = 0.0
    private var az1R = 0.0
    private var az2R = 0.0

    /* Parallel grain layer: a Poisson impulse train through a band-pass. */
    private val grainBp = Sound.bandPassCoeffs(Sound.GRAIN_F0, Sound.GRAIN_Q, sampleRate)
    private var gz1L = 0.0
    private var gz2L = 0.0
    private var gz1R = 0.0
    private var gz2R = 0.0
    private var grainCountL = 0
    private var grainCountR = 0
    private var grainGain = 0.0
    private var grainRng = 0

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
        grainRng = (seed xor 0x5f356495.toInt()) or 1
        driftRng = (seed xor 0x2c1b3c6d.toInt()) or 1
        kDrift = 1.0 - exp(-1.0 / (sampleRate * 2.5))
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
        val tGrain = t.texture
        val tDrift = t.drift

        if (!ready) {
            sCut = tCut
            sShelf = tShelf
            sMake = tMake
            sVol = tVol
            sSwell = tSwell
            sWidth = tWidth
            sAir = tAir
            sGrain = tGrain
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
        var yL = this.yL
        var yR = this.yR
        var sCut = this.sCut
        var sShelf = this.sShelf
        var sMake = this.sMake
        var sVol = this.sVol
        var sSwell = this.sSwell
        var sWidth = this.sWidth
        var sAir = this.sAir
        var sGrain = this.sGrain
        var az1L = this.az1L
        var az2L = this.az2L
        var az1R = this.az1R
        var az2R = this.az2R
        var gz1L = this.gz1L
        var gz2L = this.gz2L
        var gz1R = this.gz1R
        var gz2R = this.gz2R
        var grainCountL = this.grainCountL
        var grainCountR = this.grainCountR
        var grainGain = this.grainGain
        var grainRng = this.grainRng
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
        val gb0 = grainBp[0]
        val gb1 = grainBp[1]
        val gb2 = grainBp[2]
        val ga1 = grainBp[3]
        val ga2 = grainBp[4]
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
            sGrain += ks * (tGrain - sGrain)

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

            // Grain: Poisson-scheduled clicks through a band-pass. Density
            // falls as texture rises, so the layer goes from dense velvet to
            // distinct crackle; gain is density-compensated. Density and gain
            // are recalculated only when a pulse is scheduled.
            if (sGrain > 0.001) {
                grainCountL--
                var inL = 0.0
                if (grainCountL <= 0) {
                    val density = Sound.GRAIN_MAX_PPS *
                        (Sound.GRAIN_MIN_PPS / Sound.GRAIN_MAX_PPS).pow(sGrain)
                    grainGain = sGrain * Sound.GRAIN_NORM * sqrt(Sound.GRAIN_MAX_PPS / density)
                    grainRng = grainRng xor (grainRng shl 13)
                    grainRng = grainRng xor (grainRng ushr 17)
                    grainRng = grainRng xor (grainRng shl 5)
                    val u = ((grainRng.toLong() and 0xFFFFFFFFL) + 1.0) / 4294967297.0
                    grainCountL = (-ln(u) / density * sampleRate).toInt().coerceAtLeast(1)
                    inL = if ((grainRng ushr 8) and 1 == 0) 1.0 else -1.0
                }
                val gL = gb0 * inL + gz1L
                gz1L = gb1 * inL - ga1 * gL + gz2L
                gz2L = gb2 * inL - ga2 * gL

                grainCountR--
                var inR = 0.0
                if (grainCountR <= 0) {
                    val density = Sound.GRAIN_MAX_PPS *
                        (Sound.GRAIN_MIN_PPS / Sound.GRAIN_MAX_PPS).pow(sGrain)
                    grainGain = sGrain * Sound.GRAIN_NORM * sqrt(Sound.GRAIN_MAX_PPS / density)
                    grainRng = grainRng xor (grainRng shl 13)
                    grainRng = grainRng xor (grainRng ushr 17)
                    grainRng = grainRng xor (grainRng shl 5)
                    val u = ((grainRng.toLong() and 0xFFFFFFFFL) + 1.0) / 4294967297.0
                    grainCountR = (-ln(u) / density * sampleRate).toInt().coerceAtLeast(1)
                    inR = if ((grainRng ushr 8) and 1 == 0) 1.0 else -1.0
                }
                val gR = gb0 * inR + gz1R
                gz1R = gb1 * inR - ga1 * gR + gz2R
                gz2R = gb2 * inR - ga2 * gR

                xL += gL * grainGain
                xR += (sCor * gL + sInd * gR) * grainGain
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
        this.yL = yL
        this.yR = yR
        this.sCut = sCut
        this.sShelf = sShelf
        this.sMake = sMake
        this.sVol = sVol
        this.sSwell = sSwell
        this.sWidth = sWidth
        this.sAir = sAir
        this.sGrain = sGrain
        this.az1L = az1L
        this.az2L = az2L
        this.az1R = az1R
        this.az2R = az2R
        this.gz1L = gz1L
        this.gz2L = gz2L
        this.gz1R = gz1R
        this.gz2R = gz2R
        this.grainCountL = grainCountL
        this.grainCountR = grainCountR
        this.grainGain = grainGain
        this.grainRng = grainRng
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
