package com.margin.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.data.NoteRepository
import com.margin.app.data.Place
import com.margin.app.data.PlaceReminders
import com.margin.app.data.displayTitle
import com.margin.app.data.isLocked
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A place reminder's place was reached (or left): shows the note as a notification and the reminder is done.
 * A plain one, not the alarm a time reminder rings: you're out and about, and there's no snoozing a place.
 * Also puts the geofences back when location is turned on again (Android drops them while it's off).
 */
@AndroidEntryPoint
class PlaceReceiver : BroadcastReceiver() {
    @Inject lateinit var places: PlaceReminders
    @Inject lateinit var repo: NoteRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LocationManager.PROVIDERS_CHANGED_ACTION -> places.registerAll()
            ACTION_PLACE -> {
                val event = GeofencingEvent.fromIntent(intent) ?: return
                if (event.hasError()) {
                    if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) return // location is off; back on PROVIDERS_CHANGED
                    return
                }
                val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
                val done = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (id in ids) {
                            val place = places.at(id) ?: continue
                            places.clear(id)
                            // A note deleted since rings no more.
                            val note = repo.getNote(id)?.takeIf { it.trashedAt == null && !it.deleted } ?: continue
                            val body = if (note.isLocked) context.getString(R.string.place_receiver_locked_note) else note.searchText.lineSequence().map { it.trim() }
                                .firstOrNull { it.isNotEmpty() && it != note.title.trim() }
                            notify(context, id, note.folderId, note.displayTitle, body, place)
                        }
                    } finally {
                        done.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PLACE = "com.margin.app.PLACE"
        private const val CHANNEL = "place_reminders"

        private fun notify(c: Context, noteId: String, folderId: String, title: String, body: String?, place: Place) {
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, c.getString(R.string.place_receiver_place_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = c.getString(R.string.place_receiver_note_reminders_for_when_you)
                },
            )
            val open = PendingIntent.getActivity(
                c, noteId.hashCode() + 5,
                Intent(c, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN)
                    .putExtra(RecentWidget.EXTRA_NOTE, noteId).putExtra(RecentWidget.EXTRA_FOLDER, folderId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val doneIntent = PendingIntent.getBroadcast(
                c, noteId.hashCode() + 6,
                Intent(c, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE).putExtra(ReminderReceiver.EXTRA_NOTE, noteId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val where = place.phraseText(c)
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(title)
                .setContentText(body ?: where)
                .setSubText(where)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(0, c.getString(R.string.common_done), doneIntent)
                .build()
            // Same tag and id as a time reminder, so the note's Done clears it either way.
            runCatching { NotificationManagerCompat.from(c).notify(noteId, ReminderReceiver.NOTIFICATION_ID, n) }
        }
    }
}
