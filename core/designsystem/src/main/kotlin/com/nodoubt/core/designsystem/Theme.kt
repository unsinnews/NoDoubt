package com.nodoubt.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodoubt.core.model.AppPreferences
import com.nodoubt.core.model.Appearance
import com.nodoubt.core.model.ThemeFamily

data class NoDoubtTokens(
    val inset: Color,
    val raised: Color,
    val success: Color,
    val family: ThemeFamily = ThemeFamily.PICOVE,
    val dark: Boolean = false,
    val action: Color = Color(0xffb84c20),
    val onAction: Color = Color.White,
    val foregroundAccent: Color = Color(0xffb84c20)
)

val LocalNoDoubtTokens = staticCompositionLocalOf {
    NoDoubtTokens(Color(0xfff5f6f7), Color.White, Color(0xff16803e))
}

@Composable
fun NoDoubtTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark = when (preferences.appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.DARK -> true
        Appearance.LIGHT -> false
    }
    val palette = paletteFor(preferences.theme, dark)
    val radius = 12.dp
    CompositionLocalProvider(LocalNoDoubtTokens provides palette.second) {
        MaterialTheme(
            colorScheme = palette.first,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(radius),
                small = RoundedCornerShape(radius),
                medium = RoundedCornerShape(radius + 2.dp),
                large = RoundedCornerShape(radius + 4.dp),
                extraLarge = RoundedCornerShape(radius + 6.dp)
            ),
            typography = Typography(
                headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontFamily = FontFamily.SansSerif),
                bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            ),
            content = content
        )
    }
}
