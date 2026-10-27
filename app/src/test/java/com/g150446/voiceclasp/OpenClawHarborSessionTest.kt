package com.g150446.voiceclasp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenClawHarborSessionTest {
    @Test
    fun `one session per workspace`() {
        assertEquals(
            "voice-clasp:abc:harbor:ws-1",
            harborSessionKey("voice-clasp:abc", "ws-1"),
        )
        assertTrue(
            harborSessionKey("voice-clasp:abc", "ws-1") !=
                harborSessionKey("voice-clasp:abc", "ws-2"),
        )
    }

    @Test
    fun `an unknown workspace keeps the shared harbor session`() {
        assertEquals("voice-clasp:abc:harbor", harborSessionKey("voice-clasp:abc", null))
        assertEquals("voice-clasp:abc:harbor", harborSessionKey("voice-clasp:abc", "   "))
    }

    @Test
    fun `a workspace id cannot introduce a namespace of its own`() {
        assertEquals(
            "voice-clasp:abc:harbor:subagent-evil",
            harborSessionKey("voice-clasp:abc", "subagent:evil"),
        )
        assertEquals(
            "voice-clasp:abc:harbor:a-b",
            harborSessionKey("voice-clasp:abc", "  a/b  "),
        )
    }

    @Test
    fun `interpretation sessions are hidden from the chat session picker`() {
        assertTrue(isHarborSessionKey("voice-clasp:abc:harbor"))
        assertTrue(isHarborSessionKey("voice-clasp:abc:harbor:ws-1"))
        assertFalse(isHarborSessionKey("voice-clasp:abc"))
        assertFalse(isHarborSessionKey("main"))
    }

    @Test
    fun `a named agent target scopes the session key`() {
        assertEquals(
            "agent:harbor-voice:voice-clasp:abc:harbor:ws-1",
            agentScopedSessionKey("openclaw/harbor-voice", "voice-clasp:abc:harbor:ws-1"),
        )
        assertEquals("agent:main:voice-clasp:abc", agentScopedSessionKey("openclaw/main", "voice-clasp:abc"))
    }

    @Test
    fun `a single-agent Gateway keeps its unscoped keys`() {
        assertEquals("voice-clasp:abc", agentScopedSessionKey("openclaw/default", "voice-clasp:abc"))
        assertEquals("voice-clasp:abc", agentScopedSessionKey("openclaw", "voice-clasp:abc"))
        assertEquals("voice-clasp:abc", agentScopedSessionKey("", "voice-clasp:abc"))
        assertNull(openClawAgentId("openclaw/default"))
        assertEquals("main", openClawAgentId("openclaw/main"))
    }

    @Test
    fun `chat defaults to the targeted agent's main session`() {
        assertEquals("agent:main:main", mainSessionKey("openclaw/default"))
        assertEquals("agent:main:main", mainSessionKey(""))
        assertEquals("agent:harbor-voice:main", mainSessionKey("openclaw/harbor-voice"))
        // Already scoped, so a fleet-wide target cannot re-prefix it.
        assertEquals("agent:main:main", agentScopedSessionKey("openclaw/harbor-voice", mainSessionKey("openclaw/main")))
    }

    @Test
    fun `a generic target takes the agent its session key names`() {
        // A multi-agent Gateway rejects openclaw/default even on agent:main:main.
        assertEquals("openclaw/main", chatTargetFor("openclaw/default", "agent:main:main"))
        assertEquals("openclaw/main", chatTargetFor("openclaw", "agent:main:main"))
        assertEquals("openclaw/main", chatTargetFor("", "agent:main:voice-clasp:abc"))
        assertEquals("openclaw/harbor-voice", chatTargetFor("openclaw/harbor-voice", "agent:main:main"))
        assertEquals("openclaw/default", chatTargetFor("openclaw/default", "voice-clasp:abc"))
    }

    @Test
    fun `the multiple agents rejection says to pick an agent without echoing the body`() {
        val body = """{"error":{"message":"Multiple agents are configured, but this operation has no explicit owner. TOPSECRET"}}"""

        val message = OpenClawChatRequestBuilder.safeHttpError(400, body)

        assertTrue(message.contains("openclaw/main"))
        assertFalse(message.contains("TOPSECRET"))
        assertEquals(
            "OpenClaw error 400: Gateway への要求に失敗しました。",
            OpenClawChatRequestBuilder.safeHttpError(400, "{}"),
        )
    }

    @Test
    fun `an already scoped key is left as the user chose it`() {
        assertEquals(
            "agent:main:existing",
            agentScopedSessionKey("openclaw/harbor-voice", "agent:main:existing"),
        )
    }

    @Test
    fun `the session picker drops every interpretation session`() {
        val body = """
            {"ok":true,"result":{"details":{"sessions":[
              {"key":"main","updatedAt":3},
              {"key":"voice-clasp:abc","updatedAt":2},
              {"key":"voice-clasp:abc:harbor","updatedAt":1},
              {"key":"voice-clasp:abc:harbor:ws-1","updatedAt":1}
            ]}}}
        """.trimIndent()

        val sessions = OpenClawHistoryParser.parseSessions(body)

        assertEquals(listOf("main", "voice-clasp:abc"), sessions.map { it.key })
    }
}
