package com.shuva.jotter.sync

import androidx.room.withTransaction
import com.shuva.jotter.data.FolderEntity
import com.shuva.jotter.data.JotterDatabase
import com.shuva.jotter.data.NoteEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSyncStore @Inject constructor(private val db: JotterDatabase) : SyncLocalStore {
    override suspend fun allFolders() = db.folders().all()
    override suspend fun allNotes() = db.notes().all()

    override suspend fun saveFolder(folder: FolderEntity, expectedUpdatedAt: Long?, force: Boolean) =
        db.withTransaction {
            if (force || db.folders().get(folder.id)?.updatedAt == expectedUpdatedAt) db.folders().upsert(folder)
        }

    override suspend fun saveNote(note: NoteEntity, expectedUpdatedAt: Long?, force: Boolean) =
        db.withTransaction {
            if (force || db.notes().get(note.id)?.updatedAt == expectedUpdatedAt) db.notes().upsert(note)
        }

    override suspend fun markFolderSynced(id: String, updatedAt: Long) = db.folders().markSynced(id, updatedAt)
    override suspend fun markNoteSynced(id: String, updatedAt: Long) = db.notes().markSynced(id, updatedAt)
}
