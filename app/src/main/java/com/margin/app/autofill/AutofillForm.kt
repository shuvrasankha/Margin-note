package com.margin.app.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** What a field on a sign-in form is for. */
enum class FieldRole { USERNAME, PASSWORD, NEW_PASSWORD, NONE }

/** What Android tells about one text field, enough to guess its role. */
data class FieldInfo(
    val hints: List<String> = emptyList(),
    val inputType: Int = 0,
    val htmlType: String? = null,
    val htmlAutocomplete: String? = null,
    /** The view's resource id name, its hint text and its HTML name/id: words like "email" or "pwd". */
    val words: List<String> = emptyList(),
)

private val PASSWORD_VARIATIONS = setOf(
    InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
)
private val EMAIL_VARIATIONS = setOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)

/** The role of one field on its own: the app's autofill hints first, then the input type, then its words. */
fun roleOf(f: FieldInfo): FieldRole {
    val hints = (f.hints + listOfNotNull(f.htmlAutocomplete)).flatMap { it.lowercase().split(' ') }
    if (hints.any { it == "off" || it == "one-time-code" || it == "cc-number" || it == "cc-csc" }) return FieldRole.NONE
    if (hints.any { it == "newpassword" || it == "new-password" }) return FieldRole.NEW_PASSWORD
    if (hints.any { it == "password" || it == "current-password" || it == "currentpassword" }) return FieldRole.PASSWORD
    if (hints.any { it == "username" || it == "emailaddress" || it == "email" || it == "newusername" }) return FieldRole.USERNAME
    val cls = f.inputType and InputType.TYPE_MASK_CLASS
    val variation = f.inputType and InputType.TYPE_MASK_VARIATION
    if (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) return FieldRole.PASSWORD
    if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return FieldRole.PASSWORD
    val type = f.htmlType?.lowercase()
    if (type == "password") return FieldRole.PASSWORD
    val words = f.words.joinToString(" ").lowercase()
    if ("search" in words) return FieldRole.NONE
    if (cls == InputType.TYPE_CLASS_TEXT && variation in EMAIL_VARIATIONS || type == "email") return FieldRole.USERNAME
    if (Regex("pass(word|wd|code)?\\b|pwd|passwort|contrase|mot de passe").containsMatchIn(words) && type != "text") return FieldRole.PASSWORD
    if (Regex("user|e-?mail|login|account|identifier").containsMatchIn(words)) return FieldRole.USERNAME
    return FieldRole.NONE
}

/**
 * Roles for a form's text [fields] in screen order. Where a password has no username field named as such,
 * the text field just before the first password is taken as the username.
 */
fun rolesOf(fields: List<FieldInfo>): List<FieldRole> {
    val roles = fields.map(::roleOf).toMutableList()
    val firstPassword = roles.indexOfFirst { it == FieldRole.PASSWORD || it == FieldRole.NEW_PASSWORD }
    if (firstPassword > 0 && FieldRole.USERNAME !in roles) {
        val before = (firstPassword - 1 downTo 0).firstOrNull { roles[it] == FieldRole.NONE && fields[it].looksTyped() }
        if (before != null) roles[before] = FieldRole.USERNAME
    }
    return roles
}

private fun FieldInfo.looksTyped(): Boolean {
    val cls = inputType and InputType.TYPE_MASK_CLASS
    return cls == InputType.TYPE_CLASS_TEXT || cls == InputType.TYPE_CLASS_PHONE || (inputType == 0 && htmlType in setOf(null, "text", "tel"))
}

/** A sign-in form as autofill sees it. */
class AutofillForm(
    val pkg: String,
    /** The site a browser (or web view) says it shows; trust it only from a browser. */
    val webDomain: String?,
    val username: List<AutofillId>,
    /** Where the current password goes. */
    val password: List<AutofillId>,
    /** New-password fields: saved from, never filled with the old password. */
    val newPassword: List<AutofillId>,
    val usernameValue: String?,
    /** The password to save: the last one typed into a new-password field, else into a password field. */
    val passwordValue: String?,
) {
    val isEmpty get() = username.isEmpty() && password.isEmpty() && newPassword.isEmpty()

    companion object {
        fun parse(structure: AssistStructure): AutofillForm {
            val nodes = mutableListOf<AssistStructure.ViewNode>()
            var domain: String? = null
            fun walk(n: AssistStructure.ViewNode) {
                if (domain == null) n.webDomain?.takeIf { it.isNotBlank() }?.let { domain = it }
                if (n.autofillId != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && n.visibility == View.VISIBLE) nodes += n
                for (i in 0 until n.childCount) walk(n.getChildAt(i))
            }
            for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
            val roles = rolesOf(nodes.map(::infoOf))
            fun ids(r: FieldRole) = nodes.indices.filter { roles[it] == r }.map { nodes[it].autofillId!! }
            fun values(r: FieldRole) = nodes.indices.filter { roles[it] == r }.mapNotNull { nodes[it].autofillValue?.takeIf { v -> v.isText }?.textValue?.toString()?.takeIf(String::isNotEmpty) }
            return AutofillForm(
                pkg = structure.activityComponent.packageName,
                webDomain = domain,
                username = ids(FieldRole.USERNAME),
                password = ids(FieldRole.PASSWORD),
                newPassword = ids(FieldRole.NEW_PASSWORD),
                usernameValue = values(FieldRole.USERNAME).firstOrNull(),
                passwordValue = values(FieldRole.NEW_PASSWORD).lastOrNull() ?: values(FieldRole.PASSWORD).lastOrNull(),
            )
        }

        private fun infoOf(n: AssistStructure.ViewNode): FieldInfo {
            val attrs = n.htmlInfo?.attributes.orEmpty().mapNotNull { p -> p.first?.lowercase()?.let { it to (p.second ?: "") } }.toMap()
            val words = listOfNotNull(n.idEntry, n.hint, attrs["name"], attrs["id"], attrs["placeholder"], attrs["aria-label"]) +
                (if (Build.VERSION.SDK_INT >= 28) listOfNotNull(n.textIdEntry) else emptyList())
            return FieldInfo(
                hints = n.autofillHints?.toList().orEmpty(),
                inputType = n.inputType,
                htmlType = attrs["type"],
                htmlAutocomplete = attrs["autocomplete"],
                words = words,
            )
        }
    }
}
