package com.g150446.voiceclasp

/** One operable or readable element of the foreground window, free of Android types. */
internal data class ScreenNodeInfo(
    val index: Int,
    val kind: String,
    val label: String,
    val centerX: Int,
    val centerY: Int,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val password: Boolean = false,
)

internal object ScreenTree {
    const val MAX_CHARS = 6_000
    const val MAX_LABEL_CHARS = 80
    const val PASSWORD_PLACEHOLDER = "[password field]"

    /** Text shown to the model for one element; password content is never included. */
    fun line(node: ScreenNodeInfo): String {
        val label = if (node.password) PASSWORD_PLACEHOLDER else node.label.replace('\n', ' ').trim().take(MAX_LABEL_CHARS)
        val flags = buildList {
            if (node.clickable) add("clickable")
            if (node.longClickable) add("long-clickable")
            if (node.editable) add("editable")
            if (node.scrollable) add("scrollable")
            if (node.focused) add("focused")
            when (node.checked) {
                true -> add("checked")
                false -> add("unchecked")
                null -> Unit
            }
            if (!node.enabled) add("disabled")
        }
        val quoted = if (label.isEmpty()) "" else " \"$label\""
        val flagText = if (flags.isEmpty()) "" else " " + flags.joinToString(",")
        return "#${node.index} ${node.kind}$quoted (${node.centerX},${node.centerY})$flagText"
    }

    /** The numbered element list, trimmed to [maxChars] without cutting a line in half. */
    fun render(nodes: List<ScreenNodeInfo>, maxChars: Int = MAX_CHARS): String {
        if (nodes.isEmpty()) return "(no readable elements)"
        val out = StringBuilder()
        var shown = 0
        for (node in nodes) {
            val line = line(node)
            if (out.length + line.length + 1 > maxChars) break
            out.append(line).append('\n')
            shown++
        }
        if (shown < nodes.size) out.append("... ${nodes.size - shown} more elements omitted")
        return out.toString().trimEnd()
    }
}
