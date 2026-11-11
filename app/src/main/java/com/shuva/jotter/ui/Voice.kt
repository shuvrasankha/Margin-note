package com.shuva.jotter.ui

import android.media.MediaPlayer
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import com.shuva.jotter.data.Attachment
import com.shuva.jotter.data.clock
import com.shuva.jotter.data.newId
import com.shuva.jotter.data.Waveform
import com.shuva.jotter.data.loadWaveform
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

private const val BARS = 40

/**
 * Full-screen recorder, laid out like the design: Cancel and a red "Recording" label on top, a large timer,
 * a level meter from the microphone, then pause, stop-and-insert and discard. Stop (or Back) hands over the
 * file and its length; Cancel and Discard throw it away. Needs RECORD_AUDIO already granted. The recording runs in
 * [Recorder], so it carries on in the background; Stop in the notification ends it and it's added on return.
 * shortcut: leaving the note (or swiping Jotter away) while recording discards it, as before; keep it as a draft if that bites.
 */
@Composable
fun RecordingDialog(noteTitle: String, onSave: (File, Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rec by Recorder.state.collectAsState()
    val paused = rec.paused
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val levels = remember { mutableStateListOf<Float>() }
    var started by remember { mutableStateOf(false) }
    val elapsed = rec.doneMs ?: rec.elapsed(now)

    fun finish(save: Boolean) {
        if (save) Recorder.stop() else { Recorder.discard(); onDismiss() }
    }

    DisposableEffect(Unit) {
        if (Recorder.start(context, File(context.cacheDir, "rec-${newId()}.m4a"))) started = true
        else {
            Toast.makeText(context, "Couldn't start recording.", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        // Leaving without Stop or Discard (the note closed under it): discard.
        onDispose { if (Recorder.state.value.active) Recorder.discard() }
    }
    // Stopped here, from the notification, or at the length limit: hand the file over.
    LaunchedEffect(rec.doneMs, rec.active, started) {
        val ms = rec.doneMs
        val f = Recorder.file
        if (ms != null && f != null) { Recorder.handedOver(); onSave(f, ms) }
        else if (started && !rec.active && ms == null) onDismiss() // stopped with nothing usable
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (rec.active && !rec.paused) {
                levels.add(Recorder.level())
                if (levels.size > BARS) levels.removeAt(0)
            }
            delay(120)
        }
    }

    Dialog(onDismissRequest = { finish(save = true) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(80.dp)) { TextAction("Cancel", { finish(save = false) }) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (paused) jc.muted else jc.danger))
                    Spacer(Modifier.width(6.dp))
                    Text(if (paused) "Paused" else "Recording", style = JType.secondary.copy(fontWeight = FontWeight.SemiBold), color = jc.ink)
                }
                Spacer(Modifier.width(80.dp))
            }
            Text(
                clock(elapsed), Modifier.padding(top = 56.dp),
                style = TextStyle(fontFamily = Inter, fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum"),
                color = jc.ink,
            )
            if (noteTitle.isNotBlank()) {
                Text("Into \u201C$noteTitle\u201D", Modifier.padding(top = 4.dp), style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val muted = jc.switchOff
            val ink = jc.ink
            Canvas(Modifier.padding(vertical = 40.dp).fillMaxWidth().height(80.dp).semantics { contentDescription = "Sound level" }) {
                val bar = 4.dp.toPx()
                val gap = 3.dp.toPx()
                val total = BARS * bar + (BARS - 1) * gap
                val left = (size.width - total) / 2
                val minH = 8.dp.toPx()
                for (i in 0 until BARS) {
                    // Newest level on the right; bars not yet filled stay short and grey.
                    val level = levels.getOrNull(i - (BARS - levels.size))
                    val h = if (level == null) minH else minH + level * (size.height - minH)
                    drawRoundRect(
                        if (level == null) muted else ink,
                        topLeft = Offset(left + i * (bar + gap), (size.height - h) / 2), size = Size(bar, h),
                        cornerRadius = CornerRadius(bar / 2),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(if (paused) "Resume" else "Pause", { Recorder.togglePause() }) {
                    if (paused) Box(Modifier.size(14.dp).clip(CircleShape).background(jc.danger))
                    else JIcon(Ico.PAUSE, jc.ink, size = 18.dp, fill = true)
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(jc.ink).clickable(role = Role.Button) { finish(save = true) }
                        .semantics { contentDescription = "Stop and add to note" },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(jc.bg)) }
                RoundButton("Discard", { finish(save = false) }) { JIcon(Ico.TRASH, jc.ink, size = 20.dp) }
            }
            Text("Audio is added to the note", Modifier.padding(top = 16.dp), style = JType.small, color = jc.muted)
        }
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).border(1.dp, jc.line, CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * A voice note in the note: play/pause and the time played so far.
 * shortcut: keeps playing if the user leaves the app with the note open, and two notes can play at once; fine for short memos.
 */
@Composable
fun AudioCard(a: Attachment, file: File, modifier: Modifier) {
    val context = LocalContext.current
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var position by remember(file) { mutableLongStateOf(0L) }
    DisposableEffect(file) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) { position = player?.currentPosition?.toLong() ?: 0; delay(250) }
    }
    val toggle = {
        val p = player
        when {
            p == null -> try {
                player = MediaPlayer().apply {
                    setDataSource(file.path)
                    prepare()
                    setOnCompletionListener { release(); player = null; playing = false; position = 0 }
                    start()
                }
                playing = true
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't play this recording.", Toast.LENGTH_SHORT).show()
            }
            playing -> { p.pause(); playing = false }
            else -> { p.start(); playing = true }
        }
    }
    val ready = file.exists()
    val wave by produceState<Waveform?>(null, file, ready) {
        if (ready) value = withContext(Dispatchers.IO) { loadWaveform(file, context.cacheDir, WAVE_BARS) }
    }
    val duration = wave?.durationMs ?: player?.duration?.toLong()?.takeIf { it > 0 }
    Row(
        modifier.clickable(enabled = ready, role = Role.Button, onClick = toggle)
            .semantics { contentDescription = (if (playing) "Pause " else "Play ") + a.name }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (ready) jc.accent else jc.line), contentAlignment = Alignment.Center) {
            JIcon(if (playing) Ico.PAUSE else Ico.PLAY, jc.bg, size = 16.dp, fill = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, Modifier.weight(1f), style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ready) Text(
                    when {
                        player != null && duration != null -> clock(position) + " / " + clock(duration)
                        player != null -> clock(position)
                        duration != null -> clock(duration)
                        else -> ""
                    },
                    Modifier.padding(start = 8.dp), style = JType.small.copy(fontFeatureSettings = "tnum"), color = jc.muted, maxLines = 1,
                )
            }
            if (!ready) {
                Text("Not downloaded yet. It arrives with the next sync.", style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // Played part in the accent colour, the rest grey; flat bars until the recording has been read.
                val played = jc.accent
                val rest = jc.switchOff
                val progress = if (player != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    val bars = wave?.bars
                    val step = size.width / WAVE_BARS
                    val bar = (step * 0.6f).coerceAtMost(4.dp.toPx())
                    val minH = 3.dp.toPx()
                    for (i in 0 until WAVE_BARS) {
                        val level = bars?.getOrNull(i) ?: 0f
                        val h = minH + level * (size.height - minH)
                        drawRoundRect(
                            if ((i + 0.5f) / WAVE_BARS <= progress) played else rest,
                            topLeft = Offset(i * step + (step - bar) / 2, (size.height - h) / 2), size = Size(bar, h),
                            cornerRadius = CornerRadius(bar / 2),
                        )
                    }
                }
            }
        }
    }
}

/** How many bars a voice note's sound wave has. */
private const val WAVE_BARS = 48
