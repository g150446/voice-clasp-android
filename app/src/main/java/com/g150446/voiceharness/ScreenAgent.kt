package com.g150446.voiceharness

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** What the agent sees of the foreground window. */
internal data class ScreenObservation(
    val packageName: String?,
    val text: String,
    val signature: Int,
    val readable: Boolean,
)

internal data class ActionResult(val ok: Boolean, val message: String)

/** The phone side of the loop; the accessibility implementation is [ScreenOperator]. */
internal interface ScreenOperatorPort {
    suspend fun observe(): ScreenObservation
    suspend fun perform(action: ScreenAction): ActionResult

    /** Label of element #[index] in the latest observation, to judge a tap before making it. */
    fun labelOf(index: Int): String?

    /** Waits until the screen stops changing (bounded). */
    suspend fun settle()
}

internal enum class ScreenAgentOutcome { FINISHED, DENIED, CANCELLED, STEP_LIMIT, TIME_LIMIT, NO_PROGRESS, ERROR }

internal data class ScreenAgentResult(
    val outcome: ScreenAgentOutcome,
    val message: String,
    val steps: Int,
)

/**
 * Observe → ask the LLM for one action → act, until it calls finish. The LLM only ever sees the
 * task, a short log of steps and the current screen; tool results travel as plain text so any
 * tool-capable model works. The limits, the denied-app list and the confirm-before-risky-tap rule
 * are enforced here, not left to the prompt.
 */
internal class ScreenAgent(
    private val operator: ScreenOperatorPort,
    private val chat: suspend (ChatRequest) -> Result<ChatResult>,
    private val confirm: suspend (String) -> Boolean,
    private val onProgress: (String) -> Unit = {},
    private val isStopRequested: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
    private val maxSteps: Int = MAX_STEPS,
    private val maxMillis: Long = MAX_MILLIS,
) {
    suspend fun run(task: String, languageCode: String?): ScreenAgentResult {
        val startedAt = now()
        val log = mutableListOf<String>()
        var lastSignature: Int? = null
        var repeats = 0
        var step = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (isStopRequested()) return result(ScreenAgentOutcome.CANCELLED, "中止しました", step)
            if (step >= maxSteps) return result(ScreenAgentOutcome.STEP_LIMIT, "手順が多すぎるため中止しました", step)
            if (now() - startedAt > maxMillis) return result(ScreenAgentOutcome.TIME_LIMIT, "時間がかかりすぎるため中止しました", step)

            val observation = operator.observe()
            if (ScreenGuard.isDeniedPackage(observation.packageName)) {
                return result(ScreenAgentOutcome.DENIED, "このアプリは操作できません", step)
            }
            if (observation.signature == lastSignature) repeats++ else repeats = 0
            lastSignature = observation.signature
            if (repeats >= NO_PROGRESS_REPEATS) {
                return result(ScreenAgentOutcome.NO_PROGRESS, "画面が変わらないため中止しました", step)
            }

            val request = ChatRequest(
                conversationHistory = listOf(ConversationTurn("user", userTurn(task, log, observation))),
                languageCode = languageCode,
                screenAgent = true,
            )
            val reply = chat(request).getOrElse {
                return result(ScreenAgentOutcome.ERROR, "AIの応答に失敗しました", step)
            }
            val call = reply.toolCalls.firstOrNull()
            val action = if (call != null) {
                ScreenAgentTools.parse(call.name, call.argumentsJson)
            } else if (reply.text.isNotBlank()) {
                ScreenAction.Finish(reply.text.trim())
            } else {
                return result(ScreenAgentOutcome.ERROR, "AIが操作を返しませんでした", step)
            }

            step++
            val described = describe(action)
            Log.i(TAG, "step $step: $described pkg=${observation.packageName}")
            if (action is ScreenAction.Finish) {
                return result(ScreenAgentOutcome.FINISHED, action.message, step)
            }
            onProgress(described)

            if (action is ScreenAction.Invalid) {
                log += "$step. ${described} → ${action.reason}"
                continue
            }
            confirmationQuestion(action)?.let { question ->
                if (!confirm(question)) {
                    return result(ScreenAgentOutcome.CANCELLED, "確認が取れなかったため中止しました", step)
                }
            }
            val outcome = operator.perform(action)
            log += "$step. $described → ${if (outcome.ok) "ok" else "failed"}: ${outcome.message}"
            operator.settle()
        }
    }

    /** The question for a tap that cannot be undone, or null for anything that may run untapped. */
    private fun confirmationQuestion(action: ScreenAction): String? {
        val index = when (action) {
            is ScreenAction.Tap -> action.index
            is ScreenAction.LongPress -> action.index
            is ScreenAction.TypeText -> if (action.submit) action.index else null
            else -> null
        } ?: return null
        val label = operator.labelOf(index)
        return if (ScreenGuard.isRiskyLabel(label)) "「$label」を押してよいですか？" else null
    }

    private fun userTurn(task: String, log: List<String>, observation: ScreenObservation): String = buildString {
        append("Task: ").append(task).append("\n\n")
        append("Steps so far:\n")
        if (log.isEmpty()) append("(none)\n") else log.takeLast(LOG_WINDOW).forEach { append(it).append('\n') }
        append("\nCurrent screen (app: ").append(observation.packageName ?: "unknown").append("):\n")
        append("<screen>\n")
        append(if (observation.readable) observation.text else "(the screen cannot be read)")
        append("\n</screen>\n")
        append("Call exactly one function.")
    }

    private fun describe(action: ScreenAction): String = when (action) {
        is ScreenAction.Tap -> "tap #${action.index}"
        is ScreenAction.TapXy -> "tap (${action.x},${action.y})"
        is ScreenAction.LongPress -> "long_press #${action.index}"
        is ScreenAction.Scroll -> "scroll ${action.direction.name.lowercase()}" + (action.index?.let { " #$it" } ?: "")
        is ScreenAction.TypeText -> "type_text \"${action.text.take(40)}\"" + if (action.submit) " +enter" else ""
        ScreenAction.Back -> "back"
        ScreenAction.Home -> "home"
        is ScreenAction.OpenApp -> "open_app ${action.name}"
        is ScreenAction.Wait -> "wait ${action.ms}ms"
        is ScreenAction.Finish -> "finish"
        is ScreenAction.Invalid -> "invalid (${action.reason})"
    }

    private fun result(outcome: ScreenAgentOutcome, message: String, steps: Int): ScreenAgentResult {
        Log.i(TAG, "result: $outcome steps=$steps $message")
        return ScreenAgentResult(outcome, message, steps)
    }

    companion object {
        private const val TAG = "ScreenAgent"
        const val MAX_STEPS = 15
        const val MAX_MILLIS = 90_000L
        const val NO_PROGRESS_REPEATS = 3
        private const val LOG_WINDOW = 8
    }
}
