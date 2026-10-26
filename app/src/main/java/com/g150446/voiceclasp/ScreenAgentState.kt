package com.g150446.voiceclasp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * What the screen agent is doing right now, for the home screen, plus the human confirmation a
 * risky tap waits for. A tap on the ring/glass and the home-screen buttons both answer it.
 */
internal object ScreenAgentState {
    private const val CONFIRM_TIMEOUT_MS = 60_000L

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _pendingConfirm = MutableStateFlow<String?>(null)
    val pendingConfirm: StateFlow<String?> = _pendingConfirm.asStateFlow()

    private val answer = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val stopRequested = AtomicBoolean(false)

    fun setStatus(text: String?) {
        _status.value = text
    }

    fun beginRun() {
        stopRequested.set(false)
    }

    fun requestStop() {
        stopRequested.set(true)
        respond(false)
    }

    fun isStopRequested(): Boolean = stopRequested.get()

    /** Waits for a tap. Anything but an explicit "yes" (including silence) is a no. */
    suspend fun ask(question: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        answer.set(deferred)
        _pendingConfirm.value = question
        return try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            answer.compareAndSet(deferred, null)
            _pendingConfirm.value = null
        }
    }

    /** @return true when a question was waiting. */
    fun respond(approved: Boolean): Boolean {
        val waiting = answer.getAndSet(null) ?: return false
        waiting.complete(approved)
        return true
    }

    fun isConfirmPending(): Boolean = answer.get() != null
}
