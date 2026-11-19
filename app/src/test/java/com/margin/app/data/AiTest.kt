package com.margin.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {
    private fun body(r: AiRequest) = Json.parseToJsonElement(r.body).jsonObject

    @Test fun everyProviderIsHttpsAndGetsTheKeyOnlyInAHeader() {
        for (p in AI_PROVIDERS) {
            val r = aiRequest(p, "secret-key-123", p.defaultModel, "sys", "hello", 100)
            assertTrue(p.id, r.url.startsWith("https://"))
            assertTrue(p.id, r.headers.values.any { "secret-key-123" in it })
            assertFalse(p.id, "secret-key-123" in r.body)
            assertEquals(p.defaultModel, body(r)["model"]!!.jsonPrimitive.content)
        }
    }

    @Test fun anthropicUsesItsOwnShape() {
        val r = aiRequest(aiProvider("anthropic"), "k", "some-model", "sys", "hello", 100)
        assertEquals("k", r.headers["x-api-key"])
        assertNull(r.headers["Authorization"])
        assertEquals("sys", body(r)["system"]!!.jsonPrimitive.content)
        assertEquals("user", body(r)["messages"]!!.jsonArray.single().jsonObject["role"]!!.jsonPrimitive.content)
        assertNull(body(r)["output_config"]) // only the default model is known to take it
    }

    @Test fun openAiCompatibleShape() {
        val r = aiRequest(aiProvider("groq"), "k", "m", "sys", "hello", 100)
        assertEquals("Bearer k", r.headers["Authorization"])
        assertEquals(listOf("system", "user"), body(r)["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals(100 + 2048, body(r)["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertNull(body(aiRequest(aiProvider("openai"), "k", "m", "", "hello", 100))["max_tokens"])
    }

    @Test fun readsReplies() {
        assertEquals("Hi there", aiReply(aiProvider("openai"), """{"choices":[{"message":{"role":"assistant","content":" Hi there\n"}}]}"""))
        assertEquals("Hi there", aiReply(aiProvider("anthropic"), """{"content":[{"type":"thinking","thinking":"hm"},{"type":"text","text":"Hi "},{"type":"text","text":"there"}]}"""))
        assertNull(aiReply(aiProvider("openai"), """{"choices":[{"message":{"content":null}}]}"""))
        assertNull(aiReply(aiProvider("gemini"), "not json"))
    }

    @Test fun errorsNeverShowTheBody() {
        assertEquals("API key rejected. Check the key.", aiErrorText(401, "secret"))
        assertEquals("API key rejected. Check the key.", aiErrorText(400, """{"error":{"status":"API_KEY_INVALID"}}"""))
        assertEquals("Model unavailable. Check the model name.", aiErrorText(404, ""))
        assertEquals("Provider limit reached. Check your provider account or try later.", aiErrorText(429, ""))
        assertEquals("Provider error (500). Try again later.", aiErrorText(500, "secret"))
    }

    @Test fun dropsReasoningInTheAnswer() {
        val groq = aiProvider("groq")
        assertEquals("Hi", aiReply(groq, """{"choices":[{"message":{"content":"<think>\nlet me see\n</think>\n\nHi"}}]}"""))
        // Ran out mid-thought: no answer at all.
        assertNull(aiReply(groq, """{"choices":[{"message":{"content":"<think>still going"}}]}"""))
    }

    @Test fun tellsWhyAReplyStopped() {
        assertEquals("length", aiStop(aiProvider("groq"), """{"choices":[{"finish_reason":"length","message":{"content":"a"}}]}"""))
        assertEquals("length", aiStop(aiProvider("anthropic"), """{"stop_reason":"max_tokens","content":[]}"""))
        assertEquals("refusal", aiStop(aiProvider("anthropic"), """{"stop_reason":"refusal","content":[]}"""))
        assertNull(aiStop(aiProvider("openai"), """{"choices":[{"finish_reason":"stop","message":{"content":"a"}}]}"""))
    }

    @Test fun everyProviderGetsRoomToThink() {
        for (p in AI_PROVIDERS) {
            val b = body(aiRequest(p, "k", p.defaultModel, "", "hi", 100))
            val limit = (b["max_tokens"] ?: b["max_completion_tokens"])!!.jsonPrimitive.content.toInt()
            assertTrue(p.id, limit > 100)
        }
    }

    @Test fun answersBecomePlainNoteText() = assertEquals(
        "Plan\n- buy milk\n- call Sam\nsee the notes",
        plainAnswer("## Plan\n* buy **milk**\n- call `Sam`\n```\nsee the __notes__"),
    )

    @Test fun unknownProviderFallsBackToTheFirst() = assertEquals(AI_PROVIDERS.first(), aiProvider("gone"))

    @Test fun pastedKeysAreMatchedToTheirProvider() {
        assertEquals("gemini", providerForKey("AIzaSyAbc123")?.id)
        assertEquals("anthropic", providerForKey("sk-ant-api03-xyz")?.id)
        assertEquals("openai", providerForKey("sk-proj-xyz")?.id)
        assertEquals("groq", providerForKey(" gsk_abc ")?.id)
        assertEquals("nvidia", providerForKey("nvapi-abc")?.id)
        assertNull(providerForKey("not-a-key"))
    }

    @Test fun everyProviderHasAKeyPage() = assertTrue(AI_PROVIDERS.all { it.keyUrl.startsWith("https://") })
}
