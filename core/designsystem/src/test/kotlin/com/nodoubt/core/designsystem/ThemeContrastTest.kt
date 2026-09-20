package com.nodoubt.core.designsystem

import androidx.compose.ui.graphics.Color
import com.nodoubt.core.model.ThemeFamily
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastTest {
    @Test fun everyThemeHasReadableBodyAndPrimaryActionText() {
        ThemeFamily.entries.forEach { family ->
            listOf(false, true).forEach { dark ->
                val (scheme, tokens) = paletteFor(family, dark)
                assertTrue("$family dark=$dark body", contrast(scheme.onSurface, scheme.surface) >= 4.5)
                assertTrue("$family dark=$dark action", contrast(tokens.onAction, tokens.action) >= 4.5)
                assertTrue("$family dark=$dark link", contrast(tokens.foregroundAccent, scheme.surface) >= 4.5)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double = if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
