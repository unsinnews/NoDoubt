package com.nodoubt.core.designsystem

import android.content.Context
import android.view.ViewTreeObserver
import android.widget.TextView

/**
 * A TextView that can pin its last [tailLines] lines. The scroll is applied just before each draw,
 * after layout, so growing text is never drawn at a stale position, even when the view's own size is
 * unchanged. [onLineCount] reports the full line count whenever it changes.
 */
internal class TailTextView(context: Context) : TextView(context), ViewTreeObserver.OnPreDrawListener {
    var tailLines = 0
    var onLineCount: ((Int) -> Unit)? = null
    private var reportedLines = -1

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(this)
        super.onDetachedFromWindow()
    }

    override fun onPreDraw(): Boolean {
        val layout = layout ?: return true
        if (layout.lineCount != reportedLines) {
            reportedLines = layout.lineCount
            onLineCount?.invoke(reportedLines)
        }
        if (tailLines > 0) {
            val target = (layout.height - (height - totalPaddingTop - totalPaddingBottom)).coerceAtLeast(0)
            if (scrollY != target) scrollTo(0, target)
        }
        return true
    }
}
