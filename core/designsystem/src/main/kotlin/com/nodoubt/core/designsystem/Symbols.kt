package com.nodoubt.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class UiSymbol { BACK, CHEVRON, ADD, MORE, CHECK, SERVER, SLIDERS, FOLDER, LINK, PLAY, KEY, TRASH, EDIT, DOWNLOAD, UPLOAD }

@Composable
fun Symbol(symbol: UiSymbol, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier.size(22.dp)) {
        val stroke = Stroke(1.8f, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)
        fun path(vararg points: Float) = drawPath(Path().apply {
            moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        }, color, style = stroke)
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            when (symbol) {
                UiSymbol.BACK -> path(15f, 4f, 7f, 12f, 15f, 20f)
                UiSymbol.CHEVRON -> path(9f, 6f, 15f, 12f, 9f, 18f)
                UiSymbol.ADD -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
                UiSymbol.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.6f, Offset(it, 12f)) }
                UiSymbol.CHECK -> path(5f, 12f, 10f, 17f, 19f, 7f)
                UiSymbol.SERVER -> {
                    listOf(4f, 13f).forEach { y ->
                        drawRoundRect(color, Offset(3f, y), Size(18f, 7f), CornerRadius(2f), style = stroke)
                        drawCircle(color, 1f, Offset(7f, y + 3.5f)); line(12f, y + 3.5f, 17f, y + 3.5f)
                    }
                }
                UiSymbol.SLIDERS -> {
                    listOf(6f, 12f, 18f).forEach { line(3f, it, 21f, it) }
                    listOf(Offset(8f, 6f), Offset(16f, 12f), Offset(10f, 18f)).forEach { drawCircle(color, 2.6f, it) }
                }
                UiSymbol.FOLDER -> path(3f, 7f, 3f, 19f, 21f, 19f, 21f, 7f, 12f, 7f, 10f, 4f, 3f, 4f, 3f, 7f)
                UiSymbol.LINK -> { path(9f, 7f, 12f, 4f, 17f, 4f, 21f, 8f, 21f, 12f, 17f, 16f); path(15f, 17f, 12f, 20f, 7f, 20f, 3f, 16f, 3f, 12f, 7f, 8f); line(8f, 16f, 16f, 8f) }
                UiSymbol.PLAY -> path(8f, 5f, 19f, 12f, 8f, 19f, 8f, 5f)
                UiSymbol.KEY -> { drawCircle(color, 4f, Offset(8f, 8f), style = stroke); path(11f, 11f, 20f, 20f, 21f, 17f); line(16f, 16f, 18f, 14f) }
                UiSymbol.TRASH -> { line(4f, 6f, 20f, 6f); path(6f, 6f, 7f, 21f, 17f, 21f, 18f, 6f); path(9f, 6f, 9f, 3f, 15f, 3f, 15f, 6f) }
                UiSymbol.EDIT -> { path(4f, 15f, 4f, 20f, 9f, 20f, 21f, 8f, 16f, 3f, 4f, 15f); line(13f, 6f, 18f, 11f) }
                UiSymbol.DOWNLOAD, UiSymbol.UPLOAD -> {
                    val a = if (symbol == UiSymbol.DOWNLOAD) 4f else 16f
                    val b = if (symbol == UiSymbol.DOWNLOAD) 16f else 4f
                    line(12f, a, 12f, b); path(7f, if (b == 16f) 11f else 9f, 12f, b, 17f, if (b == 16f) 11f else 9f)
                    path(4f, 17f, 4f, 21f, 20f, 21f, 20f, 17f)
                }
            }
        }
    }
}

@Composable
fun ToolbarIcon(symbol: UiSymbol, description: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = description }, enabled) {
        Symbol(symbol, color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
            else if (danger) MaterialTheme.colorScheme.error else LocalNoDoubtTokens.current.foregroundAccent)
    }
}

@Composable
fun LeadingSymbol(symbol: UiSymbol) {
    Box(Modifier.size(30.dp).background(LocalNoDoubtTokens.current.foregroundAccent.copy(alpha = .10f), RoundedCornerShape(7.dp)).padding(5.dp)) {
        Symbol(symbol, color = LocalNoDoubtTokens.current.foregroundAccent)
    }
}
