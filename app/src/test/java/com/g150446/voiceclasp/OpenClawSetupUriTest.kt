package com.g150446.voiceclasp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenClawSetupUriTest {
    @Test
    fun `parses the uri openclaw connect-qr prints`() {
        // Output of `openclaw connect-qr --uri-only` (URLSearchParams encoding: space is +).
        val setup = parseOpenClawSetupUri(
            "openclaw-connect://http?v=1&url=http%3A%2F%2F100.64.0.1%3A18789%2F&auth=token" +
                "&secret=a%26b%3Dc%3Fd%2Fe%2Bf+g&agent=openclaw%2Fmain&name=my-mac",
        ).getOrThrow()

        assertEquals("http://100.64.0.1:18789", setup.baseUrl)
        assertEquals("a&b=c?d/e+f g", setup.secret)
        assertEquals("openclaw/main", setup.agent)
        assertEquals("my-mac", setup.name)
    }

    @Test
    fun `agent and name are optional`() {
        val setup = parseOpenClawSetupUri(
            "openclaw-connect://http?v=1&url=http%3A%2F%2Fh%3A1&auth=password&secret=s",
        ).getOrThrow()

        assertNull(setup.agent)
        assertNull(setup.name)
    }

    @Test
    fun `rejects other codes and never echoes the secret`() {
        listOf(
            "harbor://pair?x=1",
            "openclaw-connect://http?v=2&url=http%3A%2F%2Fh&auth=token&secret=TOPSECRET",
            "openclaw-connect://http?v=1&url=ftp%3A%2F%2Fh&auth=token&secret=TOPSECRET",
            "openclaw-connect://http?v=1&url=http%3A%2F%2Fh&auth=bearer&secret=TOPSECRET",
            "openclaw-connect://http?v=1&url=http%3A%2F%2Fh&auth=token",
        ).forEach { raw ->
            val result = parseOpenClawSetupUri(raw)
            assertTrue(raw, result.isFailure)
            assertFalse(raw, result.exceptionOrNull()!!.message.orEmpty().contains("TOPSECRET"))
        }
    }
}
