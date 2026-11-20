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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.ensureDefaultFolder()
            repository.purgeExpiredTrash()
            attachmentFiles.cleanUp(repository.attachmentsInUse())
        }
        // Keeps the home-screen widget current while the process runs (app or background sync).
        appScope.launch {
            repository.liveNotes().collect {
                RecentWidget.push(this@MarginApp, it)
                com.margin.app.ui.ListWidget.push(this@MarginApp, it)
            }
        }
        // Reads text in photos and PDFs so search finds it.
        appScope.launch { attachmentIndex.run() }
        scheduler.onAppStart() // sync on app start (when signed in) + 6 h periodic job
        com.margin.app.sync.BackupWorker.schedule(this, backupPrefs.targets.value.isNotEmpty()) // daily backups, if any place is set up
    }
}
