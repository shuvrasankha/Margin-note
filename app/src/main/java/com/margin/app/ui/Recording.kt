package com.margin.app.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.margin.app.data.MAX_RECORDING_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Where a recording is. [doneMs] is set once it has stopped with something worth keeping. */
data class RecState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val banked: Long = 0, // time recorded before the current run
    val runStart: Long = 0, // when the current run started (elapsedRealtime)
    val doneMs: Long? = null,
) {
    /** Recorded time at [now]; pauses don't count. */
    fun elapsed(now: Long) = banked + if (paused || !active) 0 else now - runStart
}

/**
 * The one voice recording. It only runs while Margin is on screen: the recorder screen stops and saves it when
 * Margin goes to the background, so there's no foreground service (and no Play microphone declaration).
 */
object Recorder {
    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set

    /** Starts recording into [into]. False when the microphone couldn't be opened. */
    fun start(context: Context, into: File): Boolean {
        if (recorder != null) return false
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setMaxDuration(MAX_RECORDING_MS)
            r.setOutputFile(into.path)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop(maxReached = true) }
            r.prepare()
            r.start()
            recorder = r
            file = into
            _state.value = RecState(active = true, runStart = SystemClock.elapsedRealtime())
            true
        } catch (e: Exception) {
            r.release()
            false
        }
    }

    /** The microphone's peak since the last call, 0..1 (square-rooted so quiet speech shows). */
    fun level(): Float = recorder?.let { r -> kotlin.math.sqrt((runCatching { r.maxAmplitude }.getOrDefault(0) / 32767f).coerceIn(0f, 1f)) } ?: 0f

    fun togglePause() {
        val r = recorder ?: return
        val s = _state.value
        val now = SystemClock.elapsedRealtime()
        runCatching {
            if (s.paused) { r.resume(); _state.value = s.copy(paused = false, runStart = now) }
            else { r.pause(); _state.value = s.copy(paused = true, banked = s.elapsed(now)) }
        }
    }

    /** Stops and keeps the file: [RecState.doneMs] is set, or the file is dropped when nothing usable was recorded. */
    fun stop(maxReached: Boolean = false) {
        val r = recorder ?: return
        val ms = _state.value.elapsed(SystemClock.elapsedRealtime())
        // stop() throws when nothing usable was recorded (e.g. a tap straight through).
        val ok = runCatching { r.stop() }.isSuccess || maxReached
        release(r)
        val f = file
        if (ok && f != null && f.length() > 0) _state.value = RecState(doneMs = ms)
        else { f?.delete(); file = null; _state.value = RecState() }
    }

    /** Stops and throws the recording away. */
    fun discard() {
        recorder?.let { runCatching { it.stop() }; release(it) }
        file?.delete()
        file = null
        _state.value = RecState()
    }

    /** The finished recording has been taken into a note. */
    fun handedOver() { file = null; _state.value = RecState() }

    private fun release(r: MediaRecorder) {
        r.release()
        recorder = null
    }
}
