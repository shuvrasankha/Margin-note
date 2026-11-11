package com.shuva.jotter.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun blocksToJson(blocks: List<Block>): String = JotterJson.encodeToString(blocks)

    @TypeConverter
    fun blocksFromJson(json: String): List<Block> = JotterJson.decodeFromString(json)

    @TypeConverter
    fun filterToJson(filter: SmartFilter?): String? = filter?.let { JotterJson.encodeToString(it) }

    @TypeConverter
    fun filterFromJson(json: String?): SmartFilter? = json?.let { JotterJson.decodeFromString<SmartFilter>(it) }
}

data class FolderCount(val folderId: String, val count: Int)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deleted = 0 ORDER BY isDefault DESC, name COLLATE NOCASE")
    fun observeLive(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun all(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observe(id: String): Flow<FolderEntity?>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET syncedUpdatedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    /** Folders changed since the last sync (the offline queue). */
    @Query("SELECT COUNT(*) FROM folders WHERE syncedUpdatedAt IS NULL OR syncedUpdatedAt != updatedAt")
    fun observeDirtyCount(): Flow<Int>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE folderId = :folderId AND deleted = 0 AND trashedAt IS NULL ORDER BY isPinned DESC, CASE WHEN editedAt > 0 THEN editedAt ELSE updatedAt END DESC")
    fun observeInFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE deleted = 0 AND trashedAt IS NULL " +
            "AND (title LIKE :pattern ESCAPE '\\' OR searchText LIKE :pattern ESCAPE '\\' " +
            "OR EXISTS (SELECT 1 FROM attachment_text t WHERE t.text LIKE :pattern ESCAPE '\\' AND notes.blocks LIKE '%\"' || t.file || '\"%')) " +
            "ORDER BY isPinned DESC, CASE WHEN editedAt > 0 THEN editedAt ELSE updatedAt END DESC",
    )
    fun observeSearch(pattern: String): Flow<List<NoteEntity>>

    @Query("SELECT folderId, COUNT(*) AS count FROM notes WHERE deleted = 0 AND trashedAt IS NULL GROUP BY folderId")
    fun observeCounts(): Flow<List<FolderCount>>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND trashedAt IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND cipher IS NOT NULL")
    fun observeLockedCount(): Flow<Int>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND deleted = 0 AND trashedAt IS NULL")
    suspend fun liveInFolder(folderId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND trashedAt IS NOT NULL")
    suspend fun trashed(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND trashedAt IS NULL")
    suspend fun live(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND trashedAt IS NULL")
    fun observeLive(): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET syncedUpdatedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    /** Notes changed since the last sync, deletions included (the offline queue). */
    @Query("SELECT COUNT(*) FROM notes WHERE syncedUpdatedAt IS NULL OR syncedUpdatedAt != updatedAt")
    fun observeDirtyCount(): Flow<Int>
}

/** Text read from a photo or PDF attachment, for search. Local only: each phone reads its own. Empty = read, none found. */
@androidx.room.Entity(tableName = "attachment_text")
data class AttachmentText(@androidx.room.PrimaryKey val file: String, val text: String)

@Dao
interface AttachmentTextDao {
    @Query("SELECT file FROM attachment_text")
    suspend fun files(): List<String>

    @Query("SELECT * FROM attachment_text WHERE text LIKE :pattern ESCAPE '\\'")
    fun observeMatching(pattern: String): Flow<List<AttachmentText>>

    @Upsert
    suspend fun upsert(row: AttachmentText)

    @Query("DELETE FROM attachment_text WHERE file IN (:files)")
    suspend fun delete(files: List<String>)
}

/** A note as it was at the end of an editing session. Local only, never for locked notes. */
@androidx.room.Entity(tableName = "note_versions", indices = [androidx.room.Index("noteId")])
data class NoteVersion(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val savedAt: Long,
    val blocks: List<Block>,
)

@Dao
interface NoteVersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun observe(noteId: String): Flow<List<NoteVersion>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT 1")
    suspend fun latest(noteId: String): NoteVersion?

    @Upsert
    suspend fun upsert(v: NoteVersion)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId AND id NOT IN (SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT :keep)")
    suspend fun trim(noteId: String, keep: Int)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteFor(noteId: String)

    @Query("DELETE FROM note_versions WHERE noteId NOT IN (SELECT id FROM notes WHERE deleted = 0)")
    suspend fun deleteOrphans()

    @Query("SELECT blocks FROM note_versions")
    suspend fun allBlocks(): List<String>
}

@Database(entities = [FolderEntity::class, NoteEntity::class, AttachmentText::class, NoteVersion::class], version = 6, exportSchema = true)
@TypeConverters(Converters::class)
abstract class JotterDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun notes(): NoteDao
    abstract fun attachmentText(): AttachmentTextDao
    abstract fun versions(): NoteVersionDao
}

/** v6: text read from attachments (for search) and note version history. Both local only. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `attachment_text` (`file` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`file`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_versions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, `blocks` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_versions_noteId` ON `note_versions` (`noteId`)")
    }
}

/** v2: per-folder sort and a note edit time separate from the sync time. Existing notes start with editedAt = updatedAt. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN sortBy TEXT NOT NULL DEFAULT 'EDITED'")
        db.execSQL("ALTER TABLE folders ADD COLUMN sortAscending INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN editedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE notes SET editedAt = updatedAt")
    }
}

/** v4: folder order from Edit folders. Existing folders start at 0, so they stay A to Z until reordered. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5: a to-do list's target end date on notes. Existing notes start with none. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN targetDate INTEGER")
    }
}

/** v3: nested folders (parentId), smart folders (smartFilter) and locked notes (cipher). All start out null. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN parentId TEXT")
        db.execSQL("ALTER TABLE folders ADD COLUMN smartFilter TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN cipher TEXT")
    }
}
