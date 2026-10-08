/*
 * Operator: chat for keypad phones.
 * Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. It is distributed WITHOUT ANY WARRANTY; see
 * the GNU General Public License (LICENSE) for details.
 */
package chat.operator.app.ui

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import chat.operator.app.R

/**
 * Theme, light/dark mode and text size (SPEC §7), persisted and applied to
 * every activity before its content is set.
 */
object Appearance {
    private const val PREFS = "operator_appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_THEME = "theme"
    private const val KEY_TEXT = "text"
    private const val KEY_MOTION = "motion"
    private const val KEY_PEEK = "peek"

    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    /** Theme ids in display order. */
    val themes = listOf(
        ThemeOption("operator", R.style.Theme_Operator_Brand, R.string.theme_operator),
        ThemeOption("slate", R.style.Theme_Operator_Slate, R.string.theme_slate),
        ThemeOption("switchboard", R.style.Theme_Operator_Switchboard, R.string.theme_switchboard),
        ThemeOption("matrix", R.style.Theme_Operator_Matrix, R.string.theme_matrix),
        ThemeOption("highcontrast", R.style.Theme_Operator_HighContrast, R.string.theme_high_contrast),
    )
    val textScales = listOf(
        TextScale("normal", R.style.TextScale_Normal, R.string.text_size_normal),
        TextScale("large", R.style.TextScale_Large, R.string.text_size_large),
        TextScale("xl", R.style.TextScale_ExtraLarge, R.string.text_size_extra_large),
    )

    data class ThemeOption(val id: String, val style: Int, val nameRes: Int)
    data class TextScale(val id: String, val style: Int, val nameRes: Int)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): Int = prefs(context).getInt(KEY_MODE, SYSTEM)
    fun theme(context: Context): ThemeOption = themes.firstOrNull { it.id == prefs(context).getString(KEY_THEME, null) } ?: themes[0]
    fun textScale(context: Context): TextScale = textScales.firstOrNull { it.id == prefs(context).getString(KEY_TEXT, null) } ?: textScales[0]

    fun setMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply()
        applyNightMode(context)
    }

    /** Screen and menu animations; on by default, off for people who find movement distracting. */
    fun motion(context: Context): Boolean = prefs(context).getBoolean(KEY_MOTION, true)
    fun setMotion(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_MOTION, on).apply()

    /** Turning the phone sideways on the chat list previews the highlighted chat (needs auto-rotate). */
    fun peek(context: Context): Boolean = prefs(context).getBoolean(KEY_PEEK, true)
    fun setPeek(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_PEEK, on).apply()

    fun setTheme(context: Context, theme: ThemeOption) = prefs(context).edit().putString(KEY_THEME, theme.id).apply()
    fun setTextScale(context: Context, scale: TextScale) = prefs(context).edit().putString(KEY_TEXT, scale.id).apply()

    /** Light / dark / follow the system; app-wide, at start-up and on change. */
    fun applyNightMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode(context)) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

    /** Theme and text size for one activity; call before setContentView. */
    fun applyTo(activity: Activity) {
        activity.setTheme(theme(activity).style)
        activity.theme.applyStyle(textScale(activity).style, true)
    }
}
