package com.margin.app.sync

import com.margin.app.data.isSafeAttachmentName
import java.io.File
import java.util.Base64

const val ATTACHMENT_PREFIX = "att-"

/**
 * Attachment files never change once added, so syncing them is by name only: upload the ones Drive is
 * missing, download the ones this device is missing. Files go up as base64 text through the same calls
 * as notes.
 * shortcut: base64 in memory, fine up to the 20 MB attachment limit; switch to binary/resumable uploads for bigger files.
 * A file in Drive that no note uses any more is deleted: the readable copies of a note that was locked (its
 * files were re-encrypted under new names) or deleted for good. [referenced] is read after the notes synced, so
 * it covers every device's notes; a file another device still has is uploaded again by that device.
 */
suspend fun syncAttachments(drive: DriveStore, dir: File, referenced: Set<String>): Int {
    val remote = drive.list().filter { it.name.startsWith(ATTACHMENT_PREFIX) }.associateBy { it.name.removePrefix(ATTACHMENT_PREFIX) }
    var moved = 0
    for (name in referenced) {
        if (!isSafeAttachmentName(name)) continue
        val file = File(dir, name)
        val inDrive = remote[name]
        if (file.exists() && inDrive == null) {
            drive.upload(ATTACHMENT_PREFIX + name, null, Base64.getEncoder().encodeToString(file.readBytes()), file.lastModified())
            moved++
        } else if (!file.exists() && inDrive != null) {
            val tmp = File(dir, "$name.part")
            tmp.writeBytes(Base64.getDecoder().decode(drive.download(inDrive.fileId)))
            tmp.renameTo(file) // whole file or nothing
            moved++
        }
    }
    for ((name, file) in remote) if (name !in referenced) drive.delete(file.fileId)
    return moved
}
