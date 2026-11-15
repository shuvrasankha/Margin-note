package com.margin.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.margin.app.data.KEEP_DELETED_DAYS
import com.margin.app.data.NoteSort
import com.margin.app.data.TextSize
import com.margin.app.data.ThemeMode
import com.margin.app.data.Toggle
import com.margin.app.data.color
import com.margin.app.data.invoke
import com.margin.app.sync.signInWithGoogle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ---------- Settings ----------

/** Google's four-colour "G" (48 x 48). */
internal val GoogleG: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("GoogleG", 18.dp, 18.dp, 48f, 48f).apply {
        listOf(
            0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
        ).forEach { (color, d) ->
            addPath(androidx.compose.ui.graphics.vector.addPathNodes(d), fill = androidx.compose.ui.graphics.SolidColor(Color(color)))
        }
    }.build()
}

@Composable
internal fun SettingsLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 8.dp), style = JType.section, color = jc.muted)
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val status by vm.status.collectAsState()
    val pending by vm.pending.collectAsState()
    val online by vm.online.collectAsState()
    val lockedCount by vm.lockedCount.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val fingerprint by vm.fingerprint.collectAsState()
    var askCurrent by remember { mutableStateOf<(() -> Unit)?>(null) }
    var newPassword by remember { mutableStateOf(false) }
    val autoSort by vm.autoSortChecked.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val defaultSort by vm.defaultSort.collectAsState()
    val toggles by vm.toggles.collectAsState()
    val keepDays by vm.keepDeletedDays.collectAsState()
    val exportContext = LocalContext.current
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportAll(it) { msg -> Toast.makeText(exportContext, msg, Toast.LENGTH_LONG).show() } }
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importFiles(uris) { msg -> Toast.makeText(exportContext, msg, Toast.LENGTH_LONG).show() }
    }
    var shorterKeep by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.data)
    }
    LaunchedEffect(vm.consentIntent) {
        vm.consentIntent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    Scaffold(
        containerColor = jc.page,
        topBar = { Box(Modifier.background(jc.page)) { NavBar(null, {}, color = jc.page) { TextAction("Done", onBack, bold = true) } } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            PageTitle("Settings")
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
                // Account and sync, compact: status with a sync button, then Restore and Sign out side by side.
                val signIn = {
                    scope.launch {
                        runCatching { signInWithGoogle(context as Activity) }
                            .onSuccess { vm.message = null; vm.onSignedIn(it) }
                            .onFailure {
                                // Backing out of the account picker is not an error.
                                vm.message = if (it is androidx.credentials.exceptions.GetCredentialCancellationException) null
                                else "Sign-in failed: ${it.message ?: it.javaClass.simpleName}"
                            }
                    }
                    Unit
                }
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Google's "G" on a white tile, as Google's sign-in branding asks.
                        Box(Modifier.size(30.dp).clip(JShape.xs).background(Color.White), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(GoogleG, contentDescription = "Google", modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (status.signedIn) {
                                val waiting = if (pending == 1) "1 change" else "$pending changes"
                                Text(
                                    when {
                                        !online -> if (pending > 0) "Offline · $waiting waiting" else "Offline"
                                        pending > 0 -> "$waiting waiting to sync"
                                        else -> "Everything is synced"
                                    },
                                    style = JType.item, color = jc.ink,
                                )
                                Text(
                                    // The time first: a long email is what gets cut.
                                    listOfNotNull("Last sync " + (status.lastSyncAt?.let { listTime(context, it) } ?: "never"), status.email ?: status.name).joinToString(" · "),
                                    style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text("Back up to Google Drive", style = JType.item, color = jc.ink)
                                Text("Optional. Notes go to hidden app storage in your Drive.", style = JType.small, color = jc.muted)
                            }
                        }
                        if (!status.signedIn) TextAction("Sign in", signIn, bold = true)
                        else if (vm.busy) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = jc.accent, strokeWidth = 2.dp)
                        } else IconAction(Ico.SYNC, "Sync now", { vm.syncNow() }, size = 20.dp)
                    }
                    if (status.signedIn) {
                        CardDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextAction("Restore from Drive", { if (!vm.busy) confirmRestore = true })
                            TextAction("Sign out", { if (!vm.busy) vm.signOut() }, color = jc.danger)
                        }
                    }
                }
                status.lastError?.let { Text("Last error: $it", Modifier.padding(start = 16.dp, top = 8.dp), style = JType.small, color = jc.danger) }
                vm.message?.let { Text(it, Modifier.padding(start = 16.dp, top = 8.dp), style = JType.small, color = jc.muted) }

                SettingsLabel("Appearance")
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        @Composable
                        fun <T> choices(icon: String, title: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
                            IconTitle(icon, title)
                            Row(Modifier.padding(start = 42.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { (value, label) -> Pill(label, value == current, { onPick(value) }) }
                            }
                        }
                        choices(Ico.MOON, "Theme", listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), appearance.theme) {
                            vm.setAppearance(appearance.copy(theme = it))
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTitle(Ico.TEXT_SIZE, "Text size", Modifier.weight(1f))
                            Text(appearance.textSize.label, style = JType.secondary, color = jc.muted)
                        }
                        Row(Modifier.padding(start = 42.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("A", style = JType.small, color = jc.muted)
                            Slider(
                                value = appearance.textSize.ordinal.toFloat(),
                                onValueChange = { vm.setAppearance(appearance.copy(textSize = TextSize.entries[it.roundToInt()])) },
                                valueRange = 0f..TextSize.entries.lastIndex.toFloat(),
                                steps = TextSize.entries.size - 2,
                                modifier = Modifier.weight(1f).semantics { stateDescription = appearance.textSize.label },
                                colors = SliderDefaults.colors(thumbColor = jc.accent, activeTrackColor = jc.accent, inactiveTrackColor = jc.line),
                                thumb = { Box(Modifier.size(24.dp).shadow(JElevation.card, CircleShape).background(jc.bg, CircleShape).border(2.dp, jc.accent, CircleShape)) },
                                track = { state -> SizeTrack(state.value, state.valueRange.endInclusive) },
                            )
                            Text("A", style = JType.heading, color = jc.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        choices(Ico.LINES, "Preview lines in lists", listOf(0 to "None", 1 to "1 line", 2 to "2 lines"), appearance.previewLines) {
                            vm.setAppearance(appearance.copy(previewLines = it))
                        }
                    }
                }

                SettingsLabel("Writing")
                Card {
                    CardRow("Move checked items to the bottom", icon = Ico.SINK, sub = "In checklists, ticked items sink below the rest.") {
                        JSwitch(autoSort, { vm.setAutoSortChecked(it) })
                    }
                    CardDivider()
                    CardRow("Smart checklists", icon = Ico.CHECKLIST, sub = "Lines that start with [ ] become checkboxes.") {
                        JSwitch(toggles(Toggle.SMART_CHECKLISTS), { vm.setToggle(Toggle.SMART_CHECKLISTS, it) })
                    }
                    CardDivider()
                    CardRow("Link with [[ ]]", icon = Ico.LINK, sub = "Type two brackets to link another note.") {
                        JSwitch(toggles(Toggle.LINKS), { vm.setToggle(Toggle.LINKS, it) })
                    }
                    CardDivider()
                    CardRow("Check grammar", icon = Ico.GRAMMAR, sub = "Underlines common mistakes in blue. Tap one to fix it. Works offline.") {
                        JSwitch(toggles(Toggle.GRAMMAR), { vm.setToggle(Toggle.GRAMMAR, it) })
                    }
                    CardDivider()
                    CardRow(
                        "Better grammar checking", icon = Ico.CLOUD,
                        sub = "Sends note text to LanguageTool (languagetool.org) for a fuller check. Locked notes are never sent.",
                    ) {
                        JSwitch(toggles(Toggle.CLOUD_GRAMMAR), { vm.setToggle(Toggle.CLOUD_GRAMMAR, it) }, enabled = toggles(Toggle.GRAMMAR))
                    }
                }

                SettingsLabel("AI")
                AiSettingsCard(vm.ai)

                SettingsLabel("Notes list")
                Card {
                    val (sortBy, ascending) = defaultSort
                    val key = when (sortBy) { NoteSort.EDITED -> "Date edited"; NoteSort.CREATED -> "Date created"; NoteSort.TITLE -> "Title" }
                    val direction = if (sortBy == NoteSort.TITLE) (if (ascending) "A to Z" else "Z to A")
                    else if (ascending) "oldest first" else "newest first"
                    CardRow("Sort new folders by", icon = Ico.SORT, sub = "$key, $direction") {
                        SortMenu(sortBy, ascending, label = "Change", onChange = vm::setDefaultSort)
                    }
                    CardDivider()
                    CardRow("Group by date", icon = Ico.CALENDAR, sub = "Today, Yesterday, Previous 7 Days, …") {
                        JSwitch(toggles(Toggle.GROUP_BY_DATE), { vm.setToggle(Toggle.GROUP_BY_DATE, it) })
                    }
                }

                SettingsLabel("Privacy & data")
                Card {
                    CardRow("Hide locked notes in search", icon = Ico.EYE_OFF) {
                        JSwitch(toggles(Toggle.HIDE_LOCKED_IN_SEARCH), { vm.setToggle(Toggle.HIDE_LOCKED_IN_SEARCH, it) })
                    }
                    CardDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTitle(Ico.TRASH, "Keep deleted notes")
                        Row(Modifier.padding(start = 42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Shorter removes older deleted notes for good, so it asks first.
                            KEEP_DELETED_DAYS.forEach { d -> Pill("$d days", d == keepDays, { if (d < keepDays) shorterKeep = d else vm.setKeepDeletedDays(d) }) }
                        }
                    }
                    CardDivider()
                    CardRow(
                        "Export all as Markdown", icon = Ico.ARCHIVE, sub = "A zip of every note with its photos and files. Locked notes export only their title.",
                        labelColor = jc.accent, onClick = { exportTo.launch("Margin export ${java.time.LocalDate.now()}.zip") },
                    )
                    CardDivider()
                    CardRow(
                        if (vm.importing) "Importing…" else "Import notes", icon = Ico.ARROW_DOWN,
                        sub = "From Google Keep (the Takeout zip), Evernote (.enex), or Markdown, text and HTML files. Each source gets its own folder.",
                        labelColor = if (vm.importing) jc.muted else jc.accent,
                        onClick = if (vm.importing) null else ({ importFrom.launch(arrayOf("*/*")) }),
                    )
                }

                SettingsLabel("Locked notes")
                Card {
                    if (lockedCount == 0) {
                        CardRow("No locked notes", icon = Ico.LOCK, sub = "Lock a note from the ⋯ menu in the editor. You'll create a 6-digit passcode the first time.")
                    } else {
                        CardRow(
                            plural(lockedCount, "locked note"), icon = Ico.LOCK,
                            sub = if (unlocked) "Unlocked until Margin goes to the background." else "Your passcode opens them and the journal.",
                        )
                        if (unlocked) {
                            CardDivider()
                            CardRow("Lock now", icon = Ico.LOCK, labelColor = jc.accent, onClick = vm::lockNow)
                        }
                        CardDivider()
                        CardRow("Change passcode", icon = Ico.KEY, labelColor = jc.accent, onClick = {
                            if (vm.busy) return@CardRow
                            if (unlocked) newPassword = true else askCurrent = { newPassword = true }
                        })
                        val activity = context as? FragmentActivity
                        if (activity != null && Biometric.available(activity)) {
                            CardDivider()
                            CardRow("Unlock with fingerprint", icon = Ico.FINGERPRINT) {
                                JSwitch(fingerprint != null, { on ->
                                    if (!on) {
                                        vm.setFingerprint(null)
                                        Biometric.forget()
                                    } else {
                                        val enable = {
                                            scope.launch {
                                                val pw = vm.passwordForFingerprint() ?: return@launch
                                                val bytes = String(pw).toByteArray(Charsets.UTF_8)
                                                pw.fill('\u0000')
                                                when (val r = Biometric.encrypt(activity, bytes)) {
                                                    is Biometric.Result.Ok -> {
                                                        val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
                                                        vm.setFingerprint(b64.encodeToString(r.iv) + "." + b64.encodeToString(r.bytes))
                                                    }
                                                    is Biometric.Result.Failed -> vm.message = r.message
                                                    else -> {}
                                                }
                                                bytes.fill(0)
                                            }
                                            Unit
                                        }
                                        if (unlocked) enable() else askCurrent = enable
                                    }
                                })
                            }
                        }
                    }
                }
                // Who and which version, quietly at the end.
                Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MarginMark(36.dp)
                    Text("Margin " + com.margin.app.BuildConfig.VERSION_NAME, Modifier.padding(top = 8.dp), style = JType.small, color = jc.muted)
                }
            }
        }
    }

    askCurrent?.let { then ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            // An old password brings up the switch to a passcode (MarginRoot), which replaces this step.
            if (vm.checkPassword(pw)) { askCurrent = null; if (!vm.legacyPassword.value) then(); null } else if (old) "Wrong password" else "Wrong passcode"
        }
        if (old) OldPasswordDialog(onDismiss = { askCurrent = null }, onSubmit = submit)
        else PasscodeDialog(PasswordMode.ENTER, onDismiss = { askCurrent = null }, onOldPassword = { old = true }, onSubmit = submit)
    }
    if (newPassword) {
        PasscodeDialog(PasswordMode.CREATE, onDismiss = { newPassword = false }) { password ->
            vm.changePassword(password)
            newPassword = false
            null
        }
    }
    shorterKeep?.let { d ->
        MarginDialog(
            onDismissRequest = { shorterKeep = null },
            title = { Text("Keep deleted notes for $d days?") },
            text = { Text("Notes in Recently Deleted for longer than $d days are removed for good now.") },
            confirmButton = { TextButton(onClick = { vm.setKeepDeletedDays(d); shorterKeep = null }) { Text("Keep $d days", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { shorterKeep = null }) { Text("Cancel") } },
        )
    }
    if (confirmRestore) {
        MarginDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google account?") },
            text = { Text("Notes saved in your Google account replace the copies on this device. Notes that exist only on this device are kept.") },
            confirmButton = { TextButton(onClick = { vm.restore(); confirmRestore = false }) { Text("Restore", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

/** The text-size track: a rounded line with a dot for each size, filled up to the chosen one. */
@Composable
internal fun SizeTrack(value: Float, last: Float) {
    val on = jc.accent
    val off = jc.line
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val y = size.height / 2
        val r = 12.dp.toPx() // keeps the ends under the thumb's centre
        val w = size.width - 2 * r
        val x = r + w * (value / last)
        drawLine(off, Offset(r, y), Offset(r + w, y), 4.dp.toPx(), StrokeCap.Round)
        drawLine(on, Offset(r, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
        for (k in 0..last.toInt()) drawCircle(if (k <= value) on else off, 4.dp.toPx(), Offset(r + w * k / last, y))
    }
}
