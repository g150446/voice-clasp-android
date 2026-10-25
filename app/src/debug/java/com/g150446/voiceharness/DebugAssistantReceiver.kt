package com.g150446.voiceharness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * adb shell am broadcast -a com.g150446.voiceharness.debug.ASK \
 *   -n com.g150446.voiceharness/.DebugAssistantReceiver --es text "..." [--es mode AI|HARBOR] [--es conv harness-node] [--ez speak true]
 */
class DebugAssistantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
