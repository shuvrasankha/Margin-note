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
            "AND (title LIKE :pattern ESCAPE '\\' OR searchText LIKE :pattern ESCAPE '\\') " +
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

@Database(entities = [FolderEntity::class, NoteEntity::class], version = 5, exportSchema = true)
@TypeConverters(Converters::class)
abstract class JotterDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun notes(): NoteDao
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
