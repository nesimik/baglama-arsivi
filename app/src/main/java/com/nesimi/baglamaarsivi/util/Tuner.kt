package com.nesimi.baglamaarsivi.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PitchReading(
    val frequency: Double,
    val noteName: String,      // ör. "La"
    val letter: String,        // ör. "A"
    val octave: Int,
    val cents: Int,            // -50..+50
    val level: Float           // 0..1 ses seviyesi
)

/**
 * Kromatik akort aleti: mikrofondan sesi alır, YIN algoritmasıyla perde bulur.
 * Bağlama telleri dahil her enstrümanda çalışır.
 */
class Tuner {
    private val sampleRate = 44100
    private val bufferSize = 4096
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile var referenceA4: Double = 440.0
    var onReading: ((PitchReading?) -> Unit)? = null

    val isRunning get() = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, bufferSize * 2))
            } catch (_: Exception) {
                running = false
                return@Thread
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { running = false; rec.release(); return@Thread }
            val shorts = ShortArray(bufferSize)
            val floats = FloatArray(bufferSize)
            val smooth = ArrayDeque<Double>()
            try {
                rec.startRecording()
                while (running) {
                    var read = 0
                    while (read < bufferSize && running) {
                        val r = rec.read(shorts, read, bufferSize - read)
                        if (r <= 0) break
                        read += r
                    }
                    if (read < bufferSize) continue
                    var sum = 0.0
                    for (i in 0 until bufferSize) { val f = shorts[i] / 32768f; floats[i] = f; sum += f * f }
                    val rms = sqrt(sum / bufferSize)
                    val level = (rms * 8).toFloat().coerceIn(0f, 1f)
                    if (rms < 0.008) { smooth.clear(); onReading?.invoke(null); continue }
                    val f = yin(floats)
                    if (f == null || f < 40 || f > 1500) { onReading?.invoke(null); continue }
                    smooth.addLast(f); while (smooth.size > 4) smooth.removeFirst()
                    val avg = smooth.sorted()[smooth.size / 2] // medyan: titremeyi azaltır
                    onReading?.invoke(toReading(avg, level))
                }
            } catch (_: Exception) {
            } finally {
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
            }
        }.apply { name = "akort"; start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    private val solfej = arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    private val letters = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun toReading(freq: Double, level: Float): PitchReading {
        val midi = 69 + 12 * log2(freq / referenceA4)
        val nearest = midi.roundToInt()
        val cents = ((midi - nearest) * 100).roundToInt().coerceIn(-50, 50)
        val idx = ((nearest % 12) + 12) % 12
        return PitchReading(freq, solfej[idx], letters[idx], nearest / 12 - 1, cents, level)
    }

    fun noteFrequency(midi: Int): Double = referenceA4 * 2.0.pow((midi - 69) / 12.0)

    /** YIN perde bulma (de Cheveigné & Kawahara, 2002) */
    private fun yin(buf: FloatArray): Double? {
        val half = buf.size / 2
        val d = FloatArray(half)
        for (tau in 1 until half) {
            var s = 0f
            for (i in 0 until half) { val x = buf[i] - buf[i + tau]; s += x * x }
            d[tau] = s
        }
        // cumulative mean normalized difference
        d[0] = 1f
        var running = 0f
        for (tau in 1 until half) {
            running += d[tau]
            d[tau] = if (running == 0f) 1f else d[tau] * tau / running
        }
        val threshold = 0.12f
        var tau = 2
        val maxTau = sampleRate / 40
        while (tau < half && tau < maxTau) {
            if (d[tau] < threshold) {
                while (tau + 1 < half && d[tau + 1] < d[tau]) tau++
                break
            }
            tau++
        }
        if (tau >= half - 1 || tau >= maxTau || d[tau] >= threshold) return null
        // parabolik ara değerleme
        val s0 = d[tau - 1]; val s1 = d[tau]; val s2 = d[tau + 1]
        val denom = 2 * (2 * s1 - s2 - s0)
        val better = if (abs(denom) > 1e-9f) tau + (s2 - s0) / denom else tau.toFloat()
        return sampleRate / better.toDouble()
    }
}
