package com.deepbrown.noise

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import kotlin.math.max

/**
 * Owns the AudioTrack and the audio thread. The noise is generated continuously;
 * there is no file, loop or media element involved.
 */
class NoiseEngine(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val sampleRate: Int = 48000,
) {
    private val dsp = NoiseDsp(sampleRate)

    @Volatile private var running = false
    @Volatile private var intentionalStop = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    @Volatile private var sleepEndElapsed = 0L

    /** Called from the audio thread when playback ends without a user request. */
    var onUnexpectedStop: (() -> Unit)? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        intentionalStop = false
        running = true
        thread = Thread({ audioLoop() }, "DeepBrownAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        intentionalStop = true
        running = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) {
            try { t.join(1200) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    fun setParams(params: Params) {
        dsp.setParams(params.toDsp())
    }

    fun setMasterTarget(value: Double) {
        dsp.setMasterTarget(value)
    }

    fun setSleepEnd(elapsedRealtimeMs: Long) {
        sleepEndElapsed = elapsedRealtimeMs
        if (elapsedRealtimeMs <= 0) dsp.setSleepGain(1.0)
    }

    fun scope(): FloatArray? = if (running) dsp.copyScope(72) else null

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // Small enough that control changes are heard quickly, large enough to
        // absorb Bluetooth scheduling hiccups. About 100 ms of stereo audio.
        val bufferBytes = max(minBytes * 2, sampleRate * 2 * 2 / 10)

        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (_: Throwable) {
            running = false
            notifyUnexpectedStop()
            return
        }

        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            running = false
            notifyUnexpectedStop()
            return
        }

        track = t
        val frames = 512
        val buffer = ShortArray(frames * 2)
        try {
            t.play()
            while (running) {
                val sleep = if (sleepEndElapsed > 0L) {
                    ((sleepEndElapsed - SystemClock.elapsedRealtime()) / 30_000.0).coerceIn(0.0, 1.0)
                } else {
                    1.0
                }
                dsp.setSleepGain(sleep)
                dsp.render(buffer, frames)

                var offset = 0
                while (offset < buffer.size && running) {
                    val written = t.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        running = false
                        break
                    }
                    offset += written
                }
            }
        } catch (_: Throwable) {
            // Audio device disappeared (e.g. the last Bluetooth route).
        } finally {
            running = false
            try { t.pause() } catch (_: Throwable) {}
            try { t.flush() } catch (_: Throwable) {}
            t.release()
            if (track === t) track = null
            notifyUnexpectedStop()
        }
    }

    private fun notifyUnexpectedStop() {
        if (!intentionalStop) onUnexpectedStop?.invoke()
    }
}
