package com.g150446.voiceclasp

/** Where the screen agent must not go, and which buttons need a human tap first. */
internal object ScreenGuard {
    /** Package-name fragments the agent never reads or operates (banking, payments, secrets, system prompts). */
    private val DENIED_PACKAGE_FRAGMENTS = listOf(
        "com.g150446.voiceclasp",
        "com.android.settings",
        "packageinstaller",
        "permissioncontroller",
        "com.android.vending",
        "bank",
        "wallet",
        "paypay",
        "paisa",
        "pay.",
        "authenticator",
        "password",
        "keepass",
        "bitwarden",
        "1password",
        "lastpass",
        "dashlane",
    )

    fun isDeniedPackage(packageName: String?): Boolean {
        val value = packageName?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return false
        return DENIED_PACKAGE_FRAGMENTS.any { value.contains(it) }
    }

    private val RISKY_LABEL = Regex(
        "送信|送る|送金|振込|購入|買う|支払|決済|注文|予約確定|削除|消去|破棄|初期化|許可|承認|確定|" +
            "send|submit|buy|purchase|pay|order|checkout|delete|remove|erase|reset|transfer|allow|confirm|post|publish",
        RegexOption.IGNORE_CASE,
    )

    /** True for a button whose label means "this cannot be undone or costs something". */
    fun isRiskyLabel(label: String?): Boolean {
        val value = label?.trim().orEmpty()
        return value.isNotEmpty() && RISKY_LABEL.containsMatchIn(value)
    }
}
