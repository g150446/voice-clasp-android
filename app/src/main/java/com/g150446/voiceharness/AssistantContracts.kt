package com.g150446.voiceharness

/** Origin of a request entering the shared assistant runtime. */
enum class QueryOrigin {
    HARNESS_NODE_VOICE,
    DIGITAL_ASSISTANT_VOICE,
    DIGITAL_ASSISTANT_TEXT,
}

/** Transient screen snapshot for a single ChatRequest. Never stored in conversation history. */
data class ScreenContext(
    val assistText: String? = null,
    val sourcePackage: String? = null,
    val sourceUri: String? = null,
    val jpegBytes: ByteArray? = null,
    val capturedAt: Long = System.currentTimeMillis(),
) {
    val hasText: Boolean get() = !assistText.isNullOrBlank()
    val hasImage: Boolean get() = jpegBytes != null && jpegBytes.isNotEmpty()
    val isEmpty: Boolean get() = !hasText && !hasImage

    fun withoutImage(): ScreenContext = copy(jpegBytes = null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScreenContext) return false
        return assistText == other.assistText &&
            sourcePackage == other.sourcePackage &&
            sourceUri == other.sourceUri &&
            capturedAt == other.capturedAt &&
            jpegBytes.contentEquals(other.jpegBytes)
    }

    override fun hashCode(): Int {
        var result = assistText?.hashCode() ?: 0
        result = 31 * result + (sourcePackage?.hashCode() ?: 0)
        result = 31 * result + (sourceUri?.hashCode() ?: 0)
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + (jpegBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Whether the LLM may press buttons in the ChatGPT app, which needs the accessibility service. */
enum class ChatGptToolMode {
    OFF,
    ENABLED,
    /** Like ENABLED, and the app may also switch accessibility off: expose disable_accessibility_service. */
    ENABLED_CAN_DISABLE,
    /** Accessibility is off and cannot be switched on by the app: the LLM explains the manual setup. */
    NEEDS_ACCESSIBILITY,
    /** Accessibility is off but the app may switch it on: expose enable_accessibility_service. */
    CAN_ENABLE_ACCESSIBILITY,
}

data class ChatRequest(
    val conversationHistory: List<ConversationTurn>,
    val languageCode: String? = null,
    val screenContext: ScreenContext? = null,
    /** When true, expose harbor_command alongside set_reminder (cloud LLM only). */
    val harborToolEnabled: Boolean = false,
    /**
     * When true, force the model to call harbor_command (Harbor confirm interpretation).
     * Implies [harborToolEnabled].
     */
    val forceHarborCommand: Boolean = false,
    /** Selected Terminal Harbor workspace scrollback / agent metadata for interpretation. */
    val harborContext: HarborInterpretContext? = null,
    /** ENABLED exposes open_chatgpt_conversation; NEEDS_ACCESSIBILITY makes the LLM explain the setup. */
    val chatGptToolMode: ChatGptToolMode = ChatGptToolMode.OFF,
    /** Offer operate_phone (the screen agent). Pilot only, behind the home-screen switch. */
    val operatePhoneToolEnabled: Boolean = false,
    /** One step of the screen agent's observe-act loop: agent prompt and tools only. */
    val screenAgent: Boolean = false,
)

data class AssistantRequest(
    val text: String,
    val origin: QueryOrigin,
    val requestId: String? = null,
    val conversationId: String? = null,
    val speakResponse: Boolean = true,
    val screenContext: ScreenContext? = null,
    val languageCode: String? = null,
    val harborToolEnabled: Boolean = false,
    val harborContext: HarborInterpretContext? = null,
    val chatGptToolMode: ChatGptToolMode = ChatGptToolMode.OFF,
    val operatePhoneToolEnabled: Boolean = false,
)

data class AssistantResult(
    val text: String,
    val conversationId: String,
    val requestId: String? = null,
    val toolCalls: List<ChatToolCall> = emptyList(),
    val latencyMs: Long = 0L,
)

/** Stable boundary shared by BLE and Android's digital-assistant entry points. */
interface AssistantGateway {
    suspend fun submit(request: AssistantRequest): Result<AssistantResult>
    fun resetConversation(conversationId: String)

    /** True while a multi-turn session is still open, so a bare reply makes sense. */
    fun isConversationActive(conversationId: String): Boolean
}
