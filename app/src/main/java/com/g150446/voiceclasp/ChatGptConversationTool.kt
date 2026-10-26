package com.g150446.voiceclasp

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
            "ChatGPT's conversation (voice) mode. That needs the Voice Clasp accessibility " +
            "permission (ユーザー補助), which is currently turned OFF, so you cannot do it. " +
            "When they ask for such a button operation, do not call any tool and do not pretend it " +
            "was done. Reply briefly, in the user's language, that they must first turn on " +
            "Voice Clasp under Settings > Accessibility (設定 > ユーザー補助 > Voice Clasp) " +
            "and then ask again. If they ask to turn it off, just say it is already off."

    /**
     * Terminal Harbor tells the model to turn every control request into harbor_command, and a
     * Harbor instruction is confirmed by tap while the phone-side tools run at once. So while
     * Harbor is active an unqualified request stays with Harbor and only an explicit phone
     * target (スマホ / このアプリ / Voice Clasp / ChatGPTアプリ ...) reaches the phone tools.
     */
    private const val HARBOR_ACTIVE_SCOPE =
        "Terminal Harbor is active, so a request without a stated target, such as just " +
            "「ユーザー補助を有効化して」, is an instruction for the terminal: use harbor_command. " +
            "Use the phone tools described here only when the user clearly names the phone side, " +
            "for example スマホ, 端末, このアプリ, Voice Clasp, ボイスクラスプ or the ChatGPT app. "

    private const val ALREADY_ENABLED_APPENDIX =
        "The Voice Clasp accessibility service (ユーザー補助) is already ON. If the user asks " +
            "to turn it on, just tell them briefly that it is already enabled."

    /** System-prompt addition for [mode]; empty when the capability is not relevant. */
    fun systemAppendix(mode: ChatGptToolMode, harborActive: Boolean = false): String {
        val scope = if (harborActive) HARBOR_ACTIVE_SCOPE else ""
        return when (mode) {
            ChatGptToolMode.OFF -> ""
            ChatGptToolMode.ENABLED -> " $scope$SYSTEM_APPENDIX $ALREADY_ENABLED_APPENDIX"
            ChatGptToolMode.ENABLED_CAN_DISABLE ->
                " $scope$SYSTEM_APPENDIX $ALREADY_ENABLED_APPENDIX ${AccessibilitySetupTool.DISABLE_APPENDIX}"
            ChatGptToolMode.NEEDS_ACCESSIBILITY -> " $scope$NEEDS_ACCESSIBILITY_APPENDIX"
            ChatGptToolMode.CAN_ENABLE_ACCESSIBILITY ->
                " $scope${AccessibilitySetupTool.SYSTEM_APPENDIX}"
        }
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
