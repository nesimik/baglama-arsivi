package com.nesimi.baglamaarsivi.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Basit ve kararlı metronom: tıklamalar ses akışına örnek (sample) hassasiyetinde yazılır,
 * böylece tempo kaymaz.
 */
class Metronome {
    private val sampleRate = 44100
    @Volatile private var running = false
    @Volatile var bpm: Int = 80
    @Volatile var beatsPerBar: Int = 4
    @Volatile var accentFirst: Boolean = true
    @Volatile var volume: Float = 0.9f
    private var thread: Thread? = null
    var onBeat: ((Int) -> Unit)? = null

    private fun click(freq: Double, lengthMs: Int): ShortArray {
        val n = sampleRate * lengthMs / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 60.0)
            (sin(2 * PI * freq * t) * env * Short.MAX_VALUE * 0.95).toInt().toShort()
        }
    }

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val accent = click(1760.0, 35)
            val normal = click(1175.0, 30)
            try {
                track.play()
                var beat = 0
                while (running) {
                    track.setVolume(volume)
                    val samplesPerBeat = (sampleRate * 60.0 / bpm.coerceIn(20, 300)).toInt()
                    val isAccent = accentFirst && beat % beatsPerBar.coerceAtLeast(1) == 0
                    val c = if (isAccent) accent else normal
                    val buf = ShortArray(samplesPerBeat)
                    System.arraycopy(c, 0, buf, 0, minOf(c.size, buf.size))
                    val b = beat % beatsPerBar.coerceAtLeast(1)
                    onBeat?.invoke(b)
                    var off = 0
                    while (off < buf.size && running) {
                        val w = track.write(buf, off, minOf(2048, buf.size - off))
                        if (w <= 0) break
                        off += w
                    }
                    beat++
                }
            } catch (_: Exception) {
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        }.apply {
            name = "metronom"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread = null
    }
}
