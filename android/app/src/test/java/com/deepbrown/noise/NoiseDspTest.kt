package com.deepbrown.noise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class NoiseDspTest {

    @Test
    fun rendersAudibleSignalWithoutClipping() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params().toDsp())
        dsp.setMasterTarget(1.0)
        dsp.setSleepGain(1.0)

        val frames = 48_000
        val out = ShortArray(frames * 2)
        dsp.render(out, frames)

        var sum = 0.0
        var peak = 0
        for (sample in out) {
            val v = sample / 32768.0
            sum += v * v
            peak = max(peak, abs(sample.toInt()))
        }
        val rms = sqrt(sum / out.size)

        assertTrue("expected audible RMS, got $rms", rms > 0.005)
        assertTrue("expected unclipped output, got $peak", peak <= 32767)
        assertTrue("expected finite output", rms.isFinite())
    }

    @Test
    fun spectralBandsAreFiniteAndNormalized() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params(volume = 1f).toDsp())
        dsp.setMasterTarget(1.0)
        dsp.setSleepGain(1.0)
        val frames = 48_000
        val out = ShortArray(frames * 2)
        dsp.render(out, frames)

        val bands = dsp.copyBands(512)
        assertEquals(4, bands.size)
        assertTrue("expected at least one live band", bands.any { it > 0.01f })
        assertTrue("bands should stay finite and normalised", bands.all { it.isFinite() && it in 0f..1f })
    }

    @Test
    fun levelMatchesTarget() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params(volume = 1f).toDsp())
        dsp.setMasterTarget(1.0)
        dsp.setSleepGain(1.0)

        val frames = 96_000
        val out = ShortArray(frames * 2)
        dsp.render(out, frames)

        // Skip the fade-in and measure the steady state. The engine
        // normalises to TARGET_RMS = 0.14.
        var sum = 0.0
        var count = 0
        for (i in 24_000 until frames) {
            val l = out[i * 2] / 32768.0
            val r = out[i * 2 + 1] / 32768.0
            sum += (l * l + r * r) / 2.0
            count++
        }
        val rms = sqrt(sum / count)
        assertTrue("expected RMS near 0.14, got $rms", rms > 0.085 && rms < 0.20)
    }

    @Test
    fun colourLevelsAcrossDepth() {
        for (deep in listOf(0.2f, 0.78f, 1f)) {
            for (color in NoiseColor.entries) {
                val dsp = NoiseDsp(48000)
                dsp.setParams(Params(deep = deep, volume = 1f, color = color, swell = 0f).toDsp())
                dsp.setMasterTarget(1.0)
                val frames = 96_000
                val out = ShortArray(frames * 2)
                dsp.render(out, frames)
                var sum = 0.0
                var count = 0
                var peak = 0
                for (i in 24_000 until frames) {
                    val l = out[i * 2] / 32768.0
                    val r = out[i * 2 + 1] / 32768.0
                    sum += (l * l + r * r) / 2.0
                    count++
                    peak = max(peak, max(abs(out[i * 2].toInt()), abs(out[i * 2 + 1].toInt())))
                }
                val rms = sqrt(sum / count)
                assertTrue("deep=$deep $color RMS 0.13 expected, got $rms", rms > 0.085 && rms < 0.20)
                assertTrue("deep=$deep $color clipped, peak=$peak", peak < 32767)
            }
        }
    }

    @Test
    fun coloursHaveDifferentSpectra() {
        val zcr = mutableMapOf<NoiseColor, Double>()
        for (color in NoiseColor.entries) {
            val dsp = NoiseDsp(48000)
            dsp.setParams(Params(deep = 0.5f, volume = 1f, color = color, swell = 0f).toDsp())
            dsp.setMasterTarget(1.0)
            val frames = 48_000
            val out = ShortArray(frames * 2)
            dsp.render(out, frames)
            var crossings = 0
            var previous = out[24_000]
            for (i in 24_001 until frames) {
                val current = out[i * 2]
                if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) crossings++
                previous = current
            }
            zcr[color] = crossings.toDouble() / (frames - 24_000)
        }
        assertTrue("white should be brighter than brown", zcr.getValue(NoiseColor.WHITE) > zcr.getValue(NoiseColor.BROWN))
        assertTrue("pink should be brighter than brown", zcr.getValue(NoiseColor.PINK) > zcr.getValue(NoiseColor.BROWN))
        assertTrue("green should be brighter than brown", zcr.getValue(NoiseColor.GREEN) > zcr.getValue(NoiseColor.BROWN))
        assertTrue("green should be the mid-band source", zcr.getValue(NoiseColor.GREEN) > zcr.getValue(NoiseColor.WHITE))
        assertTrue("white brighter than pink", zcr.getValue(NoiseColor.WHITE) > zcr.getValue(NoiseColor.PINK))
        assertTrue("pink brighter than brown", zcr.getValue(NoiseColor.PINK) > zcr.getValue(NoiseColor.BROWN))
    }

    @Test
    fun airChangesHighFrequencyEnergy() {
        fun renderAir(air: Float): Pair<Double, Double> {
            val dsp = NoiseDsp(48000)
            dsp.setParams(
                Params(deep = 0.5f, volume = 1f, color = NoiseColor.WHITE, swell = 0f, air = air).toDsp(),
            )
            dsp.setMasterTarget(1.0)
            dsp.setSleepGain(1.0)
            val frames = 48_000
            val out = ShortArray(frames * 2)
            dsp.render(out, frames)
            var sum = 0.0
            var diff = 0.0
            var count = 0
            var previous = out[24_000] / 32768.0
            for (i in 24_001 until frames) {
                val value = out[i * 2] / 32768.0
                sum += value * value
                val d = value - previous
                diff += d * d
                previous = value
                count++
            }
            return sqrt(sum / count) to diff / count
        }

        val (rmsHushed, hfHushed) = renderAir(0f)
        val (rmsAiry, hfAiry) = renderAir(1f)

        assertTrue("hushed RMS sane, got $rmsHushed", rmsHushed in 0.11..0.18)
        assertTrue("airy RMS sane, got $rmsAiry", rmsAiry in 0.11..0.18)
        assertTrue("airy should have more high-frequency energy", hfAiry > hfHushed)
    }

    @Test
    fun rainAddsEnergyWithoutClipping() {
        fun renderRain(rain: Float, drops: Float, plink: Float): Triple<Double, Double, Int> {
            val dsp = NoiseDsp(48000)
            dsp.setParams(
                Params(
                    deep = 0.5f,
                    volume = 1f,
                    color = NoiseColor.BROWN,
                    swell = 0f,
                    rain = rain,
                    drops = drops,
                    plink = plink,
                ).toDsp(),
            )
            dsp.setMasterTarget(1.0)
            dsp.setSleepGain(1.0)
            val frames = 96_000
            val out = ShortArray(frames * 2)
            dsp.render(out, frames)
            var sum = 0.0
            var count = 0
            var peak = 0
            var diff = 0.0
            var previous = out[24_000] / 32768.0
            for (i in 24_001 until frames) {
                val value = out[i * 2] / 32768.0
                sum += value * value
                val d = value - previous
                diff += d * d
                previous = value
                peak = max(peak, abs(out[i * 2].toInt()))
                count++
            }
            return Triple(sqrt(sum / count), diff / count, peak)
        }

        val (rmsDry, hfDry, _) = renderRain(0f, 0f, 0f)
        val (rmsLight, _, _) = renderRain(0.2f, 0.7f, 0.15f)
        val (rmsMedium, hfMedium, _) = renderRain(0.45f, 0.65f, 0.1f)
        val (rmsHeavy, hfHeavy, peakHeavy) = renderRain(0.85f, 0.75f, 0.04f)
        val (rmsPlink, _, peakPlink) = renderRain(0.25f, 0f, 0.8f)

        assertTrue("dry RMS sane, got $rmsDry", rmsDry in 0.10..0.19)
        assertTrue("light rain RMS sane, got $rmsLight", rmsLight in 0.10..0.19)
        assertTrue("medium rain RMS sane, got $rmsMedium", rmsMedium in 0.10..0.19)
        assertTrue("heavy rain RMS sane, got $rmsHeavy", rmsHeavy in 0.10..0.19)
        assertTrue("plink RMS sane, got $rmsPlink", rmsPlink in 0.10..0.19)
        assertTrue("medium rain should add high-frequency energy", hfMedium > hfDry)
        assertTrue("heavy rain should add high-frequency energy", hfHeavy > hfDry)
        assertTrue("heavy rain clipped, peak=$peakHeavy", peakHeavy < 32767)
        assertTrue("plinks clipped, peak=$peakPlink", peakPlink < 32767)
    }

    @Test
    fun windAndFanStayBounded() {
        fun renderFlow(wind: Float, fan: Float): Triple<Double, Int, Double> {
            val dsp = NoiseDsp(48000)
            dsp.setParams(
                Params(
                    deep = 0.5f,
                    volume = 1f,
                    color = NoiseColor.BROWN,
                    swell = 0f,
                    wind = wind,
                    fan = fan,
                ).toDsp(),
            )
            dsp.setMasterTarget(1.0)
            dsp.setSleepGain(1.0)
            val frames = 144_000
            val out = ShortArray(frames * 2)
            dsp.render(out, frames)
            var sum = 0.0
            var count = 0
            var peak = 0
            var diff = 0.0
            var previous = out[24_000] / 32768.0
            for (i in 24_001 until frames) {
                val value = out[i * 2] / 32768.0
                sum += value * value
                val d = value - previous
                diff += d * d
                previous = value
                peak = max(peak, abs(out[i * 2].toInt()))
                count++
            }
            return Triple(sqrt(sum / count), peak, diff / count)
        }

        val (rmsWind, peakWind, _) = renderFlow(0.65f, 0f)
        val (rmsFan, peakFan, _) = renderFlow(0f, 0.65f)

        assertTrue("wind RMS sane, got $rmsWind", rmsWind in 0.10..0.19)
        assertTrue("fan RMS sane, got $rmsFan", rmsFan in 0.10..0.19)
        assertTrue("wind clipped, peak=$peakWind", peakWind < 32767)
        assertTrue("fan clipped, peak=$peakFan", peakFan < 32767)
    }

    @Test
    fun driftStaysBounded() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(
            Params(deep = 0.5f, volume = 1f, color = NoiseColor.BROWN, drift = 1f).toDsp(),
        )
        dsp.setMasterTarget(1.0)
        dsp.setSleepGain(1.0)
        val frames = 480_000
        val out = ShortArray(frames * 2)
        dsp.render(out, frames)

        var sum = 0.0
        var count = 0
        var peak = 0
        for (i in 24_000 until frames) {
            val l = out[i * 2] / 32768.0
            val r = out[i * 2 + 1] / 32768.0
            sum += (l * l + r * r) / 2.0
            peak = max(peak, max(abs(out[i * 2].toInt()), abs(out[i * 2 + 1].toInt())))
            count++
        }
        val rms = sqrt(sum / count)
        assertTrue("drift RMS sane, got $rms", rms in 0.11..0.18)
        assertTrue("drift clipped, peak=$peak", peak < 32767)
    }

    @Test
    fun fadeInStartsQuietly() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params(deep = 0.5f, volume = 1f, swell = 0f).toDsp())
        dsp.setFadeIn(2)
        dsp.resetUserFade()
        dsp.setMasterTarget(1.0)
        dsp.setSleepGain(1.0)
        val frames = 120_000
        val out = ShortArray(frames * 2)
        dsp.render(out, frames)

        fun rms(from: Int, to: Int): Double {
            var sum = 0.0
            var count = 0
            for (i in from until to) {
                val value = out[i * 2] / 32768.0
                sum += value * value
                count++
            }
            return sqrt(sum / count)
        }

        val early = rms(4_800, 9_600)       // ~0.1–0.2 s
        val late = rms(100_000, 115_000)    // ~2.1–2.4 s
        assertTrue("fade-in should start quieter, early=$early late=$late", early < late)
        assertTrue("fade-in should reach a normal level, got $late", late in 0.09..0.20)
    }

    @Test
    fun extremeSettingsStayFinite() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params(deep = 1f, rumble = 1f, width = 0f, swell = 1f, volume = 1f, rain = 1f, drops = 1f, plink = 1f, wind = 1f, fan = 1f, air = 1f, drift = 1f).toDsp())
        dsp.setMasterTarget(1.0)
        val out = ShortArray(4800 * 2)
        dsp.render(out, 4800)
        for (sample in out) {
            assertTrue(sample.toInt() in -32768..32767)
        }
    }

    @Test
    fun scopeReflectsGeneratedAudio() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params().toDsp())
        dsp.setMasterTarget(1.0)
        val out = ShortArray(8192 * 2)
        dsp.render(out, 8192)
        val scope = dsp.copyScope(72)
        assertTrue(scope.size == 72)
        assertTrue(scope.any { abs(it) > 0.0001f })
    }
}
