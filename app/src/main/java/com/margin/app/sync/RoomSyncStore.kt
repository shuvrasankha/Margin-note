package com.margin.app.sync

import androidx.room.withTransaction
import com.margin.app.data.FolderEntity
import com.margin.app.data.MarginDatabase
import com.margin.app.data.NoteEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSyncStore @Inject constructor(private val db: MarginDatabase) : SyncLocalStore {
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
