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
        busy = "Finding where you are…"; error = null
        currentPlace(context) { found ->
            busy = null
            if (found == null) error = "Couldn't find where you are. Check that location is on, or search instead."
            else { naming = found; name = "" }
        }
    }
    val access = rememberPlaceAccess()
    fun search() {
        val q = query.trim().ifEmpty { return }
        busy = "Searching…"; error = null; results = null
        scope.launch {
            val found = findPlaces(context, q)
            busy = null
            if (found == null) error = "Searching needs an internet connection." else if (found.isEmpty()) error = "Nothing found for “$q”." else results = found
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            Text(if (naming == null) "Remind me at a place" else "Name this place", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            current?.takeIf { naming == null }?.let { Text("Set: ${it.phrase.replaceFirstChar(Char::lowercase)}", Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("Arriving", !leaving, { leaving = false })
                Pill("Leaving", leaving, { leaving = true })
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Within", style = JType.small, color = jc.muted)
                Place.RADII.forEach { r -> Pill(if (r >= 1000) "${r / 1000} km" else "$r m", radius == r, { radius = r }) }
            }
            val found = naming
            if (found != null) {
                Text(found.label, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), style = JType.small, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PlainField(name, { name = it }, "Shop, Home, Office…", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onDone = {
                    onPick(found.copy(label = name.trim().ifEmpty { found.label }, leaving = leaving, radius = radius))
                })
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextAction("Back", { naming = null })
                    TextAction("Set Reminder", { onPick(found.copy(label = name.trim().ifEmpty { found.label }, leaving = leaving, radius = radius)) }, bold = true)
                }
                return@Column
            }
            PlaceRow(Ico.LOCATE, "Where I am now", null) { access.ensure(background = false) { ok -> if (ok) locate() } }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainField(query, { query = it }, "Search an address or place", Modifier.weight(1f), search = true, onDone = ::search)
                TextAction("Search", ::search, enabled = query.isNotBlank())
            }
            busy?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = JType.small, color = jc.muted) }
            error?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = JType.small, color = jc.danger) }
            results?.forEach { p -> PlaceRow(Ico.PLACE, p.label, null, lines = 2) { naming = p; name = "" } }
            if (results == null && recent.isNotEmpty()) {
                Text("Recent", Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp), style = JType.small, color = jc.muted)
                recent.forEach { p -> PlaceRow(Ico.PLACE, p.label, null) { onPick(p.copy(leaving = leaving, radius = radius)) } }
            }
            if (current != null) PlaceRow(Ico.TRASH, "Remove place reminder", null, danger = true, onClick = onRemove)
            Box(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), contentAlignment = Alignment.CenterEnd) { TextAction("Cancel", onDismiss) }
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
    Row(
        Modifier.padding(bottom = 10.dp).clip(JShape.xs).clickable(role = Role.Button, onClick = onClick)
            .background(jc.selected).padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Place reminder: ${place.phrase}. Tap to change it." },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JIcon(Ico.PLACE, jc.accent, size = 14.dp, stroke = 2f)
        Text(place.phrase, style = JType.small, color = jc.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                title = { Text("Use your location?") },
                text = {
                    Text(
                        if (background) "To remind you when you arrive at or leave a place, Margin checks your location in the background, " +
                            "even when the app is closed. Choose “Allow all the time” on the next screen. Your location stays on this phone: " +
                            "Margin doesn't store it or send it anywhere."
                        else "Margin uses your location once, to find where you are now. It stays on this phone.",
                    )
                },
                confirmButton = { TextButton(onClick = ::continueAsking) { Text("Continue") } },
                dismissButton = { TextButton(onClick = { explaining = null; finish(false) }) { Text("Not Now") } },
            )
        }
        if (noPrecise) MarginDialog(
            onDismissRequest = { noPrecise = false },
            title = { Text("Precise location needed") },
            text = { Text("Place reminders need precise location; approximate location can be off by a few kilometers. Turn on “Use precise location” for Margin in Settings.") },
            confirmButton = { TextButton(onClick = { noPrecise = false; openAppSettings(context) }) { Text("Open Settings") } },
            dismissButton = { TextButton(onClick = { noPrecise = false }) { Text("Not Now") } },
        )
        if (settingsHint) MarginDialog(
            onDismissRequest = { settingsHint = false },
            title = { Text("Location is only allowed while using Margin") },
            text = { Text("The reminder is saved, but it can't ring until Margin may use location all the time. In Settings, open Permissions › Location and choose “Allow all the time”.") },
            confirmButton = { TextButton(onClick = { settingsHint = false; openAppSettings(context) }) { Text("Open Settings") } },
            dismissButton = { TextButton(onClick = { settingsHint = false }) { Text("Not Now") } },
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

/** Where the phone is now, named by its address when Android can tell; null if it can't be found. */
@SuppressLint("MissingPermission") // asked for before
private fun currentPlace(context: Context, onResult: (Place?) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    runCatching {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) return@addOnSuccessListener onResult(null)
                kotlinx.coroutines.MainScope().launch {
                    val label = addressAt(context, loc.latitude, loc.longitude) ?: "%.5f, %.5f".format(loc.latitude, loc.longitude)
                    onResult(Place(loc.latitude, loc.longitude, label))
                }
            }
            .addOnFailureListener { onResult(null) }
    }.onFailure { onResult(null) }
}

/** A short address for a spot: "12 High Street, Leeds". */
private fun Address.short(): String =
    listOfNotNull(featureName?.takeIf { it != thoroughfare && it != subThoroughfare && it.any(Char::isLetter) },
        listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ").takeIf { it.isNotBlank() }, locality)
        .distinct().joinToString(", ").ifBlank { getAddressLine(0) ?: "" }

@Suppress("DEPRECATION")
private suspend fun addressAt(context: Context, lat: Double, lng: Double): String? {
    if (!Geocoder.isPresent()) return null
    val g = Geocoder(context)
    val list: List<Address>? = if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { c ->
        g.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { c.resume(addresses) }
            override fun onError(errorMessage: String?) { c.resume(null) }
        })
    } else withContext(Dispatchers.IO) { runCatching { g.getFromLocation(lat, lng, 1) }.getOrNull() }
    return list?.firstOrNull()?.short()?.takeIf { it.isNotBlank() }
}

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
    Toast.makeText(context, "Saved, but it can't ring until Margin may use location all the time.", Toast.LENGTH_LONG).show()
