package com.margin.app.data

import android.content.Context
import android.os.SystemClock
import android.text.format.DateUtils
import com.margin.app.R

/**
 * Slows down guessing the notes passcode on the phone: after [FREE] wrong tries in a row, each try waits
 * 30 s, then twice as long after every further miss, up to an hour. Shared by every way in (App Lock, locked
 * notes, Passwords, autofill), since they all use the one passcode. Kept on disk, so force-stopping the app
 * doesn't reset it; timed on the boot clock, so changing the phone's time doesn't either, and after a reboot
 * the wait starts over.
 */
object UnlockLimit {
    const val FREE = 5
    private const val FIRST_WAIT = 30_000L
    private const val MAX_WAIT = 3_600_000L

    interface Store { var failures: Int; var since: Long }

    /** In memory until [init] points it at the app's preferences. */
    var store: Store = object : Store { override var failures = 0; override var since = 0L }
    var clock: () -> Long = SystemClock::elapsedRealtime

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("unlock_limit", Context.MODE_PRIVATE)
        store = object : Store {
            override var failures get() = prefs.getInt("failures", 0); set(v) { prefs.edit().putInt("failures", v).commit() }
            override var since get() = prefs.getLong("since", 0); set(v) { prefs.edit().putLong("since", v).commit() }
        }
    }

    fun delayFor(failures: Int): Long =
        if (failures < FREE) 0 else minOf(FIRST_WAIT shl minOf(failures - FREE, 10), MAX_WAIT)

    /** How long until the passcode may be tried again; 0 = now. */
    @Synchronized fun waitMs(): Long {
        val delay = delayFor(store.failures)
        if (delay == 0L) return 0
        val now = clock()
        if (now < store.since) store.since = now // the phone restarted: the wait starts over
        return (store.since + delay - now).coerceAtLeast(0)
    }

    /** Call after checking a passcode only when [waitMs] was 0. */
    @Synchronized fun record(right: Boolean) {
        if (right) store.failures = 0 else { store.failures = store.failures + 1; store.since = clock() }
    }

    /** [wrong] (the usual "Wrong passcode"), or how long to wait when the limit is reached. */
    fun message(context: Context, wrong: String): String {
        val wait = waitMs()
        return if (wait == 0L) wrong
        else context.getString(R.string.passcode_too_many_tries, DateUtils.formatElapsedTime((wait + 999) / 1000))
    }
}
