package com.margin.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.data.NoteRepository
import com.margin.app.data.Reminders
import com.margin.app.data.Repeat
import com.margin.app.data.displayTitle
import com.margin.app.data.isLocked
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Rings a note's reminder: a notification with the note's title (and first line, unless it's locked) that
 * opens the note, with Snooze (10 minutes) and Done. It plays the alarm sound and, on a locked or sleeping
 * phone, fills the screen ([ReminderAlarmActivity]). Also puts the alarms back after a restart or an update.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: Reminders
    @Inject lateinit var repo: NoteRepository

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE)
        when (intent.action) {
            // Also when "Alarms & reminders" is turned on: alarms set before then were inexact, so set them again.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> reminders.rescheduleAll()
            // The nudge rings once a day, so skipping is just putting it away until tomorrow's.
            ACTION_SKIP_JOURNAL -> NotificationManagerCompat.from(context).cancel(JOURNAL_REMINDER, NOTIFICATION_ID)
            ACTION_SNOOZE -> if (noteId != null) {
                reminders.snooze(noteId, System.currentTimeMillis() + SNOOZE_MS)
                NotificationManagerCompat.from(context).cancel(noteId, NOTIFICATION_ID)
            }
            ACTION_DONE -> if (noteId != null) {
                NotificationManagerCompat.from(context).cancel(noteId, NOTIFICATION_ID)
                // A habit's Done ticks today off.
                if (noteId.startsWith("habit-")) {
                    val done = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            repo.getNote(noteId)?.takeIf { it.trashedAt == null && !it.deleted }
                                ?.let { repo.saveNote(it.id, it.folderId, habitTicked(it, java.time.LocalDate.now())) }
                        } finally { done.finish() }
                    }
                }
            }
            ACTION_RING -> if (noteId != null) {
                // A snooze of a repeating reminder is its own alarm; the series has already moved on.
                if (!intent.getBooleanExtra(EXTRA_SNOOZED, false)) reminders.rang(noteId)
                val done = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // The daily journal nudge isn't a note: it shows only while today's entry is empty.
                        if (noteId == JOURNAL_REMINDER) {
                            val entry = repo.getNote(journalId(java.time.LocalDate.now()))?.takeIf { it.trashedAt == null && !it.deleted }
                            // Entries are locked notes: a sealed body counts as written.
                            val written = entry != null && (entry.isLocked || entry.searchText.isNotBlank())
                            if (!written) journalNudge(context)
                            return@launch
                        }
                        // A note deleted since the reminder was set rings no more.
                        val note = repo.getNote(noteId)?.takeIf { it.trashedAt == null && !it.deleted }
                            ?: return@launch reminders.clear(noteId)
                        if (isHabit(note)) {
                            val days = habitDays(note)
                            val today = java.time.LocalDate.now()
                            if (today !in days) nudge(context, noteId, note.displayTitle, habitStreak(days, today))
                            return@launch
                        }
                        val body = if (note.isLocked) "Locked note" else note.searchText.lineSequence().map { it.trim() }
                            .firstOrNull { it.isNotEmpty() && it != note.title.trim() }
                        ring(context, noteId, note.folderId, note.displayTitle, body, reminders.repeatOf(noteId))
                    } finally {
                        done.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_RING = "com.margin.app.REMINDER"
        const val ACTION_SNOOZE = "com.margin.app.REMINDER_SNOOZE"
        const val ACTION_DONE = "com.margin.app.REMINDER_DONE"
        const val EXTRA_NOTE = "note"
        const val EXTRA_SNOOZED = "snoozed"
        // A channel's sound can't change once created, so the alarm-sound channel has a new id; the old one goes.
        private const val OLD_CHANNEL = "reminders"
        private const val CHANNEL = "reminders_alarm"
        const val NOTIFICATION_ID = 7 // tagged with the note id, so each note has its own
        private const val SNOOZE_MS = 10 * 60_000L

        private const val HABIT_CHANNEL = "habits"

        /** The daily journal nudge: a plain notification that opens the Journal. */
        private fun journalNudge(c: Context) {
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(JOURNAL_CHANNEL, "Journal reminder", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A daily nudge to write today's journal entry" },
            )
            val open = PendingIntent.getActivity(
                c, JOURNAL_REMINDER.hashCode(),
                Intent(c, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN).putExtra(RecentWidget.EXTRA_NOTE, JOURNAL_REMINDER).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val skip = PendingIntent.getBroadcast(
                c, JOURNAL_REMINDER.hashCode() + 1,
                Intent(c, ReminderReceiver::class.java).setAction(ACTION_SKIP_JOURNAL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(c, JOURNAL_CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle("Time to journal")
                .setContentText("You haven't written today's entry yet.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(0, "Skip today", skip)
                .build()
            runCatching { NotificationManagerCompat.from(c).notify(JOURNAL_REMINDER, NOTIFICATION_ID, n) }
        }

        private const val JOURNAL_CHANNEL = "journal"
        const val ACTION_SKIP_JOURNAL = "com.margin.app.JOURNAL_SKIP"

        /** A habit's daily nudge: a plain notification (no alarm sound, no full screen) with Done to tick today. */
        private fun nudge(c: Context, noteId: String, title: String, streak: Int) {
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(HABIT_CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Daily nudges for habits not ticked yet" },
            )
            val open = PendingIntent.getActivity(
                c, noteId.hashCode(),
                Intent(c, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN).putExtra(RecentWidget.EXTRA_NOTE, noteId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val doneIntent = PendingIntent.getBroadcast(
                c, noteId.hashCode() + 2,
                Intent(c, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_NOTE, noteId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(c, HABIT_CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(title)
                .setContentText(if (streak > 0) "Keep your ${plural(streak, "day")} streak going." else "Not done yet today.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(0, "Done", doneIntent)
                .build()
            runCatching { NotificationManagerCompat.from(c).notify(noteId, NOTIFICATION_ID, n) }
        }

        private fun ring(c: Context, noteId: String, folderId: String, title: String, body: String?, repeat: Repeat) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(OLD_CHANNEL)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Note reminders you set"
                    // The alarm sound, played as an alarm: heard even when notification sounds are turned down.
                    setSound(
                        RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
            // Opens the note the way the home-screen widget does.
            val open = PendingIntent.getActivity(
                c, noteId.hashCode(),
                Intent(c, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN)
                    .putExtra(RecentWidget.EXTRA_NOTE, noteId).putExtra(RecentWidget.EXTRA_FOLDER, folderId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            fun action(action: String, code: Int) = PendingIntent.getBroadcast(
                c, noteId.hashCode() + code,
                Intent(c, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_NOTE, noteId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Shown full screen when the phone is locked or asleep; as a heads-up banner while it's in use.
            val alarm = PendingIntent.getActivity(
                c, noteId.hashCode() + 4,
                Intent(c, ReminderAlarmActivity::class.java).putExtra(EXTRA_NOTE, noteId)
                    .putExtra(ReminderAlarmActivity.EXTRA_FOLDER, folderId).putExtra(ReminderAlarmActivity.EXTRA_TITLE, title)
                    .putExtra(ReminderAlarmActivity.EXTRA_BODY, body).putExtra(ReminderAlarmActivity.EXTRA_REPEAT, repeat.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(title)
                .apply { body?.let { setContentText(it) } }
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(alarm, true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(0, "Snooze 10 min", action(ACTION_SNOOZE, 1))
                .addAction(0, "Done", action(ACTION_DONE, 2))
                .build()
            runCatching { NotificationManagerCompat.from(c).notify(noteId, NOTIFICATION_ID, n) } // no POST_NOTIFICATIONS: nothing shown
        }
    }
}
