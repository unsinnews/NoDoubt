package com.nodoubt.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nodoubt.core.model.ThemeFamily

internal fun paletteFor(family: ThemeFamily, dark: Boolean): Pair<ColorScheme, NoDoubtTokens> {
    val colors = when (family) {
        ThemeFamily.PICOVE -> if (dark) longArrayOf(
            0xff17171b, 0xff1e1e23, 0xff121216, 0xffedeef0, 0xff9a9ba3, 0xff2a2a2f, 0xffdf6b35
        ) else longArrayOf(0xfff5f4f2, 0xffffffff, 0xffeeece9, 0xff1b1c1f, 0xff6b6c74, 0xffe0e0e4, 0xffd45d2d)
        ThemeFamily.VERCEL -> if (dark) longArrayOf(
            0xff000000, 0xff0a0a0a, 0xff050505, 0xffededed, 0xffa0a0a0, 0xff292929, 0xffededed
        ) else longArrayOf(0xfff2f2f2, 0xffffffff, 0xffeaeaea, 0xff171717, 0xff4d4d4d, 0xffe0e0e0, 0xff171717)
        ThemeFamily.APPLE -> if (dark) longArrayOf(
            0xff1c1c1e, 0xff2c2c2e, 0xff242426, 0xfff5f5f7, 0xffa1a1a6, 0xff444446, 0xff0068d9
        ) else longArrayOf(0xfff2f2f7, 0xffffffff, 0xffe8e8ed, 0xff1d1d1f, 0xff6e6e73, 0xffd4d4d8, 0xff0068d9)
    }.map(::Color)
    val surface = colors[0]
    val raised = colors[1]
    val inset = colors[2]
    val foreground = colors[3]
    val muted = colors[4]
    val border = colors[5]
    val accent = colors[6]
    val onAccent = if (family == ThemeFamily.VERCEL && dark) Color(0xff171717) else Color.White
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.12f), onPrimaryContainer = foreground,
        secondary = muted, onSecondary = surface,
        secondaryContainer = inset, onSecondaryContainer = foreground,
        tertiary = accent, onTertiary = onAccent,
        background = surface, onBackground = foreground,
        surface = surface, onSurface = foreground,
        surfaceVariant = raised, onSurfaceVariant = muted,
        surfaceContainer = inset, surfaceContainerLow = raised,
        surfaceContainerHigh = raised, surfaceContainerHighest = inset,
        outline = border, outlineVariant = border.copy(alpha = 0.72f),
        error = if (dark) Color(0xffff6868) else Color(0xffbd2525),
        surfaceTint = Color.Transparent
    )
    // Brand accent remains exact; normal-size filled button labels use a WCAG AA pair.
    val action = if (family == ThemeFamily.PICOVE && !dark) Color(0xffb84c20) else accent
    val onAction = if (family == ThemeFamily.PICOVE && dark) Color(0xff17171b) else onAccent
    val foregroundAccent = if (family == ThemeFamily.APPLE && dark) Color(0xff409cff) else action
    return scheme to NoDoubtTokens(inset, raised, if (dark) Color(0xff68d890) else Color(0xff16743b), family, dark,
        action, onAction, foregroundAccent)
}
