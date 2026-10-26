package com.g150446.voiceclasp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * adb shell am broadcast -a com.g150446.voiceclasp.debug.ASK \
 *   -n com.g150446.voiceclasp/.DebugAssistantReceiver --es text "..." [--es mode AI|HARBOR] [--es conv harness-node] [--ez speak true] [--ez screen_agent true|false] [--es stt GROQ|OPENROUTER] [--es transcript "recognized text"] [--es stt_model ID] [--es transcribe_file PATH]
 */
class DebugAssistantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("screen_agent")) {
            ScreenAgentPreferences(context).setEnabled(intent.getBooleanExtra("screen_agent", false))
        }
        intent.getStringExtra("stt")?.let { name ->
            val backend = runCatching { SttBackendId.valueOf(name.uppercase()) }.getOrNull()
            if (backend != null) {
                BleConnectionService.switchSttBackend(context.applicationContext, backend)
                Log.i(TAG, "stt=$backend")
            }
        }
        intent.getStringExtra("stt_model")?.let { OpenRouterPrefs.setSttModelId(context, it) }
        intent.getStringExtra("transcribe_file")?.let { path ->
            val pending = goAsync()
            Thread {
                try {
                    val result = kotlinx.coroutines.runBlocking {
                        OnDeviceAiFacade(context.applicationContext).transcribe(java.io.File(path), emptyList())
                    }
                    Log.i(TAG, "transcribe(" + path.substringAfterLast('/') + ") -> " + result)
                } finally {
                    pending.finish()
                }
            }.start()
        }
        intent.getStringExtra("transcript")?.let { BleConnectionService.submitTranscript(it) }
        val text = intent.getStringExtra("text").orEmpty()
        if (text.isBlank()) return
        intent.getStringExtra("mode")?.let { name ->
            val mode = runCatching { InteractionMode.valueOf(name.uppercase()) }.getOrNull()
            if (mode != null) {
                val applied = BleConnectionService.setInteractionMode(context.applicationContext, mode)
                Log.i(TAG, "mode=$mode applied=$applied")
            }
        }
        BleConnectionService.submitAssistantRequest(
            context = context.applicationContext,
            text = text,
            conversationId = intent.getStringExtra("conv") ?: "debug-${System.currentTimeMillis()}",
            requestId = null,
            speakResponse = intent.getBooleanExtra("speak", false),
            screenToken = null,
            origin = QueryOrigin.HARNESS_NODE_VOICE,
        )
    }

    private companion object {
        const val TAG = "DebugAssistant"
    }
}
