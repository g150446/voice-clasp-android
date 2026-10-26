package com.g150446.voiceclasp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Flattened component id for [RingAccessibilityService] in Secure settings. */
internal fun ringAccessibilityComponentFlat(context: Context): String =
    ComponentName(context, RingAccessibilityService::class.java).flattenToString()

/**
 * Whether [enabledServicesSetting] (colon-separated Secure setting value) lists [componentFlat].
 * Pure string compare so unit tests do not need Android ComponentName stubs.
 */
internal fun isAccessibilityServiceListed(
    enabledServicesSetting: String?,
    componentFlat: String,
): Boolean {
    if (enabledServicesSetting.isNullOrBlank() || componentFlat.isBlank()) return false
    val expected = componentFlat.trim()
    val expectedSlash = expected.replace('/', '.')
    return enabledServicesSetting.split(':').any { entry ->
        val name = entry.trim()
        if (name.isEmpty()) return@any false
        name.equals(expected, ignoreCase = true) ||
            name.replace('/', '.').equals(expectedSlash, ignoreCase = true)
    }
}

/** True when the user has enabled [RingAccessibilityService] in system settings. */
internal fun isRingAccessibilityEnabled(context: Context): Boolean {
    val setting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    return isAccessibilityServiceListed(setting, ringAccessibilityComponentFlat(context))
}

/**
 * Opens the system accessibility settings list.
 *
 * Avoid ACTION_ACCESSIBILITY_DETAILS_SETTINGS: several OEMs (incl. Motorola)
 * crash Settings or the caller when the detail intent is used via
 * Activity Result APIs.
 */
internal fun accessibilitySettingsIntent(@Suppress("UNUSED_PARAMETER") context: Context): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

internal enum class EnableAccessibilityResult { ENABLED, ALREADY_ENABLED, NO_PERMISSION, FAILED }

/** Appends [componentFlat] to the colon-separated enabled-services setting without duplicating it. */
internal fun addAccessibilityServiceToSetting(current: String?, componentFlat: String): String {
    if (isAccessibilityServiceListed(current, componentFlat)) return current.orEmpty()
    return if (current.isNullOrBlank()) componentFlat else "$current:$componentFlat"
}

internal enum class DisableAccessibilityResult { DISABLED, ALREADY_DISABLED, NO_PERMISSION, FAILED }

/** Removes [componentFlat] from the colon-separated enabled-services setting. */
internal fun removeAccessibilityServiceFromSetting(current: String?, componentFlat: String): String {
    if (current.isNullOrBlank()) return ""
    val expected = componentFlat.trim()
    val expectedSlash = expected.replace('/', '.')
    return current.split(':')
        .filter { entry ->
            val name = entry.trim()
            name.isNotEmpty() &&
                !name.equals(expected, ignoreCase = true) &&
                !name.replace('/', '.').equals(expectedSlash, ignoreCase = true)
        }
        .joinToString(":")
}

/** True once `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` has been run. */
internal fun canWriteSecureSettings(context: Context): Boolean =
    context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Turns [RingAccessibilityService] on by writing Secure settings directly. Android offers no
 * public API for this; it works only with WRITE_SECURE_SETTINGS, which adb can grant.
 */
internal fun enableRingAccessibility(context: Context): EnableAccessibilityResult {
    if (isRingAccessibilityEnabled(context)) return EnableAccessibilityResult.ALREADY_ENABLED
    if (!canWriteSecureSettings(context)) return EnableAccessibilityResult.NO_PERMISSION
    return try {
        val resolver = context.contentResolver
        val updated = addAccessibilityServiceToSetting(
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            ringAccessibilityComponentFlat(context),
        )
        val written = Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            updated,
        ) && Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        if (written) EnableAccessibilityResult.ENABLED else EnableAccessibilityResult.FAILED
    } catch (_: SecurityException) {
        EnableAccessibilityResult.NO_PERMISSION
    }
}

/** Turns [RingAccessibilityService] off again; needs the same WRITE_SECURE_SETTINGS grant. */
internal fun disableRingAccessibility(context: Context): DisableAccessibilityResult {
    if (!isRingAccessibilityEnabled(context)) return DisableAccessibilityResult.ALREADY_DISABLED
    if (!canWriteSecureSettings(context)) return DisableAccessibilityResult.NO_PERMISSION
    return try {
        val resolver = context.contentResolver
        val remaining = removeAccessibilityServiceFromSetting(
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            ringAccessibilityComponentFlat(context),
        )
        val written = Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            remaining,
        ) && (
            remaining.isNotEmpty() ||
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            )
        if (written) DisableAccessibilityResult.DISABLED else DisableAccessibilityResult.FAILED
    } catch (_: SecurityException) {
        DisableAccessibilityResult.NO_PERMISSION
    }
}
