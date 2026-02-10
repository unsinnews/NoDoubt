package com.nodoubt.app.data

import android.content.Context

object ThemeManager {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "current_theme"
    private const val KEY_FLOAT_SIZE = "float_size"
    private const val KEY_NICKNAME = "user_nickname"
    private const val KEY_AVATAR_INDEX = "avatar_index"

    const val THEME_LIGHT_GREEN_GRAY = "light_green_gray"  // 浅绿灰
    const val THEME_LIGHT_BROWN_BLACK = "light_brown_black"  // 浅棕黑

    // Available avatar icons
    const val AVATAR_LIGHTBULB = 0
    const val AVATAR_BRAIN = 1
    const val AVATAR_STAR = 2
    const val AVATAR_ROCKET = 3

    fun getCurrentTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_LIGHT_GREEN_GRAY) ?: THEME_LIGHT_GREEN_GRAY
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

    fun getNickname(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NICKNAME, "我的") ?: "我的"
    }

    fun setNickname(context: Context, nickname: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getAvatarIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AVATAR_INDEX, AVATAR_LIGHTBULB)
    }

    fun setAvatarIndex(context: Context, index: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AVATAR_INDEX, index).apply()
    }

    fun isLightGreenGrayTheme(context: Context): Boolean {
        return getCurrentTheme(context) == THEME_LIGHT_GREEN_GRAY
    }

    // 浅绿灰主题颜色 (Light Green Gray Theme)
    object LightGreenGray {
        const val BACKGROUND = 0xFFFFFFFF.toInt()
        const val SURFACE = 0xFFF7F7F8.toInt()
        const val PRIMARY = 0xFF10A37F.toInt()
        const val TEXT_PRIMARY = 0xFF202123.toInt()
        const val TEXT_SECONDARY = 0xFF6E6E80.toInt()
        const val FLOAT_BG = 0xFF10A37F.toInt()
        const val NAV_BG = 0xFFFFFFFF.toInt()
    }

    // 浅棕黑主题颜色 (Light Brown Black Theme) - 暖橙色按钮，黑色文字
    object LightBrownBlack {
        const val BACKGROUND = 0xFFFAF9F5.toInt()
        const val SURFACE = 0xFFFAF9F5.toInt()
        const val PRIMARY = 0xFFDA7A5A.toInt()  // 暖橙色用于按钮/强调
        const val TEXT_PRIMARY = 0xFF141413.toInt()  // 黑色用于文字
        const val TEXT_SECONDARY = 0xFF666666.toInt()
        const val FLOAT_BG = 0xFFDA7A5A.toInt()  // 暖橙色悬浮球
        const val NAV_BG = 0xFFFAF9F5.toInt()
        const val ACCENT = 0xFFDA7A5A.toInt()
    }
}
