package com.g150446.voiceharness

import android.content.Context

/** Whether the user allows the LLM to operate other apps. Off until switched on in the home screen. */
internal class ScreenAgentPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun enabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "screen_agent"
        private const val KEY_ENABLED = "enabled"
    }
}
