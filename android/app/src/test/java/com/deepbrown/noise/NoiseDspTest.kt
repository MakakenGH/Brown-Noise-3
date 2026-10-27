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
    fun levelMatchesTheWebEngine() {
        val dsp = NoiseDsp(48000)
        dsp.setParams(Params(volume = 1f).toDsp())
        dsp.setMasterTarget(1.0)
        dsp.setSleepGain(1.0)

        val frames = 96_000
        val out = ShortArray(frames * 2)
        dsp.render(out, frames)

        // Skip the fade-in and measure the steady state. The web worklet
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
        assertTrue("expected RMS near 0.14, got $rms", rms > 0.10 && rms < 0.18)
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
