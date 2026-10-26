package com.g150446.voiceclasp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Reads the foreground window and operates it through the accessibility service. Same shape as
 * [KindlePageTurnController]: a process-local bridge that the service attaches itself to.
 * Element numbers (#N) are valid only for the latest [observe].
 */
internal object ScreenOperator : ScreenOperatorPort {
    private const val MAX_NODES = 250
    private const val MAX_DEPTH = 40
    private const val SETTLE_POLL_MS = 350L
    private const val SETTLE_MAX_MS = 2_000L
    private const val TAP_MS = 60L
    private const val LONG_PRESS_MS = 700L
    private const val SWIPE_MS = 320L

    private class Entry(val info: ScreenNodeInfo, val node: AccessibilityNodeInfo)

    @Volatile private var service: AccessibilityService? = null
    @Volatile private var entries: List<Entry> = emptyList()

    fun attach(accessibilityService: AccessibilityService) {
        service = accessibilityService
    }

    fun detach(accessibilityService: AccessibilityService) {
        if (service === accessibilityService) service = null
    }

    fun isAvailable(): Boolean = service != null

    override fun labelOf(index: Int): String? = entries.firstOrNull { it.info.index == index }?.info?.label

    override suspend fun observe(): ScreenObservation = withContext(Dispatchers.Main.immediate) {
        val current = service ?: return@withContext ScreenObservation(null, "", 0, readable = false)
        val root = current.rootInActiveWindow
            ?: return@withContext ScreenObservation(null, "", 0, readable = false)
        val packageName = root.packageName?.toString()
        // Never serialize the screens the agent must not read (and never keep their nodes).
        if (ScreenGuard.isDeniedPackage(packageName)) {
            entries = emptyList()
            return@withContext ScreenObservation(packageName, "", packageName.hashCode(), readable = false)
        }
        val collected = ArrayList<Entry>()
        collect(root, collected, depth = 0)
        entries = collected
        val text = ScreenTree.render(collected.map { it.info })
        ScreenObservation(packageName, text, text.hashCode(), readable = collected.isNotEmpty())
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Entry>, depth: Int) {
        if (out.size >= MAX_NODES || depth > MAX_DEPTH || !node.isVisibleToUser) return
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val scrollable = node.actionList.any {
            it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val interesting = node.isClickable || node.isLongClickable || node.isEditable || scrollable ||
            text.isNotBlank() || description.isNotBlank()
        if (interesting) {
            val bounds = Rect().also(node::getBoundsInScreen)
            val label = text.ifBlank { description }.ifBlank { node.hintText?.toString().orEmpty() }
            out += Entry(
                ScreenNodeInfo(
                    index = out.size + 1,
                    kind = node.className?.toString()?.substringAfterLast('.') ?: "View",
                    label = label,
                    centerX = bounds.centerX(),
                    centerY = bounds.centerY(),
                    clickable = node.isClickable,
                    longClickable = node.isLongClickable,
                    editable = node.isEditable,
                    scrollable = scrollable,
                    focused = node.isFocused,
                    checked = if (node.isCheckable) node.isChecked else null,
                    enabled = node.isEnabled,
                    password = node.isPassword,
                ),
                node,
            )
        }
        for (child in 0 until node.childCount) {
            val childNode = node.getChild(child) ?: continue
            collect(childNode, out, depth + 1)
        }
    }

    override suspend fun settle() {
        delay(SETTLE_POLL_MS)
        var waited = SETTLE_POLL_MS
        var previous = observe().signature
        while (waited < SETTLE_MAX_MS) {
            delay(SETTLE_POLL_MS)
            waited += SETTLE_POLL_MS
            val next = observe().signature
            if (next == previous) return
            previous = next
        }
    }

    override suspend fun perform(action: ScreenAction): ActionResult {
        val current = service ?: return ActionResult(false, "ユーザー補助が無効です")
        return when (action) {
            is ScreenAction.Tap -> tap(current, action.index)
            is ScreenAction.TapXy -> gestureResult(tapAt(current, action.x, action.y, TAP_MS))
            is ScreenAction.LongPress -> longPress(current, action.index)
            is ScreenAction.Scroll -> scroll(current, action)
            is ScreenAction.TypeText -> typeText(current, action)
            ScreenAction.Back -> global(current, AccessibilityService.GLOBAL_ACTION_BACK)
            ScreenAction.Home -> global(current, AccessibilityService.GLOBAL_ACTION_HOME)
            is ScreenAction.OpenApp -> {
                if (ScreenGuard.isDeniedPackage(AppLauncher.find(current.applicationContext, action.name)?.packageName)) {
                    ActionResult(false, "このアプリは操作できません")
                } else {
                    ActionResult(true, AppLauncher.openSpoken(current.applicationContext, action.name))
                }
            }
            is ScreenAction.Wait -> {
                delay(action.ms)
                ActionResult(true, "待機しました")
            }
            is ScreenAction.Finish, is ScreenAction.Invalid -> ActionResult(false, "操作ではありません")
        }
    }

    private fun entry(index: Int?): Entry? = index?.let { wanted -> entries.firstOrNull { it.info.index == wanted } }

    private suspend fun tap(current: AccessibilityService, index: Int): ActionResult {
        val target = entry(index) ?: return ActionResult(false, "#$index は現在の画面にありません")
        val clicked = withContext(Dispatchers.Main.immediate) { clickOrParent(target.node) }
        if (clicked) return ActionResult(true, "タップしました")
        return gestureResult(tapAt(current, target.info.centerX, target.info.centerY, TAP_MS))
    }

    private suspend fun longPress(current: AccessibilityService, index: Int): ActionResult {
        val target = entry(index) ?: return ActionResult(false, "#$index は現在の画面にありません")
        val done = withContext(Dispatchers.Main.immediate) {
            var node: AccessibilityNodeInfo? = target.node
            var ok = false
            while (node != null && !ok) {
                ok = node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                node = node.parent
            }
            ok
        }
        if (done) return ActionResult(true, "長押ししました")
        return gestureResult(tapAt(current, target.info.centerX, target.info.centerY, LONG_PRESS_MS))
    }

    private fun clickOrParent(start: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = start
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
        }
        return false
    }

    private suspend fun scroll(current: AccessibilityService, action: ScreenAction.Scroll): ActionResult {
        val vertical = action.direction == ScrollDirection.UP || action.direction == ScrollDirection.DOWN
        if (vertical) {
            val forward = action.direction == ScrollDirection.DOWN
            val actionId = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val done = withContext(Dispatchers.Main.immediate) {
                val node = entry(action.index)?.node
                    ?: entries.firstOrNull { it.info.scrollable }?.node
                node != null && node.performAction(actionId)
            }
            if (done) return ActionResult(true, "スクロールしました")
        }
        val target = swipeTarget(current)
        val midX = target.left + target.width / 2f
        val midY = target.top + target.height / 2f
        val (fromX, fromY, toX, toY) = when (action.direction) {
            ScrollDirection.DOWN -> listOf(midX, target.top + target.height * 0.75f, midX, target.top + target.height * 0.25f)
            ScrollDirection.UP -> listOf(midX, target.top + target.height * 0.25f, midX, target.top + target.height * 0.75f)
            ScrollDirection.LEFT -> listOf(target.left + target.width * 0.25f, midY, target.left + target.width * 0.75f, midY)
            ScrollDirection.RIGHT -> listOf(target.left + target.width * 0.75f, midY, target.left + target.width * 0.25f, midY)
        }
        return gestureResult(dispatch(current, target.displayId, fromX, fromY, toX, toY, SWIPE_MS))
    }

    private suspend fun typeText(current: AccessibilityService, action: ScreenAction.TypeText): ActionResult {
        val done = withContext(Dispatchers.Main.immediate) {
            val node = entry(action.index)?.node
                ?: current.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: entries.firstOrNull { it.info.editable }?.node
                ?: return@withContext false
            if (node.isPassword) return@withContext false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val set = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (set && action.submit && Build.VERSION.SDK_INT >= 30) {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            set
        }
        return if (done) ActionResult(true, "入力しました") else ActionResult(false, "入力できる欄が見つかりません")
    }

    private fun global(current: AccessibilityService, action: Int): ActionResult =
        if (current.performGlobalAction(action)) ActionResult(true, "実行しました") else ActionResult(false, "失敗しました")

    private fun gestureResult(ok: Boolean): ActionResult =
        if (ok) ActionResult(true, "実行しました") else ActionResult(false, "ジェスチャーに失敗しました")

    private class SwipeTarget(val displayId: Int, val left: Int, val top: Int, val width: Int, val height: Int)

    /** The display and area of the foreground app; on a foldable that may be the cover display. */
    private suspend fun swipeTarget(current: AccessibilityService): SwipeTarget = withContext(Dispatchers.Main.immediate) {
        val packageName = current.rootInActiveWindow?.packageName?.toString()
        if (Build.VERSION.SDK_INT >= 30 && packageName != null) {
            val displays = current.windowsOnAllDisplays
            for (i in 0 until displays.size()) {
                for (window in displays.valueAt(i)) {
                    val root = window.root ?: continue
                    val match = root.packageName?.toString() == packageName
                    @Suppress("DEPRECATION") root.recycle()
                    if (!match) continue
                    val bounds = Rect().also(window::getBoundsInScreen)
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        return@withContext SwipeTarget(displays.keyAt(i), bounds.left, bounds.top, bounds.width(), bounds.height())
                    }
                }
            }
        }
        val metrics = current.resources.displayMetrics
        SwipeTarget(Display.DEFAULT_DISPLAY, 0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private suspend fun tapAt(current: AccessibilityService, x: Int, y: Int, durationMs: Long): Boolean {
        val target = swipeTarget(current)
        return dispatch(current, target.displayId, x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), durationMs)
    }

    private suspend fun dispatch(
        current: AccessibilityService,
        displayId: Int,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            if (fromX != toX || fromY != toY) lineTo(toX, toY)
        }
        val builder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
        if (Build.VERSION.SDK_INT >= 30) builder.setDisplayId(displayId)
        val gesture = builder.build()
        suspendCancellableCoroutine { continuation ->
            val dispatched = runCatching {
                current.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    },
                    null,
                )
            }.getOrDefault(false)
            if (!dispatched && continuation.isActive) continuation.resume(false)
        }
    }
}
