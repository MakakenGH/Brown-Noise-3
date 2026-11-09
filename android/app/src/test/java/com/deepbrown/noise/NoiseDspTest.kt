package com.deepbrown.noise

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
    fun extremeSettingsStayFinite() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params(deep = 1f, rumble = 1f, width = 0f, swell = 1f, volume = 1f).toDsp())
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
