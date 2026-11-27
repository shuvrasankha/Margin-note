package com.margin.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.margin.app.data.Place
import com.margin.app.data.PlaceReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/**
 * Picks where to be reminded about a note: arriving or leaving, how near counts, and the place itself, from
 * where you are now, an address search, or a place used before. [current] can be removed.
 */
@Composable
fun PlaceReminderDialog(current: Place?, recent: List<Place>, onPick: (Place) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(current?.leaving ?: false) }
    var radius by remember { mutableStateOf(current?.radius ?: Place.DEFAULT_RADIUS) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    /** A spot found, waiting for its name. */
    var naming by remember { mutableStateOf<Place?>(null) }
    var name by remember { mutableStateOf("") }

    fun locate() {
        busy = context.getString(R.string.place_reminder_finding_where_you_are); error = null
        currentPlace(context) { found ->
            busy = null
            if (found == null) error = context.getString(R.string.place_reminder_couldnt_find_where_you_are)
            else { naming = found; name = "" }
        }
    }
    val access = rememberPlaceAccess()
    fun search() {
        val q = query.trim().ifEmpty { return }
        busy = context.getString(R.string.place_reminder_searching); error = null; results = null
        scope.launch {
            val found = findPlaces(context, q)
            busy = null
            if (found == null) error = context.getString(R.string.place_reminder_searching_needs_an_internet_connection) else if (found.isEmpty()) error = context.getString(R.string.place_reminder_nothing_found_for, q) else results = found
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            Text(if (naming == null) stringResource(R.string.place_reminder_remind_me_at_a_place) else stringResource(R.string.place_reminder_name_this_place), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            current?.takeIf { naming == null }?.let { Text(stringResource(R.string.place_reminder_set, it.phraseText(context).replaceFirstChar(Char::lowercase)), Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(stringResource(R.string.place_reminder_arriving), !leaving, { leaving = false })
                Pill(stringResource(R.string.place_reminder_leaving), leaving, { leaving = true })
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.place_reminder_within), style = JType.small, color = jc.muted)
                Place.RADII.forEach { r -> Pill(if (r >= 1000) stringResource(R.string.place_reminder_km, r / 1000) else "$r m", radius == r, { radius = r }) }
            }
            val found = naming
            if (found != null) {
                Text(found.label, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), style = JType.small, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PlainField(name, { name = it }, stringResource(R.string.place_reminder_shop_home_office), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onDone = {
                    onPick(found.copy(label = name.trim().ifEmpty { found.label }, leaving = leaving, radius = radius))
                })
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextAction(stringResource(R.string.common_back), { naming = null })
                    TextAction(stringResource(R.string.place_reminder_set_reminder), { onPick(found.copy(label = name.trim().ifEmpty { found.label }, leaving = leaving, radius = radius)) }, bold = true)
                }
                return@Column
            }
            PlaceRow(Ico.LOCATE, stringResource(R.string.place_reminder_where_i_am_now), null) { access.ensure(background = false) { ok -> if (ok) locate() } }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainField(query, { query = it }, stringResource(R.string.place_reminder_search_an_address_or_place), Modifier.weight(1f), search = true, onDone = ::search)
                TextAction(stringResource(R.string.common_search), ::search, enabled = query.isNotBlank())
            }
            busy?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = JType.small, color = jc.muted) }
            error?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = JType.small, color = jc.danger) }
            results?.forEach { p -> PlaceRow(Ico.PLACE, p.label, null, lines = 2) { naming = p; name = "" } }
            if (results == null && recent.isNotEmpty()) {
                Text(stringResource(R.string.place_reminder_recent), Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp), style = JType.small, color = jc.muted)
                recent.forEach { p -> PlaceRow(Ico.PLACE, p.label, null) { onPick(p.copy(leaving = leaving, radius = radius)) } }
            }
            if (current != null) PlaceRow(Ico.TRASH, stringResource(R.string.place_reminder_remove_place_reminder), null, danger = true, onClick = onRemove)
            Box(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), contentAlignment = Alignment.CenterEnd) { TextAction(stringResource(R.string.common_cancel), onDismiss) }
        }
    }
    access.Dialogs()
}

@Composable
private fun PlaceRow(icon: String, label: String, detail: String?, danger: Boolean = false, lines: Int = 1, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JIcon(icon, if (danger) jc.danger else jc.accent, size = 20.dp, stroke = 1.9f)
        Text(label, Modifier.weight(1f), style = JType.item, color = if (danger) jc.danger else jc.ink, maxLines = lines, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = JType.small, color = jc.muted) }
    }
}

@Composable
internal fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, search: Boolean = false, onDone: () -> Unit) {
    Box(modifier.fillMaxWidth().height(44.dp).clip(JShape.sm).background(jc.surface).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = JType.row, color = jc.muted, maxLines = 1)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = JType.row.copy(color = jc.ink), cursorBrush = SolidColor(jc.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = if (search) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        )
    }
}

/** The note's place reminder under its title, like the time reminder's chip. */
@Composable
fun PlaceChip(place: Place, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.padding(bottom = 10.dp).clip(JShape.xs).clickable(role = Role.Button, onClick = onClick)
            .background(jc.selected).padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = context.getString(R.string.place_reminder_place_reminder_tap_to_change, place.phraseText(context)) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JIcon(Ico.PLACE, jc.accent, size = 14.dp, stroke = 2f)
        Text(place.phraseText(context), style = JType.small, color = jc.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Asks for location access, saying first why (Google Play requires that before asking for location in the
 * background): precise location, then, with [ensure]'s background, "Allow all the time".
 */
class PlaceAccess internal constructor(
    private val context: Context,
    private val askForeground: () -> Unit,
    private val askBackground: () -> Unit,
) {
    internal var explaining by mutableStateOf<Boolean?>(null) // the background step it's for
    internal var then: ((Boolean) -> Unit)? = null
    internal var noPrecise by mutableStateOf(false)
    internal var settingsHint by mutableStateOf(false)

    /** Gets access, if needed, and then calls [onReady] with whether Margin has it. */
    fun ensure(background: Boolean, onReady: (Boolean) -> Unit) {
        val fore = PlaceReminders.hasPrecise(context)
        if (fore && (!background || PlaceReminders.hasBackground(context))) return onReady(true)
        then = onReady
        // Always say why first: Google Play requires it before the background location request.
        explaining = background
    }

    internal fun continueAsking() {
        val background = explaining ?: return
        explaining = null
        if (!PlaceReminders.hasPrecise(context)) { pendingBackground = background; askForeground() } else if (background) askBackgroundNow() else finish(true)
    }

    internal var pendingBackground = false

    internal fun foregroundResult() {
        if (!PlaceReminders.hasPrecise(context)) {
            if (PlaceReminders.hasForeground(context)) noPrecise = true
            return finish(false)
        }
        if (pendingBackground) askBackgroundNow() else finish(true)
    }

    private fun askBackgroundNow() {
        if (PlaceReminders.hasBackground(context)) return finish(true)
        askBackground()
    }

    internal fun backgroundResult() {
        val ok = PlaceReminders.hasBackground(context)
        if (!ok) settingsHint = true
        finish(ok)
    }

    internal fun finish(ok: Boolean) { val t = then; then = null; t?.invoke(ok) }

    @Composable
    fun Dialogs() {
        explaining?.let { background ->
            MarginDialog(
                onDismissRequest = { explaining = null; finish(false) },
                title = { Text(stringResource(R.string.place_reminder_use_your_location)) },
                text = {
                    Text(
                        if (background) stringResource(R.string.place_location_background_explain)
                        else stringResource(R.string.place_reminder_margin_uses_your_location_once),
                    )
                },
                confirmButton = { TextButton(onClick = ::continueAsking) { Text(stringResource(R.string.common_continue)) } },
                dismissButton = { TextButton(onClick = { explaining = null; finish(false) }) { Text(stringResource(R.string.place_reminder_not_now)) } },
            )
        }
        if (noPrecise) MarginDialog(
            onDismissRequest = { noPrecise = false },
            title = { Text(stringResource(R.string.place_reminder_precise_location_needed)) },
            text = { Text(stringResource(R.string.place_reminder_place_reminders_need_precise_location)) },
            confirmButton = { TextButton(onClick = { noPrecise = false; openAppSettings(context) }) { Text(stringResource(R.string.place_reminder_open_settings)) } },
            dismissButton = { TextButton(onClick = { noPrecise = false }) { Text(stringResource(R.string.place_reminder_not_now)) } },
        )
        if (settingsHint) MarginDialog(
            onDismissRequest = { settingsHint = false },
            title = { Text(stringResource(R.string.place_reminder_location_is_only_allowed_while)) },
            text = { Text(stringResource(R.string.place_reminder_the_reminder_is_saved_but)) },
            confirmButton = { TextButton(onClick = { settingsHint = false; openAppSettings(context) }) { Text(stringResource(R.string.place_reminder_open_settings)) } },
            dismissButton = { TextButton(onClick = { settingsHint = false }) { Text(stringResource(R.string.place_reminder_not_now)) } },
        )
    }
}

@Composable
fun rememberPlaceAccess(): PlaceAccess {
    val context = LocalContext.current
    var access: PlaceAccess? = null
    val fore = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access?.foregroundResult() }
    val back = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access?.backgroundResult() }
    access = remember {
        PlaceAccess(
            context,
            askForeground = { fore.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            askBackground = { if (Build.VERSION.SDK_INT >= 29) back.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
        )
    }
    return access
}

private fun openAppSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
}

/**
 * Where the phone is now, named by its coordinates (the user names it next); null if it can't be found. Not looked up
 * as an address: that would send where the user is to the geocoder, and the location must stay on the phone.
 */
@SuppressLint("MissingPermission") // asked for before
private fun currentPlace(context: Context, onResult: (Place?) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    runCatching {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) return@addOnSuccessListener onResult(null)
                onResult(Place(loc.latitude, loc.longitude, "%.5f, %.5f".format(loc.latitude, loc.longitude)))
            }
            .addOnFailureListener { onResult(null) }
    }.onFailure { onResult(null) }
}

/** A short address for a spot: "12 High Street, Leeds". */
private fun Address.short(): String =
    listOfNotNull(featureName?.takeIf { it != thoroughfare && it != subThoroughfare && it.any(Char::isLetter) },
        listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ").takeIf { it.isNotBlank() }, locality)
        .distinct().joinToString(", ").ifBlank { getAddressLine(0) ?: "" }

/** Places matching [query] (Android's geocoder: no account or key, but it needs the internet); null if searching failed. */
@Suppress("DEPRECATION")
private suspend fun findPlaces(context: Context, query: String): List<Place>? {
    if (!Geocoder.isPresent()) return null
    val g = Geocoder(context)
    val list: List<Address>? = if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { c ->
        g.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { c.resume(addresses) }
            override fun onError(errorMessage: String?) { c.resume(null) }
        })
    } else withContext(Dispatchers.IO) { runCatching { g.getFromLocationName(query, 5) }.getOrNull() }
    return list?.filter { it.hasLatitude() && it.hasLongitude() }?.map { a ->
        Place(a.latitude, a.longitude, a.getAddressLine(0) ?: a.short())
    }
}

/** Tells the user, where it matters, that a place reminder can't ring yet. */
fun placeNotWatched(context: Context) =
    Toast.makeText(context, context.getString(R.string.place_reminder_saved_but_it_cant_ring), Toast.LENGTH_LONG).show()

/** [Place.phrase] in the app's language: "When I arrive at Home". */
internal fun Place.phraseText(context: Context): String =
    context.getString(if (leaving) R.string.place_when_i_leave else R.string.place_when_i_arrive_at, label)
