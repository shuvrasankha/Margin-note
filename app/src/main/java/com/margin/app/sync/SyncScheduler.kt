package com.margin.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.margin.app.data.SyncPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Sync triggers: app start, 5 s after the last edit, and every 6 h. All require network. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SyncPrefs,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun onAppStart() {
        workManager.cancelUniqueWork(OLD_PERIODIC)
        // The periodic job also purges expired Recently Deleted notes, so it runs even when signed out.
        workManager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build(),
        )
        requestNow()
    }

    /** Call after every local edit. REPLACE restarts the 5 s timer, which is the debounce. */
    fun requestDebounced() = enqueue(DEBOUNCED, delaySeconds = 5)

    fun requestNow() = enqueue(NOW, delaySeconds = 0)

    private fun enqueue(name: String, delaySeconds: Long) {
        if (!prefs.isSignedIn) return
        workManager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(network)
                .build(),
        )
    }

    private companion object {
        const val PERIODIC = "margin-sync-periodic"
        const val DEBOUNCED = "margin-sync-debounced"
        const val NOW = "margin-sync-now"
        /** The periodic job queued before the rename; it names a class that's gone, so it can never run. */
        const val OLD_PERIODIC = "jotter-sync-periodic"
    }
}
