package com.g150446.voiceharness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptConversationTest {
    @Test
    fun toolDefinitionUsesExpectedName() {
        val function = ChatGptConversationTool.toolDefinitionJson().getJSONObject("function")
        assertEquals("open_chatgpt_conversation", function.getString("name"))
        assertEquals(CHATGPT_CONVERSATION_TOOL_NAME, function.getString("name"))
    }

    @Test
    fun buttonLabelMatching() {
        assertTrue(isChatGptConversationButtonLabel("音声モードを開始"))
        assertTrue(isChatGptConversationButtonLabel("Start Voice Mode"))
        assertFalse(isChatGptConversationButtonLabel("送信"))
        assertFalse(isChatGptConversationButtonLabel(null))
        assertFalse(isChatGptConversationButtonLabel("  "))
    }

    @Test
    fun packageMatching() {
        assertTrue(isChatGptPackage("com.openai.chatgpt"))
        assertTrue(isChatGptPackage("com.openai.chatgpt/.MainActivity"))
        assertFalse(isChatGptPackage("com.openai.chatgptx"))
        assertFalse(isChatGptPackage(null))
    }

    @Test
    fun toolsIncludedOnlyWhenEnabled() {
        fun names(enabled: Boolean) = GroqChatRequestBuilder.buildToolsArray(
            includeHarbor = false,
            includeChatGpt = enabled,
        ).let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it).getJSONObject("function").getString("name") }
        }
        assertTrue(CHATGPT_CONVERSATION_TOOL_NAME in names(true))
        assertFalse(CHATGPT_CONVERSATION_TOOL_NAME in names(false))
    }

    @Test
    fun promptExplainsAccessibilityWhenItIsOff() {
        fun prompt(mode: ChatGptToolMode) = GroqChatRequestBuilder.buildSystemPromptForOpenRouter(
            languageCode = "ja",
            currentTimeMillis = 0L,
            screenContext = null,
            chatGptMode = mode,
        )
        assertTrue(prompt(ChatGptToolMode.NEEDS_ACCESSIBILITY).contains("ユーザー補助"))
        assertFalse(prompt(ChatGptToolMode.NEEDS_ACCESSIBILITY).contains(CHATGPT_CONVERSATION_TOOL_NAME))
        assertTrue(prompt(ChatGptToolMode.ENABLED).contains(CHATGPT_CONVERSATION_TOOL_NAME))
        assertFalse(prompt(ChatGptToolMode.OFF).contains("ユーザー補助"))
    }

    @Test
    fun requestBodyOffersToolOnlyWhenEnabled() {
        fun body(mode: ChatGptToolMode) = GroqChatRequestBuilder.buildRequestBodyWithFunctionCalling(
            conversationHistory = emptyList(),
            languageCode = "ja",
            chatGptToolMode = mode,
        )
        assertTrue(body(ChatGptToolMode.ENABLED).contains("\"name\":\"$CHATGPT_CONVERSATION_TOOL_NAME\""))
        assertFalse(body(ChatGptToolMode.NEEDS_ACCESSIBILITY).contains("\"name\":\"$CHATGPT_CONVERSATION_TOOL_NAME\""))
    }
}
