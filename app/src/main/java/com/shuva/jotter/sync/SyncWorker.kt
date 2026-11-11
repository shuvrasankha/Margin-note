package com.shuva.jotter.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shuva.jotter.data.NoteRepository
import com.shuva.jotter.data.SyncPrefs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun syncManager(): SyncManager
        fun repository(): NoteRepository
        fun prefs(): SyncPrefs
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        deps.repository().purgeExpiredTrash()
        if (!deps.prefs().isSignedIn) return Result.success()
        return try {
            deps.syncManager().sync()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // replaced by a newer request or stopped by Android; WorkManager handles it
        } catch (e: NeedsUserConsentException) {
            Result.failure() // the user must open Settings and approve Drive access
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
