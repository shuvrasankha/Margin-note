package com.shuva.jotter.data

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** A recording's shape for drawing: [bars] heights from 0 to 1, and its length. */
@Serializable
data class Waveform(val bars: List<Float>, val durationMs: Long)

/**
 * The loudest moment in each slice, scaled so the loudest slice is 1. Square-rooted, like the recorder's
 * level meter, so quiet speech still shows. Silence stays flat.
 */
fun normalizeBars(peaks: FloatArray): List<Float> {
    val top = peaks.maxOrNull()?.takeIf { it > 0f } ?: return peaks.map { 0f }
    return peaks.map { sqrt((it / top).coerceIn(0f, 1f)) }
}

/** The waveform of [audio], cached in [cacheDir] by its file name (recordings never change). Null if it can't be read. Call off the main thread. */
fun loadWaveform(audio: File, cacheDir: File, bars: Int): Waveform? {
    val cached = File(File(cacheDir, "waveforms").apply { mkdirs() }, audio.name + ".$bars.json")
    runCatching { return JotterJson.decodeFromString<Waveform>(cached.readText()) }
    val wave = runCatching { decodeWaveform(audio, bars) }.getOrNull() ?: return null
    runCatching { cached.writeText(JotterJson.encodeToString(wave)) }
    return wave
}

/** Decodes [file]'s first audio track and records the loudest sample in each of [bars] equal slices of time. */
private fun decodeWaveform(file: File, bars: Int): Waveform? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else return null
        if (durationUs <= 0) return null
        extractor.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        val peaks = FloatArray(bars)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var float = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    float = out.containsKey(MediaFormat.KEY_PCM_ENCODING) && out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    var peak = 0f
                    if (float) { val f = buf.asFloatBuffer(); while (f.hasRemaining()) peak = maxOf(peak, abs(f.get())) }
                    else { val s = buf.asShortBuffer(); while (s.hasRemaining()) peak = maxOf(peak, abs(s.get().toFloat()) / 32768f) }
                    val slot = (info.presentationTimeUs * bars / durationUs).toInt().coerceIn(0, bars - 1)
                    peaks[slot] = maxOf(peaks[slot], peak)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return Waveform(normalizeBars(peaks), durationUs / 1000)
    } finally {
        extractor.release()
    }
}
