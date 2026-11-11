package com.shuva.jotter

import android.app.Application
import com.shuva.jotter.data.AttachmentFiles
import com.shuva.jotter.data.NoteRepository
import com.shuva.jotter.sync.SyncScheduler
import com.shuva.jotter.ui.RecentWidget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JotterApp : Application() {
    @Inject lateinit var repository: NoteRepository
    @Inject lateinit var scheduler: SyncScheduler
    @Inject lateinit var attachmentFiles: AttachmentFiles

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.ensureDefaultFolder()
            repository.purgeExpiredTrash()
            attachmentFiles.cleanUp(repository.attachmentsInUse())
        }
        // Keeps the home-screen widget current while the process runs (app or background sync).
        appScope.launch { repository.liveNotes().collect { RecentWidget.push(this@JotterApp, it) } }
        scheduler.onAppStart() // sync on app start (when signed in) + 6 h periodic job
    }
}
