package com.shuva.jotter.data

/**
 * Something that looks like a phone number: an optional "+", then digits with spaces, dashes or
 * brackets between them (at most two in a row, as in "(555) 123"). Dots aren't allowed, so dates ("10.11.2026"), sums and IP addresses don't match,
 * and nor do the digits of a decimal ("0.583333333333").
 */
private val PHONE = Regex("""(?<![\w+.,])\+?\(?\d(?:[ \-()]{0,2}\d)+(?!\w|[.,]\d)""")

/**
 * Each phone number's range and text. Without "+", it needs 10 to 13 digits (a mobile number, with or without
 * its country code), which keeps years, amounts and times out; with "+", 8 to 15 digits.
 */
fun findPhones(text: String): List<Pair<IntRange, String>> = PHONE.findAll(text).mapNotNull { m ->
    val digits = m.value.count { it.isDigit() }
    val ok = if (m.value.startsWith("+")) digits in 8..15 else digits in 10..13
    if (ok) m.range to m.value else null
}.toList()

/** The number with only its digits and a leading "+", for tel: and sms: links. */
fun dialable(number: String) = (if (number.trim().startsWith("+")) "+" else "") + number.filter { it.isDigit() }
