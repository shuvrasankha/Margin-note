package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrustedBrowsersTest {
    private val list = VaultIcons.trustedBrowsers(File("src/main/assets/trusted_browsers.json").readText())

    @Test fun knownBrowsersCarryTheirReleaseKeysOnly() {
        // Chrome's release key (checked against the copy on a phone); its userdebug key is left out.
        assertEquals(setOf("f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"), list["com.android.chrome"])
        assertTrue("9c2db70513515fdbfbbc585b3edf3d7123d4dc67c94ffd306361c1d79bbf18ac" in list["com.brave.browser"]!!)
        assertTrue(list.containsKey("org.mozilla.firefox") && list.containsKey("com.sec.android.app.sbrowser"))
    }

    @Test fun anyOtherAppIsNotABrowser() {
        assertNull(list["com.evil.phish"])
        assertFalse(list.containsKey("com.google.android.apps.chrome")) // listed with a debug key only
    }
}
