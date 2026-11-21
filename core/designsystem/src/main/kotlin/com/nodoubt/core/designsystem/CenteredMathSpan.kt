package com.nodoubt.core.designsystem

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import kotlin.math.max
import kotlin.math.min

/**
 * An inline formula centred on the middle of the surrounding text rather than on its baseline, so
 * x², fractions and roots sit level with the Chinese characters around them. The line only grows
 * when a formula is taller than the text itself.
 */
internal class CenteredMathSpan(private val drawable: Drawable) : ReplacementSpan() {
    init {
        if (drawable.bounds.isEmpty) drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val font = paint.fontMetricsInt
            val height = drawable.bounds.height()
            val top = (font.ascent + font.descent) / 2 - height / 2
            val bottom = top + height
            fm.ascent = min(font.ascent, top)
            fm.descent = max(font.descent, bottom)
            fm.top = min(font.top, top)
            fm.bottom = max(font.bottom, bottom)
        }
        return drawable.bounds.width()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint) {
        val font = paint.fontMetricsInt
        val centre = y + (font.ascent + font.descent) / 2f
        val count = canvas.save()
        canvas.translate(x, centre - drawable.bounds.height() / 2f)
        drawable.draw(canvas)
        canvas.restoreToCount(count)
    }
}
