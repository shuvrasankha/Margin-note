package com.shuva.jotter.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.shuva.jotter.MainActivity
import com.shuva.jotter.data.AppPrefs
import com.shuva.jotter.data.Repeat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The full-screen alert for a reminder, shown over the lock screen when the phone is locked or asleep:
 * the note's title, Snooze 10 min, Done, and Open note. The sound belongs to the notification, so it stops
 * when the notification goes (Snooze and Done cancel it).
 */
@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {
    @Inject lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent) {
        val noteId = intent.getStringExtra(ReminderReceiver.EXTRA_NOTE) ?: return finish()
        val folderId = intent.getStringExtra(EXTRA_FOLDER).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY)
        val repeat = intent.getStringExtra(EXTRA_REPEAT)?.let { runCatching { Repeat.valueOf(it) }.getOrNull() } ?: Repeat.NEVER
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            JotterTheme(appearance) {
                AlarmScreen(
                    title, body, repeat,
                    onSnooze = { send(ReminderReceiver.ACTION_SNOOZE, noteId); finish() },
                    onDone = { send(ReminderReceiver.ACTION_DONE, noteId); finish() },
                    onOpen = {
                        NotificationManagerCompat.from(this).cancel(noteId, ReminderReceiver.NOTIFICATION_ID)
                        startActivity(
                            Intent(this, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN)
                                .putExtra(RecentWidget.EXTRA_NOTE, noteId).putExtra(RecentWidget.EXTRA_FOLDER, folderId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        finish()
                    },
                )
            }
        }
    }

    private fun send(action: String, noteId: String) =
        sendBroadcast(Intent(this, ReminderReceiver::class.java).setAction(action).putExtra(ReminderReceiver.EXTRA_NOTE, noteId))

    companion object {
        const val EXTRA_FOLDER = "folder"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_REPEAT = "repeat"
    }
}

@Composable
private fun AlarmScreen(title: String, body: String?, repeat: Repeat, onSnooze: () -> Unit, onDone: () -> Unit, onOpen: () -> Unit) {
    val now = System.currentTimeMillis()
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().background(jc.bg).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        JIcon(Ico.BELL, jc.accent, size = 56.dp, stroke = 1.6f)
        Text(
            DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_TIME),
            Modifier.padding(top = 20.dp), style = JType.small.copy(fontSize = 44.sp), color = jc.ink,
        )
        Text(
            if (repeat == Repeat.NEVER) "Reminder" else "Reminder · ${repeat.label}",
            Modifier.padding(top = 4.dp), style = JType.secondary, color = jc.muted,
        )
        Text(
            title, Modifier.padding(top = 28.dp), style = JType.rowTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
            color = jc.ink, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        body?.let {
            Text(it, Modifier.padding(top = 8.dp), style = JType.secondary, color = jc.muted, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlarmButton("Snooze 10 min", jc.surface, jc.ink, Modifier.weight(1f), onSnooze)
            AlarmButton("Done", jc.accent, jc.bg, Modifier.weight(1f), onDone)
        }
        Box(Modifier.padding(top = 12.dp)) { TextAction("Open note", onOpen) }
    }
}

@Composable
private fun AlarmButton(label: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(container).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = JType.row.copy(fontSize = 17.sp), color = content) }
}
