package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAutofillTest {
    private val github = "g" to Login(name = "GitHub", username = "me@example.com", password = "old", url = "https://github.com/login")
    private val bankApp = "b" to Login(name = "Bank", username = "1234", password = "pin", apps = listOf("com.bank.app"))
    private val wifi = "w" to Login(name = "Home", password = "wifipass", kind = VaultKind.WIFI.name)

    @Test
    fun `sites match on the host or under it, never a look-alike`() {
        assertTrue(hostMatches("github.com", "github.com"))
        assertTrue(hostMatches("https://www.github.com/login", "accounts.github.com"))
        assertFalse(hostMatches("github.com", "github.com.evil.io"))
        assertFalse(hostMatches("github.com", "notgithub.com"))
        assertFalse(hostMatches("", "github.com"))
    }

    @Test
    fun `logins fill their site or linked app, other kinds never`() {
        assertTrue(github.second.fills("com.android.chrome", "github.com"))
        assertFalse(github.second.fills("com.evil.app", null)) // an app's claimed site isn't passed in
        assertTrue(bankApp.second.fills("com.bank.app", null))
        assertFalse(wifi.second.fills(null, "home"))
    }

    @Test
    fun `a changed password updates the matching login`() {
        val plan = planAutofillSave(listOf(github, bankApp), "com.android.chrome", "github.com", "ME@example.com ", "new", "github.com")
        plan as AutofillSave.Update
        assertEquals("g", plan.id)
        assertEquals("new", plan.login.password)
        // Its history comes from stamping on save.
        val stamped = stampPasswordChange(github.second, plan.login, 100)
        assertEquals("old", stamped.history.single().password)
    }

    @Test
    fun `the same sign-in again changes nothing`() {
        assertEquals(AutofillSave.Unchanged, planAutofillSave(listOf(github), "com.android.chrome", "github.com", "me@example.com", "old", "github.com"))
    }

    @Test
    fun `a new sign-in in an app makes a login linked to that app`() {
        val plan = planAutofillSave(listOf(github), "com.shop.app", null, "me", "pw", "Shop")
        plan as AutofillSave.Create
        assertEquals("Shop", plan.login.name)
        assertEquals(listOf("com.shop.app"), plan.login.apps)
        assertEquals("", plan.login.url)
    }

    @Test
    fun `a new sign-in in a browser makes a login for the site`() {
        val plan = planAutofillSave(emptyList(), "com.android.chrome", "www.example.com", "me", "pw", "example.com")
        plan as AutofillSave.Create
        assertEquals("example.com", plan.login.url)
        assertTrue(plan.login.apps.isEmpty())
    }

    @Test
    fun `a password change with no username asks which login when several match`() {
        val second = "g2" to github.second.copy(username = "work@example.com", password = "other")
        val plan = planAutofillSave(listOf(github, second), "com.android.chrome", "github.com", "", "new", "github.com")
        assertEquals(AutofillSave.Choose(listOf("g", "g2")), plan)
    }

    @Test
    fun `entries saved before kinds existed read as logins`() {
        val old = MarginJson.decodeFromString(Login.serializer(), """{"name":"Old","password":"x"}""")
        assertEquals(VaultKind.LOGIN, old.vaultKind)
        assertTrue(old.apps.isEmpty() && old.icon.isEmpty())
    }
}
