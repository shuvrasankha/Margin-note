package com.margin.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.margin.app.ui.PlaceReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A spot on the map a reminder rings at: on arriving there, or on [leaving]. */
@Serializable
data class Place(
    val lat: Double,
    val lng: Double,
    /** What the user calls it: "Shop", "Home", or the address it was found by. */
    val label: String,
    /** Meters around the spot that count as being there. */
    val radius: Int = DEFAULT_RADIUS,
    val leaving: Boolean = false,
) {
    /** "When I arrive at Shop" / "When I leave Home". */
    val phrase get() = (if (leaving) "When I leave " else "When I arrive at ") + label

    /** The same spot under the same name (ignoring arrive/leave and size), for the recent places list. */
    fun sameSpot(other: Place) = label == other.label && Math.abs(lat - other.lat) < 1e-4 && Math.abs(lng - other.lng) < 1e-4

    companion object {
        /** Smaller than this, Android's location isn't precise enough to notice arriving. */
        const val DEFAULT_RADIUS = 150
        val RADII = listOf(150, 300, 1000)
    }
}

/**
 * Note reminders that ring at a place, kept on this device like time reminders. Each is a geofence Google Play
 * services watches; when it's crossed [PlaceReceiver] shows the note, and the reminder is done.
 */
@Singleton
class PlaceReminders @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("place_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _all = MutableStateFlow(read())
    /** Note id → the place its reminder rings at. */
    val all: StateFlow<Map<String, Place>> = _all

    private fun read(): Map<String, Place> = prefs.all.mapNotNull { (k, v) ->
        if (k.startsWith("_")) null else (v as? String)?.let { runCatching { json.decodeFromString<Place>(it) }.getOrNull() }?.let { k to it }
    }.toMap()

    fun at(noteId: String): Place? = _all.value[noteId]

    /** Places used before, newest first, to pick again. */
    fun recent(): List<Place> = prefs.getString(RECENT, null)?.let { runCatching { json.decodeFromString<List<Place>>(it) }.getOrNull() }.orEmpty()

    /** Whether Android lets Margin watch places while it isn't open: geofences need precise location, all the time. */
    fun canWatch(): Boolean = hasPrecise(context) && hasBackground(context)

    /** Rings [noteId] at [place], replacing its earlier place reminder. False if location access is missing. */
    fun set(noteId: String, place: Place): Boolean {
        prefs.edit().putString(noteId, json.encodeToString(Place.serializer(), place))
            .putString(RECENT, json.encodeToString((listOf(place.copy(leaving = false)) + recent().filterNot { it.sameSpot(place) }).take(RECENT_MAX)))
            .apply()
        _all.value = _all.value + (noteId to place)
        return register(mapOf(noteId to place))
    }

    fun clear(noteId: String) {
        prefs.edit().remove(noteId).apply()
        _all.value = _all.value - noteId
        runCatching { client().removeGeofences(listOf(noteId)) }
    }

    /** After a restart or an update, when Android has dropped the geofences, or once location access is given. */
    fun registerAll() {
        _all.value = read()
        if (_all.value.isNotEmpty()) register(_all.value)
    }

    @SuppressLint("MissingPermission") // checked just before
    private fun register(places: Map<String, Place>): Boolean {
        if (!canWatch()) return false
        val request = GeofencingRequest.Builder()
            // Not on being there already: a reminder set at the shop rings next time you get there.
            .setInitialTrigger(0)
            .addGeofences(places.map { (id, p) ->
                Geofence.Builder().setRequestId(id)
                    .setCircularRegion(p.lat, p.lng, p.radius.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(if (p.leaving) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setNotificationResponsiveness(60_000)
                    .build()
            })
            .build()
        return runCatching {
            client().addGeofences(request, pending())
                .addOnSuccessListener { android.util.Log.i(TAG, "Watching ${places.size} place(s)") }
                // 1000: location is off, or Google Location Accuracy is; PROVIDERS_CHANGED tries again.
                .addOnFailureListener { android.util.Log.w(TAG, "Couldn't watch places: ${it.message}") }
            true
        }.getOrDefault(false)
    }

    private fun client() = LocationServices.getGeofencingClient(context)

    // Play services adds the crossed geofences to this intent, so it has to be mutable.
    private fun pending() = PendingIntent.getBroadcast(
        context, 0, Intent(context, PlaceReceiver::class.java).setAction(PlaceReceiver.ACTION_PLACE),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    companion object {
        private const val TAG = "PlaceReminders"
        private const val RECENT = "_recent"
        private const val RECENT_MAX = 6

        fun hasForeground(c: Context) = granted(c, Manifest.permission.ACCESS_FINE_LOCATION) || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)
        fun hasPrecise(c: Context) = granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
        /** Android 10+ asks separately for location while the app is closed ("Allow all the time"). */
        fun hasBackground(c: Context) = Build.VERSION.SDK_INT < 29 || granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    }
}
