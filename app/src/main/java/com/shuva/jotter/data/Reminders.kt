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
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** How often a reminder comes back after it rings. */
enum class Repeat(val label: String) {
    NEVER("Never"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");

    /** The next time after [time] (epoch millis) in this phone's time zone, or null for [NEVER]. */
    fun after(time: Long): Long? {
        val t = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
        return when (this) {
            NEVER -> null
            DAILY -> t.plusDays(1)
            WEEKLY -> t.plusWeeks(1)
            MONTHLY -> t.plusMonths(1)
            YEARLY -> t.plusYears(1)
        }?.toInstant()?.toEpochMilli()
    }
}

/**
 * Note reminders: one time per note, kept on this device (not synced, so a reminder rings on the phone it was
 * set on). Each is an alarm that posts a notification (see [ReminderReceiver]); once it rings, a one-off is
 * removed and a repeating one moves on to its next time.
 */
@Singleton
class Reminders @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("jotter_reminders", Context.MODE_PRIVATE)
    /** Note id → [Repeat] name, for repeating reminders only. */
    private val repeatPrefs = context.getSharedPreferences("jotter_reminder_repeat", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private val _all = MutableStateFlow(read())
    /** Note id → when its reminder rings (epoch millis). */
    val all: StateFlow<Map<String, Long>> = _all

    private val _repeats = MutableStateFlow(readRepeats())
    /** Note id → how its reminder repeats; notes missing here ring once. */
    val repeats: StateFlow<Map<String, Repeat>> = _repeats

    // Only Long values are reminders, so the Boolean flag below shares the file safely.
    private fun read(): Map<String, Long> = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
    private fun readRepeats(): Map<String, Repeat> =
        repeatPrefs.all.mapNotNull { (k, v) -> runCatching { Repeat.valueOf(v as String) }.getOrNull()?.let { k to it } }.toMap()

    fun at(noteId: String): Long? = _all.value[noteId]
    fun repeatOf(noteId: String): Repeat = _repeats.value[noteId] ?: Repeat.NEVER

    /** Rings for [noteId] at [time], then again every [repeat], replacing its earlier reminder. */
    fun set(noteId: String, time: Long, repeat: Repeat = Repeat.NEVER) {
        prefs.edit().putLong(noteId, time).apply()
        _all.value = _all.value + (noteId to time)
        if (repeat == Repeat.NEVER) repeatPrefs.edit().remove(noteId).apply() else repeatPrefs.edit().putString(noteId, repeat.name).apply()
        _repeats.value = if (repeat == Repeat.NEVER) _repeats.value - noteId else _repeats.value + (noteId to repeat)
        alarms.cancel(pending(noteId, snooze = true))
        schedule(noteId, time)
    }

    fun clear(noteId: String) {
        prefs.edit().remove(noteId).apply()
        repeatPrefs.edit().remove(noteId).apply()
        _all.value = _all.value - noteId
        _repeats.value = _repeats.value - noteId
        alarms.cancel(pending(noteId))
        alarms.cancel(pending(noteId, snooze = true))
    }

    /**
     * The reminder has just rung: a repeating one moves on to its next time still to come; a one-off is removed.
     */
    fun rang(noteId: String) {
        val time = at(noteId) ?: return
        val repeat = repeatOf(noteId)
        var next = repeat.after(time)
        val now = System.currentTimeMillis()
        while (next != null && next <= now) next = repeat.after(next)
        if (next == null) clear(noteId) else set(noteId, next, repeat)
    }

    /**
     * Rings [noteId] again in a few minutes. A one-off reminder simply moves; a repeating one keeps its own
     * times and gets a separate alarm, so snoozing doesn't shift the series.
     */
    fun snooze(noteId: String, time: Long) {
        if (repeatOf(noteId) == Repeat.NEVER) set(noteId, time) else schedule(noteId, time, snooze = true)
    }

    /** After a restart or an update, when Android has dropped the alarms. Ones missed meanwhile ring now. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        _all.value = read()
        _repeats.value = readRepeats()
        _all.value.forEach { (id, time) -> schedule(id, maxOf(time, now + 5_000)) }
    }

    /**
     * Whether alarms ring on the minute. Android 13+ asks the user for this ("Alarms & reminders"); without it
     * Android may hold a reminder back a few minutes to save battery.
     */
    fun canRingOnTime(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Whether a ringing reminder may fill the screen. Android 14+ lets the user turn this off (and may by default). */
    fun canFillScreen(): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

    /** True the first time only: whether to ask for the full-screen permission now. */
    fun markAskedFullScreen(): Boolean {
        if (prefs.getBoolean(ASKED_FULL_SCREEN, false)) return false
        prefs.edit().putBoolean(ASKED_FULL_SCREEN, true).apply()
        return true
    }

    private fun schedule(noteId: String, time: Long, snooze: Boolean = false) {
        val p = pending(noteId, snooze)
        if (canRingOnTime()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
    }

    private fun pending(noteId: String, snooze: Boolean = false) = PendingIntent.getBroadcast(
        context, noteId.hashCode() + if (snooze) 3 else 0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RING).putExtra(ReminderReceiver.EXTRA_NOTE, noteId)
            .putExtra(ReminderReceiver.EXTRA_SNOOZED, snooze),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object { const val ASKED_FULL_SCREEN = "_asked_full_screen" }
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
