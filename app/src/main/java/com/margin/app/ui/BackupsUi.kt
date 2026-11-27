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
import com.margin.app.data.BackupPasswordNeededException
import com.margin.app.data.WrongBackupPasswordException
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
import com.margin.app.R
import androidx.compose.ui.res.stringResource

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
    val sealed: StateFlow<Boolean> = prefs.sealed

    /** A restore waiting for the backup's password: what to restore, and whether a password was already tried. */
    data class PasswordAsk(val target: BackupTarget?, val name: String?, val uri: Uri?, val wrong: Boolean)
    var asking by mutableStateOf<PasswordAsk?>(null)
        private set

    fun setBackupPassword(password: String?, onDone: (String) -> Unit) {
        prefs.setBackupPassword(password)
        onDone(if (password == null) context.getString(R.string.backups_backups_are_no_longer_protected) else context.getString(R.string.backups_from_now_on_backups_are))
        if (prefs.targets.value.isNotEmpty()) BackupWorker.runSoon(context)
    }

    private fun reschedule() = BackupWorker.schedule(context, prefs.targets.value.isNotEmpty())

    /** A folder picked in the file picker: kept with lasting access, named after its app and folder. */
    fun addFolder(tree: Uri, onDone: (String) -> Unit) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.onFailure { onDone(context.getString(R.string.backups_that_place_cant_keep_backups)); return }
        val target = BackupTarget.Folder(newBackupId(), folderLabel(tree), tree.toString())
        prefs.add(target)
        reschedule()
        onDone(context.getString(R.string.backups_added_making_the_first_backup))
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
        return listOfNotNull(app ?: context.getString(R.string.backups_this_phone), folder).joinToString(" · ")
    }

    /** Checks the server and sign-in (making the folder if needed) before adding it. */
    fun addWebDav(url: String, user: String, password: String, onError: (String) -> Unit, onDone: (String) -> Unit) = viewModelScope.launch {
        val address = url.trim().let { if ("://" in it) it else "https://$it" }
        try {
            WebDavDestination(context, address, user.trim(), password).connect()
        } catch (e: Exception) {
            onError(when (e) {
                is java.net.UnknownHostException -> context.getString(R.string.backups_couldnt_find_that_server_check)
                is java.net.SocketTimeoutException, is java.net.ConnectException -> context.getString(R.string.backups_the_server_didnt_answer_check)
                is javax.net.ssl.SSLException -> context.getString(R.string.backups_couldnt_make_a_secure_connection)
                is com.margin.app.sync.WebDavException -> errorText(context, e) ?: context.getString(R.string.backups_the_server_refused)
                else -> context.getString(R.string.backups_couldnt_reach_the_server)
            }); return@launch
        }
        val host = runCatching { Uri.parse(address).host }.getOrNull() ?: address
        val target = BackupTarget.WebDav(newBackupId(), "WebDAV · " + host.removePrefix("www."), address, user.trim())
        prefs.add(target, password)
        reschedule()
        onDone(context.getString(R.string.backups_added_making_the_first_backup))
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
            run.failed > 0 && run.made == 0 -> context.getString(R.string.backups_backup_failed_see_the_error)
            run.failed > 0 -> context.getString(R.string.backups_backed_up_to_failed, run.made, run.failed)
            else -> context.getString(R.string.backups_backed_up)
        })
    }

    suspend fun list(t: BackupTarget): List<String> = backups.backupsIn(t)

    fun restore(t: BackupTarget?, name: String?, uri: Uri?, onDone: (String) -> Unit, password: String? = null) = viewModelScope.launch {
        asking = null
        restoring = true
        val msg = try {
            val n = if (t != null && name != null) backups.restore(t, name, password) else backups.restore(uri!!, password)
            if (n == 0) context.getString(R.string.backups_restored_this_device_already_had) else context.resources.getQuantityString(R.plurals.backups_restored_notes, n, n)
        } catch (_: BackupPasswordNeededException) {
            asking = PasswordAsk(t, name, uri, wrong = false); null
        } catch (_: WrongBackupPasswordException) {
            asking = PasswordAsk(t, name, uri, wrong = true); null
        } catch (e: Exception) {
            context.getString(R.string.backups_restore_failed, errorText(context, e) ?: e.javaClass.simpleName)
        }
        restoring = false
        msg?.let(onDone)
    }

    fun cancelAsk() { asking = null }
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
    val sealed by vm.sealed.collectAsState()
    var settingPassword by remember { mutableStateOf(false) }
    var clearingPassword by remember { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.addFolder(uri, toast) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }

    if (addingWebDav) WebDavDialog(vm, onDismiss = { addingWebDav = false }, onAdded = { addingWebDav = false; toast(it) })
    opened?.let { t -> TargetSheet(vm, t, status[t.id], running, onDismiss = { opened = null }, toast = toast) }
    if (settingPassword) BackupPasswordDialog(changing = sealed, onDismiss = { settingPassword = false }) { settingPassword = false; vm.setBackupPassword(it, toast) }
    if (clearingPassword) MarginDialog(
        onDismissRequest = { clearingPassword = false },
        title = { Text(stringResource(R.string.backups_stop_protecting_backups)) },
        text = { Text(stringResource(R.string.backups_new_backups_will_be_readable)) },
        confirmButton = { TextButton(onClick = { clearingPassword = false; vm.setBackupPassword(null, toast) }) { Text(stringResource(R.string.backups_turn_off), color = jc.danger) } },
        dismissButton = { TextButton(onClick = { clearingPassword = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
    vm.asking?.let { ask ->
        RestorePasswordDialog(ask.wrong, onDismiss = vm::cancelAsk) { pw -> vm.restore(ask.target, ask.name, ask.uri, toast, pw) }
    }
    restoreUri?.let { uri ->
        RestoreConfirm(stringResource(R.string.backups_the_file_you_picked), onDismiss = { restoreUri = null }) { restoreUri = null; vm.restore(null, null, uri, toast) }
    }

    SettingsLabel(stringResource(R.string.backups_backups))
    Card {
        targets.forEachIndexed { i, t ->
            if (i > 0) CardDivider()
            val s = status[t.id]
            CardRow(
                t.label, icon = if (t is BackupTarget.WebDav) Ico.GLOBE else Ico.CLOUD,
                sub = when {
                    s?.lastError != null -> stringResource(R.string.backups_couldnt_back_up, s.lastError)
                    s?.lastAt != null -> stringResource(R.string.backups_last_backup, listTime(context, s.lastAt))
                    else -> stringResource(R.string.backups_no_backup_yet)
                },
                onClick = { opened = t },
            ) { JIcon(Ico.CHEVRON, jc.muted, size = 16.dp, stroke = 2f) }
        }
        if (targets.isNotEmpty()) CardDivider()
        CardRow(
            stringResource(R.string.backups_add_a_cloud_or_folder), icon = Ico.PLUS,
            sub = stringResource(R.string.backups_dropbox_onedrive_box_nextcloud_mega),
            onClick = { pickFolder.launch(null) },
        )
        CardDivider()
        CardRow(
            stringResource(R.string.backups_add_a_webdav_server), icon = Ico.GLOBE,
            sub = stringResource(R.string.backups_nextcloud_owncloud_koofr_a_home), onClick = { addingWebDav = true },
        )
        CardDivider()
        CardRow(
            stringResource(R.string.backups_backup_password), icon = Ico.LOCK,
            sub = if (sealed) stringResource(R.string.backups_on_backups_can_only_be) else stringResource(R.string.backups_off_anyone_who_can_open),
            onClick = { settingPassword = true },
        ) {
            JSwitch(sealed, { on -> if (on) settingPassword = true else clearingPassword = true })
        }
        if (targets.isNotEmpty()) {
            CardDivider()
            CardRow(
                if (running) stringResource(R.string.backups_backing_up) else stringResource(R.string.backups_back_up_now), icon = Ico.ARCHIVE,
                labelColor = if (running) jc.muted else jc.accent,
                sub = stringResource(R.string.backups_daily_on_its_own_when, KEEP_BACKUPS),
                onClick = if (running) null else ({ vm.backUp(null, toast) }),
            ) { if (running) CircularProgressIndicator(Modifier.size(18.dp), color = jc.accent, strokeWidth = 2.dp) }
        }
        CardDivider()
        CardRow(
            if (vm.restoring) stringResource(R.string.backups_restoring) else stringResource(R.string.backups_restore_from_a_backup_file), icon = Ico.ARROW_DOWN,
            labelColor = if (vm.restoring) jc.muted else jc.ink,
            sub = stringResource(R.string.backups_a_margin_backup_zip_from),
            onClick = if (vm.restoring) null else ({ pickFile.launch(arrayOf("application/zip", "application/octet-stream")) }),
        )
    }
    Text(
        if (sealed) stringResource(R.string.backups_backups_hold_every_note_and)
        else stringResource(R.string.backups_backups_hold_every_note_and_2),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), style = JType.small, color = jc.muted,
    )
}

@Composable
private fun RestoreConfirm(what: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = MarginDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.backups_restore_from, what)) },
    text = { Text(stringResource(R.string.backups_notes_and_folders_this_phone)) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_restore), fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
        runCatching { vm.list(t) }.onSuccess { names = it; error = null }.onFailure { error = errorText(context, it) ?: context.getString(R.string.backups_couldnt_read_the_backups_there) }
    }
    restoring?.let { name ->
        RestoreConfirm(backupTimeOf(name)?.let { fullTime(context, it) } ?: name, onDismiss = { restoring = null }) {
            restoring = null; onDismiss(); vm.restore(t, name, null, toast)
        }
    }
    if (removing) MarginDialog(
        onDismissRequest = { removing = false },
        title = { Text(stringResource(R.string.backups_stop_backing_up_here)) },
        text = { Text(stringResource(R.string.backups_backups_already_made_there_stay)) },
        confirmButton = { TextButton(onClick = { removing = false; onDismiss(); vm.remove(t) }) { Text(stringResource(R.string.common_remove), color = jc.danger) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.label) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        s?.lastError != null -> stringResource(R.string.backups_the_last_backup_failed, s.lastError)
                        s?.lastAt != null -> stringResource(R.string.backups_last_backup_full, fullTime(context, s.lastAt))
                        else -> stringResource(R.string.backups_no_backup_here_yet)
                    },
                    color = if (s?.lastError != null) jc.danger else jc.muted,
                )
                if (t is BackupTarget.WebDav) Text(stringResource(R.string.backups_at, t.user, t.url), style = JType.small, color = jc.muted)
                Text(stringResource(R.string.backups_restore_from_2), Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
                when {
                    error != null -> Text(error!!, style = JType.small, color = jc.danger)
                    names == null -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = jc.accent, strokeWidth = 2.dp)
                    }
                    names!!.isEmpty() -> Text(stringResource(R.string.backups_no_backups_there_yet), style = JType.small, color = jc.muted)
                    else -> Card {
                        names!!.forEachIndexed { i, n ->
                            if (i > 0) CardDivider()
                            CardRow(
                                backupTimeOf(n)?.let { fullTime(context, it) } ?: n,
                                sub = if (i == 0) stringResource(R.string.backups_newest) else null, onClick = { restoring = n },
                            ) { JIcon(Ico.ARROW_DOWN, jc.accent, size = 18.dp, stroke = 2f) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.backUp(t.id, toast) }, enabled = !running) { Text(if (running) stringResource(R.string.backups_backing_up) else stringResource(R.string.backups_back_up_now_2), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { removing = true }) { Text(stringResource(R.string.common_remove), color = jc.danger) } },
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
                Text(stringResource(R.string.backups_add_a_webdav_server))
                error?.let { Text(it, color = jc.danger, style = JType.small) }
                if (checking) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = jc.accent, strokeWidth = 2.dp)
                    Text(stringResource(R.string.backups_checking_server), style = JType.small, color = jc.muted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backups_nextclouds_address_looks_like_cloud))
                OutlinedTextField(url, { url = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.backups_folder_address)) }, shape = JShape.sm,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                OutlinedTextField(user, { user = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.backups_username)) }, shape = JShape.sm,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
                PasswordField(password, { password = it; error = null }, stringResource(R.string.backups_password))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    checking = true
                    vm.addWebDav(url, user, password, onError = { checking = false; error = it }) { checking = false; onAdded(it) }
                },
                enabled = !checking && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(),
            ) { Text(stringResource(R.string.common_add), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Sets or changes the backup password: typed twice, with a plain warning that it can't be recovered. */
@Composable
private fun BackupPasswordDialog(changing: Boolean, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val tooShort = pw.length in 1 until 8
    val mismatch = again.isNotEmpty() && again != pw
    MarginDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (changing) stringResource(R.string.backups_change_backup_password) else stringResource(R.string.backups_protect_backups))
                when {
                    tooShort -> Text(stringResource(R.string.backups_use_at_least_8_characters), color = jc.danger, style = JType.small)
                    mismatch -> Text(stringResource(R.string.backups_the_two_passwords_dont_match), color = jc.danger, style = JType.small)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (changing) R.string.backups_sealed_intro_changing else R.string.backups_sealed_intro),
                )
                PasswordField(pw, { pw = it }, stringResource(R.string.backups_password))
                PasswordField(again, { again = it }, stringResource(R.string.backups_again))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(pw) }, enabled = pw.length >= 8 && again == pw) { Text(if (changing) stringResource(R.string.common_change) else stringResource(R.string.backups_turn_on), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** A sealed backup's password, asked for at restore. */
@Composable
private fun RestorePasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onRestore: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.backups_backup_password_2))
                if (wrong) Text(stringResource(R.string.backups_that_password_doesnt_open_this), color = jc.danger, style = JType.small)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backups_this_backup_is_protected_enter))
                PasswordField(pw, { pw = it }, stringResource(R.string.backups_password))
            }
        },
        confirmButton = { TextButton(onClick = { onRestore(pw) }, enabled = pw.isNotEmpty()) { Text(stringResource(R.string.common_restore), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) = OutlinedTextField(
    value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, shape = JShape.sm,
    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
)
