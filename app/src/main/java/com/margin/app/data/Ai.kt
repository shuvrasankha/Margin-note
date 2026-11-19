package com.margin.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Bring your own AI key: the user's own API key for one provider, kept encrypted on this phone, and the
 * request each provider expects. Providers, models and request shapes are the ones SpendWise uses.
 */

/** A provider the user can bring a key for. [keyHint] is the field placeholder, [keyHelp] where to get one. */
/**
 * An AI provider: where requests go, its default model, how its keys start ([keyHint], also used to tell
 * a pasted key's provider), the page where a key is made ([keyUrl]), and whether it has a free tier.
 */
data class AiProvider(
    val id: String, val name: String, val url: String, val defaultModel: String, val keyHint: String, val keyHelp: String,
    val keyUrl: String = "", val free: Boolean = false, val short: String = name,
)

val AI_PROVIDERS = listOf(
    AiProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-3.8-flash", "AIza…", "Get a key at aistudio.google.com.", "https://aistudio.google.com/apikey", free = true, short = "Gemini"),
    AiProvider("openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-6-luna", "sk-…", "Get a key at platform.openai.com.", "https://platform.openai.com/api-keys"),
    AiProvider("anthropic", "Anthropic", "https://api.anthropic.com/v1/messages", "claude-opus-5-5", "sk-ant-…", "Get a key at platform.claude.com.", "https://platform.claude.com/settings/keys", short = "Claude"),
    AiProvider("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-120b", "gsk_…", "Get a key at console.groq.com.", "https://console.groq.com/keys", free = true),
    AiProvider("nvidia", "NVIDIA", "https://integrate.api.nvidia.com/v1/chat/completions", "nvidia/nemotron-3.5-super-vl-120b-a12b", "nvapi-…", "Get a key at build.nvidia.com.", "https://build.nvidia.com/settings/api-keys", free = true),
)

/** The provider a pasted key belongs to, told by how it starts; null when it's not clear. */
fun providerForKey(key: String): AiProvider? {
    val k = key.trim()
    val id = when {
        k.startsWith("AIza") -> "gemini"
        k.startsWith("sk-ant-") -> "anthropic"
        k.startsWith("gsk_") -> "groq"
        k.startsWith("nvapi-") -> "nvidia"
        k.startsWith("sk-") -> "openai"
        else -> return null
    }
    return AI_PROVIDERS.first { it.id == id }
}

fun aiProvider(id: String?) = AI_PROVIDERS.firstOrNull { it.id == id } ?: AI_PROVIDERS.first()

/** What the user reads when a request fails. Never the provider's body or the key. */
class AiException(message: String) : Exception(message)

class AiRequest(val url: String, val headers: Map<String, String>, val body: String)

/** Reasoning models spend hidden thinking from the same token budget; without room the answer comes back empty. */
private const val THINKING_HEADROOM = 2048

/** One question ([system] instructions, [user] text) as [p] wants it. */
fun aiRequest(p: AiProvider, key: String, model: String, system: String, user: String, maxTokens: Int): AiRequest {
    val json = mapOf("Content-Type" to "application/json", "Accept" to "application/json")
    val default = model == p.defaultModel
    if (p.id == "anthropic") {
        // Messages API: the system text goes top-level, and this version refuses temperature.
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens + THINKING_HEADROOM)
            if (system.isNotEmpty()) put("system", system)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", user) } }
            if (default) putJsonObject("output_config") { put("effort", "low") }
        }
        return AiRequest(p.url, json + mapOf("x-api-key" to key, "anthropic-version" to "2026-10-21"), body.toString())
    }
    val body = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            if (system.isNotEmpty()) addJsonObject { put("role", "system"); put("content", system) }
            addJsonObject { put("role", "user"); put("content", user) }
        }
        when (p.id) {
            // OpenAI's models reason: max_completion_tokens counts the reasoning, and some refuse temperature.
            "openai" -> { put("max_completion_tokens", maxTokens + THINKING_HEADROOM); if (default) put("reasoning_effort", "none") }
            "gemini" -> { put("max_tokens", maxTokens + THINKING_HEADROOM); put("temperature", 0.3); if (default) put("reasoning_effort", "low") }
            else -> {
                put("max_tokens", maxTokens + THINKING_HEADROOM); put("temperature", 0.3)
                if (p.id == "nvidia" && default) putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
            }
        }
    }
    return AiRequest(p.url, json + ("Authorization" to "Bearer $key"), body.toString())
}

/** The answer text in a 2xx reply from [p], or null when there is none. Reasoning in <think> tags is dropped. */
fun aiReply(p: AiProvider, body: String): String? = runCatching {
    val o = Json.parseToJsonElement(body).jsonObject
    if (p.id == "anthropic") {
        o["content"]!!.jsonArray.map { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.content == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.content.orEmpty() }
    } else {
        o["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.contentOrNull
    }
}.getOrNull()?.let(::withoutThinking)?.trim()?.ifEmpty { null }

/** Some models (Qwen, DeepSeek) put their reasoning in the answer; an unclosed tag means it never finished. */
private fun withoutThinking(s: String) = s.replace(Regex("(?s)<think>.*?</think>"), "").let { t ->
    t.indexOf("<think>").let { if (it >= 0) t.substring(0, it) else t }
}

/** Why a 2xx reply from [p] stopped early: "length" when it ran out of tokens, "refusal" when declined; else null. */
fun aiStop(p: AiProvider, body: String): String? = runCatching {
    val o = Json.parseToJsonElement(body).jsonObject
    val reason = if (p.id == "anthropic") o["stop_reason"]?.jsonPrimitive?.contentOrNull
    else o["choices"]!!.jsonArray.first().jsonObject["finish_reason"]?.jsonPrimitive?.contentOrNull
    when (reason) {
        "length", "max_tokens" -> "length"
        "refusal", "content_filter" -> "refusal"
        else -> null
    }
}.getOrNull()

/**
 * An answer as plain note text: Markdown emphasis, heading marks and code fences removed, "*" bullets as "-".
 * Models are asked for plain text, but often answer in Markdown anyway.
 */
fun plainAnswer(s: String): String = s.lines()
    .filterNot { it.trimStart().startsWith("```") }
    .joinToString("\n") { line ->
        line.replace(Regex("^(\\s*)#{1,6}\\s+"), "$1")
            .replace(Regex("^(\\s*)[*+]\\s+"), "$1- ")
            .replace(Regex("\\*\\*(.+?)\\*\\*|__(.+?)__"), "$1$2")
            .replace(Regex("`([^`]+)`"), "$1")
    }.trim()

/** An error reply in words. The body only steers the wording; it is never shown. */
fun aiErrorText(status: Int, body: String): String = when {
    status == 401 || status == 403 -> "API key rejected. Check the key."
    // Gemini answers a bad key with a 400
    status == 400 && (body.contains("API key", ignoreCase = true) || body.contains("API_KEY_INVALID")) -> "API key rejected. Check the key."
    status == 404 || status == 410 || (status == 400 && body.contains("model", ignoreCase = true)) -> "Model unavailable. Check the model name."
    status == 429 -> "Provider limit reached. Check your provider account or try later."
    status == 503 || status == 529 -> "Provider is busy. Try again shortly."
    else -> "Provider error ($status). Try again later."
}

/** Sends [r] and returns the reply's status and body. Blocking; call off the main thread. */
private fun post(r: AiRequest): Pair<Int, String> {
    val u = URL(r.url)
    require(u.protocol == "https") { "AI requests must use https" } // the key goes in a header
    val c = u.openConnection() as HttpURLConnection
    try {
        c.requestMethod = "POST"
        c.connectTimeout = 30_000
        c.readTimeout = 120_000 // reasoning models can think for a while before the first byte
        c.doOutput = true
        c.instanceFollowRedirects = false // a redirect would resend the key to wherever it points
        r.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(r.body.toByteArray()) }
        val status = c.responseCode
        return status to (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
    } finally {
        c.disconnect()
    }
}

/** The chosen provider, its model ("" = the default), the encrypted key ("iv.ciphertext", base64) and the key's last 4 characters. */
data class AiConfig(val provider: String = AI_PROVIDERS.first().id, val model: String = "", val key: String? = null, val mask: String? = null)

private const val KEY_ALIAS = "margin_ai_key_v1"

/**
 * The user's API key and the calls made with it. The key is encrypted with AES-256-GCM by a key that never
 * leaves the Android Keystore (no fingerprint needed, so AI works with locked notes off), and the app isn't
 * backed up or synced, so it stays on this phone. Never log a key.
 */
@Singleton
class AiKeys @Inject constructor(private val prefs: AppPrefs) {
    val config = prefs.ai

    private fun keystoreKey(create: Boolean): SecretKey? {
        (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
            )
            generateKey()
        }
    }

    /** Saves [key] for the chosen provider. False when it doesn't look like a key or can't be stored securely. */
    fun save(key: String): Boolean {
        if (key.length < 8 || key.any { it.isWhitespace() }) return false
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey(create = true)) }
            val blob = b64(cipher.iv) + "." + b64(cipher.doFinal(key.toByteArray()))
            prefs.setAi(config.value.copy(key = blob, mask = key.takeLast(4)))
        }.isSuccess
    }

    /** The saved key, or null when there is none. One that can't be decrypted (Keystore reset) is removed. */
    private fun key(): String? {
        val blob = config.value.key ?: return null
        return runCatching {
            val (iv, ct) = blob.split('.').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(create = false), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(ct))
        }.getOrElse { remove(); null }
    }

    fun remove() {
        prefs.setAi(config.value.copy(key = null, mask = null))
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    /** A key belongs to its provider, so choosing another one removes the saved key and model. */
    fun setProvider(id: String) {
        if (id == config.value.provider) return
        remove()
        prefs.setAi(AiConfig(provider = id))
    }

    fun setModel(model: String) = prefs.setAi(config.value.copy(model = model.trim()))

    /** The provider's answer to [user]. Throws [AiException] with words for the user. */
    suspend fun ask(system: String, user: String): String =
        send(system, user, 4096) ?: throw AiException("The provider sent back an empty answer. Try again, or another model.")

    /** One token of answer: enough to prove the key and model work. Throws [AiException] when they don't. */
    suspend fun test() { send("", "Hi", 1) }

    /**
     * Setup in one step: switches to the provider [key] belongs to (when it's clear), saves it and tests it.
     * A key the provider rejects isn't kept, so the next paste starts clean. Throws [AiException] in words.
     */
    suspend fun connect(key: String) {
        val k = key.trim()
        providerForKey(k)?.let { setProvider(it.id) }
        if (!save(k)) throw AiException("That doesn't look like an API key.")
        try { test() } catch (e: AiException) {
            if (e.message?.startsWith("API key rejected") == true) remove()
            throw e
        }
    }

    private suspend fun send(system: String, user: String, maxTokens: Int): String? = withContext(Dispatchers.IO) {
        val cfg = config.value
        val p = aiProvider(cfg.provider)
        val key = key() ?: throw AiException(if (cfg.key == null) "Add your API key in Settings first." else "Your saved key couldn't be read, so it was removed. Add it again in Settings.")
        val (status, body) = try {
            post(aiRequest(p, key, cfg.model.ifBlank { p.defaultModel }, system, user, maxTokens))
        } catch (e: SocketTimeoutException) {
            throw AiException("Provider took too long. Try again.")
        } catch (e: IOException) {
            throw AiException("Could not connect. Check your internet.")
        }
        if (status !in 200..299) throw AiException(aiErrorText(status, body))
        val reply = aiReply(p, body)
        when (aiStop(p, body)) {
            "refusal" -> throw AiException("${p.name} declined to answer this.")
            // A test asks for one token, so running out is expected there.
            "length" -> if (maxTokens > 1) return@withContext reply?.let { "$it …" }
                ?: throw AiException("The answer ran out of room before it started. Try a shorter note or another model.")
        }
        reply
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
