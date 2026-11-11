package com.shuva.jotter.sync

import com.shuva.jotter.data.AttachmentFiles
import com.shuva.jotter.data.NoteRepository
import com.shuva.jotter.data.SyncPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Runs one sync or restore at a time, with a fresh Drive token. Used by the worker and Settings. */
@Singleton
class SyncManager @Inject constructor(
    private val store: RoomSyncStore,
    private val authorizer: DriveAuthorizer,
    private val prefs: SyncPrefs,
    private val repository: NoteRepository,
    private val files: AttachmentFiles,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncReport = run { it.sync() }
    suspend fun restore(): SyncReport = run { it.restore() }

    private suspend fun run(action: suspend (SyncEngine) -> SyncReport): SyncReport = mutex.withLock {
        repository.ensureDefaultFolder()
        repository.purgeExpiredTrash()
        try {
            val token = authorizer.accessToken()
            val report = try {
                action(SyncEngine(store, DriveRestStore(token)))
            } catch (e: DriveHttpException) {
                if (e.code != 401) throw e
                authorizer.clearToken(token) // stale cached token: retry once with a new one
                action(SyncEngine(store, DriveRestStore(authorizer.accessToken())))
            }
            repository.fixOrphans()
            val inUse = repository.attachmentsInUse()
            syncAttachments(DriveRestStore(authorizer.accessToken()), files.dir, inUse)
            files.cleanUp(inUse)
            prefs.recordSuccess(System.currentTimeMillis())
            report
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Not a failure: a newer sync request replaced this one (every edit re-queues it), or Android stopped
            // the work. Recording it would show "Couldn't sync" while nothing is wrong.
            throw e
        } catch (e: Exception) {
            prefs.recordError(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }
}
