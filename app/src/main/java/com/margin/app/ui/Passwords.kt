package com.margin.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.Block
import com.margin.app.data.BlockType
import com.margin.app.data.DEFAULT_FOLDER_ID
import com.margin.app.data.LOGIN_PREFIX
import com.margin.app.data.LOGIN_TITLE
import com.margin.app.data.LockSession
import com.margin.app.data.Login
import com.margin.app.data.NoteCrypto
import com.margin.app.data.NoteRepository
import com.margin.app.data.PasswordGen
import com.margin.app.data.Strength
import com.margin.app.data.isLogin
import com.margin.app.data.loginBody
import com.margin.app.data.loginFrom
import com.margin.app.data.newId
import com.margin.app.data.normalizeUrl
import com.margin.app.data.siteOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A key and a padlock's shackle: the Passwords list. */
const val ICON_PASSWORDS = "M7 11V8a5 5 0 0 1 9.6-2M5 11h14v10H5zM12 15v2"

/** A saved login, opened. */
data class VaultEntry(val id: String, val login: Login) {
    val title get() = login.name.ifBlank { siteOf(login.url) }.ifBlank { login.username }.ifBlank { "Untitled" }
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val session: LockSession,
) : ViewModel() {
    /** Every saved login, opened; null while locked (or still opening). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<VaultEntry>?> = combine(repo.liveNotes(), session.unlocked) { notes, unlocked -> notes to unlocked }
        .mapLatest { (notes, unlocked) ->
            if (!unlocked) return@mapLatest null
            notes.filter { isLogin(it) && it.cipher != null }
                .mapNotNull { n -> session.open(n.cipher!!)?.let(::loginFrom)?.let { VaultEntry(n.id, it) } }
                .sortedBy { it.title.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves [login] as a new entry ([id] null) or over an existing one. */
    fun save(id: String?, login: Login) = viewModelScope.launch {
        // All logins share one salt, so opening the list derives one key, not one per login.
        val params = repo.liveNotes().first().firstOrNull { isLogin(it) && it.cipher != null }?.cipher?.let(NoteCrypto::paramsOf)
            ?: NoteCrypto.KeyParams(NoteCrypto.newSalt(), NoteCrypto.ITERATIONS)
        val key = session.key(params) ?: return@launch
        val old = id?.let { entries.value?.firstOrNull { e -> e.id == it }?.login }
        val stamped = if (old == null || old.password != login.password) login.copy(passwordChangedAt = System.currentTimeMillis()) else login
        val body = loginBody(stamped.trimmed())
        val cipher = withContext(Dispatchers.Default) { NoteCrypto.seal(body, key, params) }
        repo.saveNote(id ?: (LOGIN_PREFIX + newId()), DEFAULT_FOLDER_ID, listOf(Block(type = BlockType.TITLE, text = LOGIN_TITLE)) + body, cipher)
    }

    fun delete(id: String) = viewModelScope.launch { repo.moveToTrash(id) }

    private fun Login.trimmed() = copy(name = name.trim(), username = username.trim(), url = url.trim(), notes = notes.trim())
}

/**
 * Copies a password or username, marked sensitive so the keyboard's clipboard preview hides it, and clears
 * the clipboard again after a minute if it still holds it.
 */
fun copySecret(context: Context, label: String, text: String, sensitive: Boolean = true) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) clip.description.extras = PersistableBundle().apply {
        putBoolean(if (Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
    if (sensitive) {
        Handler(Looper.getMainLooper()).postDelayed({
            val still = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == text }.getOrDefault(true)
            if (still) {
                if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, 60_000)
    }
    // Android 13 and later shows its own "Copied" notice.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, if (sensitive) "$label copied. It clears in a minute." else "$label copied", Toast.LENGTH_SHORT).show()
}

/** Keeps the vault out of screenshots and the recent-apps preview while it's on screen. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current as? android.app.Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Opened entry: null when no sheet; [VaultEntry.id] empty for a new login. */
private val NEW_ENTRY = VaultEntry("", Login())

/** Which logins the list shows: the health card's numbers double as filters. */
private enum class VaultFilter { ALL, REUSED, WEAK }

private fun isWeak(l: Login) = l.password.isNotEmpty() && PasswordGen.strength(l.password) == Strength.WEAK

@Composable
fun PasswordsScreen(vm: VaultViewModel, onBack: () -> Unit) {
    SecureWindow()
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(VaultFilter.ALL) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openId?.let { id -> if (id.isEmpty()) NEW_ENTRY else entries?.firstOrNull { it.id == id } }
    val list = entries.orEmpty()
    // The same password on more than one login.
    val reused = remember(list) { list.groupBy { it.login.password }.filter { (pw, e) -> pw.isNotEmpty() && e.size > 1 }.values.flatten().map { it.id }.toSet() }
    val weak = remember(list) { list.filter { isWeak(it.login) }.map { it.id }.toSet() }
    open?.let { e ->
        LoginSheet(
            e, isNew = e.id.isEmpty(), reused = e.id in reused,
            onSave = { vm.save(e.id.ifEmpty { null }, it); if (e.id.isEmpty()) openId = null },
            onDelete = { vm.delete(e.id); openId = null },
            onDismiss = { openId = null },
        )
    }
    val shown = remember(list, query, filter, reused, weak) {
        val q = query.trim().lowercase()
        list.filter { e ->
            when (filter) { VaultFilter.ALL -> true; VaultFilter.REUSED -> e.id in reused; VaultFilter.WEAK -> e.id in weak }
        }.filter { e -> q.isEmpty() || listOf(e.title, e.login.username, e.login.url, e.login.notes).any { q in it.lowercase() } }
    }
    Scaffold(
        containerColor = jc.page,
        topBar = { NavBar("Folders", onBack, color = jc.page) },
        bottomBar = {
            if (list.isNotEmpty()) BottomBar(Modifier.background(jc.page)) {
                Spacer(Modifier.size(44.dp))
                Text(
                    if (query.isBlank() && filter == VaultFilter.ALL) plural(list.size, "Password") else plural(shown.size, "Result"),
                    style = JType.small, color = jc.muted,
                )
                IconAction(Ico.PLUS, "New password", { openId = "" }, size = 24.dp)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { PageTitle("Passwords") }
            when {
                entries == null -> item {
                    Text("Opening…", Modifier.padding(horizontal = 20.dp), style = JType.secondary, color = jc.muted)
                }
                list.isEmpty() -> item { VaultEmpty(onAdd = { openId = "" }) }
                else -> {
                    item {
                        SearchField(query, { query = it }, "Search passwords", Modifier.padding(horizontal = 16.dp), fill = jc.card)
                        HealthCard(list.size, reused.size, weak.size, filter, { filter = if (filter == it) VaultFilter.ALL else it })
                    }
                    if (shown.isEmpty()) item {
                        Text(
                            if (query.isNotBlank()) "No passwords match “${query.trim()}”."
                            else if (filter == VaultFilter.REUSED) "No password is used twice." else "No weak passwords.",
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = JType.secondary, color = jc.muted,
                        )
                    }
                    shown.groupBy { e -> e.title.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }.toSortedMap().forEach { (letter, group) ->
                        item(key = "h-$letter") { SectionLabel(letter.toString(), Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).padding(top = 8.dp)) }
                        item(key = "g-$letter") {
                            DividedCard(group, 66.dp, Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).padding(horizontal = 16.dp).animateContentSize(JMotion.standard())) { e ->
                                LoginRow(e, e.id in reused, e.id in weak, onOpen = { openId = e.id }, onCopy = { copySecret(context, "Password", e.login.password) })
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            JIcon(Ico.LOCK, jc.muted, size = 14.dp, stroke = 2f)
                            Text("Encrypted with your notes passcode", style = JType.small, color = jc.muted)
                        }
                    }
                }
            }
        }
    }
}

/** Total, reused and weak at a glance; tapping reused or weak shows only those. */
@Composable
private fun HealthCard(total: Int, reused: Int, weak: Int, filter: VaultFilter, onFilter: (VaultFilter) -> Unit) {
    Card(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            @Composable
            fun RowScopeStat(n: Int, label: String, f: VaultFilter, alert: Boolean) {
                val selected = filter == f
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .background(if (selected) jc.selected else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.Button, enabled = f == VaultFilter.ALL || n > 0 || selected) { onFilter(f) }
                        .padding(vertical = 14.dp)
                        .semantics { contentDescription = "$n $label" + if (selected) ", showing only these" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CountText(n, JType.rowTitle.copy(fontSize = 24.sp, lineHeight = 28.sp), if (alert && n > 0) jc.danger else jc.ink)
                    Text(label, style = JType.small, color = jc.muted)
                }
            }
            RowScopeStat(total, "Saved", VaultFilter.ALL, alert = false)
            androidx.compose.material3.VerticalDivider(Modifier.padding(vertical = 12.dp), color = jc.line)
            RowScopeStat(reused, "Reused", VaultFilter.REUSED, alert = true)
            androidx.compose.material3.VerticalDivider(Modifier.padding(vertical = 12.dp), color = jc.line)
            RowScopeStat(weak, "Weak", VaultFilter.WEAK, alert = true)
        }
    }
}

@Composable
private fun VaultEmpty(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().appear().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(72.dp).clip(JShape.md).background(jc.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            JIcon(ICON_PASSWORDS, jc.accent, size = 36.dp, stroke = 1.8f)
        }
        Text("No passwords yet", Modifier.padding(top = 8.dp), style = JType.rowTitle, color = jc.ink)
        Text(
            "Save logins here. They're encrypted with your notes passcode and sync to your other devices.",
            style = JType.secondary, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        PrimaryButton("Add a Password", onAdd, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun LoginRow(e: VaultEntry, reused: Boolean, weak: Boolean, onOpen: () -> Unit, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onOpen).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Monogram(e.title)
        Column(Modifier.weight(1f)) {
            Text(e.title, style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            e.login.username.takeIf { it.isNotBlank() }?.let {
                Text(it, style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (reused || weak) Text(
            if (reused) "Reused" else "Weak",
            Modifier.clip(RoundedCornerShape(50)).background(jc.danger.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
            style = JType.small, color = jc.danger,
        )
        if (e.login.password.isNotEmpty()) IconAction(Ico.COPY, "Copy password for ${e.title}", onCopy, color = jc.muted, size = 20.dp)
    }
}

/** The site's first letter on a tinted tile. */
@Composable
private fun Monogram(title: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(jc.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Text(
            title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
            style = JType.rowTitle.copy(fontSize = (size.value * 0.45f).sp), color = jc.accent,
        )
    }
}

/**
 * One login. A saved one opens read-only, with copy beside each field and the password hidden until asked;
 * Edit turns the fields into a form. A new login starts in the form, with the generator open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginSheet(entry: VaultEntry, isNew: Boolean, reused: Boolean, onSave: (Login) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(entry.login.name) }
    var username by rememberSaveable { mutableStateOf(entry.login.username) }
    var password by rememberSaveable { mutableStateOf(entry.login.password) }
    var url by rememberSaveable { mutableStateOf(entry.login.url) }
    var notes by rememberSaveable { mutableStateOf(entry.login.notes) }
    var editing by rememberSaveable { mutableStateOf(isNew) }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var generating by rememberSaveable { mutableStateOf(isNew) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Compared with what was last saved here, so the view shows a save straight away.
    var saved by remember { mutableStateOf(entry.login) }
    val edited = saved.copy(name = name, username = username, password = password, url = url, notes = notes)
    val changed = edited != saved
    val canSave = name.isNotBlank() || url.isNotBlank() || username.isNotBlank() || password.isNotEmpty()
    fun reset() { name = saved.name; username = saved.username; password = saved.password; url = saved.url; notes = saved.notes; generating = false; reveal = false }
    val leaveEdit: () -> Unit = { if (isNew) onDismiss() else { reset(); editing = false } }
    val close: () -> Unit = { if (editing && changed) confirmDiscard = true else if (editing && !isNew) editing = false else onDismiss() }
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || !(editing && changed).also { c -> if (c) confirmDiscard = true } },
    )
    if (confirmDiscard) MarginDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(if (isNew) "Discard this password?" else "Discard your changes?") },
        text = { Text(if (isNew) "It hasn't been saved." else "The saved login stays as it was.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; leaveEdit() }) { Text("Discard", color = jc.danger) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep Editing") } },
    )
    if (confirmDelete) MarginDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${entry.title}”?") },
        text = { Text("It moves to Recently Deleted, still encrypted, and can be recovered from there.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = jc.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    BackHandler(onBack = close)
    val title = VaultEntry("", edited).title
    ModalBottomSheet(
        onDismissRequest = close, sheetState = state, containerColor = jc.page, shape = JShape.sheet,
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 4.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(jc.switchOff)) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) TextAction("Cancel", close) else TextAction("Done", onDismiss)
                Text(
                    when { isNew -> "New Password"; editing -> "Edit Password"; else -> "" },
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (editing) TextAction("Save", {
                    onSave(edited)
                    if (!isNew) { saved = edited; editing = false; generating = false }
                }, bold = true, enabled = canSave && changed)
                else TextAction("Edit", { editing = true })
            }
            Column(
                Modifier.fillMaxWidth().animateContentSize(JMotion.standard()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editing) {
                    // A live preview of how the login will look in the list.
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Monogram(if (canSave) title else "", 56.dp)
                        Text(
                            if (canSave) title else "Name it after the site or app", style = JType.small,
                            color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FormLabel("Site")
                    Card {
                        InlineField(name, { name = it }, "Name", siteOf(url).ifBlank { "Bank, email, Wi-Fi…" }, capitalize = true, focusFirst = isNew)
                        CardDivider()
                        InlineField(url, { url = it }, "Website", "example.com", KeyboardType.Uri)
                    }
                    FormLabel("Sign-in")
                    Card {
                        InlineField(username, { username = it }, "Username", "Email or username", KeyboardType.Email)
                        CardDivider()
                        InlineField(
                            password, { password = it }, "Password", "Required", KeyboardType.Password, mono = true,
                            transform = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        ) {
                            if (password.isNotEmpty()) IconAction(if (reveal) Ico.EYE_OFF else Ico.EYE, if (reveal) "Hide password" else "Show password", { reveal = !reveal }, color = jc.muted, size = 20.dp)
                        }
                        if (password.isNotEmpty()) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            StrengthMeter(PasswordGen.strength(password))
                        }
                    }
                    if (generating || password.isEmpty()) Generator(
                        replacing = password.isNotEmpty(),
                        onUse = { password = it; reveal = true; generating = false },
                    ) else TextAction("Suggest a Strong Password", { generating = true })
                    FormLabel("Notes")
                    Card { InlineField(notes, { notes = it }, null, "Security questions, PINs, recovery codes…", singleLine = false, capitalize = true) }
                } else {
                    // Read-only: who it is, then each field with its action.
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Monogram(title, 64.dp)
                        Text(title, style = JType.subheading.copy(fontWeight = FontWeight.Bold), color = jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        siteOf(url).takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }?.let { Text(it, style = JType.small, color = jc.muted) }
                    }
                    Card {
                        DetailRow("Username", username.ifBlank { "None" }, dim = username.isBlank()) {
                            if (username.isNotBlank()) IconAction(Ico.COPY, "Copy username", { copySecret(context, "Username", username, sensitive = false) }, color = jc.accent, size = 20.dp)
                        }
                        CardDivider()
                        DetailRow(
                            "Password", if (password.isEmpty()) "None" else if (reveal) password else "•".repeat(minOf(password.length, 16)),
                            dim = password.isEmpty(), mono = reveal,
                        ) {
                            if (password.isNotEmpty()) {
                                IconAction(if (reveal) Ico.EYE_OFF else Ico.EYE, if (reveal) "Hide password" else "Show password", { reveal = !reveal }, color = jc.muted, size = 20.dp)
                                IconAction(Ico.COPY, "Copy password", { copySecret(context, "Password", password) }, color = jc.accent, size = 20.dp)
                            }
                        }
                        if (password.isNotEmpty()) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            StrengthMeter(PasswordGen.strength(password))
                            if (reused) Text("  ·  Used on another login too", style = JType.small, color = jc.danger)
                        }
                        if (url.isNotBlank()) {
                            CardDivider()
                            DetailRow("Website", url) {
                                normalizeUrl(url)?.let { target ->
                                    IconAction(Ico.GLOBE, "Open website", {
                                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target))) }
                                    }, color = jc.accent, size = 20.dp)
                                }
                            }
                        }
                        if (notes.isNotBlank()) {
                            CardDivider()
                            DetailRow("Notes", notes, singleLine = false)
                        }
                    }
                    if (saved.passwordChangedAt > 0) Text(
                        "Password changed " + fullTime(context, saved.passwordChangedAt),
                        Modifier.fillMaxWidth(), style = JType.small, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Card { CardRow("Delete Password", onClick = { confirmDelete = true }, labelColor = jc.danger, icon = Ico.TRASH) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** A field in the read-only view: a small grey label over the value, actions on the right. */
@Composable
private fun DetailRow(
    label: String, value: String, dim: Boolean = false, mono: Boolean = false, singleLine: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = JType.small, color = jc.muted)
            Text(
                value, style = JType.item.copy(fontFamily = if (mono) FontFamily.Monospace else JType.item.fontFamily),
                color = if (dim) jc.muted else jc.ink, maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Small grey heading over a card in the form. */
@Composable
private fun FormLabel(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 16.dp, top = 6.dp, bottom = 0.dp), style = JType.section, color = jc.muted)
}

/** A borderless field on a card row: its label on the left (or none), the text filling the rest. */
@Composable
private fun InlineField(
    value: String, onChange: (String) -> Unit, label: String?, placeholder: String, type: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, mono: Boolean = false, capitalize: Boolean = false, focusFirst: Boolean = false,
    transform: VisualTransformation = VisualTransformation.None, trailing: (@Composable () -> Unit)? = null,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusFirst) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { runCatching { focus.requestFocus() } }
            .padding(start = 16.dp, end = if (trailing != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        label?.let { Text(it, Modifier.width(96.dp), style = JType.item, color = jc.ink) }
        Box(Modifier.weight(1f).padding(vertical = if (singleLine) 0.dp else 8.dp), contentAlignment = Alignment.CenterStart) {
            val style = JType.item.copy(color = jc.ink, fontFamily = if (mono) FontFamily.Monospace else JType.item.fontFamily)
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = jc.muted.copy(alpha = 0.7f), fontFamily = JType.item.fontFamily), maxLines = if (singleLine) 1 else 3)
            androidx.compose.foundation.text.BasicTextField(
                value, onChange, Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label ?: placeholder },
                textStyle = style, singleLine = singleLine, minLines = if (singleLine) 1 else 4,
                visualTransformation = transform, cursorBrush = androidx.compose.ui.graphics.SolidColor(jc.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = type, autoCorrectEnabled = !singleLine,
                    capitalization = if (capitalize) androidx.compose.ui.text.input.KeyboardCapitalization.Sentences else androidx.compose.ui.text.input.KeyboardCapitalization.None,
                ),
            )
        }
        trailing?.invoke()
    }
}

/** Four bars, filled up to the password's strength. */
@Composable
private fun StrengthMeter(s: Strength, modifier: Modifier = Modifier) {
    val color = when (s) { Strength.WEAK -> jc.danger; Strength.FAIR -> jc.accent; else -> androidx.compose.ui.graphics.Color(0xFF2E8B57) }
    Row(modifier.semantics { contentDescription = "Strength: ${s.label}" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 0..3) Box(Modifier.size(22.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (k <= s.ordinal) color else jc.line))
        Text(s.label, Modifier.padding(start = 6.dp), style = JType.small, color = color)
    }
}

/**
 * A suggested strong password, ready to use in one tap. Options (length, numbers, symbols) fold away below;
 * the suggestion changes as they do, or with the refresh button.
 */
@Composable
private fun Generator(replacing: Boolean, onUse: (String) -> Unit) {
    var length by rememberSaveable { mutableStateOf(20f) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var options by rememberSaveable { mutableStateOf(false) }
    var nonce by remember { mutableStateOf(0) }
    val candidate = remember(length.toInt(), digits, symbols, nonce) { PasswordGen.generate(length.toInt(), digits, symbols) }
    Card(Modifier.animateContentSize(JMotion.standard())) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JIcon(Ico.SPARKLE, jc.accent, size = 16.dp, stroke = 2f)
                Text("Suggested password", Modifier.weight(1f), style = JType.small, color = jc.muted)
                StrengthMeter(PasswordGen.strength(candidate))
                IconAction(Ico.ROTATE, "Suggest another", { nonce++ }, color = jc.muted, size = 20.dp)
            }
            Text(
                candidate, Modifier.padding(end = 8.dp, top = 2.dp, bottom = 4.dp),
                style = JType.rowTitle.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
                color = jc.ink,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(if (options) "Fewer Options" else "Options", { options = !options }, color = jc.muted)
                Spacer(Modifier.weight(1f))
                TextAction(if (replacing) "Replace Password" else "Use Password", { onUse(candidate) }, bold = true)
            }
        }
        if (options) {
            CardDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Length", Modifier.weight(1f), style = JType.item, color = jc.ink)
                    Text("${length.toInt()}", style = JType.item.copy(fontWeight = FontWeight.SemiBold), color = jc.accent)
                }
                Slider(
                    length, { length = it }, valueRange = 8f..40f, steps = 31,
                    colors = SliderDefaults.colors(thumbColor = jc.accent, activeTrackColor = jc.accent, inactiveTrackColor = jc.line, activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
            CardDivider()
            CardRow("Numbers", sub = "2–9") { JSwitch(digits, { digits = it }) }
            CardDivider()
            CardRow("Symbols", sub = "! @ # $ % and more") { JSwitch(symbols, { symbols = it }) }
        }
    }
}
