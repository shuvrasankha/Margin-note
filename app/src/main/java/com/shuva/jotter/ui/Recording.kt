package com.shuva.jotter.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shuva.jotter.MainActivity
import com.shuva.jotter.R
import com.shuva.jotter.data.MAX_RECORDING_MS
import com.shuva.jotter.data.clock
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
 * The one voice recording, kept outside the screen so it carries on while Jotter is in the background
 * ([RecordingService] keeps the process allowed to use the microphone and shows Pause and Stop).
 */
object Recorder {
    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state
    private var recorder: MediaRecorder? = null
    private var app: Context? = null
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
            app = context.applicationContext
            _state.value = RecState(active = true, runStart = SystemClock.elapsedRealtime())
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
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
        RecordingService.refresh()
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
        app?.let { it.stopService(Intent(it, RecordingService::class.java)) }
    }

    internal fun context() = app
}

/** Keeps a recording going in the background, with a notification to pause, resume or stop it. */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> Recorder.togglePause()
            ACTION_STOP -> Recorder.stop()
            else -> {
                val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this), type)
            }
        }
        if (!Recorder.state.value.active) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_PAUSE = "com.shuva.jotter.PAUSE_RECORDING"
        private const val ACTION_STOP = "com.shuva.jotter.STOP_RECORDING"

        /** Shows the current paused / recording state. */
        fun refresh() {
            val c = Recorder.context() ?: return
            if (!Recorder.state.value.active) return
            runCatching { NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, notification(c)) } // no POST_NOTIFICATIONS: nothing shown
        }

        private fun action(c: Context, action: String, code: Int) = PendingIntent.getService(
            c, code, Intent(c, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun notification(c: Context): android.app.Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                c.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL, "Voice recording", NotificationManager.IMPORTANCE_LOW),
                )
            }
            val s = Recorder.state.value
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val b = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_recording)
                .setContentTitle(if (s.paused) "Recording paused" else "Recording a voice note")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, if (s.paused) "Resume" else "Pause", action(c, ACTION_PAUSE, 1))
                .addAction(0, "Stop", action(c, ACTION_STOP, 2))
            if (s.paused) b.setContentText(clock(s.banked))
            // The system counts the time up while recording; it can't pause, so a paused recording shows the fixed time instead.
            else b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - s.elapsed(SystemClock.elapsedRealtime()))
            return b.build()
        }
    }
}
