package com.margin.app

import android.app.Application
import com.margin.app.data.AttachmentFiles
import com.margin.app.data.NoteRepository
import com.margin.app.sync.SyncScheduler
import com.margin.app.ui.RecentWidget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MarginApp : Application() {
    @Inject lateinit var repository: NoteRepository
    @Inject lateinit var scheduler: SyncScheduler
    @Inject lateinit var attachmentFiles: AttachmentFiles
    @Inject lateinit var attachmentIndex: com.margin.app.data.AttachmentIndex
    @Inject lateinit var backupPrefs: com.margin.app.sync.BackupPrefs
    @Inject lateinit var placeReminders: com.margin.app.data.PlaceReminders
    @Inject lateinit var pinnedNotes: com.margin.app.data.PinnedNotes

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.margin.app.data.untitledNoteName = getString(R.string.model_new_note)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        com.margin.app.data.untitledNoteName = getString(R.string.model_new_note)
        appScope.launch {
            repository.ensureDefaultFolder()
            repository.purgeExpiredTrash()
            attachmentFiles.cleanUp(repository.attachmentsInUse())
        }
        // Repeating checklists: untick the ones whose new round began while Margin was closed, then hourly.
        appScope.launch { repository.resetRepeatingLists() }
        com.margin.app.sync.ListResetWorker.schedule(this)
        // Keeps the home-screen widget current while the process runs (app or background sync).
        appScope.launch {
            repository.liveNotes().collect {
                RecentWidget.push(this@MarginApp, it)
                com.margin.app.ui.ListWidget.push(this@MarginApp, it)
                com.margin.app.ui.NoteWidget.push(this@MarginApp, it)
                com.margin.app.ui.TodayWidget.push(this@MarginApp, it)
                // Notes pinned to the shade; also puts them back after a restart, when this process starts.
                pinnedNotes.refresh(it)
            }
        }
        // Place reminders: Android or Play services may have dropped the geofences (restart, update, data cleared).
        placeReminders.registerAll()
        // Reads text in photos and PDFs so search finds it.
        appScope.launch { attachmentIndex.run() }
        scheduler.onAppStart() // sync on app start (when signed in) + 6 h periodic job
        com.margin.app.sync.BackupWorker.schedule(this, backupPrefs.targets.value.isNotEmpty()) // daily backups, if any place is set up
    }
}
