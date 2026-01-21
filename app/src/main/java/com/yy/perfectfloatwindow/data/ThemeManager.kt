package com.yy.perfectfloatwindow.data

import android.content.Context

object ThemeManager {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "current_theme"
    private const val KEY_FLOAT_SIZE = "float_size"

    const val THEME_CHATGPT = "chatgpt"
    const val THEME_NETFLIX = "netflix"

    fun getCurrentTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_CHATGPT) ?: THEME_CHATGPT
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getFloatSize(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FLOAT_SIZE, 36)
    }

    fun setFloatSize(context: Context, size: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FLOAT_SIZE, size).apply()
    }

    fun isChatGPTTheme(context: Context): Boolean {
        return getCurrentTheme(context) == THEME_CHATGPT
    }

    // ChatGPT Theme Colors
    object ChatGPT {
        const val BACKGROUND = 0xFFFFFFFF.toInt()
        const val SURFACE = 0xFFF7F7F8.toInt()
        const val PRIMARY = 0xFF10A37F.toInt()  // ChatGPT green
        const val TEXT_PRIMARY = 0xFF202123.toInt()
        const val TEXT_SECONDARY = 0xFF6E6E80.toInt()
        const val FLOAT_BG = 0xFF10A37F.toInt()
        const val NAV_BG = 0xFFFFFFFF.toInt()
    }

    // Claude Theme Colors (浅棕黑主题)
    object Netflix {
        const val BACKGROUND = 0xFFFAF9F5.toInt()  // Cream background
        const val SURFACE = 0xFFFAF9F5.toInt()     // Cream surface
        const val PRIMARY = 0xFF141413.toInt()     // Dark brownish-black
        const val TEXT_PRIMARY = 0xFF141413.toInt()
        const val TEXT_SECONDARY = 0xFF666666.toInt()
        const val FLOAT_BG = 0xFF141413.toInt()
        const val NAV_BG = 0xFFFAF9F5.toInt()
        const val ACCENT = 0xFFD97757.toInt()      // Warm orange accent (小点缀)
    }
}
