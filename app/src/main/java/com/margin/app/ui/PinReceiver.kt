package com.margin.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.data.NoteRepository
import com.margin.app.data.PinContent
import com.margin.app.data.PinnedNotes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A note pinned to the shade: ticks its next task, or unpins it (also when the notification is swiped away). */
@AndroidEntryPoint
class PinReceiver : BroadcastReceiver() {
    @Inject lateinit var pinned: PinnedNotes
    @Inject lateinit var repo: NoteRepository

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE) ?: return
        when (intent.action) {
            ACTION_UNPIN -> pinned.unpin(noteId)
            ACTION_TICK -> {
                val blockId = intent.getStringExtra(EXTRA_BLOCK) ?: return
                val done = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Only ever ticks: a second tap on a stale button leaves the task done.
                        if (repo.getNote(noteId)?.blocks?.any { it.id == blockId && !it.checked } == true) repo.toggleTask(noteId, blockId)
                        // The app's note watcher updates the notification; when it isn't running, do it here.
                        repo.getNote(noteId)?.let { pinned.refresh(listOf(it)) }
                    } finally {
                        done.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "com.margin.app.PIN_TICK"
        private const val ACTION_UNPIN = "com.margin.app.PIN_UNPIN"
        private const val EXTRA_NOTE = "note"
        private const val EXTRA_BLOCK = "block"
        private const val CHANNEL = "pinned_notes"
        private const val ID = 11 // tagged with the note id

        fun post(c: Context, noteId: String, folderId: String, content: PinContent) {
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Pinned notes", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Notes you keep in the notification shade"
                    setShowBadge(false)
                },
            )
            val open = PendingIntent.getActivity(
                c, noteId.hashCode() + 11,
                Intent(c, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN)
                    .putExtra(RecentWidget.EXTRA_NOTE, noteId).putExtra(RecentWidget.EXTRA_FOLDER, folderId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            fun broadcast(action: String, code: Int, block: String? = null) = PendingIntent.getBroadcast(
                c, noteId.hashCode() + code,
                Intent(c, PinReceiver::class.java).setAction(action).putExtra(EXTRA_NOTE, noteId).apply { block?.let { putExtra(EXTRA_BLOCK, it) } },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val unpin = broadcast(ACTION_UNPIN, 12)
            val style = NotificationCompat.InboxStyle().also { s -> content.lines.forEach { s.addLine(it) } }
            content.summary?.let { style.setSummaryText(it) }
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(content.title)
                .setContentText(content.lines.firstOrNull())
                .setSubText(content.summary)
                .setStyle(style)
                .setContentIntent(open)
                .setDeleteIntent(unpin) // swiped away (Android 14 lets ongoing ones go): unpinned
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .apply { content.next?.let { (block, text) -> addAction(0, "✓ " + text.take(30), broadcast(ACTION_TICK, 13, block)) } }
                .addAction(0, "Unpin", unpin)
                .build()
            runCatching { NotificationManagerCompat.from(c).notify(noteId, ID, n) }
        }

        fun cancel(c: Context, noteId: String) = NotificationManagerCompat.from(c).cancel(noteId, ID)
    }
}
