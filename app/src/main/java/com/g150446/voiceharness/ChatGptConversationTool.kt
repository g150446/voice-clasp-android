package com.g150446.voiceharness

import org.json.JSONArray
import org.json.JSONObject

internal const val CHATGPT_CONVERSATION_TOOL_NAME = "open_chatgpt_conversation"

/** Tool definition for starting conversation (voice) mode in the Android ChatGPT app. */
internal object ChatGptConversationTool {
    private const val SYSTEM_APPENDIX =
        "The user can start conversation (voice) mode in the ChatGPT app on this phone. " +
            "When they ask to start ChatGPT's conversation mode / voice mode / 会話モード / 音声モード, " +
            "or to talk with ChatGPT, do not refuse and do not answer yourself. " +
            "Always call the open_chatgpt_conversation function. It runs immediately without confirmation."

    private const val NEEDS_ACCESSIBILITY_APPENDIX =
        "The user can ask you to operate buttons in other apps on this phone, for example starting " +
            "ChatGPT's conversation (voice) mode. That needs the Voice Harness accessibility " +
            "permission (ユーザー補助), which is currently turned OFF, so you cannot do it. " +
            "When they ask for such a button operation, do not call any tool and do not pretend it " +
            "was done. Reply briefly, in the user's language, that they must first turn on " +
            "Voice Harness under Settings > Accessibility (設定 > ユーザー補助 > Voice Harness) " +
            "and then ask again."

    /** System-prompt addition for [mode]; empty when the capability is not relevant. */
    fun systemAppendix(mode: ChatGptToolMode): String = when (mode) {
        ChatGptToolMode.OFF -> ""
        ChatGptToolMode.ENABLED -> " $SYSTEM_APPENDIX"
        ChatGptToolMode.NEEDS_ACCESSIBILITY -> " $NEEDS_ACCESSIBILITY_APPENDIX"
    }

    fun toolDefinitionJson(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", CHATGPT_CONVERSATION_TOOL_NAME)
            put(
                "description",
                "Open the ChatGPT app on the phone and press its conversation (voice) mode start " +
                    "button. Use when the user wants to start ChatGPT's conversation mode.",
            )
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
                put("required", JSONArray())
            })
        })
    }
}
