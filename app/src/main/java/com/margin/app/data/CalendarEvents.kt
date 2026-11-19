package com.margin.app.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/** Adds reminders to the phone's own calendar, as events. */
object CalendarEvents {
    /** How long an event from a reminder lasts on the calendar. */
    private const val EVENT_MS = 30 * 60 * 1000L

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun allowed(context: Context) = PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** The calendar events go to: the phone's primary one that can be written to, else any writable one. Null if none. */
    private fun defaultCalendar(context: Context): Pair<Long, String>? {
        val cols = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val writable = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        data class Cal(val id: Long, val name: String, val primary: Boolean, val type: String) { val google get() = type == "com.google" }
        val cals = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, writable, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(Cal(c.getLong(0), c.getString(1).orEmpty(), c.getInt(2) == 1, c.getString(3).orEmpty()))
            }
        }.orEmpty()
        // Phones mark several calendars primary (a phone-only "My calendar", Samsung's, the Google account's), so a
        // synced one wins: the Google account's primary, then any other account's primary, then any Google
        // calendar, and only then one kept on the phone alone.
        val local = { c: Cal -> c.type == CalendarContract.ACCOUNT_TYPE_LOCAL || c.type == "LOCAL" }
        val pick = cals.firstOrNull { it.primary && it.google } ?: cals.firstOrNull { it.primary && !local(it) }
            ?: cals.firstOrNull { it.google } ?: cals.firstOrNull { it.primary } ?: cals.firstOrNull() ?: return null
        return pick.id to pick.name
    }

    /** "FREQ=WEEKLY" and so on, or null for a one-off. */
    private fun rule(repeat: Repeat) = when (repeat) {
        Repeat.NEVER -> null
        Repeat.DAILY -> "FREQ=DAILY"
        Repeat.WEEKLY -> "FREQ=WEEKLY"
        Repeat.MONTHLY -> "FREQ=MONTHLY"
        Repeat.YEARLY -> "FREQ=YEARLY"
    }

    /** Which calendar event belongs to which note's reminder, so changing the reminder moves its event. */
    private fun links(context: Context) = context.getSharedPreferences("calendar_events", Context.MODE_PRIVATE)

    /** True when [noteId]'s reminder already has an event on the calendar. */
    fun has(context: Context, noteId: String) = links(context).contains(noteId)

    /**
     * Puts [noteId]'s reminder on the default calendar: moves its event if it has one, else writes a new one.
     * Returns that calendar's name, or null when it couldn't (no permission, or no calendar that can be written to).
     */
    fun add(context: Context, noteId: String, title: String, details: String?, at: Long, repeat: Repeat): String? {
        if (!allowed(context)) return null
        val (calendarId, name) = runCatching { defaultCalendar(context) }.getOrNull() ?: return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title.ifBlank { "Reminder" })
            if (details.isNullOrBlank()) putNull(CalendarContract.Events.DESCRIPTION) else put(CalendarContract.Events.DESCRIPTION, details)
            put(CalendarContract.Events.DTSTART, at)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            val r = rule(repeat)
            // A repeating event takes a duration instead of an end time.
            if (r == null) { put(CalendarContract.Events.DTEND, at + EVENT_MS); putNull(CalendarContract.Events.RRULE); putNull(CalendarContract.Events.DURATION) }
            else { put(CalendarContract.Events.RRULE, r); put(CalendarContract.Events.DURATION, "PT30M"); putNull(CalendarContract.Events.DTEND) }
        }
        val resolver = context.contentResolver
        // The event made before, if it's still there (it may have been deleted in the calendar app).
        links(context).getLong(noteId, -1L).takeIf { it >= 0 }?.let { id ->
            val updated = runCatching { resolver.update(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null) }.getOrDefault(0)
            if (updated > 0) return name
        }
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
        val uri = runCatching { resolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull() ?: return null
        links(context).edit().putLong(noteId, android.content.ContentUris.parseId(uri)).apply()
        return name
    }

    /** Takes [noteId]'s reminder off the calendar, when Margin put it there. */
    fun remove(context: Context, noteId: String) {
        val id = links(context).getLong(noteId, -1L).takeIf { it >= 0 } ?: return
        if (allowed(context)) runCatching {
            context.contentResolver.delete(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
        }
        links(context).edit().remove(noteId).apply()
    }

    /** The calendar app's own "new event" screen, filled in, for when Margin can't write to the calendar itself. */
    fun insertIntent(title: String, details: String?, at: Long, repeat: Repeat): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, at)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, at + EVENT_MS)
            .apply {
                details?.takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                rule(repeat)?.let { putExtra(CalendarContract.Events.RRULE, it) }
            }
}
