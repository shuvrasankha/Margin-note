package com.margin.app.autofill

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillFormTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

    @Test
    fun `hints come first`() {
        assertEquals(FieldRole.USERNAME, roleOf(FieldInfo(hints = listOf("emailAddress"), inputType = text)))
        assertEquals(FieldRole.NEW_PASSWORD, roleOf(FieldInfo(htmlAutocomplete = "new-password", htmlType = "password")))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(htmlAutocomplete = "one-time-code", inputType = text)))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(hints = listOf("2faAppOTPCode"))))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(hints = listOf("smsOTPCode"))))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(inputType = text, words = listOf("totp_code"))))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(inputType = text, words = listOf("Enter verification code"))))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(inputType = text, words = listOf("otp-input"))))
        assertEquals(FieldRole.OTP, roleOf(FieldInfo(inputType = text, htmlAutocomplete = "off", words = listOf("login_otp"))))
        assertEquals(FieldRole.NONE, roleOf(FieldInfo(inputType = text, htmlAutocomplete = "off", words = listOf("comment"))))
        assertEquals(FieldRole.PASSWORD, roleOf(FieldInfo(inputType = text, words = listOf("passcode"), htmlType = "password")))
        assertEquals(FieldRole.USERNAME, roleOf(FieldInfo(inputType = text, words = listOf("hotpot_lover_email"))))
    }

    @Test
    fun `input type and words fill in`() {
        assertEquals(FieldRole.PASSWORD, roleOf(FieldInfo(inputType = password)))
        assertEquals(FieldRole.PASSWORD, roleOf(FieldInfo(htmlType = "password")))
        assertEquals(FieldRole.USERNAME, roleOf(FieldInfo(inputType = text, words = listOf("login_email"))))
        assertEquals(FieldRole.NONE, roleOf(FieldInfo(inputType = text, words = listOf("search_users"))))
        assertEquals(FieldRole.NONE, roleOf(FieldInfo(inputType = text, words = listOf("passport_number"))))
    }

    @Test
    fun `the field before an unnamed password is its username`() {
        val roles = rolesOf(listOf(FieldInfo(inputType = text), FieldInfo(inputType = text, words = listOf("phone")), FieldInfo(inputType = password)))
        assertEquals(listOf(FieldRole.NONE, FieldRole.USERNAME, FieldRole.PASSWORD), roles)
    }
}
