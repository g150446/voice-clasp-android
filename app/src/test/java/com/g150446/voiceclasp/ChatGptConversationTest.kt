package com.g150446.voiceclasp

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

    @Test
    fun accessibilityServiceIsAppendedWithoutDuplicates() {
        val component = "com.g150446.voiceclasp/com.g150446.voiceclasp.RingAccessibilityService"
        assertEquals(component, addAccessibilityServiceToSetting(null, component))
        assertEquals(component, addAccessibilityServiceToSetting("  ", component))
        assertEquals("a/b:$component", addAccessibilityServiceToSetting("a/b", component))
        assertEquals(component, addAccessibilityServiceToSetting(component, component))
    }

    @Test
    fun accessibilitySetupToolIsOfferedOnlyWhenItCanBeEnabled() {
        fun toolNames(mode: ChatGptToolMode): List<String> {
            val body = GroqChatRequestBuilder.buildRequestBodyWithFunctionCalling(
                conversationHistory = emptyList(),
                languageCode = "ja",
                chatGptToolMode = mode,
            )
            val tools = org.json.JSONObject(body).getJSONArray("tools")
            return (0 until tools.length()).map {
                tools.getJSONObject(it).getJSONObject("function").getString("name")
            }
        }
        val can = toolNames(ChatGptToolMode.CAN_ENABLE_ACCESSIBILITY)
        assertTrue(ENABLE_ACCESSIBILITY_TOOL_NAME in can)
        assertFalse(CHATGPT_CONVERSATION_TOOL_NAME in can)
        for (mode in listOf(ChatGptToolMode.OFF, ChatGptToolMode.NEEDS_ACCESSIBILITY)) {
            assertFalse(ENABLE_ACCESSIBILITY_TOOL_NAME in toolNames(mode))
        }
        val enabled = toolNames(ChatGptToolMode.ENABLED)
        assertTrue(CHATGPT_CONVERSATION_TOOL_NAME in enabled)
        assertFalse(ENABLE_ACCESSIBILITY_TOOL_NAME in enabled)
    }

    @Test
    fun harborKeepsUnqualifiedPhoneRequestsButAllowsExplicitOnes() {
        fun prompt(harbor: Boolean) = GroqChatRequestBuilder.buildSystemPromptForOpenRouter(
            languageCode = "ja",
            currentTimeMillis = 0L,
            screenContext = null,
            includeHarbor = harbor,
            chatGptMode = ChatGptToolMode.CAN_ENABLE_ACCESSIBILITY,
        )
        val withHarbor = prompt(true)
        assertTrue(withHarbor.contains("Terminal Harbor is active, so a request without a stated target"))
        assertTrue(withHarbor.indexOf("harbor_command") < withHarbor.indexOf(ENABLE_ACCESSIBILITY_TOOL_NAME))
        assertFalse(prompt(false).contains("Terminal Harbor is active"))
        assertTrue(prompt(false).contains(ENABLE_ACCESSIBILITY_TOOL_NAME))
    }

    @Test
    fun accessibilityServiceIsRemovedFromTheSetting() {
        val component = "com.g150446.voiceclasp/com.g150446.voiceclasp.RingAccessibilityService"
        assertEquals("", removeAccessibilityServiceFromSetting(null, component))
        assertEquals("", removeAccessibilityServiceFromSetting(component, component))
        assertEquals("a/b", removeAccessibilityServiceFromSetting("a/b:$component", component))
        assertEquals("a/b:c/d", removeAccessibilityServiceFromSetting("a/b:$component:c/d", component))
        assertEquals("a/b", removeAccessibilityServiceFromSetting("a/b", component))
    }

    @Test
    fun disableToolIsOfferedOnlyWhenEnabledAndPermitted() {
        fun names(mode: ChatGptToolMode): List<String> {
            val body = GroqChatRequestBuilder.buildRequestBodyWithFunctionCalling(
                conversationHistory = emptyList(),
                languageCode = "ja",
                chatGptToolMode = mode,
            )
            val tools = org.json.JSONObject(body).getJSONArray("tools")
            return (0 until tools.length()).map {
                tools.getJSONObject(it).getJSONObject("function").getString("name")
            }
        }
        val can = names(ChatGptToolMode.ENABLED_CAN_DISABLE)
        assertTrue(DISABLE_ACCESSIBILITY_TOOL_NAME in can)
        assertTrue(CHATGPT_CONVERSATION_TOOL_NAME in can)
        for (mode in listOf(
            ChatGptToolMode.ENABLED,
            ChatGptToolMode.OFF,
            ChatGptToolMode.NEEDS_ACCESSIBILITY,
            ChatGptToolMode.CAN_ENABLE_ACCESSIBILITY,
        )) {
            assertFalse(DISABLE_ACCESSIBILITY_TOOL_NAME in names(mode))
        }
    }
}
