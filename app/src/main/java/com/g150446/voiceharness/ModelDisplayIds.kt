package com.g150446.voiceharness

import android.content.Context

/** Resolves human-readable ASR/LLM model IDs for settings UI and history. */
internal object ModelDisplayIds {
    fun sttModelId(context: Context, backend: SttBackendId = ModelManager.currentSttBackend(context)): String =
        when (backend) {
            SttBackendId.GROQ -> GroqVoiceAiBackend.WHISPER_MODEL
            SttBackendId.OPENROUTER -> OpenRouterPrefs.getSttModelId(context)
            SttBackendId.GEMMA -> ModelManager.status.value.gemma.fileName ?: "gemma"
            SttBackendId.QWEN -> ModelManager.status.value.qwenAsrDecoder.fileName ?: "qwen-asr"
        }

    fun llmModelId(context: Context, backend: LlmBackendId = ModelManager.currentLlmBackend(context)): String =
        when (backend) {
            LlmBackendId.GROQ -> GroqChatRequestBuilder.CHAT_MODEL
            LlmBackendId.OPENROUTER ->
                OpenRouterPrefs.getModelId(context).ifBlank { "未選択" }
            LlmBackendId.GEMMA -> ModelManager.status.value.gemma.fileName ?: "gemma"
            LlmBackendId.QWEN -> ModelManager.status.value.lfmChat.fileName ?: "lfm-chat"
        }

    /** LLM backend name for history: OpenClaw when that route is active, else the selected LLM. */
    fun currentLlmBackendName(context: Context): String =
        if (isOpenClawRoute()) OPENCLAW_BACKEND_NAME else ModelManager.currentLlmBackend(context).name

    fun currentLlmModelId(context: Context): String =
        if (isOpenClawRoute()) OpenClawChatRequestBuilder.MODEL else llmModelId(context)

    private const val OPENCLAW_BACKEND_NAME = "OPENCLAW"

    /** Short name of the ASR actually in use, for the home-screen status line. */
    fun sttShortLabel(context: Context): String = when (val backend = ModelManager.currentSttBackend(context)) {
        SttBackendId.OPENROUTER -> "OpenRouter ${OpenRouterPrefs.getSttModelId(context).substringAfterLast('/')}"
        SttBackendId.GROQ -> "Groq ${GroqVoiceAiBackend.WHISPER_MODEL}"
        else -> backend.displayName
    }

    /** Short name of the LLM actually in use (OpenClaw while that route is on). */
    fun llmShortLabel(context: Context): String {
        if (isOpenClawRoute()) return "OpenClaw"
        return when (val backend = ModelManager.currentLlmBackend(context)) {
            LlmBackendId.OPENROUTER -> "OpenRouter ${OpenRouterPrefs.getModelId(context).substringAfterLast('/').ifBlank { "未選択" }}"
            LlmBackendId.GROQ -> "Groq ${GroqChatRequestBuilder.CHAT_MODEL.substringAfterLast('/')}"
            else -> backend.displayName
        }
    }

    fun sttLabel(context: Context): String {
        val backend = ModelManager.currentSttBackend(context)
        return "${backend.displayName} / ${sttModelId(context, backend)}"
    }

    fun llmLabel(context: Context): String {
        val backend = ModelManager.currentLlmBackend(context)
        return "${backend.displayName} / ${llmModelId(context, backend)}"
    }
}
