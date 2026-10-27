package com.g150446.voiceclasp

import java.net.URLDecoder

/** What `openclaw connect-qr` (github.com/g150446/openclaw-connect-qr) puts in its QR code. */
data class OpenClawSetup(
    val baseUrl: String,
    val secret: String,
    val agent: String?,
    val name: String?,
)

private const val SETUP_PREFIX = "openclaw-connect://http?"

/**
 * Parses `openclaw-connect://http?v=1&url=…&auth=…&secret=…[&agent=…][&name=…]`.
 * Error messages never include the secret.
 */
fun parseOpenClawSetupUri(raw: String): Result<OpenClawSetup> = runCatching {
    val text = raw.trim()
    require(text.startsWith(SETUP_PREFIX)) { "OpenClaw 接続用の QR ではありません" }
    val params = text.removePrefix(SETUP_PREFIX).split('&')
        .filter(String::isNotEmpty)
        .associate { pair ->
            val name = pair.substringBefore('=')
            val value = if ('=' in pair) pair.substringAfter('=') else ""
            decode(name) to decode(value)
        }
    require(params["v"] == "1") { "未対応の QR 形式です（v=${params["v"] ?: "なし"}）。アプリを更新してください" }
    val baseUrl = params["url"].orEmpty().trim().trimEnd('/')
    require(baseUrl.matches(Regex("^https?://[^/]+.*"))) { "QR の Gateway URL が不正です" }
    val auth = params["auth"]
    require(auth == "token" || auth == "password") { "QR の認証方式が不正です" }
    val secret = params["secret"].orEmpty().trim()
    require(secret.isNotEmpty()) { "QR に token / password が含まれていません" }
    OpenClawSetup(
        baseUrl = baseUrl,
        secret = secret,
        agent = params["agent"]?.trim()?.takeIf(String::isNotEmpty),
        name = params["name"]?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
