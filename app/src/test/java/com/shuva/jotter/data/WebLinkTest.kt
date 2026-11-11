package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLinkTest {
    @Test
    fun `addresses get https and only http(s) with a real host is allowed`() {
        assertEquals("https://example.com/a?b=1", normalizeUrl("  example.com/a?b=1 "))
        assertEquals("http://example.com", normalizeUrl("http://example.com"))
        assertNull(normalizeUrl("javascript:alert(1)"))
        assertNull(normalizeUrl("file:///etc/passwd"))
        assertNull(normalizeUrl("ftp://example.com"))
        assertNull(normalizeUrl("hello world"))
        assertNull(normalizeUrl("localhost"))
        assertNull(normalizeUrl(""))
    }

    @Test
    fun `page title is read, tidied and decoded`() {
        assertEquals("Tom & Jerry's \"Show\" <1>", pageTitle("<html><head><TITLE lang=en>\n  Tom &amp; Jerry&#39;s &quot;Show&quot;\n &lt;1&gt; </TITLE>"))
        assertEquals("é – ok", pageTitle("<title>&#233; &#x2013; ok</title>"))
        assertEquals("&lt;", pageTitle("<title>&amp;lt;</title>")) // decoded once only
        assertNull(pageTitle("<html><body>no title</body></html>"))
        assertNull(pageTitle("<title>   </title>"))
        assertEquals(200, pageTitle("<title>" + "x".repeat(500) + "</title>")!!.length)
    }

    @Test
    fun `host drops www`() {
        assertEquals("example.com", hostOf("https://www.example.com/path"))
    }

    @Test
    fun `preview reads og tags in any attribute order and resolves the picture`() {
        val html = """<head><meta content="A &amp; B" property="og:description"><meta name="description" content="plain">
            <meta property='og:image' content='/img/cover.png'><meta property="og:image" content="https://other.example/second.png"></head>"""
        assertEquals(PagePreview("A & B", "https://example.com/img/cover.png"), pagePreview(html, "https://example.com/post/1"))
    }

    @Test
    fun `preview falls back to the plain description and refuses non-web pictures`() {
        val p = pagePreview("<meta name=\"Description\" content=\"  Just   text \"><meta property=\"og:image\" content=\"javascript:x\">", "https://example.com")
        assertEquals(PagePreview("Just text", null), p)
        assertEquals(PagePreview(), pagePreview("<html>nothing</html>", "https://example.com"))
        assertEquals("https://cdn.example/a.jpg", pagePreview("<meta property=og:image content=//cdn.example/a.jpg>", "https://example.com").image)
    }
}
