package com.g150446.voiceharness

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal enum class ChatGptConversationResult {
    DISPATCHED,
    UNAVAILABLE,
    APP_NOT_INSTALLED,
    BUTTON_NOT_FOUND,
    FAILED,
}

/**
 * Label fragments (lower-cased) that identify ChatGPT's conversation / voice mode start button.
 * Matched against contentDescription, text and viewIdResourceName. Exact labels vary by app
 * version and language, so keep this list broad and confirm with `uiautomator dump`.
 */
internal val CHATGPT_CONVERSATION_BUTTON_LABELS = listOf(
    "音声モード",
    "音声会話",
    "会話モード",
    "音声を開始",
    "start voice",
    "voice mode",
    "start conversation",
    "conversation mode",
    "use voice mode",
)

internal fun isChatGptConversationButtonLabel(label: String?): Boolean {
    val value = label?.trim()?.lowercase().orEmpty()
    if (value.isEmpty()) return false
    return CHATGPT_CONVERSATION_BUTTON_LABELS.any { value.contains(it) }
}

/** Bare package ids and Assist-style "package/activity" titles. */
internal fun isChatGptPackage(packageName: String?): Boolean {
    val value = packageName?.trim().orEmpty()
    if (value.isEmpty()) return false
    val pkg = ChatGptConversationController.CHATGPT_PACKAGE
    return value == pkg || value.startsWith("$pkg/") || value.startsWith("$pkg.")
}

/** Process-local bridge from VoiceProcessor to the enabled AccessibilityService. */
internal object ChatGptConversationController {
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"

    private const val FOREGROUND_TIMEOUT_MS = 6_000L
    private const val POLL_INTERVAL_MS = 300L
    private const val BUTTON_RETRY_COUNT = 3
    private const val BUTTON_RETRY_DELAY_MS = 800L

    @Volatile private var service: AccessibilityService? = null

    fun attach(accessibilityService: AccessibilityService) {
        service = accessibilityService
    }

    fun detach(accessibilityService: AccessibilityService) {
        if (service === accessibilityService) service = null
    }

    fun isAvailable(): Boolean = service != null

    suspend fun start(context: Context): ChatGptConversationResult =
        startInternal(context).also { android.util.Log.i("ChatGptConversation", "start: $it") }

    private suspend fun startInternal(context: Context): ChatGptConversationResult {
        val current = service ?: return ChatGptConversationResult.UNAVAILABLE
        val launch = context.packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
            ?: return ChatGptConversationResult.APP_NOT_INSTALLED
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(launch) }.isFailure) {
            return ChatGptConversationResult.FAILED
        }

        if (!waitForForeground(current)) return ChatGptConversationResult.BUTTON_NOT_FOUND

        repeat(BUTTON_RETRY_COUNT) { attempt ->
            val clicked = withContext(Dispatchers.Main.immediate) { clickConversationButton(current) }
            if (clicked) return ChatGptConversationResult.DISPATCHED
            if (attempt < BUTTON_RETRY_COUNT - 1) delay(BUTTON_RETRY_DELAY_MS)
        }
        return ChatGptConversationResult.BUTTON_NOT_FOUND
    }

    private suspend fun waitForForeground(current: AccessibilityService): Boolean {
        var waited = 0L
        while (waited < FOREGROUND_TIMEOUT_MS) {
            val pkg = withContext(Dispatchers.Main.immediate) {
                current.rootInActiveWindow?.packageName?.toString()
            }
            if (isChatGptPackage(pkg)) return true
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return false
    }

    private fun clickConversationButton(current: AccessibilityService): Boolean {
        val root = current.rootInActiveWindow ?: return false
        if (!isChatGptPackage(root.packageName?.toString())) return false
        val target = findButton(root) ?: return false
        var node: AccessibilityNodeInfo? = target
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
        }
        return false
    }

    private fun findButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isChatGptConversationButtonLabel(node.contentDescription?.toString()) ||
            isChatGptConversationButtonLabel(node.text?.toString()) ||
            isChatGptConversationButtonLabel(node.viewIdResourceName)
        ) {
            return node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findButton(child)?.let { return it }
        }
        return null
    }
}
