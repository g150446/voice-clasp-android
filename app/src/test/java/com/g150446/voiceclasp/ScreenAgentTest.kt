package com.g150446.voiceclasp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAgentTest {
    private class FakeOperator(
        private val screens: List<ScreenObservation>,
        private val labels: Map<Int, String> = emptyMap(),
    ) : ScreenOperatorPort {
        var observed = 0
        val performed = mutableListOf<ScreenAction>()
        override suspend fun observe(): ScreenObservation = screens[minOf(observed++, screens.lastIndex)]
        override suspend fun perform(action: ScreenAction): ActionResult {
            performed += action
            return ActionResult(true, "ok")
        }
        override fun labelOf(index: Int): String? = labels[index]
        override suspend fun settle() = Unit
    }

    private fun screen(text: String, pkg: String? = "com.example.app", sig: Int = text.hashCode()) =
        ScreenObservation(pkg, text, sig, readable = true)

    private fun call(name: String, args: String = "{}") =
        Result.success(ChatResult(text = "", toolCalls = listOf(ChatToolCall(name, args))))

    private fun agent(
        operator: ScreenOperatorPort,
        replies: MutableList<Result<ChatResult>>,
        confirm: suspend (String) -> Boolean = { true },
        stop: () -> Boolean = { false },
        maxSteps: Int = 15,
        requests: MutableList<ChatRequest> = mutableListOf(),
    ) = ScreenAgent(
        operator = operator,
        chat = { request ->
            requests += request
            replies.removeAt(0)
        },
        confirm = confirm,
        isStopRequested = stop,
        maxSteps = maxSteps,
    )

    @Test
    fun finishesWhenTheModelCallsFinish() = runBlocking {
        val operator = FakeOperator(listOf(screen("#1 Button \"検索\"")))
        val requests = mutableListOf<ChatRequest>()
        val result = agent(
            operator,
            mutableListOf(call("tap", """{"node":1}"""), call("finish", """{"message":"検索しました"}""")),
            requests = requests,
        ).run("検索して", "ja")
        assertEquals(ScreenAgentOutcome.FINISHED, result.outcome)
        assertEquals("検索しました", result.message)
        assertEquals(listOf<ScreenAction>(ScreenAction.Tap(1)), operator.performed)
        assertTrue(requests.all { it.screenAgent })
        val second = requests[1].conversationHistory.single().content
        assertTrue(second.contains("1. tap #1 → ok"))
        assertTrue(second.contains("<screen>"))
    }

    @Test
    fun deniedAppIsNeverOperated() = runBlocking {
        val operator = FakeOperator(listOf(screen("x", pkg = "jp.co.examplebank.app")))
        val result = agent(operator, mutableListOf(call("tap", """{"node":1}"""))).run("t", null)
        assertEquals(ScreenAgentOutcome.DENIED, result.outcome)
        assertTrue(operator.performed.isEmpty())
    }

    @Test
    fun riskyButtonWaitsForConfirmAndStopsOnNo() = runBlocking {
        val operator = FakeOperator(listOf(screen("a")), labels = mapOf(3 to "送信"))
        val asked = mutableListOf<String>()
        val result = agent(
            operator,
            mutableListOf(call("tap", """{"node":3}""")),
            confirm = { asked += it; false },
        ).run("送って", null)
        assertEquals(ScreenAgentOutcome.CANCELLED, result.outcome)
        assertEquals(1, asked.size)
        assertTrue(asked.single().contains("送信"))
        assertTrue(operator.performed.isEmpty())
    }

    @Test
    fun riskyButtonRunsAfterApproval() = runBlocking {
        val operator = FakeOperator(listOf(screen("a"), screen("b")), labels = mapOf(3 to "Delete"))
        val result = agent(
            operator,
            mutableListOf(call("tap", """{"node":3}"""), call("finish", """{"message":"ok"}""")),
            confirm = { true },
        ).run("消して", null)
        assertEquals(ScreenAgentOutcome.FINISHED, result.outcome)
        assertEquals(listOf<ScreenAction>(ScreenAction.Tap(3)), operator.performed)
    }

    @Test
    fun ordinaryTapNeedsNoConfirmation() = runBlocking {
        val operator = FakeOperator(listOf(screen("a"), screen("b")), labels = mapOf(2 to "検索"))
        var asked = false
        agent(
            operator,
            mutableListOf(call("tap", """{"node":2}"""), call("finish")),
            confirm = { asked = true; true },
        ).run("t", null)
        assertFalse(asked)
    }

    @Test
    fun stopsAtTheStepLimit() = runBlocking {
        val screens = (1..10).map { screen("s$it") }
        val operator = FakeOperator(screens)
        val replies = MutableList(10) { call("scroll", """{"direction":"down"}""") }
        val result = agent(operator, replies, maxSteps = 3).run("t", null)
        assertEquals(ScreenAgentOutcome.STEP_LIMIT, result.outcome)
        assertEquals(3, operator.performed.size)
    }

    @Test
    fun stopsWhenTheScreenNeverChanges() = runBlocking {
        val operator = FakeOperator(listOf(screen("same")))
        val replies = MutableList(10) { call("scroll", """{"direction":"down"}""") }
        val result = agent(operator, replies).run("t", null)
        assertEquals(ScreenAgentOutcome.NO_PROGRESS, result.outcome)
    }

    @Test
    fun stopRequestEndsTheLoopBeforeAnyAction() = runBlocking {
        val operator = FakeOperator(listOf(screen("a")))
        val result = agent(operator, mutableListOf(call("tap", """{"node":1}""")), stop = { true }).run("t", null)
        assertEquals(ScreenAgentOutcome.CANCELLED, result.outcome)
        assertTrue(operator.performed.isEmpty())
    }

    @Test
    fun plainTextReplyEndsTheTask() = runBlocking {
        val operator = FakeOperator(listOf(screen("a")))
        val result = agent(
            operator,
            mutableListOf(Result.success(ChatResult(text = "できません"))),
        ).run("t", null)
        assertEquals(ScreenAgentOutcome.FINISHED, result.outcome)
        assertEquals("できません", result.message)
    }

    @Test
    fun invalidActionIsLoggedAndTheLoopContinues() = runBlocking {
        val operator = FakeOperator(listOf(screen("a"), screen("b")))
        val requests = mutableListOf<ChatRequest>()
        val result = agent(
            operator,
            mutableListOf(call("tap", "{}"), call("finish")),
            requests = requests,
        ).run("t", null)
        assertEquals(ScreenAgentOutcome.FINISHED, result.outcome)
        assertTrue(operator.performed.isEmpty())
        assertTrue(requests[1].conversationHistory.single().content.contains("node が必要です"))
    }

    @Test
    fun toolArgumentsAreParsed() {
        assertEquals(ScreenAction.Tap(12), ScreenAgentTools.parse("tap", """{"node":"#12"}"""))
        assertEquals(ScreenAction.TapXy(10, 20), ScreenAgentTools.parse("tap_xy", """{"x":10,"y":20}"""))
        assertEquals(
            ScreenAction.Scroll(ScrollDirection.DOWN, null),
            ScreenAgentTools.parse("scroll", """{"direction":"down"}"""),
        )
        assertEquals(
            ScreenAction.TypeText("こんにちは", 4, true),
            ScreenAgentTools.parse("type_text", """{"text":"こんにちは","node":4,"submit":true}"""),
        )
        assertEquals(ScreenAction.Wait(ScreenAgentTools.MAX_WAIT_MS), ScreenAgentTools.parse("wait", """{"ms":99999}"""))
        assertTrue(ScreenAgentTools.parse("scroll", """{"direction":"diagonal"}""") is ScreenAction.Invalid)
        assertTrue(ScreenAgentTools.parse("nope", "{}") is ScreenAction.Invalid)
        assertEquals("電卓で計算", ScreenAgentTools.parseTask("""{"task":"電卓で計算"}""", "x"))
        assertEquals("fallback", ScreenAgentTools.parseTask("{}", "fallback"))
    }

    @Test
    fun guardDeniesSensitiveAppsAndRiskyLabels() {
        assertTrue(ScreenGuard.isDeniedPackage("com.android.settings"))
        assertTrue(ScreenGuard.isDeniedPackage("com.g150446.voiceclasp"))
        assertTrue(ScreenGuard.isDeniedPackage("jp.co.sevenbank.appMysevenbank"))
        assertFalse(ScreenGuard.isDeniedPackage("com.android.chrome"))
        assertFalse(ScreenGuard.isDeniedPackage(null))
        assertTrue(ScreenGuard.isRiskyLabel("送信"))
        assertTrue(ScreenGuard.isRiskyLabel("Buy now"))
        assertFalse(ScreenGuard.isRiskyLabel("検索"))
        assertFalse(ScreenGuard.isRiskyLabel(null))
    }

    @Test
    fun screenTextHidesPasswordsAndTrims() {
        val nodes = listOf(
            ScreenNodeInfo(1, "EditText", "hunter2", 10, 10, editable = true, password = true),
            ScreenNodeInfo(2, "Button", "ログイン", 20, 20, clickable = true),
        )
        val text = ScreenTree.render(nodes)
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains(ScreenTree.PASSWORD_PLACEHOLDER))
        assertTrue(text.contains("#2 Button \"ログイン\" (20,20) clickable"))
        val many = (1..500).map { ScreenNodeInfo(it, "TextView", "label $it", 0, 0) }
        val trimmed = ScreenTree.render(many, maxChars = 400)
        assertTrue(trimmed.length < 500)
        assertTrue(trimmed.contains("more elements omitted"))
        assertNotNull(ScreenTree.render(emptyList()))
    }

    @Test
    fun agentRequestIsForcedToolUseWithOnlyAgentTools() {
        val body = org.json.JSONObject(
            GroqChatRequestBuilder.buildAgentRequestBody(
                listOf(ConversationTurn("user", "t")),
                "ja",
            ),
        )
        assertEquals("required", body.getString("tool_choice"))
        val names = body.getJSONArray("tools").let { tools ->
            (0 until tools.length()).map { tools.getJSONObject(it).getJSONObject("function").getString("name") }
        }
        assertTrue("tap" in names && "finish" in names)
        assertFalse("harbor_command" in names || "set_reminder" in names)
        assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("untrusted"))
    }

    @Test
    fun operatePhoneToolIsOfferedOnlyWhenEnabled() {
        fun names(enabled: Boolean): List<String> {
            val body = GroqChatRequestBuilder.buildRequestBodyWithFunctionCalling(
                conversationHistory = emptyList(),
                languageCode = "ja",
                operatePhoneToolEnabled = enabled,
            )
            val tools = org.json.JSONObject(body).getJSONArray("tools")
            return (0 until tools.length()).map { tools.getJSONObject(it).getJSONObject("function").getString("name") }
        }
        assertTrue(OPERATE_PHONE_TOOL_NAME in names(true))
        assertFalse(OPERATE_PHONE_TOOL_NAME in names(false))
    }
}
