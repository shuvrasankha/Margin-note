package com.margin.app.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.margin.app.data.AttachmentFiles
import com.margin.app.data.BackupCrypto
import com.margin.app.data.BackupPasswordNeededException
import com.margin.app.data.WrongBackupPasswordException
import com.margin.app.data.MarginDatabase
import com.margin.app.data.MarginJson
import com.margin.app.data.NoteRepository
import com.margin.app.data.backupName
import com.margin.app.data.backupsToPrune
import com.margin.app.data.isBackupName
import com.margin.app.data.newId
import com.margin.app.data.readBackup
import com.margin.app.data.restorePlan
import com.margin.app.data.writeBackup
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// ---------- Where backups go ----------

/** A place full backups are copied to, besides Google Drive sync. */
@Serializable
sealed interface BackupTarget {
    val id: String
    val label: String

    /**
     * A folder picked with Android's file picker: on the phone, or in any cloud app that offers its storage
     * there (Dropbox, OneDrive, Box, Nextcloud, MEGA, pCloud…). That app signs in and uploads.
     */
    @Serializable @SerialName("folder")
    data class Folder(override val id: String, override val label: String, val tree: String) : BackupTarget

    /** A WebDAV folder (Nextcloud, ownCloud, Koofr, a NAS…). Its password is kept apart, encrypted ([BackupPrefs]). */
    @Serializable @SerialName("webdav")
    data class WebDav(override val id: String, override val label: String, val url: String, val user: String) : BackupTarget
}

@Serializable
data class BackupStatus(val lastAt: Long? = null, val lastError: String? = null, val lastName: String? = null, val dataStamp: Long = 0)

/** How many backups each place keeps; older ones are deleted after a new one is made. */
const val KEEP_BACKUPS = 10

/** Backup places and how each last went. WebDAV passwords are encrypted with a key in the Android Keystore. */
@Singleton
class BackupPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("margin_backups", Context.MODE_PRIVATE)
    private val _targets = MutableStateFlow(readTargets())
    val targets: StateFlow<List<BackupTarget>> = _targets
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Map<String, BackupStatus>> = _status

    fun add(target: BackupTarget, password: String? = null) {
        password?.let { prefs.edit().putString(K_SECRET + target.id, Secrets.encrypt(it)).apply() }
        save(_targets.value.filterNot { it.id == target.id } + target)
    }

    fun remove(id: String) {
        prefs.edit().remove(K_SECRET + id).apply()
        save(_targets.value.filterNot { it.id == id })
        saveStatus(_status.value - id)
    }

    fun password(id: String): String? = prefs.getString(K_SECRET + id, null)?.let { runCatching { Secrets.decrypt(it) }.getOrNull() }

    private val _sealed = MutableStateFlow(prefs.contains(K_BACKUP_PASSWORD))
    /** Whether new backups are sealed with a backup password. */
    val sealed: StateFlow<Boolean> = _sealed

    fun backupPassword(): String? = prefs.getString(K_BACKUP_PASSWORD, null)?.let { runCatching { Secrets.decrypt(it) }.getOrNull() }

    /**
     * Sets (or with null, clears) the backup password. Every place is then due again, so the next backup is made
     * the new way; backups already made stay as they were.
     */
    fun setBackupPassword(password: String?) {
        prefs.edit().apply { if (password == null) remove(K_BACKUP_PASSWORD) else putString(K_BACKUP_PASSWORD, Secrets.encrypt(password)) }.apply()
        _sealed.value = password != null
        saveStatus(_status.value.mapValues { it.value.copy(dataStamp = 0) })
    }

    fun record(id: String, status: BackupStatus) = saveStatus(_status.value + (id to status))

    private fun save(list: List<BackupTarget>) {
        prefs.edit().putString(K_TARGETS, MarginJson.encodeToString(list)).apply()
        _targets.value = list
    }

    private fun saveStatus(map: Map<String, BackupStatus>) {
        prefs.edit().putString(K_STATUS, MarginJson.encodeToString(map)).apply()
        _status.value = map
    }

    private fun readTargets(): List<BackupTarget> =
        prefs.getString(K_TARGETS, null)?.let { runCatching { MarginJson.decodeFromString<List<BackupTarget>>(it) }.getOrNull() }.orEmpty()

    private fun readStatus(): Map<String, BackupStatus> =
        prefs.getString(K_STATUS, null)?.let { runCatching { MarginJson.decodeFromString<Map<String, BackupStatus>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val K_TARGETS = "targets"
        const val K_STATUS = "status"
        const val K_SECRET = "secret."
        const val K_BACKUP_PASSWORD = "backup_password"
    }
}

/** AES-GCM with a key that never leaves the Android Keystore. */
private object Secrets {
    private const val ALIAS = "margin-backup-secrets"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
            )
        }.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = Base64.getEncoder()
        return enc.encodeToString(c.iv) + "." + enc.encodeToString(c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(sealed: String): String {
        val (iv, data) = sealed.split('.').map { Base64.getDecoder().decode(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return String(c.doFinal(data), Charsets.UTF_8)
    }
}

// ---------- Talking to a place ----------

/** The few things a backup place has to do: list, write, read and delete backup files in one folder. */
interface BackupDestination {
    suspend fun list(): List<String>
    suspend fun write(name: String, file: File)
    suspend fun <T> read(name: String, reader: (InputStream) -> T): T
    suspend fun delete(name: String)
}

/** A folder picked through the file picker (Storage Access Framework). */
class FolderDestination(context: Context, private val tree: Uri) : BackupDestination {
    private val resolver = context.contentResolver
    private val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun files(): Map<String, Uri> {
        val out = mutableMapOf<String, Uri>()
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        } ?: throw IOException("Can't open the backup folder. Pick it again in Settings.")
        return out
    }

    override suspend fun list() = withContext(Dispatchers.IO) { files().keys.filter(::isBackupName) }

    override suspend fun write(name: String, file: File) = withContext(Dispatchers.IO) {
        val part = "$name.part"
        files()[part]?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        // Written under a .part name and renamed once whole, so a cut-off copy never looks like a backup.
        // Some cloud apps can't rename; those get the final name from the start.
        val doc = DocumentsContract.createDocument(resolver, root, "application/zip", part)
            ?: throw IOException("The backup folder didn't accept a new file")
        try {
            copyInto(doc, file)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        val renamed = runCatching { DocumentsContract.renameDocument(resolver, doc, name) }.getOrNull()
        if (renamed == null) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            val direct = DocumentsContract.createDocument(resolver, root, "application/zip", name)
                ?: throw IOException("The backup folder didn't accept a new file")
            try { copyInto(direct, file) } catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, direct) }; throw e }
        }
        Unit
    }

    private fun copyInto(doc: Uri, file: File) {
        (resolver.openOutputStream(doc, "w") ?: throw IOException("Can't write to the backup folder")).use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    override suspend fun <T> read(name: String, reader: (InputStream) -> T): T = withContext(Dispatchers.IO) {
        val uri = files()[name] ?: throw IOException("$name isn't in the backup folder any more")
        (resolver.openInputStream(uri) ?: throw IOException("Can't read $name")).use(reader)
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        files()[name]?.let { DocumentsContract.deleteDocument(resolver, it) }
        Unit
    }
}

class WebDavException(val code: Int, message: String) : IOException(message)

/** A WebDAV folder: PROPFIND to list, PUT to write (to a .part name, then MOVE), GET and DELETE. */
class WebDavDestination(url: String, user: String, password: String) : BackupDestination {
    private val base = url.trim().trimEnd('/') + "/"
    private val auth = Credentials.basic(user, password, Charsets.UTF_8)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

    private fun request(path: String = "") = Request.Builder().url(base + path).header("Authorization", auth)

    private fun call(r: Request, ok: (Int) -> Boolean = { it in 200..299 }): okhttp3.Response {
        val res = http.newCall(r).execute()
        if (!ok(res.code)) {
            res.close()
            throw WebDavException(res.code, when (res.code) {
                401, 403 -> "The server refused the username or password"
                404 -> "The server has no folder at that address"
                507 -> "The server is out of space"
                else -> "The server answered ${res.code}"
            })
        }
        return res
    }

    /** Checks the address and sign-in, making the folder if the server doesn't have it yet. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val probe = http.newCall(propfind("")).execute().also { it.close() }
        when (probe.code) {
            in 200..299 -> Unit
            404 -> call(request().method("MKCOL", null).build()).close()
            401, 403 -> throw WebDavException(probe.code, "The server refused the username or password")
            else -> throw WebDavException(probe.code, "That address didn't answer as a WebDAV folder (${probe.code})")
        }
    }

    private fun propfind(path: String) = request(path).header("Depth", "1")
        .method("PROPFIND", """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>""".toRequestBody("application/xml".toMediaType()))
        .build()

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        val xml = call(propfind("")).use { it.body?.string().orEmpty() }
        Regex("""<(?:[A-Za-z0-9]+:)?href>([^<]+)</(?:[A-Za-z0-9]+:)?href>""").findAll(xml)
            .map { java.net.URLDecoder.decode(it.groupValues[1].trimEnd('/').substringAfterLast('/'), "UTF-8") }
            .filter(::isBackupName).distinct().toList()
    }

    override suspend fun write(name: String, file: File) = withContext(Dispatchers.IO) {
        val body = object : RequestBody() {
            override fun contentType() = "application/zip".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) { file.source().use { sink.writeAll(it) } }
        }
        val part = enc("$name.part")
        call(request(part).put(body).build()).close()
        call(request(part).method("MOVE", null).header("Destination", base + enc(name)).header("Overwrite", "T").build()).close()
    }

    override suspend fun <T> read(name: String, reader: (InputStream) -> T): T = withContext(Dispatchers.IO) {
        call(request(enc(name)).get().build()).use { res -> reader(res.body?.byteStream() ?: throw IOException("Empty answer")) }
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        call(request(enc(name)).delete().build(), ok = { it in 200..299 || it == 404 }).close()
    }

    private fun enc(name: String) = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
}

// ---------- Making and restoring backups ----------

data class BackupRun(val made: Int, val skipped: Int, val failed: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MarginDatabase,
    private val repo: NoteRepository,
    private val files: AttachmentFiles,
    private val prefs: BackupPrefs,
    private val scheduler: SyncScheduler,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun destination(t: BackupTarget): BackupDestination = when (t) {
        is BackupTarget.Folder -> FolderDestination(context, Uri.parse(t.tree))
        is BackupTarget.WebDav -> WebDavDestination(t.url, t.user, prefs.password(t.id) ?: throw IOException("The password for ${t.label} is missing. Add the server again."))
    }

    /** Newest change to any note or folder: unchanged since a place's last backup means nothing to do there. */
    private suspend fun dataStamp(): Long =
        maxOf(db.notes().all().maxOfOrNull { it.updatedAt } ?: 0, db.folders().all().maxOfOrNull { it.updatedAt } ?: 0)

    /**
     * Backs up to every place ([only] for just one). One zip is made and copied to each; a place whose last
     * backup already holds everything is skipped unless [force]. Each place records how it went.
     */
    suspend fun backUp(only: String? = null, force: Boolean = false): BackupRun = mutex.withLock {
        val targets = prefs.targets.value.filter { only == null || it.id == only }
        val stamp = dataStamp()
        val due = targets.filter { force || (prefs.status.value[it.id]?.dataStamp ?: 0) < stamp || prefs.status.value[it.id]?.lastError != null }
        if (due.isEmpty()) return BackupRun(0, targets.size, 0)
        _running.value = true
        val zip = File(context.cacheDir, "backup-out.zip")
        val sealedZip = File(context.cacheDir, "backup-out.sealed")
        sealedZip.delete() // a leftover from a run that was cut off must not be sent
        try {
            val now = System.currentTimeMillis()
            val name = backupName(now)
            withContext(Dispatchers.IO) {
                zip.outputStream().buffered().use { out ->
                    val inUse = repo.attachmentsInUse()
                    writeBackup(out, db.folders().all(), db.notes().all(), inUse, { n -> File(files.dir, n).takeIf { it.isFile }?.inputStream() }, now)
                }
                prefs.backupPassword()?.let { pw ->
                    zip.inputStream().buffered().use { input -> sealedZip.outputStream().buffered().use { BackupCrypto.seal(input, it, pw.toCharArray()) } }
                }
            }
            val upload = if (sealedZip.exists()) sealedZip else zip
            var made = 0
            var failed = 0
            for (t in due) {
                try {
                    val d = destination(t)
                    d.write(name, upload)
                    backupsToPrune(d.list(), KEEP_BACKUPS).forEach { runCatching { d.delete(it) } }
                    prefs.record(t.id, BackupStatus(lastAt = now, lastName = name, dataStamp = stamp))
                    made++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    prefs.record(t.id, (prefs.status.value[t.id] ?: BackupStatus()).copy(lastError = e.message ?: e.javaClass.simpleName))
                    failed++
                }
            }
            BackupRun(made, targets.size - due.size, failed)
        } finally {
            zip.delete()
            sealedZip.delete()
            _running.value = false
        }
    }

    /** Newest first. */
    suspend fun backupsIn(t: BackupTarget): List<String> = destination(t).list().sortedDescending()

    /**
     * Restores the backup [name] from [t]; see [restoreFile]. A sealed one opens with [password], or else the
     * backup password set here; without either, or with the wrong one, it throws [BackupPasswordNeededException]
     * or [WrongBackupPasswordException] so the user can be asked.
     */
    suspend fun restore(t: BackupTarget, name: String, password: String? = null): Int {
        val tmp = File(context.cacheDir, "backup-in.zip")
        try {
            destination(t).read(name) { input -> tmp.outputStream().use { input.copyTo(it) } }
            return restoreFile(tmp, password)
        } finally { tmp.delete() }
    }

    /** Restores a backup file the user picked (from any app's storage). */
    suspend fun restore(uri: Uri, password: String? = null): Int {
        val tmp = File(context.cacheDir, "backup-in.zip")
        try {
            withContext(Dispatchers.IO) {
                (context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open that file")).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            return restoreFile(tmp, password)
        } finally { tmp.delete() }
    }

    /** [file] as a plain backup zip: itself, or opened into a temp file if it's sealed. */
    private suspend fun opened(file: File, password: String?): File = withContext(Dispatchers.IO) {
        if (!file.inputStream().use { BackupCrypto.isSealed(it) }) return@withContext file
        val tried = listOfNotNull(password, prefs.backupPassword()).distinct()
        if (tried.isEmpty()) throw BackupPasswordNeededException()
        val out = File(context.cacheDir, "backup-in.opened.zip")
        for (pw in tried) {
            try {
                file.inputStream().buffered().use { input -> out.outputStream().buffered().use { BackupCrypto.open(input, it, pw.toCharArray()) } }
                return@withContext out
            } catch (_: WrongBackupPasswordException) { out.delete() }
        }
        // A password typed in was wrong; the saved one alone was wrong means this backup has an older password.
        if (password != null) throw WrongBackupPasswordException() else throw BackupPasswordNeededException()
    }

    /**
     * Adds what a backup holds that this device lacks or has older (see [restorePlan]), with its files, then
     * syncs. Returns how many notes came back.
     */
    private suspend fun restoreFile(file: File, password: String?): Int = mutex.withLock {
        val zip = opened(file, password)
        try { restoreZip(zip) } finally { if (zip != file) zip.delete() }
    }

    private suspend fun restoreZip(zip: File): Int {
        val contents = withContext(Dispatchers.IO) {
            zip.inputStream().buffered().use { input ->
                readBackup(input) { name, stream ->
                    val file = File(files.dir, name)
                    if (!file.exists()) {
                        val tmp = File(files.dir, "$name.part")
                        tmp.outputStream().use { stream.copyTo(it) }
                        tmp.renameTo(file)
                    }
                }
            }
        }
        val plan = restorePlan(db.folders().all(), db.notes().all(), contents)
        db.withTransaction {
            plan.folders.forEach { db.folders().upsert(it) }
            plan.notes.forEach { db.notes().upsert(it) }
        }
        repo.ensureDefaultFolder()
        repo.fixOrphans()
        scheduler.requestNow()
        return plan.notes.size
    }
}

// ---------- Daily, in the background ----------

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun backups(): BackupManager }

    override suspend fun doWork(): Result {
        val run = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).backups().backUp()
        return if (run.failed > 0 && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val DAILY = "margin-backup-daily"
        private const val NOW = "margin-backup-now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Daily while there's any place to back up to. */
        fun schedule(context: Context, anyTargets: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!anyTargets) { wm.cancelUniqueWork(DAILY); return }
            wm.enqueueUniquePeriodicWork(
                DAILY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).setConstraints(network).build(),
            )
        }

        /** Soon after a place is added, so it gets its first backup. */
        fun runSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(network).build(),
            )
        }
    }
}

fun newBackupId() = "b-" + newId()
