package com.margin.app.ui

import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
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
import com.margin.app.data.LockSession
import com.margin.app.data.Login
import com.margin.app.data.OtpSpec
import com.margin.app.data.Totp
import com.margin.app.data.NoteRepository
import com.margin.app.data.VaultIcons
import com.margin.app.data.VaultKind
import com.margin.app.data.VaultStore
import com.margin.app.data.WifiSecurity
import com.margin.app.data.vaultKind
import com.margin.app.data.PasswordGen
import com.margin.app.data.Strength
import com.margin.app.data.isLogin
import com.margin.app.data.loginBody
import com.margin.app.data.loginFrom
import com.margin.app.data.normalizeUrl
import com.margin.app.data.relatedWords
import com.margin.app.data.siteOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** A key and a padlock's shackle: the Passwords list. */
const val ICON_PASSWORDS = "M7 11V8a5 5 0 0 1 9.6-2M5 11h14v10H5zM12 15v2"

/** Radio waves: a Wi-Fi network. */
const val ICON_WIFI = "M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.7 16a4.6 4.6 0 0 1 6.6 0M12 19.5v.01"
/** A screen on a stand: a computer, drive or other device. */
const val ICON_DEVICE = "M4 5h16v11H4zM9 20h6M12 16v4"

/** A saved login, opened. */
data class VaultEntry(val id: String, val login: Login) {
    val title get() = login.name.ifBlank { siteOf(login.url) }.ifBlank { login.username }.ifBlank { "Untitled" }
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val session: LockSession,
    private val store: VaultStore,
) : ViewModel() {
    /** Every saved entry, opened; null while locked (or still opening). */
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

    /**
     * Saves [login] as a new entry ([id] null) or over an existing one. A login whose website changed (or that
     * has no icon yet) then gets the site's icon, fetched from the site itself.
     */
    fun save(id: String?, login: Login) = viewModelScope.launch {
        val old = id?.let { entries.value?.firstOrNull { e -> e.id == it }?.login }
        val siteChanged = siteOf(login.url) != siteOf(old?.url.orEmpty())
        // A new website's icon replaces the old one; an app's icon stays with the app.
        val withIcon = if (siteChanged && login.apps.isEmpty()) login.copy(icon = "") else login
        val saved = store.save(session, id, withIcon) ?: return@launch
        if (login.vaultKind == VaultKind.LOGIN && login.url.isNotBlank() && withIcon.icon.isEmpty()) {
            VaultIcons.ofSite(login.url)?.let { store.setIcon(session, saved, it) }
        }
    }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    /** Entries already tried for a missing icon while this screen lives, so an offline site isn't asked again and again. */
    private val iconTried = mutableSetOf<String>()

    /**
     * Logins saved before icons were captured (or whose site was offline then) get theirs: the linked app's
     * icon, else the site's, fetched from the site itself.
     */
    fun fillMissingIcons(context: android.content.Context, list: List<VaultEntry>) = viewModelScope.launch {
        for (e in list) {
            val l = e.login
            if (l.icon.isNotEmpty() || l.vaultKind != VaultKind.LOGIN || !iconTried.add(e.id)) continue
            val icon = l.apps.firstNotNullOfOrNull { VaultIcons.ofApp(context, it) }
                ?: l.url.takeIf { it.isNotBlank() }?.let { VaultIcons.ofSite(it) } ?: continue
            store.setIcon(session, e.id, icon)
        }
    }
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
            // From Android 10 an app in the background reads no clipboard at all, which is the usual case by now
            // (the password went into another app). So clear unless something else can be seen there.
            val now = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            if (now == null || now == text) {
                if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, 60_000)
    }
    // Android 13 and later shows its own "Copied" notice.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, if (sensitive) context.getString(R.string.passwords_copied_it_clears_in_a, label) else context.getString(R.string.passwords_copied, label), Toast.LENGTH_SHORT).show()
}

/**
 * Adds a saved Wi-Fi network to the phone: Android 11+ asks in its own dialog; earlier, or for WEP (which
 * Android no longer adds this way), the password is copied and Wi-Fi settings open.
 */
private fun connectWifi(context: Context, ssid: String, password: String, security: WifiSecurity) {
    val added = Build.VERSION.SDK_INT >= 30 && security != WifiSecurity.WEP && runCatching {
        val b = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(ssid)
        when (security) {
            WifiSecurity.WPA -> b.setWpa2Passphrase(password)
            WifiSecurity.WPA3 -> b.setWpa3Passphrase(password)
            else -> {}
        }
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(android.provider.Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(b.build())),
        )
    }.isSuccess
    if (!added) {
        if (password.isNotEmpty()) copySecret(context, context.getString(R.string.passwords_wi_fi_password), password)
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
    }
}

/** Whether Margin is the phone's autofill service, checked again each time the screen comes back. */
@Composable
private fun autofillOn(): Boolean {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    return remember(state) {
        context.getSystemService(android.view.autofill.AutofillManager::class.java)?.let { runCatching { it.hasEnabledAutofillServices() }.getOrNull() } == true
    }
}

/** Asks Android to make Margin the autofill service; falls back to the settings page that lists them. */
private fun turnOnAutofill(context: Context) {
    val ask = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, android.net.Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(ask) }.onFailure {
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }
    }
}

/** Offers autofill until it's on; then a quiet line says so. */
@Composable
private fun AutofillCard() {
    val context = LocalContext.current
    val supported = remember { context.getSystemService(android.view.autofill.AutofillManager::class.java)?.isAutofillSupported == true }
    if (!supported) return
    if (autofillOn()) Text(
        stringResource(R.string.passwords_autofill_is_on_margin_fills),
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), style = JType.small, color = jc.muted,
    ) else Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        CardRow(
            stringResource(R.string.passwords_turn_on_autofill), sub = stringResource(R.string.passwords_fill_passwords_in_apps_and),
            onClick = { turnOnAutofill(context) }, icon = ICON_PASSWORDS,
        ) { JIcon(Ico.CHEVRON, jc.muted, size = 18.dp, stroke = 2f) }
    }
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

private fun strengthOf(l: Login) = PasswordGen.strength(l.password, l.relatedWords())
private fun isWeak(l: Login) = l.password.isNotEmpty() && strengthOf(l) == Strength.WEAK

@Composable
fun PasswordsScreen(vm: VaultViewModel, onBack: () -> Unit, backLabel: String = stringResource(R.string.passwords_library)) {
    SecureWindow()
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(VaultFilter.ALL) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openId?.let { id -> if (id.isEmpty()) NEW_ENTRY else entries?.firstOrNull { it.id == id } }
    val list = entries.orEmpty()
    androidx.compose.runtime.LaunchedEffect(entries != null) { entries?.let { vm.fillMissingIcons(context.applicationContext, it) } }
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
    val pageList = rememberLazyListState()
    Scaffold(
        containerColor = jc.page,
        topBar = { NavBar(backLabel, onBack, color = jc.page) },
        bottomBar = {
            if (list.isNotEmpty()) BottomBar() {
                Spacer(Modifier.size(44.dp))
                Text(
                    if (query.isBlank() && filter == VaultFilter.ALL) pluralStringResource(R.plurals.passwords_count, list.size, list.size) else pluralStringResource(R.plurals.passwords_results_count, shown.size, shown.size),
                    style = JType.small, color = jc.muted,
                )
                IconAction(Ico.PLUS, stringResource(R.string.passwords_new_password), { openId = "" }, size = 24.dp)
            }
        },
    ) { padding ->
        LazyColumn(state = pageList, modifier = Modifier.padding(padding).fillMaxSize()) {
            item { PageTitle(stringResource(R.string.passwords_passwords)) }
            when {
                entries == null -> item {
                    Text(stringResource(R.string.passwords_opening), Modifier.padding(horizontal = 20.dp), style = JType.secondary, color = jc.muted)
                }
                list.isEmpty() -> item {
                    VaultEmpty(onAdd = { openId = "" })
                    AutofillCard()
                }
                else -> {
                    item {
                        SearchField(query, { query = it }, stringResource(R.string.passwords_search_passwords), Modifier.padding(horizontal = 16.dp), fill = jc.card)
                        HealthCard(list.size, reused.size, weak.size, filter, { filter = if (filter == it) VaultFilter.ALL else it })
                        Spacer(Modifier.height(8.dp))
                        AutofillCard()
                    }
                    if (shown.isEmpty()) item {
                        Text(
                            if (query.isNotBlank()) stringResource(R.string.passwords_no_passwords_match, query.trim())
                            else if (filter == VaultFilter.REUSED) stringResource(R.string.passwords_no_password_is_used_twice) else stringResource(R.string.passwords_no_weak_passwords),
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = JType.secondary, color = jc.muted,
                        )
                    }
                    shown.groupBy { e -> e.title.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }.toSortedMap().forEach { (letter, group) ->
                        item(key = "h-$letter") { SectionLabel(letter.toString(), Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).padding(top = 8.dp)) }
                        item(key = "g-$letter") {
                            DividedCard(group, 66.dp, Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).padding(horizontal = 16.dp).animateContentSize(JMotion.standard())) { e ->
                                LoginRow(e, e.id in reused, e.id in weak, onOpen = { openId = e.id }, onCopy = { copySecret(context, context.getString(R.string.passwords_password), e.login.password) })
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            JIcon(Ico.LOCK, jc.muted, size = 14.dp, stroke = 2f)
                            Text(stringResource(R.string.passwords_encrypted_with_your_notes_passcode), style = JType.small, color = jc.muted)
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
    val context = androidx.compose.ui.platform.LocalContext.current
                val selected = filter == f
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .background(if (selected) jc.selected else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.Button, enabled = f == VaultFilter.ALL || n > 0 || selected) { onFilter(f) }
                        .padding(vertical = 14.dp)
                        .semantics { contentDescription = context.getString(if (selected) R.string.passwords_stat_desc_selected else R.string.passwords_stat_desc, n.toString(), label) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CountText(n, JType.rowTitle.copy(fontSize = 24.sp, lineHeight = 28.sp), if (alert && n > 0) jc.danger else jc.ink)
                    Text(label, style = JType.small, color = jc.muted)
                }
            }
            RowScopeStat(total, stringResource(R.string.passwords_saved), VaultFilter.ALL, alert = false)
            androidx.compose.material3.VerticalDivider(Modifier.padding(vertical = 12.dp), color = jc.line)
            RowScopeStat(reused, stringResource(R.string.passwords_reused), VaultFilter.REUSED, alert = true)
            androidx.compose.material3.VerticalDivider(Modifier.padding(vertical = 12.dp), color = jc.line)
            RowScopeStat(weak, stringResource(R.string.passwords_weak), VaultFilter.WEAK, alert = true)
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
        Text(stringResource(R.string.passwords_no_passwords_yet), Modifier.padding(top = 8.dp), style = JType.rowTitle, color = jc.ink)
        Text(
            stringResource(R.string.passwords_save_logins_here_theyre_encrypted),
            style = JType.secondary, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        PrimaryButton(stringResource(R.string.passwords_add_a_password), onAdd, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun LoginRow(e: VaultEntry, reused: Boolean, weak: Boolean, onOpen: () -> Unit, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onOpen).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntryIcon(e.login, e.title)
        Column(Modifier.weight(1f)) {
            Text(e.title, style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (if (e.login.vaultKind == VaultKind.WIFI) stringResource(R.string.passwords_wifi_security, stringResource(WifiSecurity.of(e.login.security).labelRes)) else e.login.username).takeIf { it.isNotBlank() }?.let {
                Text(it, style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (reused || weak) Text(
            if (reused) stringResource(R.string.passwords_reused) else stringResource(R.string.passwords_weak),
            Modifier.clip(RoundedCornerShape(50)).background(jc.danger.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
            style = JType.small, color = jc.danger,
        )
        if (e.login.password.isNotEmpty()) IconAction(Ico.COPY, stringResource(R.string.passwords_copy_password_for, e.title), onCopy, color = jc.muted, size = 20.dp)
    }
}

/** A vault entry to pick (autofill's list): its icon, name and username. */
@Composable
fun VaultEntryRow(e: VaultEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntryIcon(e.login, e.title)
        Column(Modifier.weight(1f)) {
            Text(e.title, style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (e.login.username.ifBlank { siteOf(e.login.url) }).takeIf { it.isNotBlank() }?.let {
                Text(it, style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The icon captured for the site or app when it was saved; else a Wi-Fi or device glyph for those kinds,
 * else the first letter on a tinted tile.
 */
@Composable
private fun EntryIcon(login: Login, title: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val bitmap = remember(login.icon) { VaultIcons.decode(login.icon)?.asImageBitmap() }
    val glyph = when (login.vaultKind) { VaultKind.WIFI -> ICON_WIFI; VaultKind.DEVICE -> ICON_DEVICE; else -> null }
    when {
        bitmap != null -> androidx.compose.foundation.Image(
            bitmap, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(androidx.compose.ui.graphics.Color.White),
        )
        glyph != null -> Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(jc.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            JIcon(glyph, jc.accent, size = size * 0.55f, stroke = 1.8f)
        }
        else -> Monogram(title, size)
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
    var kind by rememberSaveable { mutableStateOf(entry.login.vaultKind) }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.of(entry.login.security)) }
    var apps by rememberSaveable { mutableStateOf(entry.login.apps) }
    var otp by rememberSaveable { mutableStateOf(entry.login.otp) }
    var editing by rememberSaveable { mutableStateOf(isNew) }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var generating by rememberSaveable { mutableStateOf(isNew) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Compared with what was last saved here, so the view shows a save straight away.
    var saved by remember { mutableStateOf(entry.login) }
    // When the stored login comes back from a save (trimmed, its change time and history updated), show that.
    androidx.compose.runtime.LaunchedEffect(entry.login) {
        if (!editing && entry.login != saved) {
            saved = entry.login
            name = saved.name; username = saved.username; password = saved.password; url = saved.url; notes = saved.notes
            kind = saved.vaultKind; security = WifiSecurity.of(saved.security); apps = saved.apps; otp = saved.otp
        }
    }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var revealedPast by remember { mutableStateOf<Int?>(null) }
    val wifi = kind == VaultKind.WIFI
    val edited = saved.copy(
        name = name, username = if (wifi) "" else username, password = password, url = if (kind == VaultKind.LOGIN) url else "", notes = notes,
        kind = kind.name, security = if (wifi) security.name else "", apps = if (kind == VaultKind.LOGIN) apps else emptyList(),
        otp = if (kind == VaultKind.LOGIN) otp.trim() else "",
    )
    val changed = edited != saved
    val otpSpec = remember(otp) { Totp.parse(otp) }
    val otpBad = kind == VaultKind.LOGIN && otp.isNotBlank() && otpSpec == null
    val hasContent = name.isNotBlank() || url.isNotBlank() || username.isNotBlank() || password.isNotEmpty()
    val canSave = hasContent && !otpBad
    // A setup link names the site and account: fill in what's still empty, whether it was scanned or pasted.
    fun takeOtp(value: String) {
        val pasted = otp.isBlank() && value.length > 1
        otp = value
        if (pasted) Totp.parse(value)?.let { spec ->
            if (name.isBlank() && spec.issuer.isNotBlank()) name = spec.issuer
            if (username.isBlank() && spec.account.isNotBlank()) username = spec.account
        }
    }
    fun reset() {
        name = saved.name; username = saved.username; password = saved.password; url = saved.url; notes = saved.notes
        kind = saved.vaultKind; security = WifiSecurity.of(saved.security); apps = saved.apps; otp = saved.otp; generating = false; reveal = false
    }
    val leaveEdit: () -> Unit = { if (isNew) onDismiss() else { reset(); editing = false } }
    val close: () -> Unit = { if (editing && changed) confirmDiscard = true else if (editing && !isNew) editing = false else onDismiss() }
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || !(editing && changed).also { c -> if (c) confirmDiscard = true } },
    )
    if (confirmDiscard) MarginDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(if (isNew) stringResource(R.string.passwords_discard_this_password) else stringResource(R.string.passwords_discard_your_changes)) },
        text = { Text(if (isNew) stringResource(R.string.passwords_it_hasnt_been_saved) else stringResource(R.string.passwords_the_saved_login_stays_as)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; leaveEdit() }) { Text(stringResource(R.string.common_discard), color = jc.danger) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.passwords_keep_editing)) } },
    )
    if (confirmDelete) MarginDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.passwords_delete, entry.title)) },
        text = { Text(stringResource(R.string.passwords_it_moves_to_recently_deleted)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.common_delete), color = jc.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
    BackHandler(onBack = close)
    val title = VaultEntry("", edited).title
    ModalBottomSheet(
        onDismissRequest = close, sheetState = state, containerColor = jc.page, shape = JShape.sheet,
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 4.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(jc.switchOff)) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) TextAction(stringResource(R.string.common_cancel), close) else TextAction(stringResource(R.string.common_done), onDismiss)
                Text(
                    when { isNew -> if (kind == VaultKind.LOGIN) stringResource(R.string.passwords_new_password_title) else stringResource(R.string.passwords_new_kind, stringResource(kind.labelRes)); editing -> if (kind == VaultKind.LOGIN) stringResource(R.string.passwords_edit_password) else stringResource(R.string.passwords_edit_kind, stringResource(kind.labelRes)); else -> "" },
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (editing) TextAction(stringResource(R.string.common_save), {
                    onSave(edited)
                    if (!isNew) { saved = edited; editing = false; generating = false }
                }, bold = true, enabled = canSave && changed)
                else TextAction(stringResource(R.string.common_edit), { editing = true })
            }
            Column(
                Modifier.fillMaxWidth().animateContentSize(JMotion.standard()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editing) {
                    // A live preview of how the login will look in the list.
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EntryIcon(edited, if (hasContent) title else "", 56.dp)
                        Text(
                            if (hasContent) title else when (kind) {
                                VaultKind.LOGIN -> stringResource(R.string.passwords_name_it_after_the_site); VaultKind.WIFI -> stringResource(R.string.passwords_the_networks_name)
                                VaultKind.DEVICE -> stringResource(R.string.passwords_the_computer_drive_or_device); VaultKind.OTHER -> stringResource(R.string.passwords_name_it)
                            }, style = JType.small,
                            color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // What kind of secret it is; set when it's made.
                    if (isNew) Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) { VaultKind.entries.forEach { k -> Pill(stringResource(k.labelRes), kind == k, { kind = k }) } }
                    when (kind) {
                        VaultKind.LOGIN -> {
                            FormLabel(stringResource(R.string.passwords_site))
                            Card {
                                InlineField(name, { name = it }, stringResource(R.string.passwords_name), siteOf(url).ifBlank { stringResource(R.string.passwords_bank_email_shop) }, capitalize = true, focusFirst = isNew)
                                CardDivider()
                                InlineField(url, { url = it }, stringResource(R.string.passwords_website), "example.com", KeyboardType.Uri)
                            }
                        }
                        VaultKind.WIFI -> {
                            FormLabel(stringResource(R.string.passwords_network))
                            Card {
                                InlineField(name, { name = it }, stringResource(R.string.passwords_name), stringResource(R.string.passwords_network_name_ssid), focusFirst = isNew)
                                CardDivider()
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(R.string.passwords_security), Modifier.width(88.dp), style = JType.item, color = jc.ink)
                                    WifiSecurity.entries.forEach { w -> Pill(stringResource(w.labelRes), security == w, { security = w }) }
                                }
                            }
                        }
                        VaultKind.DEVICE, VaultKind.OTHER -> {
                            FormLabel(if (kind == VaultKind.DEVICE) stringResource(R.string.passwords_device) else stringResource(R.string.passwords_item))
                            Card {
                                InlineField(
                                    name, { name = it }, stringResource(R.string.passwords_name), if (kind == VaultKind.DEVICE) stringResource(R.string.passwords_laptop_ssd_router_safe) else stringResource(R.string.passwords_licence_key_door_code),
                                    capitalize = true, focusFirst = isNew,
                                )
                            }
                        }
                    }
                    FormLabel(if (kind == VaultKind.LOGIN) stringResource(R.string.passwords_sign_in) else stringResource(R.string.passwords_secret))
                    Card {
                        if (kind != VaultKind.WIFI && kind != VaultKind.OTHER) {
                            InlineField(
                                username, { username = it }, if (kind == VaultKind.DEVICE) stringResource(R.string.passwords_account) else stringResource(R.string.passwords_username),
                                if (kind == VaultKind.DEVICE) stringResource(R.string.passwords_user_or_account_if_any) else stringResource(R.string.passwords_email_or_username), KeyboardType.Email,
                            )
                            CardDivider()
                        }
                        InlineField(
                            password, { password = it }, stringResource(R.string.passwords_password), if (kind == VaultKind.DEVICE) stringResource(R.string.passwords_password_pin_or_key) else stringResource(R.string.passwords_required), KeyboardType.Password, mono = true,
                            transform = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        ) {
                            if (password.isNotEmpty()) IconAction(if (reveal) Ico.EYE_OFF else Ico.EYE, if (reveal) stringResource(R.string.passwords_hide_password) else stringResource(R.string.passwords_show_password), { reveal = !reveal }, color = jc.muted, size = 20.dp)
                        }
                        if (password.isNotEmpty()) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            StrengthMeter(strengthOf(edited))
                        }
                    }
                    if (generating || password.isEmpty()) Generator(
                        replacing = password.isNotEmpty(),
                        onUse = { password = it; reveal = true; generating = false },
                    ) else TextAction(stringResource(R.string.passwords_suggest_a_strong_password), { generating = true })
                    if (kind == VaultKind.LOGIN) {
                        FormLabel(stringResource(R.string.passwords_two_factor_code))
                        Card {
                            InlineField(otp, ::takeOtp, stringResource(R.string.passwords_setup_key), stringResource(R.string.passwords_paste_the_key_or_scan), KeyboardType.Password, mono = true) {
                                IconAction(Ico.SCAN, stringResource(R.string.passwords_scan_the_setup_qr_code), { scanOtp(context) { otp = ""; takeOtp(it) } }, color = jc.accent, size = 20.dp)
                            }
                            Text(
                                if (otpBad) stringResource(R.string.passwords_not_a_valid_setup_key) else stringResource(R.string.passwords_from_the_sites_set_up),
                                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), style = JType.small, color = if (otpBad) jc.danger else jc.muted,
                            )
                        }
                    }
                    if (kind == VaultKind.LOGIN && apps.isNotEmpty()) {
                        FormLabel(stringResource(R.string.passwords_fills_in_apps))
                        Card {
                            apps.forEachIndexed { i, pkg ->
                                if (i > 0) CardDivider()
                                CardRow(VaultIcons.appLabel(context, pkg) ?: pkg, sub = pkg) {
                                    IconAction(Ico.CLOSE, stringResource(R.string.passwords_stop_filling_in, VaultIcons.appLabel(context, pkg) ?: pkg), { apps = apps - pkg }, color = jc.muted, size = 20.dp)
                                }
                            }
                        }
                    }
                    FormLabel(stringResource(R.string.passwords_notes))
                    Card {
                        InlineField(
                            notes, { notes = it }, null,
                            when (kind) { VaultKind.DEVICE -> stringResource(R.string.passwords_recovery_key_encryption_details); VaultKind.WIFI -> stringResource(R.string.passwords_router_admin_login_where_it); else -> stringResource(R.string.passwords_security_questions_pins_recovery_codes) },
                            singleLine = false, capitalize = true,
                        )
                    }
                } else {
                    // Read-only: who it is, then each field with its action.
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EntryIcon(edited, title, 64.dp)
                        Text(title, style = JType.subheading.copy(fontWeight = FontWeight.Bold), color = jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        siteOf(url).takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }?.let { Text(it, style = JType.small, color = jc.muted) }
                    }
                    Card {
                        if (wifi) {
                            DetailRow(stringResource(R.string.passwords_network), name.ifBlank { stringResource(R.string.passwords_none) }, dim = name.isBlank()) {
                                if (name.isNotBlank()) IconAction(Ico.COPY, stringResource(R.string.passwords_copy_network_name), { copySecret(context, context.getString(R.string.passwords_network_name), name, sensitive = false) }, color = jc.accent, size = 20.dp)
                            }
                            CardDivider()
                            DetailRow(stringResource(R.string.passwords_security), stringResource(security.labelRes))
                            CardDivider()
                        } else if (kind != VaultKind.OTHER) {
                            DetailRow(if (kind == VaultKind.DEVICE) stringResource(R.string.passwords_account) else stringResource(R.string.passwords_username), username.ifBlank { stringResource(R.string.passwords_none) }, dim = username.isBlank()) {
                                if (username.isNotBlank()) IconAction(Ico.COPY, stringResource(R.string.passwords_copy_username), { copySecret(context, context.getString(R.string.passwords_username), username, sensitive = false) }, color = jc.accent, size = 20.dp)
                            }
                            CardDivider()
                        }
                        DetailRow(
                            stringResource(R.string.passwords_password), if (password.isEmpty()) stringResource(R.string.passwords_none) else if (reveal) password else "•".repeat(minOf(password.length, 16)),
                            dim = password.isEmpty(), mono = reveal,
                        ) {
                            if (password.isNotEmpty()) {
                                IconAction(if (reveal) Ico.EYE_OFF else Ico.EYE, if (reveal) stringResource(R.string.passwords_hide_password) else stringResource(R.string.passwords_show_password), { reveal = !reveal }, color = jc.muted, size = 20.dp)
                                IconAction(Ico.COPY, stringResource(R.string.passwords_copy_password), { copySecret(context, context.getString(R.string.passwords_password), password) }, color = jc.accent, size = 20.dp)
                            }
                        }
                        if (password.isNotEmpty()) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            StrengthMeter(strengthOf(edited))
                            if (reused) Text(stringResource(R.string.passwords_used_on_another_login_too), style = JType.small, color = jc.danger)
                        }
                        if (kind == VaultKind.LOGIN && otpSpec != null) {
                            CardDivider()
                            OtpRow(otpSpec)
                        }
                        if (url.isNotBlank()) {
                            CardDivider()
                            DetailRow(stringResource(R.string.passwords_website), url) {
                                normalizeUrl(url)?.let { target ->
                                    IconAction(Ico.GLOBE, stringResource(R.string.passwords_open_website), {
                                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target))) }
                                    }, color = jc.accent, size = 20.dp)
                                }
                            }
                        }
                        if (apps.isNotEmpty()) {
                            CardDivider()
                            DetailRow(stringResource(R.string.passwords_fills_in), apps.joinToString { VaultIcons.appLabel(context, it) ?: it }, singleLine = false)
                        }
                        if (notes.isNotBlank()) {
                            CardDivider()
                            DetailRow(stringResource(R.string.passwords_notes), notes, singleLine = false)
                        }
                    }
                    if (wifi && name.isNotBlank()) Card {
                        CardRow(stringResource(R.string.passwords_connect_this_phone), sub = stringResource(R.string.passwords_adds_the_network_to_wi), onClick = { connectWifi(context, name, password, security) }, icon = ICON_WIFI)
                    }
                    if (saved.passwordChangedAt > 0) Text(
                        stringResource(R.string.passwords_password_changed, fullTime(context, saved.passwordChangedAt)),
                        Modifier.fillMaxWidth(), style = JType.small, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (saved.history.isNotEmpty()) {
                        Card(Modifier.animateContentSize(JMotion.standard())) {
                            CardRow(
                                stringResource(R.string.passwords_previous_passwords), sub = pluralStringResource(R.plurals.passwords_previous_count, saved.history.size, saved.history.size),
                                onClick = { showHistory = !showHistory; revealedPast = null }, icon = Ico.CLOCK,
                            ) { Box(Modifier.rotate(if (showHistory) 270f else 90f)) { JIcon(Ico.CHEVRON, jc.muted, size = 18.dp, stroke = 2f) } }
                            if (showHistory) saved.history.forEachIndexed { i, past ->
                                CardDivider()
                                val shownPast = revealedPast == i
                                DetailRow(
                                    stringResource(R.string.passwords_until, fullTime(context, past.replacedAt)),
                                    if (shownPast) past.password else "•".repeat(minOf(past.password.length, 16)), mono = shownPast,
                                ) {
                                    IconAction(if (shownPast) Ico.EYE_OFF else Ico.EYE, if (shownPast) stringResource(R.string.passwords_hide_old_password) else stringResource(R.string.passwords_show_old_password), { revealedPast = if (shownPast) null else i }, color = jc.muted, size = 20.dp)
                                    IconAction(Ico.COPY, stringResource(R.string.passwords_copy_old_password), { copySecret(context, context.getString(R.string.passwords_old_password), past.password) }, color = jc.accent, size = 20.dp)
                                }
                            }
                        }
                    }
                    Card { CardRow(if (kind == VaultKind.LOGIN) stringResource(R.string.passwords_delete_password) else stringResource(R.string.passwords_delete_kind, stringResource(kind.labelRes)), onClick = { confirmDelete = true }, labelColor = jc.danger, icon = Ico.TRASH) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The current 2FA code, its seconds left, and copy; it rolls over by itself. */
@Composable
private fun OtpRow(spec: OtpSpec) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(spec) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000 - now % 1000)
        }
    }
    val code = Totp.code(spec, now)
    val left = Totp.secondsLeft(spec, now)
    DetailRow(stringResource(R.string.passwords_verification_code_s, left), Totp.grouped(code), mono = true) {
        IconAction(Ico.COPY, stringResource(R.string.passwords_copy_verification_code), { copySecret(context, context.getString(R.string.passwords_code), Totp.code(spec, System.currentTimeMillis())) }, color = jc.accent, size = 20.dp)
    }
}

/** Scans a 2FA setup QR code with Google Play services (no camera permission) and hands back what it read if it's a setup key. */
private fun scanOtp(context: Context, onKey: (String) -> Unit) {
    val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
    com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { code ->
            val raw = code.rawValue.orEmpty()
            if (Totp.parse(raw) != null) onKey(raw)
            else Toast.makeText(context, context.getString(R.string.passwords_that_qr_code_isnt_a), Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { Toast.makeText(context, context.getString(R.string.passwords_couldnt_open_the_scanner), Toast.LENGTH_SHORT).show() }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val color = when (s) { Strength.WEAK -> jc.danger; Strength.FAIR -> jc.accent; else -> androidx.compose.ui.graphics.Color(0xFF2E8B57) }
    Row(modifier.semantics { contentDescription = context.getString(R.string.passwords_strength_desc, context.getString(s.labelRes)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 0..3) Box(Modifier.size(22.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (k <= s.ordinal) color else jc.line))
        Text(stringResource(s.labelRes), Modifier.padding(start = 6.dp), style = JType.small, color = color)
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
                Text(stringResource(R.string.passwords_suggested_password), Modifier.weight(1f), style = JType.small, color = jc.muted)
                StrengthMeter(PasswordGen.strength(candidate))
                IconAction(Ico.ROTATE, stringResource(R.string.passwords_suggest_another), { nonce++ }, color = jc.muted, size = 20.dp)
            }
            Text(
                candidate, Modifier.padding(end = 8.dp, top = 2.dp, bottom = 4.dp),
                style = JType.rowTitle.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
                color = jc.ink,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(if (options) stringResource(R.string.passwords_fewer_options) else stringResource(R.string.passwords_options), { options = !options }, color = jc.muted)
                Spacer(Modifier.weight(1f))
                TextAction(if (replacing) stringResource(R.string.passwords_replace_password) else stringResource(R.string.passwords_use_password), { onUse(candidate) }, bold = true)
            }
        }
        if (options) {
            CardDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.passwords_length), Modifier.weight(1f), style = JType.item, color = jc.ink)
                    Text("${length.toInt()}", style = JType.item.copy(fontWeight = FontWeight.SemiBold), color = jc.accent)
                }
                Slider(
                    length, { length = it }, valueRange = 8f..40f, steps = 31,
                    colors = SliderDefaults.colors(thumbColor = jc.accent, activeTrackColor = jc.accent, inactiveTrackColor = jc.line, activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
            CardDivider()
            CardRow(stringResource(R.string.passwords_numbers), sub = "2–9") { JSwitch(digits, { digits = it }) }
            CardDivider()
            CardRow(stringResource(R.string.passwords_symbols), sub = stringResource(R.string.passwords_and_more)) { JSwitch(symbols, { symbols = it }) }
        }
    }
}
