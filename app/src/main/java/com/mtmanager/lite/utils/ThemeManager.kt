package com.mtmanager.lite.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

/**
 * ThemeManager – handles persisting and applying the two XYvion themes:
 *   • DARK  : Theme.MTManagerLite  (deep space dark glass – default)
 *   • LIGHT : Theme.XYvion.LiquidGlass (ultra-premium frosted glass light)
 *
 * Usage in Activity.onCreate() BEFORE setContentView():
 *   ThemeManager.applyTheme(this)
 */
object ThemeManager {

    const val THEME_DARK  = "dark"
    const val THEME_LIGHT = "light"

    private const val PREFS_NAME = "xyvion_theme_prefs"
    private const val KEY_THEME  = "selected_theme"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns current saved theme id – default is dark */
    fun getCurrentTheme(ctx: Context): String =
        prefs(ctx).getString(KEY_THEME, THEME_DARK) ?: THEME_DARK

    fun isLightGlass(ctx: Context): Boolean = getCurrentTheme(ctx) == THEME_LIGHT

    /**
     * Saves the new theme and recreates the activity so it takes effect.
     * Call from a menu action.
     */
    fun setTheme(activity: AppCompatActivity, theme: String) {
        prefs(activity).edit().putString(KEY_THEME, theme).apply()
        activity.recreate()
    }

    fun toggleTheme(activity: AppCompatActivity) {
        val next = if (isLightGlass(activity)) THEME_DARK else THEME_LIGHT
        setTheme(activity, next)
    }

    /**
     * Must be called in every Activity's onCreate() BEFORE setContentView()
     * to apply the persisted theme.
     */
    fun applyTheme(activity: AppCompatActivity) {
        when (getCurrentTheme(activity)) {
            THEME_LIGHT -> activity.setTheme(com.mtmanager.lite.R.style.Theme_XYvion_LiquidGlass)
            else        -> activity.setTheme(com.mtmanager.lite.R.style.Theme_MTManagerLite)
        }
    }

    /**
     * Returns the theme-aware tint for the toggle menu icon.
     */
    fun themeLabel(ctx: Context): String =
        if (isLightGlass(ctx)) "🌙 Switch to Dark Theme" else "☀️ Switch to Light Glass"
}
