package com.margin.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTest {
    @Test fun phrases() {
        assertEquals("When I arrive at Shop", Place(1.0, 2.0, "Shop").phrase)
        assertEquals("When I leave Home", Place(1.0, 2.0, "Home", leaving = true).phrase)
    }

    @Test fun storedFormReadsBackAndIgnoresNewFields() {
        val p = Place(51.5007, -0.1246, "Big Ben", radius = 300, leaving = true)
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(p, json.decodeFromString<Place>(json.encodeToString(Place.serializer(), p)))
        val older = json.decodeFromString<Place>("""{"lat":1.0,"lng":2.0,"label":"Gym","future":true}""")
        assertEquals(Place.DEFAULT_RADIUS, older.radius)
        assertFalse(older.leaving)
    }

    @Test fun sameSpotIgnoresDirectionAndSize() {
        val shop = Place(10.0, 20.0, "Shop")
        assertTrue(shop.sameSpot(shop.copy(leaving = true, radius = 1000, lat = 10.00001)))
        assertFalse(shop.sameSpot(shop.copy(label = "Market")))
        assertFalse(shop.sameSpot(shop.copy(lat = 10.01)))
    }
}
