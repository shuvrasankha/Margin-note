package com.margin.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.backupTimeOf
import com.margin.app.sync.BackupManager
import com.margin.app.sync.BackupPrefs
import com.margin.app.sync.BackupStatus
import com.margin.app.sync.BackupTarget
import com.margin.app.sync.BackupWorker
import com.margin.app.sync.KEEP_BACKUPS
import com.margin.app.sync.WebDavDestination
import com.margin.app.sync.newBackupId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPrefs,
    private val backups: BackupManager,
) : ViewModel() {
    val targets: StateFlow<List<BackupTarget>> = prefs.targets
    val status: StateFlow<Map<String, BackupStatus>> = prefs.status
    val running: StateFlow<Boolean> = backups.running
    var restoring by mutableStateOf(false)
        private set

    private fun reschedule() = BackupWorker.schedule(context, prefs.targets.value.isNotEmpty())

    /** A folder picked in the file picker: kept with lasting access, named after its app and folder. */
    fun addFolder(tree: Uri, onDone: (String) -> Unit) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.onFailure { onDone("That place can't keep backups. Try another folder or app."); return }
        val target = BackupTarget.Folder(newBackupId(), folderLabel(tree), tree.toString())
        prefs.add(target)
        reschedule()
        onDone("Added. Making the first backup…")
        backUp(target.id, onDone)
    }

    private fun folderLabel(tree: Uri): String {
        val app = tree.authority?.let { a ->
            runCatching { context.packageManager.resolveContentProvider(a, 0)?.loadLabel(context.packageManager)?.toString() }.getOrNull()
        }?.takeIf { it.isNotBlank() && !it.contains("documents", ignoreCase = true) && !it.contains("external storage", ignoreCase = true) }
        val folder = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return listOfNotNull(app ?: "This phone", folder).joinToString(" · ")
    }

    /** Checks the server and sign-in (making the folder if needed) before adding it. */
    fun addWebDav(url: String, user: String, password: String, onError: (String) -> Unit, onDone: (String) -> Unit) = viewModelScope.launch {
        val address = url.trim().let { if ("://" in it) it else "https://$it" }
        try {
            WebDavDestination(address, user.trim(), password).connect()
        } catch (e: Exception) {
            onError(when (e) {
                is java.net.UnknownHostException -> "Couldn't find that server. Check the address, and that you're online."
                is java.net.SocketTimeoutException, is java.net.ConnectException -> "The server didn't answer. Check the address and try again."
                is javax.net.ssl.SSLException -> "Couldn't make a secure connection to that server."
                is com.margin.app.sync.WebDavException -> e.message ?: "The server refused"
                else -> "Couldn't reach the server"
            }); return@launch
        }
        val host = runCatching { Uri.parse(address).host }.getOrNull() ?: address
        val target = BackupTarget.WebDav(newBackupId(), "WebDAV · " + host.removePrefix("www."), address, user.trim())
        prefs.add(target, password)
        reschedule()
        onDone("Added. Making the first backup…")
        backUp(target.id, onDone)
    }

    fun remove(t: BackupTarget) {
        if (t is BackupTarget.Folder) runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(t.tree), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs.remove(t.id)
        reschedule()
    }

    fun backUp(only: String? = null, onDone: (String) -> Unit) = viewModelScope.launch {
        val run = backups.backUp(only, force = true)
        onDone(when {
            run.failed > 0 && run.made == 0 -> "Backup failed. See the error under the place."
            run.failed > 0 -> "Backed up to ${run.made}, ${run.failed} failed"
            else -> "Backed up"
        })
    }

    suspend fun list(t: BackupTarget): List<String> = backups.backupsIn(t)

    fun restore(t: BackupTarget?, name: String?, uri: Uri?, onDone: (String) -> Unit) = viewModelScope.launch {
        restoring = true
        val msg = try {
            val n = if (t != null && name != null) backups.restore(t, name) else backups.restore(uri!!)
            if (n == 0) "Restored. This device already had everything in that backup." else "Restored ${plural(n, "note")}"
        } catch (e: Exception) {
            "Restore failed: ${e.message ?: e.javaClass.simpleName}"
        }
        restoring = false
        onDone(msg)
    }
}

/** Settings: the places full backups go besides Drive sync, with Back up now and restore. */
@Composable
fun BackupsSection(vm: BackupsViewModel) {
    val context = LocalContext.current
    val targets by vm.targets.collectAsState()
    val status by vm.status.collectAsState()
    val running by vm.running.collectAsState()
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    var addingWebDav by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<BackupTarget?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.addFolder(uri, toast) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }

    if (addingWebDav) WebDavDialog(vm, onDismiss = { addingWebDav = false }, onAdded = { addingWebDav = false; toast(it) })
    opened?.let { t -> TargetSheet(vm, t, status[t.id], running, onDismiss = { opened = null }, toast = toast) }
    restoreUri?.let { uri ->
        RestoreConfirm("the file you picked", onDismiss = { restoreUri = null }) { restoreUri = null; vm.restore(null, null, uri, toast) }
    }

    SettingsLabel("Backups")
    Card {
        targets.forEachIndexed { i, t ->
            if (i > 0) CardDivider()
            val s = status[t.id]
            CardRow(
                t.label, icon = if (t is BackupTarget.WebDav) Ico.GLOBE else Ico.CLOUD,
                sub = when {
                    s?.lastError != null -> "Couldn't back up: " + s.lastError
                    s?.lastAt != null -> "Last backup " + listTime(context, s.lastAt)
                    else -> "No backup yet"
                },
                onClick = { opened = t },
            ) { JIcon(Ico.CHEVRON, jc.muted, size = 16.dp, stroke = 2f) }
        }
        if (targets.isNotEmpty()) CardDivider()
        CardRow(
            "Add a cloud or folder", icon = Ico.PLUS, labelColor = jc.accent,
            sub = "Dropbox, OneDrive, Box, Nextcloud, MEGA or any app in the file picker, or a folder on this phone.",
            onClick = { pickFolder.launch(null) },
        )
        CardDivider()
        CardRow(
            "Add a WebDAV server", icon = Ico.GLOBE, labelColor = jc.accent,
            sub = "Nextcloud, ownCloud, Koofr, a home NAS…", onClick = { addingWebDav = true },
        )
        if (targets.isNotEmpty()) {
            CardDivider()
            CardRow(
                if (running) "Backing up…" else "Back up now", icon = Ico.ARCHIVE,
                labelColor = if (running) jc.muted else jc.accent,
                sub = "Daily on its own, when something changed. Each place keeps the last $KEEP_BACKUPS.",
                onClick = if (running) null else ({ vm.backUp(null, toast) }),
            ) { if (running) CircularProgressIndicator(Modifier.size(18.dp), color = jc.accent, strokeWidth = 2.dp) }
        }
        CardDivider()
        CardRow(
            if (vm.restoring) "Restoring…" else "Restore from a backup file", icon = Ico.ARROW_DOWN,
            labelColor = if (vm.restoring) jc.muted else jc.accent,
            sub = "A margin-backup zip from anywhere, Google Drive's app included.",
            onClick = if (vm.restoring) null else ({ pickFile.launch(arrayOf("application/zip", "application/octet-stream")) }),
        )
    }
    Text(
        "Backups hold every note and file. Locked notes and saved passwords stay encrypted in them.",
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), style = JType.small, color = jc.muted,
    )
}

@Composable
private fun RestoreConfirm(what: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = MarginDialog(
    onDismissRequest = onDismiss,
    title = { Text("Restore from $what?") },
    text = { Text("Notes and folders this phone doesn't have, or has an older copy of, come back and sync to your other devices. Anything changed here since the backup stays as it is.") },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Restore", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
)

/** One place: its state, Back up now, its backups to restore from, and Remove. */
@Composable
private fun TargetSheet(vm: BackupsViewModel, t: BackupTarget, s: BackupStatus?, running: Boolean, onDismiss: () -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current
    var names by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(t.id, s?.lastAt) {
        names = null
        runCatching { vm.list(t) }.onSuccess { names = it; error = null }.onFailure { error = it.message ?: "Couldn't read the backups there" }
    }
    restoring?.let { name ->
        RestoreConfirm(backupTimeOf(name)?.let { fullTime(context, it) } ?: name, onDismiss = { restoring = null }) {
            restoring = null; onDismiss(); vm.restore(t, name, null, toast)
        }
    }
    if (removing) MarginDialog(
        onDismissRequest = { removing = false },
        title = { Text("Stop backing up here?") },
        text = { Text("Backups already made there stay where they are.") },
        confirmButton = { TextButton(onClick = { removing = false; onDismiss(); vm.remove(t) }) { Text("Remove", color = jc.danger) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
    )
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.label) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        s?.lastError != null -> "The last backup failed: ${s.lastError}"
                        s?.lastAt != null -> "Last backup " + fullTime(context, s.lastAt) + "."
                        else -> "No backup here yet."
                    },
                    color = if (s?.lastError != null) jc.danger else jc.muted,
                )
                if (t is BackupTarget.WebDav) Text("${t.user} at ${t.url}", style = JType.small, color = jc.muted)
                Text("RESTORE FROM", Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
                when {
                    error != null -> Text(error!!, style = JType.small, color = jc.danger)
                    names == null -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = jc.accent, strokeWidth = 2.dp)
                    }
                    names!!.isEmpty() -> Text("No backups there yet.", style = JType.small, color = jc.muted)
                    else -> Card {
                        names!!.forEachIndexed { i, n ->
                            if (i > 0) CardDivider()
                            CardRow(
                                backupTimeOf(n)?.let { fullTime(context, it) } ?: n,
                                sub = if (i == 0) "Newest" else null, onClick = { restoring = n },
                            ) { JIcon(Ico.ARROW_DOWN, jc.accent, size = 18.dp, stroke = 2f) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.backUp(t.id, toast) }, enabled = !running) { Text(if (running) "Backing up…" else "Back Up Now", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { removing = true }) { Text("Remove", color = jc.danger) } },
    )
}

@Composable
private fun WebDavDialog(vm: BackupsViewModel, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MarginDialog(
        onDismissRequest = onDismiss,
        // The status sits under the title, not in the text: the text scrolls, and with the keyboard up the
        // bottom of it is out of sight.
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Add a WebDAV server")
                error?.let { Text(it, color = jc.danger, style = JType.small) }
                if (checking) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = jc.accent, strokeWidth = 2.dp)
                    Text("  Checking the server…", style = JType.small, color = jc.muted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nextcloud's address looks like cloud.example.com/remote.php/dav/files/you/Margin. Use an app password if the server offers them.")
                OutlinedTextField(url, { url = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Folder address") }, shape = JShape.sm,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                OutlinedTextField(user, { user = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") }, shape = JShape.sm,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
                OutlinedTextField(password, { password = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") }, shape = JShape.sm,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    checking = true
                    vm.addWebDav(url, user, password, onError = { checking = false; error = it }) { checking = false; onAdded(it) }
                },
                enabled = !checking && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(),
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
