package com.shuva.jotter.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shuva.jotter.ui.ReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Note reminders: one time per note, kept on this device (not synced, so a reminder rings on the phone it was
 * set on). Each is an alarm that posts a notification (see [ReminderReceiver]); it's removed once it rings.
 */
@Singleton
class Reminders @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("jotter_reminders", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private val _all = MutableStateFlow(read())
    /** Note id → when its reminder rings (epoch millis). */
    val all: StateFlow<Map<String, Long>> = _all

    private fun read(): Map<String, Long> = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    fun at(noteId: String): Long? = _all.value[noteId]

    /** Rings for [noteId] at [time], replacing its earlier reminder. */
    fun set(noteId: String, time: Long) {
        prefs.edit().putLong(noteId, time).apply()
        _all.value = _all.value + (noteId to time)
        schedule(noteId, time)
    }

    fun clear(noteId: String) {
        prefs.edit().remove(noteId).apply()
        _all.value = _all.value - noteId
        alarms.cancel(pending(noteId))
    }

    /** After a restart or an update, when Android has dropped the alarms. Ones missed meanwhile ring now. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        _all.value = read()
        _all.value.forEach { (id, time) -> schedule(id, maxOf(time, now + 5_000)) }
    }

    /**
     * Whether alarms ring on the minute. Android 13+ asks the user for this ("Alarms & reminders"); without it
     * Android may hold a reminder back a few minutes to save battery.
     */
    fun canRingOnTime(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun schedule(noteId: String, time: Long) {
        val p = pending(noteId)
        if (canRingOnTime()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
    }

    private fun pending(noteId: String) = PendingIntent.getBroadcast(
        context, noteId.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RING).putExtra(ReminderReceiver.EXTRA_NOTE, noteId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * The quick choices offered when setting a reminder at [now]: in an hour, this evening at 6 (before 5 PM),
 * tomorrow at 9, and next Monday at 9. Labels are sentence case.
 */
fun reminderChoices(now: ZonedDateTime): List<Pair<String, ZonedDateTime>> {
    val nine = LocalTime.of(9, 0)
    return listOfNotNull(
        "In 1 hour" to now.plusHours(1).truncatedTo(ChronoUnit.MINUTES).let { it.plusMinutes(((5 - it.minute % 5) % 5).toLong()) },
        ("This evening" to now.with(LocalTime.of(18, 0))).takeIf { now.toLocalTime() < LocalTime.of(17, 0) },
        "Tomorrow morning" to now.plusDays(1).with(nine),
        "Next week" to now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(nine),
    )
}
