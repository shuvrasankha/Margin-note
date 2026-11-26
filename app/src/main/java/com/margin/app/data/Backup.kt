package com.margin.app.data

import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A full backup: one zip holding every folder and note exactly as stored (locked notes and their files stay
 * encrypted), plus the attachment files. Unlike the Markdown export, nothing is lost, so it can be restored.
 *
 *   backup.json         [BackupInfo] with the folders
 *   notes/<id>.json     one [NoteEntity] each
 *   files/<name>        attachment files
 */
@Serializable
data class BackupInfo(
    val format: Int = BACKUP_FORMAT,
    val createdAt: Long,
    val folders: List<FolderEntity>,
    val noteCount: Int,
    val fileCount: Int,
)

const val BACKUP_FORMAT = 1
private const val INFO_ENTRY = "backup.json"
private const val NOTES_DIR = "notes/"
private const val FILES_DIR = "files/"

private val backupTime = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

/** "margin-backup-2026-11-15-103012.zip": sorts by time, and says what it is wherever it lands. */
fun backupName(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "margin-backup-" + backupTime.format(Instant.ofEpochMilli(at).atZone(zone)) + ".zip"

private val BACKUP_NAME = Regex("""margin-backup-\d{4}-\d{2}-\d{2}-\d{6}\.zip""")
fun isBackupName(name: String) = BACKUP_NAME.matches(name)

/** When a backup was made, read from its name. */
fun backupTimeOf(name: String, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
    java.time.LocalDateTime.parse(name.removePrefix("margin-backup-").removeSuffix(".zip"), backupTime).atZone(zone).toInstant().toEpochMilli()
}.getOrNull()

/** The newest [keep] backups are kept; these are the rest, to delete. */
fun backupsToPrune(names: List<String>, keep: Int): List<String> =
    names.filter(::isBackupName).sortedDescending().drop(keep)

/** Writes a backup of [folders] and [notes] (tombstones left out) with the files [files] gives for [fileNames]. */
fun writeBackup(
    out: OutputStream, folders: List<FolderEntity>, notes: List<NoteEntity>, fileNames: Collection<String>,
    openFile: (String) -> InputStream?, now: Long = System.currentTimeMillis(),
): BackupInfo {
    val liveFolders = folders.filter { !it.deleted }
    val liveNotes = notes.filter { !it.deleted }
    var files = 0
    ZipOutputStream(out).use { zip ->
        liveNotes.forEach { n ->
            zip.putNextEntry(ZipEntry(NOTES_DIR + safeEntry(n.id) + ".json"))
            zip.write(MarginJson.encodeToString(NoteEntity.serializer(), n).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        fileNames.filter(::isSafeAttachmentName).sorted().forEach { name ->
            val input = openFile(name) ?: return@forEach
            zip.putNextEntry(ZipEntry(FILES_DIR + name))
            input.use { it.copyTo(zip) }
            zip.closeEntry()
            files++
        }
        val info = BackupInfo(createdAt = now, folders = liveFolders, noteCount = liveNotes.size, fileCount = files)
        zip.putNextEntry(ZipEntry(INFO_ENTRY))
        zip.write(MarginJson.encodeToString(BackupInfo.serializer(), info).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        return info
    }
}

private fun safeEntry(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

class NotABackupException : Exception("That file isn't a Margin backup")

/** A backup from a newer version of Margin, which this one can't read. */
class NewerBackupException : Exception("This backup was made by a newer version of Margin. Update the app to restore it.")

/** What a backup holds; its files were handed to the reader's [onFile] as they were read. */
data class BackupContents(val info: BackupInfo, val notes: List<NoteEntity>)

/**
 * Reads a backup. Each attachment file goes to [onFile] (name, stream) as it's met, so big backups never sit
 * in memory; [onFile] is only called with safe file names.
 */
fun readBackup(input: InputStream, onFile: (String, InputStream) -> Unit): BackupContents {
    var info: BackupInfo? = null
    val notes = mutableListOf<NoteEntity>()
    ZipInputStream(input).use { zip ->
        while (true) {
            val e = zip.nextEntry ?: break
            when {
                e.isDirectory -> Unit
                e.name == INFO_ENTRY -> info = MarginJson.decodeFromString(BackupInfo.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                e.name.startsWith(NOTES_DIR) && e.name.endsWith(".json") ->
                    notes += MarginJson.decodeFromString(NoteEntity.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                e.name.startsWith(FILES_DIR) -> e.name.removePrefix(FILES_DIR).takeIf(::isSafeAttachmentName)?.let { onFile(it, zip) }
            }
        }
    }
    val found = info ?: throw NotABackupException()
    if (found.format > BACKUP_FORMAT) throw NewerBackupException()
    return BackupContents(found, notes)
}

/**
 * What restoring [backup] over this device changes: each record the device doesn't have, or has an older copy
 * of. Anything changed here since the backup (deleting it included) stays as it is, so a restore never loses
 * work. The records come back unsynced, so sync sends them on to the other devices.
 */
data class RestorePlan(val folders: List<FolderEntity>, val notes: List<NoteEntity>)

fun restorePlan(localFolders: List<FolderEntity>, localNotes: List<NoteEntity>, backup: BackupContents): RestorePlan {
    val haveFolders = localFolders.associateBy { it.id }
    val haveNotes = localNotes.associateBy { it.id }
    return RestorePlan(
        backup.info.folders.filter { f -> haveFolders[f.id].let { it == null || it.updatedAt < f.updatedAt } }.map { it.copy(syncedUpdatedAt = null) },
        backup.notes.filter { n -> haveNotes[n.id].let { it == null || it.updatedAt < n.updatedAt } }.map { it.copy(syncedUpdatedAt = null) },
    )
}
